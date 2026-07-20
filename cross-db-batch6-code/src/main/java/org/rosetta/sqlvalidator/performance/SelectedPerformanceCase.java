package org.rosetta.sqlvalidator.performance;

public record SelectedPerformanceCase(
        PerformanceSourceRow representative,
        SelectionReason reason
) {
}
