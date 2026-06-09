package com.autonomouspm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for all {@code gemini.*} properties
 * defined in {@code application.properties}.
 *
 * <p>Registered as a Spring bean via {@code @EnableConfigurationProperties}
 * in {@link LangChain4jConfig}. Injected into {@link LangChain4jConfig}
 * to configure the LangChain4j {@code GoogleAiGeminiChatModel}.
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(

        /** Google AI Studio API key. Override with {@code GEMINI_API_KEY} env var. */
        Api api,

        /** Target Gemini model identifier (e.g. gemini-2.5-flash). */
        Model model,

        /** Maximum tokens the LLM may return. */
        int maxTokens,

        /** Sampling temperature (0.0 deterministic → 1.0 creative). */
        double temperature,

        /** Request timeout configuration. */
        Timeout timeout

) {
    public record Api(String key) {}
    public record Model(String name) {}
    public record Timeout(int seconds) {}
}
