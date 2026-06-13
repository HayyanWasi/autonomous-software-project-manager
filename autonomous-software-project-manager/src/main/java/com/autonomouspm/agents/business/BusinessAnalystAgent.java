package com.autonomouspm.agents.business;

import com.autonomouspm.agents.business.BusinessAnalystValidator.ValidationResult;
import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.BusinessContext.UserStory;
import com.autonomouspm.context.RequirementContext;
import com.autonomouspm.core.Agent;
import com.autonomouspm.core.AgentResult;
import com.autonomouspm.core.ProjectState;
import com.autonomouspm.observer.EventLogger;
import com.autonomouspm.observer.EventType;
import com.autonomouspm.service.AiService;
import com.autonomouspm.service.AiService.AiServiceException;
import com.autonomouspm.service.MarketResearchTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Second agent in the Chain of Responsibility.
 *
 * <p>Spec: {@code specs/modules/business-analyst.md}
 *
 * <p>Consumes the {@link RequirementContext} produced by the Requirement Analyst,
 * enriches it with real market evidence from {@link MarketResearchTool}, and asks
 * the LLM (via {@link AiService}) to produce a structured {@link BusinessContext}.
 *
 * <p><b>Design patterns:</b>
 * <ul>
 *   <li><b>Chain of Responsibility</b> — second link; writes its context back to
 *       {@link ProjectState} for the Database Architect.</li>
 *   <li><b>Adapter</b> — all AI calls go through {@link AiService}; all web
 *       research through {@link MarketResearchTool}.</li>
 *   <li><b>Observer</b> — publishes lifecycle events via {@link EventLogger}.</li>
 *   <li><b>Null Object</b> — on any failure returns a safe empty
 *       {@link BusinessContext}.</li>
 * </ul>
 *
 * <p><b>Single Responsibility:</b> all validation lives in
 * {@link BusinessAnalystValidator}; this agent only orchestrates research, the LLM
 * call, JSON parsing, deduplication, state mutation and event publishing.
 */
@Component
public class BusinessAnalystAgent implements Agent<BusinessContext> {

    private static final Logger log = LoggerFactory.getLogger(BusinessAnalystAgent.class);

    private static final String AGENT_NAME = "BusinessAnalystAgent";

    private final AiService aiService;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;
    private final MarketResearchTool marketResearchTool;
    private final BusinessAnalystValidator validator;

    /**
     * @param aiService          LangChain4j Gemini adapter; injected by Spring
     * @param eventLogger        shared Observer bus; injected by Spring
     * @param objectMapper       shared Jackson mapper; injected by Spring
     * @param marketResearchTool Tavily-backed research tool; injected by Spring
     * @param validator          business-context validation rules; injected by Spring
     */
    public BusinessAnalystAgent(AiService aiService,
                                EventLogger eventLogger,
                                ObjectMapper objectMapper,
                                MarketResearchTool marketResearchTool,
                                BusinessAnalystValidator validator) {
        this.aiService          = aiService;
        this.eventLogger        = eventLogger;
        this.objectMapper       = objectMapper;
        this.marketResearchTool = marketResearchTool;
        this.validator          = validator;
    }

