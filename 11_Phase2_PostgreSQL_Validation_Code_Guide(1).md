# 11 - Phase 2 PostgreSQL Execution Validation Code Guide

## 1. Purpose

This document provides the implementation guide for adding Phase 2 PostgreSQL Execution Validation into the existing `SQL-Postgres-Validator` Spring Boot CLI project.

This is a code guide, not a request to rewrite the existing project.

Existing Phase 1 and Phase 1.5 functionality must remain working.

---

## 2. Implementation Boundary

This guide adds:

```text
Phase 2.1 validation-explain
Phase 2.2 validation-select-smoke
Phase 2.3 validation-dml-safety
Phase 2.4 real-execution
```

This guide does not add:

```text
Business code modification
Automatic SQL source rewrite
Production execution
Runtime AI dependency
Dashboard changes
```

---

## 3. Dependencies

Add PostgreSQL and Spring JDBC support to `pom.xml` if not already available.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

`spring-boot-starter-jdbc` includes JDBC infrastructure and HikariCP. It also gives access to Spring JDBC named parameter utilities.

Do not remove existing dependencies used by Phase 1 and Phase 1.5, such as JavaParser, Commons CSV, and JSqlParser.

---

## 3.1 Phase 2 Core Technical Stack

The current project is a Java / JDK 17 / Spring Boot CLI tool under `org.rosetta.sqlvalidator`. Phase 2 must continue this architecture and use the following stack.

| Component | Responsibility |
|---|---|
| JavaParser | Read Repository method signatures, `@Param` annotations, `EntityManager#setParameter(...)`, `JdbcTemplate` call sites, local variables, and method arguments. |
| Spring JDBC `NamedParameterUtils` | Handle `:name` named parameters and Collection parameter expansion. |
| Custom `JpaIndexedParameterParser` | Handle JPA indexed positional parameters such as `?1` and `?2`. |
| Custom `MockValueFactory` | Generate mock values from Java type first and SQL context second. |
| PostgreSQL JDBC Driver | Run `EXPLAIN`, SELECT smoke, DML safety validation, and controlled real execution. |
| Custom `BindingPlanReport` | Output the parameter binding process for audit and manual correction. |

Do not replace this with a random parameter generator. The binding plan is a required intermediate result.

---

## 4. Package Structure

Add the following packages:

```text
src/main/java/org/rosetta/sqlvalidator/validation
├── classifier
│   └── PostgresErrorClassifier.java
├── executor
│   ├── PostgresExecutionTemplate.java
│   ├── PostgresExplainExecutor.java
│   ├── SelectSmokeExecutor.java
│   ├── DmlSafetyExecutor.java
│   └── ControlledRealExecutionExecutor.java
├── model
│   ├── BindingPlan.java
│   ├── BindingPlanStatus.java
│   ├── BindingValue.java
│   ├── ErrorCategory.java
│   ├── ExecutionStatus.java
│   ├── IdentifierStrategy.java
│   ├── ParameterConfidence.java
│   ├── ParameterKind.java
│   ├── Placeholder.java
│   ├── PlaceholderKind.java
│   ├── SqlExecutionResult.java
│   ├── StatementType.java
│   └── ValidationMode.java
├── parameter
│   ├── BindingPlanBuilder.java
│   ├── JavaSourceParameterResolver.java
│   ├── JdbcSqlBindingPlanner.java
│   ├── JpaIndexedParameterBindingPlanner.java
│   ├── MockValueFactory.java
│   ├── NamedParameterBindingPlanner.java
│   ├── SqlContextTypeGuesser.java
│   └── SqlPlaceholderParser.java
├── report
│   ├── BindingPlanCsvWriter.java
│   ├── ExecutionReportCsvWriter.java
│   └── ExecutionSummaryWriter.java
├── runner
│   ├── ExplainValidationRunner.java
│   ├── SelectSmokeValidationRunner.java
│   ├── DmlSafetyValidationRunner.java
│   └── RealExecutionRunner.java
└── service
    ├── PostgresValidationService.java
    └── DefaultPostgresValidationService.java
```

---

## 5. Update `ValidatorPhase`

Add Phase 2 values to the existing enum.

```java
package org.rosetta.sqlvalidator.runner;

public enum ValidatorPhase {

    INVENTORY("inventory"),
    SANITY("sanity"),
    VALIDATION_EXPLAIN("validation-explain"),
    VALIDATION_SELECT_SMOKE("validation-select-smoke"),
    VALIDATION_DML_SAFETY("validation-dml-safety"),
    REAL_EXECUTION("real-execution");

    private final String code;

    ValidatorPhase(final String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

If the current enum already exists, only add the new values and keep the existing style.

---

## 6. Update `application.yml`

Add Phase 2 configuration.

```yaml
validator:
  phase: inventory

  source-roots:
    - ../50718-RegistryService/src/main/java
    - ../50718-WorkflowService/src/main/java
    - ../50718-SharedModule/src/main/java

  output-dir: ./output

  inventory:
    output-file: ./output/sql-inventory.csv
    summary-file: ./output/scan-summary.txt

  sanity:
    input-file: ./output/sql-inventory.csv
    report-file: ./output/sql-sanity-report.csv
    summary-file: ./output/sql-sanity-summary.txt

  validation:
    inventory-file: ./output/sql-inventory.csv
    sanity-report-file: ./output/sql-sanity-report.csv
    binding-plan-file: ./output/sql-binding-plan.csv
    execution-report-file: ./output/sql-execution-report.csv
    execution-summary-file: ./output/sql-execution-summary.txt

    postgres:
      jdbc-url: jdbc:postgresql://localhost:5432/appdb
      username: app_user
      password: change_me
      schema: app_schema
      search-path: app_schema,public
      identifier-strategy: UNQUOTED_LOWERCASE
      statement-timeout-ms: 5000

    parameter:
      default-collection-size: 1
      fail-on-low-confidence: false

    safety:
      allow-risky-select-execution: false
      allow-dml-execution: false
      skip-ddl: true
      skip-truncate: true
      skip-call: true

    real-execution:
      enabled: false
      confirm-token: ""
      required-confirm-token: I_UNDERSTAND_THIS_CAN_CHANGE_DATA
      rollback-after-each-sql: true
      allow-commit: false
      include-sql-ids: []
      exclude-sql-ids: []
      include-statement-types: []
      exclude-statement-types:
        - DDL
        - TRUNCATE
        - CALL
```

Do not commit real credentials.

---

## 7. Extend `ValidatorProperties`

Add nested validation properties to the existing configuration class. If the current project already uses a different structure, adapt the fields but keep the same property names.

```java
package org.rosetta.sqlvalidator.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "validator")
public class ValidatorProperties {

    private String phase;
    private List<String> sourceRoots = new ArrayList<>();
    private String outputDir;
    private Validation validation = new Validation();

    public static class Validation {
        private String inventoryFile;
        private String sanityReportFile;
        private String bindingPlanFile;
        private String executionReportFile;
        private String executionSummaryFile;
        private Postgres postgres = new Postgres();
        private Parameter parameter = new Parameter();
        private Safety safety = new Safety();
        private RealExecution realExecution = new RealExecution();

        // getters and setters
    }

    public static class Postgres {
        private String jdbcUrl;
        private String username;
        private String password;
        private String schema;
        private String searchPath;
        private String identifierStrategy = "UNKNOWN";
        private long statementTimeoutMs = 5000L;

        // getters and setters
    }

    public static class Parameter {
        private int defaultCollectionSize = 1;
        private boolean failOnLowConfidence;

        // getters and setters
    }

    public static class Safety {
        private boolean allowRiskySelectExecution;
        private boolean allowDmlExecution;
        private boolean skipDdl = true;
        private boolean skipTruncate = true;
        private boolean skipCall = true;

        // getters and setters
    }

    public static class RealExecution {
        private boolean enabled;
        private String confirmToken;
        private String requiredConfirmToken = "I_UNDERSTAND_THIS_CAN_CHANGE_DATA";
        private boolean rollbackAfterEachSql = true;
        private boolean allowCommit;
        private List<String> includeSqlIds = new ArrayList<>();
        private List<String> excludeSqlIds = new ArrayList<>();
        private List<String> includeStatementTypes = new ArrayList<>();
        private List<String> excludeStatementTypes = new ArrayList<>();

        // getters and setters
    }

    // getters and setters
}
```

Use the IDE to generate getters and setters. Keep the existing properties already used by Phase 1 and Phase 1.5.

---

## 8. Validation Model Classes

### 8.1 `ValidationMode`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum ValidationMode {
    EXPLAIN,
    SELECT_SMOKE,
    DML_SAFETY,
    REAL_EXECUTION
}
```

