package com.autonomouspm.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Concrete implementation of {@link AiService} that adapts the LangChain4j
 * {@link OpenAiChatModel} (configured for OpenRouter) to our internal interface.
 *
 * <p><b>Adapter pattern</b>: this class is the only place in the codebase that
 * directly uses LangChain4j classes. Agents only ever depend on {@link AiService},
 * so swapping the underlying model or provider requires only a new implementation
 * of that interface — zero changes in agent code.
 *
 * <p><b>Error handling strategy</b>:
 * <ul>
 *   <li>Empty or blank responses → {@link AiServiceException}</li>
 *   <li>API timeout ({@code OpenAiHttpException} with 408 / 504) → {@link AiServiceException}</li>
 *   <li>Any other runtime exception from LangChain4j → wrapped in {@link AiServiceException}</li>
 * </ul>
 *
 * <p>This class is <em>not</em> annotated with {@code @Component}; it is registered
 * as a Spring bean explicitly by {@code LangChain4jConfig#aiService}.
 */
public class OpenRouterAiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterAiServiceImpl.class);

    private final ChatLanguageModel chatModel;

    /**
     * @param chatModel the LangChain4j model configured for OpenRouter;
     *                  injected by {@code LangChain4jConfig}
     */
    public OpenRouterAiServiceImpl(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    // -------------------------------------------------------------------------
    // AiService implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Builds a two-turn conversation: system message (persona + JSON schema constraint)
     * and user message (task-specific context). LangChain4j handles HTTP, retries,
     * and serialisation under the hood.
     *
     * @throws AiServiceException on timeout, API error, or empty response
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.debug("OpenRouterAiServiceImpl – Sending request to OpenRouter");

        validateInputs(systemPrompt, userPrompt);

        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );

            ChatResponse response = chatModel.chat(messages);

            return extractAndValidateContent(response);

        } catch (AiServiceException e) {
            // Re-throw our own typed exceptions unchanged
            throw e;
        } catch (Exception e) {
            // Map all LangChain4j / OkHttp exceptions to our typed exception
            String message = classifyException(e);
            log.error("OpenRouterAiServiceImpl – LLM call failed: {}", message, e);
            throw new AiServiceException(message, e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateInputs(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new AiServiceException("systemPrompt must not be null or blank.");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new AiServiceException("userPrompt must not be null or blank.");
        }
    }

    /**
     * Extracts the text content from the model response, throwing
     * {@link AiServiceException} if the response is empty.
     */
    private String extractAndValidateContent(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            throw new AiServiceException("OpenRouter returned a null response.");
        }

        String content = response.aiMessage().text();

        if (content == null || content.isBlank()) {
            throw new AiServiceException(
                    "OpenRouter returned an empty response body. "
                    + "Finish reason: " + response.finishReason());
        }

        log.debug("OpenRouterAiServiceImpl – Received response ({} chars)", content.length());
        return content.trim();
    }

    /**
     * Maps common LangChain4j / OkHttp exception messages to user-friendly
     * {@link AiServiceException} messages, distinguishing timeouts from other errors.
     */
    private String classifyException(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("connect timed out")) {
            return "OpenRouter API request timed out. The model may be overloaded. " +
                   "Consider increasing openrouter.timeout.seconds or retrying.";
        }
        if (msg.contains("401") || msg.contains("unauthorized")) {
            return "OpenRouter authentication failed. Check that OPENROUTER_API_KEY is set correctly.";
        }
        if (msg.contains("429") || msg.contains("rate limit")) {
            return "OpenRouter rate limit exceeded. Reduce request frequency or upgrade your plan.";
        }
        if (msg.contains("503") || msg.contains("service unavailable")) {
            return "OpenRouter service is temporarily unavailable. Retry after a short delay.";
        }

        return "OpenRouter API call failed: " + e.getClass().getSimpleName() + " – " + e.getMessage();
    }
}
