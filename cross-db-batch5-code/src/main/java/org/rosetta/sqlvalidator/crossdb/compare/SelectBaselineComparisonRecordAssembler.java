package org.rosetta.sqlvalidator.crossdb.compare;

import org.rosetta.sqlvalidator.crossdb.csv.SelectBaselineComparisonRecord;

import java.util.List;

/** Adapt constructor/builder calls to the actual Batch-1 output model. */
public final class SelectBaselineComparisonRecordAssembler {

    public List<SelectBaselineComparisonRecord> assemble(
            List<BaselineComparisonOutcome> outcomes,
            String currentPostgresDatabase,
            String currentPostgresSchema
    ) {
        return outcomes.stream()
                .map(outcome -> {
                    var baseline = outcome.baseline();
                    var current = outcome.currentCandidate();
                    var postgres = outcome.currentPostgresResult();

                    return new SelectBaselineComparisonRecord(
                            baseline.baselineRunId(),
                            baseline.baselineCreatedAt(),
                            baseline.normalizationVersion(),
                            baseline.baselineKey(),
                            baseline.baselineSqlId(),
                            current == null ? "" : current.sqlId(),
                            baseline.serviceName(),
                            baseline.className(),
                            baseline.methodName(),
                            baseline.sourceType(),
                            baseline.sqlVariableName(),
                            baseline.occurrenceIndex(),
                            baseline.statementType(),
                            baseline.h2Database(),
                            baseline.h2Schema(),
                            currentPostgresDatabase,
                            currentPostgresSchema,
                            current == null ? "" : current.postgresValidationStatus(),
                            outcome.eligibility().status().name(),
                            outcome.eligibility().reason(),
                            baseline.sampleIndex(),
                            baseline.requestedSampleCount(),
                            baseline.parameterNames(),
                            baseline.parameterTypes(),
                            baseline.parameterValues(),
                            baseline.baselineH2JdbcSql(),
                            baseline.baselineH2ExecutionStatus(),
                            baseline.baselineH2RowCount(),
                            baseline.baselineH2ResultHash(),
                            baseline.baselineH2ColumnSignature(),
                            current == null ? "" : current.jdbcSql(),
                            postgres.status().name(),
                            postgres.rowCount(),
                            postgres.resultHash(),
                            postgres.columnSignature(),
                            postgres.executionTimeMs(),
                            postgres.errorMessage(),
                            outcome.comparison().status().name(),
                            overall(outcome),
                            outcome.comparison().differenceCategory().name(),
                            outcome.comparison().differenceMessage()
                    );
                })
                .toList();
    }

    private String overall(BaselineComparisonOutcome outcome) {
        return switch (outcome.comparison().status()) {
            case MATCH -> "ALL_SAMPLES_MATCH";
            case MISMATCH -> "PARTIAL_MISMATCH";
            case H2_EXECUTION_FAILED, POSTGRES_EXECUTION_FAILED,
                    BOTH_EXECUTION_FAILED, RESULT_TOO_LARGE ->
                    "PARTIAL_EXECUTION_FAILURE";
            case CURRENT_POSTGRES_NOT_READY -> "PENDING_SQL_MIGRATION";
            case EXECUTION_ONLY -> "EXECUTION_ONLY";
            default -> "NOT_EXECUTED";
        };
    }
}
