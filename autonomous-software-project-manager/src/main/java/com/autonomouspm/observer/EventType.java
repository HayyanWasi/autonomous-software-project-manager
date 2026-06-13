package com.autonomouspm.observer;

/**
 * Enumeration of all pipeline event types.
 *
 * <p>Used by the {@link EventLogger} (Observer pattern) to categorise events
 * published by agents and the {@code CentralOrchestrator}.
 *
 * <p>Design pattern: <b>Observer</b> — these types label the events that
 * {@code PipelineEventBus} broadcasts to registered listeners.
 *
 * <p>Naming convention: {@code <SUBJECT>_<VERB>} in past-tense or present-progressive.
 */
public enum EventType {

    // -------------------------------------------------------------------------
    // Pipeline lifecycle events
    // -------------------------------------------------------------------------

    /** A new pipeline run has been initialised. */
    PIPELINE_STARTED,

    /** All agents have completed and the final report is ready. */
    PIPELINE_COMPLETED,

    /** An unrecoverable error halted the pipeline. */
    PIPELINE_FAILED,

    // -------------------------------------------------------------------------
    // Agent lifecycle events
    // -------------------------------------------------------------------------

    /** An agent has started execution. */
    AGENT_STARTED,

    /** An agent has finished execution successfully. */
    AGENT_COMPLETED,

    /** An agent has encountered an error; will return a Null Object result. */
    AGENT_FAILED,

    // -------------------------------------------------------------------------
    // Requirement Analyst events
    // -------------------------------------------------------------------------

    REQUIREMENT_ANALYSIS_STARTED,
    REQUIREMENT_ANALYSIS_COMPLETED,
    REQUIREMENT_CLARIFICATION_NEEDED,

    // -------------------------------------------------------------------------
    // Business Analyst events
    // -------------------------------------------------------------------------

    BUSINESS_ANALYSIS_STARTED,
    MARKET_RESEARCH_STARTED,
    COMPETITOR_ANALYSIS_STARTED,
    USER_STORIES_GENERATING,
    BUSINESS_REPORT_PREPARING,
    BUSINESS_ANALYSIS_COMPLETED,

    // -------------------------------------------------------------------------
    // Database Architect events
    // -------------------------------------------------------------------------

    SCHEMA_DESIGN_STARTED,
    ERD_GENERATING,
    DATABASE_DESIGN_COMPLETED,

    // -------------------------------------------------------------------------
    // Project Planner events
    // -------------------------------------------------------------------------

    PLANNING_STARTED,
    WBS_GENERATING,
    DEPENDENCIES_MAPPING,
    GANTT_GENERATING,
    PLANNING_COMPLETED,

    // -------------------------------------------------------------------------
    // Risk Analyst events
    // -------------------------------------------------------------------------

    RISK_ANALYSIS_STARTED,
    DATABASE_RISKS_EVALUATING,
    SCHEDULE_RISKS_EVALUATING,
    RISK_SCORES_CALCULATING,
    RISK_REPORT_FINALIZING,
    RISK_ANALYSIS_COMPLETED,

    // -------------------------------------------------------------------------
    // Report builder events
    // -------------------------------------------------------------------------

    REPORT_BUILD_STARTED,
    REPORT_BUILD_COMPLETED
}
