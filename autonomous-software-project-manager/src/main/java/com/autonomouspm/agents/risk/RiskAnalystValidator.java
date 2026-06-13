package com.autonomouspm.agents.risk;

import com.autonomouspm.context.RiskAnalysisValidationError;
import com.autonomouspm.context.RiskContext;
import com.autonomouspm.context.RiskContext.RiskFactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates a {@link RiskContext} produced by the Risk Analyst Agent before it is
 * accepted into the pipeline.
 *
 * <p>Spec: {@code specs/modules/risk-analyst.md §6 Validation Rules}.
 *
 * <p><b>Non-fatal contract:</b> this validator never throws and never returns
 * {@code null}. On failure it logs warnings via SLF4J and returns an
 * {@link java.util.Optional} carrying a single {@link RiskAnalysisValidationError};
 * on success it returns {@link java.util.Optional#empty()}. The agent treats a
 * present error as the signal to fall back to the {@code EmptyRiskContext} Null
 * Object instead of accepting an unverified analysis.
 *
 * <p>Validation rules (spec §6):
 * <ul>
 *   <li>At least one {@link RiskFactor} exists.</li>
 *   <li>Every factor has non-empty {@code evidence}.</li>
 *   <li>Every factor has non-empty {@code mitigationStrategy}.</li>
 *   <li>Every factor's {@code riskScore == impactLevel × probabilityLevel}.</li>
 *   <li>No duplicate risk descriptions.</li>
 *   <li>{@code impactLevel} and {@code probabilityLevel} are between 1 and 5.</li>
 * </ul>
 *
 * <p>Holds no mutable state — only immutable rule constants — so it is
 * thread-safe and freely shareable as a Spring {@code @Component}.
 */
@Component
public class RiskAnalystValidator {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalystValidator.class);

    private static final String AGENT_NAME = "RiskAnalystAgent";

    /** Inclusive lower bound for impact / probability levels. */
    private static final int MIN_LEVEL = 1;

    /** Inclusive upper bound for impact / probability levels. */
    private static final int MAX_LEVEL = 5;

    /**
     * Validates the given risk context.
     *
     * @param context the parsed risk context produced by the agent (may be {@code null})
     * @return {@link java.util.Optional#empty()} when valid; otherwise an
     *         {@link java.util.Optional} carrying the first
     *         {@link RiskAnalysisValidationError} encountered. Never {@code null}.
     */
    public java.util.Optional<RiskAnalysisValidationError> validate(RiskContext context) {
        if (context == null) {
            return fail("NULL_CONTEXT", "RiskContext is null. Cannot validate a missing analysis.");
        }

        List<RiskFactor> factors = context.riskFactors();

        // --- Rule: at least one risk factor ---
        if (factors == null || factors.isEmpty()) {
            return fail("NO_RISK_FACTORS", "Risk analysis contains no risk factors.");
        }

        Set<String> seenDescriptions = new HashSet<>();
        for (int i = 0; i < factors.size(); i++) {
            RiskFactor factor = factors.get(i);

            if (factor == null) {
                return fail("NULL_RISK_FACTOR", "Risk factor at index " + i + " is null.");
            }

            // --- Rule: non-empty evidence ---
            if (isBlank(factor.evidence())) {
                return fail("MISSING_EVIDENCE",
                        "Risk factor '" + safe(factor.description()) + "' has no evidence.");
            }

            // --- Rule: non-empty mitigation strategy ---
            if (isBlank(factor.mitigationStrategy())) {
                return fail("MISSING_MITIGATION",
                        "Risk factor '" + safe(factor.description()) + "' has no mitigation strategy.");
            }

            // --- Rule: impact level within 1..5 ---
            if (factor.impactLevel() < MIN_LEVEL || factor.impactLevel() > MAX_LEVEL) {
                return fail("IMPACT_OUT_OF_RANGE",
                        "Risk factor '" + safe(factor.description()) + "' has impactLevel "
                                + factor.impactLevel() + " (expected " + MIN_LEVEL + "–" + MAX_LEVEL + ").");
            }

            // --- Rule: probability level within 1..5 ---
            if (factor.probabilityLevel() < MIN_LEVEL || factor.probabilityLevel() > MAX_LEVEL) {
                return fail("PROBABILITY_OUT_OF_RANGE",
                        "Risk factor '" + safe(factor.description()) + "' has probabilityLevel "
                                + factor.probabilityLevel() + " (expected " + MIN_LEVEL + "–" + MAX_LEVEL + ").");
            }

            // --- Rule: riskScore == impactLevel × probabilityLevel ---
            int expected = factor.impactLevel() * factor.probabilityLevel();
            if (factor.riskScore() != expected) {
                return fail("SCORE_MISMATCH",
                        "Risk factor '" + safe(factor.description()) + "' has riskScore "
                                + factor.riskScore() + " but impactLevel × probabilityLevel = " + expected + ".");
            }

            // --- Rule: no duplicate descriptions (case-insensitive) ---
            String descKey = factor.description() == null
                    ? "" : factor.description().trim().toLowerCase(Locale.ROOT);
            if (!seenDescriptions.add(descKey)) {
                return fail("DUPLICATE_DESCRIPTION",
                        "Duplicate risk description: '" + safe(factor.description()) + "'.");
            }
        }

        log.debug("RiskAnalystValidator – analysis valid: {} risk factor(s).", factors.size());
        return java.util.Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Logs the violation and wraps it in a {@link RiskAnalysisValidationError}.
     * Central helper so every rule logs consistently and returns a non-null
     * {@link java.util.Optional}.
     */
    private java.util.Optional<RiskAnalysisValidationError> fail(String errorCode, String message) {
        log.warn("RiskAnalystValidator – {} : {}", errorCode, message);
        return java.util.Optional.of(new RiskAnalysisValidationError(AGENT_NAME, errorCode, message));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String name) {
        return (name == null || name.isBlank()) ? "<no description>" : name.trim();
    }
}
