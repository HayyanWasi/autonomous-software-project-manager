package com.autonomouspm.agents.database;

import com.autonomouspm.context.DatabaseContext;
import com.autonomouspm.context.DatabaseContext.Column;
import com.autonomouspm.context.DatabaseContext.Relationship;
import com.autonomouspm.context.DatabaseContext.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates and repairs a parsed {@link DatabaseContext} produced by the
 * Database Architect Agent.
 *
 * <p>Spec: {@code specs/modules/database-architect.md §5 DatabaseArchitectValidator}.
 *
 * <p>Unlike the requirement/business validators, this validator is
 * <b>non-fatal by design</b>: it never throws and never returns {@code null}.
 * Instead it logs warnings via SLF4J and returns the best partial result —
 * dropping only the elements that are unsalvageable (e.g. relationships pointing
 * at non-existent tables, duplicate tables, tables with no columns).
 *
 * <p>Validation rules (spec §5):
 * <ul>
 *   <li>At least one table exists.</li>
 *   <li>Every table has at least one column.</li>
 *   <li>Every table has exactly one primary-key column.</li>
 *   <li>Every relationship references tables that exist in the table list.</li>
 *   <li>No duplicate table names.</li>
 * </ul>
 *
 * <p>The Mermaid chart is regenerated downstream from the cleaned tables and
 * relationships, so this validator only curates the authoritative schema lists.
 */
@Component
public class DatabaseArchitectValidator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseArchitectValidator.class);

    /**
     * Validates and sanitises the given context, returning a cleaned copy.
     *
     * <p>The returned context preserves the incoming {@code mermaidErdChart} value;
     * the agent regenerates the chart from the cleaned lists after validation.
     *
     * @param context the parsed context (may be {@code null})
     * @return a non-null, sanitised {@link DatabaseContext}
     */
    public DatabaseContext validate(DatabaseContext context) {
        if (context == null) {
            log.warn("DatabaseArchitectValidator – context is null; returning empty schema.");
            return emptyContext();
        }

        List<Table> cleanedTables = cleanTables(context.tables());
        if (cleanedTables.isEmpty()) {
            log.warn("DatabaseArchitectValidator – no valid tables found after validation.");
        }

        List<Relationship> cleanedRelationships =
                cleanRelationships(context.relationships(), cleanedTables);

        return new DatabaseContext(
                cleanedTables,
                cleanedRelationships,
                context.mermaidErdChart() == null ? "" : context.mermaidErdChart()
        );
    }

    // -------------------------------------------------------------------------
    // Table cleaning
    // -------------------------------------------------------------------------

    /**
     * Drops nameless tables, duplicates (by case-insensitive name), tables with no
     * columns, and warns when a table does not have exactly one primary key.
     */
    private List<Table> cleanTables(List<Table> tables) {
        List<Table> result = new ArrayList<>();
        if (tables == null || tables.isEmpty()) {
            return result;
        }

        Set<String> seenNames = new HashSet<>();
        for (Table table : tables) {
            if (table == null || isBlank(table.name())) {
                log.warn("DatabaseArchitectValidator – dropping a table with no name.");
                continue;
            }
            String key = table.name().trim().toLowerCase(Locale.ROOT);
            if (!seenNames.add(key)) {
                log.warn("DatabaseArchitectValidator – dropping duplicate table '{}'.", table.name());
                continue;
            }
            if (table.columns() == null || table.columns().isEmpty()) {
                log.warn("DatabaseArchitectValidator – dropping table '{}' with no columns.", table.name());
                continue;
            }
            warnOnPrimaryKey(table);
            result.add(table);
        }
        return result;
    }

    /**
     * Logs a warning when a table does not have exactly one primary-key column.
     * The table is retained (best partial result); the schema author can correct
     * the key, but a missing/duplicate PK should not silently disappear.
     */
    private void warnOnPrimaryKey(Table table) {
        long pkCount = table.columns().stream()
                .filter(c -> c != null && c.isPrimaryKey())
                .count();
        if (pkCount == 0) {
            log.warn("DatabaseArchitectValidator – table '{}' has no primary key.", table.name());
        } else if (pkCount > 1) {
            log.warn("DatabaseArchitectValidator – table '{}' has {} primary-key columns (expected exactly one).",
                    table.name(), pkCount);
        }
    }

    // -------------------------------------------------------------------------
    // Relationship cleaning
    // -------------------------------------------------------------------------

    /**
     * Drops relationships whose endpoints are blank or reference a table not in the
     * cleaned table set (case-insensitive match).
     */
    private List<Relationship> cleanRelationships(List<Relationship> relationships, List<Table> cleanedTables) {
        List<Relationship> result = new ArrayList<>();
        if (relationships == null || relationships.isEmpty()) {
            return result;
        }

        Set<String> tableNames = new HashSet<>();
        for (Table table : cleanedTables) {
            tableNames.add(table.name().trim().toLowerCase(Locale.ROOT));
        }

        for (Relationship relationship : relationships) {
            if (relationship == null
                    || isBlank(relationship.fromTable())
                    || isBlank(relationship.toTable())) {
                log.warn("DatabaseArchitectValidator – dropping a relationship with a blank endpoint.");
                continue;
            }
            String from = relationship.fromTable().trim().toLowerCase(Locale.ROOT);
            String to = relationship.toTable().trim().toLowerCase(Locale.ROOT);
            if (!tableNames.contains(from) || !tableNames.contains(to)) {
                log.warn("DatabaseArchitectValidator – dropping relationship '{}' → '{}' referencing an unknown table.",
                        relationship.fromTable(), relationship.toTable());
                continue;
            }
            result.add(relationship);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DatabaseContext emptyContext() {
        return new DatabaseContext(new ArrayList<>(), new ArrayList<>(), "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
