# Cross-Database SELECT Baseline Validation MVP — Requirements and Design

**Document status:** Scope Freeze v2.0  
**Project:** SQL PostgreSQL Validator  
**Runtime:** JDK 17 / Spring Boot CLI  
**Source database:** H2  
**Target database:** PostgreSQL  
**Delivery target:** Handover-ready MVP for the follow-up developer  
**Scope:** SELECT only  
**Explicitly excluded:** INSERT, UPDATE, DELETE, DML transactions, rollback and affected-row comparison

---

## 1. Background

The current validator already supports:

- Native SQL inventory scanning across the microservices.
- SQL text sanity checks.
- Named, positional and JPA indexed parameter conversion.
- JDBC binding-plan generation.
- Mock-value generation.
- PostgreSQL EXPLAIN validation.
- PostgreSQL execution/error classification.
- CSV outputs.
- A consolidated Excel report with `Summary` and `Detail` sheets.

The H2 database is the source database. The PostgreSQL database was copied from that H2 database, so they are expected to contain the same data at the time of baseline capture.

The SQL migration will be handed over to another developer. Some SQL statements already pass PostgreSQL validation, while others still require migration changes. After those SQL statements are changed in the Java projects, the original H2 SQL may no longer be present in the source code.

The new feature must therefore support both:

1. Immediate H2/PostgreSQL comparison for SQL that already passes.
2. Permanent capture of H2 real-data baselines for SQL that does not yet pass, so the future developer can compare the migrated PostgreSQL SQL without needing the original H2 SQL.

---

## 2. Final Goal

Add one independent phase:

```text
cross-db-validation
```

The phase has two modes:

```text
CAPTURE_BASELINE
COMPARE_POSTGRES_WITH_BASELINE
```

### 2.1 CAPTURE_BASELINE

For eligible SELECT SQL that currently passes PostgreSQL validation:

```text
sample real parameter tuples from H2
→ execute the same current BindingPlan.jdbcSql on H2
→ execute the same current BindingPlan.jdbcSql on PostgreSQL
→ compare results
→ save H2 baseline, PostgreSQL result and comparison
```

For eligible SELECT SQL that does not currently pass PostgreSQL validation but still has a usable H2 SQL and usable binding plan:

```text
sample real parameter tuples from H2
→ execute the H2 JDBC SQL only
→ save real parameter tuples and H2 result as an immutable baseline
→ leave PostgreSQL result fields empty
→ mark PostgreSQL as PENDING_SQL_MIGRATION
```

### 2.2 COMPARE_POSTGRES_WITH_BASELINE

After the handover developer changes the Java SQL and reruns the existing scanner/validator:

```text
read the saved H2 baseline
→ match each old SQL to the current migrated SQL using a stable baselineKey
→ reuse the exact saved parameter tuples
→ execute only the current PostgreSQL JDBC SQL
→ compare the current PostgreSQL result with the saved H2 baseline
→ write a comparison CSV
```

H2 does not need to be connected in comparison mode.

---

## 3. Existing Features Must Remain Unchanged

Existing phases remain available and must not be redesigned:

```text
inventory
sanity
validation-explain
validation-select-smoke
validation-dml-safety
real-execution
excel-report
```

The SELECT-only restriction applies only to the new Cross DB feature.

Existing DML-related phases must not be deleted or changed.

The new normal flow is:

### Baseline capture now

```text
inventory
    ↓
sanity
    ↓
validation-explain
    ↓
cross-db-validation (CAPTURE_BASELINE)
    ↓
excel-report
```

### PostgreSQL comparison after migration changes

```text
update Java SQL
    ↓
inventory
    ↓
sanity
    ↓
validation-explain
    ↓
cross-db-validation (COMPARE_POSTGRES_WITH_BASELINE)
    ↓
excel-report
```

---

## 4. SELECT-Only Scope

The new Cross DB phase must process SELECT statements only.

It must not implement or contain Cross DB logic for:

- INSERT.
- UPDATE.
- DELETE.
- MERGE.
- DDL.
- TRUNCATE.
- CALL.
- DML transaction handling.
- Rollback status.
- Affected-row comparison.
- DML whitelists.
- DML sample counts.

Any previous Cross DB design referring to DML is obsolete and must not be used.

---

