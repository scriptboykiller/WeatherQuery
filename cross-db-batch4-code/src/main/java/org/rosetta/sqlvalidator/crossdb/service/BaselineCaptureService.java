package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.comparison.SampleComparison;
import org.rosetta.sqlvalidator.crossdb.comparison.SelectResultComparator;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionEngine;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.sampling.CandidateSamplingResult;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.service.BaselineCaptureModels.SampleResult;
import org.rosetta.sqlvalidator.crossdb.service.BaselineCaptureModels.SqlResult;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public final class BaselineCaptureService {

    private final SelectExecutionEngine executionEngine;
    private final SelectResultComparator resultComparator;
    private final OverallStatusCalculator overallStatusCalculator;

    public BaselineCaptureService(
            SelectExecutionEngine executionEngine,
            SelectResultComparator resultComparator,
            OverallStatusCalculator overallStatusCalculator
    ) {
        this.executionEngine = executionEngine;
        this.resultComparator = resultComparator;
        this.overallStatusCalculator = overallStatusCalculator;
    }

    public SqlResult capture(
            CandidateSamplingResult samplingResult,
            Connection h2Connection,
            Connection postgresConnection,
            int maxRows,
            int statementTimeoutMs
    ) {
        CandidateAnalysis analysis = samplingResult.analysis();
        boolean postgresPassed = "PASSED".equalsIgnoreCase(
                analysis.candidate().postgresValidationStatus());

        List<SampleResult> samples = new ArrayList<>();
        for (ParameterTuple tuple : samplingResult.outcome().tuples()) {
            SelectExecutionResult h2 = executionEngine.execute(
                    h2Connection,
                    analysis.candidate().jdbcSql(),
                    tuple,
                    maxRows,
                    statementTimeoutMs);

            SelectExecutionResult postgres = postgresPassed
                    ? executionEngine.execute(
                            postgresConnection,
                            analysis.candidate().jdbcSql(),
                            tuple,
                            maxRows,
                            statementTimeoutMs)
                    : SelectExecutionResult.pendingMigration();

            SampleComparison comparison = resultComparator.compare(h2, postgres);
            samples.add(new SampleResult(
                    tuple.sampleIndex(), tuple, h2, postgres, comparison));
        }

        return new SqlResult(
                analysis,
                samplingResult.outcome().requestedSampleCount(),
                samplingResult.outcome().collectedSampleCount(),
                samples,
                overallStatusCalculator.calculate(samples, !postgresPassed));
    }
}
