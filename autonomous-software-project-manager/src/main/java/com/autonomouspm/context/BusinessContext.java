package com.autonomouspm.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Immutable output context produced by the <b>Business Analyst Agent</b>.
 *
 * <p>Spec: {@code specs/modules/business-analyst.md §6 Output Specifications}
 *
 * <p>Contains business goals, epics, market-research-backed user stories, and
 * feature recommendations. Passed via {@code CentralOrchestrator} to the
 * Database Architect Agent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessContext(

        /** High-level business goals derived from the requirements. */
        List<String> businessGoals,

        /** Epic groupings for the identified feature set. */
        List<String> epics,

        /** Detailed user stories in the actor / action / benefit format. */
        List<UserStory> userStories,

        /** User pain points discovered through market research. */
        List<String> marketPainPoints,

        /** Competitor strengths, weaknesses, and missed opportunities. */
        List<String> competitorInsights,

        /** Market-evidence-backed feature recommendations. */
        List<String> recommendedFeatures,

        /** Assumptions from RequirementContext that were validated by research. */
        List<String> validatedAssumptions,

        /** Prose summary of the entire business analysis. */
        String businessSummary
) {

    /**
     * A single user story in the canonical As a … / I want … / So that … format.
     *
     * <p>Spec: {@code specs/modules/business-analyst.md §6 UserStory}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserStory(

            /** The user role performing the action (e.g. "Customer"). */
            String actor,

            /** The desired capability (e.g. "Track my order"). */
            String action,

            /** The business value delivered (e.g. "Know estimated delivery time"). */
            String benefit
    ) {}
}