### 8.2 `StatementType`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum StatementType {
    SELECT,
    RISKY_SELECT,
    INSERT,
    UPDATE,
    DELETE,
    MERGE,
    CALL,
    DDL,
    TRUNCATE,
    UNKNOWN
}
```

### 8.3 `PlaceholderKind`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum PlaceholderKind {
    NAMED,
    JPA_INDEXED_POSITIONAL,
    JDBC_ORDINAL_POSITIONAL
}
```

### 8.4 `ParameterKind`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum ParameterKind {
    SCALAR,
    COLLECTION,
    UNKNOWN
}
```

### 8.5 `ParameterConfidence`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum ParameterConfidence {
    HIGH,
    MEDIUM,
    LOW,
    MANUAL_REQUIRED
}
```

### 8.6 `BindingPlanStatus`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum BindingPlanStatus {
    READY,
    READY_WITH_LOW_CONFIDENCE,
    MANUAL_REQUIRED,
    UNSUPPORTED_PARAMETER_STYLE,
    EMPTY_SQL,
    SKIPPED_DYNAMIC_SQL
}
```

### 8.7 `ExecutionStatus`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum ExecutionStatus {
    PASSED,
    FAILED_SQL_COMPATIBILITY,
    FAILED_PARAMETER_BINDING,
    FAILED_ENVIRONMENT,
    SKIPPED_SAFETY,
    SKIPPED_MANUAL_REQUIRED,
    SKIPPED_UNSUPPORTED,
    NOT_EXECUTED
}
```

### 8.8 `IdentifierStrategy`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum IdentifierStrategy {
    UNKNOWN,
    UNQUOTED_LOWERCASE,
    QUOTED_ORACLE_UPPERCASE
}
```

### 8.9 `ErrorCategory`

```java
package org.rosetta.sqlvalidator.validation.model;

public enum ErrorCategory {
    NONE,
    SYNTAX_ERROR,
    RELATION_NOT_FOUND,
    COLUMN_NOT_FOUND,
    FUNCTION_NOT_FOUND,
    TYPE_MISMATCH,
    AMBIGUOUS_COLUMN,
    PERMISSION_DENIED,
    TIMEOUT,
    UNIQUE_VIOLATION,
    FOREIGN_KEY_VIOLATION,
    PARAMETER_BINDING_ERROR,
    LOW_CONFIDENCE_PARAMETER,
    SCHEMA_OR_SEARCH_PATH_ISSUE,
    DML_SKIPPED_FOR_SAFETY,
    DDL_SKIPPED_FOR_SAFETY,
    TRUNCATE_SKIPPED_FOR_SAFETY,
    CALL_SKIPPED_FOR_SAFETY,
    RISKY_SELECT_SKIPPED_FOR_SAFETY,
    UNKNOWN_POSTGRES_ERROR,
    UNKNOWN
}
```

---

## 9. Core Data Models

### 9.1 `Placeholder`

```java
package org.rosetta.sqlvalidator.validation.model;

public class Placeholder {

    private final PlaceholderKind kind;
    private final String originalText;
    private final String name;
    private final Integer index;
    private final int occurrenceIndex;

    public Placeholder(
            final PlaceholderKind kind,
            final String originalText,
            final String name,
            final Integer index,
            final int occurrenceIndex) {
        this.kind = kind;
        this.originalText = originalText;
        this.name = name;
        this.index = index;
        this.occurrenceIndex = occurrenceIndex;
    }

    public PlaceholderKind getKind() {
        return kind;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getName() {
        return name;
    }

    public Integer getIndex() {
        return index;
    }

    public int getOccurrenceIndex() {
        return occurrenceIndex;
    }
}
```

### 9.2 `BindingValue`

```java
package org.rosetta.sqlvalidator.validation.model;

public class BindingValue {

    private final int bindingIndex;
    private final String sourceParameter;
    private final String javaParameterName;
    private final String javaType;
    private final ParameterKind parameterKind;
    private final String elementType;
    private final Object value;
    private final ParameterConfidence confidence;
    private final String note;

    public BindingValue(
            final int bindingIndex,
            final String sourceParameter,
            final String javaParameterName,
            final String javaType,
            final ParameterKind parameterKind,
            final String elementType,
            final Object value,
            final ParameterConfidence confidence,
            final String note) {
        this.bindingIndex = bindingIndex;
        this.sourceParameter = sourceParameter;
        this.javaParameterName = javaParameterName;
        this.javaType = javaType;
        this.parameterKind = parameterKind;
        this.elementType = elementType;
        this.value = value;
        this.confidence = confidence;
        this.note = note;
    }

    public int getBindingIndex() {
        return bindingIndex;
    }

    public String getSourceParameter() {
        return sourceParameter;
    }

    public String getJavaParameterName() {
        return javaParameterName;
    }

    public String getJavaType() {
        return javaType;
    }

    public ParameterKind getParameterKind() {
        return parameterKind;
    }

    public String getElementType() {
        return elementType;
    }

    public Object getValue() {
        return value;
    }

    public ParameterConfidence getConfidence() {
        return confidence;
    }

