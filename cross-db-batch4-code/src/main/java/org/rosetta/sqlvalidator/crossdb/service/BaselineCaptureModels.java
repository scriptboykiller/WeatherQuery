package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.comparison.SampleComparison;
import org.rosetta.sqlvalidator.crossdb.eligibility.OverallComparisonStatus;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;

import java.util.List;

public final class BaselineCaptureModels {
    private BaselineCaptureModels() {
    }

    public record SampleResult(
            int sampleIndex,
            ParameterTuple parameterTuple,
            SelectExecutionResult h2Result,
            SelectExecutionResult postgresResult,
            SampleComparison comparison
    ) {
    }

    public record SqlResult(
            CandidateAnalysis analysis,
            int requestedSampleCount,
            int collectedSampleCount,
            List<SampleResult> samples,
            OverallComparisonStatus overallStatus
    ) {
        public SqlResult {
            samples = List.copyOf(samples);
        }
    }

    public record RunMetadata(
            String baselineRunId,
            String baselineCreatedAt,
            String normalizationVersion,
            String h2Database,
            String h2Schema,
            String postgresDatabase,
            String postgresSchema
    ) {
    }
}
