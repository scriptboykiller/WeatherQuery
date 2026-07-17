package org.rosetta.sqlvalidator.crossdb.model;

/** Aggregated current-state view of one SQL statement. */
public record CrossDatabaseCandidate(
        String sqlId,
        String baselineKey,
        String serviceName,
        String filePath,
        String className,
        String methodName,
        String sourceType,
        String sqlVariableName,
        int occurrenceIndex,
        int lineNumber,
        String originalSql,
        String normalizedSql,
        String jdbcSql,
        String executionReportJdbcSql,
        String statementType,
        boolean dynamicSql,
        boolean requiresManualReview,
        String manualReviewReason,
        String confidence,
        boolean bindingPlanPresent,
        boolean bindingPlanUsable,
        String bindingPlanStatus,
        int bindingCount,
        int placeholderCount,
        String postgresValidationStatus,
        boolean duplicateBaselineKey
) {
    public CrossDatabaseCandidate withDuplicateBaselineKey(boolean duplicate) {
        return new CrossDatabaseCandidate(
                sqlId, baselineKey, serviceName, filePath, className, methodName,
                sourceType, sqlVariableName, occurrenceIndex, lineNumber,
                originalSql, normalizedSql, jdbcSql, executionReportJdbcSql,
                statementType, dynamicSql, requiresManualReview,
                manualReviewReason, confidence, bindingPlanPresent,
                bindingPlanUsable, bindingPlanStatus, bindingCount,
                placeholderCount, postgresValidationStatus, duplicate
        );
    }
}
