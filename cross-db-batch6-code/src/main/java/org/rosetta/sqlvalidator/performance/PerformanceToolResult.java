package org.rosetta.sqlvalidator.performance;

/**
 * Minimal machine-readable result contract. Detailed timings stay in HTML.
 */
public record PerformanceToolResult(
        String responseVersion,
        String caseId,
        PerformanceToolStatus status,
        String htmlReport,
        String errorCode,
        String errorMessage
) {
}
