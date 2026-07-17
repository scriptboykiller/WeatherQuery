# Copilot Batch 1 Prompt — SELECT Baseline Validation v2

Read these two documents first:

- docs/cross-db/CROSS_DB_VALIDATION_MVP_REQUIREMENTS_AND_DESIGN.md
- docs/cross-db/CROSS_DB_VALIDATION_MVP_IMPLEMENTATION_HANDOFF.md

These v2 documents are authoritative. Ignore and remove any older Cross DB
design or code that includes UPDATE, DELETE, INSERT, DML transactions,
rollback, affected rows, DML sample sizes or DML whitelists.

We will implement the feature in small independently compilable batches.

Implement ONLY Batch 1: project skeleton, configuration, SELECT-only models,
header-only CSV writers and runner integration.

Before editing:

1. Inspect the existing project.
2. Reuse the exact package structure, ValidatorRunner signature,
   RunnerDispatcher mechanism, configuration pattern, logging style,
   Commons CSV conventions and test framework.
3. Do not redesign existing phases.
4. Do not modify any business microservice module.
5. If previous Cross DB skeleton code exists, update it to match the v2
   SELECT-only documents and remove obsolete DML-related Cross DB fields.

======================================================================
BATCH 1 REQUIRED WORK
======================================================================

1. Add to ValidatorPhase:

   CROSS_DB_VALIDATION("cross-db-validation")

   Preserve existing fromConfigValue behavior.

2. Add:

   public enum CrossDatabaseMode {
       CAPTURE_BASELINE,
       COMPARE_POSTGRES_WITH_BASELINE
   }

   Default mode: CAPTURE_BASELINE.

3. Add SELECT-only Cross DB configuration with:

   - mode
   - inventoryInput
   - bindingPlanInput
   - executionInput
   - baselineInput
   - baselineOutput
   - comparisonOutput
   - baselineRunId
   - normalizationVersion, default "v1"
   - failIfBaselineExists, default true

   Source H2 properties:

   - source.databaseType, default "H2"
   - source.jdbcUrl
   - source.username
   - source.password
   - source.schema

   SELECT controls:

   - selectSampleSize, default 3
   - collectionSampleSize, default 1
   - preferNonNullValues, default true
   - distinctParameterTuples, default true
   - maxSelectRows, default 1000
   - statementTimeoutMs, default 5000

   Do not add any DML properties.

4. Add these enums:

   EligibilityStatus:
   - AUTO_COMPARABLE
   - BASELINE_ONLY
   - EXECUTION_ONLY
   - MANUAL_MAPPING_REQUIRED
   - MANUAL_BASELINE_MAPPING_REQUIRED
   - NO_SAMPLE_DATA
   - SKIPPED_UNSAFE
   - SKIPPED_UNSUPPORTED
   - STALE_VALIDATION_RESULT
   - CURRENT_POSTGRES_NOT_READY
   - BASELINE_KEY_NOT_FOUND
   - AMBIGUOUS_BASELINE_KEY
   - NOT_ELIGIBLE

   SampleComparisonStatus:
   - MATCH
   - MISMATCH
   - EXECUTION_ONLY
   - H2_EXECUTION_FAILED
   - POSTGRES_EXECUTION_FAILED
   - BOTH_EXECUTION_FAILED
   - RESULT_TOO_LARGE
   - PENDING_SQL_MIGRATION
   - CURRENT_POSTGRES_NOT_READY
   - NOT_EXECUTED

   OverallComparisonStatus:
   - ALL_SAMPLES_MATCH
   - PARTIAL_MISMATCH
   - PARTIAL_EXECUTION_FAILURE
   - BASELINE_CAPTURED
   - PENDING_SQL_MIGRATION
   - NO_SAMPLE_DATA
   - EXECUTION_ONLY
   - NOT_EXECUTED

   DifferenceCategory:
   - NONE
   - ROW_COUNT_MISMATCH
   - RESULT_HASH_MISMATCH
   - COLUMN_STRUCTURE_MISMATCH
   - H2_EXECUTION_ERROR
   - POSTGRES_EXECUTION_ERROR
   - RESULT_TOO_LARGE
   - POSSIBLE_DATA_DRIFT
   - BASELINE_MAPPING_ERROR
   - UNKNOWN

5. Add a SELECT-only SelectBaselineRecord supporting these exact CSV headers
   in this exact order:

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

6. Add SelectBaselineComparisonRecord supporting these exact CSV headers
   in this exact order:

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

7. Add:

   - SelectBaselineCsvWriter
   - SelectBaselineComparisonCsvWriter

   Requirements:

   - Reuse existing Commons CSV infrastructure.
   - UTF-8.
   - Exact header order.
   - Create parent directories.
   - Support an empty list and produce a header-only CSV.
   - Write null/not-applicable fields as empty.
   - Do not write credentials.
   - Do not mask parameter values.

8. Add CrossDatabaseValidationRunner using the actual ValidatorRunner
   interface.

   Batch 1 behavior only:

   - Read Cross DB configuration.
   - Validate structural values only.
   - Create parent directories.
   - CAPTURE_BASELINE:
       write a header-only baseline CSV to baselineOutput.
   - COMPARE_POSTGRES_WITH_BASELINE:
       write a header-only comparison CSV to comparisonOutput.
   - Log selected mode and output.
   - Return normally.

   Do not validate DB credentials in Batch 1.

9. Add the application.yml block from the v2 documents without real
   credentials.

10. Add focused tests:

   - ValidatorPhase parses cross-db-validation.
   - CrossDatabaseMode defaults to CAPTURE_BASELINE.
   - Configuration defaults are correct.
   - Baseline CSV header order is exact.
   - Comparison CSV header order is exact.
   - CAPTURE mode creates header-only baseline CSV.
   - COMPARE mode creates header-only comparison CSV.
   - No H2 connection is required.
   - No PostgreSQL connection is required.
   - Existing tests continue to pass.

======================================================================
HARD RESTRICTIONS
======================================================================

Do not implement:

- inventory/binding/execution CSV reading
- baseline CSV reading
- baselineKey generation
- eligibility evaluation
- H2 connection
- PostgreSQL connection
- parameter sampling
- parameter serialization logic
- SELECT execution
- normalization
- hashing
- comparison
- Excel changes
- UPDATE
- DELETE
- INSERT
- DML transactions
- rollback
- affected rows

Do not continue to Batch 2.

After implementation:

1. Compile the project.
2. Run the new tests.
3. Run the existing test suite if practical.
4. Report:
   - every file created;
   - every file modified;
   - every obsolete Cross DB DML field removed;
   - exact baseline headers;
   - exact comparison headers;
   - compilation result;
   - test result;
   - assumptions;
   - requirement conflicts.

Do not claim completion if compilation or tests fail.