    public String getNote() {
        return note;
    }
}
```

### 9.3 `BindingPlan`

```java
package org.rosetta.sqlvalidator.validation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BindingPlan {

    private final String sqlId;
    private final String originalSql;
    private final String jdbcSql;
    private final String parameterMode;
    private final StatementType statementType;
    private final BindingPlanStatus status;
    private final List<BindingValue> bindingValues;
    private final String note;

    public BindingPlan(
            final String sqlId,
            final String originalSql,
            final String jdbcSql,
            final String parameterMode,
            final StatementType statementType,
            final BindingPlanStatus status,
            final List<BindingValue> bindingValues,
            final String note) {
        this.sqlId = sqlId;
        this.originalSql = originalSql;
        this.jdbcSql = jdbcSql;
        this.parameterMode = parameterMode;
        this.statementType = statementType;
        this.status = status;
        this.bindingValues = new ArrayList<>(bindingValues);
        this.note = note;
    }

    public String getSqlId() {
        return sqlId;
    }

    public String getOriginalSql() {
        return originalSql;
    }

    public String getJdbcSql() {
        return jdbcSql;
    }

    public String getParameterMode() {
        return parameterMode;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public BindingPlanStatus getStatus() {
        return status;
    }

    public List<BindingValue> getBindingValues() {
        return Collections.unmodifiableList(bindingValues);
    }

    public String getNote() {
        return note;
    }
}
```

### 9.4 `SqlExecutionResult`

```java
package org.rosetta.sqlvalidator.validation.model;

public class SqlExecutionResult {

    private final String sqlId;
    private final String jdbcSql;
    private final ValidationMode validationMode;
    private final StatementType statementType;
    private final ExecutionStatus executionStatus;
    private final ErrorCategory errorCategory;
    private final String sqlState;
    private final String postgresErrorCode;
    private final String message;
    private final long executionTimeMs;
    private final int rowCount;
    private final String recommendation;

    public SqlExecutionResult(
            final String sqlId,
            final String jdbcSql,
            final ValidationMode validationMode,
            final StatementType statementType,
            final ExecutionStatus executionStatus,
            final ErrorCategory errorCategory,
            final String sqlState,
            final String postgresErrorCode,
            final String message,
            final long executionTimeMs,
            final int rowCount,
            final String recommendation) {
        this.sqlId = sqlId;
        this.jdbcSql = jdbcSql;
        this.validationMode = validationMode;
        this.statementType = statementType;
        this.executionStatus = executionStatus;
        this.errorCategory = errorCategory;
        this.sqlState = sqlState;
        this.postgresErrorCode = postgresErrorCode;
        this.message = message;
        this.executionTimeMs = executionTimeMs;
        this.rowCount = rowCount;
        this.recommendation = recommendation;
    }

    public String getSqlId() {
        return sqlId;
    }

    public String getJdbcSql() {
        return jdbcSql;
    }

    public ValidationMode getValidationMode() {
        return validationMode;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }

    public String getSqlState() {
        return sqlState;
    }

    public String getPostgresErrorCode() {
        return postgresErrorCode;
    }

    public String getMessage() {
        return message;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public int getRowCount() {
        return rowCount;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
```

---

## 10. Statement Type Detection

Create a detector inside `validation.parameter` or `validation.executor`.

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.Locale;
import org.rosetta.sqlvalidator.validation.model.StatementType;
import org.springframework.stereotype.Component;

@Component
public class StatementTypeDetector {

    private static final String SELECT = "select";
    private static final String INSERT = "insert";
    private static final String UPDATE = "update";
    private static final String DELETE = "delete";
    private static final String MERGE = "merge";
    private static final String CALL = "call";
    private static final String TRUNCATE = "truncate";

    public StatementType detect(final String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return StatementType.UNKNOWN;
        }
        final String normalized = removeLeadingComments(sql).trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(SELECT)) {
            return containsRiskySelectPattern(normalized) ? StatementType.RISKY_SELECT : StatementType.SELECT;
        }
        if (normalized.startsWith(INSERT)) {
            return StatementType.INSERT;
        }
        if (normalized.startsWith(UPDATE)) {
            return StatementType.UPDATE;
        }
        if (normalized.startsWith(DELETE)) {
            return StatementType.DELETE;
        }
        if (normalized.startsWith(MERGE)) {
            return StatementType.MERGE;
        }
        if (normalized.startsWith(CALL)) {
            return StatementType.CALL;
        }
        if (normalized.startsWith(TRUNCATE)) {
            return StatementType.TRUNCATE;
        }
        if (isDdl(normalized)) {
            return StatementType.DDL;
        }
        return StatementType.UNKNOWN;
    }

    private boolean containsRiskySelectPattern(final String sql) {
        return sql.contains("nextval(")
                || sql.contains("setval(")
                || sql.contains("pg_advisory_lock")
                || sql.matches("(?s).*select\\s+.*\\s+into\\s+.*");
    }

    private boolean isDdl(final String sql) {
        return sql.startsWith("create ")
                || sql.startsWith("alter ")
                || sql.startsWith("drop ")
                || sql.startsWith("comment ")
                || sql.startsWith("grant ")
                || sql.startsWith("revoke ");
    }

    private String removeLeadingComments(final String sql) {
        return sql.replaceFirst("(?s)^\\s*/\\*.*?\\*/", "")
                .replaceFirst("(?m)^\\s*--.*$", "");
    }
}
```

---

## 11. SQL Placeholder Parser

The parser must detect:

```text
:name
?1 / ?2
?
```

It must avoid treating PostgreSQL casts like `::date` as named parameters.

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.ArrayList;
import java.util.List;
import org.rosetta.sqlvalidator.validation.model.Placeholder;
import org.rosetta.sqlvalidator.validation.model.PlaceholderKind;
import org.springframework.stereotype.Component;

@Component
public class SqlPlaceholderParser {

    public List<Placeholder> parse(final String sql) {
        final List<Placeholder> placeholders = new ArrayList<>();
        if (sql == null || sql.isEmpty()) {
            return placeholders;
        }

        boolean inSingleQuote = false;
        int occurrenceIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char current = sql.charAt(i);
            if (current == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (inSingleQuote) {
                continue;
            }

            if (current == ':' && isNamedParameterStart(sql, i)) {
                final int start = i + 1;
                int end = start;
                while (end < sql.length() && isParameterNameChar(sql.charAt(end))) {
                    end++;
                }
                final String name = sql.substring(start, end);
                placeholders.add(new Placeholder(
                        PlaceholderKind.NAMED,
                        ":" + name,
                        name,
                        null,
                        ++occurrenceIndex));
                i = end - 1;
                continue;
            }

            if (current == '?') {
                final int start = i + 1;
                int end = start;
                while (end < sql.length() && Character.isDigit(sql.charAt(end))) {
                    end++;
                }
                if (end > start) {
                    final Integer index = Integer.valueOf(sql.substring(start, end));
                    placeholders.add(new Placeholder(
                            PlaceholderKind.JPA_INDEXED_POSITIONAL,
                            sql.substring(i, end),
                            null,
                            index,
                            ++occurrenceIndex));
                    i = end - 1;
                } else {
                    placeholders.add(new Placeholder(
                            PlaceholderKind.JDBC_ORDINAL_POSITIONAL,
                            "?",
                            null,
                            null,
                            ++occurrenceIndex));
                }
            }
        }
        return placeholders;
    }

    private boolean isNamedParameterStart(final String sql, final int colonIndex) {
        if (colonIndex + 1 >= sql.length()) {
            return false;
        }
        if (colonIndex > 0 && sql.charAt(colonIndex - 1) == ':') {
            return false;
        }
        if (colonIndex + 1 < sql.length() && sql.charAt(colonIndex + 1) == ':') {
            return false;
        }
        return Character.isJavaIdentifierStart(sql.charAt(colonIndex + 1));
    }

    private boolean isParameterNameChar(final char value) {
        return Character.isJavaIdentifierPart(value);
    }
}
```

---

## 12. Mock Value Factory

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockValueFactory {

    private static final String DEFAULT_STRING = "TEST";
    private static final Long DEFAULT_LONG = 1L;
    private static final Integer DEFAULT_INTEGER = 1;
    private static final UUID DEFAULT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public Object createScalarValue(final String javaType, final String parameterName) {
        final String type = javaType == null ? "" : javaType.toLowerCase(Locale.ROOT);
        if (type.contains("string") || type.isEmpty()) {
            return guessByName(parameterName);
        }
        if (type.contains("long")) {
            return DEFAULT_LONG;
        }
        if (type.contains("integer") || type.equals("int")) {
            return DEFAULT_INTEGER;
        }
        if (type.contains("bigdecimal")) {
            return BigDecimal.ONE;
        }
        if (type.contains("boolean")) {
            return Boolean.TRUE;
        }
        if (type.contains("localdate") && !type.contains("localdatetime")) {
            return LocalDate.now();
        }
        if (type.contains("localdatetime")) {
            return LocalDateTime.now();
        }
        if (type.contains("timestamp")) {
            return Timestamp.from(Instant.now());
        }
        if (type.contains("uuid")) {
            return DEFAULT_UUID;
        }
        return guessByName(parameterName);
    }

    public List<Object> createCollectionValue(
            final String elementType,
            final String parameterName,
            final int collectionSize) {
        final List<Object> values = new ArrayList<>();
        final int safeSize = Math.max(collectionSize, 1);
        for (int index = 0; index < safeSize; index++) {
            values.add(createScalarValue(elementType, parameterName));
        }
        return values;
    }

    private Object guessByName(final String parameterName) {
        if (parameterName == null) {
            return DEFAULT_STRING;
        }
        final String name = parameterName.toLowerCase(Locale.ROOT);
        if (name.endsWith("id") || name.endsWith("ids")) {
            return DEFAULT_LONG;
        }
        if (name.contains("date")) {
            return LocalDate.now();
        }
        if (name.contains("time")) {
            return LocalDateTime.now();
        }
        if (name.contains("uuid")) {
            return DEFAULT_UUID;
        }
        if (name.contains("flag") || name.contains("enabled") || name.contains("active")) {
            return Boolean.TRUE;
        }
        return DEFAULT_STRING;
    }
}
```

---

## 13. Named Parameter Binding Planner

Use Spring JDBC named parameter support for `:name` and collection expansion.

Important: normalize `IN :param` to `IN (:param)` before expansion.

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.rosetta.sqlvalidator.validation.model.BindingValue;
import org.rosetta.sqlvalidator.validation.model.ParameterConfidence;
import org.rosetta.sqlvalidator.validation.model.ParameterKind;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Component;

@Component
public class NamedParameterBindingPlanner {

    private static final Pattern IN_WITHOUT_PARENTHESES = Pattern.compile("(?i)\\bIN\\s+:([A-Za-z][A-Za-z0-9_]*)");

    public NamedBindingResult build(
            final String sql,
            final Map<String, ResolvedParameter> resolvedParameters,
            final int defaultCollectionSize) {

        final String normalizedSql = normalizeInWithoutParentheses(sql);
        final MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        final List<BindingValue> bindingValues = new ArrayList<>();

        for (Map.Entry<String, ResolvedParameter> entry : resolvedParameters.entrySet()) {
            final String parameterName = entry.getKey();
            final ResolvedParameter parameter = entry.getValue();
            final Object value = parameter.getValue();
            parameterSource.addValue(parameterName, value);
        }

        final ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(normalizedSql);
        final String jdbcSql = NamedParameterUtils.substituteNamedParameters(parsedSql, parameterSource);
        final List<SqlParameter> declaredParameters = NamedParameterUtils.buildSqlParameterList(parsedSql, parameterSource);
        final Object[] jdbcValues = NamedParameterUtils.buildValueArray(parsedSql, parameterSource, declaredParameters);

        for (int index = 0; index < jdbcValues.length; index++) {
            bindingValues.add(new BindingValue(
                    index + 1,
                    "named",
                    null,
                    null,
                    ParameterKind.UNKNOWN,
                    null,
                    jdbcValues[index],
                    ParameterConfidence.MEDIUM,
                    "Generated by Spring NamedParameterUtils"));
        }

        return new NamedBindingResult(jdbcSql, bindingValues);
    }

    private String normalizeInWithoutParentheses(final String sql) {
        return IN_WITHOUT_PARENTHESES.matcher(sql).replaceAll("IN (:$1)");
    }
}
```

Add a small result class:

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.List;
import org.rosetta.sqlvalidator.validation.model.BindingValue;

public class NamedBindingResult {

    private final String jdbcSql;
    private final List<BindingValue> bindingValues;

    public NamedBindingResult(final String jdbcSql, final List<BindingValue> bindingValues) {
        this.jdbcSql = jdbcSql;
        this.bindingValues = bindingValues;
    }

    public String getJdbcSql() {
        return jdbcSql;
    }

    public List<BindingValue> getBindingValues() {
        return bindingValues;
    }
}
```

`ResolvedParameter` should be created by the Java source parameter resolver.

```java
package org.rosetta.sqlvalidator.validation.parameter;

import org.rosetta.sqlvalidator.validation.model.ParameterConfidence;
import org.rosetta.sqlvalidator.validation.model.ParameterKind;

public class ResolvedParameter {

    private final String name;
    private final String javaType;
    private final ParameterKind parameterKind;
    private final String elementType;
    private final Object value;
    private final ParameterConfidence confidence;

    public ResolvedParameter(
            final String name,
            final String javaType,
            final ParameterKind parameterKind,
            final String elementType,
            final Object value,
            final ParameterConfidence confidence) {
        this.name = name;
        this.javaType = javaType;
        this.parameterKind = parameterKind;
        this.elementType = elementType;
        this.value = value;
        this.confidence = confidence;
    }

    // getters
}
```

---

## 14. JPA Indexed Positional Binding Planner

Handles `?1`, `?2`, repeated indexes, and conversion to JDBC `?`.

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.rosetta.sqlvalidator.validation.model.BindingValue;
import org.rosetta.sqlvalidator.validation.model.ParameterConfidence;
import org.rosetta.sqlvalidator.validation.model.ParameterKind;
import org.springframework.stereotype.Component;

@Component
public class JpaIndexedParameterBindingPlanner {

    public IndexedBindingResult build(
            final String sql,
            final Map<Integer, ResolvedParameter> parametersByIndex) {

        final StringBuilder jdbcSql = new StringBuilder();
        final List<BindingValue> bindingValues = new ArrayList<>();
        int bindingIndex = 0;

        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            final char current = sql.charAt(i);
            if (current == '\'') {
                inSingleQuote = !inSingleQuote;
                jdbcSql.append(current);
                continue;
            }
            if (!inSingleQuote && current == '?' && i + 1 < sql.length() && Character.isDigit(sql.charAt(i + 1))) {
                int end = i + 1;
                while (end < sql.length() && Character.isDigit(sql.charAt(end))) {
                    end++;
                }
                final Integer parameterIndex = Integer.valueOf(sql.substring(i + 1, end));
                final ResolvedParameter parameter = parametersByIndex.get(parameterIndex);
                jdbcSql.append('?');
                bindingValues.add(toBindingValue(++bindingIndex, parameterIndex, parameter));
                i = end - 1;
                continue;
            }
            jdbcSql.append(current);
        }

        return new IndexedBindingResult(jdbcSql.toString(), bindingValues);
    }

    private BindingValue toBindingValue(
            final int bindingIndex,
            final Integer parameterIndex,
            final ResolvedParameter parameter) {
        if (parameter == null) {
            return new BindingValue(
                    bindingIndex,
                    "?" + parameterIndex,
                    null,
                    null,
                    ParameterKind.UNKNOWN,
                    null,
                    "TEST",
                    ParameterConfidence.LOW,
                    "Parameter index was not resolved, fallback value used");
        }
        return new BindingValue(
                bindingIndex,
                "?" + parameterIndex,
                parameter.getName(),
                parameter.getJavaType(),
                parameter.getParameterKind(),
                parameter.getElementType(),
                parameter.getValue(),
                parameter.getConfidence(),
                "Mapped from JPA indexed positional parameter");
    }
}
```

Add result class:

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.List;
import org.rosetta.sqlvalidator.validation.model.BindingValue;

public class IndexedBindingResult {

    private final String jdbcSql;
    private final List<BindingValue> bindingValues;

    public IndexedBindingResult(final String jdbcSql, final List<BindingValue> bindingValues) {
        this.jdbcSql = jdbcSql;
        this.bindingValues = bindingValues;
    }

    public String getJdbcSql() {
        return jdbcSql;
    }

    public List<BindingValue> getBindingValues() {
        return bindingValues;
    }
}
```

---

## 15. Java Source Parameter Resolver

This is the most important but also most difficult part. MVP should support common cases first.

Supported MVP cases:

```text
@Repository @Query method parameters with @Param
JPA ?1 / ?2 mapped by method parameter order
simple method parameter Java types
List<T> / Set<T> / Collection<T>
EntityManager setParameter basic cases
JdbcTemplate Object[] or varargs basic cases
```

Create interface:

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.Map;
import org.rosetta.sqlvalidator.inventory.model.NativeSqlRecord;

public interface JavaSourceParameterResolver {

    Map<String, ResolvedParameter> resolveNamedParameters(NativeSqlRecord record);

    Map<Integer, ResolvedParameter> resolveIndexedParameters(NativeSqlRecord record);

    Map<Integer, ResolvedParameter> resolveOrdinalParameters(NativeSqlRecord record);
}
```

Create initial implementation:

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.Collections;
import java.util.Map;
import org.rosetta.sqlvalidator.inventory.model.NativeSqlRecord;
import org.springframework.stereotype.Component;

@Component
public class DefaultJavaSourceParameterResolver implements JavaSourceParameterResolver {

    private final MockValueFactory mockValueFactory;

    public DefaultJavaSourceParameterResolver(final MockValueFactory mockValueFactory) {
        this.mockValueFactory = mockValueFactory;
    }

    @Override
    public Map<String, ResolvedParameter> resolveNamedParameters(final NativeSqlRecord record) {
        // MVP implementation:
        // 1. Use record.getParameterNames() from Phase 1.
        // 2. Later enhance by opening record.getFilePath() and resolving method parameters.
        // 3. If Java type cannot be resolved, fallback with LOW confidence.
        return Collections.emptyMap();
    }

    @Override
    public Map<Integer, ResolvedParameter> resolveIndexedParameters(final NativeSqlRecord record) {
        // MVP implementation:
        // Resolve ?1, ?2 from method parameter order when source method is found.
        return Collections.emptyMap();
    }

    @Override
    public Map<Integer, ResolvedParameter> resolveOrdinalParameters(final NativeSqlRecord record) {
        // MVP implementation:
        // Resolve ordinary JDBC ? parameters from JdbcTemplate call-site arguments where possible.
        return Collections.emptyMap();
    }
}
```

Important: do not try to solve every Java expression in the first implementation. Generate `MANUAL_REQUIRED` or `LOW` confidence when unclear.

---

## 16. Binding Plan Builder

This component chooses the right binding planner.

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.List;
import java.util.Map;
import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.rosetta.sqlvalidator.inventory.model.NativeSqlRecord;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.BindingPlanStatus;
import org.rosetta.sqlvalidator.validation.model.BindingValue;
import org.rosetta.sqlvalidator.validation.model.Placeholder;
import org.rosetta.sqlvalidator.validation.model.PlaceholderKind;
import org.rosetta.sqlvalidator.validation.model.StatementType;
import org.springframework.stereotype.Component;

@Component
public class BindingPlanBuilder {

    private final SqlPlaceholderParser placeholderParser;
    private final StatementTypeDetector statementTypeDetector;
    private final JavaSourceParameterResolver javaSourceParameterResolver;
    private final NamedParameterBindingPlanner namedParameterBindingPlanner;
    private final JpaIndexedParameterBindingPlanner indexedParameterBindingPlanner;
    private final JdbcSqlBindingPlanner jdbcSqlBindingPlanner;
    private final ValidatorProperties validatorProperties;

    public BindingPlanBuilder(
            final SqlPlaceholderParser placeholderParser,
            final StatementTypeDetector statementTypeDetector,
            final JavaSourceParameterResolver javaSourceParameterResolver,
            final NamedParameterBindingPlanner namedParameterBindingPlanner,
            final JpaIndexedParameterBindingPlanner indexedParameterBindingPlanner,
            final JdbcSqlBindingPlanner jdbcSqlBindingPlanner,
            final ValidatorProperties validatorProperties) {
        this.placeholderParser = placeholderParser;
        this.statementTypeDetector = statementTypeDetector;
        this.javaSourceParameterResolver = javaSourceParameterResolver;
        this.namedParameterBindingPlanner = namedParameterBindingPlanner;
        this.indexedParameterBindingPlanner = indexedParameterBindingPlanner;
        this.jdbcSqlBindingPlanner = jdbcSqlBindingPlanner;
        this.validatorProperties = validatorProperties;
    }

    public BindingPlan build(final NativeSqlRecord record) {
        final String sql = chooseEffectiveSql(record);
        final StatementType statementType = statementTypeDetector.detect(sql);
        final List<Placeholder> placeholders = placeholderParser.parse(sql);

        if (sql == null || sql.trim().isEmpty()) {
            return new BindingPlan(record.getId(), sql, null, statementType,
                    BindingPlanStatus.EMPTY_SQL, List.of(), "Empty SQL");
        }

        if (Boolean.TRUE.equals(record.getDynamicSql())) {
            return new BindingPlan(record.getId(), sql, null, statementType,
                    BindingPlanStatus.SKIPPED_DYNAMIC_SQL, List.of(), "Dynamic SQL requires manual review");
        }

        if (placeholders.isEmpty()) {
            return new BindingPlan(record.getId(), sql, sql, statementType,
                    BindingPlanStatus.READY, List.of(), "No parameters");
        }

        if (containsOnly(placeholders, PlaceholderKind.NAMED)) {
            final Map<String, ResolvedParameter> namedParameters = javaSourceParameterResolver.resolveNamedParameters(record);
            final NamedBindingResult result = namedParameterBindingPlanner.build(
                    sql,
                    namedParameters,
                    validatorProperties.getValidation().getParameter().getDefaultCollectionSize());
            return new BindingPlan(record.getId(), sql, result.getJdbcSql(), statementType,
                    determineStatus(result.getBindingValues()), result.getBindingValues(), "Named parameter binding plan");
        }

        if (containsOnly(placeholders, PlaceholderKind.JPA_INDEXED_POSITIONAL)) {
            final Map<Integer, ResolvedParameter> indexedParameters = javaSourceParameterResolver.resolveIndexedParameters(record);
            final IndexedBindingResult result = indexedParameterBindingPlanner.build(sql, indexedParameters);
            return new BindingPlan(record.getId(), sql, result.getJdbcSql(), statementType,
                    determineStatus(result.getBindingValues()), result.getBindingValues(), "JPA indexed positional binding plan");
        }

        if (containsOnly(placeholders, PlaceholderKind.JDBC_ORDINAL_POSITIONAL)) {
            return jdbcSqlBindingPlanner.build(record, sql, statementType);
        }

        return new BindingPlan(record.getId(), sql, null, statementType,
                BindingPlanStatus.UNSUPPORTED_PARAMETER_STYLE, List.of(), "Mixed parameter styles are not supported in MVP");
    }

    private String chooseEffectiveSql(final NativeSqlRecord record) {
        if (record.getNormalizedSqlText() != null && !record.getNormalizedSqlText().isBlank()) {
            return record.getNormalizedSqlText();
        }
        return record.getSqlText();
    }

    private boolean containsOnly(final List<Placeholder> placeholders, final PlaceholderKind kind) {
        return placeholders.stream().allMatch(placeholder -> placeholder.getKind() == kind);
    }

    private BindingPlanStatus determineStatus(final List<BindingValue> bindingValues) {
        final boolean hasManual = bindingValues.stream()
                .anyMatch(value -> value.getConfidence() == org.rosetta.sqlvalidator.validation.model.ParameterConfidence.MANUAL_REQUIRED);
        if (hasManual) {
            return BindingPlanStatus.MANUAL_REQUIRED;
        }
        final boolean hasLow = bindingValues.stream()
                .anyMatch(value -> value.getConfidence() == org.rosetta.sqlvalidator.validation.model.ParameterConfidence.LOW);
        return hasLow ? BindingPlanStatus.READY_WITH_LOW_CONFIDENCE : BindingPlanStatus.READY;
    }
}
```

Adjust method names such as `record.getId()` to match your actual `NativeSqlRecord` class.

---

## 17. JDBC Ordinal Binding Planner

```java
package org.rosetta.sqlvalidator.validation.parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.rosetta.sqlvalidator.inventory.model.NativeSqlRecord;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.BindingPlanStatus;
import org.rosetta.sqlvalidator.validation.model.BindingValue;
import org.rosetta.sqlvalidator.validation.model.ParameterConfidence;
import org.rosetta.sqlvalidator.validation.model.ParameterKind;
import org.rosetta.sqlvalidator.validation.model.StatementType;
import org.springframework.stereotype.Component;

@Component
public class JdbcSqlBindingPlanner {

    private final JavaSourceParameterResolver javaSourceParameterResolver;
    private final MockValueFactory mockValueFactory;

    public JdbcSqlBindingPlanner(
            final JavaSourceParameterResolver javaSourceParameterResolver,
            final MockValueFactory mockValueFactory) {
        this.javaSourceParameterResolver = javaSourceParameterResolver;
        this.mockValueFactory = mockValueFactory;
    }

    public BindingPlan build(final NativeSqlRecord record, final String sql, final StatementType statementType) {
        final Map<Integer, ResolvedParameter> ordinalParameters = javaSourceParameterResolver.resolveOrdinalParameters(record);
        final int count = countQuestionMarks(sql);
        final List<BindingValue> bindingValues = new ArrayList<>();

        for (int index = 1; index <= count; index++) {
            final ResolvedParameter parameter = ordinalParameters.get(index);
            if (parameter == null) {
                bindingValues.add(new BindingValue(index, "?", null, null,
                        ParameterKind.UNKNOWN, null, mockValueFactory.createScalarValue(null, null),
                        ParameterConfidence.LOW, "Ordinal parameter not resolved, fallback value used"));
            } else {
                bindingValues.add(new BindingValue(index, "?", parameter.getName(), parameter.getJavaType(),
                        parameter.getParameterKind(), parameter.getElementType(), parameter.getValue(),
                        parameter.getConfidence(), "Mapped from JDBC ordinal parameter"));
            }
        }

        final BindingPlanStatus status = bindingValues.stream()
                .anyMatch(value -> value.getConfidence() == ParameterConfidence.LOW)
                ? BindingPlanStatus.READY_WITH_LOW_CONFIDENCE
                : BindingPlanStatus.READY;

        return new BindingPlan(record.getId(), sql, sql, statementType, status, bindingValues,
                "JDBC ordinal positional binding plan");
    }

    private int countQuestionMarks(final String sql) {
        int count = 0;
        boolean inSingleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            final char current = sql.charAt(i);
            if (current == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote && current == '?') {
                count++;
            }
        }
        return count;
    }
}
```

---

## 18. PostgreSQL Error Classifier

```java
package org.rosetta.sqlvalidator.validation.classifier;

import java.sql.SQLException;
import org.rosetta.sqlvalidator.validation.model.ErrorCategory;
import org.springframework.stereotype.Component;

@Component
public class PostgresErrorClassifier {

    private static final String SYNTAX_ERROR = "42601";
    private static final String RELATION_NOT_FOUND = "42P01";
    private static final String COLUMN_NOT_FOUND = "42703";
    private static final String FUNCTION_NOT_FOUND = "42883";
    private static final String TYPE_MISMATCH = "42804";
    private static final String AMBIGUOUS_COLUMN = "42702";
    private static final String PERMISSION_DENIED = "42501";
    private static final String TIMEOUT = "57014";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    public ErrorCategory classify(final SQLException exception) {
        final String sqlState = exception.getSQLState();
        if (SYNTAX_ERROR.equals(sqlState)) {
            return ErrorCategory.SYNTAX_ERROR;
        }
        if (RELATION_NOT_FOUND.equals(sqlState)) {
            return ErrorCategory.RELATION_NOT_FOUND;
        }
        if (COLUMN_NOT_FOUND.equals(sqlState)) {
            return ErrorCategory.COLUMN_NOT_FOUND;
        }
        if (FUNCTION_NOT_FOUND.equals(sqlState)) {
            return ErrorCategory.FUNCTION_NOT_FOUND;
        }
        if (TYPE_MISMATCH.equals(sqlState)) {
            return ErrorCategory.TYPE_MISMATCH;
        }
        if (AMBIGUOUS_COLUMN.equals(sqlState)) {
            return ErrorCategory.AMBIGUOUS_COLUMN;
        }
        if (PERMISSION_DENIED.equals(sqlState)) {
            return ErrorCategory.PERMISSION_DENIED;
        }
        if (TIMEOUT.equals(sqlState)) {
            return ErrorCategory.TIMEOUT;
        }
        if (UNIQUE_VIOLATION.equals(sqlState)) {
            return ErrorCategory.UNIQUE_VIOLATION;
        }
        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return ErrorCategory.FOREIGN_KEY_VIOLATION;
        }
        return ErrorCategory.UNKNOWN_POSTGRES_ERROR;
    }
}
```

---

## 18.1 Identifier Case and Quoted Identifier Support

Add explicit support for recording PostgreSQL identifier strategy. This is not a SQL rewrite feature. It is an environment interpretation aid used when many statements fail with `relation does not exist` or `column does not exist`.

The configuration value should be stored from:

```yaml
validator:
  validation:
    postgres:
      identifier-strategy: UNQUOTED_LOWERCASE
```

Use one of:

```text
UNKNOWN
UNQUOTED_LOWERCASE
QUOTED_ORACLE_UPPERCASE
```

Rules:

1. Do not automatically quote table or column names.
2. Do not rewrite SQL based on this value.
3. Record the value in execution reports.
4. If many `42P01` or `42703` errors appear, recommend checking schema, search path, and identifier strategy before classifying all failures as SQL compatibility issues.

---

## 19. PostgreSQL Execution Template

Centralize connection, transaction, timeout, search path, identifier strategy recording, binding, rollback.

```java
package org.rosetta.sqlvalidator.validation.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.BindingValue;
import org.springframework.stereotype.Component;

@Component
public class PostgresExecutionTemplate {

    private static final int FIRST_RESULT_SET_ROW = 1;

    private final DataSource dataSource;
    private final ValidatorProperties validatorProperties;

    public PostgresExecutionTemplate(
            final DataSource dataSource,
            final ValidatorProperties validatorProperties) {
        this.dataSource = dataSource;
        this.validatorProperties = validatorProperties;
    }

    public <T> T executeInRollbackTransaction(final SqlExecutionCallback<T> callback) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                applySearchPath(connection);
                applyStatementTimeout(connection);
                final T result = callback.execute(connection);
                connection.rollback();
                return result;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void bind(final PreparedStatement statement, final BindingPlan bindingPlan) throws SQLException {
        for (BindingValue value : bindingPlan.getBindingValues()) {
            statement.setObject(value.getBindingIndex(), value.getValue());
        }
    }

    private void applySearchPath(final Connection connection) throws SQLException {
        final String searchPath = validatorProperties.getValidation().getPostgres().getSearchPath();
        if (searchPath == null || searchPath.isBlank()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("SET search_path TO " + searchPath)) {
            statement.execute();
        }
    }

    private void applyStatementTimeout(final Connection connection) throws SQLException {
        final long timeoutMs = validatorProperties.getValidation().getPostgres().getStatementTimeoutMs();
        if (timeoutMs <= 0) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("SET LOCAL statement_timeout = ?")) {
            statement.setLong(FIRST_RESULT_SET_ROW, timeoutMs);
            statement.execute();
        }
    }
}
```

Callback:

```java
package org.rosetta.sqlvalidator.validation.executor;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlExecutionCallback<T> {
    T execute(Connection connection) throws SQLException;
}
```

Note: if PostgreSQL does not accept parameter binding in `SET LOCAL statement_timeout = ?` in your environment, replace it with a sanitized numeric string because the value comes from internal config, not user input.

---

## 20. EXPLAIN Executor

```java
package org.rosetta.sqlvalidator.validation.executor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.rosetta.sqlvalidator.validation.classifier.PostgresErrorClassifier;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.ErrorCategory;
import org.rosetta.sqlvalidator.validation.model.ExecutionStatus;
import org.rosetta.sqlvalidator.validation.model.SqlExecutionResult;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.springframework.stereotype.Component;

@Component
public class PostgresExplainExecutor {

