package org.rosetta.sqlvalidator.performance;

public enum PerformanceStatus {
    SUCCESS,
    NOT_SELECTED,
    NOT_ELIGIBLE,
    COVERED_BY_SELECTED_SAMPLE,
    PARAMETER_DECODE_FAILED,
    TOOL_FAILED,
    TIMEOUT,
    REPORT_MISSING,
    RESULT_JSON_INVALID
}
