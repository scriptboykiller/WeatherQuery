package org.rosetta.sqlvalidator.crossdb.comparison;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.eligibility.DifferenceCategory;
import org.rosetta.sqlvalidator.crossdb.eligibility.SampleComparisonStatus;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectResultComparatorTest {

    private final SelectResultComparator comparator = new SelectResultComparator();

    @Test
    void matchesEqualResults() {
        SampleComparison result = comparator.compare(
                success(2, "hash", "ID:NUMBER"),
                success(2, "hash", "ID:NUMBER"));
        assertEquals(SampleComparisonStatus.MATCH, result.status());
    }

    @Test
    void detectsColumnMismatchFirst() {
        SampleComparison result = comparator.compare(
                success(2, "a", "ID:NUMBER"),
                success(2, "b", "ID:STRING"));
        assertEquals(DifferenceCategory.COLUMN_STRUCTURE_MISMATCH,
                result.differenceCategory());
    }

    @Test
    void supportsPendingMigration() {
        SampleComparison result = comparator.compare(
                success(1, "hash", "ID:NUMBER"),
                SelectExecutionResult.pendingMigration());
        assertEquals(SampleComparisonStatus.PENDING_SQL_MIGRATION, result.status());
    }

    private SelectExecutionResult success(int rows, String hash, String signature) {
        return new SelectExecutionResult(
                SelectExecutionStatus.SUCCESS,
                rows, hash, signature, 1L, "", null);
    }
}
