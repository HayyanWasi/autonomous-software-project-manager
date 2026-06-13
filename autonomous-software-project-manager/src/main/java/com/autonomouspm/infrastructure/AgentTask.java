package com.autonomouspm.infrastructure;

import com.autonomouspm.core.ProjectState;

/**
 * BRIDGE PATTERN — Abstraction.
 *
 * <p>Spec: {@code specs/modules/central-orchestrator-agent.md §3 File 1}
 *
 * <p>Separates a pipeline task's <em>definition</em> from its <em>execution
 * mechanism</em>. The {@link com.autonomouspm.core.CentralOrchestrator} works with
 * {@code AgentTask} only — it never depends on how the work is actually carried
 * out internally (LLM agent, rule-based engine, remote call, …). Swapping the
 * concrete implementation (see {@link LlmAgentTask}) requires no change to the
 * orchestrator.
 *
 * @param <T> the typed output context produced by the task
 *            (e.g. {@code RequirementContext}, {@code RiskContext})
 */
public interface AgentTask<T> {

    /**
     * Executes this task against the shared pipeline state.
     *
     * @param state the shared, mutable pipeline state
     * @return the typed output context (a Null Object on failure, never {@code null})
     */
    T execute(ProjectState state);

    /**
     * @return the human-readable agent name (e.g. {@code "RequirementAnalystAgent"})
     */
    String agentName();

    /**
     * @return the SSE event name to emit on completion (e.g. {@code "requirement-complete"})
     */
    String eventName();
}
