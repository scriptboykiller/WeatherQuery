# Copilot Batch 4 Prompt

Read the authoritative Cross DB v2 documents and this Batch 4 code pack.

Implement ONLY Batch 4:

```text
H2 business SELECT execution
+ current PASSED PostgreSQL SELECT execution
+ deterministic normalization
+ row count / column signature / SHA-256 hash
+ observed execution time
+ sample comparison
+ permanent baseline CSV rows
```

## Confirmed H2 environment

- Remote H2 JDBC connection.
- No special schema is required.
- JDBC URL may contain connection parameters.
- Exact H2 version is unknown.

Reuse the configured URL unchanged. Do not add H2-version-specific SQL and do
not log credentials.

## Precondition

Confirm Batch 1–3 compile and work. If Batch 3 is broken, stop and report it.

## Required work

1. Add/adapt the supplied execution and normalization classes.
2. Reuse Batch-3 `ParameterTuple` and `ParameterTupleCodec`.
3. Execute SELECT only.
4. Use the same tuple for H2 and PostgreSQL.
5. Current PostgreSQL PASSED: execute both independently.
6. Current non-PASSED but baseline eligible: execute H2 only and mark PG
   `PENDING_SQL_MIGRATION`.
7. Fully consume ResultSet; timing includes consumption.
8. Enforce timeout and `maxSelectRows`.
9. Normalize:
   - ordered column labels and type families;
   - numeric 1 / 1.0 / 1.00;
   - NULL;
   - Boolean;
   - Date/Time/Timestamp;
   - UUID;
   - byte[];
   - duplicate rows preserved;
   - sort rows only when no top-level ORDER BY.
10. Mark unsupported LOB/proprietary values rather than guessing.
11. Calculate SHA-256.
12. Compare in this order:
    - execution status;
    - result too large;
    - column signature;
    - row count;
    - result hash.
13. Write real `SelectBaselineRecord` rows using the exact Batch-1 schema.
14. Replace only the old header-only skeleton; never silently overwrite a real
    baseline when `failIfBaselineExists=true`.
15. Update only `CAPTURE_BASELINE` runner behavior.
16. Keep `COMPARE_POSTGRES_WITH_BASELINE` for Batch 5.
17. One SQL/sample failure must not stop later records.

## Minimum tests

- H2 business SELECT execution;
- typed parameter binding;
- repeated JDBC indexes;
- collection size one;
- numeric/date/time/Boolean normalization;
- row order independence without ORDER BY;
- ORDER BY sensitivity;
- duplicate rows preserved;
- column/row/hash mismatches;
- RESULT_TOO_LARGE;
- PASSED SQL executes both DBs;
- non-PASSED eligible SQL executes only H2;
- baseline contains data rows;
- baseline overwrite protection;
- existing tests remain green.

## Hard restrictions

Do not implement:

- baseline CSV reader;
- current-to-baseline matching;
- `COMPARE_POSTGRES_WITH_BASELINE`;
- Excel changes;
- performance-report phase;
- colleague performance JAR integration;
- UPDATE / DELETE / INSERT / rollback.

Do not continue to Batch 5.

## Completion report

After compile and tests, run CAPTURE_BASELINE against the configured remote H2
and current PostgreSQL test database. Report:

- files created/modified;
- baseline run ID and output path;
- SQL/sample rows written;
- H2 success/failure;
- PG success/failure/pending;
- MATCH/MISMATCH;
- RESULT_TOO_LARGE;
- unsupported result types;
- compile/test results;
- assumptions and unresolved types.
