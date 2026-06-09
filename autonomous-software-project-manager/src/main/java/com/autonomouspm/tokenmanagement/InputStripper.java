package com.autonomouspm.tokenmanagement;

import com.autonomouspm.context.BusinessContext;
import com.autonomouspm.context.BusinessContext.UserStory;
import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.context.DatabaseContext.Relationship;
import com.autonomouspm.context.DatabaseContext.Table;
import com.autonomouspm.context.RequirementContext;
import com.autonomouspm.core.ProjectState;

import java.util.List;

/**
 * Static utility that strips the accumulated pipeline context down to only the
 * fields each agent actually needs before an LLM call.
 *
 * <p>Spec: {@code specs/docs/token-management.md} (Step 4) — Input Stripping.
 * Never pass the full accumulated context forward; every dropped field is saved
 * input tokens.
 *
 * <p><b>Signature note (spec deviation, intentional):</b> the spec lists
 * single-argument signatures (e.g. {@code toDatabaseArchitectInput(BusinessContext)}),
 * but several agents' required fields are spread across more than one context —
 * the Database Architect needs {@code userStories} from {@link BusinessContext}
 * <em>and</em> {@code coreFeatures}/{@code constraints} from
 * {@link RequirementContext}. The spec's {@code ProjectContext} type also does not
 * exist in this codebase; {@link ProjectState} is the real accumulator. Each
 * method therefore accepts exactly the contexts whose fields it must read. The
 * <em>output</em> stripped records honour the spec's field lists precisely.
 *
 * <p>All methods are null-safe: a missing context yields empty collections rather
 * than a {@code NullPointerException}.
 */
public final class InputStripper {

    private InputStripper() {
        // Utility class — no instances.
    }

    // -------------------------------------------------------------------------
    // Business Analyst  ← RequirementContext
    // -------------------------------------------------------------------------

    /**
     * Strips the {@link RequirementContext} to the fields the Business Analyst
     * needs: executive summary, user roles, core features, constraints,
     * assumptions, and non-functional requirements.
     *
     * @param requirements upstream requirement context (may be {@code null})
     * @return a stripped {@link BusinessAnalystInput}
     */
    public static BusinessAnalystInput toBusinessAnalystInput(RequirementContext requirements) {
        if (requirements == null) {
            return new BusinessAnalystInput("", List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return new BusinessAnalystInput(
                safe(requirements.executiveSummary()),
                safe(requirements.userRoles()),
                safe(requirements.coreFeatures()),
                safe(requirements.constraints()),
                safe(requirements.assumptions()),
                safe(requirements.nonFunctionalRequirements())
        );
    }

    // -------------------------------------------------------------------------
    // Database Architect  ← BusinessContext + RequirementContext
    // -------------------------------------------------------------------------

    /**
     * Strips inputs for the Database Architect: {@code userStories} from the
     * {@link BusinessContext}, plus {@code coreFeatures} and {@code constraints}
     * from the {@link RequirementContext}. All other fields
     * ({@code marketPainPoints}, {@code competitorInsights}, {@code businessSummary},
     * etc.) are dropped to manage token cost.
     *
     * @param business     upstream business context (may be {@code null})
     * @param requirements upstream requirement context (may be {@code null})
     * @return a stripped {@link DatabaseArchitectInput}
     */
    public static DatabaseArchitectInput toDatabaseArchitectInput(BusinessContext business,
                                                                  RequirementContext requirements) {
        List<UserStory> userStories = business == null ? List.of() : safe(business.userStories());
        List<String> coreFeatures = requirements == null ? List.of() : safe(requirements.coreFeatures());
        List<String> constraints = requirements == null ? List.of() : safe(requirements.constraints());
        return new DatabaseArchitectInput(userStories, coreFeatures, constraints);
    }

    // -------------------------------------------------------------------------
    // Project Planner  ← DatabaseContext + BusinessContext
    // -------------------------------------------------------------------------

    /**
     * Strips inputs for the Project Planner: {@code tables} and
     * {@code relationships} from the {@link DatabaseContext}, plus {@code epics}
     * from the {@link BusinessContext}.
     *
     * @param database upstream database context (may be {@code null})
     * @param business upstream business context (may be {@code null})
     * @return a stripped {@link ProjectPlannerInput}
     */
    public static ProjectPlannerInput toProjectPlannerInput(DatabaseContext database,
                                                            BusinessContext business) {
        List<Table> tables = database == null ? List.of() : safe(database.tables());
        List<Relationship> relationships = database == null ? List.of() : safe(database.relationships());
        List<String> epics = business == null ? List.of() : safe(business.epics());
        return new ProjectPlannerInput(tables, relationships, epics);
    }

    // -------------------------------------------------------------------------
    // Risk Analyst  ← ProjectState (idea string only)
    // -------------------------------------------------------------------------

    /**
     * Strips inputs for the Risk Analyst to the single field it needs: the user's
     * original project idea ({@code RequirementContext.projectIdea}). The Risk
     * Analyst researches real-world risks for that idea via web search and must
     * NOT receive the full {@link ProjectState}, {@code DatabaseContext},
     * {@code GanttContext}, or {@code BusinessContext} (spec §1, §8).
     *
     * @param state the accumulated pipeline state (may be {@code null})
     * @return the original project idea string, or {@code ""} when unavailable
     */
    public static String toRiskAnalystInput(ProjectState state) {
        if (state == null || state.getRequirementContext() == null) {
            return "";
        }
        return safe(state.getRequirementContext().projectIdea());
    }

    // -------------------------------------------------------------------------
    // Null-safe helpers
    // -------------------------------------------------------------------------

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    // -------------------------------------------------------------------------
    // Stripped input records (one per agent)
    // -------------------------------------------------------------------------

    /** Minimal input for the Business Analyst Agent. */
    public record BusinessAnalystInput(
            String executiveSummary,
            List<String> userRoles,
            List<String> coreFeatures,
            List<String> constraints,
            List<String> assumptions,
            List<String> nonFunctionalRequirements
    ) {}

    /** Minimal input for the Database Architect Agent. */
    public record DatabaseArchitectInput(
            List<UserStory> userStories,
            List<String> coreFeatures,
            List<String> constraints
    ) {}

    /** Minimal input for the Project Planner Agent. */
    public record ProjectPlannerInput(
            List<Table> tables,
            List<Relationship> relationships,
            List<String> epics
    ) {}
}
