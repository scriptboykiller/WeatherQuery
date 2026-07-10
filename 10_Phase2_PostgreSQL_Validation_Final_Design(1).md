# 10 - Phase 2 PostgreSQL Execution Validation Final Design

## 1. Purpose

This document defines the final design for **Phase 2 PostgreSQL SQL Execution Validation** in the `SQL-Postgres-Validator` Spring Boot CLI project.

The objective of Phase 2 is **not** to prove business correctness. The objective is to validate whether the native SQL statements discovered in Phase 1 and checked in Phase 1.5 can be safely prepared, planned, and optionally executed against the migrated PostgreSQL database.

The tool must remain a migration validation assistant. It must not modify business service modules, must not automatically rewrite source code, and must not depend on runtime AI.

---

## 2. Current Project Status

The current project has already completed:

| Phase | Status | Description |
|---|---:|---|
| Phase 1 | Completed | SQL Inventory Scanner. Extracts native SQL from Java source code and outputs `sql-inventory.csv`. |
| Phase 1.5 | Completed | SQL Text Sanity Check. Uses text rules and JSqlParser to detect dirty extraction or parser issues. |
| Spring Boot CLI Refactor | Completed | Existing tools are now organized as a JDK 17 Spring Boot CLI project with phase-based execution. |

Phase 2 must be added into the **same Spring Boot CLI project**, under the `validation` package, and must be executable independently from Phase 1 and Phase 1.5.

---

## 3. Phase 2 Scope

### 3.1 In Scope

Phase 2 includes:

1. Reading Phase 1 and Phase 1.5 outputs.
2. Building a parameter binding plan for each SQL.
3. Converting Spring/JPA/native SQL placeholders into JDBC executable SQL.
4. Generating safe mock parameter values.
5. Connecting to a PostgreSQL test database.
6. Running controlled validation modes:
   - Phase 2.1: PostgreSQL `EXPLAIN` validation.
   - Phase 2.2: SELECT smoke execution.
   - Phase 2.3: DML safety validation.
   - Phase 2.4: controlled real execution.
7. Classifying PostgreSQL errors using SQLState.
8. Outputting validation reports in CSV and summary text format.
9. Keeping all validation evidence auditable.

### 3.2 Out of Scope

Phase 2 does **not** include:

1. PostgreSQL schema migration.
2. Table/index/view/sequence creation.
3. Data migration.
4. Performance tuning.
5. Business result correctness verification.
6. QA regression testing.
7. Automatic modification of business code.
8. Runtime AI-driven SQL repair.
9. Direct execution on production.

---

## 4. Final Phase 2 Layering

Phase 2 is split into four independently executable layers.

```text
Phase 2.1  PostgreSQL EXPLAIN Validation
Phase 2.2  SELECT Smoke Execution
Phase 2.3  DML Safety Validation
Phase 2.4  Controlled Real Execution Validation
```

Each layer must be runnable separately through the Spring Boot CLI phase mechanism.

---

## 5. CLI Phase Names

The following phase names should be added to `ValidatorPhase`:

| Phase | CLI Value | Purpose |
|---|---|---|
| Phase 2.1 | `validation-explain` | Run PostgreSQL `EXPLAIN` validation. |
| Phase 2.2 | `validation-select-smoke` | Execute SELECT statements in a safe smoke mode. |
| Phase 2.3 | `validation-dml-safety` | Validate DML statements using safe policies, normally EXPLAIN only. |
| Phase 2.4 | `real-execution` | Controlled real execution, disabled by default. |

Example commands:

```bash
java -jar sql-postgres-validator.jar --validator.phase=validation-explain
```

```bash
java -jar sql-postgres-validator.jar --validator.phase=validation-select-smoke
```

```bash
java -jar sql-postgres-validator.jar --validator.phase=validation-dml-safety
```

```bash
java -jar sql-postgres-validator.jar \
  --validator.phase=real-execution \
  --validator.validation.real-execution.enabled=true \
  --validator.validation.real-execution.confirm-token=I_UNDERSTAND_THIS_CAN_CHANGE_DATA
```

---

## 6. Inputs and Outputs

### 6.1 Inputs

Phase 2 reads:

