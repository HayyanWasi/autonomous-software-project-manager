package com.autonomouspm.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Immutable output context produced by the <b>Risk Analyst Agent</b>, the final
 * agent in the Chain of Responsibility.
 *
 * <p>Spec: {@code specs/modules/risk-analyst.md §5 Records}
 *
 * <p>The Risk Analyst researches <b>real-world</b> risks for the user's original
 * project idea via web search ({@code MarketResearchTool} / Tavily) and asks the
 * LLM to distil them into structured factors. It does <em>not</em> analyse
 * pipeline artifacts and does <em>not</em> perform cost or budget forecasting.
 *
 * <p>Risk Score Formula: {@code riskScore = impactLevel × probabilityLevel}
 * (both on a 1–5 scale; maximum score per factor = 25).
 *
 * <p>Overall project risk classification, derived from the <b>average</b>
 * {@code riskScore} across all {@link RiskFactor}s (spec §5):
 * <ul>
 *   <li>1–8   → LOW</li>
 *   <li>9–14  → MEDIUM</li>
 *   <li>15–19 → HIGH</li>
 *   <li>20–25 → CRITICAL</li>
 * </ul>
 *
 * <p>After successful generation, {@code CentralOrchestrator} triggers
 * {@code ProjectReportBuilder} (Builder pattern) to compile the final report.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskContext(

        /**
         * Aggregate project risk score derived from all identified {@link RiskFactor}s
         * (the average of their individual risk scores), not chosen arbitrarily.
         */
        int overallRiskScore,

        /** One of: {@code LOW}, {@code MEDIUM}, {@code HIGH}, {@code CRITICAL}. */
        String overallRiskLevel,

        /** All identified risk factors, each backed by web-search evidence. */
        List<RiskFactor> riskFactors,

        /** Prose conclusion and recommended next steps. */
        String conclusion
) {

    /**
     * A single, evidence-backed risk item.
     *
     * <p>Every {@code RiskFactor} must:
     * <ul>
     *   <li>Quote concrete evidence drawn from the web-search results.</li>
     *   <li>Include a mitigation strategy.</li>
     *   <li>Have {@code riskScore == impactLevel × probabilityLevel}.</li>
     * </ul>
     *
     * <p>Spec: {@code specs/modules/risk-analyst.md §5 RiskFactor}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskFactor(

            /**
             * Risk category. One of: {@code Technical}, {@code Legal},
             * {@code Market}, {@code Resource}.
             */
            String category,

            /** Human-readable description of the risk. */
            String description,

            /**
             * Traceable evidence taken from the {@code MarketResearchTool} search
             * results — never fabricated by the LLM.
             */
            String evidence,

            /** Recommended action to reduce or eliminate the risk. */
            String mitigationStrategy,

            /**
             * Impact level: 1 (Negligible) → 5 (Critical).
             */
            int impactLevel,

            /**
             * Probability level: 1 (Rare) → 5 (Very Likely).
             */
            int probabilityLevel,

            /**
             * Computed as {@code impactLevel × probabilityLevel}.
             * Validated before the context is accepted by the orchestrator.
             */
            int riskScore
    ) {}
}
