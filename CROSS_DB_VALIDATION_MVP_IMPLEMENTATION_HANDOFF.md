# Cross-Database SELECT Baseline Validation MVP — Implementation Handoff

**Document status:** Implementation Handoff v2.0  
**Project:** SQL PostgreSQL Validator  
**Runtime:** JDK 17  
**Framework:** Spring Boot CLI  
**Source database:** H2  
**Target database:** PostgreSQL  
**Scope:** SELECT only  
**Implementation style:** Incremental, independently compilable batches

---

## 1. Implementation Objective

Add one independent phase:

```text
cross-db-validation
```

with two modes:

```text
CAPTURE_BASELINE
COMPARE_POSTGRES_WITH_BASELINE
```

### CAPTURE_BASELINE

- Read current inventory, binding plan and PostgreSQL execution report.
- Process eligible SELECT SQL.
- Sample up to three real parameter tuples from H2.
- For current PostgreSQL `PASSED` SQL:
  - execute H2;
  - execute PostgreSQL;
  - compare and save both results.
- For eligible current non-PASSED SQL:
  - execute H2 only;
  - save immutable H2 baseline and real parameters;
  - mark PostgreSQL `PENDING_SQL_MIGRATION`.

### COMPARE_POSTGRES_WITH_BASELINE

- Read the saved baseline.
- Read current inventory, binding plan and execution report after SQL migration.
- Match old and current SQL using `baselineKey`.
- Reuse exact saved parameter values.
- Execute only current PostgreSQL SQL.
- Compare with saved H2 baseline.
- Write comparison CSV.

Do not modify any business microservice module.

---

## 2. Authoritative Scope

The new feature must not implement:

```text
INSERT
UPDATE
DELETE
MERGE
DML transactions
rollback
affected rows
DML sample sizes
DML enablement flags
DML whitelists
```

Do not delete or alter existing DML-related validator phases. They are unrelated existing functionality.

Any older Cross DB document or code containing DML design is obsolete.

---

## 3. Suggested Package Structure

Use the existing root package and project conventions. A possible structure is:

```text
org.rosetta.sqlvalidator.crossdb
├── config
│   └── CrossDatabaseProperties.java
├── mode
│   └── CrossDatabaseMode.java
├── identity
│   ├── BaselineKeyFactory.java
│   └── BaselineIdentity.java
├── eligibility
│   ├── CrossDatabaseEligibilityEvaluator.java
│   ├── EligibilityDecision.java
│   └── EligibilityStatus.java
├── model
│   ├── CrossDatabaseCandidate.java
│   ├── ParameterTuple.java
│   ├── ParameterTupleValue.java
│   ├── SelectBaselineRecord.java
│   ├── SelectBaselineComparisonRecord.java
│   ├── SelectExecutionResult.java
│   ├── SampleComparisonStatus.java
│   ├── OverallComparisonStatus.java
│   └── DifferenceCategory.java
├── sampling
│   ├── RealParameterSamplePlanner.java
│   ├── H2ParameterSampleProvider.java
│   ├── ParameterColumnMapping.java
│   ├── ParameterSamplingQueryBuilder.java
│   └── LikePatternResolver.java
├── execution
│   ├── SelectExecutionEngine.java
│   ├── JdbcParameterBinder.java
│   └── TimedExecution.java
├── comparison
│   ├── ResultSetNormalizer.java
│   ├── NormalizedResult.java
│   ├── ResultHashCalculator.java
│   └── SelectResultComparator.java
├── csv
│   ├── SelectBaselineCsvReader.java
│   ├── SelectBaselineCsvWriter.java
│   ├── SelectBaselineComparisonCsvWriter.java
│   └── CurrentSqlInputReaders.java
├── service
│   ├── BaselineCaptureService.java
│   └── BaselineComparisonService.java
└── runner
    └── CrossDatabaseValidationRunner.java
```

Match existing package patterns if they differ. Do not create duplicate utility layers without checking existing code.

---

## 4. Existing Components to Reuse

Reuse where possible:

