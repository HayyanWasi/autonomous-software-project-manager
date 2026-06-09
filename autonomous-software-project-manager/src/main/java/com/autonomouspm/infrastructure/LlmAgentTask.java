package com.autonomouspm.infrastructure;

import com.autonomouspm.core.Agent;
import com.autonomouspm.core.AgentResult;
import com.autonomouspm.core.ProjectState;

/**
 * BRIDGE PATTERN — Concrete Implementation.
 *
 * <p>Spec: {@code specs/modules/central-orchestrator-agent.md §3 File 1}
 *
 * <p>Executes an {@link Agent} against the shared {@link ProjectState} on behalf of
 * the {@link com.autonomouspm.core.CentralOrchestrator}. Because the orchestrator
 * depends only on the {@link AgentTask} abstraction, this implementation can later
 * be swapped (e.g. for a {@code RuleBasedAgentTask}) without touching the
 * orchestrator.
 *
 * <p><b>Spec adaptation (intentional):</b> the spec sketch calls
 * {@code agent.process(state)} returning the bare context {@code T}. The real
 * {@link Agent} contract in this codebase is {@link Agent#execute(ProjectState)},
 * which returns an {@link AgentResult}{@code <T>}. This class therefore calls
 * {@code execute(...)} and returns {@link AgentResult#output()} — the typed output
 * context, which is a <em>Null Object</em> (never {@code null}) on failure. That
 * is exactly what the orchestrator's Null-Object detection inspects
 * (e.g. {@code tables.isEmpty()}), so the behaviour matches the spec's intent.
 *
 * @param <T> the typed output context produced by the wrapped agent
 */
public class LlmAgentTask<T> implements AgentTask<T> {

    private final Agent<T> agent;
    private final String agentName;
    private final String eventName;

    /**
     * @param agent     the agent to execute
     * @param agentName the human-readable agent name (used in events/logging)
     * @param eventName the SSE event name to emit on completion
     */
    public LlmAgentTask(Agent<T> agent, String agentName, String eventName) {
        this.agent = agent;
        this.agentName = agentName;
        this.eventName = eventName;
    }

    /**
     * Executes the wrapped agent and returns its output context.
     *
     * @param state the shared pipeline state
     * @return the typed output context (a Null Object on failure, never {@code null})
     */
    @Override
    public T execute(ProjectState state) {
        AgentResult<T> result = agent.execute(state);
        return result.output();
    }

    @Override
    public String agentName() {
        return agentName;
    }

    @Override
    public String eventName() {
        return eventName;
    }
}
