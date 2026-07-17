package org.rosetta.sqlvalidator.crossdb.service;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.identity.BaselineKeyFactory;
import org.rosetta.sqlvalidator.crossdb.input.BindingPlanSnapshot;
import org.rosetta.sqlvalidator.crossdb.input.CurrentSqlInventoryRow;
import org.rosetta.sqlvalidator.crossdb.input.ExecutionReportSnapshot;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;
import org.rosetta.sqlvalidator.crossdb.sql.JdbcPlaceholderCounter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossDatabaseCandidateAggregatorTest {

    private final CrossDatabaseCandidateAggregator aggregator =
            new CrossDatabaseCandidateAggregator(
                    new BaselineKeyFactory(),
                    new JdbcPlaceholderCounter()
            );

    @Test
    void joinsInputsAndCreatesBaselineKey() {
        List<CurrentSqlInventoryRow> inventory = List.of(
                inventoryRow("sql-1", 10, "query")
        );

        Map<String, BindingPlanSnapshot> bindings = Map.of(
                "sql-1",
                new BindingPlanSnapshot(
                        "sql-1",
                        "SELECT * FROM customer WHERE id = ?",
                        "READY",
                        1
                )
        );

        Map<String, ExecutionReportSnapshot> executions = Map.of(
                "sql-1",
                new ExecutionReportSnapshot(
                        "sql-1",
                        "PASSED",
                        "SELECT * FROM customer WHERE id = ?"
                )
        );

        CrossDatabaseCandidate candidate =
                aggregator.aggregate(inventory, bindings, executions).get(0);

        assertEquals("sql-1", candidate.sqlId());
        assertEquals(
                "service|customerrepository|findbyid|spring_data_query|query|1",
                candidate.baselineKey()
        );
        assertEquals(1, candidate.placeholderCount());
        assertEquals(1, candidate.bindingCount());
        assertEquals("PASSED", candidate.postgresValidationStatus());
        assertFalse(candidate.duplicateBaselineKey());
    }

    @Test
    void detectsDuplicateBaselineKeys() {
        List<CurrentSqlInventoryRow> inventory = List.of(
                inventoryRow("sql-1", 10, "query"),
                inventoryRow("sql-2", 10, "query")
        );

        Map<String, BindingPlanSnapshot> bindings = Map.of(
                "sql-1", new BindingPlanSnapshot(
                        "sql-1", "SELECT * FROM a WHERE id = ?", "READY", 1),
                "sql-2", new BindingPlanSnapshot(
                        "sql-2", "SELECT * FROM b WHERE id = ?", "READY", 1)
        );

        List<CrossDatabaseCandidate> candidates =
                aggregator.aggregate(inventory, bindings, Map.of());

        assertTrue(candidates.stream()
                .allMatch(CrossDatabaseCandidate::duplicateBaselineKey));
    }

    private CurrentSqlInventoryRow inventoryRow(
            String id,
            int lineNumber,
            String variableName
    ) {
        return new CurrentSqlInventoryRow(
                id,
                "service",
                "CustomerRepository.java",
                "CustomerRepository",
                "findById",
                "SPRING_DATA_QUERY",
                lineNumber,
                variableName,
                "SELECT * FROM customer WHERE id = :id",
                "SELECT * FROM customer WHERE id = :id",
                "NAMED",
                "id",
                1,
                false,
                false,
                "",
                "HIGH",
                ""
        );
    }
}
