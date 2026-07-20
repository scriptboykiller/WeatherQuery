package org.rosetta.sqlvalidator.crossdb.compare;

import org.rosetta.sqlvalidator.crossdb.baseline.SelectBaselineSnapshot;
import org.rosetta.sqlvalidator.crossdb.eligibility.EligibilityStatus;
import org.rosetta.sqlvalidator.crossdb.eligibility.SelectInspectionResult;
import org.rosetta.sqlvalidator.crossdb.eligibility.SelectSafetyInspector;
import org.rosetta.sqlvalidator.crossdb.model.CrossDatabaseCandidate;
import org.rosetta.sqlvalidator.crossdb.sql.JdbcPlaceholderCounter;

public final class PostgresBaselineComparisonEligibilityEvaluator {

    private final SelectSafetyInspector safetyInspector;
    private final JdbcPlaceholderCounter placeholderCounter;

    public PostgresBaselineComparisonEligibilityEvaluator(
            SelectSafetyInspector safetyInspector,
            JdbcPlaceholderCounter placeholderCounter
    ) {
        this.safetyInspector = safetyInspector;
        this.placeholderCounter = placeholderCounter;
    }

    public ComparisonEligibilityDecision evaluate(
            SelectBaselineSnapshot baseline,
            CurrentSqlBaselineMatcher.Match match,
            String currentNormalizationVersion
    ) {
        if (!baseline.usable()) {
            return decision(EligibilityStatus.NOT_ELIGIBLE,
                    "H2_BASELINE_NOT_USABLE",
                    "The saved H2 baseline is incomplete or unsuccessful.");
        }

        if (!safe(baseline.normalizationVersion())
                .equalsIgnoreCase(safe(currentNormalizationVersion))) {
            return decision(EligibilityStatus.STALE_VALIDATION_RESULT,
                    "NORMALIZATION_VERSION_MISMATCH",
                    "The saved baseline normalization version differs from the current version.");
        }

        if (!match.matched()) {
            return switch (match.status()) {
                case BASELINE_KEY_MISSING -> decision(
                        EligibilityStatus.MANUAL_BASELINE_MAPPING_REQUIRED,
                        "BASELINE_KEY_MISSING", match.reason());
                case BASELINE_KEY_NOT_FOUND -> decision(
                        EligibilityStatus.BASELINE_KEY_NOT_FOUND,
                        "BASELINE_KEY_NOT_FOUND", match.reason());
                case AMBIGUOUS_BASELINE_KEY -> decision(
                        EligibilityStatus.AMBIGUOUS_BASELINE_KEY,
                        "AMBIGUOUS_BASELINE_KEY", match.reason());
                default -> decision(EligibilityStatus.NOT_ELIGIBLE,
                        "BASELINE_MATCH_FAILED", match.reason());
            };
        }

        CrossDatabaseCandidate current = match.currentCandidate();
        if (!"SELECT".equalsIgnoreCase(current.statementType())) {
            return decision(EligibilityStatus.SKIPPED_UNSUPPORTED,
                    "CURRENT_SQL_NOT_SELECT",
                    "The matched current statement is not SELECT.");
        }
        if (current.requiresManualReview() || current.dynamicSql()) {
            return decision(EligibilityStatus.MANUAL_MAPPING_REQUIRED,
                    "CURRENT_SQL_MANUAL_REVIEW",
                    "The matched current SQL is dynamic or requires manual review.");
        }
        if (!current.bindingPlanPresent() || !current.bindingPlanUsable()) {
            return decision(EligibilityStatus.MANUAL_MAPPING_REQUIRED,
                    "CURRENT_BINDING_PLAN_UNUSABLE",
                    "The current binding plan is missing or unusable.");
        }
        if (placeholderCounter.count(current.jdbcSql()) != current.bindingCount()) {
            return decision(EligibilityStatus.MANUAL_MAPPING_REQUIRED,
                    "CURRENT_BINDING_COUNT_MISMATCH",
                    "Current JDBC placeholders do not match the binding count.");
        }
        if (!"PASSED".equalsIgnoreCase(current.postgresValidationStatus())) {
            return decision(EligibilityStatus.CURRENT_POSTGRES_NOT_READY,
                    "CURRENT_POSTGRES_NOT_READY",
                    "Current PostgreSQL validation status is not PASSED.");
        }

        SelectInspectionResult inspection = safetyInspector.inspect(current.jdbcSql());
        if (!inspection.select() || inspection.multipleStatements()) {
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

        return decision(EligibilityStatus.AUTO_COMPARABLE,
                "READY_FOR_POSTGRES_BASELINE_COMPARISON",
                "The current PostgreSQL SQL is ready for comparison with the saved H2 baseline.");
    }

    private ComparisonEligibilityDecision decision(
            EligibilityStatus status, String code, String reason) {
        return new ComparisonEligibilityDecision(status, code, reason);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
