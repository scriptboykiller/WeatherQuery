package org.rosetta.sqlvalidator.crossdb.input;

/** Minimal Batch-2 projection of sql-execution-report.csv. */
public record ExecutionReportSnapshot(
        String sqlId,
        String executionStatus,
        String jdbcSql
) {
    public boolean postgresPassed() {
        return executionStatus != null
                && "PASSED".equalsIgnoreCase(executionStatus.trim());
    }
}
