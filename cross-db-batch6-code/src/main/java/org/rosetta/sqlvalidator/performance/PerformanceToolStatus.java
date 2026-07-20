package org.rosetta.sqlvalidator.performance;

public enum PerformanceToolStatus {
    SUCCESS,
    INVALID_REQUEST,
    H2_EXECUTION_FAILED,
    POSTGRES_EXECUTION_FAILED,
    BOTH_EXECUTION_FAILED,
    REPORT_GENERATION_FAILED,
    TIMEOUT,
    INTERNAL_ERROR
}
