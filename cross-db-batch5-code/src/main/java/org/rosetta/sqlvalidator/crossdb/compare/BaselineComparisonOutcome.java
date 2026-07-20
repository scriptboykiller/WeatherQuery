package org.rosetta.sqlvalidator.crossdb.compare;

import org.rosetta.sqlvalidator.crossdb.baseline.SelectBaselineSnapshot;
import org.rosetta.sqlvalidator.crossdb.comparison.SampleComparison;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;

public record BaselineComparisonOutcome(
        SelectBaselineSnapshot baseline,
        CrossDatabaseCandidate currentCandidate,
        ComparisonEligibilityDecision eligibility,
        SelectExecutionResult currentPostgresResult,
        SampleComparison comparison
) {
}
