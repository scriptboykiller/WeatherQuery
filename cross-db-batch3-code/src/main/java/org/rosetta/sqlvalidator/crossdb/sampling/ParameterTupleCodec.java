package org.rosetta.sqlvalidator.crossdb.sampling;

import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.TupleValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dependency-free, deterministic and reversible serialization for the future
 * baseline CSV parameterValues field.
 *
 * Format: v1:<sampleIndex>:<entry>;<entry>
 */
public final class ParameterTupleCodec {

    private static final String PREFIX = "v1:";

    public String encode(ParameterTuple tuple) {
        return PREFIX + tuple.sampleIndex() + ":" + tuple.values().stream()
                .map(this::encodeValue)
                .collect(Collectors.joining(";"));
    }

    public ParameterTuple decode(String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported parameter tuple format");
        }

        String remainder = encoded.substring(PREFIX.length());
        int separator = remainder.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Missing sample index");
        }

        int sampleIndex = Integer.parseInt(remainder.substring(0, separator));
        String body = remainder.substring(separator + 1);
        if (body.isBlank()) {
            return new ParameterTuple(sampleIndex, List.of());
        }

        List<TupleValue> values = new ArrayList<>();
        for (String entry : body.split(";", -1)) {
            values.add(decodeValue(entry));
        }
        return new ParameterTuple(sampleIndex, values);
    }

    public String parameterNames(ParameterTuple tuple) {
        return tuple.values().stream()
                .map(TupleValue::parameterName)
                .collect(Collectors.joining(","));
    }

    public String parameterTypes(ParameterTuple tuple) {
        return tuple.values().stream()
                .map(value -> value.javaType() == null || value.javaType().isBlank()
                        ? inferType(value.value())
                        : value.javaType())
                .collect(Collectors.joining(","));
    }

    private String encodeValue(TupleValue parameter) {
        EncodedValue encoded = encodeObject(parameter.value());
        String indexes = parameter.jdbcIndexes().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String source = safe(parameter.sourceTable()) + "." + safe(parameter.sourceColumn());

        return String.join("|",
                escape(Integer.toString(parameter.logicalParameterIndex())),
                escape(indexes),
                escape(parameter.parameterName()),
                escape(parameter.javaType()),
                escape(Boolean.toString(parameter.collection())),
                escape(encoded.kind()),
                escape(encoded.value()),
                escape(source)
        );
    }

    private TupleValue decodeValue(String entry) {
        String[] fields = entry.split("\\|", -1);
        if (fields.length != 8) {
            throw new IllegalArgumentException("Invalid parameter entry field count: " + fields.length);
        }

        int logicalIndex = Integer.parseInt(unescape(fields[0]));
        List<Integer> indexes = List.of(unescape(fields[1]).split(","))
                .stream()
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .toList();

        String source = unescape(fields[7]);
        int dot = source.lastIndexOf('.');
        String sourceTable = dot < 0 ? "" : source.substring(0, dot);
        String sourceColumn = dot < 0 ? source : source.substring(dot + 1);

        return new TupleValue(
                logicalIndex,
                indexes,
                unescape(fields[2]),
                unescape(fields[3]),
                Boolean.parseBoolean(unescape(fields[4])),
                decodeObject(unescape(fields[5]), unescape(fields[6])),
                sourceTable,
                sourceColumn
        );
    }

    private EncodedValue encodeObject(Object value) {
        if (value == null) return new EncodedValue("NULL", "");
        if (value instanceof List<?> list) {
            if (list.size() != 1) {
                throw new IllegalArgumentException("Batch 3 supports collectionSampleSize=1 only");
            }
            EncodedValue nested = encodeObject(list.get(0));
            return new EncodedValue("LIST_" + nested.kind(), nested.value());
        }
        if (value instanceof byte[] bytes) {
            return new EncodedValue("BYTES", Base64.getEncoder().encodeToString(bytes));
        }
        if (value instanceof String text) return new EncodedValue("STRING", text);
        if (value instanceof Boolean bool) return new EncodedValue("BOOLEAN", bool.toString());
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return new EncodedValue("LONG", value.toString());
        }
        if (value instanceof BigInteger integer) return new EncodedValue("BIG_INTEGER", integer.toString());
        if (value instanceof Float || value instanceof Double) return new EncodedValue("DOUBLE", value.toString());
        if (value instanceof BigDecimal decimal) return new EncodedValue("BIG_DECIMAL", decimal.toPlainString());
        if (value instanceof LocalDate date) return new EncodedValue("LOCAL_DATE", date.toString());
        if (value instanceof LocalTime time) return new EncodedValue("LOCAL_TIME", time.toString());
        if (value instanceof LocalDateTime dateTime) return new EncodedValue("LOCAL_DATE_TIME", dateTime.toString());
        if (value instanceof OffsetDateTime dateTime) return new EncodedValue("OFFSET_DATE_TIME", dateTime.toString());
        if (value instanceof Instant instant) return new EncodedValue("INSTANT", instant.toString());
        if (value instanceof java.sql.Date date) return new EncodedValue("SQL_DATE", date.toLocalDate().toString());
        if (value instanceof Time time) return new EncodedValue("SQL_TIME", time.toLocalTime().toString());
        if (value instanceof Timestamp timestamp) {
            return new EncodedValue("SQL_TIMESTAMP", timestamp.toLocalDateTime().toString());
        }
        if (value instanceof UUID uuid) return new EncodedValue("UUID", uuid.toString());
        return new EncodedValue("STRING", value.toString());
    }

    private Object decodeObject(String kind, String value) {
        if (kind.startsWith("LIST_")) {
            return List.of(decodeObject(kind.substring("LIST_".length()), value));
        }
        return switch (kind) {
            case "NULL" -> null;
            case "STRING" -> value;
            case "BOOLEAN" -> Boolean.parseBoolean(value);
            case "LONG" -> Long.parseLong(value);
            case "BIG_INTEGER" -> new BigInteger(value);
            case "DOUBLE" -> Double.parseDouble(value);
            case "BIG_DECIMAL" -> new BigDecimal(value);
            case "LOCAL_DATE" -> LocalDate.parse(value);
            case "LOCAL_TIME" -> LocalTime.parse(value);
            case "LOCAL_DATE_TIME" -> LocalDateTime.parse(value);
            case "OFFSET_DATE_TIME" -> OffsetDateTime.parse(value);
            case "INSTANT" -> Instant.parse(value);
            case "SQL_DATE" -> java.sql.Date.valueOf(LocalDate.parse(value));
            case "SQL_TIME" -> Time.valueOf(LocalTime.parse(value));
            case "SQL_TIMESTAMP" -> Timestamp.valueOf(LocalDateTime.parse(value));
            case "UUID" -> UUID.fromString(value);
            case "BYTES" -> Base64.getDecoder().decode(value);
            default -> throw new IllegalArgumentException("Unsupported value kind: " + kind);
        };
    }

    private String inferType(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return "java.util.List<" + list.get(0).getClass().getName() + ">";
        }
        return value.getClass().getName();
    }

    private String escape(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    private String unescape(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record EncodedValue(String kind, String value) {
    }
}
