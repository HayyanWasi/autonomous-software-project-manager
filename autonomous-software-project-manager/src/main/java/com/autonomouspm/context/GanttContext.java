package com.autonomouspm.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.List;

/**
 * Immutable output context produced by the <b>Project Planner Agent</b>.
 *
 * <p>Spec: {@code specs/modules/project-planner.md §4 Output Specifications}
 *
 * <p>Implements the <b>Composite</b> design pattern: the project hierarchy is modelled
 * as a tree of {@link ProjectComponent} nodes. {@link ProjectNode} represents a phase
 * (composite) and {@link TaskLeaf} represents an atomic task (leaf).
 *
 * <p>The {@code mermaidGanttChart} is a derived visualisation artifact.
 * The {@code rootProject} composite structure is the authoritative representation.
 *
 * <p>Passed via {@code CentralOrchestrator} to the Risk Analyst Agent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GanttContext(

        /** Root of the composite project tree. Contains all phases and their tasks. */
        ProjectNode rootProject,

        /** Total number of phases (direct children of rootProject). */
        int totalPhases,

        /** Total number of leaf tasks across all phases. */
        int totalTasks,

        /** Prose summary of the planning analysis. */
        String planningSummary,

        /**
         * Mermaid Gantt chart string for visualisation purposes.
         * Generated from the composite structure; not the source of truth.
         */
        String mermaidGanttChart
) {

    // =========================================================================
    // Composite Pattern contracts
    // =========================================================================

    /**
     * Common interface for all nodes in the project composite tree.
     *
     * <p>Spec: {@code specs/modules/project-planner.md §4 Composite Pattern Contracts}
     *
     * <p>Annotated for Jackson polymorphic (de)serialisation via a {@code "type"}
     * discriminator ({@code "node"} → {@link ProjectNode}, {@code "leaf"} →
     * {@link TaskLeaf}) so the LLM-produced WBS tree round-trips correctly.
     */
    // COMPOSITE PATTERN — Component
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ProjectNode.class, name = "node"),
            @JsonSubTypes.Type(value = TaskLeaf.class, name = "leaf")
    })
    public interface ProjectComponent {

        /** Human-readable name of this component. */
        String name();

        /**
         * Child components. Returns an empty list for leaf nodes.
         *
         * @return unmodifiable list of children
         */
        List<ProjectComponent> children();
    }

    // -------------------------------------------------------------------------

    /**
     * Composite node representing a project phase (e.g. "Backend Development").
     *
     * <p>Allowed phase names are defined in the spec:
     * Requirements / Design / Database / Backend Development / Frontend Development /
     * Integration / Testing / Deployment.
     *
     * <p>Spec: {@code specs/modules/project-planner.md §4 ProjectNode}
     */
    // COMPOSITE PATTERN — Composite
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectNode(
            String name,
            List<ProjectComponent> children
    ) implements ProjectComponent {}

    // -------------------------------------------------------------------------

    /**
     * Leaf node representing a single, atomic implementation task.
     *
     * <p>Complexity values must be one of: {@code Small}, {@code Medium},
     * {@code Large}, {@code Very Large}.  These feed into the Risk Analyst Agent.
     *
     * <p>Spec: {@code specs/modules/project-planner.md §4 TaskLeaf}
     */
    // COMPOSITE PATTERN — Leaf
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskLeaf(
            String name,
            String assigneeRole,
            String complexity,
            List<String> dependencies
    ) implements ProjectComponent {

        @Override
        public List<ProjectComponent> children() {
            return Collections.emptyList();
        }
    }
}
