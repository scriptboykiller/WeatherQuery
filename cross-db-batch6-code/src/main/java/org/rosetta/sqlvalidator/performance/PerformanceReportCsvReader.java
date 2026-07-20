package org.rosetta.sqlvalidator.performance;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PerformanceReportCsvReader {

    public List<PerformanceReportRecord> readOptional(Path path) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            return List.of();
        }
        List<PerformanceReportRecord> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).build()
                     .parse(reader)) {
            for (CSVRecord row : parser) {
                String selected = row.get("selectedSampleIndex");
                rows.add(new PerformanceReportRecord(
                        row.get("caseId"), row.get("baselineKey"),
                        row.get("baselineSqlId"), row.get("currentSqlId"),
                        Integer.parseInt(row.get("sampleIndex")),
                        selected.isBlank() ? null : Integer.valueOf(selected),
                        SelectionReason.valueOf(row.get("selectionReason")),
                        PerformanceStatus.valueOf(row.get("performanceStatus")),
                        row.get("htmlReportPath"), row.get("errorCode"),
                        row.get("errorMessage")));
            }
        }
        return List.copyOf(rows);
    }
}
