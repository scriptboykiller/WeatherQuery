package org.rosetta.sqlvalidator.crossdb.eligibility;

import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;
import org.rosetta.sqlvalidator.crossdb.sql.SqlCanonicalizer;

/** Batch-2 static eligibility only. No DB connection and no SQL execution. */
public final class CrossDatabaseEligibilityEvaluator {

    private final SelectSafetyInspector safetyInspector;
    private final SqlCanonicalizer sqlCanonicalizer;

    public CrossDatabaseEligibilityEvaluator(
            SelectSafetyInspector safetyInspector,
            SqlCanonicalizer sqlCanonicalizer
    ) {
        this.safetyInspector = safetyInspector;
        this.sqlCanonicalizer = sqlCanonicalizer;
    }

    public EligibilityDecision evaluate(CrossDatabaseCandidate candidate) {
        if (candidate == null) {
            return decision(EligibilityStatus.NOT_ELIGIBLE,
                    "NULL_CANDIDATE", "Candidate is null.");
        }
        if (!"SELECT".equalsIgnoreCase(candidate.statementType())) {
            return decision(EligibilityStatus.NOT_ELIGIBLE,
                    "NOT_SELECT",
                    "Cross Database baseline validation supports SELECT only.");
        }
        if (candidate.requiresManualReview() || candidate.dynamicSql()) {
            return decision(EligibilityStatus.MANUAL_MAPPING_REQUIRED,
                    "MANUAL_SQL_REQUIRED",
                    nonBlankOrDefault(candidate.manualReviewReason(),
                            "The SQL is dynamic or still requires manual review."));
        }
        if (candidate.baselineKey() == null || candidate.baselineKey().isBlank()) {
            return decision(EligibilityStatus.MANUAL_BASELINE_MAPPING_REQUIRED,
                    "BASELINE_KEY_MISSING",
                    "A stable baseline key could not be generated.");
        }
        if (candidate.duplicateBaselineKey()) {
            return decision(EligibilityStatus.MANUAL_BASELINE_MAPPING_REQUIRED,
                    "BASELINE_KEY_DUPLICATE",
                    "More than one current SQL record has the same baseline key.");
        }
        if (!candidate.bindingPlanPresent() || !candidate.bindingPlanUsable()) {
            return decision(EligibilityStatus.MANUAL_MAPPING_REQUIRED,
                    "BINDING_PLAN_UNUSABLE",
                    "The binding plan is missing or not usable.");
        }
        if (candidate.bindingCount() != candidate.placeholderCount()) {
            return decision(EligibilityStatus.MANUAL_MAPPING_REQUIRED,
                    "BINDING_COUNT_MISMATCH",
                    "Binding count " + candidate.bindingCount()
                            + " does not match JDBC placeholder count "
                            + candidate.placeholderCount() + ".");
        }
        if (isStale(candidate)) {
            return decision(EligibilityStatus.STALE_VALIDATION_RESULT,
                    "STALE_EXECUTION_REPORT",
                    "The execution-report JDBC SQL differs from the current binding-plan JDBC SQL.");
        }

        SelectInspectionResult inspection = safetyInspector.inspect(candidate.jdbcSql());
        if (!inspection.select()) {
            return decision(EligibilityStatus.NOT_ELIGIBLE,
                    inspection.reasonCode(), inspection.reason());
        }
        if (inspection.multipleStatements()) {
            return decision(EligibilityStatus.SKIPPED_UNSUPPORTED,
                    inspection.reasonCode(), inspection.reason());
        }
        if (inspection.dynamicIdentifier()) {
            return decision(EligibilityStatus.MANUAL_MAPPING_REQUIRED,
                    inspection.reasonCode(), inspection.reason());
        }
        if (inspection.unsafe()) {
            return decision(EligibilityStatus.SKIPPED_UNSAFE,
                    inspection.reasonCode(), inspection.reason());
        }
        if (inspection.nonDeterministic()) {
            return decision(EligibilityStatus.EXECUTION_ONLY,
                    inspection.reasonCode(), inspection.reason());
        }
        if ("PASSED".equalsIgnoreCase(candidate.postgresValidationStatus())) {
            return decision(EligibilityStatus.AUTO_COMPARABLE,
                    "POSTGRES_ALREADY_PASSED",
                    "The SELECT can later execute on both H2 and PostgreSQL.");
        }
        return decision(EligibilityStatus.BASELINE_ONLY,
                "POSTGRES_MIGRATION_PENDING",
                "The SELECT is eligible for an H2 baseline; PostgreSQL execution is pending migration.");
    }

    private boolean isStale(CrossDatabaseCandidate candidate) {
        if (candidate.executionReportJdbcSql() == null
                || candidate.executionReportJdbcSql().isBlank()) {
            return false;
        }
        return !sqlCanonicalizer.canonicalize(candidate.jdbcSql())
                .equals(sqlCanonicalizer.canonicalize(
                        candidate.executionReportJdbcSql()));
    }

    private EligibilityDecision decision(
            EligibilityStatus status,
            String reasonCode,
            String reason
    ) {
        return new EligibilityDecision(status, reasonCode, reason);
    }

    private String nonBlankOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
