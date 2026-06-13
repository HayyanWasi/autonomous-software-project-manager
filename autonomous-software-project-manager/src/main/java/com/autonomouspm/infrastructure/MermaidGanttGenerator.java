package com.autonomouspm.infrastructure;

import com.autonomouspm.context.GanttContext.ProjectComponent;
import com.autonomouspm.context.GanttContext.ProjectNode;
import com.autonomouspm.context.GanttContext.TaskLeaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates a <a href="https://mermaid.js.org/syntax/gantt.html">Mermaid Gantt</a>
 * string from a Project Planner composite tree.
 *
 * <p>Spec: {@code specs/modules/project-planner.md §5 MermaidGanttGenerator}.
 *
 * <p>This is <b>pure Java logic — no LLM is involved</b>. The Gantt chart is a
 * secondary visualisation; the authoritative plan remains the
 * {@link ProjectNode} composite tree on {@code GanttContext}.
 *
 * <p>Output example:
 * <pre>
 * gantt
 * title Project Plan
 * dateFormat YYYY-MM-DD
 * section Backend Development
 * User Authentication :task1, 2024-01-01, 4d
 * Order Management :task2, after task1, 7d
 * </pre>
 *
 * <p><b>Complexity → placeholder duration</b> (spec §5). Note these are visual
 * placeholders only — the planner does not own real duration/effort estimates:
 * <ul>
 *   <li>{@code Small}      → {@code 2d}</li>
 *   <li>{@code Medium}     → {@code 4d}</li>
 *   <li>{@code Large}      → {@code 7d}</li>
 *   <li>{@code Very Large} → {@code 12d}</li>
 * </ul>
 *
 * <p>All methods are null-safe and never throw: malformed input yields the
 * best-effort chart (at minimum the {@code gantt} header block).
 */
@Component
public class MermaidGanttGenerator {

