package org.rosetta.sqlvalidator.crossdb.eligibility;

public record SelectInspectionResult(
        boolean select,
        boolean multipleStatements,
        boolean dynamicIdentifier,
        boolean unsafe,
        boolean nonDeterministic,
        String reasonCode,
        String reason
) {
}
