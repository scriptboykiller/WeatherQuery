package org.rosetta.sqlvalidator.crossdb.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaselineKeyFactoryTest {

    private final BaselineKeyFactory factory = new BaselineKeyFactory();

    @Test
    void createsStableNormalizedKey() {
        String key = factory.create(new BaselineIdentity(
                "DMR Service", "CustomerRepository", "find Active",
                "SPRING_DATA_QUERY", "QUERY", 1
        )).orElseThrow();

        assertEquals(
                "dmr service|customerrepository|find active|spring_data_query|query|1",
                key
        );
    }

    @Test
    void usesPlaceholderForMissingOptionalVariableName() {
        String key = factory.create(new BaselineIdentity(
                "service", "Repository", "findAll",
                "ENTITY_MANAGER", "", 2
        )).orElseThrow();

        assertEquals("service|repository|findall|entity_manager|-|2", key);
    }

    @Test
    void returnsEmptyWhenRequiredMetadataIsMissing() {
        assertTrue(factory.create(new BaselineIdentity(
                "service", "", "findAll",
                "ENTITY_MANAGER", "sql", 1
        )).isEmpty());
    }
}
