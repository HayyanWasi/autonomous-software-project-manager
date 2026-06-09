package com.autonomouspm.agents.business;

import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.BusinessContext.UserStory;
import com.autonomouspm.service.MarketResearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless validation component for the business-analysis stage.
 *
 * <p>Spec: {@code specs/modules/business-analyst.md §7 Validation Rules} and
 * {@code specs/modules/business_research_tool.md §6 Hallucination Prevention}.
 *
 * <p>Extracted from {@code BusinessAnalystAgent} to honour the <b>Single
 * Responsibility Principle</b>: the agent orchestrates research, the LLM call and
 * state wiring, while this class owns every semantic rule on the parsed
 * {@link BusinessContext}.
 *
 * <p>Holds no mutable state (only immutable rule constants), is therefore
 * thread-safe, and is null-safe: every public method tolerates {@code null}
 * input and reports it as an ordinary violation rather than throwing.
 */
@Component
public class BusinessAnalystValidator {

    private static final Logger log = LoggerFactory.getLogger(BusinessAnalystValidator.class);

    /**
     * Validates a parsed {@link BusinessContext}.
     *
     * <p>Structural rules:
     * <ol>
     *   <li>Context must not be {@code null}.</li>
     *   <li>{@code businessGoals}, {@code epics} and {@code userStories} must each
     *       contain at least one entry.</li>
     *   <li>Every user story must have a non-blank actor, action and benefit.</li>
     *   <li>{@code businessSummary} must not be blank.</li>
     * </ol>
     *
     * <p>Hallucination rules (enforced only when {@code researchAvailable} is
     * {@code false}): {@code competitorInsights} and {@code marketPainPoints} must
     * be empty or contain solely the
     * {@link MarketResearchTool#INSUFFICIENT_EVIDENCE} sentinel — the LLM may not
     * invent competitors or pain points without market evidence.
     *
     * @param context           the parsed context (may be {@code null})
     * @param researchAvailable {@code true} when the market-research tool returned
     *                          real evidence for this run
     * @return a {@link ValidationResult} describing every violation found
     */
    public ValidationResult validateContext(BusinessContext context, boolean researchAvailable) {
        List<String> violations = new ArrayList<>();

        if (context == null) {
            violations.add("BusinessContext is null.");
            return ValidationResult.failed(violations);
        }

        if (isEmpty(context.businessGoals())) {
            violations.add("BusinessContext must contain at least one businessGoal.");
        }
        if (isEmpty(context.epics())) {
            violations.add("BusinessContext must contain at least one epic.");
        }
        if (isEmpty(context.userStories())) {
            violations.add("BusinessContext must contain at least one userStory.");
        } else {
            validateUserStories(context.userStories(), violations);
        }
        if (isBlank(context.businessSummary())) {
            violations.add("BusinessContext.businessSummary must not be blank.");
        }

        if (!researchAvailable) {
            assertNoFabrication("competitorInsights", context.competitorInsights(), violations);
            assertNoFabrication("marketPainPoints", context.marketPainPoints(), violations);
        }

        if (violations.isEmpty()) {
            return ValidationResult.ok();
        }

        log.debug("BusinessAnalystValidator – context failed {} rule(s): {}",
                violations.size(), violations);
        return ValidationResult.failed(violations);
    }

    // -------------------------------------------------------------------------
    // Helpers (null-safe)
    // -------------------------------------------------------------------------

    private void validateUserStories(List<UserStory> stories, List<String> violations) {
        for (int i = 0; i < stories.size(); i++) {
            UserStory story = stories.get(i);
            if (story == null) {
                violations.add("userStories[" + i + "] is null.");
                continue;
            }
            if (isBlank(story.actor()) || isBlank(story.action()) || isBlank(story.benefit())) {
                violations.add("userStories[" + i + "] must have a non-blank actor, action and benefit.");
            }
        }
    }

    /**
     * Fails when a research-backed field contains fabricated entries while no real
     * evidence was available. The field is acceptable only if it is empty or holds
     * exclusively the {@link MarketResearchTool#INSUFFICIENT_EVIDENCE} sentinel.
     */
    private void assertNoFabrication(String fieldName, List<String> values, List<String> violations) {
        if (values == null || values.isEmpty()) {
            return;
        }
        boolean onlySentinel = values.stream()
                .allMatch(v -> v != null && MarketResearchTool.INSUFFICIENT_EVIDENCE.equals(v.trim()));
        if (!onlySentinel) {
            violations.add(fieldName + " contains fabricated entries: market research was unavailable, "
                    + "so it must be empty or state \"" + MarketResearchTool.INSUFFICIENT_EVIDENCE + "\".");
        }
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
     * Immutable outcome of a validation call. Never carries {@code null}: the
     * {@code violations} list is defensively copied and empty when {@link #valid()}.
     *
     * @param valid      {@code true} when no rules were violated
     * @param violations human-readable descriptions of each violation
     */
    public record ValidationResult(boolean valid, List<String> violations) {

        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        /** @return a successful result with no violations */
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        /** @return a failed result carrying the given violations */
        public static ValidationResult failed(List<String> violations) {
            return new ValidationResult(false, violations);
        }

        /** @return all violations joined into a single human-readable line */
        public String summary() {
            return String.join(" ", violations);
        }
    }
}