    private static final String EXPLAIN_PREFIX = "EXPLAIN ";

    private final PostgresExecutionTemplate executionTemplate;
    private final PostgresErrorClassifier errorClassifier;

    public PostgresExplainExecutor(
            final PostgresExecutionTemplate executionTemplate,
            final PostgresErrorClassifier errorClassifier) {
        this.executionTemplate = executionTemplate;
        this.errorClassifier = errorClassifier;
    }

    public SqlExecutionResult execute(final BindingPlan bindingPlan) {
        final long startTime = System.currentTimeMillis();
        try {
            executionTemplate.executeInRollbackTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(EXPLAIN_PREFIX + bindingPlan.getJdbcSql())) {
                    executionTemplate.bind(statement, bindingPlan);
                    statement.execute();
                }
                return null;
            });
            return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.EXPLAIN,
                    bindingPlan.getStatementType(), ExecutionStatus.PASSED, ErrorCategory.NONE,
                    null, "EXPLAIN passed", elapsed(startTime), 0, "No action required");
        } catch (SQLException exception) {
            final ErrorCategory category = errorClassifier.classify(exception);
            return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.EXPLAIN,
                    bindingPlan.getStatementType(), ExecutionStatus.FAILED_SQL_COMPATIBILITY, category,
                    exception.getSQLState(), exception.getMessage(), elapsed(startTime), 0,
                    "Review SQL compatibility, schema/search_path, parameter types, and PostgreSQL functions");
        }
    }

    private long elapsed(final long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
```

---

## 21. SELECT Smoke Executor

```java
package org.rosetta.sqlvalidator.validation.executor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.rosetta.sqlvalidator.validation.classifier.PostgresErrorClassifier;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.ErrorCategory;
import org.rosetta.sqlvalidator.validation.model.ExecutionStatus;
import org.rosetta.sqlvalidator.validation.model.SqlExecutionResult;
import org.rosetta.sqlvalidator.validation.model.StatementType;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.springframework.stereotype.Component;

@Component
public class SelectSmokeExecutor {

    private static final int MAX_ROWS = 1;

    private final PostgresExecutionTemplate executionTemplate;
    private final PostgresErrorClassifier errorClassifier;

    public SelectSmokeExecutor(
            final PostgresExecutionTemplate executionTemplate,
            final PostgresErrorClassifier errorClassifier) {
        this.executionTemplate = executionTemplate;
        this.errorClassifier = errorClassifier;
    }

    public SqlExecutionResult execute(final BindingPlan bindingPlan) {
        if (bindingPlan.getStatementType() != StatementType.SELECT) {
            return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.SELECT_SMOKE,
                    bindingPlan.getStatementType(), ExecutionStatus.SKIPPED_SAFETY,
                    ErrorCategory.RISKY_SELECT_SKIPPED_FOR_SAFETY, null,
                    "Only safe SELECT statements are executed in SELECT smoke mode",
                    0L, 0, "Run EXPLAIN or manual validation instead");
        }

        final long startTime = System.currentTimeMillis();
        try {
            final int rowCount = executionTemplate.executeInRollbackTransaction(connection -> {
                connection.setReadOnly(true);
                try (PreparedStatement statement = connection.prepareStatement(bindingPlan.getJdbcSql())) {
                    statement.setMaxRows(MAX_ROWS);
                    executionTemplate.bind(statement, bindingPlan);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next() ? 1 : 0;
                    }
                }
            });
            return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.SELECT_SMOKE,
                    bindingPlan.getStatementType(), ExecutionStatus.PASSED, ErrorCategory.NONE,
                    null, "SELECT smoke execution passed", elapsed(startTime), rowCount,
                    "Execution passed. Business correctness is not validated.");
        } catch (SQLException exception) {
            final ErrorCategory category = errorClassifier.classify(exception);
            return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.SELECT_SMOKE,
                    bindingPlan.getStatementType(), ExecutionStatus.FAILED_SQL_COMPATIBILITY, category,
                    exception.getSQLState(), exception.getMessage(), elapsed(startTime), 0,
                    "Review SQL compatibility or parameter mock values");
        }
    }

    private long elapsed(final long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
```

---

## 22. DML Safety Executor

MVP behavior: do not truly execute DML. Reuse EXPLAIN executor.

```java
package org.rosetta.sqlvalidator.validation.executor;

import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.ErrorCategory;
import org.rosetta.sqlvalidator.validation.model.ExecutionStatus;
import org.rosetta.sqlvalidator.validation.model.SqlExecutionResult;
import org.rosetta.sqlvalidator.validation.model.StatementType;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.springframework.stereotype.Component;

@Component
public class DmlSafetyExecutor {

    private final PostgresExplainExecutor explainExecutor;

    public DmlSafetyExecutor(final PostgresExplainExecutor explainExecutor) {
        this.explainExecutor = explainExecutor;
    }

    public SqlExecutionResult execute(final BindingPlan bindingPlan) {
        if (isDml(bindingPlan.getStatementType())) {
            return explainExecutor.execute(bindingPlan);
        }
        return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.DML_SAFETY,
                bindingPlan.getStatementType(), ExecutionStatus.SKIPPED_SAFETY,
                ErrorCategory.DML_SKIPPED_FOR_SAFETY, null,
                "DML safety mode only validates DML statements using EXPLAIN",
                0L, 0, "No DML real execution attempted");
    }

    private boolean isDml(final StatementType statementType) {
        return statementType == StatementType.INSERT
                || statementType == StatementType.UPDATE
                || statementType == StatementType.DELETE
                || statementType == StatementType.MERGE;
    }
}
```

---

## 23. Controlled Real Execution Executor

This is a guarded placeholder. Implement full execution only after Phase 2.1 to 2.3 are stable.

```java
package org.rosetta.sqlvalidator.validation.executor;

