package org.rosetta.sqlvalidator.performance;

public enum SelectionReason {
    NONE,
    TOP_SLOWEST,
    MANUAL_INCLUDE,
    TOP_SLOWEST_AND_MANUAL_INCLUDE
}