| Input | Source | Required |
|---|---|---:|
| `sql-inventory.csv` | Phase 1 | Yes |
| `sql-sanity-report.csv` | Phase 1.5 | Recommended |
| Java source roots | Workspace source code | Required for parameter type resolving |
| PostgreSQL connection config | `application.yml` / CLI | Required for execution phases |

### 6.2 Outputs

Phase 2 outputs:

| Output | Description |
|---|---|
| `sql-binding-plan.csv` | Shows how every SQL parameter is mapped and bound. |
| `sql-execution-report.csv` | Shows validation result for each SQL. |
| `sql-execution-summary.txt` | Human-readable summary. |
| `sql-execution-errors.csv` | Optional filtered report for failures only. |
| `sql-real-execution-audit.csv` | Required for Phase 2.4 real execution. |

The binding plan is important because it explains **how the SQL was tested**.

---

## 7. Success Definition

A SQL passing Phase 2 does **not** mean business correctness.

### 7.1 What Phase 2 Can Prove

Phase 2 can prove:

1. SQL can be parsed by PostgreSQL.
2. Referenced objects are available under the configured schema/search path.
3. Referenced columns can be resolved.
4. Referenced functions/operators can be resolved.
5. Mock parameters can be bound.
6. PostgreSQL can generate a plan or execute the statement under the selected validation mode.

### 7.2 What Phase 2 Cannot Prove

Phase 2 cannot prove:

1. The SQL returns correct business rows.
2. The SQL implements correct business logic.
3. The SQL performs well under production data volume.
4. Application behavior is correct after migration.
5. Frontend or API regression has passed.

Final business correctness must still be covered by QA regression, targeted business tests, and user acceptance testing.

---

## 8. Statement Type Classification

Before validation, every SQL must be classified.

| Statement Type | Default Handling |
|---|---|
| `SELECT` | EXPLAIN + optional safe smoke execution. |
| `RISKY_SELECT` | EXPLAIN only or skip smoke execution. |
| `INSERT` | EXPLAIN only by default. |
| `UPDATE` | EXPLAIN only by default. |
| `DELETE` | EXPLAIN only by default. |
| `MERGE` | EXPLAIN only by default. |
| `CALL` | Skipped by default. |
| `DDL` | Skipped by default. |
| `TRUNCATE` | Skipped by default. |
| `UNKNOWN` | Skipped by default. |

### 8.1 Risky SELECT

Not every `SELECT` is harmless. The tool must detect risky SELECT patterns and avoid blind execution.

Examples:

```text
nextval(
setval(
pg_advisory_lock
select ... into
call inside select
known write-capable functions
```

A risky SELECT should be marked:

```text
RISKY_SELECT_FUNCTION
```

and should not be executed in Phase 2.2 unless explicitly allowed.

---

## 9. Parameter Strategy

Parameter resolving is the core of Phase 2.

The tool must not simply count placeholders and generate random values. It must build a structured `BindingPlan`.

### 9.0 Core Technical Stack

Phase 2 must use the following technical stack and responsibility split. This is important because the current project is a Java / JDK 17 / Spring Boot CLI tool, and the implementation should remain consistent with the existing codebase under `org.rosetta.sqlvalidator`.

| Component | Responsibility |
|---|---|
| JavaParser | Read Repository method signatures, `@Param` annotations, `EntityManager#setParameter(...)`, `JdbcTemplate` call sites, local variables, and method arguments. |
| Spring JDBC `NamedParameterUtils` | Handle `:name` named parameters and Collection parameter expansion, including `IN (:ids)` style SQL. |
| Custom `JpaIndexedParameterParser` | Handle JPA indexed positional parameters such as `?1`, `?2`, including repeated occurrences of the same index. |
| Custom `MockValueFactory` | Generate mock parameter values based on Java type first and SQL context second. |
| PostgreSQL JDBC Driver | Run `EXPLAIN`, SELECT smoke execution, and controlled real execution. |
| Custom `BindingPlanReport` | Output the parameter binding process for audit, troubleshooting, and manual correction. |

This means Phase 2 should not be implemented as a black-box random-parameter executor. The binding plan is a first-class output and must be reviewable before or after execution.

### 9.1 Supported Parameter Styles