import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.ErrorCategory;
import org.rosetta.sqlvalidator.validation.model.ExecutionStatus;
import org.rosetta.sqlvalidator.validation.model.SqlExecutionResult;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.springframework.stereotype.Component;

@Component
public class ControlledRealExecutionExecutor {

    private final ValidatorProperties validatorProperties;

    public ControlledRealExecutionExecutor(final ValidatorProperties validatorProperties) {
        this.validatorProperties = validatorProperties;
    }

    public SqlExecutionResult execute(final BindingPlan bindingPlan) {
        if (!isEnabledAndConfirmed()) {
            return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.REAL_EXECUTION,
                    bindingPlan.getStatementType(), ExecutionStatus.SKIPPED_SAFETY,
                    ErrorCategory.UNKNOWN, null,
                    "Real execution is disabled or confirmation token is missing",
                    0L, 0, "Enable real execution only with migration owner approval");
        }

        // Implement only after approval.
        return new SqlExecutionResult(bindingPlan.getSqlId(), ValidationMode.REAL_EXECUTION,
                bindingPlan.getStatementType(), ExecutionStatus.NOT_EXECUTED,
                ErrorCategory.UNKNOWN, null,
                "Real execution executor is prepared but not implemented in MVP",
                0L, 0, "Implement after Phase 2.1-2.3 are stable");
    }

    private boolean isEnabledAndConfirmed() {
        final ValidatorProperties.RealExecution realExecution = validatorProperties.getValidation().getRealExecution();
        return realExecution.isEnabled()
                && realExecution.getRequiredConfirmToken().equals(realExecution.getConfirmToken());
    }
}
```

This class is intentionally conservative.

---

## 24. Report Writers

Phase 2 has two required reports. They are linked by `sqlId`.

Required `sql-binding-plan.csv` fields:

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

Required `sql-execution-report.csv` fields:

```text
sqlId
jdbcSql
validationMode
executionStatus
postgresErrorCode
errorCategory
message
```

Additional fields are allowed, but the required fields above must not be removed or renamed.

### 24.1 Binding Plan CSV Writer

```java
package org.rosetta.sqlvalidator.validation.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.BindingValue;
import org.springframework.stereotype.Component;