- Existing `ValidatorRunner`.
- Existing `RunnerDispatcher`.
- Existing `ValidatorPhase`.
- Existing `ValidatorProperties`.
- Existing inventory CSV reader/model.
- Existing binding-plan CSV reader/model.
- Existing execution-report CSV reader/model.
- Existing `StatementType`.
- Existing parameter binding logic.
- Existing JSqlParser integration.
- Existing PostgreSQL configuration.
- Existing Commons CSV utilities.
- Existing Apache POI report writer.
- Existing logging and exception conventions.

Binding-plan CSV may contain one row per binding. Aggregate it by `sqlId`.

---

## 5. Phase and Mode

Add:

```java
CROSS_DB_VALIDATION("cross-db-validation")
```

Add:

```java
public enum CrossDatabaseMode {
    CAPTURE_BASELINE,
    COMPARE_POSTGRES_WITH_BASELINE
}
```

Default mode:

```text
CAPTURE_BASELINE
```

Use one runner and switch behavior by mode.

---

## 6. Configuration Model

Recommended fields:

```java
public class CrossDatabaseProperties {

    private CrossDatabaseMode mode = CrossDatabaseMode.CAPTURE_BASELINE;

    private Path inventoryInput;
    private Path bindingPlanInput;
    private Path executionInput;

    private Path baselineInput;
    private Path baselineOutput;
    private Path comparisonOutput;

    private String baselineRunId;
    private String normalizationVersion = "v1";
    private boolean failIfBaselineExists = true;

    private SourceDatabaseProperties source = new SourceDatabaseProperties();

    private int selectSampleSize = 3;
    private int collectionSampleSize = 1;
    private boolean preferNonNullValues = true;
    private boolean distinctParameterTuples = true;
    private int maxSelectRows = 1000;
    private int statementTimeoutMs = 5000;
}
```

Source properties:

```java
public class SourceDatabaseProperties {

    private String databaseType = "H2";
    private String jdbcUrl;
    private String username;
    private String password;
    private String schema;
}
```

Reuse current PostgreSQL properties. Do not duplicate PostgreSQL credentials.

---

## 7. YAML

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

Report configuration planned for the report batch:

```yaml
validator:
  report:
    cross-db-baseline-input: ./output/sql-select-baseline.csv
    cross-db-comparison-input: ./output/sql-select-comparison.csv
```

---

## 8. Stable Baseline Key

Suggested factory input:

```java
public record BaselineIdentity(
        String serviceName,
        String className,
        String methodName,
        String sourceType,
        String sqlVariableName,
        int occurrenceIndex
) {
}
```

Canonical key:

```text
serviceName|className|methodName|sourceType|sqlVariableName|occurrenceIndex
```

Normalize empty optional parts consistently.

Do not use SQL text, line number or current `sqlId` as the sole identity.

Detect duplicate keys before database execution.

Duplicate or missing identities must produce:

```text
MANUAL_BASELINE_MAPPING_REQUIRED
```

---

## 9. Candidate Aggregation

Build one candidate per current `sqlId`.

Suggested model:

```java
public record CrossDatabaseCandidate(
        String sqlId,
        String baselineKey,
        String serviceName,
        String className,
        String methodName,
        String sourceType,
        String sqlVariableName,
        int occurrenceIndex,
        String jdbcSql,
        StatementType statementType,
        boolean requiresManualReview,
        String postgresValidationStatus,
        List<BindingRow> bindings
) {
}
```

Do not duplicate candidates because the binding CSV contains multiple rows.

---

## 10. Eligibility Status

Use:

```java
public enum EligibilityStatus {
    AUTO_COMPARABLE,
    BASELINE_ONLY,
    EXECUTION_ONLY,
    MANUAL_MAPPING_REQUIRED,
    MANUAL_BASELINE_MAPPING_REQUIRED,
    NO_SAMPLE_DATA,
    SKIPPED_UNSAFE,
    SKIPPED_UNSUPPORTED,
    STALE_VALIDATION_RESULT,
    CURRENT_POSTGRES_NOT_READY,
    BASELINE_KEY_NOT_FOUND,
    AMBIGUOUS_BASELINE_KEY,
    NOT_ELIGIBLE
}
```

Suggested decision:

```java
public record EligibilityDecision(
        EligibilityStatus status,
        String reasonCode,
        String reason
) {
}
```

---

## 11. Capture Eligibility Algorithm

Recommended order:

```java
if (statementType != SELECT) {
    return NOT_ELIGIBLE;
}

if (requiresManualReview) {
    return MANUAL_MAPPING_REQUIRED;
}

if (!bindingPlanUsable) {
    return MANUAL_MAPPING_REQUIRED;
}

if (baselineKeyMissingOrDuplicate) {
    return MANUAL_BASELINE_MAPPING_REQUIRED;
}

if (containsMultipleStatements) {
    return SKIPPED_UNSUPPORTED;
}

if (containsDynamicIdentifier) {
    return MANUAL_MAPPING_REQUIRED;
}

if (isUnsafeSelect) {
    return SKIPPED_UNSAFE;
}

if (isNonDeterministicButSafe) {
    return EXECUTION_ONLY;
}

if (postgresValidationPassed) {
    return AUTO_COMPARABLE;
}

return BASELINE_ONLY;
```

Do not require PostgreSQL `PASSED` for H2 baseline capture.

---

## 12. Comparison Eligibility Algorithm

For each baseline sample:

```java
current = findCurrentSqlByBaselineKey(baseline.baselineKey());

if (current == null) {
    return BASELINE_KEY_NOT_FOUND;
}

if (multipleCurrentMatches) {
    return AMBIGUOUS_BASELINE_KEY;
}

if (current.statementType() != SELECT) {
    return SKIPPED_UNSUPPORTED;
}

if (!current.bindingPlanUsable()) {
    return MANUAL_MAPPING_REQUIRED;
}

if (!current.postgresValidationPassed()) {
    return CURRENT_POSTGRES_NOT_READY;
}

if (isUnsafeSelect(current.jdbcSql())) {
    return SKIPPED_UNSAFE;
}

return AUTO_COMPARABLE;
```

Comparison mode does not sample values and does not connect to H2.

---

## 13. Parameter Tuple Model

Suggested model:

```java
public record ParameterTuple(
        int sampleIndex,
        List<ParameterTupleValue> values
) {
}
```

```java
public record ParameterTupleValue(
        int logicalParameterIndex,
        List<Integer> jdbcBindingIndexes,
        String parameterName,
        String javaType,
        Object value,
        String sourceTable,
        String sourceColumn
) {
}
```

The saved baseline serialization must be deterministic and reversible.

Parameter values are not masked.

---

## 14. Parameter Serialization

The baseline must preserve exact values for future PostgreSQL binding.

Do not rely only on display text.

Recommended serialized payload:

```text
parameterNames
parameterTypes
parameterValues
```

The implementation may use a compact JSON array if a JSON library is already available. Do not add a large dependency only for this feature.

Example conceptual payload:

```json
[
  {
    "name": "status",
    "javaType": "java.lang.String",
    "jdbcIndexes": [1],
    "value": "ACTIVE"
  },
  {
    "name": "regionId",
    "javaType": "java.lang.Long",
    "jdbcIndexes": [2],
    "value": 100
  }
]
```

CSV display may remain human-readable, but future comparison must parse the persisted values reliably.

---

## 15. Sampling Planner

Support only simple mappings:

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

Build one H2 query selecting all tuple columns from the same row.

Example:

```sql
SELECT DISTINCT STATUS, REGION_ID
FROM CUSTOMER
WHERE STATUS IS NOT NULL
  AND REGION_ID IS NOT NULL
LIMIT ?
```

Centralize H2 limit syntax.

Return at most:

```text
selectSampleSize
```

Use:

```text
collectionSampleSize = 1
```

No Mock fallback.

---

## 16. LIKE Handling

Apply wildcard transformation only when the Java parameter construction or captured metadata provides an explicit rule.

Otherwise:

```text
MANUAL_MAPPING_REQUIRED
```

Do not assume `%value%`.

---

## 17. SELECT Safety

Reject:

```text
SELECT FOR UPDATE
NEXTVAL
SETVAL
SELECT INTO
lock-related functions
state-changing functions
custom functions with unknown side effects
multiple executable statements
```