    private static final Logger log = LoggerFactory.getLogger(MermaidGanttGenerator.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Baseline project start date (spec §5: {@code 2024-01-01}, incremented per phase). */
    private static final LocalDate BASELINE_START = LocalDate.of(2024, 1, 1);

    /** Default placeholder duration (days) when a complexity value is unknown. */
    private static final int DEFAULT_DURATION_DAYS = 4;

    private static final String HEADER = """
            gantt
            title Project Plan
            dateFormat YYYY-MM-DD""";

    /**
     * Builds a Mermaid Gantt string from the given project root.
     *
     * <p>Each direct child of {@code root} renders as a {@code section}; each
     * {@link TaskLeaf} renders as a task entry. Task ids are assigned globally
     * ({@code task1}, {@code task2}, …) so dependencies can be resolved across
     * sections. A task with no resolvable dependency anchors to its section's
     * start date; otherwise it uses Mermaid {@code after <id>} sequencing.
     *
     * @param root the composite project root (may be {@code null})
     * @return a Mermaid Gantt string; never {@code null}
     */
    public String generate(ProjectNode root) {
        StringBuilder sb = new StringBuilder(HEADER);

        if (root == null || root.children() == null || root.children().isEmpty()) {
            log.warn("MermaidGanttGenerator – empty project tree; emitting header only.");
            return sb.toString();
        }

        // Pass 1: assign a stable id to every leaf task so dependencies (which
        // reference task *names*) can be rewritten as Mermaid task ids.
        Map<String, String> taskIdsByName = assignTaskIds(root);

        // Pass 2: emit sections and tasks, incrementing the start date per phase.
        LocalDate sectionStart = BASELINE_START;
        for (ProjectComponent child : root.children()) {
            if (!(child instanceof ProjectNode phase) || isBlank(phase.name())) {
                continue;
            }
            sb.append('\n').append("section ").append(phase.name().trim());

            int phaseDurationDays = appendPhaseTasks(sb, phase, sectionStart, taskIdsByName);

            // Increment the baseline for the next phase by this phase's total
            // placeholder duration so sections lay out sequentially.
            sectionStart = sectionStart.plusDays(Math.max(phaseDurationDays, 1));
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Pass 1 — id assignment
    // -------------------------------------------------------------------------

    /**
     * Walks the tree and assigns {@code task1..taskN} ids to every leaf task,
     * keyed by the task's (trimmed) name. Later duplicate names keep the first id.
     */
    private Map<String, String> assignTaskIds(ProjectNode root) {
        Map<String, String> ids = new LinkedHashMap<>();
        int[] counter = {0};
        collectTaskIds(root, ids, counter);
        return ids;
    }

    private void collectTaskIds(ProjectComponent component, Map<String, String> ids, int[] counter) {
        if (component instanceof TaskLeaf leaf) {
            if (!isBlank(leaf.name())) {
                ids.putIfAbsent(leaf.name().trim(), "task" + (++counter[0]));
            }
            return;
        }
        if (component != null && component.children() != null) {
            for (ProjectComponent child : component.children()) {
                collectTaskIds(child, ids, counter);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2 — section / task emission
    // -------------------------------------------------------------------------

    /**
     * Appends every leaf task in {@code phase} and returns the phase's total
     * placeholder duration in days (used to offset the next section's start).
     */
    private int appendPhaseTasks(StringBuilder sb,
                                 ProjectNode phase,
                                 LocalDate sectionStart,
                                 Map<String, String> taskIdsByName) {
        int totalDays = 0;
        if (phase.children() == null) {
            return totalDays;
        }
        for (ProjectComponent component : phase.children()) {
            if (!(component instanceof TaskLeaf leaf) || isBlank(leaf.name())) {
                continue;
            }
            int durationDays = toDurationDays(leaf.complexity());
            appendTask(sb, leaf, sectionStart, durationDays, taskIdsByName);
            totalDays += durationDays;
        }
        return totalDays;
    }

    /**
     * Appends one task line: {@code <name> :<id>, <start|after ids>, <Nd>}.
     * Dependencies are resolved from task names to ids; unknown names are skipped
     * (the validator owns dependency correctness — the generator is best-effort).
     */
    private void appendTask(StringBuilder sb,
                            TaskLeaf leaf,
                            LocalDate sectionStart,
                            int durationDays,
                            Map<String, String> taskIdsByName) {
        String id = taskIdsByName.get(leaf.name().trim());
        if (id == null) {
            return;
        }

        String anchor = resolveAnchor(leaf, sectionStart, taskIdsByName);

        sb.append('\n')
                .append(leaf.name().trim()).append(" :")
                .append(id).append(", ")
                .append(anchor).append(", ")
                .append(durationDays).append('d');
    }

    /**
     * Computes a task's Mermaid start anchor: either {@code after <id> <id>...}
     * when it has resolvable dependencies, or an explicit section start date.
     */
    private String resolveAnchor(TaskLeaf leaf,
                                 LocalDate sectionStart,
                                 Map<String, String> taskIdsByName) {
        List<String> deps = leaf.dependencies();
        if (deps != null && !deps.isEmpty()) {
            List<String> depIds = new ArrayList<>();
            for (String depName : deps) {
                if (isBlank(depName)) {
                    continue;
                }
                String depId = taskIdsByName.get(depName.trim());
                if (depId != null && !depId.equals(taskIdsByName.get(leaf.name().trim()))) {
                    depIds.add(depId);
                }
            }
            if (!depIds.isEmpty()) {
                return "after " + String.join(" ", depIds);
            }
        }
        return sectionStart.format(DATE_FORMAT);
    }

    // -------------------------------------------------------------------------
    // Complexity → duration
    // -------------------------------------------------------------------------

    /**
     * Maps a complexity descriptor to its placeholder duration in days. Accepts the
     * spec values (case-insensitive, hyphen/space tolerant); unknown values fall
     * back to {@value #DEFAULT_DURATION_DAYS} with a warning.
     */
    private int toDurationDays(String complexity) {
        if (isBlank(complexity)) {
            return DEFAULT_DURATION_DAYS;
        }
        String normalized = complexity.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return switch (normalized) {
            case "SMALL" -> 2;
            case "MEDIUM" -> 4;
            case "LARGE" -> 7;
            case "VERY_LARGE" -> 12;
            default -> {
                log.warn("MermaidGanttGenerator – unknown complexity '{}', using default {}d",
                        complexity, DEFAULT_DURATION_DAYS);
                yield DEFAULT_DURATION_DAYS;
            }
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
