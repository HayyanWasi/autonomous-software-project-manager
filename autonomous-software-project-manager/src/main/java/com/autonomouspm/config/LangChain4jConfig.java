package com.autonomouspm.config;

import com.autonomouspm.service.AiService;
import com.autonomouspm.service.OpenRouterAiServiceImpl;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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

    /**
     * Creates and configures the LangChain4j {@link GoogleAiGeminiChatModel} pointed at
     * the official Google Gemini AI Studio endpoint.
     *
     * @param props bound {@link GeminiProperties}
     * @return fully configured {@link GoogleAiGeminiChatModel} singleton
     */
    @Bean
    public GoogleAiGeminiChatModel googleAiGeminiChatModel(GeminiProperties props) {
        log.info("LangChain4jConfig – Initialising GoogleAiGeminiChatModel → model={}", props.model().name());

        return GoogleAiGeminiChatModel.builder()
                .apiKey(props.api().key())
                .modelName(props.model().name())
                .temperature(props.temperature())
                .timeout(Duration.ofSeconds(props.timeout().seconds()))
                .logRequestsAndResponses(false)
                .build();
    }

    /**
     * Registers {@link OpenRouterAiServiceImpl} as the canonical {@link AiService} bean.
     * The name {@code OpenRouterAiServiceImpl} is kept for backward compatibility;
     * it is simply the adapter that wraps whichever {@link ChatLanguageModel} is active.
     *
     * @param model the configured chat model
     * @return the {@link AiService} adapter
     */
    @Bean
    public AiService aiService(ChatLanguageModel model) {
        return new OpenRouterAiServiceImpl(model);
    }
}
