package org.rosetta.sqlvalidator.report.excel.model;

public enum IssueType {
    PASSED("Passed"),
    SEQUENCE_SYNTAX("Oracle/H2 sequence syntax"),
    DUAL_TABLE("Oracle DUAL table"),
    NVL_FUNCTION("NVL function"),
    SYSDATE_FUNCTION("SYSDATE function"),
    ROWNUM_PSEUDOCOLUMN("ROWNUM pseudocolumn"),
    TO_CHAR_FUNCTION("TO_CHAR compatibility"),
    TO_TIMESTAMP_FUNCTION("TO_TIMESTAMP compatibility"),
    DATEADD_FUNCTION("DATEADD function"),
    DATETIME_FUNCTION_SYNTAX("Date/time function syntax"),
    UNSUPPORTED_FUNCTION("Unsupported function"),
    QUOTED_IDENTIFIER_CASE_MISMATCH("Quoted identifier / case mismatch"),
    UPDATE_TARGET_ALIAS("UPDATE target alias in SET clause"),
    PARAMETER_TYPE_MISMATCH("Java/SQL parameter type mismatch"),
    PARAMETER_BINDING_ERROR("Parameter binding error"),
    COLUMN_NOT_FOUND("Column not found"),
    RELATION_NOT_FOUND("Table/view/relation not found"),
    IDENTIFIER_CASE_OR_QUOTING("Identifier case or quoting"),
    MERGE_SYNTAX("MERGE syntax compatibility"),
    GENERIC_SQL_SYNTAX("SQL syntax error"),
    SKIPPED_OR_NOT_EXECUTED("Skipped / not executed"),
    UNKNOWN("Unknown / review required");

    private final String displayName;

    IssueType(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