@Component
public class BindingPlanCsvWriter {

    private static final String[] HEADERS = {
            "sqlId", "parameterMode", "originalParameter", "bindingIndex", "javaParameterName",
            "javaType", "parameterKind", "mockValue", "confidence", "bindingNote"
    };

    public void write(final List<BindingPlan> plans, final Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(outputFile),
                CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            for (BindingPlan plan : plans) {
                if (plan.getBindingValues().isEmpty()) {
                    printer.printRecord(plan.getSqlId(), plan.getParameterMode(), "",
                            "", "", "", "", "", "", plan.getNote());
                    continue;
                }
                for (BindingValue value : plan.getBindingValues()) {
                    printer.printRecord(plan.getSqlId(), plan.getParameterMode(), value.getSourceParameter(),
                            value.getBindingIndex(), value.getJavaParameterName(), value.getJavaType(),
                            value.getParameterKind(), preview(value.getValue()), value.getConfidence(), value.getNote());
                }
            }
        }
    }

    private String preview(final Object value) {
        if (value == null) {
            return "";
        }
        final String text = String.valueOf(value);
        return text.length() > 100 ? text.substring(0, 100) : text;
    }
}
```

### 24.2 Execution Report CSV Writer

```java
package org.rosetta.sqlvalidator.validation.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.rosetta.sqlvalidator.validation.model.SqlExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class ExecutionReportCsvWriter {

    private static final String[] HEADERS = {
            "sqlId", "jdbcSql", "validationMode", "executionStatus", "postgresErrorCode",
            "errorCategory", "message"
    };

    public void write(final List<SqlExecutionResult> results, final Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(outputFile),
                CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            for (SqlExecutionResult result : results) {
                printer.printRecord(result.getSqlId(), result.getJdbcSql(), result.getValidationMode(),
                        result.getExecutionStatus(), result.getPostgresErrorCode(), result.getErrorCategory(),
                        result.getMessage());
            }
        }
    }
}
```

If you want richer dashboard support later, add extended columns such as `serviceName`, `statementType`, `sqlState`, `schema`, `searchPath`, `identifierStrategy`, `executionTimeMs`, `rowCount`, and `recommendation`. Do not remove or rename the minimum required fields above.

---

## 25. Validation Service

```java
package org.rosetta.sqlvalidator.validation.service;

