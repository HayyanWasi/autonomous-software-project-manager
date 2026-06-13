package com.autonomouspm.core;

import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.context.DatabaseContext.Table;
import com.autonomouspm.context.GanttContext;
import com.autonomouspm.context.GanttContext.ProjectComponent;
import com.autonomouspm.context.GanttContext.ProjectNode;
import com.autonomouspm.context.GanttContext.TaskLeaf;
import com.autonomouspm.context.RequirementContext;
import com.autonomouspm.context.RiskContext;
import com.autonomouspm.context.RiskContext.RiskFactor;

import java.util.List;

/**
 * BUILDER PATTERN — assembles the final project report as a Markdown string,
 * section by section.
 *
 * <p>Spec: {@code specs/modules/central-orchestrator-agent.md §4 File 2}
 *
 * <p>Each {@code withX(...)} method appends one agent's section and returns
 * {@code this} for fluent chaining; {@link #build()} is the terminal operation
 * that returns the assembled Markdown.
 *
 * <p><b>Format rules (spec §4):</b>
 * <ul>
 *   <li>Each section starts with a {@code ##} heading.</li>
 *   <li>Lists use {@code -} bullet points.</li>
 *   <li>If a context is {@code null} or empty, the section is skipped entirely.</li>
 *   <li>The literal words "null"/"empty" are never written into the report.</li>
 *   <li><b>No Mermaid diagrams are embedded</b> — React renders them directly from
 *       {@code databaseContext.mermaidErdChart} and
 *       {@code ganttContext.mermaidGanttChart}.</li>
 * </ul>
 *
 * <p>Not a Spring bean: the orchestrator instantiates a fresh builder per pipeline
 * run (each instance is single-use and stateful).
 */
public class ProjectReportBuilder {

    private static final String NL = "\n";

    private final StringBuilder report = new StringBuilder();

    /**
     * Writes the report title.
     *
     * @param projectIdea the original user idea (blank-safe)
     * @return this builder
     */
    public ProjectReportBuilder withHeader(String projectIdea) {
        String idea = isBlank(projectIdea) ? "Untitled Project" : projectIdea.trim();
        report.append("# Project Report: ").append(idea).append(NL).append(NL);
        return this;
    }

    /**
     * Appends the requirements summary: core features and constraints.
     * Skipped entirely when the context is null or carries no usable lists.
     *
     * @param ctx the requirement context (may be {@code null})
     * @return this builder
     */
    public ProjectReportBuilder withRequirements(RequirementContext ctx) {
        if (ctx == null) {
            return this;
        }
        boolean hasFeatures = isNotEmpty(ctx.coreFeatures());
        boolean hasConstraints = isNotEmpty(ctx.constraints());
        if (!hasFeatures && !hasConstraints) {
            return this;
        }

        heading("Requirements Summary");
        if (hasFeatures) {
            line("**Functional Requirements**");
            bullets(ctx.coreFeatures());
        }
        if (hasConstraints) {
            line("**Constraints**");
            bullets(ctx.constraints());
        }
        report.append(NL);
        return this;
    }

    /**
     * Appends the business analysis: epics, user-story count, core features.
     *
     * @param ctx the business context (may be {@code null})
     * @return this builder
     */
    public ProjectReportBuilder withBusinessAnalysis(BusinessContext ctx) {
        if (ctx == null) {
            return this;
        }
        boolean hasEpics = isNotEmpty(ctx.epics());
        boolean hasStories = isNotEmpty(ctx.userStories());
        boolean hasFeatures = isNotEmpty(ctx.recommendedFeatures());
        if (!hasEpics && !hasStories && !hasFeatures) {
            return this;
        }

        heading("Business Analysis");
        if (hasEpics) {
            line("**Epics**");
            bullets(ctx.epics());
        }
        if (hasStories) {
            line("**Total User Stories:** " + ctx.userStories().size());
        }
        if (hasFeatures) {
            line("**Recommended Features**");
            bullets(ctx.recommendedFeatures());
        }
        report.append(NL);
        return this;
    }

