package org.rosetta.sqlvalidator.crossdb.compare;

public enum BaselineMatchStatus {
    MATCHED,
    BASELINE_KEY_MISSING,
    BASELINE_KEY_NOT_FOUND,
    AMBIGUOUS_BASELINE_KEY
}
