# Copilot Batch 5 Prompt

Read the authoritative Cross DB v2 documents and the supplied Batch 5 code
pack.

Implement ONLY:

```text
COMPARE_POSTGRES_WITH_BASELINE
```

## Preconditions

Confirm:

- Batch 4 compiles and tests pass;
- `sql-select-baseline.csv` contains real data rows;
- saved `parameterValues` can be decoded by the Batch-3 codec;
- CAPTURE_BASELINE remains unchanged.

If the baseline is header-only or invalid, stop and report it.

## Required work

1. Add/adapt:

- SelectBaselineSnapshot
- SelectBaselineCsvReader
- BaselineMatchStatus
- CurrentSqlBaselineMatcher
- ComparisonEligibilityDecision
- PostgresBaselineComparisonEligibilityEvaluator
- SavedH2BaselineResultFactory
- BaselineComparisonOutcome
- PostgresBaselineComparisonService
- SelectBaselineComparisonRecordAssembler

2. Reuse existing:

- Batch-2 current CSV readers and candidate aggregator;
- stable baselineKey;
- Batch-3 ParameterTupleCodec;
- Batch-4 SelectExecutionEngine;
- Batch-4 SelectResultComparator;
- Batch-1 SelectBaselineComparisonRecord and CSV writer;
- existing PostgreSQL connection configuration.

Do not create duplicate models or readers.

3. Read every saved baseline sample.

4. Match only by baselineKey.

- no match → BASELINE_KEY_NOT_FOUND;
- multiple matches → AMBIGUOUS_BASELINE_KEY;
- missing key → MANUAL_BASELINE_MAPPING_REQUIRED;
- do not fall back to sqlId, SQL text, line number or hash.

5. Require:

- saved H2 status SUCCESS;
- saved row count/hash/column signature present;
- normalizationVersion unchanged;
- current statement SELECT;
- current binding plan usable;
- current PostgreSQL validation PASSED;
- current SELECT safe.

6. Decode exact saved `parameterValues` with the Batch-3 codec.

Do not resample and do not connect to H2.

7. Execute only current `BindingPlan.jdbcSql` on PostgreSQL.

8. Compare current PostgreSQL with saved H2:

```text
row count
column signature
result hash
```

9. Write one comparison row for every baseline sample, including skipped and
unresolved rows.

10. Output:

```text
./output/sql-select-comparison.csv
```

It may be overwritten on each comparison run.

11. Update only the `COMPARE_POSTGRES_WITH_BASELINE` runner branch.

12. Tests at minimum:

- baseline CSV reader;
- old sqlId changed but baselineKey matches;
- key not found;
- ambiguous key;
- current PostgreSQL PASSED;
- current PostgreSQL non-PASSED;
- normalization-version mismatch;
- unusable H2 baseline;
- saved parameter decode;
- matching result;
- row-count mismatch;
- column mismatch;
- hash mismatch;
- PostgreSQL execution failure;
- one failure does not stop later rows;
- comparison CSV contains all baseline samples;
- no H2 connection;
- CAPTURE regression tests remain green.

## Hard restrictions

Do not implement:

- H2 connection;
- new parameter sampling;
- baseline overwrite;
- Excel changes;
- performance-report phase;
- HTML links;
- DML.

Do not continue to Batch 6.

## Completion report

After compile and tests, run comparison mode and report:

- baseline rows read;
- unique matches;
- key-not-found count;
- ambiguous-key count;
- current-PostgreSQL-not-ready count;
- parameter decode failures;
- PostgreSQL execution success/failure;
- MATCH/MISMATCH counts;
- output path;
- compilation and test results;
- unresolved mapping issues.

Do not claim completion if compilation or tests fail.
