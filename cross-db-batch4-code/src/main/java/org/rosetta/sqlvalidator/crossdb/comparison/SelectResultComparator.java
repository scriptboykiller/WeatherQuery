package org.rosetta.sqlvalidator.crossdb.comparison;

import org.rosetta.sqlvalidator.crossdb.eligibility.DifferenceCategory;
import org.rosetta.sqlvalidator.crossdb.eligibility.SampleComparisonStatus;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionStatus;

public final class SelectResultComparator {

    public SampleComparison compare(SelectExecutionResult h2, SelectExecutionResult postgres) {
        if (postgres.status() == SelectExecutionStatus.PENDING_SQL_MIGRATION) {
            return new SampleComparison(
                    SampleComparisonStatus.PENDING_SQL_MIGRATION,
                    DifferenceCategory.NONE,
                    "PostgreSQL execution is pending SQL migration.");
        }

        if (h2.status() == SelectExecutionStatus.RESULT_TOO_LARGE
                || postgres.status() == SelectExecutionStatus.RESULT_TOO_LARGE) {
            return new SampleComparison(
                    SampleComparisonStatus.RESULT_TOO_LARGE,
                    DifferenceCategory.RESULT_TOO_LARGE,
                    "At least one database exceeded maxSelectRows.");
        }

        boolean h2Failed = !h2.successful();
        boolean pgFailed = !postgres.successful();
        if (h2Failed && pgFailed) {
            return new SampleComparison(
                    SampleComparisonStatus.BOTH_EXECUTION_FAILED,
                    DifferenceCategory.UNKNOWN,
                    "Both database executions failed.");
        }
        if (h2Failed) {
            return new SampleComparison(
                    SampleComparisonStatus.H2_EXECUTION_FAILED,
                    DifferenceCategory.H2_EXECUTION_ERROR,
                    h2.errorMessage());
        }
        if (pgFailed) {
            return new SampleComparison(
                    SampleComparisonStatus.POSTGRES_EXECUTION_FAILED,
                    DifferenceCategory.POSTGRES_EXECUTION_ERROR,
                    postgres.errorMessage());
        }

        if (!h2.columnSignature().equals(postgres.columnSignature())) {
            return mismatch(DifferenceCategory.COLUMN_STRUCTURE_MISMATCH,
                    "Normalized column signatures differ.");
        }
        if (!h2.rowCount().equals(postgres.rowCount())) {
            return mismatch(DifferenceCategory.ROW_COUNT_MISMATCH,
                    "H2 row count " + h2.rowCount()
                            + " differs from PostgreSQL row count " + postgres.rowCount() + ".");
        }
        if (!h2.resultHash().equals(postgres.resultHash())) {
            return mismatch(DifferenceCategory.RESULT_HASH_MISMATCH,
                    "Normalized result hashes differ.");
        }

        return new SampleComparison(
                SampleComparisonStatus.MATCH,
                DifferenceCategory.NONE,
                "");
    }

    private SampleComparison mismatch(DifferenceCategory category, String message) {
        return new SampleComparison(SampleComparisonStatus.MISMATCH, category, message);
    }
}