| Style | Example | Required Handling |
|---|---|---|
| Named parameter | `:fieldNames` | Resolve by name and type. |
| JPA indexed positional | `?1`, `?2` | Resolve by repository method parameter index. |
| JDBC ordinal positional | `?`, `?` | Resolve by call-site argument order where possible. |
| Collection parameter | `IN :ids`, `IN (:ids)` | Expand to JDBC placeholders. |
| EntityManager setParameter | `query.setParameter("name", value)` | Resolve from setter calls. |
| JdbcTemplate arguments | `jdbcTemplate.query(sql, ..., args)` | Resolve from call-site arguments where possible. |

### 9.2 Parameter Resolving Priority

The resolver should use the following priority:

1. Repository method signature and `@Param` annotations.
2. JPA indexed parameter position in method signature.
3. `EntityManager#setParameter(...)` calls.
4. `MapSqlParameterSource#addValue(...)` calls.
5. `JdbcTemplate` method arguments / `Object[]` arguments.
6. Java variable type in the same method.
7. SQL context heuristic.
8. Default fallback mock value.

### 9.3 Parameter Confidence

Every binding must have a confidence level.

| Confidence | Meaning |
|---|---|
| `HIGH` | Java type is directly resolved from method signature or explicit value. |
| `MEDIUM` | Type inferred from local variable or SQL context. |
| `LOW` | Type unknown, fallback value used. |
| `MANUAL_REQUIRED` | Cannot safely generate a value. |

Failures caused by low-confidence parameters must not be blindly reported as SQL compatibility failures.

---

## 10. Binding Plan

The tool must generate a binding plan before executing SQL.

### 10.1 Binding Plan Example for Named Collection

Original SQL:

```sql
SELECT DISTINCT FIELD_NAME
FROM DATA_MODEL df
WHERE df.FIELD_NAME IN :fieldNames
AND df.CURRENT_INDICATOR = 1
```

Java method:

```java
List<String> findFields(@Param("fieldNames") List<String> fieldNames);
```

Binding plan:

```text
parameterMode=NAMED
originalParameter=:fieldNames
javaParameterName=fieldNames
javaType=List<String>
parameterKind=COLLECTION
elementType=String
collectionSizeUsed=1
jdbcSql=... IN (?) ...
bindings[1]=TEST
confidence=HIGH
```

### 10.2 Binding Plan Example for JPA Indexed Positional

Original SQL:

```sql
WHERE a = ?1 AND b = ?1 AND c = ?2
```

Java method:

```java
List<X> findSomething(Long sourceId, String status);
```

Binding plan:

```text
?1 -> sourceId -> Long -> 1L
?2 -> status   -> String -> TEST
```

JDBC SQL:

```sql
WHERE a = ? AND b = ? AND c = ?
```

JDBC bindings:

```text
1 -> valueOf(?1)
2 -> valueOf(?1)
3 -> valueOf(?2)
```

### 10.3 Binding Plan Example for JDBC Ordinal

Original SQL:

```sql
WHERE a = ? AND b = ?
```

Call site:

```java
jdbcTemplate.query(sql, rowMapper, sourceId, status);
```

Binding plan:

```text
1 -> sourceId -> inferred Long
2 -> status   -> inferred String
```

---

## 11. Named Parameter and Collection Handling

For named parameters and collection expansion, the implementation should prefer Spring JDBC's `NamedParameterUtils` where possible.

However, the tool must normalize this common Spring/JPA style:

```sql
IN :fieldNames
```

into:

```sql
IN (:fieldNames)
```

before using named parameter expansion.

Default collection size for validation should be:

```yaml
validator:
  validation:
    parameter:
      default-collection-size: 1
```

Using one value is enough for syntax, object, function, and type validation. Larger collection sizes can be configured later.

---

## 12. Mock Value Generation

The mock value generator should use Java type first and SQL context second.

