package com.autonomouspm.tokenmanagement;

import com.autonomouspm.config.LangChain4jConfig.GeminiKeyServices;
import com.autonomouspm.service.AiService;
import com.autonomouspm.service.AiService.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
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

    /** Backoff (seconds) applied BEFORE each retry on the next key: retry1=15s, retry2=30s, retry3=60s, retry4=60s. */
    private static final int[] BACKOFF_SECONDS = {15, 30, 60, 60};

    /** Ordered per-key LLM services; rotation walks this list in index order (key 0 first). */
    private final List<AiService> keyServices;
    private final LlmCacheRepository cacheRepository;
    private final TokenBudgetConfig tokenBudgetConfig;

    /**
     * @param geminiKeyServices ordered per-key {@link AiService} pool (one per {@code gemini.api.keys} entry)
     * @param cacheRepository   permanent response cache
     * @param tokenBudgetConfig per-agent output-token budgets
     */
    public CachingAiService(GeminiKeyServices geminiKeyServices,
                            LlmCacheRepository cacheRepository,
                            TokenBudgetConfig tokenBudgetConfig) {
        this.keyServices = geminiKeyServices.services();
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
     * @return the cached or freshly generated LLM response, or {@code null} when every
     *         configured key is exhausted (429/503) or a non-retryable error occurs —
     *         so the caller's Null Object fallback engages
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

        // Call the LLM with key rotation + exponential backoff on 429/503.
        // Returns null when every key is exhausted, so the agent's Null Object path engages.
        String response = chatWithRotation(systemPrompt, userPrompt, agent);

        // Never cache null/empty/blank responses.
        if (response != null && !response.isBlank()) {
            persist(hash, agent, response);
        }
        return response;
    }

    // -------------------------------------------------------------------------
    // Key rotation + exponential backoff
    // -------------------------------------------------------------------------

    /**
     * Calls the LLM trying each key in order. On a 429/503 failure it waits with
     * exponential backoff (2s before key 1, 4s before key 2, …) and rotates to the
     * next key. When all keys are exhausted — or a non-rotatable error occurs — it
     * returns {@code null} so the caller's Null Object fallback engages.
     *
     * @return the LLM response text, or {@code null} if no key could satisfy the call
     */
    private String chatWithRotation(String systemPrompt, String userPrompt, String agent) {
        int keyCount = keyServices.size();

        for (int index = 0; index < keyCount; index++) {
            try {
                return keyServices.get(index).chat(systemPrompt, userPrompt);

            } catch (AiServiceException e) {
                boolean rotatable = isRateLimitedOrUnavailable(e);
                boolean hasNextKey = index + 1 < keyCount;

                if (!rotatable) {
                    // Not a 429/503 — rotating to another key will not help. Bail to Null Object.
                    log.error("CachingAiService – agent {} key index {} failed with non-retryable error: {}",
                            agent, index, e.getMessage());
                    return null;
                }

                if (!hasNextKey) {
                    log.error("CachingAiService – agent {} key index {} hit 429/503 and all {} key(s) "
                                    + "are exhausted; returning null to trigger Null Object",
                            agent, index, keyCount);
                    return null;
                }

                int waitSeconds = BACKOFF_SECONDS[Math.min(index, BACKOFF_SECONDS.length - 1)];
                log.warn("CachingAiService – agent {} key index {} failed with 429/503 ({}); "
                                + "waiting {}s then trying key index {}",
                        agent, index, e.getMessage(), waitSeconds, index + 1);

                if (!sleepSeconds(waitSeconds)) {
                    // Interrupted while backing off — abort to Null Object.
                    log.error("CachingAiService – agent {} interrupted during backoff; returning null", agent);
                    return null;
                }
            }
        }

        // keyServices was empty (config guards against this, but stay defensive).
        log.error("CachingAiService – agent {} had no Gemini keys available; returning null", agent);
        return null;
    }

    /**
     * Returns {@code true} when the failure is a rate-limit (429) or service-unavailable
     * (503) condition, by scanning the exception message and its full cause chain.
     */
    private boolean isRateLimitedOrUnavailable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg == null) {
                continue;
            }
            String lower = msg.toLowerCase();
            if (lower.contains("429") || lower.contains("rate limit")
                    || lower.contains("503") || lower.contains("unavailable")
                    || lower.contains("high demand")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sleeps for the given number of seconds. Returns {@code false} if interrupted
     * (restoring the thread's interrupt flag), {@code true} otherwise.
     */
    private boolean sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
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
