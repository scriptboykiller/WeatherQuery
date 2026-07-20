package org.rosetta.sqlvalidator.excel;

public record CrossDatabaseExcelRow(
        String baselineKey,
        String baselineSqlId,
        String currentSqlId,
        int sampleIndex,
        String parameterValues,
        String h2Status,
        String postgresStatus,
        String resultComparison,
        Long h2ObservedTimeMs,
        Long postgresObservedTimeMs,
        String performanceDisplay,
        String performanceLink
) {
}