| Java Type | Default Mock Value |
|---|---|
| `String` | `TEST` |
| `Long` / `long` | `1L` |
| `Integer` / `int` | `1` |
| `Short` / `short` | `1` |
| `BigDecimal` | `BigDecimal.ONE` |
| `Boolean` / `boolean` | `true` |
| `LocalDate` | `LocalDate.now()` |
| `LocalDateTime` | `LocalDateTime.now()` |
| `Date` | `new Date()` |
| `Timestamp` | `Timestamp.from(Instant.now())` |
| `UUID` | `00000000-0000-0000-0000-000000000001` |
| `Enum` | first enum constant where resolvable, otherwise `TEST` |
| `Collection<String>` | `[TEST]` |
| `Collection<Long>` | `[1L]` |
| unknown | SQL context heuristic, then `TEST` |

Parameter name heuristics may include:

| Name Pattern | Suggested Type |
|---|---|
| ends with `Id` | Long, unless Java type says otherwise |
| contains `date` | LocalDate |
| contains `time` | LocalDateTime / Timestamp |
| contains `flag` / `enabled` / `active` | Boolean |
| contains `uuid` | UUID |
| contains `name` / `status` / `type` | String |

---

## 13. Validation Modes

### 13.1 Phase 2.1 - PostgreSQL EXPLAIN Validation

This is the first real PostgreSQL validation layer.

Execution:

```sql
EXPLAIN <jdbc_sql>
```

Rules:

1. Do not use `EXPLAIN ANALYZE`.
2. Bind all parameters using the binding plan.
3. Use statement timeout.
4. Use transaction rollback as safety guard.
5. Applicable to SELECT and DML statements.
6. DDL/TRUNCATE/CALL are skipped by default.

`EXPLAIN` validates more than offline parsing because PostgreSQL must resolve objects, columns, operators, functions, types, and plan generation.

### 13.2 Phase 2.2 - SELECT Smoke Execution

This layer executes only safe SELECT statements.

Rules:

1. Only execute `READ_ONLY_SELECT`.
2. Skip `RISKY_SELECT` by default.
3. Use `connection.setReadOnly(true)` where supported.
4. Use transaction rollback.
5. Set `statement_timeout`.
6. Set `PreparedStatement#setMaxRows(1)`.
7. Returning zero rows is considered OK.

Success means the SQL can execute, not that the business result is correct.

### 13.3 Phase 2.3 - DML Safety Validation

DML statements are validated but not executed by default.

Rules:

1. Use EXPLAIN only.
2. Do not execute UPDATE/DELETE/INSERT/MERGE.
3. Report DML execution as skipped for safety.
4. Keep this phase repeatable.

### 13.4 Phase 2.4 - Controlled Real Execution

This mode is planned real execution and must be separately enabled.

Rules:

1. Disabled by default.
2. Requires explicit configuration flag.
3. Requires confirmation token.
4. Supports include/exclude SQL IDs.
5. Supports include/exclude statement types.
6. Records audit output.
7. Default transaction behavior should be rollback, not commit.
8. Commit must require explicit configuration.
9. DDL/TRUNCATE/CALL remain skipped unless explicitly whitelisted.

This phase should only be run after migration owner approval and database refresh/rollback plan confirmation.

---

## 14. Transaction and Safety Policy

Every executed SQL should run inside its own transaction.

Default behavior:

| Mode | Transaction | Commit? |
|---|---|---:|
| EXPLAIN | Yes | No, rollback |
| SELECT smoke | Yes | No, rollback |
| DML safety | Yes | No, rollback |
| Real execution | Yes | Rollback by default |

Real commit must be explicitly enabled and should be used only with approval.

---

## 15. PostgreSQL Environment Configuration

The tool must not assume default schema behavior.

Required configuration:

```yaml
validator:
  validation:
    postgres:
      jdbc-url: jdbc:postgresql://localhost:5432/appdb
      username: app_user
      password: change_me
      schema: app_schema
      search-path: app_schema,public
      identifier-strategy: UNQUOTED_LOWERCASE
      statement-timeout-ms: 5000
```

Before validation, the tool should execute:

```sql
SET search_path TO app_schema, public
```

where configured.

The report should record:

```text
jdbcUrl without password
username
schema
searchPath
identifierStrategy
validationMode
```

### 15.1 Identifier Case and Quoted Identifier Risk

PostgreSQL folds unquoted identifiers to lower case. Therefore, the following unquoted references normally resolve to the same object if the table or column was created without quotes:

```sql
SELECT * FROM DATA_MODEL;
SELECT * FROM data_model;
```

