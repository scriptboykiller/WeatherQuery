package org.rosetta.sqlvalidator.crossdb.execution;

public enum SelectExecutionStatus {
    SUCCESS,
    RESULT_TOO_LARGE,
    UNSUPPORTED_RESULT_TYPE,
    EXECUTION_FAILED,
    PENDING_SQL_MIGRATION,
    NOT_EXECUTED
}
