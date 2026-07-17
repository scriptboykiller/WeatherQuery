package org.rosetta.sqlvalidator.crossdb.eligibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelectSafetyInspectorTest {

    private final SelectSafetyInspector inspector = new SelectSafetyInspector();

    @Test
    void acceptsSimpleSelect() {
        SelectInspectionResult result =
                inspector.inspect("SELECT * FROM customer WHERE id = ?");
        assertTrue(result.select());
        assertFalse(result.unsafe());
        assertFalse(result.nonDeterministic());
    }

    @Test
    void rejectsSelectForUpdate() {
        SelectInspectionResult result = inspector.inspect(
                "SELECT * FROM customer WHERE id = ? FOR UPDATE");
        assertTrue(result.unsafe());
        assertEquals("UNSAFE_SELECT", result.reasonCode());
    }

    @Test
    void marksCurrentTimestampAsNonDeterministic() {
        assertTrue(inspector.inspect("SELECT CURRENT_TIMESTAMP")
                .nonDeterministic());
    }

    @Test
    void detectsMultipleStatementsButAllowsTrailingSemicolon() {
        assertFalse(inspector.inspect("SELECT 1;").multipleStatements());
        assertTrue(inspector.inspect("SELECT 1; SELECT 2").multipleStatements());
    }
}
