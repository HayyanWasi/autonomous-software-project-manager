package com.autonomouspm.agents.database;

import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.core.Agent;
import com.autonomouspm.core.AgentResult;
import com.autonomouspm.core.ProjectState;
import com.autonomouspm.infrastructure.MermaidErdGenerator;
import com.autonomouspm.observer.EventLogger;
import com.autonomouspm.observer.EventType;
import com.autonomouspm.service.AiService.AiServiceException;
import com.autonomouspm.tokenmanagement.CachingAiService;
import com.autonomouspm.tokenmanagement.InputStripper;
import com.autonomouspm.tokenmanagement.InputStripper.DatabaseArchitectInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Third agent in the Chain of Responsibility.
 *
 * <p>Spec: {@code specs/modules/database-architect.md}
 *
 * <p>Consumes the upstream {@link com.autonomouspm.context.BusinessContext} and
 * {@link com.autonomouspm.context.RequirementContext}, designs a normalised
 * relational schema via the LLM, and produces a {@link DatabaseContext} whose
 * {@code tables}/{@code relationships} are authoritative and whose
 * {@code mermaidErdChart} is generated in pure Java by {@link MermaidErdGenerator}.
 *
 * <p><b>Token management:</b> all LLM calls go through {@link CachingAiService}
 * (never {@code AiService} directly), and inputs are stripped to only
 * {@code userStories}, {@code coreFeatures} and {@code constraints} via
 * {@link InputStripper} before prompting.
 *
 * <p><b>Design patterns:</b> Chain of Responsibility (third link), Adapter
 * (LLM via the caching service), Decorator (the caching service itself),
 * Observer (events via {@link EventLogger}), Null Object (safe empty context on
 * failure).
 *
 * <p><b>Single Responsibility:</b> schema validation/repair lives in
 * {@link DatabaseArchitectValidator}; ERD rendering lives in
 * {@link MermaidErdGenerator}; this agent only orchestrates.
 */
