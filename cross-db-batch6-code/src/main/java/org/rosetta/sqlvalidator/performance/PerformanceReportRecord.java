package org.rosetta.sqlvalidator.performance;

public record PerformanceReportRecord(
        String caseId,
        String baselineKey,
        String baselineSqlId,
        String currentSqlId,
        int sampleIndex,
        Integer selectedSampleIndex,
        SelectionReason selectionReason,
        PerformanceStatus performanceStatus,
        String htmlReportPath,
        String errorCode,
        String errorMessage
) {
    public String mergeKey() {
        return baselineKey + "|" + sampleIndex;
    }
}