## 5. SQL Text Rules

### 5.1 Current passing SQL

The SQL scanned from the Java code is the H2 SQL currently used by the application.

For a SQL statement that currently passes PostgreSQL validation, no business SQL rewrite has occurred yet. Both databases execute:

```text
BindingPlan.jdbcSql
```

with the same real parameter tuple.

Conversion such as:

```text
:name → ?
?1 → ?
collection expansion → ?, ?, ?
```

is JDBC placeholder normalization, not business SQL modification.

### 5.2 Current non-passing SQL

For baseline capture, H2 executes the current original JDBC SQL:

```text
BindingPlan.jdbcSql
```

PostgreSQL is not executed.

The saved baseline must retain:

- Original H2 JDBC SQL.
- Real parameter names.
- Real parameter types.
- Real parameter values.
- H2 result metadata.
- H2 result hash.
- H2 column signature.

### 5.3 Future migrated SQL

In comparison mode, the current Java SQL may be different.

The current PostgreSQL execution uses:

```text
current BindingPlan.jdbcSql
```

The saved H2 JDBC SQL is retained for traceability but is not executed again.

---

## 6. Inputs and Outputs

### 6.1 CAPTURE_BASELINE inputs

```text
./output/sql-inventory.csv
./output/sql-binding-plan.csv
./output/sql-execution-report.csv
H2 JDBC connection
existing PostgreSQL JDBC configuration
```

### 6.2 CAPTURE_BASELINE output

```text
./output/sql-select-baseline.csv
```

The baseline is a handover artifact and must not be silently overwritten.

Recommended default behavior:

```text
fail-if-baseline-exists: true
```

A deliberate override may be added later, but silent replacement is not allowed.

### 6.3 COMPARE_POSTGRES_WITH_BASELINE inputs

```text
saved sql-select-baseline.csv
current sql-inventory.csv
current sql-binding-plan.csv
current sql-execution-report.csv
existing PostgreSQL JDBC configuration
```

H2 connection is not required.

### 6.4 COMPARE_POSTGRES_WITH_BASELINE output

```text
./output/sql-select-comparison.csv
```

### 6.5 General output rules

- One row per SQL sample.
- A skipped SQL must still produce at least one row.
- One SQL failure must not terminate the batch.
- Parameter values do not require masking.
- Database passwords and connection secrets must never be written.

---

## 7. Stable SQL Identity

The current `sqlId` may change after the SQL text or line number changes.

The baseline therefore requires a stable key:

```text
baselineKey
```

Recommended components:

```text
serviceName
className
methodName
sourceType
sqlVariableName
occurrenceIndexWithinMethod
```

Recommended canonical format:

```text
serviceName|className|methodName|sourceType|sqlVariableName|occurrenceIndex
```

The key must not rely only on:

- SQL text.
- SQL hash.
- Source line number.
- Scan order.
- Current `sqlId`.

If stable metadata is missing or duplicated, the record must be marked:

```text
MANUAL_BASELINE_MAPPING_REQUIRED
```

The baseline must retain both:

```text
baselineKey
original sqlId
```

Comparison output must retain:

```text
baselineSqlId
currentSqlId
```

---

## 8. Capture Eligibility

A SQL statement may enter baseline capture only when:

```text
statementType = SELECT
requiresManualReview = false
Binding Plan exists
Binding Plan status is usable
JDBC placeholder count matches binding count
SQL is not unresolved dynamic SQL
SQL does not use a dynamic table name
SQL does not use a dynamic column name
SQL contains only one executable statement
baselineKey can be generated uniquely
```

### 8.1 Current PASSED SELECT

If PostgreSQL validation status is `PASSED`:

```text
eligibilityStatus = AUTO_COMPARABLE
```

The tool may execute both H2 and PostgreSQL.

### 8.2 Current non-PASSED SELECT

A non-passing SELECT may still be captured as H2 baseline when:

- Its H2 JDBC SQL is available.
- Its binding plan is usable.
- Real parameter mapping is possible.
- It is safe to execute as a read-only H2 SELECT.

Then:

```text
eligibilityStatus = BASELINE_ONLY
postgresExecutionStatus = PENDING_SQL_MIGRATION
overallComparisonStatus = BASELINE_CAPTURED
```

### 8.3 Excluded non-PASSED cases