    /**
     * Appends the database design: table names with descriptions. The ERD diagram
     * is NOT embedded — a note points to the UI-rendered chart.
     *
     * @param ctx the database context (may be {@code null})
     * @return this builder
     */
    public ProjectReportBuilder withDatabaseDesign(DatabaseContext ctx) {
        if (ctx == null || !isNotEmpty(ctx.tables())) {
            return this;
        }

        heading("Database Design");
        for (Table table : ctx.tables()) {
            if (table == null || isBlank(table.name())) {
                continue;
            }
            String desc = isBlank(table.description()) ? "" : " — " + table.description().trim();
            line("- **" + table.name().trim() + "**" + desc);
        }
        line("*ERD diagram rendered in UI*");
        report.append(NL);
        return this;
    }

    /**
     * Appends the project plan: each phase with its task count. The Gantt chart is
     * NOT embedded — a note points to the UI-rendered chart.
     *
     * @param ctx the gantt context (may be {@code null})
     * @return this builder
     */
    public ProjectReportBuilder withProjectPlan(GanttContext ctx) {
        if (ctx == null || ctx.rootProject() == null) {
            return this;
        }
        List<ProjectComponent> phases = ctx.rootProject().children();
        if (phases == null || phases.isEmpty()) {
            return this;
        }

        heading("Project Plan");
        boolean wrote = false;
        for (ProjectComponent child : phases) {
            if (child instanceof ProjectNode phase && !isBlank(phase.name())) {
                line("- **" + phase.name().trim() + "**: " + countTasks(phase) + " task(s)");
                wrote = true;
            }
        }
        if (!wrote) {
            return this;
        }
        line("*Gantt chart rendered in UI*");
        report.append(NL);
        return this;
    }

    /**
     * Appends the risk assessment: overall level/score plus each risk factor's
     * category, description and mitigation.
     *
     * @param ctx the risk context (may be {@code null})
     * @return this builder
     */
    public ProjectReportBuilder withRiskAnalysis(RiskContext ctx) {
        if (ctx == null || !isNotEmpty(ctx.riskFactors())) {
            return this;
        }

        heading("Risk Assessment");
        if (!isBlank(ctx.overallRiskLevel())) {
            line("**Overall Risk Level:** " + ctx.overallRiskLevel().trim());
        }
        line("**Overall Risk Score:** " + ctx.overallRiskScore());
        report.append(NL);

        for (RiskFactor factor : ctx.riskFactors()) {
            if (factor == null) {
                continue;
            }
            String category = isBlank(factor.category()) ? "General" : factor.category().trim();
            String description = isBlank(factor.description()) ? "" : factor.description().trim();
            line("- **" + category + "**: " + description);
            if (!isBlank(factor.mitigationStrategy())) {
                line("  - *Mitigation:* " + factor.mitigationStrategy().trim());
            }
        }
        report.append(NL);
        return this;
    }

    /**
     * Appends the report footer.
     *
     * @return this builder
     */
    public ProjectReportBuilder withFooter() {
        report.append("---").append(NL).append("*Generated by Autonomous Project Manager*").append(NL);
        return this;
    }

    /**
     * BUILDER PATTERN — terminal operation.
     *
     * @return the assembled Markdown report
     */
    public String build() {
        return report.toString();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void heading(String title) {
        report.append("## ").append(title).append(NL).append(NL);
    }

    private void line(String text) {
        report.append(text).append(NL);
    }

    /** Appends each non-blank item as a {@code -} bullet. */
    private void bullets(List<String> items) {
        for (String item : items) {
            if (!isBlank(item)) {
                report.append("- ").append(item.trim()).append(NL);
            }
        }
    }

    /** Counts leaf tasks reachable beneath a component. */
    private int countTasks(ProjectComponent component) {
        if (component instanceof TaskLeaf) {
            return 1;
        }
        int total = 0;
        if (component != null && component.children() != null) {
            for (ProjectComponent child : component.children()) {
                total += countTasks(child);
            }
        }
        return total;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
