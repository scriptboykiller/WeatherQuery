package org.rosetta.sqlvalidator.crossdb.input;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Header-based reader for the current validator CSV files.
 *
 * Binding-plan and execution-report aliases are intentionally tolerant because
 * their exact existing headers must be confirmed against the real project.
 */
public final class CurrentSqlInputReader {

    private static final CSVFormat INPUT_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build();

    public List<CurrentSqlInventoryRow> readInventory(Path path) throws IOException {
        requireReadable(path, "inventory");

        List<CurrentSqlInventoryRow> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = INPUT_FORMAT.parse(reader)) {

            for (CSVRecord record : parser) {
                rows.add(new CurrentSqlInventoryRow(
                        CsvRecordValues.first(record, "id", "sqlId"),
                        CsvRecordValues.first(record, "serviceName"),
                        CsvRecordValues.first(record, "filePath"),
                        CsvRecordValues.first(record, "className"),
                        CsvRecordValues.first(record, "methodName"),
                        CsvRecordValues.first(record, "sourceType"),
                        CsvRecordValues.integer(record, -1, "lineNumber"),
                        CsvRecordValues.first(record, "sqlVariableName"),
                        CsvRecordValues.first(record, "sqlText"),
                        CsvRecordValues.first(record, "normalizedSqlText"),
                        CsvRecordValues.first(record, "parameterMode"),
                        CsvRecordValues.first(record, "parameterNames"),
                        CsvRecordValues.integer(record, 0, "parameterCount"),
                        CsvRecordValues.bool(record, false, "isDynamicSql", "dynamicSql"),
                        CsvRecordValues.bool(record, false,
                                "requiresManualReview", "manualReview"),
                        CsvRecordValues.first(record, "manualReviewReason"),
                        CsvRecordValues.first(record, "confidence"),
                        CsvRecordValues.first(record, "notes")
                ));
            }
        }
        return List.copyOf(rows);
    }

    public Map<String, BindingPlanSnapshot> readBindingPlans(Path path) throws IOException {
        requireReadable(path, "binding plan");

        Map<String, MutableBindingAccumulator> accumulators = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = INPUT_FORMAT.parse(reader)) {

            for (CSVRecord record : parser) {
                String sqlId = CsvRecordValues.first(record, "sqlId", "id");
                if (sqlId.isBlank()) {
                    continue;
                }

                MutableBindingAccumulator accumulator =
                        accumulators.computeIfAbsent(sqlId, MutableBindingAccumulator::new);

                accumulator.accept(
                        CsvRecordValues.first(record,
                                "jdbcSql", "adaptedJdbcSql", "executedSql"),
                        CsvRecordValues.first(record,
                                "bindingStatus", "bindingPlanStatus", "status"),
                        CsvRecordValues.first(record,
                                "jdbcIndex", "bindingIndex", "parameterIndex"),
                        CsvRecordValues.integer(record, 0,
                                "bindingCount", "parameterCount", "jdbcParameterCount")
                );
            }
        }

        Map<String, BindingPlanSnapshot> result = new LinkedHashMap<>();
        accumulators.forEach((sqlId, accumulator) ->
                result.put(sqlId, accumulator.toSnapshot()));
        return Map.copyOf(result);
    }

    public Map<String, ExecutionReportSnapshot> readExecutionReports(Path path)
            throws IOException {
        requireReadable(path, "execution report");

        Map<String, ExecutionReportSnapshot> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = INPUT_FORMAT.parse(reader)) {

            for (CSVRecord record : parser) {
                String sqlId = CsvRecordValues.first(record, "sqlId", "id");
                if (sqlId.isBlank()) {
                    continue;
                }

                result.putIfAbsent(sqlId, new ExecutionReportSnapshot(
                        sqlId,
                        CsvRecordValues.first(record,
                                "executionStatus", "validationStatus", "status"),
                        CsvRecordValues.first(record,
                                "jdbcSql", "adaptedJdbcSql", "executedSql")
                ));
            }
        }
        return Map.copyOf(result);
    }

    private void requireReadable(Path path, String description) {
        if (path == null) {
            throw new IllegalArgumentException(description + " path must not be null");
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(
                    description + " file is not readable: " + path.toAbsolutePath());
        }
    }

    private static final class MutableBindingAccumulator {

        private final String sqlId;
        private final Set<String> bindingIndexes = new LinkedHashSet<>();
        private String jdbcSql = "";
        private String status = "";
        private int explicitBindingCount;
        private int rowCount;

        private MutableBindingAccumulator(String sqlId) {
            this.sqlId = sqlId;
        }

        private void accept(
                String candidateJdbcSql,
                String candidateStatus,
                String bindingIndex,
                int candidateExplicitBindingCount
        ) {
            rowCount++;

            if (jdbcSql.isBlank() && candidateJdbcSql != null
                    && !candidateJdbcSql.isBlank()) {
                jdbcSql = candidateJdbcSql.trim();
            }
            if (status.isBlank() && candidateStatus != null
                    && !candidateStatus.isBlank()) {
                status = candidateStatus.trim();
            }
            if (bindingIndex != null && !bindingIndex.isBlank()) {
                bindingIndexes.add(bindingIndex.trim());
            }
            explicitBindingCount = Math.max(
                    explicitBindingCount,
                    candidateExplicitBindingCount
            );
        }

        private BindingPlanSnapshot toSnapshot() {
            int count;
            if (explicitBindingCount > 0) {
                count = explicitBindingCount;
            } else if (!bindingIndexes.isEmpty()) {
                count = bindingIndexes.size();
            } else {
                count = rowCount;
            }
            return new BindingPlanSnapshot(sqlId, jdbcSql, status, count);
        }
    }
}
