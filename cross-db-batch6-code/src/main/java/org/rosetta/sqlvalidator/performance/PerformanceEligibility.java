package org.rosetta.sqlvalidator.performance;

public record PerformanceEligibility(boolean eligible, String reason) {
    public static PerformanceEligibility eligible() {
        return new PerformanceEligibility(true, "");
    }

    public static PerformanceEligibility rejected(String reason) {
        return new PerformanceEligibility(false, reason);
    }
}
