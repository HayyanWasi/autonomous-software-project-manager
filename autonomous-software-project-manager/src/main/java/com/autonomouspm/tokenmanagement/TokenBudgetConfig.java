package com.autonomouspm.tokenmanagement;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the per-agent output-token budgets defined under
 * the {@code token.budget.*} prefix in {@code application.properties}.
 *
 * <p>Spec: {@code specs/docs/token-management.md} (Step 3) — Output Token
 * Limiting. Each agent has a configured maximum output-token limit that the
 * Gemini call layer reads before invoking the LLM.
 *
 * <p>Kebab-case property keys bind to these record components automatically
 * (e.g. {@code token.budget.requirement-analyst} → {@link #requirementAnalyst()}).
 * Each component carries the spec default so the application still starts if a
 * key is omitted.
 *
 * <p>Registered as a Spring bean via {@code @EnableConfigurationProperties}.
 */
@ConfigurationProperties(prefix = "token.budget")
public record TokenBudgetConfig(

        /** Max output tokens for the Requirement Analyst Agent. */
        int requirementAnalyst,

        /** Max output tokens for the Business Analyst Agent. */
        int businessAnalyst,

        /** Max output tokens for the Database Architect Agent. */
        int databaseArchitect,

        /** Max output tokens for the Project Planner Agent. */
        int projectPlanner,

        /** Max output tokens for the Risk Analyst Agent. */
        int riskAnalyst

) {

    /** Spec defaults applied when a {@code token.budget.*} key is absent. */
    private static final int DEFAULT_REQUIREMENT_ANALYST = 800;
    private static final int DEFAULT_BUSINESS_ANALYST = 1200;
    private static final int DEFAULT_DATABASE_ARCHITECT = 1000;
    private static final int DEFAULT_PROJECT_PLANNER = 1500;
    private static final int DEFAULT_RISK_ANALYST = 800;

    /**
     * Canonical constructor that substitutes the spec default for any
     * non-positive (missing or invalid) budget value, guaranteeing every agent
     * always has a usable, positive limit.
     */
    public TokenBudgetConfig {
        requirementAnalyst = positiveOr(requirementAnalyst, DEFAULT_REQUIREMENT_ANALYST);
        businessAnalyst    = positiveOr(businessAnalyst, DEFAULT_BUSINESS_ANALYST);
        databaseArchitect  = positiveOr(databaseArchitect, DEFAULT_DATABASE_ARCHITECT);
        projectPlanner     = positiveOr(projectPlanner, DEFAULT_PROJECT_PLANNER);
        riskAnalyst        = positiveOr(riskAnalyst, DEFAULT_RISK_ANALYST);
    }

    /**
     * Resolves the configured output-token budget for an agent by its
     * {@link com.autonomouspm.core.Agent#getName() name}.
     *
     * <p>Falls back to {@code databaseArchitect} for any unrecognised name so a
     * misconfigured caller still receives a safe, bounded limit rather than zero.
     *
     * @param agentName the agent's {@code getName()} value
     * @return the output-token budget for that agent
     */
    public int forAgent(String agentName) {
        if (agentName == null) {
            return databaseArchitect;
        }
        return switch (agentName) {
            case "RequirementAnalystAgent" -> requirementAnalyst;
            case "BusinessAnalystAgent"    -> businessAnalyst;
            case "DatabaseArchitectAgent"  -> databaseArchitect;
            case "ProjectPlannerAgent"     -> projectPlanner;
            case "RiskAnalystAgent"        -> riskAnalyst;
            default -> databaseArchitect;
        };
    }

    private static int positiveOr(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
