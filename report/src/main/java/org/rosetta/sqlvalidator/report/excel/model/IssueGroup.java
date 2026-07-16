package org.rosetta.sqlvalidator.report.excel.model;

/**
 * Small, stable set of management-facing groups.
 * Colors are applied by group, not by every issue type.
 */
public enum IssueGroup {
    PASSED("Passed", 0),
    SQL_DIALECT_COMPATIBILITY("SQL Dialect Compatibility", 1),
    SOURCE_SQL_DEFECT("Source SQL Defect", 2),
    PARAMETER_TYPE_OR_BINDING("Parameter Type / Binding", 3),
    DATABASE_ENVIRONMENT("Database Environment", 4),
    MANUAL_REVIEW("Manual Review", 5);

    private final String displayName;
    private final int sortOrder;

    IssueGroup(final String displayName, final int sortOrder) {
        this.displayName = displayName;
        this.sortOrder = sortOrder;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
