package com.autonomouspm.agents.requirement;

import com.autonomouspm.agents.requirement.RequirementValidator.ValidationResult;
import com.autonomouspm.context.RequirementContext;
import com.autonomouspm.core.Agent;
import com.autonomouspm.core.AgentResult;
import com.autonomouspm.core.ProjectState;
import com.autonomouspm.observer.EventLogger;
import com.autonomouspm.observer.EventType;
import com.autonomouspm.service.AiService;
import com.autonomouspm.service.AiService.AiServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * First agent in the Chain of Responsibility.
 *
 * <p>Takes the user's raw project idea from {@link ProjectState#getRawUserInput()}
 * and expands it into a structured {@link RequirementContext} using the LLM via
 * {@link AiService} (Adapter pattern).
 *
 * <p><b>Design patterns:</b>
 * <ul>
 *   <li><b>Chain of Responsibility</b> — first link; output is written back to
 *       {@link ProjectState} by the {@code CentralOrchestrator}.</li>
 *   <li><b>Adapter</b> — delegates all AI calls through {@link AiService}.</li>
 *   <li><b>Observer</b> — publishes lifecycle events via {@link EventLogger}.</li>
 *   <li><b>Null Object</b> — on any failure, returns a safe empty
 *       {@link RequirementContext} via {@link AgentResult#failure}.</li>
 * </ul>
 *
 * <p><b>Single Responsibility:</b> all validation rules live in the injected
 * {@link RequirementValidator}; this agent only orchestrates the LLM call,
 * JSON parsing, state mutation, and event publishing.
 */
@Component
public class RequirementAnalystAgent implements Agent<RequirementContext> {

    private static final Logger log = LoggerFactory.getLogger(RequirementAnalystAgent.class);

    private static final String AGENT_NAME = "RequirementAnalystAgent";

    private final AiService aiService;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;
    private final RequirementValidator validator;

    /**
     * @param aiService    LangChain4j OpenRouter adapter; injected by Spring
     * @param eventLogger  shared Observer bus; injected by Spring
     * @param objectMapper shared Jackson mapper; injected by Spring (auto-configured)
     * @param validator    validation rules for input and parsed context; injected by Spring
     */
    public RequirementAnalystAgent(AiService aiService,
                                   EventLogger eventLogger,
                                   ObjectMapper objectMapper,
                                   RequirementValidator validator) {
        this.aiService    = aiService;
        this.eventLogger  = eventLogger;
        this.objectMapper = objectMapper;
        this.validator    = validator;
    }

    // -------------------------------------------------------------------------
    // Agent contract
    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    /**
     * Executes requirement analysis against the raw user input stored in {@code state}.
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Validate raw input via {@link RequirementValidator#validateRawInput(String)}.</li>
     *   <li>Publish {@link EventType#REQUIREMENT_ANALYSIS_STARTED}.</li>
     *   <li>Build system + user prompts.</li>
     *   <li>Call {@link AiService#chat}.</li>
     *   <li>Strip any accidental markdown wrapper.</li>
     *   <li>Parse response into {@link RequirementContext} via Jackson.</li>
     *   <li>Validate parsed context via {@link RequirementValidator#validateContext(RequirementContext)}.</li>
     *   <li>Write context back to {@code state}.</li>
     *   <li>Publish {@link EventType#REQUIREMENT_ANALYSIS_COMPLETED}.</li>
     * </ol>
     *
     * <p>Any failure at steps 4–7 is caught and returned as
     * {@link AgentResult#failure} with a Null Object placeholder,
     * preventing pipeline crashes.
     *
     * @param state shared pipeline state containing {@link ProjectState#getRawUserInput()}
     * @return successful or failed {@link AgentResult}
     */
    @Override
    public AgentResult<RequirementContext> execute(ProjectState state) {
        if (state == null) {
            return buildFailure("ProjectState is null. Cannot proceed.");
        }

        String rawInput = state.getRawUserInput();

        // ---- Guard: validate raw input before any LLM call ----
        ValidationResult inputCheck = validator.validateRawInput(rawInput);
        if (!inputCheck.valid()) {
            eventLogger.publish(AGENT_NAME, EventType.REQUIREMENT_CLARIFICATION_NEEDED,
                    inputCheck.summary());
            return buildFailure(inputCheck.summary());
        }

        eventLogger.publish(AGENT_NAME, EventType.REQUIREMENT_ANALYSIS_STARTED,
                "Analysing raw input: \"" + abbreviate(rawInput, 80) + "\"");

        try {
            // ---- Build prompts ----
            String systemPrompt = buildSystemPrompt();
            String userPrompt   = buildUserPrompt(rawInput);

            log.debug("{} – Calling AiService", AGENT_NAME);

            // ---- LLM call (Adapter) ----
            String rawResponse = aiService.chat(systemPrompt, userPrompt);

            // ---- Guard: empty response ----
            if (rawResponse == null || rawResponse.isBlank()) {
                return buildFailure("LLM returned an empty response.");
            }

            // ---- Strip accidental markdown wrapper ----
            String cleanJson = stripMarkdown(rawResponse);

            // ---- Parse JSON → RequirementContext ----
            RequirementContext context = parseContext(cleanJson);

            // ---- Semantic validation ----
            ValidationResult contextCheck = validator.validateContext(context);
            if (!contextCheck.valid()) {
                throw new IllegalArgumentException(contextCheck.summary());
            }

            // ---- Write back to shared state ----
            state.setRequirementContext(context);
            state.setStatus(ProjectState.PipelineStatus.IN_PROGRESS);

            eventLogger.publish(AGENT_NAME, EventType.REQUIREMENT_ANALYSIS_COMPLETED,
                    "RequirementContext parsed successfully. completionScore=" + context.completionScore()
                    + ", needsClarification=" + context.needsClarification());

            log.info("{} – Completed. completionScore={}", AGENT_NAME, context.completionScore());
            return AgentResult.success(AGENT_NAME, context);

        } catch (AiServiceException e) {
            log.error("{} – AiService call failed: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED,
                    "AiService failure: " + e.getMessage());
            return buildFailure("AI service failure: " + e.getMessage());

        } catch (JsonProcessingException e) {
            log.error("{} – JSON parsing failed: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED,
                    "JSON parsing failure: " + e.getMessage());
            return buildFailure("LLM returned invalid JSON: " + e.getOriginalMessage());

        } catch (IllegalArgumentException e) {
            log.error("{} – Context validation failed: {}", AGENT_NAME, e.getMessage());
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED,
                    "Context validation failure: " + e.getMessage());
            return buildFailure("Context validation failed: " + e.getMessage());

        } catch (Exception e) {
            log.error("{} – Unexpected error: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED,
                    "Unexpected error: " + e.getMessage());
            return buildFailure("Unexpected error during requirement analysis: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    /**
     * Builds the system prompt: persona definition + strict JSON constraint block.
     *
     * <p>The schema is injected verbatim so the LLM knows exactly what fields are
     * required. Temperature is already set low (0.2) in config to reduce creativity.
     */
    private String buildSystemPrompt() {
        return """
                You are a Senior Software Requirement Analyst with 15 years of experience \
                designing Software Requirements Specifications (SRS) for enterprise projects.

                Your task is to read a raw project description from a user and expand it into \
                a complete, structured requirements analysis.

                You must identify:
                - Primary user roles who will interact with the system
                - Core functional features the system must deliver
                - Non-functional requirements (performance, security, scalability, availability)
                - Reasonable assumptions made based on context
                - Known constraints (technology, legal, budget, time)
                - Open questions that need clarification before development begins
                - A completionScore (0.0–1.0) reflecting how complete the requirements are
                - Whether the project needs clarification before proceeding (needsClarification)

                ======================================================================
                CRITICAL INSTRUCTION: STRICT JSON OUTPUT REQUIRED
                ======================================================================

                You are functioning as a backend data-processing node.
                You MUST respond with pure, valid JSON.
                Your entire response will be parsed directly by a strict JSON parser (Jackson in Java).

                RULES:
                1. DO NOT wrap the JSON in markdown blocks (e.g., no ```json ... ```).
                2. DO NOT include any conversational text before or after the JSON.
                3. Ensure all keys and string values are properly escaped in double quotes.
                4. DO NOT use trailing commas in arrays or objects.
                5. Your output MUST exactly match the following JSON schema structure:

                {
                    "projectIdea": "The original user input string verbatim",
                    "executiveSummary": "1-2 paragraph professional summary of the project",
                    "userRoles": ["Role1", "Role2"],
                    "coreFeatures": ["Feature 1", "Feature 2", "Feature 3"],
                    "assumptions": ["Assumption 1", "Assumption 2"],
                    "constraints": ["Constraint 1", "Constraint 2"],
                    "nonFunctionalRequirements": ["NFR 1", "NFR 2"],
                    "openQuestions": ["Question 1?", "Question 2?"],
                    "completionScore": 0.85,
                    "needsClarification": false
                }

                If you fail to follow these rules, the pipeline will crash. Return ONLY the JSON object.
                """;
    }

    /**
     * Builds the user-turn prompt containing the raw project description.
     */
    private String buildUserPrompt(String rawInput) {
        return """
                Analyse the following project description and return a RequirementContext JSON object:

                PROJECT DESCRIPTION:
                %s

                Remember: respond with ONLY the JSON object. No markdown, no explanations.
                """.formatted(rawInput);
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
            // Remove opening fence (with optional "json" language tag)
            cleaned = cleaned.replaceFirst("^```(json)?\\s*", "");
            // Remove closing fence
            int lastFence = cleaned.lastIndexOf("```");
            if (lastFence != -1) {
                cleaned = cleaned.substring(0, lastFence).trim();
            }
        }
        return cleaned;
    }

    /**
     * Parses the cleaned JSON string into a {@link RequirementContext} record.
     *
     * @throws JsonProcessingException if the JSON is malformed or fields are missing
     */
    private RequirementContext parseContext(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, RequirementContext.class);
    }

    // -------------------------------------------------------------------------
    // Null Object factory
    // -------------------------------------------------------------------------

    /**
     * Builds a failed {@link AgentResult} with a safe empty {@link RequirementContext}
     * Null Object. The pipeline can continue without crashing.
     */
    private AgentResult<RequirementContext> buildFailure(String reason) {
        RequirementContext nullObject = new RequirementContext(
                "",                         // projectIdea
                "Analysis Failed",          // executiveSummary
                List.of(),                  // userRoles
                List.of(),                  // coreFeatures
                List.of(),                  // assumptions
                List.of(),                  // constraints
                List.of(),                  // nonFunctionalRequirements
                List.of(),                  // openQuestions
                0.0,                        // completionScore
                true                        // needsClarification
        );
        return AgentResult.failure(AGENT_NAME, nullObject, reason);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private String abbreviate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
