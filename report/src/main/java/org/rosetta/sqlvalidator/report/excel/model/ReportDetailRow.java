package org.rosetta.sqlvalidator.report.excel.model;

public record ReportDetailRow(
        InventoryRecord inventory,
        ExecutionRecord execution,
        String parameterSummary,
        IssueClassification classification,
        int issueTypeCount) {

    public String sqlId() {
        return execution != null && execution.sqlId() != null && !execution.sqlId().isBlank()
                ? execution.sqlId()
                : inventory == null ? "" : inventory.sqlId();
    }

    public String originalSql() {
        if (inventory == null) {
            return "";
        }
        if (inventory.normalizedSqlText() != null && !inventory.normalizedSqlText().isBlank()) {
            return inventory.normalizedSqlText();
        }
        return inventory.sqlText() == null ? "" : inventory.sqlText();
    }

    public String executedSql() {
        if (execution == null) {
            return "";
        }
        if (execution.adaptedJdbcSql() != null && !execution.adaptedJdbcSql().isBlank()) {
            return execution.adaptedJdbcSql();
        }
        return execution.jdbcSql() == null ? "" : execution.jdbcSql();
    }
}