However, if the migration creates Oracle-style quoted identifiers such as:

```sql
CREATE TABLE "DATA_MODEL" (...);
```

then unquoted SQL may fail with `relation does not exist` or `column does not exist`. Bulk failures of this kind must not be immediately treated as SQL compatibility problems. The migration owner must confirm the identifier strategy before Phase 2 results are interpreted.

The tool should support and record this configuration:

```yaml
validator:
  validation:
    postgres:
      identifier-strategy: UNQUOTED_LOWERCASE
```

Recommended values:

| Value | Meaning |
|---|---|
| `UNKNOWN` | Strategy has not been confirmed yet. Use caution when interpreting relation/column errors. |
| `UNQUOTED_LOWERCASE` | PostgreSQL objects are created as normal unquoted lower-case identifiers. Oracle-style upper-case SQL text should still resolve when unquoted. |
| `QUOTED_ORACLE_UPPERCASE` | Objects are created with quoted Oracle-style upper-case identifiers. Existing SQL may require quoted references or migration-side compatibility handling. |

Before Phase 2 validation, confirm with the migration owner:

1. Whether PostgreSQL table and column names are created as unquoted lower-case identifiers.
2. Whether Oracle-style upper-case names are preserved using quoted identifiers.
3. Whether `search_path` is configured correctly for the validation user.

If many SQL statements fail with `42P01` or `42703`, first check schema, search path, and identifier strategy before concluding that the SQL itself is wrong.

---

## 16. Error Classification

Error classification must use PostgreSQL SQLState first, message second.

Common SQLState mappings:

| SQLState | Category | Meaning |
|---|---|---|
| `42601` | `SYNTAX_ERROR` | SQL syntax error. |
| `42P01` | `RELATION_NOT_FOUND` | Table/view/relation does not exist. |
| `42703` | `COLUMN_NOT_FOUND` | Column does not exist. |
| `42883` | `FUNCTION_NOT_FOUND` | Function/operator does not exist. |
| `42804` | `TYPE_MISMATCH` | Datatype mismatch. |
| `42702` | `AMBIGUOUS_COLUMN` | Column reference ambiguous. |
| `42501` | `PERMISSION_DENIED` | Permission issue. |
| `57014` | `TIMEOUT` | Query canceled, often due to timeout. |
| `23505` | `UNIQUE_VIOLATION` | Unique constraint violation. |
| `23503` | `FOREIGN_KEY_VIOLATION` | FK violation. |
| Unknown | `UNKNOWN_POSTGRES_ERROR` | Needs manual review. |

Top-level result categories:

```text
PASSED
FAILED_SQL_COMPATIBILITY
FAILED_PARAMETER_BINDING
FAILED_ENVIRONMENT
SKIPPED_SAFETY
SKIPPED_MANUAL_REQUIRED
```

Fine-grained error category must also be output.

---

## 17. Output Report Fields

### 17.1 `sql-binding-plan.csv`

This is a required Phase 2 output. It explains how each parameter was resolved and bound.

Minimum required columns:

```text
sqlId
parameterMode
originalParameter
bindingIndex
javaParameterName
javaType
parameterKind
mockValue
confidence
bindingNote
```

Recommended extended columns:

```text
serviceName
className
methodName
sourceType
placeholderKind
elementType
collectionSizeUsed
bindingPlanStatus
parameterSource
```

The `sqlId` column links this report to `sql-inventory.csv`, `sql-sanity-report.csv`, and `sql-execution-report.csv`.

### 17.2 `sql-execution-report.csv`

This is a required Phase 2 output. It shows whether each SQL was tested, skipped, passed, or failed under the selected validation mode.

Minimum required columns:

```text
sqlId
jdbcSql
validationMode
executionStatus
postgresErrorCode
errorCategory
message
```

Recommended extended columns:

```text
serviceName
className
methodName
statementType
statementRiskLevel
sqlState
parameterConfidence
bindingPlanStatus
jdbcSqlPreview
executionTimeMs
rowCount
schema
searchPath
identifierStrategy
databaseUser
recommendation
```

The report must answer three questions:

1. Was this SQL tested?
2. If not tested, why was it skipped?
3. If failed, was the likely cause SQL compatibility, parameter binding, schema/search path, permission, safety policy, or environment?