Do not capture when:

- Parameter binding is unresolved.
- Manual SQL reconstruction is still required.
- SQL is unsupported.
- SQL is unsafe.
- H2 SQL cannot be executed.
- Parameter mapping is ambiguous.

---

## 9. Comparison Eligibility

In `COMPARE_POSTGRES_WITH_BASELINE` mode:

1. Read a saved baseline row.
2. Match it to the current SQL using `baselineKey`.
3. Confirm the current statement is SELECT.
4. Confirm the current binding plan is usable.
5. Confirm the current PostgreSQL validation status is `PASSED`.
6. Reuse the exact saved parameter values.
7. Execute only PostgreSQL.
8. Compare with the saved H2 baseline.

If the migrated SQL is still not PASSED:

```text
CURRENT_POSTGRES_NOT_READY
```

If the current SQL cannot be matched:

```text
BASELINE_KEY_NOT_FOUND
```

If multiple current SQL records match:

```text
AMBIGUOUS_BASELINE_KEY
```

---

## 10. Eligibility Statuses

Use statuses suitable for both modes:

```text
AUTO_COMPARABLE
BASELINE_ONLY
EXECUTION_ONLY
MANUAL_MAPPING_REQUIRED
MANUAL_BASELINE_MAPPING_REQUIRED
NO_SAMPLE_DATA
SKIPPED_UNSAFE
SKIPPED_UNSUPPORTED
STALE_VALIDATION_RESULT
CURRENT_POSTGRES_NOT_READY
BASELINE_KEY_NOT_FOUND
AMBIGUOUS_BASELINE_KEY
NOT_ELIGIBLE
```

Every row must contain:

```text
eligibilityStatus
eligibilityReason
```

---

## 11. Real Parameter Sampling

### 11.1 Source

All new samples in capture mode come from H2.

### 11.2 Same tuple for both databases

For current PASSED SQL, the same tuple is bound to H2 and PostgreSQL.

The two databases must never sample independent values.

### 11.3 No Mock fallback

Mock values remain available in existing EXPLAIN validation.

For Cross DB validation:

```text
real H2 sample exists → execute
real H2 sample unavailable → NO_SAMPLE_DATA or MANUAL_MAPPING_REQUIRED
```

No Mock fallback is allowed.

### 11.4 Tuple sampling

Multiple parameters must be sampled from one logically valid H2 row or one clearly defined joined row.

Example:

```sql
WHERE customer_id = ?
  AND status = ?
```

The tuple must contain values from the same source record.

### 11.5 Saved parameter values

The baseline must persist:

```text
parameterNames
parameterTypes
parameterValues
```

Parameter values do not require masking.

Comparison mode must reuse these exact values and must not resample.

### 11.6 Repeated logical parameters

When one logical parameter appears in multiple JDBC positions, the same saved value must be used for all positions.

---

## 12. Sample Counts

Configuration:

```yaml
select-sample-size: 3
collection-sample-size: 1
```

`select-sample-size: 3` means up to three distinct real parameter tuples.

It does not mean three repeated performance cycles.

Rules:

```text
0 available tuples → no execution
1 available tuple  → execute 1 sample
2 available tuples → execute 2 samples
3+ available tuples → execute exactly 3 samples
```

Each sample executes once per applicable database.

Comparison mode reuses all saved samples from the baseline. It does not generate new samples.

---

## 13. Automatically Supported Parameter Mappings

The MVP may automatically sample simple predicates such as:

```sql
column = ?
column <> ?
column > ?
column >= ?
column < ?
column <= ?
LOWER(column) = ?
column IN (?)
```

All parameters must be traceable to an H2 table/column.

Multiple parameters must form one valid tuple.

---

## 14. Manual or Unsupported Parameter Mappings

Return `MANUAL_MAPPING_REQUIRED` for cases such as:

```sql
amount > ? * exchange_rate
created_date > current_date - ?
function_a(column, ?) = other_column
column = (SELECT ... WHERE id = ?)
CASE WHEN ... THEN ? ...
ORDER BY ?
FROM ?
```

Also treat these as manual or unsupported:

- Dynamic table names.
- Dynamic column names.
- Complex CTE mappings.
- Deep nested subqueries.
- Complex join-derived parameter tuples.
- Parameters generated only by application business logic.

