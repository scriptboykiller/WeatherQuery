package org.rosetta.sqlvalidator.performance;

public record PerformanceInvocationOutcome(
        PerformanceStatus status,
        String htmlReportPath,
        String errorCode,
        String errorMessage
) {
}
