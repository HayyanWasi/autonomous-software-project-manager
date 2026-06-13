package com.autonomouspm.core;

import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.context.GanttContext;
import com.autonomouspm.context.RequirementContext;
import com.autonomouspm.context.RiskContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA Entity mapping for the {@link ProjectState}.
 *
 * <p>Uses Hibernate 6's native {@code @JdbcTypeCode(SqlTypes.JSON)} to map complex
 * context objects (like {@link GanttContext}) into PostgreSQL {@code JSONB} columns.
 * This avoids the need to build dozens of relational tables for nested structures
 * (like {@code UserStory}, {@code Table}, {@code Column}, etc.), keeping the
 * persistence layer fast and simple.
 */
@Entity
@Table(name = "project_state")
@Getter
@Setter
public class ProjectStateEntity {

    @Id
    @Column(name = "pipeline_id", updatable = false, nullable = false)
    private String pipelineId;

    @Column(name = "raw_user_input", columnDefinition = "TEXT")
    private String rawUserInput;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectState.PipelineStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    // -------------------------------------------------------------------------
    // JSONB Columns for Context Objects
    // -------------------------------------------------------------------------

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirement_context", columnDefinition = "jsonb")
    private RequirementContext requirementContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_context", columnDefinition = "jsonb")
    private BusinessContext businessContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "database_context", columnDefinition = "jsonb")
    private DatabaseContext databaseContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gantt_context", columnDefinition = "jsonb")
    private GanttContext ganttContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_context", columnDefinition = "jsonb")
    private RiskContext riskContext;

    @Column(name = "final_report", columnDefinition = "TEXT")
    private String finalReport;

    // -------------------------------------------------------------------------
    // Conversion Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a transient {@link ProjectState} object into a persistable JPA entity.
     */
    public static ProjectStateEntity fromState(ProjectState state) {
        ProjectStateEntity entity = new ProjectStateEntity();
        entity.setPipelineId(state.getPipelineId());
        entity.setRawUserInput(state.getRawUserInput());
        entity.setStatus(state.getStatus());
        entity.setStartedAt(state.getStartedAt());
        
        entity.setRequirementContext(state.getRequirementContext());
        entity.setBusinessContext(state.getBusinessContext());
        entity.setDatabaseContext(state.getDatabaseContext());
        entity.setGanttContext(state.getGanttContext());
        entity.setRiskContext(state.getRiskContext());
        entity.setFinalReport(state.getFinalReport());

        return entity;
    }

    /**
     * Reconstructs a transient {@link ProjectState} from this JPA entity.
     */
    public ProjectState toState() {
        ProjectState state = new ProjectState();
        state.setPipelineId(this.pipelineId);
        state.setRawUserInput(this.rawUserInput);
        state.setStatus(this.status);
        state.setStartedAt(this.startedAt);
        
        state.setRequirementContext(this.requirementContext);
        state.setBusinessContext(this.businessContext);
        state.setDatabaseContext(this.databaseContext);
        state.setGanttContext(this.ganttContext);
        state.setRiskContext(this.riskContext);
        state.setFinalReport(this.finalReport);

        return state;
    }
}