### 17.3 `sql-execution-summary.txt`

Recommended summary:

```text
Total SQL records
Eligible SQL records
Skipped SQL records
EXPLAIN passed
EXPLAIN failed
SELECT smoke passed
SELECT smoke failed
DML skipped for safety
Real execution attempted
Manual parameter required
Top error categories
Top affected services
```

---

## 18. Relationship with Dashboard

The existing React dashboard should remain a separate frontend project.

Phase 2 will generate new CSV files that can be imported by the dashboard later:

```text
sql-binding-plan.csv
sql-execution-report.csv
sql-execution-summary.txt
```

Dashboard enhancement is not part of Phase 2 backend implementation, but the report columns should be stable enough to support future visualization.

---

## 19. Implementation Package Structure

Add Phase 2 under existing package:

```text
org.rosetta.sqlvalidator.validation
├── classifier
├── executor
├── model
├── parameter
├── report
├── runner
└── service
```

Do not move or rewrite existing `inventory` and `sanity` packages unless a minor compatible change is required.

---

## 20. Development Rules

All Phase 2 code must follow the current Spring Boot CLI refactor standard:

1. JDK 17.
2. Spring Boot CLI architecture.
3. SLF4J logging only.
4. No `System.out.println`.
5. Use `private static final` constants.
6. Avoid magic strings.
7. Use clear package structure.
8. Use service interfaces where useful.
9. Runner only orchestrates workflow.
10. Do not modify business service modules.
11. Do not upload company data.
12. No runtime AI dependency.
13. No automatic business code changes.
14. Safe execution is the default.
15. Real execution must be explicitly enabled.

---

## 21. Recommended Implementation Order

Implement Phase 2 in the following order:

1. Add configuration and phase enum values.
2. Add statement type detector.
3. Add SQL placeholder parser.
4. Add mock value generator.
5. Add binding plan model and CSV writer.
6. Add JPA indexed positional binding planner.
7. Add named parameter binding planner using Spring JDBC support.
8. Add basic Java method parameter resolver using existing JavaParser source scanning.
9. Add PostgreSQL connection and EXPLAIN executor.
10. Add SQLState error classifier.
11. Add Phase 2.1 runner.
12. Validate Phase 2.1 first.
13. Add Phase 2.2 SELECT smoke runner.
14. Add Phase 2.3 DML safety runner.
15. Add Phase 2.4 real execution runner with strict guard.

Do not implement everything in one uncontrolled change.

---

## 22. Copilot Implementation Prompt

Use the following prompt when asking Copilot to implement Phase 2:

```text
We have an existing working JDK 17 Spring Boot CLI project named SQL-Postgres-Validator.

Phase 1 and Phase 1.5 are already working. Do not rewrite them.

Please implement Phase 2 PostgreSQL Execution Validation following:
- docs/10_Phase2_PostgreSQL_Validation_Final_Design.md
- docs/11_Phase2_PostgreSQL_Validation_Code_Guide.md

Requirements:
1. Add new phase values only.
2. Add Phase 2 under org.rosetta.sqlvalidator.validation.
3. Keep runners separately executable by validator.phase.
4. Implement BindingPlan before execution.
5. Use PostgreSQL JDBC and Spring JDBC named parameter utilities where appropriate.
6. Use SQLState-based error classification.
7. Default to safe execution.
8. DML must not be truly executed by default.
9. Real execution must be disabled by default and require confirm token.
10. Use SLF4J logging.
11. Follow existing project coding style.
12. Do not modify business service modules.
13. Do not connect to production.
14. Do not add runtime AI dependency.
```

---

## 23. Final Decision

Phase 2 is finalized as a **Spring Boot CLI based PostgreSQL validation pipeline** with four controlled execution layers:

```text
2.1 EXPLAIN validation
2.2 SELECT smoke execution
2.3 DML safety validation
2.4 controlled real execution
```

The most important deliverable is not only pass/fail. The most important deliverables are:

```text
sql-binding-plan.csv
sql-execution-report.csv
sql-execution-summary.txt
```

These reports allow the migration team to understand what was tested, how parameters were generated, what passed, what failed, and what must be manually reviewed.
