package com.sda.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sda.agent.model.*;
import com.sda.agent.observer.EventLogger;
import com.sda.agent.tool.AiService;
import com.sda.agent.tool.MarketResearchTool;
import com.sda.agent.validation.BusinessAnalystValidator;
import org.springframework.stereotype.Component;

/**
 * Business Analyst Agent — second agent in the pipeline.
 *
 * Patterns applied:
 * - Chain of Responsibility : implements Agent<I,O> interface, receives input, passes output forward
 * - Adapter                 : uses AiService (not Ollama directly — decoupled via interface)
 * - Observer                : publishes progress events through EventLogger
 * - Null Object             : returns BusinessAnalysisValidationError instead of throwing or returning null
 *
 * Execution Order (per spec Section 10):
 * 1. Publish "Analyzing Business Requirements..."
 * 2. Run market research (use marketResearchTool) check business_research_tool.md
 * 3. Build LLM prompt with requirements + research
 * 4. Call LLM via AiService (Adapter)
 * 5. Parse JSON response into BusinessContext
 * 6. Validate output
 * 7. Return BusinessContext or BusinessAnalysisValidationError
 */
@Component
public class BusinessAnalystAgent implements Agent<RequirementContext, Object> {

    private static final String AGENT_NAME = "Business Analyst Agent";

    private final AiService aiService;
    private final MarketResearchTool marketResearchTool;
    private final BusinessAnalystValidator validator;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;

    public BusinessAnalystAgent(
        AiService aiService,
        MarketResearchTool marketResearchTool,
        BusinessAnalystValidator validator,
        EventLogger eventLogger,
        ObjectMapper objectMapper
    ) {
        this.aiService          = aiService;
        this.marketResearchTool = marketResearchTool;
        this.validator          = validator;
        this.eventLogger        = eventLogger;
        this.objectMapper       = objectMapper;
    }

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    /**
     * Main execution method.
     * Returns BusinessContext on success.
     * Returns BusinessAnalysisValidationError on validation failure.
     * Never returns null — Null Object pattern.
     */
    @Override
    public Object execute(RequirementContext input) {

        // Step 1: Signal start
        eventLogger.publish(AGENT_NAME, "Analyzing Business Requirements...");

        // Step 2: Run market research
        eventLogger.publish(AGENT_NAME, "Researching Market Trends...");
        String domain = extractDomain(input);
        String researchData = marketResearchTool.researchDomain(domain);
        boolean researchUsed = !researchData.contains("Insufficient Market Evidence");

        // Step 3: Analyze competitors
        eventLogger.publish(AGENT_NAME, "Analyzing Competitors...");

        // Step 4: Build the LLM prompt
        String systemPrompt = buildSystemPrompt();
        String userPrompt   = buildUserPrompt(input, researchData);

        // Step 5: Call LLM via Adapter
        eventLogger.publish(AGENT_NAME, "Generating User Stories...");
        String rawResponse = aiService.complete(systemPrompt, userPrompt);

        // Step 6: Parse response
        eventLogger.publish(AGENT_NAME, "Preparing Business Report...");
        BusinessContext context = parseResponse(rawResponse);

        // Step 7: Validate
        BusinessAnalysisValidationError error = validator.validate(context, input, researchUsed);

        if (error != null && error.hasViolations()) {
            eventLogger.publish(AGENT_NAME, "Validation FAILED: " + error.summary());
            return error; // Null Object pattern — safe failure, not null
        }

        eventLogger.publish(AGENT_NAME, "Business Analysis complete. Passing to Database Architect...");
        return context;
    }

    // ─────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Extract the domain keyword from the executive summary for market research.
     */
    private String extractDomain(RequirementContext input) {
        // Simple extraction — take first 6 words of executive summary
        String[] words = input.executiveSummary().split("\\s+");
        int limit = Math.min(words.length, 6);
        StringBuilder domain = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            domain.append(words[i]).append(" ");
        }
        return domain.toString().trim();
    }

    /**
     * System prompt — defines the LLM's role and strict rules.
     */
    private String buildSystemPrompt() {
        return """
            You are a Senior Business Analyst with 15 years of experience in software product analysis.

            Your job is to analyze software requirements and produce structured business analysis output.

            STRICT RULES YOU MUST FOLLOW:
            1. Only recommend features supported by the provided requirements or market research evidence.
            2. Never invent competitors, statistics, or user feedback. If market data is unavailable, state "Insufficient Market Evidence".
            3. Every UserStory must map to a real user role from the input.
            4. Every competitor insight must come from the provided research data — not your training data.
            5. Remove all duplicate user stories before returning.
            6. Your output MUST be a valid JSON object matching this exact structure:
            {
              "businessGoals": ["string"],
              "epics": ["string"],
              "userStories": [{"actor": "string", "action": "string", "benefit": "string"}],
              "marketPainPoints": ["string"],
              "competitorInsights": ["string"],
              "recommendedFeatures": ["string"],
              "validatedAssumptions": ["string"],
              "businessSummary": "string"
            }
            7. Return ONLY the JSON object. No preamble. No explanation. No markdown.
            """;
    }

    /**
     * User prompt — injects the actual requirements + research data.
     */
    private String buildUserPrompt(RequirementContext input, String researchData) {
        return """
            Analyze the following software project requirements and market research data.
            Produce business analysis output in the required JSON format.

            === EXECUTIVE SUMMARY ===
            %s

            === USER ROLES ===
            %s

            === CORE FEATURES ===
            %s

            === CONSTRAINTS ===
            %s

            === ASSUMPTIONS ===
            %s

            === NON-FUNCTIONAL REQUIREMENTS ===
            %s

            === MARKET RESEARCH DATA ===
            %s

            Produce the JSON output now.
            """.formatted(
                input.executiveSummary(),
                String.join("\n- ", input.userRoles()),
                String.join("\n- ", input.coreFeatures()),
                String.join("\n- ", input.constraints()),
                String.join("\n- ", input.assumptions()),
                String.join("\n- ", input.nonFunctionalRequirements()),
                researchData
            );
    }

    /**
     * Parses the LLM's raw JSON response into BusinessContext.
     * Strips markdown code fences if present (some models add them).
     */
    private BusinessContext parseResponse(String rawResponse) {
        String cleaned = rawResponse.trim();

        // Strip markdown JSON fences if present
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }

        try {
            return objectMapper.readValue(cleaned, BusinessContext.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                "BA Agent received invalid JSON from LLM.\nRaw response:\n" + rawResponse, e);
        }
    }
}
