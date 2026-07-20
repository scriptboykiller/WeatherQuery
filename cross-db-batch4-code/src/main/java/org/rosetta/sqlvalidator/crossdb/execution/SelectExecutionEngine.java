package org.rosetta.sqlvalidator.crossdb.execution;

import org.rosetta.sqlvalidator.crossdb.normalization.NormalizedResult;
import org.rosetta.sqlvalidator.crossdb.normalization.ResultHashCalculator;
import org.rosetta.sqlvalidator.crossdb.normalization.ResultSetNormalizer;
import org.rosetta.sqlvalidator.crossdb.normalization.UnsupportedResultTypeException;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SelectExecutionEngine {

    private final JdbcParameterBinder parameterBinder;
    private final ResultSetNormalizer resultSetNormalizer;
    private final ResultHashCalculator hashCalculator;
    private final SqlOrderByDetector orderByDetector;

    public SelectExecutionEngine(
            JdbcParameterBinder parameterBinder,
            ResultSetNormalizer resultSetNormalizer,
            ResultHashCalculator hashCalculator,
            SqlOrderByDetector orderByDetector
    ) {
        this.parameterBinder = parameterBinder;
        this.resultSetNormalizer = resultSetNormalizer;
        this.hashCalculator = hashCalculator;
        this.orderByDetector = orderByDetector;
    }

    public SelectExecutionResult execute(
            Connection connection,
            String jdbcSql,
            ParameterTuple tuple,
            int maxRows,
            int statementTimeoutMs
    ) {
        long start = System.nanoTime();

        try (PreparedStatement statement = connection.prepareStatement(jdbcSql)) {
            statement.setQueryTimeout(Math.max(
                    1, (int) Math.ceil(statementTimeoutMs / 1000.0)));
            parameterBinder.bind(statement, tuple);

            try (ResultSet resultSet = statement.executeQuery()) {
                NormalizedResult normalized = resultSetNormalizer.read(
                        resultSet, maxRows, orderByDetector.hasTopLevelOrderBy(jdbcSql));
                long elapsedMs = elapsedMs(start);

                if (normalized.resultTooLarge()) {
                    return new SelectExecutionResult(
                            SelectExecutionStatus.RESULT_TOO_LARGE,
                            normalized.rowCount(), "", normalized.columnSignature(),
                            elapsedMs,
                            "Result contains more than " + maxRows + " rows.",
                            normalized);
                }

                return new SelectExecutionResult(
                        SelectExecutionStatus.SUCCESS,
                        normalized.rowCount(), hashCalculator.hash(normalized),
                        normalized.columnSignature(), elapsedMs, "", normalized);
            }
        } catch (UnsupportedResultTypeException exception) {
            return new SelectExecutionResult(
                    SelectExecutionStatus.UNSUPPORTED_RESULT_TYPE,
                    null, "", "", elapsedMs(start), exception.getMessage(), null);
        } catch (SQLException | RuntimeException exception) {
            return new SelectExecutionResult(
                    SelectExecutionStatus.EXECUTION_FAILED,
                    null, "", "", elapsedMs(start), exception.getMessage(), null);
        }
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
