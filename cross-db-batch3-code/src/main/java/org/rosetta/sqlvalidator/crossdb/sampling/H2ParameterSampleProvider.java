package org.rosetta.sqlvalidator.crossdb.sampling;

import org.rosetta.sqlvalidator.crossdb.sampling.H2SamplingQueryBuilder.SamplingQuery;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ColumnMapping;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.OutcomeStatus;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.PlanStatus;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.SamplingOutcome;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.SamplingPlan;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.TupleValue;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ValueTransform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Executes only generated H2 parameter-sampling SELECTs. */
public final class H2ParameterSampleProvider {

    private final H2SamplingQueryBuilder queryBuilder;
    private final ParameterTupleCodec codec;

    public H2ParameterSampleProvider(
            H2SamplingQueryBuilder queryBuilder,
            ParameterTupleCodec codec
    ) {
        this.queryBuilder = queryBuilder;
        this.codec = codec;
    }

    public SamplingOutcome sample(
            Connection connection,
            SamplingPlan plan,
            int requestedSampleCount,
            boolean preferNonNullValues,
            int statementTimeoutMs
    ) {
        if (plan.status() == PlanStatus.NO_PARAMETERS) {
            return new SamplingOutcome(
                    OutcomeStatus.NO_PARAMETERS,
                    "NO_PARAMETERS",
                    "The SELECT has no parameters.",
                    requestedSampleCount,
                    List.of(new ParameterTuple(1, List.of())),
                    "",
                    "");
        }

        if (plan.status() != PlanStatus.READY) {
            OutcomeStatus status = plan.status() == PlanStatus.SKIPPED_UNSUPPORTED
                    ? OutcomeStatus.SKIPPED_UNSUPPORTED
                    : OutcomeStatus.MANUAL_MAPPING_REQUIRED;
            return new SamplingOutcome(
                    status,
                    plan.reasonCode(),
                    plan.reason(),
                    requestedSampleCount,
                    List.of(),
                    "",
                    "");
        }

        if (requestedSampleCount < 1) {
            throw new IllegalArgumentException("requestedSampleCount must be greater than zero");
        }

        SamplingQuery query = queryBuilder.build(plan, preferNonNullValues);
        List<ParameterTuple> tuples = new ArrayList<>();
        Set<String> distinctKeys = new LinkedHashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            int timeoutSeconds = Math.max(1, (int) Math.ceil(statementTimeoutMs / 1000.0));
            statement.setQueryTimeout(timeoutSeconds);
            statement.setInt(1, requestedSampleCount);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next() && tuples.size() < requestedSampleCount) {
                    List<TupleValue> values = new ArrayList<>();

                    for (int index = 0; index < plan.mappings().size(); index++) {
                        ColumnMapping mapping = plan.mappings().get(index);
                        Object raw = resultSet.getObject(query.resultAliases().get(index));
                        Object transformed = applyTransform(raw, mapping.transform());
                        Object finalValue = mapping.collection()
                                ? List.of(transformed)
                                : transformed;

                        values.add(new TupleValue(
                                mapping.logicalParameterIndex(),
                                mapping.jdbcIndexes(),
                                mapping.parameterName(),
                                mapping.javaType(),
                                mapping.collection(),
                                finalValue,
                                plan.tableExpression(),
                                mapping.sourceColumnName()));
                    }

                    ParameterTuple candidate = new ParameterTuple(tuples.size() + 1, values);
                    String key = codec.encode(new ParameterTuple(1, candidate.values()));
                    if (distinctKeys.add(key)) {
                        tuples.add(candidate);
                    }
                }
            }

            if (tuples.isEmpty()) {
                return new SamplingOutcome(
                        OutcomeStatus.NO_SAMPLE_DATA,
                        "NO_SAMPLE_DATA",
                        "H2 returned no usable real parameter tuple.",
                        requestedSampleCount,
                        List.of(),
                        query.sql(),
                        "");
            }

            return new SamplingOutcome(
                    OutcomeStatus.SAMPLED,
                    "SAMPLED",
                    "Real parameter tuples were sampled from H2.",
                    requestedSampleCount,
                    tuples,
                    query.sql(),
                    "");

        } catch (SQLException exception) {
            return new SamplingOutcome(
                    OutcomeStatus.H2_QUERY_FAILED,
                    "H2_SAMPLING_QUERY_FAILED",
                    "The H2 sampling query failed.",
                    requestedSampleCount,
                    List.of(),
                    query.sql(),
                    exception.getMessage());
        }
    }

    private Object applyTransform(Object value, ValueTransform transform) {
        if (value == null) return null;
        if (transform == ValueTransform.LOWERCASE) {
            return value.toString().toLowerCase(Locale.ROOT);
        }
        return value;
    }
}
