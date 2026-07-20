package org.rosetta.sqlvalidator.excel;

import org.rosetta.sqlvalidator.performance.PerformanceReportRecord;
import org.rosetta.sqlvalidator.performance.PerformanceSourceRow;
import org.rosetta.sqlvalidator.performance.PerformanceStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CrossDatabaseExcelRowAssembler {

    public List<CrossDatabaseExcelRow> assemble(
            List<PerformanceSourceRow> crossDbRows,
            List<PerformanceReportRecord> performanceRows,
            boolean performanceFilePresent
    ) {
        Map<String, PerformanceReportRecord> byKey = new HashMap<>();
        for (PerformanceReportRecord row : performanceRows) {
            byKey.put(row.mergeKey(), row);
        }

        return crossDbRows.stream()
                .map(row -> toExcelRow(
                        row,
                        byKey.get(row.baselineKey() + "|" + row.sampleIndex()),
                        performanceFilePresent))
                .toList();
    }

    private CrossDatabaseExcelRow toExcelRow(
            PerformanceSourceRow row,
            PerformanceReportRecord performance,
            boolean performanceFilePresent
    ) {
        String display;
        String link = "";

        if (isPending(row.postgresExecutionStatus(), row.sampleComparisonStatus())) {
            display = "Pending Migration";
        } else if (!"SUCCESS".equalsIgnoreCase(row.postgresExecutionStatus())) {
            display = "Not Eligible";
        } else if (!"MATCH".equalsIgnoreCase(row.sampleComparisonStatus())) {
            display = "Result Mismatch";
        } else if (!performanceFilePresent) {
            display = "Not Generated";
        } else if (performance == null) {
            display = "Not Generated";
        } else {
            display = switch (performance.performanceStatus()) {
                case SUCCESS -> "Open Performance Report";
                case COVERED_BY_SELECTED_SAMPLE ->
                        "Covered by Sample " + performance.selectedSampleIndex();
                case NOT_SELECTED -> "Not Selected";
                case NOT_ELIGIBLE -> "Not Eligible";
                case PARAMETER_DECODE_FAILED -> "Parameter Decode Failed";
                case TIMEOUT -> "Timeout";
                case REPORT_MISSING -> "Report Missing";
                case RESULT_JSON_INVALID -> "Invalid Result JSON";
                case TOOL_FAILED -> "Report Generation Failed";
            };
            if (performance.performanceStatus() == PerformanceStatus.SUCCESS) {
                link = performance.htmlReportPath();
            }
        }

        return new CrossDatabaseExcelRow(
                row.baselineKey(), row.baselineSqlId(), row.currentSqlId(),
                row.sampleIndex(), row.parameterValues(),
                row.h2ExecutionStatus(), row.postgresExecutionStatus(),
                row.sampleComparisonStatus(), row.h2ObservedTimeMs(),
                row.postgresObservedTimeMs(), display, link);
    }

    private boolean isPending(String postgresStatus, String comparisonStatus) {
        String pg = postgresStatus == null ? "" : postgresStatus.toUpperCase();
        String comparison = comparisonStatus == null ? "" : comparisonStatus.toUpperCase();
        return pg.contains("PENDING")
                || pg.contains("NOT_READY")
                || comparison.contains("PENDING")
                || comparison.contains("NOT_READY");
    }
}
