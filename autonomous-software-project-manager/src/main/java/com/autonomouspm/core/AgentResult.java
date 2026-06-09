package com.autonomouspm.core;

/**
 * Immutable result envelope returned by every {@link Agent#execute(ProjectState)} call.
 *
 * <p>Follows the <b>Null Object</b> pattern convention: callers check {@link #isSuccess()}
 * before consuming {@link #output()}. When {@code isSuccess} is {@code false} the output
 * will be a Null Object instance — never a raw {@code null} — so the Builder pattern
 * further down the pipeline never crashes on a missing section.
 *
 * @param <O> the typed output context (e.g. {@code RequirementContext})
 */
public record AgentResult<O>(

        /**
         * Whether the agent completed successfully and produced usable output.
         */
        boolean isSuccess,

        /**
         * The typed output context produced by the agent.
         * On failure this must be a Null Object instance, never {@code null}.
         */
        O output,

        /**
         * Human-readable description of the failure. Empty string on success.
         */
        String errorMessage,

        /**
         * The name of the agent that produced this result.
         * Populated automatically by {@link #success} / {@link #failure} factories.
         */
        String agentName
) {

    // -------------------------------------------------------------------------
    // Factory constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a successful result carrying the given {@code output}.
     *
     * @param agentName the name of the producing agent
     * @param output    the non-null typed context
     * @param <O>       output type
     * @return a successful {@code AgentResult}
     */
    public static <O> AgentResult<O> success(String agentName, O output) {
        return new AgentResult<>(true, output, "", agentName);
    }

    /**
     * Creates a failed result carrying a Null Object placeholder.
     *
     * @param agentName    the name of the producing agent
     * @param nullObject   a Null Object implementing the expected output type
     * @param errorMessage description of the failure
     * @param <O>          output type
     * @return a failed {@code AgentResult}
     */
    public static <O> AgentResult<O> failure(String agentName, O nullObject, String errorMessage) {
        return new AgentResult<>(false, nullObject, errorMessage, agentName);
    }
}
