package com.autonomouspm.core;

import com.autonomouspm.agents.business.BusinessAnalystAgent;
import com.autonomouspm.agents.database.DatabaseArchitectAgent;
import com.autonomouspm.agents.planner.ProjectPlannerAgent;
import com.autonomouspm.agents.requirement.RequirementAnalystAgent;
import com.autonomouspm.agents.risk.RiskAnalystAgent;
import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.context.GanttContext;
import com.autonomouspm.context.RequirementContext;
import com.autonomouspm.context.RiskContext;
import com.autonomouspm.infrastructure.AgentTask;
import com.autonomouspm.infrastructure.LlmAgentTask;
import com.autonomouspm.observer.EventLogger;
import com.autonomouspm.observer.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * MEDIATOR PATTERN — CentralOrchestrator.
 *
 * <p>Spec: {@code specs/modules/central-orchestrator-agent.md §5 File 3}
 *
 * <p>Agents never call each other directly. Every agent reports to this
 * orchestrator, which decides what runs next. After each agent completes, its
 * result is <b>immediately streamed to the React UI</b> via {@link SseEmitter}
 * before the next agent starts.
 *
 * <p>Pipeline order (Chain of Responsibility): Requirement → Business → Database
 * → Planner → Risk. After all five succeed, a Markdown report is assembled by
 * {@link ProjectReportBuilder} (Builder pattern) and streamed as the terminal
 * {@code pipeline-complete} event.
 *
 * <p><b>Resilience:</b> {@link #runPipeline(String, SseEmitter)} never throws. A
 * Null-Object result halts the pipeline with {@code PARTIAL_COMPLETE}; an
 * exception halts it with {@code FAILED} and completes the emitter with the error.
 */
@Service
public class CentralOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CentralOrchestrator.class);

    // FACTORY PATTERN — the Spring container instantiates and injects the correct
    // Agent implementation for each pipeline stage; no manual AgentFactory class
    // is needed. Each agent is a @Component discovered and wired by Spring.
    private final RequirementAnalystAgent requirementAgent;
    private final BusinessAnalystAgent businessAgent;
    private final DatabaseArchitectAgent databaseAgent;
    private final ProjectPlannerAgent plannerAgent;
    private final RiskAnalystAgent riskAgent;
    private final ProjectStateRepository stateRepository;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper;

    /**
     * FACTORY PATTERN — Spring supplies every collaborator via constructor
     * injection; the orchestrator never constructs an agent itself.
     */
    public CentralOrchestrator(RequirementAnalystAgent requirementAgent,
                               BusinessAnalystAgent businessAgent,
                               DatabaseArchitectAgent databaseAgent,
                               ProjectPlannerAgent plannerAgent,
                               RiskAnalystAgent riskAgent,
                               ProjectStateRepository stateRepository,
                               EventLogger eventLogger,
                               ObjectMapper objectMapper) {
        this.requirementAgent = requirementAgent;
        this.businessAgent    = businessAgent;
        this.databaseAgent    = databaseAgent;
        this.plannerAgent     = plannerAgent;
        this.riskAgent        = riskAgent;
        this.stateRepository  = stateRepository;
        this.eventLogger      = eventLogger;
        this.objectMapper     = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Pipeline entry point
    // -------------------------------------------------------------------------

    /**
     * Runs the full agent pipeline for {@code userIdea}, streaming one SSE event
     * per completed agent and a terminal event at the end.
     *
     * <p>Never throws: a Null-Object result ends the run as {@code PARTIAL_COMPLETE};
     * any exception ends it as {@code FAILED} via {@link SseEmitter#completeWithError}.
     *
     * @param userIdea the raw project idea from the user
     * @param emitter  the SSE emitter connected to the React client
     */
    public void runPipeline(String userIdea, SseEmitter emitter) {
        String pipelineId = UUID.randomUUID().toString();
        ProjectState state = ProjectState.init(pipelineId, userIdea);
        state.setStatus(ProjectState.PipelineStatus.IN_PROGRESS);

        eventLogger.publish("CentralOrchestrator", EventType.PIPELINE_STARTED,
                "Pipeline started – id=" + pipelineId);
        persist(state);

        // BRIDGE PATTERN — agents are sequenced as AgentTask abstractions; the
        // orchestrator never depends on how each task executes internally.
        List<AgentTask<?>> tasks = List.of(
                new LlmAgentTask<>(requirementAgent, requirementAgent.getName(), "requirement-complete"),
                new LlmAgentTask<>(businessAgent, businessAgent.getName(), "business-complete"),
                new LlmAgentTask<>(databaseAgent, databaseAgent.getName(), "database-complete"),
                new LlmAgentTask<>(plannerAgent, plannerAgent.getName(), "planner-complete"),
                new LlmAgentTask<>(riskAgent, riskAgent.getName(), "risk-complete")
        );

        try {
            for (AgentTask<?> task : tasks) {
                Object result = task.execute(state);

                // Write the result back into the shared state and persist immediately.
                writeBack(state, result);
                persist(state);

                if (isNullObject(result)) {
                    // Null Object → pipeline cannot meaningfully continue.
                    log.warn("CentralOrchestrator – agent '{}' returned a Null Object; stopping as PARTIAL_COMPLETE.",
                            task.agentName());
                    emit(emitter, task.eventName(),
                            new PipelineEvent(task.eventName(), "PARTIAL", result, "Agent failed"));
                    state.setStatus(ProjectState.PipelineStatus.PARTIAL_COMPLETE);
                    persist(state);
                    emitter.complete();
                    return;
                }

                emit(emitter, task.eventName(),
                        new PipelineEvent(task.eventName(), "COMPLETED", result,
                                task.agentName() + " completed"));
            }

            // ---- All agents succeeded: assemble and stream the final report ----
            String report = new ProjectReportBuilder()
                    .withHeader(userIdea)
                    .withRequirements(state.getRequirementContext())
                    .withBusinessAnalysis(state.getBusinessContext())
                    .withDatabaseDesign(state.getDatabaseContext())
                    .withProjectPlan(state.getGanttContext())
                    .withRiskAnalysis(state.getRiskContext())
                    .withFooter()
                    .build();

            state.setFinalReport(report);
            state.setStatus(ProjectState.PipelineStatus.COMPLETED);
            persist(state);

            eventLogger.publish("CentralOrchestrator", EventType.PIPELINE_COMPLETED,
                    "Pipeline completed – id=" + pipelineId);
            emit(emitter, "pipeline-complete",
                    new PipelineEvent("pipeline-complete", "COMPLETED", report, "Pipeline completed"));
            emitter.complete();

        } catch (Exception e) {
            // runPipeline must never throw — surface the failure to the client.
            log.error("CentralOrchestrator – pipeline {} failed: {}", pipelineId, e.getMessage(), e);
            state.setStatus(ProjectState.PipelineStatus.FAILED);
            persist(state);
            eventLogger.publish("CentralOrchestrator", EventType.PIPELINE_FAILED,
                    "Pipeline failed: " + e.getMessage());
            safeEmit(emitter, "pipeline-failed",
                    new PipelineEvent("pipeline-failed", "FAILED", null,
                            e.getMessage() == null ? "Pipeline failed" : e.getMessage()));
            emitter.completeWithError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Result routing (Mediator write-back) & Null-Object detection
    // -------------------------------------------------------------------------

    /**
     * Writes an agent's typed result back into the shared {@link ProjectState}.
     * Agents already set their context on success; this makes the write-back
     * explicit and idempotent regardless of success or Null-Object outcome.
     */
    private void writeBack(ProjectState state, Object result) {
        if (result instanceof RequirementContext rc) {
            state.setRequirementContext(rc);
        } else if (result instanceof BusinessContext bc) {
            state.setBusinessContext(bc);
        } else if (result instanceof DatabaseContext dc) {
            state.setDatabaseContext(dc);
        } else if (result instanceof GanttContext gc) {
            state.setGanttContext(gc);
        } else if (result instanceof RiskContext rk) {
            state.setRiskContext(rk);
        }
    }

    /**
     * Detects a Null-Object result per spec §5:
     * <ul>
     *   <li>RequirementContext → no core features</li>
     *   <li>BusinessContext → no user stories</li>
     *   <li>DatabaseContext → no tables</li>
     *   <li>GanttContext → {@code totalTasks == 0}</li>
     *   <li>RiskContext → no risk factors</li>
     * </ul>
     * A {@code null} result is treated as a failure.
     */
    private boolean isNullObject(Object result) {
        if (result == null) {
            return true;
        }
        if (result instanceof RequirementContext rc) {
            return isEmpty(rc.coreFeatures());
        }
        if (result instanceof BusinessContext bc) {
            return isEmpty(bc.userStories());
        }
        if (result instanceof DatabaseContext dc) {
            return isEmpty(dc.tables());
        }
        if (result instanceof GanttContext gc) {
            return gc.totalTasks() == 0;
        }
        if (result instanceof RiskContext rk) {
            return isEmpty(rk.riskFactors());
        }
        return false;
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    // -------------------------------------------------------------------------
    // SSE & persistence helpers
    // -------------------------------------------------------------------------

    /**
     * Sends one SSE event. Propagates {@link java.io.IOException} as an unchecked
     * failure so the surrounding {@code try/catch} routes it to the
     * {@code pipeline-failed} path.
     */
    private void emit(SseEmitter emitter, String name, PipelineEvent event) {
        try {
            emitter.send(SseEmitter.event().name(name).data(event));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send SSE event '" + name + "': " + e.getMessage(), e);
        }
    }

    /**
     * Best-effort SSE send used on the failure path — swallows any error so the
     * subsequent {@link SseEmitter#completeWithError} still runs.
     */
    private void safeEmit(SseEmitter emitter, String name, PipelineEvent event) {
        try {
            emitter.send(SseEmitter.event().name(name).data(event));
        } catch (Exception e) {
            log.warn("CentralOrchestrator – failed to send '{}' event during error handling: {}",
                    name, e.getMessage());
        }
    }

    /** Persists the current transient state to PostgreSQL. */
    private void persist(ProjectState state) {
        ProjectStateEntity entity = ProjectStateEntity.fromState(state);
        stateRepository.save(entity);
        log.debug("CentralOrchestrator – saved state for pipelineId={}", state.getPipelineId());
    }
}
