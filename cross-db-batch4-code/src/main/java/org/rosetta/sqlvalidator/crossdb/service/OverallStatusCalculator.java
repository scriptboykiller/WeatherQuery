package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.eligibility.OverallComparisonStatus;
import org.rosetta.sqlvalidator.crossdb.eligibility.SampleComparisonStatus;
import org.rosetta.sqlvalidator.crossdb.service.BaselineCaptureModels.SampleResult;

import java.util.List;

public final class OverallStatusCalculator {

    public OverallComparisonStatus calculate(List<SampleResult> samples, boolean postgresPending) {
        if (samples.isEmpty()) {
            return OverallComparisonStatus.NO_SAMPLE_DATA;
        }

        if (postgresPending) {
            boolean allH2Success = samples.stream()
                    .allMatch(sample -> sample.h2Result().successful());
            return allH2Success
                    ? OverallComparisonStatus.BASELINE_CAPTURED
                    : OverallComparisonStatus.PARTIAL_EXECUTION_FAILURE;
        }

        boolean allMatch = samples.stream()
                .allMatch(sample -> sample.comparison().status()
                        == SampleComparisonStatus.MATCH);
        if (allMatch) {
            return OverallComparisonStatus.ALL_SAMPLES_MATCH;
        }

        boolean executionFailure = samples.stream().anyMatch(sample -> switch (
                sample.comparison().status()) {
            case H2_EXECUTION_FAILED, POSTGRES_EXECUTION_FAILED,
                    BOTH_EXECUTION_FAILED, RESULT_TOO_LARGE -> true;
            default -> false;
        });

        return executionFailure
                ? OverallComparisonStatus.PARTIAL_EXECUTION_FAILURE
                : OverallComparisonStatus.PARTIAL_MISMATCH;
    }
}
