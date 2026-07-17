package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.eligibility.EligibilityStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record CandidateAnalysisSummary(
        int totalCandidates,
        Map<EligibilityStatus, Long> statusCounts
) {
    public CandidateAnalysisSummary {
        EnumMap<EligibilityStatus, Long> copy =
                new EnumMap<>(EligibilityStatus.class);
        if (statusCounts != null) {
            copy.putAll(statusCounts);
        }
        statusCounts = Collections.unmodifiableMap(copy);
    }

    public long count(EligibilityStatus status) {
        return statusCounts.getOrDefault(status, 0L);
    }
}
