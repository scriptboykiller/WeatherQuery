package org.rosetta.sqlvalidator.performance;

import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTupleValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reuses Batch-3 ParameterTuple. Adapt accessor names only if the actual
 * Batch-3 record differs.
 */
public final class PerformanceParameterMapper {

    public List<PerformanceParameter> map(ParameterTuple tuple) {
        List<PerformanceParameter> result = new ArrayList<>();

        for (ParameterTupleValue tupleValue : tuple.values()) {
            List<Integer> indexes = tupleValue.jdbcIndexes();
            List<?> expandedValues = expand(tupleValue.value(), indexes.size());

            for (int i = 0; i < indexes.size(); i++) {
                result.add(toParameter(
                        indexes.get(i),
                        tupleValue.logicalParameterIndex(),
                        tupleValue.name(),
                        tupleValue.javaTypeName(),
                        expandedValues.get(i)
                ));
            }
        }

        result.sort(Comparator.comparingInt(PerformanceParameter::jdbcIndex));
        validateUniqueAndContiguous(result);
        return List.copyOf(result);
    }

    private List<?> expand(Object value, int expectedSize) {
        if (!(value instanceof List<?> list)) {
            return java.util.Collections.nCopies(expectedSize, value);
        }
        if (list.size() == expectedSize) {
            return list;
        }
        if (list.size() == 1) {
            return java.util.Collections.nCopies(expectedSize, list.get(0));
        }
        throw new IllegalArgumentException(
                "Collection parameter size " + list.size()
                        + " does not match JDBC index count " + expectedSize);
    }

    private PerformanceParameter toParameter(
            int jdbcIndex,
            Integer logicalIndex,
            String name,
            String declaredJavaType,
            Object value
    ) {
        if (value == null) {
            return new PerformanceParameter(
                    jdbcIndex, logicalIndex, name,
                    PerformanceValueType.NULL,
                    jdbcTypeForDeclaredType(declaredJavaType),
                    null, null);
        }
        if (value instanceof String text) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.STRING, "VARCHAR", text);
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.INTEGER, "INTEGER", ((Number) value).intValue());
        }
        if (value instanceof Long number) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.LONG, "BIGINT", number);
        }
        if (value instanceof BigInteger number) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.BIG_INTEGER, "NUMERIC", number.toString());
        }
        if (value instanceof BigDecimal number) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.DECIMAL, "DECIMAL", number.toPlainString());
        }
        if (value instanceof Float || value instanceof Double) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.DOUBLE, "DOUBLE", ((Number) value).doubleValue());
        }
        if (value instanceof Boolean bool) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.BOOLEAN, "BOOLEAN", bool);
        }
        if (value instanceof java.sql.Date date) {
            value = date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.DATE, "DATE", date.toString());
        }
        if (value instanceof Time time) {
            value = time.toLocalTime();
        }
        if (value instanceof LocalTime time) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.TIME, "TIME", time.toString());
        }
        if (value instanceof Timestamp timestamp) {
            value = timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dateTime) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.TIMESTAMP, "TIMESTAMP", dateTime.toString());
        }
        if (value instanceof OffsetDateTime dateTime) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.OFFSET_DATE_TIME,
                    "TIMESTAMP_WITH_TIMEZONE", dateTime.toString());
        }
        if (value instanceof Instant instant) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.INSTANT,
                    "TIMESTAMP_WITH_TIMEZONE", instant.toString());
        }
        if (value instanceof UUID uuid) {
            return parameter(jdbcIndex, logicalIndex, name,
                    PerformanceValueType.UUID, "OTHER", uuid.toString());
        }
        if (value instanceof byte[] bytes) {
            return new PerformanceParameter(
                    jdbcIndex, logicalIndex, name,
                    PerformanceValueType.BYTES, "BINARY",
                    Base64.getEncoder().encodeToString(bytes), "BASE64");
        }

        throw new IllegalArgumentException(
                "Unsupported performance parameter type: " + value.getClass().getName());
    }

    private PerformanceParameter parameter(
            int jdbcIndex,
            Integer logicalIndex,
            String name,
            PerformanceValueType type,
            String jdbcType,
            Object value
    ) {
        return new PerformanceParameter(
                jdbcIndex, logicalIndex, name, type, jdbcType, value, null);
    }

    private String jdbcTypeForDeclaredType(String javaType) {
        String type = javaType == null ? "" : javaType;
        if (type.contains("Long")) return "BIGINT";
        if (type.contains("Integer") || type.contains("Short") || type.contains("Byte")) return "INTEGER";
        if (type.contains("BigDecimal") || type.contains("BigInteger")) return "DECIMAL";
        if (type.contains("Boolean")) return "BOOLEAN";
        if (type.contains("LocalDate") || type.equals("java.sql.Date")) return "DATE";
        if (type.contains("LocalTime") || type.equals("java.sql.Time")) return "TIME";
        if (type.contains("Timestamp") || type.contains("LocalDateTime")) return "TIMESTAMP";
        if (type.contains("UUID")) return "OTHER";
        if (type.equals("byte[]") || type.contains("Byte[]")) return "BINARY";
        return "VARCHAR";
    }

    private void validateUniqueAndContiguous(List<PerformanceParameter> parameters) {
        int expected = 1;
        for (PerformanceParameter parameter : parameters) {
            if (parameter.jdbcIndex() != expected) {
                throw new IllegalArgumentException(
                        "JDBC parameter indexes must be unique and contiguous from 1. Expected "
                                + expected + " but found " + parameter.jdbcIndex());
            }
            expected++;
        }
    }
}
