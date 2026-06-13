package com.autonomouspm.infrastructure;

import com.autonomouspm.context.DatabaseContext.Column;
import com.autonomouspm.context.DatabaseContext.Relationship;
import com.autonomouspm.context.DatabaseContext.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Generates a <a href="https://mermaid.js.org/syntax/entityRelationshipDiagram.html">Mermaid
 * ERD</a> string from a schema's tables and relationships.
 *
 * <p>Spec: {@code specs/modules/database-architect.md §4 MermaidErdGenerator}.
 *
 * <p>This is <b>pure Java logic — no LLM is involved</b>. The Mermaid chart is a
 * secondary visualisation; the authoritative schema remains the {@link Table} and
 * {@link Relationship} lists.
 *
 * <p>Output example:
 * <pre>
 * erDiagram
 *   CUSTOMER ||--o{ ORDER : places
 *   ORDER ||--|{ ORDER_ITEM : contains
 * </pre>
 *
 * <p><b>Cardinality mapping</b> (spec §4):
 * <ul>
 *   <li>{@code ONE_TO_ONE}   → {@code ||--||}</li>
 *   <li>{@code ONE_TO_MANY}  → {@code ||--o{}</li>
 *   <li>{@code MANY_TO_MANY} → <code>}o--o{</code></li>
 * </ul>
 *
 * <p>All methods are null-safe and never throw: malformed input yields the
 * best-effort diagram (at minimum the {@code erDiagram} header).
 */
@Component
public class MermaidErdGenerator {

    private static final Logger log = LoggerFactory.getLogger(MermaidErdGenerator.class);

    private static final String HEADER = "erDiagram";
    private static final String INDENT = "  ";

    /** Fallback Mermaid relationship operator for unknown cardinalities. */
    private static final String DEFAULT_OPERATOR = "||--o{";

    /**
     * Builds a Mermaid ERD string from the given tables and relationships.
     *
     * <p>Each table is rendered as an entity block listing its columns (type and
     * name, with {@code PK}/{@code FK} key markers). Each relationship is rendered
     * as a connection line using the mapped cardinality operator and a derived
     * verb label.
     *
     * @param tables        the schema tables (may be {@code null} or empty)
     * @param relationships the inter-table relationships (may be {@code null} or empty)
     * @return a Mermaid ERD string; never {@code null}
     */
    public String generate(List<Table> tables, List<Relationship> relationships) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');

        if (tables != null) {
            for (Table table : tables) {
                appendTable(sb, table);
            }
        }

        if (relationships != null) {
            for (Relationship relationship : relationships) {
                appendRelationship(sb, relationship);
            }
        }

        return sb.toString().stripTrailing();
    }

    // -------------------------------------------------------------------------
    // Entity blocks
    // -------------------------------------------------------------------------

    /**
     * Appends a single entity block, e.g.:
     * <pre>
     *   CUSTOMER {
     *     BIGINT id PK
     *     VARCHAR name
     *   }
     * </pre>
     */
    private void appendTable(StringBuilder sb, Table table) {
        if (table == null || isBlank(table.name())) {
            return;
        }
        String entity = toEntityName(table.name());
        sb.append(INDENT).append(entity).append(" {").append('\n');

        List<Column> columns = table.columns();
        if (columns != null) {
            for (Column column : columns) {
                appendColumn(sb, column);
            }
        }

        sb.append(INDENT).append('}').append('\n');
    }

    /**
     * Appends one column line: {@code <type> <name> [PK|FK]}. Mermaid requires a
     * type token, so a missing type falls back to {@code string}.
     */
    private void appendColumn(StringBuilder sb, Column column) {
        if (column == null || isBlank(column.name())) {
            return;
        }
        String type = isBlank(column.dataType()) ? "string" : sanitizeType(column.dataType());
        sb.append(INDENT).append(INDENT)
                .append(type).append(' ')
                .append(sanitizeName(column.name()));

        if (column.isPrimaryKey()) {
            sb.append(" PK");
        } else if (column.isForeignKey()) {
            sb.append(" FK");
        }
        sb.append('\n');
    }

    // -------------------------------------------------------------------------
    // Relationship lines
    // -------------------------------------------------------------------------

    /**
     * Appends one relationship line, e.g. {@code CUSTOMER ||--o{ ORDER : places}.
     * Relationships referencing a blank table name are skipped.
     */
    private void appendRelationship(StringBuilder sb, Relationship relationship) {
        if (relationship == null
                || isBlank(relationship.fromTable())
                || isBlank(relationship.toTable())) {
            return;
        }
        String from = toEntityName(relationship.fromTable());
        String to = toEntityName(relationship.toTable());
        String operator = toOperator(relationship.cardinality());
        String label = toLabel(relationship);

        sb.append(INDENT)
                .append(from).append(' ')
                .append(operator).append(' ')
                .append(to).append(" : ")
                .append(label)
                .append('\n');
    }

    /**
     * Maps a cardinality descriptor to its Mermaid operator. Accepts the spec
     * enum-style values (case-insensitive, hyphen/space tolerant); unknown values
     * fall back to {@value #DEFAULT_OPERATOR} with a warning.
     */
    private String toOperator(String cardinality) {
        if (isBlank(cardinality)) {
            return DEFAULT_OPERATOR;
        }
        String normalized = cardinality.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return switch (normalized) {
            case "ONE_TO_ONE" -> "||--||";
            case "ONE_TO_MANY" -> "||--o{";
            case "MANY_TO_MANY" -> "}o--o{";
            default -> {
                log.warn("MermaidErdGenerator – unknown cardinality '{}', using default operator", cardinality);
                yield DEFAULT_OPERATOR;
            }
        };
    }

    /**
     * Derives a single-token relationship verb label. Prefers a concise word from
     * the justification; falls back to {@code relates}. Mermaid labels after the
     * colon must be a single token, so whitespace is collapsed to underscores.
     */
    private String toLabel(Relationship relationship) {
        String justification = relationship.justification();
        if (isBlank(justification)) {
            return "relates";
        }
        String firstWord = justification.trim().split("\\s+")[0]
                .replaceAll("[^A-Za-z0-9_]", "");
        return firstWord.isBlank() ? "relates" : firstWord.toLowerCase(Locale.ROOT);
    }

    // -------------------------------------------------------------------------
    // Name sanitisation
    // -------------------------------------------------------------------------

    /** Uppercases a table name and strips characters Mermaid cannot render in an entity id. */
    private String toEntityName(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
    }

    /** Strips characters Mermaid cannot render in a column name. */
    private String sanitizeName(String name) {
        return name.trim().replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** Strips characters Mermaid cannot render in a column type token. */
    private String sanitizeType(String type) {
        return type.trim().replaceAll("[^A-Za-z0-9_]", "_");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
