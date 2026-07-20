package org.rosetta.sqlvalidator.crossdb.baseline;

/** One immutable H2 baseline sample read from sql-select-baseline.csv. */
public record SelectBaselineSnapshot(
        String baselineRunId,
        String baselineCreatedAt,
        String normalizationVersion,
        String baselineKey,
        String baselineSqlId,
        String serviceName,
        String className,
        String methodName,
        String sourceType,
        String sqlVariableName,
        int occurrenceIndex,
        String statementType,
        String h2Database,
        String h2Schema,
        int sampleIndex,
        int requestedSampleCount,
        String parameterNames,
        String parameterTypes,
        String parameterValues,
        String baselineH2JdbcSql,
        String baselineH2ExecutionStatus,
        Integer baselineH2RowCount,
        String baselineH2ResultHash,
        String baselineH2ColumnSignature
) {
    public boolean usable() {
        return "SUCCESS".equalsIgnoreCase(baselineH2ExecutionStatus)
                && baselineH2RowCount != null
                && baselineH2ResultHash != null
                && !baselineH2ResultHash.isBlank()
                && baselineH2ColumnSignature != null
                && !baselineH2ColumnSignature.isBlank();
    }
}
