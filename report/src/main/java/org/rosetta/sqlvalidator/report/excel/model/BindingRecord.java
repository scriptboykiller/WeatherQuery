package org.rosetta.sqlvalidator.report.excel.model;

public record BindingRecord(
        String sqlId,
        String parameterMode,
        String adaptedJdbcSql,
        String originalParameter,
        int bindingIndex,
        String javaParameterName,
        String javaType,
        String parameterKind,
        String mockValue,
        String confidence,
        String bindingNote) {
}
