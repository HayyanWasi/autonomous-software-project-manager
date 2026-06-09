package com.autonomouspm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for all {@code openrouter.*} properties
 * defined in {@code application.properties}.
 *
 * <p>Registered as a Spring bean via {@code @EnableConfigurationProperties}
 * in {@link LangChain4jConfig}. Injected into {@link OpenRouterAiServiceImpl}
 * to configure the LangChain4j {@code OpenAiChatModel}.
 */
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(

        /** OpenRouter API key. Override with {@code OPENROUTER_API_KEY} env var. */
        Api api,

        /** OpenRouter base URL (OpenAI-compatible endpoint). */
        Base base,

        /** Target model identifier on OpenRouter. */
        Model model,

        /** Request timeout configuration. */
        Timeout timeout,

        /** Maximum tokens the LLM may return. */
        int maxTokens,

        /** Sampling temperature (0.0 deterministic → 1.0 creative). */
        double temperature
) {

    public record Api(String key) {}
    public record Base(String url) {}
    public record Model(String name) {}
    public record Timeout(int seconds) {}
}
