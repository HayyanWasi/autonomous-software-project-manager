package com.autonomouspm.agents.requirement;

import com.autonomouspm.context.RequirementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless validation component for the requirement-analysis stage.
 *
 * <p>Extracted from {@code RequirementAnalystAgent} to honour the <b>Single
 * Responsibility Principle</b>: the agent orchestrates the LLM call and pipeline
 * wiring, while this class owns <em>all</em> validation rules — both the raw
 * pre-flight checks on user input and the semantic checks on the parsed
 * {@link RequirementContext}.
 *
 * <p><b>Design notes:</b>
 * <ul>
 *   <li>Registered as a Spring {@code @Component}; injected into the agent via
 *       constructor injection.</li>
 *   <li>Holds no mutable state — only immutable {@code static final} rule
 *       constants — so it is inherently thread-safe and freely shareable.</li>
 *   <li>Null-safe: every public method tolerates {@code null} arguments and
 *       reports them as ordinary validation violations rather than throwing.</li>
 *   <li>Returns a {@link ValidationResult} value object instead of throwing, so
 *       callers decide how to react (fail fast, request clarification, etc.).</li>
 * </ul>
 */
@Component
public class RequirementValidator {

    private static final Logger log = LoggerFactory.getLogger(RequirementValidator.class);

    /** Minimum word count accepted as a valid project description. */
    public static final int MIN_WORD_COUNT = 3;

    /** Inclusive lower bound for {@link RequirementContext#completionScore()}. */
    private static final double MIN_COMPLETION_SCORE = 0.0;

    /** Inclusive upper bound for {@link RequirementContext#completionScore()}. */
    private static final double MAX_COMPLETION_SCORE = 1.0;

    // -------------------------------------------------------------------------
    // Raw input validation (pre-LLM)
    // -------------------------------------------------------------------------

    /**
     * Validates the user's raw project description before any LLM call is made.
     *
     * <p>Rules:
     * <ol>
     *   <li>Input must not be {@code null} or blank.</li>
     *   <li>Input must contain at least {@link #MIN_WORD_COUNT} words.</li>
     * </ol>
     *
     * @param rawInput the raw user input (may be {@code null})
     * @return a {@link ValidationResult}; {@link ValidationResult#valid()} is
     *         {@code true} only when every rule passes
     */
    public ValidationResult validateRawInput(String rawInput) {
        List<String> violations = new ArrayList<>();

        if (rawInput == null || rawInput.isBlank()) {
            violations.add("Raw user input is null or blank. Cannot proceed.");
            return ValidationResult.failed(violations);
        }

        int words = wordCount(rawInput);
        if (words < MIN_WORD_COUNT) {
            violations.add("Input is too brief (" + words + " word(s)). "
                    + "Please provide at least " + MIN_WORD_COUNT + " words describing your project.");
        }

        return violations.isEmpty()
                ? ValidationResult.ok()
                : ValidationResult.failed(violations);
    }

    // -------------------------------------------------------------------------
    // Parsed context validation (post-LLM)
    // -------------------------------------------------------------------------

    /**
     * Validates a parsed {@link RequirementContext} for semantic completeness.
     *
     * <p>Rules:
     * <ol>
     *   <li>Context itself must not be {@code null}.</li>
     *   <li>{@code projectIdea} and {@code executiveSummary} must not be blank.</li>
     *   <li>{@code coreFeatures} and {@code userRoles} must each contain at least
     *       one entry.</li>
     *   <li>{@code completionScore} must lie within
     *       [{@value #MIN_COMPLETION_SCORE}, {@value #MAX_COMPLETION_SCORE}].</li>
     * </ol>
     *
     * <p>All violations are collected and returned together so the caller can
     * surface a complete picture rather than only the first failure.
     *
     * @param context the parsed context (may be {@code null})
     * @return a {@link ValidationResult} describing every violation found
     */
    public ValidationResult validateContext(RequirementContext context) {
        List<String> violations = new ArrayList<>();

        if (context == null) {
            violations.add("RequirementContext is null.");
            return ValidationResult.failed(violations);
        }

        if (isBlank(context.projectIdea())) {
            violations.add("RequirementContext.projectIdea must not be blank.");
        }
        if (isBlank(context.executiveSummary())) {
            violations.add("RequirementContext.executiveSummary must not be blank.");
        }
        if (isEmpty(context.coreFeatures())) {
            violations.add("RequirementContext must contain at least one coreFeature.");
        }
        if (isEmpty(context.userRoles())) {
            violations.add("RequirementContext must contain at least one userRole.");
        }
        if (context.completionScore() < MIN_COMPLETION_SCORE
                || context.completionScore() > MAX_COMPLETION_SCORE) {
            violations.add("completionScore must be between " + MIN_COMPLETION_SCORE
                    + " and " + MAX_COMPLETION_SCORE + ", got: " + context.completionScore());
        }

        if (violations.isEmpty()) {
            return ValidationResult.ok();
        }

        log.debug("RequirementValidator – context failed {} rule(s): {}",
                violations.size(), violations);
        return ValidationResult.failed(violations);
    }

    // -------------------------------------------------------------------------
    // Helpers (null-safe)
    // -------------------------------------------------------------------------

    private int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Result value object
    // -------------------------------------------------------------------------

    /**
     * Immutable outcome of a validation call.
     *
     * <p>Never carries {@code null}: the {@code violations} list is defensively
     * copied and is empty when {@link #valid()} is {@code true}.
     *
     * @param valid      {@code true} when no rules were violated
     * @param violations human-readable descriptions of each violation (never {@code null})
     */
    public record ValidationResult(boolean valid, List<String> violations) {

        /**
         * Canonical constructor enforcing null-safety and immutability of the
         * {@code violations} list.
         */
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        /**
         * @return a successful result with no violations
         */
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        /**
         * @param violations the non-empty list of violations
         * @return a failed result carrying the given violations
         */
        public static ValidationResult failed(List<String> violations) {
            return new ValidationResult(false, violations);
        }

        /**
         * @return {@code true} when at least one violation was recorded
         */
        public boolean hasViolations() {
            return !violations.isEmpty();
        }

        /**
         * @return the first violation message, or an empty string when valid
         */
        public String firstViolation() {
            return violations.isEmpty() ? "" : violations.get(0);
        }

        /**
         * @return all violations joined into a single human-readable line
         */
        public String summary() {
            return String.join(" ", violations);
        }
    }
}
