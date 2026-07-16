package org.rosetta.sqlvalidator.report.excel.model;

public record InventoryRecord(
        String sqlId,
        String serviceName,
        String filePath,
        String className,
        String methodName,
        String sourceType,
        int lineNumber,
        String sqlVariableName,
        String sqlText,
        String normalizedSqlText,
        String parameterMode,
        String parameterNames,
        int parameterCount,
        boolean dynamicSql,
        boolean requiresManualReview,
        String manualReviewReason,
        String confidence,
        String notes) {
}