The MVP must not become a generic SQL reasoning engine.

---

## 15. LIKE Parameters

For:

```sql
LOWER(name) LIKE ?
```

the sampled H2 column value alone does not determine whether the runtime parameter should be:

```text
value%
%value
%value%
```

Rules:

- Apply wildcard transformation only when source metadata or captured Java parameter construction makes the pattern explicit.
- Otherwise return `MANUAL_MAPPING_REQUIRED`.
- Do not assume `%value%`.

---

## 16. Collection Parameters

For:

```sql
WHERE id IN (:ids)
```

the MVP uses one real value:

```yaml
collection-sample-size: 1
```

The saved baseline must preserve the collection value in a stable serialized form.

Arbitrary large collections are outside the MVP.

---

## 17. Safe SELECT Rules

A SELECT may execute automatically when:

- It is read-only.
- Parameter sampling succeeds or it has no parameters.
- The result size is controlled.
- No obvious database side effect exists.

Do not automatically execute:

```text
SELECT FOR UPDATE
NEXTVAL / SETVAL
SELECT INTO
lock-related functions
state-changing functions
custom functions with unknown side effects
```

Recommended controls:

```yaml
max-select-rows: 1000
statement-timeout-ms: 5000
```

If more than `maxSelectRows` are detected:

```text
RESULT_TOO_LARGE
```

Do not compare a truncated result as if it were complete.

---

## 18. SELECT Result Capture and Comparison

Per sample, capture:

```text
execution status
row count
normalized result hash
normalized column signature
observed execution time
error message
```

### 18.1 Column signature

The normalized column signature should include ordered column labels and, when practical, normalized JDBC type families.

Example:

```text
ID:INTEGER|NAME:STRING|STATUS:STRING
```

This enables:

```text
COLUMN_STRUCTURE_MISMATCH
```

### 18.2 Row normalization

At minimum normalize:

- NULL.
- Numeric values such as `1`, `1.0`, `1.00`.
- Date, Time and Timestamp.
- Boolean values such as `0/1` and `true/false`.
- Column-name case.
- Byte arrays.
- Common H2/PostgreSQL JDBC type differences.

### 18.3 Row order

Without stable `ORDER BY`:

```text
normalize each row
→ preserve duplicates
→ sort normalized rows
→ calculate SHA-256 hash
```

Do not use a `Set`.

### 18.4 Non-deterministic SELECT

Statements such as:

```sql
SELECT CURRENT_TIMESTAMP
SELECT RANDOM()
SELECT UUID()
```

may be marked:

```text
EXECUTION_ONLY
```

Compare execution status and row count, but do not require result hash equality.

---

## 19. Comparison Statuses

Per sample:

```text
MATCH
MISMATCH
EXECUTION_ONLY
H2_EXECUTION_FAILED
POSTGRES_EXECUTION_FAILED
BOTH_EXECUTION_FAILED
RESULT_TOO_LARGE
PENDING_SQL_MIGRATION
CURRENT_POSTGRES_NOT_READY
NOT_EXECUTED
```

Overall:

```text
ALL_SAMPLES_MATCH
PARTIAL_MISMATCH
PARTIAL_EXECUTION_FAILURE
BASELINE_CAPTURED
PENDING_SQL_MIGRATION
NO_SAMPLE_DATA
EXECUTION_ONLY
NOT_EXECUTED
```

Difference categories:

```text
NONE
ROW_COUNT_MISMATCH
RESULT_HASH_MISMATCH
COLUMN_STRUCTURE_MISMATCH
H2_EXECUTION_ERROR
POSTGRES_EXECUTION_ERROR
RESULT_TOO_LARGE
POSSIBLE_DATA_DRIFT
BASELINE_MAPPING_ERROR
UNKNOWN
```

---

## 20. Observed Execution Time

Each sample executes once per applicable database.

Do not implement:

- Warm-up.
- Repeated cycles.
- Average calculations.
- P95/P99.
- Formal performance analysis.

SELECT timing begins immediately before `executeQuery()` and ends after the allowed ResultSet is completely read.

The report must state:

```text
Observed execution time from a single validation run.
Not intended as a formal performance benchmark.
```

---

## 21. Baseline Immutability and Metadata