    // -------------------------------------------------------------------------
    // Agent contract
    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    /**
     * Executes business analysis against the {@link RequirementContext} held in
     * {@code state}.
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Guard: a successful RequirementContext must be present.</li>
     *   <li>Publish {@link EventType#BUSINESS_ANALYSIS_STARTED}.</li>
     *   <li>Run market research over the project domain (never throws).</li>
     *   <li>Build system + user prompts embedding requirements and research.</li>
     *   <li>Call {@link AiService#chat}, strip markdown, parse to {@link BusinessContext}.</li>
     *   <li>Deduplicate user stories.</li>
     *   <li>Validate (structure + hallucination contract).</li>
     *   <li>Write context to {@code state}, publish completion.</li>
     * </ol>
     *
     * @param state shared pipeline state
     * @return successful or failed {@link AgentResult}
     */
    @Override
    public AgentResult<BusinessContext> execute(ProjectState state) {
        if (state == null) {
            return buildFailure("ProjectState is null. Cannot proceed.");
        }

        RequirementContext requirements = state.getRequirementContext();
        if (requirements == null) {
            return buildFailure("RequirementContext is missing. Business analysis requires upstream requirements.");
        }

        eventLogger.publish(AGENT_NAME, EventType.BUSINESS_ANALYSIS_STARTED,
                "Analyzing Business Requirements...");

        try {
            // ---- Market research (Adapter; never throws) ----
            String domain = resolveDomain(requirements);

            eventLogger.publish(AGENT_NAME, EventType.MARKET_RESEARCH_STARTED,
                    "Researching Market Trends...");
            String research = marketResearchTool.researchDomain(domain);
            boolean researchAvailable = !MarketResearchTool.INSUFFICIENT_EVIDENCE.equals(research);

            eventLogger.publish(AGENT_NAME, EventType.COMPETITOR_ANALYSIS_STARTED,
                    "Analyzing Competitors...");

            // ---- Build prompts ----
            String requirementsJson = objectMapper.writeValueAsString(requirements);
            String systemPrompt = buildSystemPrompt();
            String userPrompt   = buildUserPrompt(requirementsJson, research);

            eventLogger.publish(AGENT_NAME, EventType.USER_STORIES_GENERATING,
                    "Generating User Stories...");

            log.debug("{} – Calling AiService (researchAvailable={})", AGENT_NAME, researchAvailable);

            // ---- LLM call ----
            String rawResponse = aiService.chat(systemPrompt, userPrompt);
            if (rawResponse == null || rawResponse.isBlank()) {
                return buildFailure("LLM returned an empty response.");
            }

            // ---- Parse JSON → BusinessContext ----
            BusinessContext parsed = parseContext(stripMarkdown(rawResponse));

            // ---- Deduplicate user stories (spec §7) ----
            BusinessContext context = withDistinctUserStories(parsed);

            eventLogger.publish(AGENT_NAME, EventType.BUSINESS_REPORT_PREPARING,
                    "Preparing Business Report...");

            // ---- Validation ----
            ValidationResult validation = validator.validateContext(context, researchAvailable);
            if (!validation.valid()) {
                throw new IllegalArgumentException(validation.summary());
            }

            // ---- Write back to shared state ----
            state.setBusinessContext(context);
            state.setStatus(ProjectState.PipelineStatus.IN_PROGRESS);

            eventLogger.publish(AGENT_NAME, EventType.BUSINESS_ANALYSIS_COMPLETED,
                    "BusinessContext parsed successfully. userStories=" + context.userStories().size()
                    + ", recommendedFeatures=" + context.recommendedFeatures().size());

            log.info("{} – Completed. userStories={}", AGENT_NAME, context.userStories().size());
            return AgentResult.success(AGENT_NAME, context);

        } catch (AiServiceException e) {
            log.error("{} – AiService call failed: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED, "AiService failure: " + e.getMessage());
            return buildFailure("AI service failure: " + e.getMessage());

        } catch (JsonProcessingException e) {
            log.error("{} – JSON parsing failed: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED, "JSON parsing failure: " + e.getMessage());
            return buildFailure("LLM returned invalid JSON: " + e.getOriginalMessage());

        } catch (IllegalArgumentException e) {
            log.error("{} – Context validation failed: {}", AGENT_NAME, e.getMessage());
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED, "Context validation failure: " + e.getMessage());
            return buildFailure("Context validation failed: " + e.getMessage());

        } catch (Exception e) {
            log.error("{} – Unexpected error: {}", AGENT_NAME, e.getMessage(), e);
            eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED, "Unexpected error: " + e.getMessage());
            return buildFailure("Unexpected error during business analysis: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    /**
     * Builds the system prompt: Senior Business Analyst persona, hallucination
     * guardrails, and the strict JSON constraint block.
     */
    private String buildSystemPrompt() {
        return """
                You are a Senior Business Analyst with deep experience turning software \
                requirements into actionable business artifacts: business goals, epics, \
                user stories, and market-driven feature recommendations.

                You will be given a RequirementContext (as JSON) and a MARKET RESEARCH \
                block containing real, externally-sourced evidence.

                Your tasks:
                - Identify high-level business goals derived from the requirements.
                - Group features into epics.
                - Write detailed user stories in actor / action / benefit form.
                - Extract market pain points ONLY from the research block.
                - Derive competitor insights ONLY from the research block.
                - Recommend high-value features supported by requirements or research.
                - Validate which requirement assumptions the research supports.
                - Write a concise businessSummary.

                ======================================================================
                EVIDENCE & HALLUCINATION RULES (MANDATORY)
                ======================================================================
                - DO NOT invent competitors, statistics, customer feedback, or trends.
                - "marketPainPoints" and "competitorInsights" MUST be derived ONLY from
                  the MARKET RESEARCH block.
                - If the MARKET RESEARCH block is exactly "Insufficient Market Evidence",
                  you MUST return an empty array [] for both "marketPainPoints" and
                  "competitorInsights". Do not fabricate them.
                - Every user story must trace to a requirement. Remove duplicates.

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
                5. Your output MUST exactly match this JSON schema structure:

                {
                    "businessGoals": ["Goal 1", "Goal 2"],
                    "epics": ["Epic 1", "Epic 2"],
                    "userStories": [
                        {"actor": "Customer", "action": "Track my order", "benefit": "Know estimated delivery time"}
                    ],
                    "marketPainPoints": ["Pain point 1"],
                    "competitorInsights": ["Insight 1"],
                    "recommendedFeatures": ["Feature 1"],
                    "validatedAssumptions": ["Assumption 1"],
                    "businessSummary": "1-2 paragraph professional summary"
                }

                If you fail to follow these rules, the pipeline will crash. Return ONLY the JSON object.
                """;
    }

    /**
     * Builds the user-turn prompt embedding the requirement JSON and the research block.
     */
    private String buildUserPrompt(String requirementsJson, String research) {
        return """
                Produce a BusinessContext JSON object for the project below.

                REQUIREMENT CONTEXT (JSON):
                %s

                MARKET RESEARCH:
                %s

                Remember: respond with ONLY the JSON object. No markdown, no explanations.
                """.formatted(requirementsJson, research);
    }

    // -------------------------------------------------------------------------
    // Parsing & transformation
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
     * Parses the cleaned JSON string into a {@link BusinessContext} record.
     *
     * @throws JsonProcessingException if the JSON is malformed or fields are missing
     */
    private BusinessContext parseContext(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, BusinessContext.class);
    }

    /**
     * Returns a copy of {@code context} with duplicate user stories removed,
     * preserving order. Records compare by value, so {@link LinkedHashSet} dedups
     * structurally identical stories. Other fields are passed through unchanged.
     */
    private BusinessContext withDistinctUserStories(BusinessContext context) {
        List<UserStory> stories = context.userStories();
        if (stories == null || stories.isEmpty()) {
            return context;
        }
        Set<UserStory> distinct = new LinkedHashSet<>(stories);
        if (distinct.size() == stories.size()) {
            return context;
        }
        return new BusinessContext(
                context.businessGoals(),
                context.epics(),
                new ArrayList<>(distinct),
                context.marketPainPoints(),
                context.competitorInsights(),
                context.recommendedFeatures(),
                context.validatedAssumptions(),
                context.businessSummary()
        );
    }

    /**
     * Resolves the domain string used for market research, preferring the concise
     * project idea and falling back to the executive summary.
     */
    private String resolveDomain(RequirementContext requirements) {
        if (requirements.projectIdea() != null && !requirements.projectIdea().isBlank()) {
            return requirements.projectIdea();
        }
        return requirements.executiveSummary() == null ? "" : requirements.executiveSummary();
    }

    // -------------------------------------------------------------------------
    // Null Object factory
    // -------------------------------------------------------------------------

    /**
     * Builds a failed {@link AgentResult} with a safe empty {@link BusinessContext}
     * Null Object so the pipeline can continue without crashing.
     */
    private AgentResult<BusinessContext> buildFailure(String reason) {
        BusinessContext nullObject = new BusinessContext(
                List.of(),                  // businessGoals
                List.of(),                  // epics
                List.of(),                  // userStories
                List.of(),                  // marketPainPoints
                List.of(),                  // competitorInsights
                List.of(),                  // recommendedFeatures
                List.of(),                  // validatedAssumptions
                "Business Analysis Failed"  // businessSummary
        );
        return AgentResult.failure(AGENT_NAME, nullObject, reason);
    }
}