import java.util.List;
import org.rosetta.sqlvalidator.validation.model.SqlExecutionResult;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;

public interface PostgresValidationService {

    List<SqlExecutionResult> validate(ValidationMode validationMode);
}
```

Implementation outline:

```java
package org.rosetta.sqlvalidator.validation.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.rosetta.sqlvalidator.inventory.model.NativeSqlRecord;
import org.rosetta.sqlvalidator.sanity.service.SqlInventoryCsvReader;
import org.rosetta.sqlvalidator.validation.executor.ControlledRealExecutionExecutor;
import org.rosetta.sqlvalidator.validation.executor.DmlSafetyExecutor;
import org.rosetta.sqlvalidator.validation.executor.PostgresExplainExecutor;
import org.rosetta.sqlvalidator.validation.executor.SelectSmokeExecutor;
import org.rosetta.sqlvalidator.validation.model.BindingPlan;
import org.rosetta.sqlvalidator.validation.model.BindingPlanStatus;
import org.rosetta.sqlvalidator.validation.model.ErrorCategory;
import org.rosetta.sqlvalidator.validation.model.ExecutionStatus;
import org.rosetta.sqlvalidator.validation.model.SqlExecutionResult;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.rosetta.sqlvalidator.validation.parameter.BindingPlanBuilder;
import org.rosetta.sqlvalidator.validation.report.BindingPlanCsvWriter;
import org.rosetta.sqlvalidator.validation.report.ExecutionReportCsvWriter;
import org.springframework.stereotype.Service;

@Service
public class DefaultPostgresValidationService implements PostgresValidationService {

    private final ValidatorProperties validatorProperties;
    private final SqlInventoryCsvReader sqlInventoryCsvReader;
    private final BindingPlanBuilder bindingPlanBuilder;
    private final PostgresExplainExecutor explainExecutor;
    private final SelectSmokeExecutor selectSmokeExecutor;
    private final DmlSafetyExecutor dmlSafetyExecutor;
    private final ControlledRealExecutionExecutor realExecutionExecutor;
    private final BindingPlanCsvWriter bindingPlanCsvWriter;
    private final ExecutionReportCsvWriter executionReportCsvWriter;

    public DefaultPostgresValidationService(
            final ValidatorProperties validatorProperties,
            final SqlInventoryCsvReader sqlInventoryCsvReader,
            final BindingPlanBuilder bindingPlanBuilder,
            final PostgresExplainExecutor explainExecutor,
            final SelectSmokeExecutor selectSmokeExecutor,
            final DmlSafetyExecutor dmlSafetyExecutor,
            final ControlledRealExecutionExecutor realExecutionExecutor,
            final BindingPlanCsvWriter bindingPlanCsvWriter,
            final ExecutionReportCsvWriter executionReportCsvWriter) {
        this.validatorProperties = validatorProperties;
        this.sqlInventoryCsvReader = sqlInventoryCsvReader;
        this.bindingPlanBuilder = bindingPlanBuilder;
        this.explainExecutor = explainExecutor;
        this.selectSmokeExecutor = selectSmokeExecutor;
        this.dmlSafetyExecutor = dmlSafetyExecutor;
        this.realExecutionExecutor = realExecutionExecutor;
        this.bindingPlanCsvWriter = bindingPlanCsvWriter;
        this.executionReportCsvWriter = executionReportCsvWriter;
    }

    @Override
    public List<SqlExecutionResult> validate(final ValidationMode validationMode) {
        try {
            final List<NativeSqlRecord> records = sqlInventoryCsvReader.read(
                    Path.of(validatorProperties.getValidation().getInventoryFile()));
            final List<BindingPlan> bindingPlans = new ArrayList<>();
            final List<SqlExecutionResult> results = new ArrayList<>();

            for (NativeSqlRecord record : records) {
                final BindingPlan bindingPlan = bindingPlanBuilder.build(record);
                bindingPlans.add(bindingPlan);
                if (bindingPlan.getStatus() == BindingPlanStatus.MANUAL_REQUIRED
                        || bindingPlan.getStatus() == BindingPlanStatus.UNSUPPORTED_PARAMETER_STYLE
                        || bindingPlan.getStatus() == BindingPlanStatus.EMPTY_SQL
                        || bindingPlan.getStatus() == BindingPlanStatus.SKIPPED_DYNAMIC_SQL) {
                    results.add(skipped(bindingPlan, validationMode));
                    continue;
                }
                results.add(execute(validationMode, bindingPlan));
            }

            bindingPlanCsvWriter.write(bindingPlans,
                    Path.of(validatorProperties.getValidation().getBindingPlanFile()));
            executionReportCsvWriter.write(results,
                    Path.of(validatorProperties.getValidation().getExecutionReportFile()));
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("PostgreSQL validation failed", exception);
        }
    }

