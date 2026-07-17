package org.rosetta.sqlvalidator.crossdb.eligibility;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;
import org.rosetta.sqlvalidator.crossdb.sql.SqlCanonicalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossDatabaseEligibilityEvaluatorTest {

    private final CrossDatabaseEligibilityEvaluator evaluator =
            new CrossDatabaseEligibilityEvaluator(
                    new SelectSafetyInspector(),
                    new SqlCanonicalizer()
            );

    @Test
    void passedSelectIsAutoComparable() {
        assertEquals(
                EligibilityStatus.AUTO_COMPARABLE,
                evaluator.evaluate(candidate("PASSED", false, false)).status()
        );
    }

    @Test
    void nonPassedUsableSelectIsBaselineOnly() {
        assertEquals(
                EligibilityStatus.BASELINE_ONLY,
                evaluator.evaluate(candidate(
                        "FAILED_SQL_COMPATIBILITY", false, false
                )).status()
        );
    }

    @Test
    void duplicateBaselineKeyRequiresManualMapping() {
        assertEquals(
                EligibilityStatus.MANUAL_BASELINE_MAPPING_REQUIRED,
                evaluator.evaluate(candidate("PASSED", true, false)).status()
        );
    }

    @Test
    void staleExecutionReportIsRejected() {
        CrossDatabaseCandidate base = candidate("PASSED", false, false);
        CrossDatabaseCandidate stale = new CrossDatabaseCandidate(
                base.sqlId(), base.baselineKey(), base.serviceName(),
                base.filePath(), base.className(), base.methodName(),
                base.sourceType(), base.sqlVariableName(),
                base.occurrenceIndex(), base.lineNumber(), base.originalSql(),
                base.normalizedSql(), base.jdbcSql(),
                "SELECT * FROM different_table WHERE id = ?",
                base.statementType(), base.dynamicSql(),
                base.requiresManualReview(), base.manualReviewReason(),
                base.confidence(), base.bindingPlanPresent(),
                base.bindingPlanUsable(), base.bindingPlanStatus(),
                base.bindingCount(), base.placeholderCount(),
                base.postgresValidationStatus(), base.duplicateBaselineKey()
        );

        assertEquals(
                EligibilityStatus.STALE_VALIDATION_RESULT,
                evaluator.evaluate(stale).status()
        );
    }

    private CrossDatabaseCandidate candidate(
            String postgresStatus,
            boolean duplicateKey,
            boolean manualReview
    ) {
        return new CrossDatabaseCandidate(
                "sql-1",
                "service|repository|find|spring_data_query|query|1",
                "service",
                "Repository.java",
                "Repository",
                "find",
                "SPRING_DATA_QUERY",
                "query",
                1,
                10,
                "SELECT * FROM customer WHERE id = :id",
                "SELECT * FROM customer WHERE id = :id",
                "SELECT * FROM customer WHERE id = ?",
                "SELECT * FROM customer WHERE id = ?",
                "SELECT",
                false,
                manualReview,
                manualReview ? "Manual SQL" : "",
                "HIGH",
                true,
                true,
                "READY",
                1,
                1,
                postgresStatus,
                duplicateKey
        );
    }
}
