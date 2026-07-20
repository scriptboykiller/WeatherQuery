package org.rosetta.sqlvalidator.crossdb.comparison;

import org.rosetta.sqlvalidator.crossdb.eligibility.DifferenceCategory;
import org.rosetta.sqlvalidator.crossdb.eligibility.SampleComparisonStatus;

public record SampleComparison(
        SampleComparisonStatus status,
        DifferenceCategory differenceCategory,
        String differenceMessage
) {
}
