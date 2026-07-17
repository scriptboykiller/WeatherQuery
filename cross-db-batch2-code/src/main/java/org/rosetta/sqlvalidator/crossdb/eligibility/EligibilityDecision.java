package org.rosetta.sqlvalidator.crossdb.eligibility;

/** EligibilityStatus is created in Batch 1. Reuse that enum. */
public record EligibilityDecision(
        EligibilityStatus status,
        String reasonCode,
        String reason
) {
    public boolean executableLater() {
        return status == EligibilityStatus.AUTO_COMPARABLE
                || status == EligibilityStatus.BASELINE_ONLY
                || status == EligibilityStatus.EXECUTION_ONLY;
    }
}
