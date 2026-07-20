package org.rosetta.sqlvalidator.crossdb.normalization;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalValueFormatterTest {

    private final CanonicalValueFormatter formatter = new CanonicalValueFormatter();

    @Test
    void normalizesEquivalentNumbers() {
        assertEquals(
                formatter.format(new BigDecimal("1.00"), Types.DECIMAL),
                formatter.format(new BigDecimal("1.0"), Types.NUMERIC));
    }

    @Test
    void normalizesDateAndBoolean() {
        assertEquals("DATE:2026-07-20",
                formatter.format(LocalDate.of(2026, 7, 20), Types.DATE));
        assertEquals("BOOL:true", formatter.format(1, Types.BOOLEAN));
    }
}
