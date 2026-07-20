package org.rosetta.sqlvalidator.performance;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrossDatabasePerformanceInputReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build();

    public List<PerformanceSourceRow> readPreferred(
            Path comparisonInput,
            Path baselineInput
    ) throws IOException {
        if (hasDataRows(comparisonInput)) {
            return read(comparisonInput);
        }
        if (hasDataRows(baselineInput)) {
            return read(baselineInput);
        }
        throw new IllegalArgumentException(
                "Neither comparison nor baseline CSV contains data rows.");
    }

    public List<PerformanceSourceRow> read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Cross DB CSV is not readable: " + path);
        }

        List<PerformanceSourceRow> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                rows.add(new PerformanceSourceRow(
                        value(record, "baselineRunId"),
                        value(record, "baselineKey"),
                        first(record, "baselineSqlId", "sqlId"),
                        first(record, "currentSqlId", "currentPostgresSqlId"),
                        value(record, "statementType"),
                        integer(record, 1, "sampleIndex"),
                        value(record, "parameterValues"),
                        first(record, "h2JdbcSql", "baselineH2JdbcSql"),
                        first(record, "currentPostgresJdbcSql", "postgresJdbcSql", "postgresJdbcSqlAtCapture"),
                        value(record, "h2ExecutionStatus"),
                        value(record, "postgresExecutionStatus"),
                        value(record, "sampleComparisonStatus"),
                        nullableLong(record, "h2ObservedTimeMs", "h2ExecutionTimeMs"),
                        nullableLong(record, "postgresObservedTimeMs", "postgresExecutionTimeMs")
                ));
            }
        }
        return List.copyOf(rows);
    }

    private boolean hasDataRows(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            return false;
        }
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.skip(1).anyMatch(line -> !line.isBlank());
        }
    }

    private String first(CSVRecord record, String... headers) {
        for (String header : headers) {
            String value = value(record, header);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String value(CSVRecord record, String header) {
        for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().trim().toLowerCase(Locale.ROOT)
                    .equals(header.toLowerCase(Locale.ROOT))) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    private int integer(CSVRecord record, int fallback, String header) {
        String value = value(record, header);
        return value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private Long nullableLong(CSVRecord record, String... headers) {
        String value = first(record, headers);
        return value.isBlank() ? null : Long.valueOf(value);
    }
}
