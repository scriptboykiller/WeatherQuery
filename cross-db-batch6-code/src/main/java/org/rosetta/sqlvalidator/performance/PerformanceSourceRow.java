package org.rosetta.sqlvalidator.performance;

public record PerformanceSourceRow(
        String baselineRunId,
        String baselineKey,
        String baselineSqlId,
        String currentSqlId,
        String statementType,
        int sampleIndex,
        String parameterValues,
        String h2JdbcSql,
        String postgresJdbcSql,
        String h2ExecutionStatus,
        String postgresExecutionStatus,
        String sampleComparisonStatus,
        Long h2ObservedTimeMs,
        Long postgresObservedTimeMs
) {
    public String effectiveSqlId() {
        if (currentSqlId != null && !currentSqlId.isBlank()) {
            return currentSqlId;
        }
        return baselineSqlId == null ? "" : baselineSqlId;
    }

    public String sqlGroupKey() {
        if (baselineKey != null && !baselineKey.isBlank()) {
            return baselineKey;
        }
        return effectiveSqlId();
    }

    public long rankingTimeMs() {
        long h2 = h2ObservedTimeMs == null ? Long.MIN_VALUE : h2ObservedTimeMs;
        long pg = postgresObservedTimeMs == null ? Long.MIN_VALUE : postgresObservedTimeMs;
        return Math.max(h2, pg);
    }

    public boolean hasRankingTime() {
        return h2ObservedTimeMs != null || postgresObservedTimeMs != null;
    }
}
