package org.rosetta.sqlvalidator.crossdb.execution;

import org.rosetta.sqlvalidator.crossdb.normalization.NormalizedResult;

public record SelectExecutionResult(
        SelectExecutionStatus status,
        Integer rowCount,
        String resultHash,
        String columnSignature,
        Long executionTimeMs,
        String errorMessage,
        NormalizedResult normalizedResult
) {
    public static SelectExecutionResult pendingMigration() {
        return new SelectExecutionResult(
                SelectExecutionStatus.PENDING_SQL_MIGRATION,
                null, "", "", null, "", null);
    }

    public boolean successful() {
        return status == SelectExecutionStatus.SUCCESS;
    }
}
