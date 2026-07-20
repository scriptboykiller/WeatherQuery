package org.rosetta.sqlvalidator.crossdb.normalization;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public final class CanonicalValueFormatter {

    public String format(Object value, int jdbcType) {
        if (value == null) {
            return "NULL";
        }

        JDBCType type = safeJdbcType(jdbcType);

        if (value instanceof Boolean bool) {
            return "BOOL:" + bool;
        }
        if ((type == JDBCType.BOOLEAN || type == JDBCType.BIT)
                && value instanceof Number number) {
            return "BOOL:" + (number.intValue() != 0);
        }

        if (value instanceof BigDecimal decimal) {
            return "NUM:" + normalizeDecimal(decimal);
        }
        if (value instanceof BigInteger integer) {
            return "NUM:" + integer;
        }
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return "NUM:" + value;
        }
        if (value instanceof Float || value instanceof Double) {
            return "NUM:" + normalizeDecimal(new BigDecimal(value.toString()));
        }

        if (value instanceof java.sql.Date date) {
            return "DATE:" + date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return "DATE:" + date;
        }
        if (value instanceof Time time) {
            return "TIME:" + time.toLocalTime();
        }
        if (value instanceof LocalTime time) {
            return "TIME:" + time;
        }
        if (value instanceof Timestamp timestamp) {
            return "TIMESTAMP:" + normalizeDateTime(timestamp.toLocalDateTime());
        }
        if (value instanceof LocalDateTime dateTime) {
            return "TIMESTAMP:" + normalizeDateTime(dateTime);
        }
        if (value instanceof OffsetDateTime dateTime) {
            return "INSTANT:" + dateTime.toInstant();
        }
        if (value instanceof Instant instant) {
            return "INSTANT:" + instant;
        }

        if (value instanceof UUID uuid) {
            return "UUID:" + uuid.toString().toLowerCase();
        }
        if (value instanceof byte[] bytes) {
            return "BYTES:" + Base64.getEncoder().encodeToString(bytes);
        }

        if (value instanceof Blob || value instanceof Clob || value instanceof Array) {
            throw new UnsupportedResultTypeException(
                    "Unsupported JDBC result value: " + value.getClass().getName());
        }

        return "TEXT:" + value;
    }

    public String typeFamily(int jdbcType) {
        JDBCType type = safeJdbcType(jdbcType);
        return switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL,
                    DOUBLE, NUMERIC, DECIMAL -> "NUMBER";
            case BOOLEAN, BIT -> "BOOLEAN";
            case DATE -> "DATE";
            case TIME, TIME_WITH_TIMEZONE -> "TIME";
            case TIMESTAMP, TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP";
            case BINARY, VARBINARY, LONGVARBINARY, BLOB -> "BINARY";
            case CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR,
                    LONGNVARCHAR, CLOB, NCLOB -> "STRING";
            default -> type.getName();
        };
    }

    private String normalizeDecimal(BigDecimal decimal) {
        BigDecimal normalized = decimal.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private String normalizeDateTime(LocalDateTime value) {
        return value.withNano(value.getNano() / 1_000_000 * 1_000_000).toString();
    }

    private JDBCType safeJdbcType(int jdbcType) {
        try {
            return JDBCType.valueOf(jdbcType);
        } catch (IllegalArgumentException exception) {
            return JDBCType.OTHER;
        }
    }
}