The baseline is a long-lived handover artifact.

It must store:

```text
baselineRunId
baselineCreatedAt
normalizationVersion
baselineKey
H2 database/schema identity
PostgreSQL database/schema identity at capture
```

Do not store passwords.

Recommended behavior:

- Generate `baselineRunId` when omitted.
- Fail if baseline output already exists by default.
- Require explicit user action to replace a baseline.
- Log the final baseline path and run ID.

---

## 22. Data Drift

The PostgreSQL database was copied from H2, but data may change before future comparison.

The tool cannot prove that a mismatch is caused only by SQL.

When environments may have changed, the report may classify an unexplained mismatch as:

```text
POSSIBLE_DATA_DRIFT
```

The handover documentation must state that accurate comparison assumes the PostgreSQL data still corresponds to the captured H2 dataset or has been refreshed from the same source snapshot.

---

## 23. Baseline CSV Schema

Use one row per sample with this exact order:

```text
baselineRunId
baselineCreatedAt
normalizationVersion
baselineKey
sqlId
serviceName
className
methodName
sourceType
sqlVariableName
occurrenceIndex
statementType
h2Database
h2Schema
postgresDatabaseAtCapture
postgresSchemaAtCapture
postgresValidationStatusAtCapture
eligibilityStatus
eligibilityReason
sampleIndex
requestedSampleCount
collectedSampleCount
parameterNames
parameterTypes
parameterValues
h2JdbcSql
h2ExecutionStatus
h2RowCount
h2ResultHash
h2ColumnSignature
h2ExecutionTimeMs
h2ErrorMessage
postgresJdbcSqlAtCapture
postgresExecutionStatus
postgresRowCount
postgresResultHash
postgresColumnSignature
postgresExecutionTimeMs
postgresErrorMessage
sampleComparisonStatus
overallComparisonStatus
differenceCategory
differenceMessage
```

For baseline-only records:

```text
postgresExecutionStatus = PENDING_SQL_MIGRATION
PostgreSQL result fields = empty
overallComparisonStatus = BASELINE_CAPTURED
```

---

## 24. Comparison CSV Schema

Use one row per saved baseline sample with this exact order:

```text
baselineRunId
baselineCreatedAt
normalizationVersion
baselineKey
baselineSqlId
currentSqlId
serviceName
className
methodName
sourceType
sqlVariableName
occurrenceIndex
statementType
h2Database
h2Schema
currentPostgresDatabase
currentPostgresSchema
currentPostgresValidationStatus
eligibilityStatus
eligibilityReason
sampleIndex
requestedSampleCount
parameterNames
parameterTypes
parameterValues
baselineH2JdbcSql
baselineH2ExecutionStatus
baselineH2RowCount
baselineH2ResultHash
baselineH2ColumnSignature
currentPostgresJdbcSql
currentPostgresExecutionStatus
currentPostgresRowCount
currentPostgresResultHash
currentPostgresColumnSignature
currentPostgresExecutionTimeMs
currentPostgresErrorMessage
sampleComparisonStatus
overallComparisonStatus
differenceCategory
differenceMessage
```

---

## 25. Failure Isolation

Any single SQL may encounter:

- Baseline-key failure.
- Sampling failure.
- H2 execution failure.
- PostgreSQL execution failure.
- Timeout.
- Result too large.
- Normalization failure.
- Current SQL not found.

The batch must:

```text
record the current SQL/sample result
continue with the next SQL/sample
```

---

## 26. Excel Report Integration

The final workbook remains:

```text
sql-postgres-migration-report.xlsx
```

Sheets:

```text
1. Summary
2. Detail
3. Cross DB Comparison
```

The Cross DB phase writes CSV only.

The `excel-report` phase rebuilds the workbook from CSV inputs.

Recommended report inputs:

```yaml
validator:
  report:
    cross-db-baseline-input: ./output/sql-select-baseline.csv
    cross-db-comparison-input: ./output/sql-select-comparison.csv
```

Behavior:

- If neither Cross DB file exists: generate existing `Summary + Detail`.
- If only baseline exists: show current capture results and pending migration baselines.
- If comparison exists: show current comparison results, linked to the baseline.
- Do not create a second workbook.
- Do not add many Cross DB columns to the existing `Detail` sheet.

---

