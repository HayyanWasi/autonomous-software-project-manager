package com.autonomouspm.tokenmanagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA entity for the permanent LLM response cache.
 *
 * <p>Spec: {@code specs/docs/token-management.md} (Step 1) — part of the Token
 * Management Layer that must exist before any agent makes an LLM call.
 *
 * <p>Each row stores the response produced for a unique SHA-256 hash of the
 * {@code systemPrompt + userPrompt}. On a cache hit, the cached
 * {@link #responseJson} is returned and zero LLM tokens are spent.
 *
 * <p>Maps to the {@code llm_cache} table:
 * <pre>
 * CREATE TABLE llm_cache (
 *     id BIGSERIAL PRIMARY KEY,
 *     input_hash VARCHAR(64) UNIQUE NOT NULL,
 *     agent_name VARCHAR(100) NOT NULL,
 *     response_json TEXT NOT NULL,
 *     created_at TIMESTAMP DEFAULT NOW()
 * );
 * </pre>
 */
@Entity
@Table(
        name = "llm_cache",
        indexes = @Index(name = "idx_llm_cache_hash", columnList = "input_hash")
)
@Getter
@Setter
@NoArgsConstructor
public class LlmCacheEntry {

    /** Surrogate primary key (BIGSERIAL). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** SHA-256 hash of {@code systemPrompt + userPrompt}; unique cache key. */
    @Column(name = "input_hash", length = 64, unique = true, nullable = false)
    private String inputHash;

    /** Name of the agent that produced the cached response. */
    @Column(name = "agent_name", length = 100, nullable = false)
    private String agentName;

    /** The raw LLM response text that was cached. */
    @Column(name = "response_json", columnDefinition = "TEXT", nullable = false)
    private String responseJson;

    /** Timestamp the entry was created. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Convenience constructor for creating a fresh cache entry.
     *
     * @param inputHash    SHA-256 hash of the prompt pair
     * @param agentName    producing agent name
     * @param responseJson the LLM response to cache
     * @param createdAt    creation timestamp
     */
    public LlmCacheEntry(String inputHash, String agentName, String responseJson, LocalDateTime createdAt) {
        this.inputHash = inputHash;
        this.agentName = agentName;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }
}
