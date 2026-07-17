# Copilot Batch 2 Prompt

Read these authoritative documents:

- docs/cross-db/CROSS_DB_VALIDATION_MVP_REQUIREMENTS_AND_DESIGN.md
- docs/cross-db/CROSS_DB_VALIDATION_MVP_IMPLEMENTATION_HANDOFF.md

Also read the supplied Batch 2 code pack. Treat it as the reference
implementation, but adapt package names, constructors, existing models/readers
and configuration access to the real project.

Implement ONLY Batch 2:

```text
Current CSV input aggregation
+ stable baselineKey
+ duplicate/missing identity detection
+ static SELECT-only eligibility
+ tests
```

## Precondition

First inspect Batch 1 and confirm:

- the project compiles;
- CROSS_DB_VALIDATION exists;
- CrossDatabaseMode exists;
- SELECT-only configuration exists;
- SelectBaselineRecord and SelectBaselineComparisonRecord exist;
- both header-only CSV writers exist;
- no Cross DB DML fields remain.

If Batch 1 is incomplete or broken, stop and report it rather than hiding it.

## Required work

1. Add or adapt these Batch 2 classes:

- BaselineIdentity
- BaselineKeyFactory
- CurrentSqlInventoryRow
- BindingPlanSnapshot
- ExecutionReportSnapshot
- CurrentSqlInputReader
- JdbcPlaceholderCounter
- SqlCanonicalizer
- CrossDatabaseCandidate
- CrossDatabaseCandidateAggregator
- EligibilityDecision
- SelectInspectionResult
- SelectSafetyInspector
- CrossDatabaseEligibilityEvaluator
- CandidateAnalysis
- CandidateAnalysisSummary
- CrossDatabaseCandidateAnalysisService

2. Reuse existing project models/readers where they already provide the same
information. Do not create duplicates without inspecting current code.

3. Confirm actual binding-plan and execution-report CSV headers. Narrow or
replace the supplied aliases so they exactly match current output while
remaining header-based.

4. Aggregate by `sqlId`. Produce exactly one candidate per inventory SQL even
when the binding-plan CSV contains multiple rows.

5. Generate `baselineKey` from:

```text
serviceName
className
methodName
sourceType
sqlVariableName
occurrenceIndex
```

Do not use SQL text, line number, SQL hash or sqlId as the sole identity.

6. `occurrenceIndex` rules:

- calculate within the same stable source group;
- use current ordering metadata only to distinguish repeated occurrences;
- do not include line number in the final baselineKey;
- detect duplicate final keys.

7. Implement static capture eligibility:

- non-SELECT → NOT_ELIGIBLE
- dynamic/manual SQL → MANUAL_MAPPING_REQUIRED
- missing/duplicate key → MANUAL_BASELINE_MAPPING_REQUIRED
- missing/unusable binding plan → MANUAL_MAPPING_REQUIRED
- binding count mismatch → MANUAL_MAPPING_REQUIRED
- stale execution-report SQL → STALE_VALIDATION_RESULT
- multiple statements → SKIPPED_UNSUPPORTED
- dynamic identifier → MANUAL_MAPPING_REQUIRED
- unsafe SELECT → SKIPPED_UNSAFE
- safe non-deterministic SELECT → EXECUTION_ONLY
- PostgreSQL PASSED → AUTO_COMPARABLE
- otherwise usable H2 SELECT → BASELINE_ONLY

Do not require PostgreSQL PASSED for H2 baseline eligibility.

8. Update the Batch-1 runner only enough to:

- read the three current CSV inputs;
- aggregate candidates;
- evaluate static eligibility;
- log status counts;
- keep producing the existing header-only baseline/comparison CSV.

Do not write partial baseline rows in Batch 2.

9. Add/adapt the supplied tests. Verify at minimum:

- stable key;
- missing key;
- duplicate key;
- CSV input reading;
- one candidate per sqlId;
- placeholder counting;
- PASSED SELECT → AUTO_COMPARABLE;
- non-PASSED usable SELECT → BASELINE_ONLY;
- non-SELECT excluded;
- unsafe SELECT skipped;
- non-deterministic SELECT → EXECUTION_ONLY;
- stale execution report;
- binding-count mismatch;
- existing Batch 1 tests remain green.

## Hard restrictions

Do not implement:

- baseline CSV reading;
- current-to-baseline matching;
- H2 connections;
- PostgreSQL connections;
- parameter sampling;
- parameter serialization;
- SELECT execution;
- ResultSet reading;
- normalization;
- hashes;
- comparison result rows;
- Excel changes;
- UPDATE;
- DELETE;
- INSERT;
- transactions;
- rollback;
- affected rows.

Do not continue to Batch 3.

## Completion actions

After implementation:

1. Compile the project.
2. Run all new tests.
3. Run the existing test suite if practical.
4. Run `cross-db-validation` in CAPTURE_BASELINE mode using current CSVs.
5. Confirm it logs static eligibility counts and creates only a header CSV.
6. Report:
   - every file created;
   - every file modified;
   - actual CSV header mappings used;
   - candidate totals by EligibilityStatus;
   - duplicate/missing baseline-key counts;
   - compilation result;
   - test result;
   - assumptions and unresolved issues.

Do not claim completion if compilation or tests fail.
