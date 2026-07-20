package org.rosetta.sqlvalidator.performance;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PerformanceReportCsvWriter {

    private static final String[] HEADERS = {
            "caseId", "baselineKey", "baselineSqlId", "currentSqlId",
            "sampleIndex", "selectedSampleIndex", "selectionReason",
            "performanceStatus", "htmlReportPath", "errorCode", "errorMessage"
    };

    public void write(Path path, List<PerformanceReportRecord> records)
            throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            for (PerformanceReportRecord record : records) {
                printer.printRecord(
                        record.caseId(), record.baselineKey(), record.baselineSqlId(),
                        record.currentSqlId(), record.sampleIndex(),
                        record.selectedSampleIndex() == null ? "" : record.selectedSampleIndex(),
                        record.selectionReason(), record.performanceStatus(),
                        record.htmlReportPath(), record.errorCode(), record.errorMessage());
            }
        }
    }
}
