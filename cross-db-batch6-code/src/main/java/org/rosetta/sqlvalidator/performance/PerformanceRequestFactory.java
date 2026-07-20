package org.rosetta.sqlvalidator.performance;

import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTupleCodec;
import org.rosetta.sqlvalidator.crossdb.sql.JdbcPlaceholderCounter;

import java.util.List;

public final class PerformanceRequestFactory {

    private final ParameterTupleCodec tupleCodec;
    private final PerformanceParameterMapper parameterMapper;
    private final JdbcPlaceholderCounter placeholderCounter;
    private final CaseIdFactory caseIdFactory;

    public PerformanceRequestFactory(
            ParameterTupleCodec tupleCodec,
            PerformanceParameterMapper parameterMapper,
            JdbcPlaceholderCounter placeholderCounter,
            CaseIdFactory caseIdFactory
    ) {
        this.tupleCodec = tupleCodec;
        this.parameterMapper = parameterMapper;
        this.placeholderCounter = placeholderCounter;
        this.caseIdFactory = caseIdFactory;
    }

    public PerformanceToolRequest create(PerformanceSourceRow row) {
        ParameterTuple tuple = tupleCodec.decode(row.parameterValues());
        List<PerformanceParameter> parameters = parameterMapper.map(tuple);

        validatePlaceholderCount("H2", row.h2JdbcSql(), parameters.size());
        validatePlaceholderCount("PostgreSQL", row.postgresJdbcSql(), parameters.size());

        return new PerformanceToolRequest(
                "v2",
                caseIdFactory.create(row),
                row.effectiveSqlId(),
                row.baselineKey(),
                row.sampleIndex(),
                row.h2JdbcSql(),
                row.postgresJdbcSql(),
                parameters,
                true,
                "index.html"
        );
    }

    private void validatePlaceholderCount(
            String database,
            String sql,
            int parameterCount
    ) {
        int placeholders = placeholderCounter.count(sql);
        if (placeholders != parameterCount) {
            throw new IllegalArgumentException(
                    database + " placeholder count " + placeholders
                            + " differs from parameter count " + parameterCount);
        }
    }
}
