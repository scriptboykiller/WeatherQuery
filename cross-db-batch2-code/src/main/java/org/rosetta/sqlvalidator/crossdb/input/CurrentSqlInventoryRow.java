package org.rosetta.sqlvalidator.crossdb.input;

/** Minimal Batch-2 projection of sql-inventory.csv. */
public record CurrentSqlInventoryRow(
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
        String notes
) {
}
