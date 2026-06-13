package com.autonomouspm.core;

/**
 * Core agent interface. Every agent in the Chain of Responsibility must implement this contract.
 *
 * <p>Design patterns involved:
 * <ul>
 *   <li><b>Chain of Responsibility</b> — each agent processes the shared {@link ProjectState}
 *       and returns an {@link AgentResult}, which the {@code CentralOrchestrator} uses to
 *       advance the pipeline to the next agent.</li>
 *   <li><b>Adapter</b> — concrete implementations delegate AI calls through {@code AiService}
 *       rather than hitting external libraries directly.</li>
 * </ul>
 *
 * @param <O> the typed output context produced by this agent
 *            (e.g. {@code RequirementContext}, {@code BusinessContext}, …)
 */
public interface Agent<O> {

    /**
     * Unique, human-readable name for this agent.
     * Used for logging, event publishing, and orchestration routing.
     *
     * @return agent name (e.g. {@code "RequirementAnalystAgent"})
     */
    String getName();

    /**
     * Execute this agent against the current shared pipeline state.
     *
     * <p>Implementations must:
     * <ol>
     *   <li>Read required context fields from {@code state}.</li>
     *   <li>Publish progress events via {@code EventLogger} (Observer pattern).</li>
     *   <li>Delegate AI calls through {@code AiService} (Adapter pattern).</li>
     *   <li>Parse the LLM response into the typed output using Jackson.</li>
     *   <li>Return a successful {@link AgentResult} wrapping the output, or a failed
     *       {@link AgentResult} wrapping a {@code Null Object} on unrecoverable errors.</li>
     * </ol>
     *
     * <p>Implementations must <em>never</em> swallow exceptions silently.
     *
     * @param state the shared, mutable pipeline state
     * @return result containing the typed output or an error signal
     */
    AgentResult<O> execute(ProjectState state);
}
