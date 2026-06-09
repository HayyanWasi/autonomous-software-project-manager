package com.autonomouspm.service;

import java.util.List;

/**
 * Contract for web-based market research used by the <b>Business Analyst Agent</b>.
 *
 * <p>Spec: {@code specs/modules/business-analyst.md §3 Market Research Tool Usage}
 *
 * <p>Provides access to publicly available market intelligence:
 * competitor information, user reviews, forum discussions, App/Play Store reviews,
 * and industry trends.
 *
 * <p>Implementations must follow the hallucination-prevention rules defined in the spec:
 * if no data is available for a query, return an empty list rather than fabricated
 * results. Callers must handle an empty list by stating
 * {@code "Insufficient Market Evidence"} in their output.
 *
 * <p>Registered as a Spring-managed bean; injected into the Business Analyst Agent
 * via constructor injection.
 */
public interface MarketResearchService {

    /**
     * Searches public competitor sources for information related to the given topic.
     *
     * @param topic the product category or domain to research (e.g. "food delivery apps")
     * @return list of competitor insight snippets; empty if no data found
     */
    List<String> searchCompetitors(String topic);

    /**
     * Retrieves user reviews and ratings for apps or products in the given domain.
     *
     * <p>Sources may include App Store, Play Store, and equivalent platforms.
     *
     * @param topic the product or app category to query
     * @return list of representative review snippets; empty if no data found
     */
    List<String> fetchUserReviews(String topic);

    /**
     * Fetches relevant discussions from public forums (e.g. Reddit) for the given topic.
     *
     * @param topic the subject to search
     * @return list of forum post excerpts; empty if no data found
     */
    List<String> fetchForumDiscussions(String topic);

    /**
     * Retrieves current industry trend summaries for the given market segment.
     *
     * @param marketSegment the industry or product segment (e.g. "on-demand logistics")
     * @return list of trend summary strings; empty if no data found
     */
    List<String> fetchIndustryTrends(String marketSegment);

    // -------------------------------------------------------------------------
    // Exception type
    // -------------------------------------------------------------------------

    /**
     * Unchecked exception thrown when a market-research source is unreachable
     * or returns an irrecoverable error.
     *
     * <p>Callers should catch this, log a warning, and treat the result as an
     * empty list to satisfy the "Insufficient Market Evidence" fallback rule.
     */
    class MarketResearchException extends RuntimeException {

        public MarketResearchException(String message) {
            super(message);
        }

        public MarketResearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
