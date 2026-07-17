package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.eligibility.CrossDatabaseEligibilityEvaluator;
import org.rosetta.sqlvalidator.crossdb.eligibility.EligibilityStatus;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CrossDatabaseCandidateAnalysisService {

    private final CrossDatabaseEligibilityEvaluator eligibilityEvaluator;

    public CrossDatabaseCandidateAnalysisService(
            CrossDatabaseEligibilityEvaluator eligibilityEvaluator
    ) {
        this.eligibilityEvaluator = eligibilityEvaluator;
    }

    public List<CandidateAnalysis> analyze(
            List<CrossDatabaseCandidate> candidates
    ) {
        return candidates.stream()
                .map(candidate -> new CandidateAnalysis(
                        candidate,
                        eligibilityEvaluator.evaluate(candidate)
                ))
                .toList();
    }

    public CandidateAnalysisSummary summarize(List<CandidateAnalysis> analyses) {
        Map<EligibilityStatus, Long> counts =
                new EnumMap<>(EligibilityStatus.class);
        for (CandidateAnalysis analysis : analyses) {
            counts.merge(analysis.eligibility().status(), 1L, Long::sum);
        }
        return new CandidateAnalysisSummary(analyses.size(), counts);
    }
}
