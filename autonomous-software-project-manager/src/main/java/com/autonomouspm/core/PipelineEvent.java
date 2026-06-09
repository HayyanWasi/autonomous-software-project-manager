package com.autonomouspm.core;

/**
 * A single Server-Sent Event (SSE) payload streamed to the React UI after each
 * pipeline agent completes.
 *
 * <p>Spec: {@code specs/modules/central-orchestrator-agent.md §2 SSE Event Types}
 *
 * <p>The {@link CentralOrchestrator} emits one of these immediately after every
 * agent finishes (and once more for the terminal {@code pipeline-complete} /
 * {@code pipeline-failed} events), serialised to JSON by Jackson and sent over an
 * {@code SseEmitter}. React listens per {@link #eventName()} and renders each
 * agent's section as it arrives.
 *
 * <p><b>Event names</b> — the orchestrator uses exactly these strings, which the
 * front-end registers as {@code EventSource} listeners:
 * <ul>
 *   <li>{@code "requirement-complete"}</li>
 *   <li>{@code "business-complete"}</li>
 *   <li>{@code "database-complete"}</li>
 *   <li>{@code "planner-complete"}</li>
 *   <li>{@code "risk-complete"}</li>
 *   <li>{@code "pipeline-complete"}</li>
 *   <li>{@code "pipeline-failed"}</li>
 * </ul>
 *
 * @param eventName the SSE event name (one of the strings listed above)
 * @param status    lifecycle status: {@code COMPLETED}, {@code FAILED}, or {@code PARTIAL}
 * @param data      the payload — typically the agent's output context record
 *                  (serialised as JSON), or the final report string on completion
 * @param message   a short human-readable description (e.g. "Requirements analyzed")
 */
public record PipelineEvent(
        String eventName,
        String status,
        Object data,
        String message
) {}
