package com.autonomouspm.config;

import com.autonomouspm.service.AiService;
import com.autonomouspm.service.OpenRouterAiServiceImpl;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Spring configuration class responsible for wiring the LangChain4j
 * {@link GoogleAiGeminiChatModel} (configured for the official Google AI Studio)
 * into the application context.
 *
 * <p><b>Design patterns:</b>
 * <ul>
 *   <li><b>Singleton</b> — the {@link GoogleAiGeminiChatModel} bean is created once and
 *       shared across the application.</li>
 *   <li><b>Adapter</b> — {@link OpenRouterAiServiceImpl} wraps the model,
 *       exposing the clean {@link AiService} interface to agents.</li>
 * </ul>
 *
 * <p>All configuration is read from {@link GeminiProperties}, which binds
 * the {@code gemini.*} keys in {@code application.properties}.
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    // -------------------------------------------------------------------------
    // Multi-key rotation support
    // -------------------------------------------------------------------------

    /**
     * Reads {@code gemini.api.keys} (a comma-separated list, no spaces required —
     * each entry is trimmed) and builds one {@link AiService} per key, each backed
     * by its own {@link GoogleAiGeminiChatModel}. {@link CachingAiService} consumes
     * this ordered list to rotate keys with exponential backoff on 429/503.
     *
     * <p>Falls back to the single {@code gemini.api.key} when {@code gemini.api.keys}
     * is absent, so the application never starts with zero usable keys.
     *
     * @param props   bound {@link GeminiProperties} (model name, temperature, timeout)
     * @param rawKeys raw value of {@code gemini.api.keys} (may be empty)
     * @return a holder wrapping one {@link AiService} per configured key
     */
    @Bean
    public GeminiKeyServices geminiKeyServices(GeminiProperties props,
                                               @Value("${gemini.api.keys:}") String rawKeys) {
        // Split by comma and trim each key to tolerate accidental spaces; drop blanks.
        List<String> keys = Arrays.stream(rawKeys.split(","))
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .toList();

        // Fallback to the legacy single key so we never boot with an empty pool.
        if (keys.isEmpty() && props.api() != null && props.api().key() != null
                && !props.api().key().isBlank()) {
            keys = List.of(props.api().key().trim());
            log.warn("LangChain4jConfig – gemini.api.keys empty; falling back to single gemini.api.key");
        }

        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "No Gemini API keys configured. Set gemini.api.keys (comma-separated) "
                    + "or gemini.api.key in application.properties.");
        }

        log.info("LangChain4jConfig – loaded {} Gemini API key(s) for rotation", keys.size());

        List<AiService> services = new ArrayList<>(keys.size());
        for (int index = 0; index < keys.size(); index++) {
            GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey(keys.get(index))
                    .modelName(props.model().name())
                    .temperature(props.temperature())
                    .timeout(Duration.ofSeconds(props.timeout().seconds()))
                    .logRequestsAndResponses(false)
                    .build();
            services.add(new OpenRouterAiServiceImpl(model));
            log.info("LangChain4jConfig – initialised Gemini key index {} (model={})",
                    index, props.model().name());
        }

        return new GeminiKeyServices(services);
    }

    /**
     * Immutable holder for the ordered, per-key {@link AiService} pool.
     *
     * <p>Wrapping the list in a dedicated type avoids Spring's collection-injection
     * semantics (autowiring a bare {@code List<AiService>} would instead gather every
     * {@link AiService} bean — including {@link CachingAiService} itself — causing
     * self-injection).
     */
    public static final class GeminiKeyServices {

        private final List<AiService> services;

        public GeminiKeyServices(List<AiService> services) {
            this.services = List.copyOf(services);
        }

        /** @return the ordered per-key services; index 0 is the first key. */
        public List<AiService> services() {
            return services;
        }

        /** @return the number of configured keys. */
        public int size() {
            return services.size();
        }
    }
}
