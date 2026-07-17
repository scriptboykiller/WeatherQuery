package org.rosetta.sqlvalidator.crossdb.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcPlaceholderCounterTest {

    private final JdbcPlaceholderCounter counter = new JdbcPlaceholderCounter();

    @Test
    void ignoresQuestionMarksInsideQuotesAndComments() {
        String sql = """
                SELECT *
                FROM customer
                WHERE name = '?'
                  AND id = ?
                  -- ignored ?
                  AND status = ?
                  /* ignored ? */
                """;

        assertEquals(2, counter.count(sql));
    }
}
