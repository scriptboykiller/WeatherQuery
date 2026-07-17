package org.rosetta.sqlvalidator.crossdb.service;

import org.rosetta.sqlvalidator.crossdb.eligibility.EligibilityDecision;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;

public record CandidateAnalysis(
        CrossDatabaseCandidate candidate,
        EligibilityDecision eligibility
) {
}
