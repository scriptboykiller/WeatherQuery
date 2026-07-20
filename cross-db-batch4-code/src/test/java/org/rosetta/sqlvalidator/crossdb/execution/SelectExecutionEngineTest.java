package org.rosetta.sqlvalidator.crossdb.execution;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.normalization.CanonicalValueFormatter;
import org.rosetta.sqlvalidator.crossdb.normalization.ResultHashCalculator;
import org.rosetta.sqlvalidator.crossdb.normalization.ResultSetNormalizer;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTuple;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterTupleValue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelectExecutionEngineTest {

    private final SelectExecutionEngine engine = new SelectExecutionEngine(
            new JdbcParameterBinder(),
            new ResultSetNormalizer(new CanonicalValueFormatter()),
            new ResultHashCalculator(),
            new SqlOrderByDetector());

    @Test
    void executesAndHashesSelect() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:batch4execute;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE CUSTOMER(ID BIGINT PRIMARY KEY, NAME VARCHAR(50))");
                statement.execute("INSERT INTO CUSTOMER VALUES(1, 'Dexter'),(2, 'Alice')");
            }

            ParameterTuple tuple = new ParameterTuple(1, List.of(
                    new ParameterTupleValue(
                            1, List.of(1), "id", "java.lang.Long", false,
                            1L, "CUSTOMER", "ID")));

            SelectExecutionResult result = engine.execute(
                    connection,
                    "SELECT ID, NAME FROM CUSTOMER WHERE ID >= ?",
                    tuple,
                    1000,
                    5000);

            assertEquals(SelectExecutionStatus.SUCCESS, result.status());
            assertEquals(2, result.rowCount());
            assertEquals("ID:NUMBER|NAME:STRING", result.columnSignature());
            assertFalse(result.resultHash().isBlank());
        }
    }

    @Test
    void detectsResultTooLarge() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:batch4large;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE T(ID INT)");
                statement.execute("INSERT INTO T VALUES(1),(2),(3)");
            }

            SelectExecutionResult result = engine.execute(
                    connection,
                    "SELECT ID FROM T",
                    new ParameterTuple(1, List.of()),
                    2,
                    5000);

            assertEquals(SelectExecutionStatus.RESULT_TOO_LARGE, result.status());
            assertEquals(3, result.rowCount());
        }
    }
}
