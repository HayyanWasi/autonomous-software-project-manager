package com.autonomouspm.context;

/**
 * Immutable error descriptor returned by the {@code ProjectPlannerValidator} when
 * a generated Work Breakdown Structure fails validation.
 *
 * <p>Spec: {@code specs/modules/project-planner.md §4 / §6}
 *
 * <p>Per spec, the validator never throws and never returns {@code null}: on
 * failure it logs warnings and surfaces one of these records so the agent can
 * fall back to the {@code EmptyGanttContext} Null Object without generating a
 * Gantt chart.
 *
 * @param agentName the name of the agent that produced the error
 *                  (e.g. {@code "ProjectPlannerAgent"})
 * @param errorCode a short, stable machine-readable code (e.g.
 *                  {@code "NO_PHASES"}, {@code "CIRCULAR_DEPENDENCY"})
 * @param message   a human-readable description of the validation failure
 */
public record PlanningValidationError(
        String agentName,
        String errorCode,
        String message
) {}
