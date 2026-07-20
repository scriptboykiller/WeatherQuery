package org.rosetta.sqlvalidator.crossdb.execution;

import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTupleValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class JdbcParameterBinder {

    public void bind(PreparedStatement statement, ParameterTuple tuple) throws SQLException {
        for (ParameterTupleValue parameter : tuple.values()) {
            Object value = unwrapCollection(parameter.value());
            for (Integer jdbcIndex : parameter.jdbcIndexes()) {
                bindOne(statement, jdbcIndex, value);
            }
        }
    }

    private Object unwrapCollection(Object value) {
        if (!(value instanceof List<?> list)) {
            return value;
        }
        if (list.size() != 1) {
            throw new IllegalArgumentException(
                    "Cross DB MVP supports collection size one only");
        }
        return list.get(0);
    }

    private void bindOne(PreparedStatement statement, int index, Object value)
            throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else if (value instanceof String text) {
            statement.setString(index, text);
        } else if (value instanceof Integer number) {
            statement.setInt(index, number);
        } else if (value instanceof Long number) {
            statement.setLong(index, number);
        } else if (value instanceof Short number) {
            statement.setShort(index, number);
        } else if (value instanceof Byte number) {
            statement.setByte(index, number);
        } else if (value instanceof BigDecimal decimal) {
            statement.setBigDecimal(index, decimal);
        } else if (value instanceof BigInteger integer) {
            statement.setBigDecimal(index, new BigDecimal(integer));
        } else if (value instanceof Double number) {
            statement.setDouble(index, number);
        } else if (value instanceof Float number) {
            statement.setFloat(index, number);
        } else if (value instanceof Boolean bool) {
            statement.setBoolean(index, bool);
        } else if (value instanceof LocalDate date) {
            statement.setObject(index, date);
        } else if (value instanceof LocalTime time) {
            statement.setObject(index, time);
        } else if (value instanceof LocalDateTime dateTime) {
            statement.setObject(index, dateTime);
        } else if (value instanceof OffsetDateTime dateTime) {
            statement.setObject(index, dateTime);
        } else if (value instanceof Instant instant) {
            statement.setTimestamp(index, Timestamp.from(instant));
        } else if (value instanceof UUID uuid) {
            statement.setObject(index, uuid);
        } else if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else {
            statement.setObject(index, value);
        }
    }
}
