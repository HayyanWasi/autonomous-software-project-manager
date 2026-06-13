package com.autonomouspm.context;

/**
 * Immutable error descriptor returned by the {@code RiskAnalystValidator} when a
 * generated risk analysis fails validation.
 *
 * <p>Spec: {@code specs/modules/risk-analyst.md §5 / §6}
 *
 * <p>Per spec, the validator never throws and never returns {@code null}: on
 * failure it logs warnings and surfaces one of these records so the agent can
 * fall back to the {@code EmptyRiskContext} Null Object without accepting an
 * unverified analysis.
 *
 * @param agentName the name of the agent that produced the error
 *                  (e.g. {@code "RiskAnalystAgent"})
 * @param errorCode a short, stable machine-readable code (e.g.
 *                  {@code "NO_RISK_FACTORS"}, {@code "SCORE_MISMATCH"})
 * @param message   a human-readable description of the validation failure
 */
public record RiskAnalysisValidationError(
        String agentName,
        String errorCode,
        String message
) {}