    private SqlExecutionResult execute(final ValidationMode validationMode, final BindingPlan bindingPlan) {
        return switch (validationMode) {
            case EXPLAIN -> explainExecutor.execute(bindingPlan);
            case SELECT_SMOKE -> selectSmokeExecutor.execute(bindingPlan);
            case DML_SAFETY -> dmlSafetyExecutor.execute(bindingPlan);
            case REAL_EXECUTION -> realExecutionExecutor.execute(bindingPlan);
        };
    }

    private SqlExecutionResult skipped(final BindingPlan bindingPlan, final ValidationMode validationMode) {
        return new SqlExecutionResult(bindingPlan.getSqlId(), validationMode, bindingPlan.getStatementType(),
                ExecutionStatus.SKIPPED_MANUAL_REQUIRED, ErrorCategory.PARAMETER_BINDING_ERROR,
                null, bindingPlan.getNote(), 0L, 0, "Review binding plan manually");
    }
}
```

Adjust reader usage if your existing `SqlInventoryCsvReader` has a different package or method signature.

---

## 26. Runners

### 26.1 `ExplainValidationRunner`

```java
package org.rosetta.sqlvalidator.validation.runner;

import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.rosetta.sqlvalidator.validation.service.PostgresValidationService;
import org.springframework.stereotype.Component;

@Component
public class ExplainValidationRunner implements ValidatorRunner {

    private final PostgresValidationService postgresValidationService;

    public ExplainValidationRunner(final PostgresValidationService postgresValidationService) {
        this.postgresValidationService = postgresValidationService;
    }

    @Override
    public ValidatorPhase getPhase() {
        return ValidatorPhase.VALIDATION_EXPLAIN;
    }

    @Override
    public void run() {
        postgresValidationService.validate(ValidationMode.EXPLAIN);
    }
}
```

### 26.2 `SelectSmokeValidationRunner`

```java
package org.rosetta.sqlvalidator.validation.runner;

import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.rosetta.sqlvalidator.validation.service.PostgresValidationService;
import org.springframework.stereotype.Component;

@Component
public class SelectSmokeValidationRunner implements ValidatorRunner {

    private final PostgresValidationService postgresValidationService;

    public SelectSmokeValidationRunner(final PostgresValidationService postgresValidationService) {
        this.postgresValidationService = postgresValidationService;
    }

    @Override
    public ValidatorPhase getPhase() {
        return ValidatorPhase.VALIDATION_SELECT_SMOKE;
    }

    @Override
    public void run() {
        postgresValidationService.validate(ValidationMode.SELECT_SMOKE);
    }
}
```

### 26.3 `DmlSafetyValidationRunner`

```java
package org.rosetta.sqlvalidator.validation.runner;

import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.rosetta.sqlvalidator.validation.service.PostgresValidationService;
import org.springframework.stereotype.Component;

@Component
public class DmlSafetyValidationRunner implements ValidatorRunner {

    private final PostgresValidationService postgresValidationService;

    public DmlSafetyValidationRunner(final PostgresValidationService postgresValidationService) {
        this.postgresValidationService = postgresValidationService;
    }

    @Override
    public ValidatorPhase getPhase() {
        return ValidatorPhase.VALIDATION_DML_SAFETY;
    }

    @Override
    public void run() {
        postgresValidationService.validate(ValidationMode.DML_SAFETY);
    }
}
```

### 26.4 `RealExecutionRunner`

```java
package org.rosetta.sqlvalidator.validation.runner;

import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;
import org.rosetta.sqlvalidator.validation.model.ValidationMode;
import org.rosetta.sqlvalidator.validation.service.PostgresValidationService;
import org.springframework.stereotype.Component;

@Component
public class RealExecutionRunner implements ValidatorRunner {

    private final PostgresValidationService postgresValidationService;

    public RealExecutionRunner(final PostgresValidationService postgresValidationService) {
        this.postgresValidationService = postgresValidationService;
    }

    @Override
    public ValidatorPhase getPhase() {
        return ValidatorPhase.REAL_EXECUTION;
    }

    @Override
    public void run() {
        postgresValidationService.validate(ValidationMode.REAL_EXECUTION);
    }
}
```

---

## 27. DataSource Configuration

Spring Boot can auto-configure the datasource if properties are mapped to `spring.datasource.*`. Because this project uses custom `validator.validation.postgres.*`, add a DataSource config.

```java
package org.rosetta.sqlvalidator.validation.executor;

import javax.sql.DataSource;
import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostgresDataSourceConfig {

    @Bean
    public DataSource postgresDataSource(final ValidatorProperties validatorProperties) {
        final ValidatorProperties.Postgres postgres = validatorProperties.getValidation().getPostgres();
        return DataSourceBuilder.create()
                .url(postgres.getJdbcUrl())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
```

If your project already has a DataSource bean, do not duplicate it. Use the existing one.

---

## 28. Implementation Order for Copilot

Use this order to avoid breaking the working tool:

```text
1. Add dependencies.
2. Add application.yml Phase 2 configuration.
3. Add new ValidatorPhase enum values.
4. Add model enums and data classes.
5. Add StatementTypeDetector.
6. Add SqlPlaceholderParser.
7. Add MockValueFactory.
8. Add binding planners.
9. Add BindingPlanBuilder.
10. Add DataSource config.
11. Add PostgresErrorClassifier.
12. Add EXPLAIN executor.
13. Add report writers.
14. Add service and runner for validation-explain.
15. Compile and test Phase 2.1 first.
16. Add SELECT smoke runner.
17. Add DML safety runner.
18. Add real-execution guarded runner.
```

---

## 29. Copilot Prompt

```text
Please implement Phase 2 PostgreSQL Execution Validation in the existing SQL-Postgres-Validator project.

Important:
- Phase 1 and Phase 1.5 are already working. Do not rewrite them.
- Keep the current Spring Boot CLI structure.
- Add Phase 2 under org.rosetta.sqlvalidator.validation.
- Follow docs/10_Phase2_PostgreSQL_Validation_Final_Design.md and docs/11_Phase2_PostgreSQL_Validation_Code_Guide.md.

Implementation requirements:
1. Add new phase enum values:
   validation-explain
   validation-select-smoke
   validation-dml-safety
   real-execution
2. Add BindingPlan generation before execution.
3. Support named parameters, JPA indexed positional parameters, and JDBC ordinal parameters.
4. Use Spring JDBC NamedParameterUtils for named parameter expansion where appropriate.
5. Normalize IN :param to IN (:param).
6. Generate mock values based on Java type when resolvable, otherwise fallback with LOW confidence.
7. Add PostgreSQL EXPLAIN executor first.
8. Use SQLState-based error classification.
9. Default to safe execution.
10. DML must not be truly executed by default.
11. Real execution must be disabled by default and require confirm token.
12. Use SLF4J logging.
13. Do not use System.out.println.
14. Do not modify business service modules.
15. Do not add runtime AI dependency.

Please implement incrementally and keep the code compiling after each step.
```

---

## 30. Validation Checklist

After implementation, verify:

```text
mvn clean test
mvn spring-boot:run -Dspring-boot.run.arguments="--validator.phase=inventory"
mvn spring-boot:run -Dspring-boot.run.arguments="--validator.phase=sanity"
mvn spring-boot:run -Dspring-boot.run.arguments="--validator.phase=validation-explain"
```

Expected outputs:

```text
output/sql-binding-plan.csv
output/sql-execution-report.csv
output/sql-execution-summary.txt
```

Do not run `real-execution` until migration owner approval.

---

## 31. Important Notes

1. Some method names in this code guide may need minor adjustment to match your existing classes.
2. `NativeSqlRecord` getters must be aligned with your actual implementation.
3. `SqlInventoryCsvReader` may already exist under the `sanity` package. Reuse it if possible.
4. The first working target should be Phase 2.1 `validation-explain`.
5. Do not attempt to solve all parameter inference problems in the first pass.
6. Low-confidence parameter failures should be reported as parameter-related, not SQL compatibility failures.
7. Real execution code should stay guarded.

