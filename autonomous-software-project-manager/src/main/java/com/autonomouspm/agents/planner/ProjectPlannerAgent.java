package com.autonomouspm.agents.planner;

import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.context.GanttContext;
import com.autonomouspm.context.GanttContext.ProjectComponent;
import com.autonomouspm.context.GanttContext.ProjectNode;
import com.autonomouspm.context.GanttContext.TaskLeaf;
import com.autonomouspm.context.PlanningValidationError;
import com.autonomouspm.core.Agent;
import com.autonomouspm.core.AgentResult;
import com.autonomouspm.core.ProjectState;
import com.autonomouspm.infrastructure.MermaidGanttGenerator;
import com.autonomouspm.observer.EventLogger;
import com.autonomouspm.observer.EventType;
import com.autonomouspm.service.AiService.AiServiceException;
import com.autonomouspm.tokenmanagement.CachingAiService;
import com.autonomouspm.tokenmanagement.InputStripper;
import com.autonomouspm.tokenmanagement.InputStripper.ProjectPlannerInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fourth agent in the Chain of Responsibility.
 *
 * <p>Spec: {@code specs/modules/project-planner.md}
 *
 * <p>Consumes the upstream {@link DatabaseContext} and {@link BusinessContext},
 * asks the LLM (acting as a Senior Technical Project Planner) to decompose the
 * work into a phased Work Breakdown Structure, and produces a {@link GanttContext}
 * whose {@link ProjectNode} composite tree is authoritative and whose
 * {@code mermaidGanttChart} is generated in pure Java by
 * {@link MermaidGanttGenerator}.
 *
 * <p><b>Scope (spec §3):</b> the planner handles task decomposition, sequencing,
 * and dependency mapping ONLY. It must NOT produce duration, effort, cost, or
 * team estimates — those are out of scope for this agent. The prompt forbids
 * those fields explicitly.
 *
 * <p><b>Token management:</b> all LLM calls go through {@link CachingAiService}
 * (never {@code AiService} directly), with the dedicated {@code AGENT_NAME} for
 * cache keying and the 1500-token output budget; inputs are stripped to only
 * {@code tables}, {@code relationships} and {@code epics} via {@link InputStripper}.
 *
 * <p><b>Design patterns:</b> Chain of Responsibility (fourth link), Composite
 * (the WBS tree), Adapter/Decorator (LLM via the caching service), Observer
 * (events via {@link EventLogger}), Null Object ({@code EmptyGanttContext} on
 * failure).
 *
 * <p><b>Single Responsibility:</b> plan validation lives in
 * {@link ProjectPlannerValidator}; Gantt rendering lives in
 * {@link MermaidGanttGenerator}; this agent only orchestrates.
 */
@Component
public class ProjectPlannerAgent implements Agent<GanttContext> {

    private static final Logger log = LoggerFactory.getLogger(ProjectPlannerAgent.class);

    private static final String AGENT_NAME = "ProjectPlannerAgent";

    private final CachingAiService aiService;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;
    private final MermaidGanttGenerator mermaidGanttGenerator;
    private final ProjectPlannerValidator validator;

    /**
     * @param aiService             caching LLM decorator; injected by Spring
     * @param eventLogger           shared Observer bus; injected by Spring
     * @param objectMapper          shared Jackson mapper; injected by Spring
     * @param mermaidGanttGenerator pure-Java Gantt renderer; injected by Spring
     * @param validator             WBS validator; injected by Spring
     */
    public ProjectPlannerAgent(CachingAiService aiService,
                               EventLogger eventLogger,
                               ObjectMapper objectMapper,
                               MermaidGanttGenerator mermaidGanttGenerator,
                               ProjectPlannerValidator validator) {
        this.aiService             = aiService;
        this.eventLogger           = eventLogger;
        this.objectMapper          = objectMapper;
        this.mermaidGanttGenerator = mermaidGanttGenerator;
        this.validator             = validator;
    }

