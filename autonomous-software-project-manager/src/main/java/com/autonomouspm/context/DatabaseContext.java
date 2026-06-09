package com.autonomouspm.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Immutable output context produced by the <b>Database Architect Agent</b>.
 *
 * <p>Spec: {@code specs/modules/database-architect.md §3 Output Specifications}
 *
 * <p>The structured {@code tables} and {@code relationships} fields are the
 * authoritative schema definition. {@code mermaidErdChart} is a derived
 * visualisation artifact only.
 *
 * <p>Passed via {@code CentralOrchestrator} to the Project Planner Agent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatabaseContext(

        /** All database tables derived from requirements and user stories. */
        List<Table> tables,

        /** All inter-table relationships with cardinality and business justification. */
        List<Relationship> relationships,

        /**
         * Mermaid ERD chart string for visualisation purposes.
         * Not the authoritative schema — the {@code tables} list is.
         */
        String mermaidErdChart
) {

    /**
     * Represents a single database table.
     *
     * <p>Every table must be traceable to at least one requirement or user story.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Table(

            /** Table name in snake_case. */
            String name,

            /** Short business description justifying the table's existence. */
            String description,

            /** Ordered list of columns in this table. */
            List<Column> columns
    ) {}

    /**
     * Represents a single column within a {@link Table}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Column(

            /** Column name in snake_case. */
            String name,

            /** SQL data type (e.g. "VARCHAR(255)", "BIGINT", "BOOLEAN"). */
            String dataType,

            /** Whether this column is part of the primary key. */
            boolean isPrimaryKey,

            /** Whether this column is a foreign key referencing another table. */
            boolean isForeignKey,

            /** Whether this column accepts NULL values. */
            boolean isNullable
    ) {}

    /**
     * Represents a relationship between two tables.
     *
     * <p>Every relationship must carry a business justification.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relationship(

            /** Source table name. */
            String fromTable,

            /** Target table name. */
            String toTable,

            /**
             * Cardinality descriptor (e.g. "ONE_TO_MANY", "MANY_TO_MANY",
             * "ONE_TO_ONE").
             */
            String cardinality,

            /** Business reason explaining why this relationship exists. */
            String justification
    ) {}
}