Set statement timeout.

Detect oversized results by reading at most:

```text
maxSelectRows + 1
```

---

## 18. Execution Result Model

Suggested model:

```java
public record SelectExecutionResult(
        String executionStatus,
        Integer rowCount,
        String resultHash,
        String columnSignature,
        Long executionTimeMs,
        String errorMessage,
        boolean resultTooLarge
) {
}
```

Use nullable numeric fields for not-applicable values.

---

## 19. SELECT Execution

Timing begins before `executeQuery()` and ends after ResultSet normalization is complete.

Pseudo-flow:

```java
long start = System.nanoTime();

try (PreparedStatement statement = connection.prepareStatement(sql)) {
    configureTimeout(statement);
    bind(statement, tuple);

    try (ResultSet resultSet = statement.executeQuery()) {
        normalizedResult = normalizer.read(resultSet, maxSelectRows);
    }
}

long elapsedMs = nanosToMillis(System.nanoTime() - start);
```

For current PASSED capture:

```text
execute H2
execute PostgreSQL
```

For baseline-only capture:

```text
execute H2
PostgreSQL = PENDING_SQL_MIGRATION
```

For future comparison:

```text
execute PostgreSQL only
```

---

## 20. Result Normalization

Normalize:

- NULL.
- BigDecimal with trailing zeros removed.
- Integral numbers.
- Date.
- Time.
- Timestamp.
- Boolean.
- byte[].
- Common H2/PostgreSQL JDBC type differences.
- Column labels and type families.

Preserve duplicate rows.

Without stable ORDER BY, sort normalized rows before SHA-256 hashing.

---

## 21. Comparison Status Enums

Sample:

```java
public enum SampleComparisonStatus {
    MATCH,
    MISMATCH,
    EXECUTION_ONLY,
    H2_EXECUTION_FAILED,
    POSTGRES_EXECUTION_FAILED,
    BOTH_EXECUTION_FAILED,
    RESULT_TOO_LARGE,
    PENDING_SQL_MIGRATION,
    CURRENT_POSTGRES_NOT_READY,
    NOT_EXECUTED
}
```

Overall:

```java
public enum OverallComparisonStatus {
    ALL_SAMPLES_MATCH,
    PARTIAL_MISMATCH,
    PARTIAL_EXECUTION_FAILURE,
    BASELINE_CAPTURED,
    PENDING_SQL_MIGRATION,
    NO_SAMPLE_DATA,
    EXECUTION_ONLY,
    NOT_EXECUTED
}
```

Difference:

```java
public enum DifferenceCategory {
    NONE,
    ROW_COUNT_MISMATCH,
    RESULT_HASH_MISMATCH,
    COLUMN_STRUCTURE_MISMATCH,
    H2_EXECUTION_ERROR,
    POSTGRES_EXECUTION_ERROR,
    RESULT_TOO_LARGE,
    POSSIBLE_DATA_DRIFT,
    BASELINE_MAPPING_ERROR,
    UNKNOWN
}
```

---

## 22. Baseline Record

Create `SelectBaselineRecord` with exact CSV headers:

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

Use exact order.

---

## 23. Comparison Record

Create `SelectBaselineComparisonRecord` with exact CSV headers:

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

Use exact order.

---

## 24. CSV Behavior

Writers must:

- Use UTF-8.
- Use header-name conventions consistent with the project.
- Preserve exact header order.
- Write empty fields for null/not-applicable values.
- Write a row for skipped SQL.
- Create parent directories.
- Support a header-only CSV.
- Never write credentials.
- Keep parameter values unmasked.

The baseline reader must preserve the exact parameter representation needed for future binding.

---

## 25. Baseline Protection

Before capture:

```java
if (failIfBaselineExists && Files.exists(baselineOutput)) {
    fail clearly;
}
```

Do not silently overwrite.

If `baselineRunId` is empty, generate one such as:

```text
baseline-YYYYMMDD-HHmmss
```

Use UTC or the existing project time convention consistently.

---

## 26. Failure Isolation

Process every candidate/sample independently.

One error must produce a result row and continue.

