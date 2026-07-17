package org.rosetta.sqlvalidator.crossdb.sampling;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.TupleValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParameterTupleCodecTest {

    private final ParameterTupleCodec codec = new ParameterTupleCodec();

    @Test
    void roundTripsSpecialCharactersTypesAndCollection() {
        ParameterTuple original = new ParameterTuple(
                2,
                List.of(
                        value(1, List.of(1), "status", "java.lang.String", false, "A|B;C% D"),
                        value(2, List.of(2), "amount", "java.math.BigDecimal", false,
                                new BigDecimal("100.250")),
                        value(3, List.of(3), "date", "java.time.LocalDate", false,
                                LocalDate.of(2026, 7, 17)),
                        value(4, List.of(4), "ids", "java.util.List<java.lang.Long>", true,
                                List.of(42L))
                ));

        ParameterTuple decoded = codec.decode(codec.encode(original));

        assertEquals(2, decoded.sampleIndex());
        assertEquals("A|B;C% D", decoded.values().get(0).value());
        assertEquals(new BigDecimal("100.250"), decoded.values().get(1).value());
        assertEquals(LocalDate.of(2026, 7, 17), decoded.values().get(2).value());
        assertEquals(List.of(42L), decoded.values().get(3).value());
    }

    private TupleValue value(
            int logical,
            List<Integer> indexes,
            String name,
            String type,
            boolean collection,
            Object value
    ) {
        return new TupleValue(
                logical,
                indexes,
                name,
                type,
                collection,
                value,
                "CUSTOMER",
                name.toUpperCase());
    }
}
