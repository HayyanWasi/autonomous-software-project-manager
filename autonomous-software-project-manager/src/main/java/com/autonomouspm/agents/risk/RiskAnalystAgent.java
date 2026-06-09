package com.autonomouspm.agents.risk;

import com.autonomouspm.context.RiskAnalysisValidationError;
import com.autonomouspm.context.RiskContext;
import com.autonomouspm.context.RiskContext.RiskFactor;
import com.autonomouspm.core.Agent;
import com.autonomouspm.core.AgentResult;
import com.autonomouspm.core.ProjectState;
import com.autonomouspm.observer.EventLogger;
import com.autonomouspm.observer.EventType;
import com.autonomouspm.service.AiService.AiServiceException;
import com.autonomouspm.service.MarketResearchTool;
import com.autonomouspm.tokenmanagement.CachingAiService;
import com.autonomouspm.tokenmanagement.InputStripper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Fifth and final agent in the Chain of Responsibility.
 *
 * <p>Spec: {@code specs/modules/risk-analyst.md}
 *
 * <p>Researches <b>real-world</b> risks for the user's original project idea via
 * web search ({@link MarketResearchTool} / Tavily), then asks the LLM to distil
 * the findings into structured {@link RiskFactor}s and produces a
 * {@link RiskContext}. It does <em>not</em> analyse pipeline artifacts and does
 * <em>not</em> perform cost or budget forecasting (spec lead-in).
 *
 * <p><b>Input:</b> ONLY the original idea string, obtained via
 * {@link InputStripper#toRiskAnalystInput(ProjectState)} — never the full state.
 *
 * <p><b>Web search (spec §2):</b> exactly three Tavily searches are run via
 * {@link MarketResearchTool#search(String)} before the LLM is called. If all three
 * return the {@link MarketResearchTool#INSUFFICIENT_EVIDENCE} sentinel, the LLM
 * call is skipped and the {@code EmptyRiskContext} Null Object is returned.
 *
 * <p><b>Token management:</b> the single LLM call goes through
 * {@link CachingAiService} (never {@code AiService} directly) with the dedicated
 * {@code AGENT_NAME} and the 800-token output budget.
 *
 * <p><b>Design patterns:</b> Chain of Responsibility (final link), Adapter
 * (web search + LLM), Decorator (the caching service), Observer (events via
 * {@link EventLogger}), Null Object ({@code EmptyRiskContext} on failure).
 *
 * <p><b>Single Responsibility:</b> validation lives in
 * {@link RiskAnalystValidator}; this agent only orchestrates search, the LLM call,
 * parsing, scoring and event publishing.
 */
@Component
public class RiskAnalystAgent implements Agent<RiskContext> {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalystAgent.class);

    private static final String AGENT_NAME = "RiskAnalystAgent";

    /** Null Object overall risk level (spec §5). */
    private static final String UNKNOWN_LEVEL = "UNKNOWN";

    private final CachingAiService aiService;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;
    private final MarketResearchTool marketResearchTool;
    private final RiskAnalystValidator validator;

    /**
     * @param aiService          caching LLM decorator; injected by Spring
     * @param eventLogger        shared Observer bus; injected by Spring
     * @param objectMapper       shared Jackson mapper; injected by Spring
     * @param marketResearchTool Tavily-backed search tool (reused); injected by Spring
     * @param validator          risk-analysis validator; injected by Spring
     */
    public RiskAnalystAgent(CachingAiService aiService,
                            EventLogger eventLogger,
                            ObjectMapper objectMapper,
                            MarketResearchTool marketResearchTool,
                            RiskAnalystValidator validator) {
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
     * Executes risk analysis for the user's original project idea.
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Guard: a non-blank project idea must be available.</li>
     *   <li>Run exactly three Tavily searches (startup-risk, legal, technical).</li>
     *   <li>If all three are insufficient, skip the LLM and return the Null Object.</li>
     *   <li>Call the LLM via {@link CachingAiService}; strip markdown; parse.</li>
     *   <li>Validate via {@link RiskAnalystValidator}; on error, fall back to Null Object.</li>
     *   <li>Derive the overall score/level from the factors; write the context to {@code state}.</li>
     * </ol>
     *
     * @param state shared pipeline state
     * @return successful or failed {@link AgentResult}
     */
    @Override
    public AgentResult<RiskContext> execute(ProjectState state) {
        if (state == null) {
            return buildFailure("ProjectState is null. Cannot proceed.");
        }

        // ---- Input stripping: idea string only (spec §1, §8) ----
        String idea = InputStripper.toRiskAnalystInput(state);
        if (idea == null || idea.isBlank()) {
            return buildFailure("Project idea is missing. Risk analysis requires the original idea string.");
        }

        eventLogger.publish(AGENT_NAME, EventType.RISK_ANALYSIS_STARTED, "Researching Market Risks...");

        try {
            // ---- Exactly three Tavily searches (spec §2) ----
            String startupResearch = marketResearchTool.search(idea + " startup risks failures");

            eventLogger.publish(AGENT_NAME, EventType.DATABASE_RISKS_EVALUATING, "Analyzing Legal Risks...");
            String legalResearch = marketResearchTool.search(idea + " legal compliance challenges");

            eventLogger.publish(AGENT_NAME, EventType.SCHEDULE_RISKS_EVALUATING, "Analyzing Technical Risks...");
            String technicalResearch = marketResearchTool.search(idea + " technical challenges problems");

            // ---- All-insufficient short-circuit → Null Object (spec §2) ----
            boolean allInsufficient =
                    isInsufficient(startupResearch)
                            && isInsufficient(legalResearch)
                            && isInsufficient(technicalResearch);
            if (allInsufficient) {
                log.warn("{} – all three searches returned insufficient evidence; skipping LLM.", AGENT_NAME);
                eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED,
                        "No market evidence found; returning empty risk analysis.");
                return buildFailure("All risk searches returned insufficient evidence.");
            }

            // ---- Combine available results, labelled by search type ----
            String combined = combineResearch(startupResearch, legalResearch, technicalResearch);

            // ---- LLM call via caching decorator (never AiService directly) ----
            String systemPrompt = buildSystemPrompt(idea);
            String userPrompt   = combined;

            log.debug("{} – Calling CachingAiService", AGENT_NAME);
            String rawResponse = aiService.chat(systemPrompt, userPrompt, AGENT_NAME);
            if (rawResponse == null || rawResponse.isBlank()) {
                return buildFailure("LLM returned an empty response.");
            }

            // ---- Parse JSON → RiskContext ----
            RiskContext parsed = parseContext(stripMarkdown(rawResponse));

            eventLogger.publish(AGENT_NAME, EventType.RISK_SCORES_CALCULATING, "Calculating Risk Scores...");

            // ---- Validate (non-fatal; present error → Null Object) ----
            Optional<RiskAnalysisValidationError> error = validator.validate(parsed);
            if (error.isPresent()) {
                RiskAnalysisValidationError e = error.get();
                log.warn("{} – Risk validation failed [{}]: {}", AGENT_NAME, e.errorCode(), e.message());
                eventLogger.publish(AGENT_NAME, EventType.AGENT_FAILED,
                        "Risk validation failed: " + e.errorCode());
                return buildFailure("Risk validation failed [" + e.errorCode() + "]: " + e.message());
            }

            eventLogger.publish(AGENT_NAME, EventType.RISK_REPORT_FINALIZING, "Finalizing Risk Report...");

            // ---- Derive overall score & level from validated factors (spec §5) ----
            List<RiskFactor> factors = parsed.riskFactors();
            int overallScore = averageRiskScore(factors);
            String overallLevel = toRiskLevel(overallScore);
            RiskContext context = new RiskContext(
                    overallScore, overallLevel, factors, safe(parsed.conclusion()));

            // ---- Write back to shared state ----
            state.setRiskContext(context);
            state.setStatus(ProjectState.PipelineStatus.IN_PROGRESS);

            eventLogger.publish(AGENT_NAME, EventType.RISK_ANALYSIS_COMPLETED,
                    "RiskContext built. factors=" + factors.size()
                    + ", level=" + overallLevel + ", score=" + overallScore);

            log.info("{} – Completed. factors={}, level={}", AGENT_NAME, factors.size(), overallLevel);
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
            return buildFailure("Unexpected error during risk analysis: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Research combination
    // -------------------------------------------------------------------------

    /** @return {@code true} when the result is the insufficient-evidence sentinel. */
    private boolean isInsufficient(String result) {
        return result == null || MarketResearchTool.INSUFFICIENT_EVIDENCE.equals(result);
    }

    /**
     * Combines the three search results into one labelled string. Insufficient
     * sections are omitted so the LLM only sees real evidence (spec §2: proceed
     * with available results when only some are insufficient).
     */
    private String combineResearch(String startup, String legal, String technical) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "STARTUP RISKS & FAILURES", startup);
        appendSection(sb, "LEGAL & COMPLIANCE CHALLENGES", legal);
        appendSection(sb, "TECHNICAL CHALLENGES & PROBLEMS", technical);
        return sb.toString().trim();
    }

    private void appendSection(StringBuilder sb, String label, String content) {
        if (isInsufficient(content)) {
            return;
        }
        sb.append("### ").append(label).append('\n').append(content.trim()).append("\n\n");
    }

    // -------------------------------------------------------------------------
    // Scoring (spec §5)
    // -------------------------------------------------------------------------

    /**
     * Computes the overall risk score as the (rounded) average of the individual
     * factor risk scores. Returns 0 for an empty list (defensive; the validator
     * already guarantees ≥1 factor by this point).
     */
    private int averageRiskScore(List<RiskFactor> factors) {
        if (factors == null || factors.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (RiskFactor factor : factors) {
            sum += factor.riskScore();
        }
        return Math.round((float) sum / factors.size());
    }

    /**
     * Maps an average risk score to its level band (spec §5):
     * 1–8 LOW, 9–14 MEDIUM, 15–19 HIGH, 20–25 CRITICAL. A non-positive score
     * (no usable factors) maps to {@code UNKNOWN}.
     */
    private String toRiskLevel(int score) {
        if (score <= 0) {
            return UNKNOWN_LEVEL;
        }
        if (score <= 8) {
            return "LOW";
        }
        if (score <= 14) {
            return "MEDIUM";
        }
        if (score <= 19) {
            return "HIGH";
        }
        return "CRITICAL";
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    /**
     * Builds the system prompt: Senior Risk Analyst persona over the spec §3 base,
     * extended with the strict-JSON contract and {@link RiskContext} schema so the
     * response parses cleanly with Jackson. The {@code overallRiskScore} /
     * {@code overallRiskLevel} are recomputed in Java, so the LLM is told to focus
     * on the factors.
     */
    private String buildSystemPrompt(String idea) {
        return """
                You are a Senior Risk Analyst. Analyze the following real-world research about \
                "%s" and identify 4-6 concrete risks. Return strict JSON only — no markdown, no preamble.

                For each risk:
                - "category" MUST be exactly one of: Technical, Legal, Market, Resource.
                - "evidence" MUST be grounded in the provided research — do NOT invent facts.
                - "impactLevel" and "probabilityLevel" are integers from 1 to 5.
                - "riskScore" MUST equal impactLevel × probabilityLevel.
                - Provide a concrete "mitigationStrategy" for every risk.

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
                5. Your output MUST match this JSON schema structure:

                {
                    "overallRiskScore": 0,
                    "overallRiskLevel": "MEDIUM",
                    "riskFactors": [
                        {
                            "category": "Legal",
                            "description": "Regulatory compliance burden for handling user data",
                            "evidence": "Research indicates frequent GDPR-related fines in this sector",
                            "mitigationStrategy": "Engage compliance counsel early and adopt privacy-by-design",
                            "impactLevel": 4,
                            "probabilityLevel": 3,
                            "riskScore": 12
                        }
                    ],
                    "conclusion": "A concise overall assessment and recommended next steps."
                }

                If you fail to follow these rules, the pipeline will crash. Return ONLY the JSON object.
                """.formatted(idea);
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
     * Parses the cleaned JSON string into a {@link RiskContext} record.
     *
     * @throws JsonProcessingException if the JSON is malformed
     */
    private RiskContext parseContext(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, RiskContext.class);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    // -------------------------------------------------------------------------
    // Null Object factory
    // -------------------------------------------------------------------------

    /**
     * Builds a failed {@link AgentResult} with a safe empty {@link RiskContext}
     * (the {@code EmptyRiskContext} Null Object per spec §5: zero score,
     * {@code UNKNOWN} level, no factors, blank conclusion), so the pipeline can
     * continue without crashing.
     */
    private AgentResult<RiskContext> buildFailure(String reason) {
        RiskContext nullObject = new RiskContext(0, UNKNOWN_LEVEL, List.of(), "");
        return AgentResult.failure(AGENT_NAME, nullObject, reason);
    }
}
