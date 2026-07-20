package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.csv.SelectBaselineRecord;
import org.rosetta.sqlvalidator.crossdb.execution.SelectExecutionResult;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTupleCodec;
import org.rosetta.sqlvalidator.crossdb.service.BaselineCaptureModels.RunMetadata;
import org.rosetta.sqlvalidator.crossdb.service.BaselineCaptureModels.SampleResult;
import org.rosetta.sqlvalidator.crossdb.service.BaselineCaptureModels.SqlResult;

import java.util.ArrayList;
import java.util.List;

public final class SelectBaselineRecordAssembler {

    private final ParameterTupleCodec parameterTupleCodec;

    public SelectBaselineRecordAssembler(ParameterTupleCodec parameterTupleCodec) {
        this.parameterTupleCodec = parameterTupleCodec;
    }

    public List<SelectBaselineRecord> assemble(SqlResult result, RunMetadata metadata) {
        List<SelectBaselineRecord> records = new ArrayList<>();

        for (SampleResult sample : result.samples()) {
            SelectExecutionResult h2 = sample.h2Result();
            SelectExecutionResult pg = sample.postgresResult();

            records.add(new SelectBaselineRecord(
                    metadata.baselineRunId(),
                    metadata.baselineCreatedAt(),
                    metadata.normalizationVersion(),
                    result.analysis().candidate().baselineKey(),
                    result.analysis().candidate().sqlId(),
                    result.analysis().candidate().serviceName(),
                    result.analysis().candidate().className(),
                    result.analysis().candidate().methodName(),
                    result.analysis().candidate().sourceType(),
                    result.analysis().candidate().sqlVariableName(),
                    result.analysis().candidate().occurrenceIndex(),
                    result.analysis().candidate().statementType(),
                    metadata.h2Database(),
                    metadata.h2Schema(),
                    metadata.postgresDatabase(),
                    metadata.postgresSchema(),
                    result.analysis().candidate().postgresValidationStatus(),
                    result.analysis().eligibility().status().name(),
                    result.analysis().eligibility().reason(),
                    sample.sampleIndex(),
                    result.requestedSampleCount(),
                    result.collectedSampleCount(),
                    parameterTupleCodec.parameterNames(sample.parameterTuple()),
                    parameterTupleCodec.parameterTypes(sample.parameterTuple()),
                    parameterTupleCodec.encode(sample.parameterTuple()),
                    result.analysis().candidate().jdbcSql(),
                    h2.status().name(),
                    h2.rowCount(),
                    h2.resultHash(),
                    h2.columnSignature(),
                    h2.executionTimeMs(),
                    h2.errorMessage(),
                    result.analysis().candidate().jdbcSql(),
                    pg.status().name(),
                    pg.rowCount(),
                    pg.resultHash(),
                    pg.columnSignature(),
                    pg.executionTimeMs(),
                    pg.errorMessage(),
                    sample.comparison().status().name(),
                    result.overallStatus().name(),
                    sample.comparison().differenceCategory().name(),
                    sample.comparison().differenceMessage()));
        }

        return List.copyOf(records);
    }
}
