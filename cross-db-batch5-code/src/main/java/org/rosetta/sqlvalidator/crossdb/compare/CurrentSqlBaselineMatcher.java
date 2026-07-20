package org.rosetta.sqlvalidator.crossdb.compare;

import org.rosetta.sqlvalidator.crossdb.baseline.SelectBaselineSnapshot;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CurrentSqlBaselineMatcher {

    private final Map<String, List<CrossDatabaseCandidate>> byKey;

    public CurrentSqlBaselineMatcher(List<CrossDatabaseCandidate> currentCandidates) {
        Map<String, List<CrossDatabaseCandidate>> grouped = new LinkedHashMap<>();
        for (CrossDatabaseCandidate candidate : currentCandidates) {
            if (candidate.baselineKey() == null || candidate.baselineKey().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(candidate.baselineKey(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        this.byKey = Map.copyOf(grouped);
    }

    public Match match(SelectBaselineSnapshot baseline) {
        if (baseline.baselineKey() == null || baseline.baselineKey().isBlank()) {
            return new Match(BaselineMatchStatus.BASELINE_KEY_MISSING, null,
                    "The baseline row has no baselineKey.");
        }

        List<CrossDatabaseCandidate> matches = byKey.getOrDefault(
                baseline.baselineKey(), List.of());

        if (matches.isEmpty()) {
            return new Match(BaselineMatchStatus.BASELINE_KEY_NOT_FOUND, null,
                    "No current SQL matches baselineKey " + baseline.baselineKey());
        }
        if (matches.size() > 1) {
            return new Match(BaselineMatchStatus.AMBIGUOUS_BASELINE_KEY, null,
                    "Multiple current SQL records match baselineKey " + baseline.baselineKey());
        }
        return new Match(BaselineMatchStatus.MATCHED, matches.get(0), "");
    }

    public record Match(
            BaselineMatchStatus status,
            CrossDatabaseCandidate currentCandidate,
            String reason
    ) {
        public boolean matched() {
            return status == BaselineMatchStatus.MATCHED && currentCandidate != null;
        }
    }
}
