package org.rosetta.sqlvalidator.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PerformanceCaseSelector {

    private final PerformanceEligibilityEvaluator eligibilityEvaluator;

    public PerformanceCaseSelector(PerformanceEligibilityEvaluator eligibilityEvaluator) {
        this.eligibilityEvaluator = eligibilityEvaluator;
    }

    public List<SelectedPerformanceCase> select(
            List<PerformanceSourceRow> rows,
            int topSlowest,
            int defaultSampleIndex,
            boolean requireResultMatch,
            List<String> includeSqlIds
    ) {
        Map<String, List<PerformanceSourceRow>> groups = new LinkedHashMap<>();
        for (PerformanceSourceRow row : rows) {
            if (eligibilityEvaluator.evaluate(row, requireResultMatch).eligible()) {
                groups.computeIfAbsent(row.sqlGroupKey(), ignored -> new ArrayList<>()).add(row);
            }
        }

        List<PerformanceSourceRow> representatives = groups.values().stream()
                .map(group -> representative(group, defaultSampleIndex))
                .toList();

        Set<String> topKeys = representatives.stream()
                .filter(PerformanceSourceRow::hasRankingTime)
                .sorted(Comparator
                        .comparingLong(PerformanceSourceRow::rankingTimeMs)
                        .reversed()
                        .thenComparing(PerformanceSourceRow::sqlGroupKey))
                .limit(Math.max(0, topSlowest))
                .map(PerformanceSourceRow::sqlGroupKey)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        Set<String> includes = new LinkedHashSet<>();
        if (includeSqlIds != null) {
            includeSqlIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .forEach(includes::add);
        }

        List<SelectedPerformanceCase> selected = new ArrayList<>();
        for (PerformanceSourceRow row : representatives) {
            boolean top = topKeys.contains(row.sqlGroupKey());
            boolean manual = includes.contains(normalize(row.currentSqlId()))
                    || includes.contains(normalize(row.baselineSqlId()))
                    || includes.contains(normalize(row.effectiveSqlId()));

            if (top || manual) {
                SelectionReason reason = top && manual
                        ? SelectionReason.TOP_SLOWEST_AND_MANUAL_INCLUDE
                        : top ? SelectionReason.TOP_SLOWEST
                        : SelectionReason.MANUAL_INCLUDE;
                selected.add(new SelectedPerformanceCase(row, reason));
            }
        }

        return List.copyOf(selected);
    }

    public PerformanceSourceRow representative(
            List<PerformanceSourceRow> eligibleRows,
            int defaultSampleIndex
    ) {
        return eligibleRows.stream()
                .filter(row -> row.sampleIndex() == defaultSampleIndex)
                .findFirst()
                .orElseGet(() -> eligibleRows.stream()
                        .min(Comparator.comparingInt(PerformanceSourceRow::sampleIndex))
                        .orElseThrow());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
