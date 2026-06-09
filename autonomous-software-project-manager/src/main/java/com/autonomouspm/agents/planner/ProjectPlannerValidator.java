package com.autonomouspm.agents.planner;

import com.autonomouspm.context.GanttContext.ProjectComponent;
import com.autonomouspm.context.GanttContext.ProjectNode;
import com.autonomouspm.context.GanttContext.TaskLeaf;
import com.autonomouspm.context.PlanningValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates a Project Planner composite tree ({@link ProjectNode}) before the
 * Gantt chart is generated.
 *
 * <p>Spec: {@code specs/modules/project-planner.md §6 ProjectPlannerValidator}.
 *
 * <p><b>Non-fatal contract:</b> this validator never throws and never returns
 * {@code null}. On failure it logs warnings via SLF4J and returns an
 * {@link Optional} carrying a single {@link PlanningValidationError}; on success
 * it returns {@link Optional#empty()}. The agent uses a present error as the
 * signal to skip Gantt generation and fall back to the {@code EmptyGanttContext}
 * Null Object.
 *
 * <p>Validation rules (spec §6):
 * <ul>
 *   <li>At least one phase exists.</li>
 *   <li>At least one task exists.</li>
 *   <li>Every task belongs to a phase (no leaf directly under the root).</li>
 *   <li>Every dependency references an existing task name.</li>
 *   <li>No circular dependencies (DFS check).</li>
 *   <li>No orphan tasks (here: no empty phase — every phase holds ≥1 task).</li>
 *   <li>Phase names match the allowed list only.</li>
 * </ul>
 *
 * <p>Holds no mutable state — only immutable rule constants — so it is
 * thread-safe and freely shareable as a Spring {@code @Component}.
 */
@Component
public class ProjectPlannerValidator {

    private static final Logger log = LoggerFactory.getLogger(ProjectPlannerValidator.class);

    private static final String AGENT_NAME = "ProjectPlannerAgent";

    /** Canonical allowed phase names (spec §3), keyed lower-case for matching. */
    private static final Set<String> ALLOWED_PHASES = Set.of(
            "requirements",
            "design",
            "database",
            "backend development",
            "frontend development",
            "integration",
            "testing",
            "deployment"
    );

    /**
     * Validates the composite tree rooted at {@code root}.
     *
     * @param root the project root produced by the Project Planner Agent (may be {@code null})
     * @return {@link Optional#empty()} when valid; otherwise an {@link Optional}
     *         carrying the first {@link PlanningValidationError} encountered.
     *         Never {@code null}.
     */
    public Optional<PlanningValidationError> validate(ProjectNode root) {
        if (root == null) {
            return fail("NULL_ROOT", "Project root is null. Cannot validate a missing plan.");
        }

        List<ProjectComponent> children = root.children() == null ? List.of() : root.children();

        // --- Rule: tasks must not hang directly off the root (must live in a phase) ---
        for (ProjectComponent child : children) {
            if (child instanceof TaskLeaf leaf) {
                return fail("TASK_OUTSIDE_PHASE",
                        "Task '" + safeName(leaf.name()) + "' is attached to the root instead of a phase.");
            }
        }

        // --- Collect phases (ProjectNode children of root) ---
        List<ProjectNode> phases = new ArrayList<>();
        for (ProjectComponent child : children) {
            if (child instanceof ProjectNode phase) {
                phases.add(phase);
            }
        }

        // --- Rule: at least one phase ---
        if (phases.isEmpty()) {
            return fail("NO_PHASES", "Plan contains no phases. At least one phase is required.");
        }

        // --- Rule: phase names match the allowed list ---
        for (ProjectNode phase : phases) {
            String name = phase.name() == null ? "" : phase.name().trim();
            if (!ALLOWED_PHASES.contains(name.toLowerCase(Locale.ROOT))) {
                return fail("INVALID_PHASE_NAME",
                        "Phase '" + safeName(phase.name()) + "' is not in the allowed phase list.");
            }
        }

        // --- Rule: no orphan tasks (every phase must hold at least one task) ---
        for (ProjectNode phase : phases) {
            if (countTasks(phase) == 0) {
                return fail("ORPHAN_TASK",
                        "Phase '" + safeName(phase.name()) + "' contains no tasks.");
            }
        }

        // --- Gather every leaf task across all phases ---
        Map<String, TaskLeaf> tasksByName = collectTasks(phases);

        // --- Rule: at least one task ---
        if (tasksByName.isEmpty()) {
            return fail("NO_TASKS", "Plan contains no tasks. At least one task is required.");
        }

        // --- Rule: every dependency references an existing task name ---
        for (TaskLeaf task : tasksByName.values()) {
            List<String> deps = task.dependencies() == null ? List.of() : task.dependencies();
            for (String dep : deps) {
                if (dep == null || dep.isBlank()) {
                    continue;
                }
                if (!tasksByName.containsKey(dep.trim())) {
                    return fail("UNKNOWN_DEPENDENCY",
                            "Task '" + safeName(task.name()) + "' depends on unknown task '" + dep.trim() + "'.");
                }
            }
        }

        // --- Rule: no circular dependencies (DFS over the dependency graph) ---
        Optional<PlanningValidationError> cycle = detectCycle(tasksByName);
        if (cycle.isPresent()) {
            return cycle;
        }

        log.debug("ProjectPlannerValidator – plan valid: {} phase(s), {} task(s).",
                phases.size(), tasksByName.size());
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Task collection
    // -------------------------------------------------------------------------

    /**
     * Collects every leaf task across all phases, keyed by trimmed name. A later
     * duplicate name keeps the first occurrence (the dependency graph treats a
     * name as a single node).
     */
    private Map<String, TaskLeaf> collectTasks(List<ProjectNode> phases) {
        Map<String, TaskLeaf> tasks = new LinkedHashMap<>();
        for (ProjectNode phase : phases) {
            collectTasks(phase, tasks);
        }
        return tasks;
    }

    private void collectTasks(ProjectComponent component, Map<String, TaskLeaf> tasks) {
        if (component instanceof TaskLeaf leaf) {
            if (leaf.name() != null && !leaf.name().isBlank()) {
                tasks.putIfAbsent(leaf.name().trim(), leaf);
            }
            return;
        }
        if (component != null && component.children() != null) {
            for (ProjectComponent child : component.children()) {
                collectTasks(child, tasks);
            }
        }
    }

    /** Counts leaf tasks reachable beneath a phase node. */
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

    // -------------------------------------------------------------------------
    // Cycle detection (DFS, three-colour)
    // -------------------------------------------------------------------------

    /**
     * Detects a circular dependency using iterative depth-first search with the
     * classic three-colour marking (white/unseen, grey/on-stack, black/done).
     * A grey node reached again closes a cycle.
     *
     * @param tasksByName the full task set, keyed by name
     * @return an {@link Optional} carrying a {@code CIRCULAR_DEPENDENCY} error when
     *         a cycle exists; otherwise {@link Optional#empty()}
     */
    private Optional<PlanningValidationError> detectCycle(Map<String, TaskLeaf> tasksByName) {
        Map<String, Integer> colour = new HashMap<>();   // 0=white, 1=grey, 2=black
        for (String start : tasksByName.keySet()) {
            if (colour.getOrDefault(start, 0) != 0) {
                continue;
            }
            Deque<String> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                String node = stack.peek();
                int state = colour.getOrDefault(node, 0);
                if (state == 0) {
                    colour.put(node, 1); // mark grey on first visit
                    for (String dep : dependenciesOf(node, tasksByName)) {
                        int depColour = colour.getOrDefault(dep, 0);
                        if (depColour == 1) {
                            return fail("CIRCULAR_DEPENDENCY",
                                    "Circular dependency detected involving task '" + dep + "'.");
                        }
                        if (depColour == 0) {
                            stack.push(dep);
                        }
                    }
                } else {
                    if (state == 1) {
                        colour.put(node, 2); // all descendants processed → black
                    }
                    stack.pop();
                }
            }
        }
        return Optional.empty();
    }

    /** Returns the existing-task dependency names for a node (blanks/unknowns filtered). */
    private List<String> dependenciesOf(String taskName, Map<String, TaskLeaf> tasksByName) {
        TaskLeaf task = tasksByName.get(taskName);
        if (task == null || task.dependencies() == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String dep : task.dependencies()) {
            if (dep == null || dep.isBlank()) {
                continue;
            }
            String trimmed = dep.trim();
            if (tasksByName.containsKey(trimmed) && seen.add(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Logs the violation and wraps it in a {@link PlanningValidationError}. Central
     * helper so every rule logs consistently and returns a non-null {@link Optional}.
     */
    private Optional<PlanningValidationError> fail(String errorCode, String message) {
        log.warn("ProjectPlannerValidator – {} : {}", errorCode, message);
        return Optional.of(new PlanningValidationError(AGENT_NAME, errorCode, message));
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "<unnamed>" : name.trim();
    }
}
