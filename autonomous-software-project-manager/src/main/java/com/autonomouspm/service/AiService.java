package com.autonomouspm.service;

/**
 * Adapter interface for all AI language-model interactions.
 *
 * <p>Implements the <b>Adapter</b> design pattern: concrete implementations
 * wrap LangChain4j {@code OpenAiChatModel} (configured for OpenRouter) so that
 * every agent calls this clean interface instead of the external library directly.
 * Swapping the underlying LLM provider requires only a new implementation of
 * this interface — agent code remains unchanged.
 *
 * <p>Design patterns: <b>Adapter</b>, <b>Bridge</b> (separates the what-to-ask
 * abstraction from the how-to-call execution mechanism).
 *
 * <p>Registered as a Spring-managed bean; injected into agents via constructor
 * injection.
 */
public interface AiService {

    /**
     * Sends a structured prompt to the configured LLM and returns the raw text response.
     *
     * <p>Implementations must:
     * <ul>
     *   <li>Prepend the {@code systemPrompt} as the system message.</li>
     *   <li>Send the {@code userPrompt} as the user turn.</li>
     *   <li>Return the model's plain-text reply for Jackson parsing by the caller.</li>
     *   <li>Never return {@code null}. On failure, throw a checked or unchecked
     *       exception; do not silently return an empty string.</li>
     * </ul>
     *
     * @param systemPrompt instruction that defines the LLM's persona and output format
     * @param userPrompt   the task-specific prompt built from the current
     *                     {@code ProjectState} context
     * @return raw LLM response text (expected to be valid JSON for agent parsing)
     * @throws AiServiceException if the LLM call fails or returns an unusable response
     */
    String chat(String systemPrompt, String userPrompt);

    // -------------------------------------------------------------------------
    // Exception type
    // -------------------------------------------------------------------------

    /**
     * Unchecked exception thrown when an AI service call cannot be completed.
     *
     * <p>Callers should catch this and trigger the <b>Null Object</b> fallback
     * rather than propagating the exception up the pipeline.
     */
    class AiServiceException extends RuntimeException {

        public AiServiceException(String message) {
            super(message);
        }

        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
