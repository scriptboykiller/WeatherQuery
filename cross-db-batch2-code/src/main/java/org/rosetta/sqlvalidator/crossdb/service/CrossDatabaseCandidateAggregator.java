package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.identity.BaselineIdentity;
import org.rosetta.sqlvalidator.crossdb.identity.BaselineKeyFactory;
import org.rosetta.sqlvalidator.crossdb.input.BindingPlanSnapshot;
import org.rosetta.sqlvalidator.crossdb.input.CurrentSqlInventoryRow;
import org.rosetta.sqlvalidator.crossdb.input.ExecutionReportSnapshot;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;
import org.rosetta.sqlvalidator.crossdb.sql.JdbcPlaceholderCounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Joins the three current CSV inputs by sqlId and generates baseline keys. */
public final class CrossDatabaseCandidateAggregator {

    private final BaselineKeyFactory baselineKeyFactory;
    private final JdbcPlaceholderCounter placeholderCounter;

    public CrossDatabaseCandidateAggregator(
            BaselineKeyFactory baselineKeyFactory,
            JdbcPlaceholderCounter placeholderCounter
    ) {
        this.baselineKeyFactory = baselineKeyFactory;
        this.placeholderCounter = placeholderCounter;
    }

    public List<CrossDatabaseCandidate> aggregate(
            List<CurrentSqlInventoryRow> inventoryRows,
            Map<String, BindingPlanSnapshot> bindingPlans,
            Map<String, ExecutionReportSnapshot> executionReports
    ) {
        Map<String, Integer> occurrenceBySqlId = calculateOccurrences(inventoryRows);
        List<CrossDatabaseCandidate> candidates = new ArrayList<>();

        for (CurrentSqlInventoryRow inventory : inventoryRows) {
            BindingPlanSnapshot binding = bindingPlans.get(inventory.sqlId());
            ExecutionReportSnapshot execution = executionReports.get(inventory.sqlId());

            int occurrenceIndex = occurrenceBySqlId.getOrDefault(inventory.sqlId(), 1);
            String baselineKey = baselineKeyFactory.create(new BaselineIdentity(
                    inventory.serviceName(),
                    inventory.className(),
                    inventory.methodName(),
                    inventory.sourceType(),
                    inventory.sqlVariableName(),
                    occurrenceIndex
            )).orElse("");

            String jdbcSql = binding == null ? "" : binding.jdbcSql();
            int bindingCount = binding == null ? 0 : binding.bindingCount();

            candidates.add(new CrossDatabaseCandidate(
                    inventory.sqlId(),
                    baselineKey,
                    inventory.serviceName(),
                    inventory.filePath(),
                    inventory.className(),
                    inventory.methodName(),
                    inventory.sourceType(),
                    inventory.sqlVariableName(),
                    occurrenceIndex,
                    inventory.lineNumber(),
                    inventory.sqlText(),
                    inventory.normalizedSqlText(),
                    jdbcSql,
                    execution == null ? "" : execution.jdbcSql(),
                    detectStatementType(jdbcSql.isBlank()
                            ? inventory.normalizedSqlText() : jdbcSql),
                    inventory.dynamicSql(),
                    inventory.requiresManualReview(),
                    inventory.manualReviewReason(),
                    inventory.confidence(),
                    binding != null,
                    binding != null && binding.usable(),
                    binding == null ? "" : binding.status(),
                    bindingCount,
                    placeholderCounter.count(jdbcSql),
                    execution == null ? "" : execution.executionStatus(),
                    false
            ));
        }

        Map<String, Long> keyCounts = candidates.stream()
                .filter(candidate -> candidate.baselineKey() != null
                        && !candidate.baselineKey().isBlank())
                .collect(Collectors.groupingBy(
                        CrossDatabaseCandidate::baselineKey,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        return candidates.stream()
                .map(candidate -> candidate.withDuplicateBaselineKey(
                        candidate.baselineKey() != null
                                && !candidate.baselineKey().isBlank()
                                && keyCounts.getOrDefault(candidate.baselineKey(), 0L) > 1
                ))
                .toList();
    }

    private Map<String, Integer> calculateOccurrences(
            List<CurrentSqlInventoryRow> inventoryRows
    ) {
        Map<OccurrenceGroup, List<CurrentSqlInventoryRow>> grouped =
                inventoryRows.stream()
                        .collect(Collectors.groupingBy(
                                row -> new OccurrenceGroup(
                                        safe(row.serviceName()),
                                        safe(row.className()),
                                        safe(row.methodName()),
                                        safe(row.sourceType()),
                                        safe(row.sqlVariableName())
                                ),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        Map<String, Integer> result = new HashMap<>();
        Comparator<CurrentSqlInventoryRow> comparator =
                Comparator.comparingInt(CurrentSqlInventoryRow::lineNumber)
                        .thenComparing(CurrentSqlInventoryRow::sqlId,
                                Comparator.nullsLast(String::compareTo));

        for (List<CurrentSqlInventoryRow> rows : grouped.values()) {
            List<CurrentSqlInventoryRow> ordered = rows.stream()
                    .sorted(comparator)
                    .toList();

            for (int index = 0; index < ordered.size(); index++) {
                result.put(ordered.get(index).sqlId(), index + 1);
            }
        }
        return result;
    }

    private String detectStatementType(String sql) {
        if (sql == null) {
            return "UNKNOWN";
        }
        String normalized = sql
                .replaceFirst("(?s)^\\s*/\\*.*?\\*/\\s*", "")
                .replaceFirst("(?m)^\\s*--.*(?:\\R|$)", "")
                .trim()
                .toUpperCase();

        return normalized.startsWith("SELECT") || normalized.startsWith("WITH")
                ? "SELECT" : "UNKNOWN";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record OccurrenceGroup(
            String serviceName,
            String className,
            String methodName,
            String sourceType,
            String sqlVariableName
    ) {
    }
}