@Component
public class DatabaseArchitectAgent implements Agent<DatabaseContext> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseArchitectAgent.class);

    private static final String AGENT_NAME = "DatabaseArchitectAgent";

    private final CachingAiService aiService;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;
    private final MermaidErdGenerator mermaidErdGenerator;
    private final DatabaseArchitectValidator validator;

    /**
     * @param aiService           caching LLM decorator; injected by Spring
     * @param eventLogger         shared Observer bus; injected by Spring
     * @param objectMapper        shared Jackson mapper; injected by Spring
     * @param mermaidErdGenerator pure-Java ERD renderer; injected by Spring
     * @param validator           schema validator/repairer; injected by Spring
     */
    public DatabaseArchitectAgent(CachingAiService aiService,
                                  EventLogger eventLogger,
                                  ObjectMapper objectMapper,
                                  MermaidErdGenerator mermaidErdGenerator,
                                  DatabaseArchitectValidator validator) {
        this.aiService           = aiService;
        this.eventLogger         = eventLogger;
        this.objectMapper        = objectMapper;
        this.mermaidErdGenerator = mermaidErdGenerator;
        this.validator           = validator;
    }

    // -------------------------------------------------------------------------
    // Agent contract
    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    /**
     * Executes schema design against the upstream contexts in {@code state}.
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Guard: BusinessContext and RequirementContext must be present.</li>
     *   <li>Publish {@link EventType#SCHEMA_DESIGN_STARTED}.</li>
     *   <li>Strip inputs ({@code userStories}, {@code coreFeatures}, {@code constraints}).</li>
     *   <li>Call the LLM via {@link CachingAiService}; strip markdown; parse.</li>
     *   <li>Validate/repair via {@link DatabaseArchitectValidator}.</li>
     *   <li>Generate the Mermaid ERD from the cleaned schema.</li>
     *   <li>Write the context to {@code state}; publish completion.</li>
     * </ol>
     *
     * @param state shared pipeline state
     * @return successful or failed {@link AgentResult}
     */
    @Override
    public AgentResult<DatabaseContext> execute(ProjectState state) {
        if (state == null) {
            return buildFailure("ProjectState is null. Cannot proceed.");
        }
        if (state.getRequirementContext() == null) {
            return buildFailure("RequirementContext is missing. Schema design requires upstream requirements.");
        }
        if (state.getBusinessContext() == null) {
            return buildFailure("BusinessContext is missing. Schema design requires upstream business analysis.");
        }

        eventLogger.publish(AGENT_NAME, EventType.SCHEMA_DESIGN_STARTED, "Designing Schema...");

        try {
            // ---- Input stripping (token management) ----
            DatabaseArchitectInput input = InputStripper.toDatabaseArchitectInput(
                    state.getBusinessContext(), state.getRequirementContext());

            // ---- Build prompts ----
            String inputJson = objectMapper.writeValueAsString(input);
            String systemPrompt = buildSystemPrompt();
            String userPrompt   = buildUserPrompt(inputJson);

            log.debug("{} – Calling CachingAiService", AGENT_NAME);

            // ---- LLM call via caching decorator (never AiService directly) ----
            String rawResponse = aiService.chat(systemPrompt, userPrompt, AGENT_NAME);
            if (rawResponse == null || rawResponse.isBlank()) {
                return buildFailure("LLM returned an empty response.");
            }

            // ---- Parse JSON → DatabaseContext ----
            DatabaseContext parsed = parseContext(stripMarkdown(rawResponse));

            // ---- Validate & repair the authoritative schema ----
            DatabaseContext validated = validator.validate(parsed);

            // ---- Generate Mermaid ERD (pure Java) from the cleaned schema ----
            eventLogger.publish(AGENT_NAME, EventType.ERD_GENERATING, "Generating ERD...");
            String mermaid = mermaidErdGenerator.generate(validated.tables(), validated.relationships());
            DatabaseContext context = new DatabaseContext(
                    validated.tables(), validated.relationships(), mermaid);

            // ---- Write back to shared state ----
            state.setDatabaseContext(context);
            state.setStatus(ProjectState.PipelineStatus.IN_PROGRESS);

            eventLogger.publish(AGENT_NAME, EventType.DATABASE_DESIGN_COMPLETED,
                    "DatabaseContext built. tables=" + context.tables().size()
                    + ", relationships=" + context.relationships().size());

            log.info("{} – Completed. tables={}", AGENT_NAME, context.tables().size());
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
            return buildFailure("Unexpected error during schema design: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    /**
     * Builds the system prompt: Senior Database Architect persona, schema rules,
     * and the strict JSON constraint block. The {@code mermaidErdChart} is
     * deliberately excluded — it is generated in Java, not by the LLM.
     */
    private String buildSystemPrompt() {
        return """
                You are a Senior Database Architect. Design a normalised relational \
                schema (up to 3NF) from the provided user stories, core features and \
                constraints.

                Rules:
                - Derive entities only from explicit business objects, features, or constraints.
                - Do NOT invent tables that cannot be justified by a requirement or user story.
                - Every table must have exactly one primary key column.
                - Resolve many-to-many relationships using junction tables.
                - Every relationship must carry a business justification.
                - If information is insufficient, set a table's description to "Needs Clarification"
                  rather than inventing details.
                - Use cardinality values exactly: ONE_TO_ONE, ONE_TO_MANY, or MANY_TO_MANY.

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
                5. DO NOT include a "mermaidErdChart" field — it is generated separately.
                6. Your output MUST exactly match this JSON schema structure:

                {
                    "tables": [
                        {
                            "name": "customer",
                            "description": "Registered users who place orders",
                            "columns": [
                                {"name": "id", "dataType": "BIGINT", "isPrimaryKey": true, "isForeignKey": false, "isNullable": false},
                                {"name": "email", "dataType": "VARCHAR(255)", "isPrimaryKey": false, "isForeignKey": false, "isNullable": false}
                            ]
                        }
                    ],
                    "relationships": [
                        {"fromTable": "customer", "toTable": "order", "cardinality": "ONE_TO_MANY", "justification": "A customer places many orders"}
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
                Design the database schema for the following stripped project input (JSON):

                %s

                Remember: respond with ONLY the JSON object. No markdown, no explanations,
                and do not include a mermaidErdChart field.
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
     * Parses the cleaned JSON string into a {@link DatabaseContext} record.
     *
     * @throws JsonProcessingException if the JSON is malformed
     */
    private DatabaseContext parseContext(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, DatabaseContext.class);
    }

    // -------------------------------------------------------------------------
    // Null Object factory
    // -------------------------------------------------------------------------

    /**
     * Builds a failed {@link AgentResult} with a safe empty {@link DatabaseContext}
     * (the {@code EmptyDatabaseContext} Null Object: empty lists and empty chart),
     * so the pipeline can continue without crashing.
     */
    private AgentResult<DatabaseContext> buildFailure(String reason) {
        DatabaseContext nullObject = new DatabaseContext(List.of(), List.of(), "");
        return AgentResult.failure(AGENT_NAME, nullObject, reason);
    }
}
