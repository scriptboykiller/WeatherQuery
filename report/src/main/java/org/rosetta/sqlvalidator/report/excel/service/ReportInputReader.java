package org.rosetta.sqlvalidator.report.excel.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.rosetta.sqlvalidator.report.excel.model.BindingRecord;
import org.rosetta.sqlvalidator.report.excel.model.ExecutionRecord;
import org.rosetta.sqlvalidator.report.excel.model.InventoryRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportInputReader {

    private static final CSVFormat INPUT_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(false)
            .build();

    public Map<String, InventoryRecord> readInventory(final Path inputFile) throws IOException {
        final Map<String, InventoryRecord> records = new LinkedHashMap<>();
        if (!Files.exists(inputFile)) {
            throw new IOException("Inventory CSV not found: " + inputFile.toAbsolutePath());
        }

        try (Reader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8);
             CSVParser parser = INPUT_FORMAT.parse(reader)) {
            for (final CSVRecord csv : parser) {
                final String id = value(csv, "id");
                if (id.isBlank()) {
                    continue;
                }
                records.put(id, new InventoryRecord(
                        id,
                        value(csv, "serviceName"),
                        value(csv, "filePath"),
                        value(csv, "className"),
                        value(csv, "methodName"),
                        value(csv, "sourceType"),
                        intValue(csv, "lineNumber"),
                        value(csv, "sqlVariableName"),
                        value(csv, "sqlText"),
                        value(csv, "normalizedSqlText"),
                        value(csv, "parameterMode"),
                        value(csv, "parameterNames"),
                        intValue(csv, "parameterCount"),
                        booleanValue(csv, "isDynamicSql"),
                        booleanValue(csv, "requiresManualReview"),
                        value(csv, "manualReviewReason"),
                        value(csv, "confidence"),
                        value(csv, "notes")));
            }
        }
        return records;
    }

    public List<ExecutionRecord> readExecution(final Path inputFile) throws IOException {
        if (!Files.exists(inputFile)) {
            throw new IOException("Execution report CSV not found: " + inputFile.toAbsolutePath());
        }

        final List<ExecutionRecord> records = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8);
             CSVParser parser = INPUT_FORMAT.parse(reader)) {
            for (final CSVRecord csv : parser) {
                records.add(new ExecutionRecord(
                        value(csv, "sqlId"),
                        value(csv, "jdbcSql"),
                        value(csv, "validationMode"),
                        value(csv, "executionStatus"),
                        value(csv, "sqlState"),
                        value(csv, "postgresErrorCode"),
                        value(csv, "errorCategory"),
                        value(csv, "identifierStrategy"),
                        booleanValue(csv, "identifierAdaptationApplied"),
                        value(csv, "identifierAdaptationMode"),
                        value(csv, "adaptedJdbcSql"),
                        value(csv, "identifierAdaptationNote"),
                        value(csv, "message"),
                        value(csv, "recommendation")));
            }
        }
        return records;
    }

    public Map<String, List<BindingRecord>> readBindingsOptional(final Path inputFile) throws IOException {
        if (inputFile == null || !Files.exists(inputFile)) {
            return Collections.emptyMap();
        }

        final Map<String, List<BindingRecord>> grouped = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8);
             CSVParser parser = INPUT_FORMAT.parse(reader)) {
            for (final CSVRecord csv : parser) {
                final BindingRecord record = new BindingRecord(
                        value(csv, "sqlId"),
                        value(csv, "parameterMode"),
                        value(csv, "adaptedJdbcSql"),
                        value(csv, "originalParameter"),
                        intValue(csv, "bindingIndex"),
                        value(csv, "javaParameterName"),
                        value(csv, "javaType"),
                        value(csv, "parameterKind"),
                        value(csv, "mockValue"),
                        value(csv, "confidence"),
                        value(csv, "bindingNote"));
                grouped.computeIfAbsent(record.sqlId(), ignored -> new ArrayList<>()).add(record);
            }
        }
        return grouped;
    }

    private String value(final CSVRecord csv, final String header) {
        if (!csv.isMapped(header)) {
            return "";
        }
        final String value = csv.get(header);
        return value == null ? "" : value.trim();
    }

    private int intValue(final CSVRecord csv, final String header) {
        final String value = value(csv, header);
        if (value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean booleanValue(final CSVRecord csv, final String header) {
        return Boolean.parseBoolean(value(csv, header));
    }
}
