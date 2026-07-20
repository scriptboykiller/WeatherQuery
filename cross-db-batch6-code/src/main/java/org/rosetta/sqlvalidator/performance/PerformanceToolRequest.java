package org.rosetta.sqlvalidator.performance;

import java.util.List;

public record PerformanceToolRequest(
        String requestVersion,
        String caseId,
        String sqlId,
        String baselineKey,
        int sampleIndex,
        String h2JdbcSql,
        String postgresJdbcSql,
        List<PerformanceParameter> parameters,
        boolean resultMatchedBeforePerformanceTest,
        String outputFileName
) {
    public PerformanceToolRequest {
        parameters = List.copyOf(parameters);
    }
}