Unexpected failure example:

```java
for (CrossDatabaseCandidate candidate : candidates) {
    try {
        process(candidate);
    } catch (Exception ex) {
        writeUnexpectedFailure(candidate, ex);
    }
}
```

---

## 27. Excel Integration

Do not create a second workbook.

Add optional report inputs:

```text
crossDbBaselineInput
crossDbComparisonInput
```

The report must keep existing behavior when neither file exists.

Third sheet:

```text
Cross DB Comparison
```

Use a unified view over baseline and comparison records.

If comparison exists, show the latest current PostgreSQL result while retaining baseline identity and H2 values.

Keep parameter values visible and unmasked.

---

## 28. Suggested Implementation Batches

### Batch 1 — Skeleton

- Phase registration.
- Mode enum.
- SELECT-only configuration.
- Baseline/comparison enums and models.
- Two header-only CSV writers.
- Runner skeleton.
- Tests.
- No database connections.

### Batch 2 — Input aggregation and baseline identity

- Read current CSVs.
- Aggregate candidates.
- Generate stable `baselineKey`.
- Detect duplicate/missing keys.
- Capture/compare eligibility without database execution.
- Write skip/pending rows.

### Batch 3 — H2 parameter sampling

- Simple SELECT predicates.
- Real tuple sampling.
- Up to three samples.
- Exact parameter serialization.
- No Mock fallback.

### Batch 4 — Capture execution

- H2 SELECT execution.
- PostgreSQL execution for current PASSED SQL.
- Baseline-only path for non-PASSED SQL.
- Normalization, column signature, hash and observed time.

### Batch 5 — Future comparison mode

- Read immutable baseline.
- Match current SQL by baselineKey.
- Reuse saved parameter values.
- Execute PostgreSQL only.
- Compare with H2 baseline.

### Batch 6 — Excel integration

- Optional readers.
- Third sheet.
- Summary KPI.
- Backward compatibility.

---

## 29. Testing Plan

At minimum test:

### Batch 1

- Phase parsing.
- Default mode.
- Default configuration.
- Exact baseline headers.
- Exact comparison headers.
- Header-only outputs.
- No DB connection required.

### Identity and eligibility

- Stable key generation.
- Duplicate key.
- Missing metadata.
- PASSED SELECT → AUTO_COMPARABLE.
- Non-PASSED usable SELECT → BASELINE_ONLY.
- Manual review rejected.
- Non-SELECT excluded.
- Unsafe SELECT rejected.

### Sampling

- One-column sample.
- Multi-column tuple from same row.
- Up to three distinct tuples.
- No sample.
- Repeated logical parameter.
- Collection size one.
- Ambiguous LIKE.

### Normalization

- Numbers.
- NULL.
- Date/time/timestamp.
- Boolean.
- Byte array.
- Duplicate rows.
- Different row order.
- Column signature.

### Capture

- PASSED SQL executes both.
- Non-PASSED SQL executes H2 only.
- PENDING_SQL_MIGRATION fields.
- Baseline overwrite protection.
- One failure does not stop batch.

### Comparison

- Baseline key matched.
- Key not found.
- Ambiguous key.
- Current PostgreSQL not ready.
- Exact saved parameters reused.
- Matching result.
- Row-count mismatch.
- Hash mismatch.
- Column mismatch.
- Possible data drift classification.

### Excel

- No Cross DB files → two sheets.
- Baseline only → third sheet.
- Comparison present → third sheet with current result.
- Long parameter/error cells wrapped and top-aligned.

---

## 30. Definition of Done

The implementation is done when:

- JDK 17 compilation succeeds.
- Existing phases remain unchanged.
- Cross DB feature contains no DML implementation.
- Both modes run independently.
- Baseline capture handles PASSED and eligible non-PASSED SELECT.
- Baseline is immutable by default.
- Exact real parameters are saved without masking.
- Future comparison does not require H2.
- Future comparison uses stable baselineKey and saved values.
- Result normalization is deterministic.
- CSV schemas match exactly.
- Excel remains one workbook with an optional third sheet.
- Failures are isolated.
- Scope has not expanded beyond SELECT baseline validation.
