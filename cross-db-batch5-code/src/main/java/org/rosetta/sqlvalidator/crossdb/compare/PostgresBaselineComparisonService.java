package org.rosetta.sqlvalidator.crossdb.compare;

import org.rosetta.sqlvalidator.crossdb.baseline.SelectBaselineSnapshot;
import org.rosetta.sqlvalidator.crossdb.comparison.SampleComparison;
import org.rosetta.sqlvalidator.crossdb.comparison.SelectResultComparator;
import org.rosetta.sqlvalidator.crossdb.eligibility.DifferenceCategory;
import org.rosetta.sqlvalidator.crossdb.eligibility.SampleComparisonStatus;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionEngine;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTupleCodec;

import java.sql.Connection;

public final class PostgresBaselineComparisonService {

    private final ParameterTupleCodec tupleCodec;
    private final SelectExecutionEngine executionEngine;
    private final SelectResultComparator comparator;
    private final SavedH2BaselineResultFactory savedH2Factory;

    public PostgresBaselineComparisonService(
            ParameterTupleCodec tupleCodec,
            SelectExecutionEngine executionEngine,
            SelectResultComparator comparator,
            SavedH2BaselineResultFactory savedH2Factory
    ) {
        this.tupleCodec = tupleCodec;
        this.executionEngine = executionEngine;
        this.comparator = comparator;
        this.savedH2Factory = savedH2Factory;
    }

    public BaselineComparisonOutcome compare(
            SelectBaselineSnapshot baseline,
            CurrentSqlBaselineMatcher.Match match,
            ComparisonEligibilityDecision eligibility,
            Connection postgresConnection,
            int maxRows,
            int statementTimeoutMs
    ) {
        CrossDatabaseCandidate current = match.currentCandidate();

        if (!eligibility.executable()) {
            SampleComparisonStatus status = eligibility.status().name()
                    .equals("CURRENT_POSTGRES_NOT_READY")
                    ? SampleComparisonStatus.CURRENT_POSTGRES_NOT_READY
                    : SampleComparisonStatus.NOT_EXECUTED;

            return new BaselineComparisonOutcome(
                    baseline,
                    current,
                    eligibility,
                    SelectExecutionResult.notExecuted(eligibility.reason()),
                    new SampleComparison(status,
                            DifferenceCategory.BASELINE_MAPPING_ERROR,
                            eligibility.reason())
            );
        }

        try {
            ParameterTuple tuple = tupleCodec.decode(baseline.parameterValues());
            SelectExecutionResult postgresResult = executionEngine.execute(
                    postgresConnection,
                    current.jdbcSql(),
                    tuple,
                    maxRows,
                    statementTimeoutMs
            );

            SampleComparison comparison = comparator.compare(
                    savedH2Factory.create(baseline),
                    postgresResult
            );

            return new BaselineComparisonOutcome(
                    baseline, current, eligibility, postgresResult, comparison);

        } catch (RuntimeException exception) {
            return new BaselineComparisonOutcome(
                    baseline,
                    current,
                    eligibility,
                    SelectExecutionResult.notExecuted(
                            "Saved parameterValues could not be decoded: "
                                    + exception.getMessage()),
                    new SampleComparison(
                            SampleComparisonStatus.NOT_EXECUTED,
                            DifferenceCategory.BASELINE_MAPPING_ERROR,
                            "Saved parameterValues could not be decoded.")
            );
        }
    }
}
