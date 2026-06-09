package com.autonomouspm.tokenmanagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the permanent LLM response cache.
 *
 * <p>Spec: {@code specs/docs/token-management.md} (Step 2) — part of the Token
 * Management Layer.
 *
 * <p>Used by {@code CachingAiService} to look up a previously stored response by
 * the SHA-256 hash of the {@code systemPrompt + userPrompt}. A present result is
 * a cache hit (zero LLM tokens spent); an empty result triggers a live LLM call.
 */
@Repository
public interface LlmCacheRepository extends JpaRepository<LlmCacheEntry, Long> {

    /**
     * Finds a cached entry by its unique input hash.
     *
     * @param inputHash SHA-256 hash of {@code systemPrompt + userPrompt}
     * @return the matching {@link LlmCacheEntry}, or empty if none is cached
     */
    Optional<LlmCacheEntry> findByInputHash(String inputHash);
}
