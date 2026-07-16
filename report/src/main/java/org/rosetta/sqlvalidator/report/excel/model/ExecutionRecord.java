package org.rosetta.sqlvalidator.report.excel.model;

public record ExecutionRecord(
        String sqlId,
        String jdbcSql,
        String validationMode,
        String executionStatus,
        String sqlState,
        String postgresErrorCode,
        String errorCategory,
        String identifierStrategy,
        boolean identifierAdaptationApplied,
        String identifierAdaptationMode,
        String adaptedJdbcSql,
        String identifierAdaptationNote,
        String message,
        String recommendation) {
}
