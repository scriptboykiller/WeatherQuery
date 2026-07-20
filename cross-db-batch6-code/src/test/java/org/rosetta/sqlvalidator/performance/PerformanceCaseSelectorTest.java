package org.rosetta.sqlvalidator.performance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceCaseSelectorTest {

    private final PerformanceCaseSelector selector =
            new PerformanceCaseSelector(new PerformanceEligibilityEvaluator());

    @Test
    void selectsTopSlowestAndManualUnionWithOneRepresentativeSample() {
        List<PerformanceSourceRow> rows = List.of(
                row("key-a", "SQL-A", 1, 10L),
                row("key-a", "SQL-A", 2, 100L),
                row("key-b", "SQL-B", 1, 50L),
                row("key-c", "SQL-C", 1, 20L)
        );

        List<SelectedPerformanceCase> selected = selector.select(
                rows, 1, 1, true, List.of("SQL-C"));

        assertEquals(2, selected.size());
        assertTrue(selected.stream().anyMatch(item ->
                item.representative().effectiveSqlId().equals("SQL-B")
                        && item.reason() == SelectionReason.TOP_SLOWEST));
        assertTrue(selected.stream().anyMatch(item ->
                item.representative().effectiveSqlId().equals("SQL-C")
                        && item.reason() == SelectionReason.MANUAL_INCLUDE));
        assertTrue(selected.stream().allMatch(item ->
                item.representative().sampleIndex() == 1));
    }

    private PerformanceSourceRow row(
            String key, String sqlId, int sample, Long time) {
        return new PerformanceSourceRow(
                "run", key, sqlId, sqlId, "SELECT", sample,
                "encoded", "SELECT ?", "SELECT ?", "SUCCESS",
                "SUCCESS", "MATCH", time, time);
    }
}
