package org.rosetta.sqlvalidator.crossdb.baseline;

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

public final class SelectBaselineCsvReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build();

    public List<SelectBaselineSnapshot> read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Baseline CSV is not readable: " + path);
        }

        List<SelectBaselineSnapshot> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                rows.add(new SelectBaselineSnapshot(
                        value(record, "baselineRunId"),
                        value(record, "baselineCreatedAt"),
                        value(record, "normalizationVersion"),
                        value(record, "baselineKey"),
                        value(record, "sqlId"),
                        value(record, "serviceName"),
                        value(record, "className"),
                        value(record, "methodName"),
                        value(record, "sourceType"),
                        value(record, "sqlVariableName"),
                        integer(record, 1, "occurrenceIndex"),
                        value(record, "statementType"),
                        value(record, "h2Database"),
                        value(record, "h2Schema"),
                        integer(record, 1, "sampleIndex"),
                        integer(record, 0, "requestedSampleCount"),
                        value(record, "parameterNames"),
                        value(record, "parameterTypes"),
                        value(record, "parameterValues"),
                        value(record, "h2JdbcSql"),
                        value(record, "h2ExecutionStatus"),
                        nullableInteger(record, "h2RowCount"),
                        value(record, "h2ResultHash"),
                        value(record, "h2ColumnSignature")
                ));
            }
        }
        return List.copyOf(rows);
    }

    private String value(CSVRecord record, String header) {
        for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().toLowerCase(Locale.ROOT)
                    .equals(header.toLowerCase(Locale.ROOT))) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    private int integer(CSVRecord record, int defaultValue, String header) {
        String value = value(record, header);
        return value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private Integer nullableInteger(CSVRecord record, String header) {
        String value = value(record, header);
        return value.isBlank() ? null : Integer.valueOf(value);
    }
}
