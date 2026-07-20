package org.rosetta.sqlvalidator.performance;

public final class PerformanceEligibilityEvaluator {

    public PerformanceEligibility evaluate(
            PerformanceSourceRow row,
            boolean requireResultMatch
    ) {
        if (!"SELECT".equalsIgnoreCase(row.statementType())) {
            return PerformanceEligibility.rejected("Statement is not SELECT.");
        }
        if (!"SUCCESS".equalsIgnoreCase(row.h2ExecutionStatus())) {
            return PerformanceEligibility.rejected("H2 execution is not SUCCESS.");
        }
        if (!"SUCCESS".equalsIgnoreCase(row.postgresExecutionStatus())) {
            return PerformanceEligibility.rejected("PostgreSQL execution is not SUCCESS.");
        }
        if (requireResultMatch
                && !"MATCH".equalsIgnoreCase(row.sampleComparisonStatus())) {
            return PerformanceEligibility.rejected("Cross DB result is not MATCH.");
        }
        if (row.h2JdbcSql() == null || row.h2JdbcSql().isBlank()) {
            return PerformanceEligibility.rejected("H2 JDBC SQL is missing.");
        }
        if (row.postgresJdbcSql() == null || row.postgresJdbcSql().isBlank()) {
            return PerformanceEligibility.rejected("PostgreSQL JDBC SQL is missing.");
        }
        if (row.parameterValues() == null) {
            return PerformanceEligibility.rejected("Saved parameter values are missing.");
        }
        return PerformanceEligibility.eligible();
    }
}