## 27. Cross DB Comparison Sheet

Recommended columns:

```text
Baseline Run ID
Baseline Key
Baseline SQL ID
Current SQL ID
Service
Class
Method
Statement Type
PostgreSQL Validation Status
Eligibility Status
Eligibility Reason
Sample Index
Parameter Names
Parameter Types
Parameter Values
H2 Status
H2 Row Count
H2 Time (ms)
PostgreSQL Status
PostgreSQL Row Count
PostgreSQL Time (ms)
Sample Comparison
Overall Comparison
Difference Category
Difference Message
H2 Error Message
PostgreSQL Error Message
```

Parameter values are not masked.

Long parameter, error and difference cells should be wrapped and top-aligned.

---

## 28. Summary KPI Extension

Keep the existing PostgreSQL compatibility summary.

Add a separate Cross DB KPI area:

```text
Baseline SQL
Baseline Samples
Current Dual-DB Comparisons
All Samples Matched
Partial Mismatch
Execution Failure
Pending SQL Migration
Current PostgreSQL Not Ready
No Sample Data
Manual Mapping Required
Baseline Mapping Failure
Skipped Unsafe
Skipped Unsupported
```

Do not mix these counts with the existing compatibility issue counts.

---

## 29. Configuration

Recommended configuration:

```yaml
validator:
  phase: cross-db-validation

  cross-database:
    mode: CAPTURE_BASELINE

    inventory-input: ./output/sql-inventory.csv
    binding-plan-input: ./output/sql-binding-plan.csv
    execution-input: ./output/sql-execution-report.csv

    baseline-input: ./output/sql-select-baseline.csv
    baseline-output: ./output/sql-select-baseline.csv
    comparison-output: ./output/sql-select-comparison.csv

    baseline-run-id:
    normalization-version: v1
    fail-if-baseline-exists: true

    source:
      database-type: H2
      jdbc-url:
      username:
      password:
      schema:

    select-sample-size: 3
    collection-sample-size: 1
    prefer-non-null-values: true
    distinct-parameter-tuples: true
    max-select-rows: 1000
    statement-timeout-ms: 5000
```

Reuse the existing PostgreSQL configuration. Do not create a duplicate PostgreSQL block.

---

## 30. Explicit Non-Goals

This MVP does not:

- Execute or compare DML.
- Modify business SQL automatically.
- Modify business microservice source code.
- Use Mock fallback.
- Perform formal performance tests.
- Repeat samples.
- Infer arbitrary complex parameter mappings.
- Generate unique values.
- Capture full result rows in CSV.
- Replace the existing PostgreSQL validator.
- Guarantee that every future mismatch is caused by SQL rather than data drift.
- Create a second Excel report.

---

## 31. Acceptance Criteria

The MVP is complete when:

1. Existing phases behave as before.
2. `cross-db-validation` supports both modes.
3. The new feature is SELECT-only.
4. CAPTURE mode processes current PASSED SELECT with H2 + PostgreSQL comparison.
5. CAPTURE mode processes eligible current non-PASSED SELECT as H2 baseline only.
6. Real parameter tuples come from H2.
7. Current dual execution uses the same SQL and same tuple.
8. Baseline parameters are saved without masking.
9. Up to three distinct samples are supported.
10. Each sample executes once.
11. Baseline output is protected from silent overwrite.
12. Stable `baselineKey` is generated from source metadata.
13. Comparison mode does not require H2.
14. Comparison mode reuses exact saved parameter values.
15. Comparison mode matches current SQL using `baselineKey`.
16. Current PostgreSQL SQL must pass validation before execution.
17. SELECT comparison includes row count, column signature, normalized hash and observed time.
18. One failure does not terminate the batch.
19. Baseline CSV and comparison CSV use the agreed headers.
20. Excel report optionally adds `Cross DB Comparison`.
21. Missing Cross DB files do not break the existing two-sheet report.
22. No business microservice source code is modified.

---

## 32. Product Positioning

The delivered capability is:

> A SELECT-only migration-validation MVP that captures permanent H2 real-data execution baselines before SQL migration, immediately compares already-compatible SQL against PostgreSQL, and later reuses the exact saved parameters and H2 result hashes to validate migrated PostgreSQL SQL without requiring the original H2 SQL to remain in the codebase.
