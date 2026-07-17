package org.rosetta.sqlvalidator.crossdb.sampling;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ColumnMapping;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.OutcomeStatus;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.SamplingOutcome;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.SamplingPlan;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ValueTransform;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class H2ParameterSampleProviderTest {

    private final H2ParameterSampleProvider provider =
            new H2ParameterSampleProvider(
                    new H2SamplingQueryBuilder(),
                    new ParameterTupleCodec());

    @Test
    void samplesThreeDistinctSameRowTuples() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:batch3;DB_CLOSE_DELAY=-1")) {

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE CUSTOMER(
                            ID BIGINT PRIMARY KEY,
                            STATUS VARCHAR(20),
                            REGION_ID BIGINT,
                            NAME VARCHAR(100)
                        )
                        """);
                statement.execute("""
                        INSERT INTO CUSTOMER(ID, STATUS, REGION_ID, NAME)
                        VALUES
                          (1, 'ACTIVE', 10, 'Dexter'),
                          (2, 'INACTIVE', 20, 'Alice'),
                          (3, 'ACTIVE', 30, 'Bob')
                        """);
            }

            SamplingPlan plan = SamplingPlan.ready(
                    "CUSTOMER",
                    "",
                    List.of(
                            mapping(1, "status", "STATUS", ValueTransform.IDENTITY, false),
                            mapping(2, "regionId", "REGION_ID", ValueTransform.IDENTITY, false)
                    ));

            SamplingOutcome outcome = provider.sample(connection, plan, 3, true, 5000);

            assertEquals(OutcomeStatus.SAMPLED, outcome.status());
            assertEquals(3, outcome.collectedSampleCount());
            assertTrue(outcome.tuples().stream()
                    .allMatch(tuple -> tuple.values().size() == 2));
        }
    }

    @Test
    void appliesLowercaseAndCollectionSizeOne() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:batch3lower;DB_CLOSE_DELAY=-1")) {

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE CUSTOMER(ID BIGINT PRIMARY KEY, NAME VARCHAR(100))");
                statement.execute("INSERT INTO CUSTOMER(ID, NAME) VALUES (1, 'Dexter')");
            }

            SamplingPlan plan = SamplingPlan.ready(
                    "CUSTOMER",
                    "",
                    List.of(
                            mapping(1, "name", "NAME", ValueTransform.LOWERCASE, false),
                            mapping(2, "ids", "ID", ValueTransform.IDENTITY, true)
                    ));

            SamplingOutcome outcome = provider.sample(connection, plan, 1, true, 5000);

            assertEquals("dexter", outcome.tuples().get(0).values().get(0).value());
            assertEquals(List.of(1L), outcome.tuples().get(0).values().get(1).value());
        }
    }

    private ColumnMapping mapping(
            int logical,
            String name,
            String column,
            ValueTransform transform,
            boolean collection
    ) {
        return new ColumnMapping(
                logical,
                List.of(logical),
                name,
                "",
                collection,
                column,
                column,
                transform);
    }
}
