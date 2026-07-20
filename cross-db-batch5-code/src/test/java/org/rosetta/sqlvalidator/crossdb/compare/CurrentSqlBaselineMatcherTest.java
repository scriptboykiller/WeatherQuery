package org.rosetta.sqlvalidator.crossdb.compare;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.baseline.SelectBaselineSnapshot;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrentSqlBaselineMatcherTest {

    @Test
    void matchesEvenWhenSqlIdChanged() {
        CurrentSqlBaselineMatcher matcher = new CurrentSqlBaselineMatcher(
                List.of(candidate("new-sql-id", "stable-key")));

        assertEquals(BaselineMatchStatus.MATCHED,
                matcher.match(baseline("old-sql-id", "stable-key")).status());
    }

    @Test
    void reportsMissingAndAmbiguousKeys() {
        assertEquals(BaselineMatchStatus.BASELINE_KEY_NOT_FOUND,
                new CurrentSqlBaselineMatcher(List.of())
                        .match(baseline("old", "key")).status());

        assertEquals(BaselineMatchStatus.AMBIGUOUS_BASELINE_KEY,
                new CurrentSqlBaselineMatcher(List.of(
                        candidate("a", "key"), candidate("b", "key")))
                        .match(baseline("old", "key")).status());
    }

    private SelectBaselineSnapshot baseline(String sqlId, String key) {
        return new SelectBaselineSnapshot(
                "run", "time", "v1", key, sqlId,
                "service", "Repo", "find", "SPRING_DATA_QUERY", "query",
                1, "SELECT", "h2", "", 1, 3,
                "id", "java.lang.Long", "v1:1:",
                "SELECT 1", "SUCCESS", 1, "hash", "ID:NUMBER");
    }

    private CrossDatabaseCandidate candidate(String sqlId, String key) {
        return new CrossDatabaseCandidate(
                sqlId, key, "service", "Repo.java", "Repo", "find",
                "SPRING_DATA_QUERY", "query", 1, 10,
                "SELECT 1", "SELECT 1", "SELECT 1", "SELECT 1",
                "SELECT", false, false, "", "HIGH",
                true, true, "READY", 0, 0, "PASSED", false);
    }
}
