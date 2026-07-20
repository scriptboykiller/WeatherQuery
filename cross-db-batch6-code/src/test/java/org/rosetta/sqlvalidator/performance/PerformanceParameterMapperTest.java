package org.rosetta.sqlvalidator.performance;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTupleValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerformanceParameterMapperTest {

    @Test
    void expandsRepeatedIndexesAndKeepsTypes() {
        ParameterTuple tuple = new ParameterTuple(
                1,
                List.of(
                        new ParameterTupleValue(
                                1, List.of(1, 3), "id",
                                "java.lang.Long", false, 9L, "T", "ID"),
                        new ParameterTupleValue(
                                2, List.of(2), "amount",
                                "java.math.BigDecimal", false,
                                new BigDecimal("12.30"), "T", "AMOUNT")
                ));

        List<PerformanceParameter> parameters =
                new PerformanceParameterMapper().map(tuple);

        assertEquals(3, parameters.size());
        assertEquals(1, parameters.get(0).jdbcIndex());
        assertEquals(PerformanceValueType.LONG, parameters.get(0).valueType());
        assertEquals(PerformanceValueType.DECIMAL, parameters.get(1).valueType());
        assertEquals(3, parameters.get(2).jdbcIndex());
        assertEquals(9L, parameters.get(2).value());
    }

    @Test
    void mapsDate() {
        ParameterTuple tuple = new ParameterTuple(
                1,
                List.of(new ParameterTupleValue(
                        1, List.of(1), "date",
                        "java.time.LocalDate", false,
                        LocalDate.of(2026, 7, 20), "T", "D")));

        PerformanceParameter parameter =
                new PerformanceParameterMapper().map(tuple).get(0);

        assertEquals(PerformanceValueType.DATE, parameter.valueType());
        assertEquals("2026-07-20", parameter.value());
    }
}