    // -------------------------------------------------------------------------
    // Agent contract
    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    /**
     * Executes project planning against the upstream contexts in {@code state}.
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Guard: BusinessContext and DatabaseContext must be present.</li>
     *   <li>Publish {@link EventType#PLANNING_STARTED} ("Analyzing Requirements...").</li>
     *   <li>Strip inputs ({@code tables}, {@code relationships}, {@code epics}).</li>
     *   <li>Call the LLM via {@link CachingAiService}; strip markdown; parse the WBS tree.</li>
     *   <li>Validate via {@link ProjectPlannerValidator}; on error, fall back to Null Object.</li>
     *   <li>Generate the Mermaid Gantt from the validated tree.</li>
     *   <li>Write the context to {@code state}; publish completion.</li>
     * </ol>
     *
     * @param state shared pipeline state
     * @return successful or failed {@link AgentResult}
     */
    @Override
    public AgentResult<GanttContext> execute(ProjectState state) {
        if (state == null) {
            return buildFailure("ProjectState is null. Cannot proceed.");
        }
        if (state.getBusinessContext() == null) {
            return buildFailure("BusinessContext is missing. Planning requires upstream business analysis.");
        }
        if (state.getDatabaseContext() == null) {
            return buildFailure("DatabaseContext is missing. Planning requires upstream schema design.");
        }

        eventLogger.publish(AGENT_NAME, EventType.PLANNING_STARTED, "Analyzing Requirements...");

        try {
            // ---- Input stripping (token management) ----
            ProjectPlannerInput input = InputStripper.toProjectPlannerInput(
                    state.getDatabaseContext(), state.getBusinessContext());

            // ---- Build prompts ----
            String inputJson = objectMapper.writeValueAsString(input);
            String systemPrompt = buildSystemPrompt();
            String userPrompt   = buildUserPrompt(inputJson);

            eventLogger.publish(AGENT_NAME, EventType.WBS_GENERATING,
                    "Generating Work Breakdown Structure...");

            log.debug("{} – Calling CachingAiService", AGENT_NAME);

            // ---- LLM call via caching decorator (never AiService directly) ----
            String rawResponse = aiService.chat(systemPrompt, userPrompt, AGENT_NAME);
            if (rawResponse == null || rawResponse.isBlank()) {
                return buildFailure("LLM returned an empty response.");
            }

            // ---- Parse JSON → composite WBS tree ----
            ProjectNode root = parseRoot(stripMarkdown(rawResponse));

            eventLogger.publish(AGENT_NAME, EventType.DEPENDENCIES_MAPPING, "Mapping Dependencies...");

            // ---- Validate the plan (non-fatal; present error → Null Object) ----
            Optional<PlanningValidationError> error = validator.validate(root);
            if (error.isPresent()) {
                PlanningValidationError e = error.get();
                log.warn("{} – Plan validation failed [{}]: {}", AGENT_NAME, e.errorCode(), e.message());
                eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED,
                        "Plan validation failed: " + e.errorCode());
                return buildFailure("Plan validation failed [" + e.errorCode() + "]: " + e.message());
            }

            // ---- Generate Mermaid Gantt (pure Java) from the validated tree ----
            eventLogger.publish(AGENT_NAME, EventType.GANTT_GENERATING, "Generating Gantt Structure...");
            String mermaid = mermaidGanttGenerator.generate(root);

            int totalPhases = countPhases(root);
            int totalTasks  = countTasks(root);
            GanttContext context = new GanttContext(
                    root, totalPhases, totalTasks, buildSummary(totalPhases, totalTasks), mermaid);

            // ---- Write back to shared state ----
            state.setGanttContext(context);
            state.setStatus(ProjectState.PipelineStatus.IN_PROGRESS);

            eventLogger.publish(AGENT_NAME, EventType.PLANNING_COMPLETED,
                    "GanttContext built. phases=" + totalPhases + ", tasks=" + totalTasks);

            log.info("{} – Completed. phases={}, tasks={}", AGENT_NAME, totalPhases, totalTasks);
            return AgentResult.success(AGENT_NAME, context);

        } catch (AiServiceException e) {
            log.error("{} – AiService call failed: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED, "AiService failure: " + e.getMessage());
            return buildFailure("AI service failure: " + e.getMessage());

        } catch (JsonProcessingException e) {
            log.error("{} – JSON parsing failed: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED, "JSON parsing failure: " + e.getMessage());
            return buildFailure("LLM returned invalid JSON: " + e.getOriginalMessage());

        } catch (Exception e) {
            log.error("{} – Unexpected error: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED, "Unexpected error: " + e.getMessage());
            return buildFailure("Unexpected error during project planning: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    /**
     * Builds the system prompt: Senior Technical Project Planner persona, the
     * allowed-phase constraint, the scope exclusions (no duration/effort/cost), the
     * Composite {@code "type"} discriminator contract, and the strict JSON block.
     */
    private String buildSystemPrompt() {
        return """
                You are a Senior Technical Project Planner. Decompose a software project \
                into a structured Work Breakdown Structure (WBS): phases, tasks, execution \
                order, and dependencies.

                You will be given stripped project input (as JSON) containing database \
                tables, relationships, and business epics.

                Your tasks:
                - Convert the epics, tables and relationships into concrete implementation tasks.
                - Organise tasks into phases.
                - Determine a sensible execution order.
                - Identify dependencies between tasks.

                ======================================================================
                PHASE CONSTRAINT (MANDATORY)
                ======================================================================
                Each phase name MUST be EXACTLY one of these (no others allowed):
                  Requirements, Design, Database, Backend Development,
                  Frontend Development, Integration, Testing, Deployment

                ======================================================================
                SCOPE — DO NOT PRODUCE
                ======================================================================
                - NO duration estimates (no days/weeks).
                - NO effort estimates (no hours/story points).
                - NO cost or budget estimates.
                - NO team size or salary recommendations.
                Those belong to a later agent. Provide ONLY the WBS structure.

                ======================================================================
                DEPENDENCY RULES
                ======================================================================
                - A task's "dependencies" array lists the NAMES of other tasks it depends on.
                - Every dependency MUST reference an existing task name in your output.
                - NO circular dependencies.

                ======================================================================
                COMPOSITE STRUCTURE & DISCRIMINATORS (MANDATORY)
                ======================================================================
                The output is a tree. EVERY node MUST carry a "type" discriminator:
                - A phase node uses "type": "node" and has a "children" array.
                - A task uses "type": "leaf" and has NO "children".
                The root project is a "node" whose children are the phases.

                ======================================================================
                CRITICAL INSTRUCTION: STRICT JSON OUTPUT REQUIRED
                ======================================================================
                You are functioning as a backend data-processing node.
                You MUST respond with pure, valid JSON parsed directly by Jackson (Java).

                RULES:
                1. DO NOT wrap the JSON in markdown blocks (e.g., no ```json ... ```).
                2. DO NOT include any conversational text before or after the JSON.
                3. Ensure all keys and string values are properly escaped in double quotes.
                4. DO NOT use trailing commas in arrays or objects.
                5. "complexity" MUST be exactly one of: Small, Medium, Large, Very Large.
                6. Your output MUST be the ROOT project node matching this structure:

                {
                    "type": "node",
                    "name": "Project",
                    "children": [
                        {
                            "type": "node",
                            "name": "Backend Development",
                            "children": [
                                {
                                    "type": "leaf",
                                    "name": "User Authentication",
                                    "assigneeRole": "Backend Developer",
                                    "complexity": "Medium",
                                    "dependencies": []
                                },
                                {
                                    "type": "leaf",
                                    "name": "Order Management",
                                    "assigneeRole": "Backend Developer",
                                    "complexity": "Large",
                                    "dependencies": ["User Authentication"]
                                }
                            ]
                        }
                    ]
                }

                If you fail to follow these rules, the pipeline will crash. Return ONLY the JSON object.
                """;
    }

    /**
     * Builds the user-turn prompt embedding the stripped input JSON.
     */
    private String buildUserPrompt(String inputJson) {
        return """
                Produce the project Work Breakdown Structure (root project node JSON) for the
                following stripped project input (JSON):

                %s

                Remember: respond with ONLY the JSON object. No markdown, no explanations,
                no duration/effort/cost, and include the "type" discriminator on every node.
                """.formatted(inputJson);
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /**
     * Strips accidental markdown wrappers that some models add despite instructions.
     * Handles both {@code ```json ... ```} and bare {@code ``` ... ```} fences.
     */
    private String stripMarkdown(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?\\s*", "");
            int lastFence = cleaned.lastIndexOf("```");
            if (lastFence != -1) {
                cleaned = cleaned.substring(0, lastFence).trim();
            }
        }
        return cleaned;
    }

    /**
     * Parses the cleaned JSON string into the root {@link ProjectNode} of the
     * composite WBS tree. Relies on the {@code @JsonTypeInfo}/{@code @JsonSubTypes}
     * discriminators declared on {@link ProjectComponent}.
     *
     * @throws JsonProcessingException if the JSON is malformed
     */
    private ProjectNode parseRoot(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, ProjectNode.class);
    }

    // -------------------------------------------------------------------------
    // Tree metrics
    // -------------------------------------------------------------------------

    /** Counts phases: the direct {@link ProjectNode} children of the root. */
    private int countPhases(ProjectNode root) {
        if (root == null || root.children() == null) {
            return 0;
        }
        int phases = 0;
        for (ProjectComponent child : root.children()) {
            if (child instanceof ProjectNode) {
                phases++;
            }
        }
        return phases;
    }

    /** Counts all leaf tasks reachable beneath the root. */
    private int countTasks(ProjectComponent component) {
        if (component instanceof TaskLeaf) {
            return 1;
        }
        int total = 0;
        if (component != null && component.children() != null) {
            for (ProjectComponent child : component.children()) {
                total += countTasks(child);
            }
        }
        return total;
    }

    private String buildSummary(int totalPhases, int totalTasks) {
        return "Work Breakdown Structure generated with " + totalPhases
                + " phase(s) and " + totalTasks + " task(s).";
    }

    // -------------------------------------------------------------------------
    // Null Object factory
    // -------------------------------------------------------------------------

    /**
     * Builds a failed {@link AgentResult} with a safe empty {@link GanttContext}
     * (the {@code EmptyGanttContext} Null Object per spec §4: an "Empty" root with
     * no children, zero counts, blank summary and chart), so the pipeline can
     * continue without crashing.
     */
    private AgentResult<GanttContext> buildFailure(String reason) {
        GanttContext nullObject = new GanttContext(
                new ProjectNode("Empty", List.of()),
                0,
                0,
                "",
                ""
        );
        return AgentResult.failure(AGENT_NAME, nullObject, reason);
    }
}
