package com.autonomouspm.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Immutable output context produced by the <b>Requirement Analyst Agent</b>.
 *
 * <p>Spec: {@code specs/modules/requirement-analyst.md §3 Output Specifications}
 *
 * <p>Passed to {@code CentralOrchestrator} (Mediator) and forwarded to the
 * Business Analyst Agent as its primary input.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RequirementContext(

        /** The original project idea submitted by the user. */
        String projectIdea,

        /** High-level executive summary of the project. */
        String executiveSummary,

        /** Identified user roles (e.g. "Customer", "Admin"). */
        List<String> userRoles,

        /** Core features extracted from the raw input. */
        List<String> coreFeatures,

        /** Assumptions accepted during analysis. */
        List<String> assumptions,

        /** Known constraints (technical, legal, financial). */
        List<String> constraints,

        /** Non-functional requirements (performance, security, scalability…). */
        List<String> nonFunctionalRequirements,

        /** Open questions that require clarification before proceeding. */
        List<String> openQuestions,

        /**
         * Completeness score between 0.0 and 1.0.
         * Computed by the LLM; used to decide whether clarification is needed.
         */
        double completionScore,

        /**
         * {@code true} when the analysis cannot proceed without additional input.
         * Triggers the {@code needsClarification} guard in {@code CentralOrchestrator}.
         */
        boolean needsClarification
) {}
