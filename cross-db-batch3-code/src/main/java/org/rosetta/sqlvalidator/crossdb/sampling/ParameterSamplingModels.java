package org.rosetta.sqlvalidator.crossdb.sampling;

import java.util.List;

/** Shared SELECT-only H2 sampling models for Batch 3. */
public final class ParameterSamplingModels {

    private ParameterSamplingModels() {
    }

    public enum ValueTransform {
        IDENTITY,
        LOWERCASE
    }

    public enum PlanStatus {
        READY,
        NO_PARAMETERS,
        MANUAL_MAPPING_REQUIRED,
        SKIPPED_UNSUPPORTED
    }

    public enum OutcomeStatus {
        SAMPLED,
        NO_PARAMETERS,
        NO_SAMPLE_DATA,
        MANUAL_MAPPING_REQUIRED,
        SKIPPED_UNSUPPORTED,
        H2_CONNECTION_FAILED,
        H2_QUERY_FAILED
    }

    public record BindingParameter(
            int jdbcIndex,
            int logicalParameterIndex,
            String parameterName,
            String javaType,
            boolean collection
    ) {
        public BindingParameter {
            if (jdbcIndex < 1) {
                throw new IllegalArgumentException("jdbcIndex must be greater than zero");
            }
            if (logicalParameterIndex < 1) {
                logicalParameterIndex = jdbcIndex;
            }
            parameterName = parameterName == null || parameterName.isBlank()
                    ? "param" + logicalParameterIndex
                    : parameterName.trim();
            javaType = javaType == null ? "" : javaType.trim();
        }

        public String logicalKey() {
            return logicalParameterIndex + "|" + parameterName;
        }
    }

    public record ColumnMapping(
            int logicalParameterIndex,
            List<Integer> jdbcIndexes,
            String parameterName,
            String javaType,
            boolean collection,
            String sourceColumnExpression,
            String sourceColumnName,
            ValueTransform transform
    ) {
        public ColumnMapping {
            jdbcIndexes = List.copyOf(jdbcIndexes);
            transform = transform == null ? ValueTransform.IDENTITY : transform;
        }
    }

    public record SamplingPlan(
            PlanStatus status,
            String reasonCode,
            String reason,
            String tableExpression,
            String tableAlias,
            List<ColumnMapping> mappings
    ) {
        public SamplingPlan {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
        }

        public static SamplingPlan ready(
                String tableExpression,
                String tableAlias,
                List<ColumnMapping> mappings
        ) {
            if (mappings == null || mappings.isEmpty()) {
                return new SamplingPlan(
                        PlanStatus.NO_PARAMETERS,
                        "NO_PARAMETERS",
                        "The SELECT has no JDBC parameters.",
                        tableExpression,
                        tableAlias,
                        List.of()
                );
            }
            return new SamplingPlan(
                    PlanStatus.READY,
                    "READY",
                    "All JDBC parameters have simple H2 source-column mappings.",
                    tableExpression,
                    tableAlias,
                    mappings
            );
        }

        public static SamplingPlan manual(String code, String reason) {
            return new SamplingPlan(
                    PlanStatus.MANUAL_MAPPING_REQUIRED,
                    code,
                    reason,
                    "",
                    "",
                    List.of()
            );
        }

        public static SamplingPlan unsupported(String code, String reason) {
            return new SamplingPlan(
                    PlanStatus.SKIPPED_UNSUPPORTED,
                    code,
                    reason,
                    "",
                    "",
                    List.of()
            );
        }
    }

    public record TupleValue(
            int logicalParameterIndex,
            List<Integer> jdbcIndexes,
            String parameterName,
            String javaType,
            boolean collection,
            Object value,
            String sourceTable,
            String sourceColumn
    ) {
        public TupleValue {
            jdbcIndexes = List.copyOf(jdbcIndexes);
        }
    }

    public record ParameterTuple(
            int sampleIndex,
            List<TupleValue> values
    ) {
        public ParameterTuple {
            if (sampleIndex < 1) {
                throw new IllegalArgumentException("sampleIndex must be greater than zero");
            }
            values = List.copyOf(values);
        }
    }

    public record SamplingOutcome(
            OutcomeStatus status,
            String reasonCode,
            String reason,
            int requestedSampleCount,
            List<ParameterTuple> tuples,
            String samplingSql,
            String errorMessage
    ) {
        public SamplingOutcome {
            tuples = tuples == null ? List.of() : List.copyOf(tuples);
        }

        public int collectedSampleCount() {
            return tuples.size();
        }
    }
}
