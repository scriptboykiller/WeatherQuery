package org.rosetta.sqlvalidator.crossdb.compare;

import org.rosetta.sqlvalidator.crossdb.eligibility.EligibilityStatus;

public record ComparisonEligibilityDecision(
        EligibilityStatus status,
        String reasonCode,
        String reason
) {
    public boolean executable() {
        return status == EligibilityStatus.AUTO_COMPARABLE;
    }
}
