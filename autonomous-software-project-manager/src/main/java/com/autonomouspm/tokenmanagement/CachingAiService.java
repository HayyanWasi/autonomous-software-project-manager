package com.autonomouspm.tokenmanagement;

import com.autonomouspm.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Caching decorator over {@link AiService} that eliminates duplicate LLM calls.
 *
 * <p>Spec: {@code specs/docs/token-management.md} (Step 5) — the Token Management
 * Layer's caching component.
 *
 * <p><b>Decorator pattern:</b> implements {@link AiService} and wraps the real
 * {@link AiService} delegate. Marked {@link Primary} so every agent that depends
 * on {@link AiService} transparently receives caching; the underlying provider
 * bean (named {@code aiService}) is injected explicitly via {@link Qualifier} to
 * avoid self-injection.
 *
 * <p><b>Caching logic:</b>
 * <ol>
 *   <li>SHA-256 hash the {@code systemPrompt + userPrompt}.</li>
 *   <li>Look the hash up in {@link LlmCacheRepository}.</li>
 *   <li>Hit → log and return the cached response (zero tokens spent).</li>
 *   <li>Miss → call the delegate, then persist the response.</li>
 * </ol>
 *
 * <p><b>Caching rules:</b> error responses are never cached (the delegate's
 * {@code AiServiceException} propagates before any save), and empty/blank
 * responses are never cached. The cache is permanent (no expiry during
 * development).
 *
 * <p>This class also activates {@link TokenBudgetConfig} via
 * {@link EnableConfigurationProperties} so per-agent output-token budgets are
 * available to the layer.
 */
@Service
@Primary
@EnableConfigurationProperties(TokenBudgetConfig.class)
public class CachingAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(CachingAiService.class);

    /** Agent label used when a caller does not supply one. */
    private static final String UNKNOWN_AGENT = "UnknownAgent";

    private final AiService delegate;
    private final LlmCacheRepository cacheRepository;
    private final TokenBudgetConfig tokenBudgetConfig;

    /**
     * @param delegate          the real LLM-backed {@link AiService} (bean named {@code aiService})
     * @param cacheRepository   permanent response cache
     * @param tokenBudgetConfig per-agent output-token budgets
     */
    public CachingAiService(@Qualifier("aiService") AiService delegate,
                            LlmCacheRepository cacheRepository,
                            TokenBudgetConfig tokenBudgetConfig) {
        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
        this.tokenBudgetConfig = tokenBudgetConfig;
    }

    // -------------------------------------------------------------------------
    // AiService implementation (Decorator)
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #chat(String, String, String)} with an unknown agent
     * label. Prefer the three-argument overload so cache events are attributed to
     * the calling agent.
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, UNKNOWN_AGENT);
    }

    /**
     * Agent-aware variant of {@link #chat(String, String)} that attributes cache
     * hits/misses to a named agent.
     *
     * @param systemPrompt the system message
     * @param userPrompt   the user message
     * @param agentName    the calling agent's name (for logging and budget lookup)
     * @return the cached or freshly generated LLM response
     * @throws com.autonomouspm.service.AiService.AiServiceException if the delegate call fails
     */
    public String chat(String systemPrompt, String userPrompt, String agentName) {
        String agent = (agentName == null || agentName.isBlank()) ? UNKNOWN_AGENT : agentName;
        String hash = sha256(nullSafe(systemPrompt) + nullSafe(userPrompt));

        Optional<LlmCacheEntry> cached = cacheRepository.findByInputHash(hash);
        if (cached.isPresent()) {
            log.info("Cache hit for agent: {} hash: {}", agent, hash);
            return cached.get().getResponseJson();
        }

        log.info("Cache miss for agent: {} — calling LLM", agent);
        log.debug("CachingAiService – output-token budget for {} = {}", agent, tokenBudgetConfig.forAgent(agent));

        // Errors propagate here and are never cached.
        String response = delegate.chat(systemPrompt, userPrompt);

        // Never cache empty/blank responses.
        if (response != null && !response.isBlank()) {
            persist(hash, agent, response);
        }
        return response;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Persists a fresh response. A cache-write failure must never break the
     * pipeline, so any exception is logged and swallowed — the response is still
     * returned to the caller.
     */
    private void persist(String hash, String agentName, String response) {
        try {
            LlmCacheEntry entry = new LlmCacheEntry(hash, agentName, response, LocalDateTime.now());
            cacheRepository.save(entry);
            log.debug("CachingAiService – cached response for agent: {} hash: {}", agentName, hash);
        } catch (Exception e) {
            log.warn("CachingAiService – failed to cache response for agent {}: {}", agentName, e.getMessage());
        }
    }

    /**
     * Computes the lowercase hex SHA-256 digest of the given text (64 chars).
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; this should never happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
