package com.autonomouspm.core;

import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.context.GanttContext;
import com.autonomouspm.context.RequirementContext;
import com.autonomouspm.context.RiskContext;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 * Shared, mutable pipeline state passed through the Chain of Responsibility.
 *
 * <p>{@code ProjectState} is the single source of truth for all context objects
 * produced by the agent pipeline. Each agent reads the contexts it needs,
 * writes its own output context, and hands the same state object back to the
 * {@code CentralOrchestrator} (Mediator) for routing to the next agent.
 *
 * <p><b>Design patterns:</b>
 * <ul>
 *   <li><b>Mediator</b> — owned and threaded through the pipeline by
 *       {@code CentralOrchestrator}; agents never talk to each other directly.</li>
 *   <li><b>Chain of Responsibility</b> — each agent enriches the state and
 *       forwards it implicitly by returning an {@link AgentResult}.</li>
 *   <li><b>Observer</b> — state changes trigger events through
 *       {@code EventLogger}.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> This class is intentionally <em>not</em> thread-safe.
 * The pipeline is sequential; concurrent modification is not expected. If parallel
 * execution is introduced in the future, this class must be revisited.
 */
@Getter
@Setter
public class ProjectState {

    private static final Logger log = LoggerFactory.getLogger(ProjectState.class);

    // -------------------------------------------------------------------------
    // Pipeline metadata
    // -------------------------------------------------------------------------

    /** Unique identifier for this pipeline run. Set at pipeline initialisation. */
    private String pipelineId;

    /** Raw project idea string provided by the end user. */
    private String rawUserInput;

    /** Current high-level status of the pipeline run. */
    private PipelineStatus status;

    /** Timestamp when this pipeline run was started. */
    private Instant startedAt;

    // -------------------------------------------------------------------------
    // Agent output contexts
    // -------------------------------------------------------------------------

    /**
     * Output of the Requirement Analyst Agent (step 1).
     * {@code null} until that agent completes successfully.
     */
    private RequirementContext requirementContext;

    /**
     * Output of the Business Analyst Agent (step 2).
     * {@code null} until that agent completes successfully.
     */
    private BusinessContext businessContext;

    /**
     * Output of the Database Architect Agent (step 3).
     * {@code null} until that agent completes successfully.
     */
    private DatabaseContext databaseContext;

    /**
     * Output of the Project Planner Agent (step 4).
     * {@code null} until that agent completes successfully.
     */
    private GanttContext ganttContext;

    /**
     * Output of the Risk Analyst Agent (step 5 / final).
     * {@code null} until that agent completes successfully.
     */
    private RiskContext riskContext;

    /**
     * Final assembled Markdown report produced by {@code ProjectReportBuilder}
     * after all agents complete. {@code null} until the pipeline finishes.
     */
    private String finalReport;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a fresh {@code ProjectState} for a new pipeline run.
     *
     * @param pipelineId    unique run identifier
     * @param rawUserInput  the raw string submitted by the user
     * @return a newly initialised state object ready for the first agent
     */
    public static ProjectState init(String pipelineId, String rawUserInput) {
        ProjectState state = new ProjectState();
        state.pipelineId   = pipelineId;
        state.rawUserInput = rawUserInput;
        state.status       = PipelineStatus.STARTED;
        state.startedAt    = Instant.now();
        log.info("[{}] ProjectState initialised – input length={}", pipelineId, rawUserInput.length());
        return state;
    }

    // -------------------------------------------------------------------------
    // Safe accessors (Optional wrappers)
    // -------------------------------------------------------------------------

    /** @return RequirementContext wrapped in Optional; empty if the agent has not run yet. */
    public Optional<RequirementContext> getRequirementContextOpt() {
        return Optional.ofNullable(requirementContext);
    }

    /** @return BusinessContext wrapped in Optional; empty if the agent has not run yet. */
    public Optional<BusinessContext> getBusinessContextOpt() {
        return Optional.ofNullable(businessContext);
    }

    /** @return DatabaseContext wrapped in Optional; empty if the agent has not run yet. */
    public Optional<DatabaseContext> getDatabaseContextOpt() {
        return Optional.ofNullable(databaseContext);
    }

    /** @return GanttContext wrapped in Optional; empty if the agent has not run yet. */
    public Optional<GanttContext> getGanttContextOpt() {
        return Optional.ofNullable(ganttContext);
    }

    /** @return RiskContext wrapped in Optional; empty if the agent has not run yet. */
    public Optional<RiskContext> getRiskContextOpt() {
        return Optional.ofNullable(riskContext);
    }

    // -------------------------------------------------------------------------
    // Pipeline status enum
    // -------------------------------------------------------------------------

    /**
     * High-level lifecycle states for a pipeline run.
     */
    public enum PipelineStatus {
        /** Pipeline has been initialised but no agent has run yet. */
        STARTED,
        /** At least one agent is currently executing. */
        IN_PROGRESS,
        /** All agents completed successfully; report has been generated. */
        COMPLETED,
        /**
         * The pipeline stopped early because an agent returned a Null Object
         * result; earlier results are preserved but the report is incomplete.
         */
        PARTIAL_COMPLETE,
        /** A non-recoverable failure occurred; pipeline is halted. */
        FAILED
    }
}
