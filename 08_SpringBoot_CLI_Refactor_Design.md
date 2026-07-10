# 08 Spring Boot CLI Refactor Design

## 1. Purpose

This document defines the refactor plan for the existing `sql-postgres-validator` MVP.

The current Phase 1 and Phase 1.5 implementations are already working. The goal of this refactor is not to redesign the scanner or the sanity checker. The goal is to upgrade the project into a standard, maintainable, JDK 17 Spring Boot CLI tool that can run each validation phase independently.

This refactor prepares the project for Phase 2, but does not implement Phase 2 PostgreSQL validation yet.

---

## 2. Current Status

The current MVP already supports:

| Capability | Status |
|---|---|
| Phase 1 SQL inventory scanner | Working |
| Phase 1 output `sql-inventory.csv` | Working |
| Phase 1 summary output | Working |
| Phase 1.5 SQL text sanity check | Working |
| JSqlParser-based parser check | Working |
| Sanity report output | Working |
| Dashboard CSV-based demo | Separate frontend project |

The refactor should preserve these behaviors.

---

## 3. Target Architecture

The target project is a Spring Boot CLI application.

It is not a web service.
It does not expose REST APIs.
It does not start a UI.
It only runs one selected phase and writes local output files.

Recommended project name:

```text
sql-postgres-validator
```

Target runtime:

```text
JDK 17
Spring Boot CLI style
Maven project
SLF4J + Logback logging
application.yml configuration
```

---

## 4. One Backend Project, Multiple Phases

Phase 1, Phase 1.5, and future Phase 2 should stay in the same Java backend project.

Reason:

- They share CSV models.
- They share input and output folders.
- They share SQL text normalization.
- They share common constants and exceptions.
- Future Phase 2 will reuse Phase 1 inventory output and Phase 1.5 sanity output.

Do not create a separate Java backend project for Phase 2.

Recommended logical modules inside the same project:

```text
org.rosetta.sqlvalidator
├── common
├── config
├── runner
├── inventory      # Phase 1
├── sanity         # Phase 1.5
└── validation     # Future Phase 2
```

---

## 5. Phase Execution Strategy

The tool must support running a specific phase independently.

Example commands:

```bash
java -jar sql-postgres-validator.jar --validator.phase=inventory
```

```bash
java -jar sql-postgres-validator.jar --validator.phase=sanity
```

Future Phase 2 commands:

```bash
java -jar sql-postgres-validator.jar --validator.phase=validation-explain
java -jar sql-postgres-validator.jar --validator.phase=validation-select-smoke
java -jar sql-postgres-validator.jar --validator.phase=validation-dml-safety
java -jar sql-postgres-validator.jar --validator.phase=real-execution
```

The current refactor only needs Phase 1 and Phase 1.5 to work.

Future Phase 2 phase names can exist as enum values, but their runners should not be implemented in this refactor.

---

## 6. Supported Phases

| Phase Key | Meaning | Current Refactor Scope |
|---|---|---|
| `inventory` | Phase 1 SQL inventory scan | Yes |
| `sanity` | Phase 1.5 SQL text sanity check | Yes |
| `validation-explain` | Phase 2.1 PostgreSQL EXPLAIN validation | No, future only |
| `validation-select-smoke` | Phase 2.2 SELECT smoke execution | No, future only |
| `validation-dml-safety` | Phase 2.3 DML safety validation | No, future only |
| `real-execution` | Phase 2.4 controlled real execution | No, future only |

---

## 7. Configuration Design

All file paths, phase settings, and feature flags should be configured in `application.yml`.

Example:

```yaml
validator:
  phase: inventory
  output-dir: ./output
  inventory:
    source-roots:
      - ../50718-DMRService/src/main/java
      - ../50718-WorkflowService/src/main/java
    inventory-output: ./output/sql-inventory.csv
    summary-output: ./output/scan-summary.txt
  sanity:
    input: ./output/sql-inventory.csv
    report-output: ./output/sql-sanity-report.csv
    summary-output: ./output/sql-sanity-summary.txt
```

Rules:

- Do not hardcode company paths in Java code.
- Do not commit real company paths to external GitHub.
- Use sample placeholder paths in documentation.
- Use local `application-local.yml` for real company paths if needed.
- Keep `application-local.yml` out of Git.

Recommended `.gitignore` entries:

```gitignore
output/
*.csv
*.log
application-local.yml
.idea/
target/
```

---

## 8. Runner Dispatch Design

The application should have one dispatcher.

The dispatcher reads:

```text
validator.phase
```

Then it finds the matching runner.

Each phase runner implements a common interface:

```java
public interface ValidatorRunner {
    ValidatorPhase getSupportedPhase();

    void run();
}
```

Example runners:

```text
InventoryScanRunner
SqlSanityCheckRunner
```

Future runners:

```text
ExplainValidationRunner
SelectSmokeValidationRunner
DmlSafetyValidationRunner
RealExecutionRunner
```

---

## 9. Java Coding Standard

Future code should follow enterprise Java style and Alibaba Java coding guideline principles.

Mandatory rules:

1. Use JDK 17.
2. Use Spring Boot CLI architecture.
3. Use SLF4J logging.
4. Do not use `System.out.println`.
5. Use constructor injection.
6. Prefer `final` for injected fields and local variables that should not change.
7. Use `private static final` constants for magic strings.
8. Do not hardcode file paths.
9. Use clear package boundaries.
10. Keep methods small and focused.
11. Keep runner classes as orchestration only.
12. Put business logic into service/checker/parser/writer classes.
13. Use checked exceptions only when they add value; otherwise wrap into project exceptions.
14. Do not swallow exceptions silently.
15. Do not modify business service modules.
16. Do not upload company SQL, CSV, source code, logs, or real paths to external repositories.

---

## 10. Logging Standard

Use SLF4J:

```java
private static final Logger LOGGER = LoggerFactory.getLogger(InventoryScanRunner.class);
```

Use logging levels consistently:

| Level | Usage |
|---|---|
| `info` | Phase started, phase completed, output files created |
| `warn` | Skipped record, unsupported pattern, manual review |
| `error` | Phase failed, file read/write failed |
| `debug` | Detailed internal parsing information |

Do not log real sensitive SQL values unless explicitly required for local debugging.

For company-safe reporting, write detailed SQL information to local output CSV only, not to external logs.

---

## 11. Exception Design

Recommended base exception:

```text
SqlValidatorException
```

Recommended subclasses:

```text
ConfigurationException
PhaseExecutionException
ReportWriteException
CsvReadException
```

Current refactor can start with only:

```text
SqlValidatorException
PhaseExecutionException
```

Future Phase 2 can add more specific exceptions.

---

## 12. Package Structure

Recommended package structure:

```text
src/main/java/org/rosetta/sqlvalidator
├── SqlPostgresValidatorApplication.java
├── common
│   ├── constant
│   │   └── ValidatorConstants.java
│   └── exception
│       ├── SqlValidatorException.java
│       └── PhaseExecutionException.java
├── config
│   └── ValidatorProperties.java
├── runner
│   ├── RunnerDispatcher.java
│   ├── ValidatorPhase.java
│   └── ValidatorRunner.java
├── inventory
│   ├── runner
│   │   └── InventoryScanRunner.java
│   └── service
│       ├── InventoryScanService.java
│       └── DefaultInventoryScanService.java
├── sanity
│   ├── runner
│   │   └── SqlSanityCheckRunner.java
│   └── service
│       ├── SqlSanityCheckService.java
│       └── DefaultSqlSanityCheckService.java
└── validation
    └── README_PLACEHOLDER.md
```

Existing scanner/parser/report classes can remain under their current packages first. The refactor should introduce wrapper services and runners before moving every class.

Do not do a risky all-at-once package migration unless the current project is already stable.

---

## 13. Refactor Strategy

Recommended sequence:

### Step 1: Add Spring Boot dependencies

Add Spring Boot parent/starter and keep existing dependencies.

### Step 2: Add configuration

Create `application.yml` and `ValidatorProperties`.

### Step 3: Add runner framework

Create:

```text
ValidatorPhase
ValidatorRunner
RunnerDispatcher
```

### Step 4: Wrap existing Phase 1

Move or call the existing Phase 1 main logic from `InventoryScanService`.

### Step 5: Wrap existing Phase 1.5

Move or call the existing Phase 1.5 main logic from `SqlSanityCheckService`.

### Step 6: Replace `System.out.println`

Use SLF4J logging.

### Step 7: Verify commands

Run:

```bash
mvn clean package
java -jar target/sql-postgres-validator.jar --validator.phase=inventory
java -jar target/sql-postgres-validator.jar --validator.phase=sanity
```

---

## 14. What This Refactor Must Not Do

This refactor must not implement Phase 2.

Do not add:

- PostgreSQL JDBC connection logic.
- Parameter binding plan.
- Mock value generation.
- EXPLAIN execution.
- SELECT smoke execution.
- DML real execution.
- Real execution confirm token.
- SQL auto-fix logic.

These belong to later Phase 2 documents.

---

## 15. Copilot Instruction

Use this prompt in Copilot after copying the design and code documents:

```text
This is a refactor task only.

The existing Phase 1 SQL inventory scanner and Phase 1.5 SQL sanity checker are already working.

Please refactor the project into a standard JDK 17 Spring Boot CLI application.

Requirements:
1. Support running each phase separately by validator.phase.
2. Keep existing scanner and sanity logic unchanged as much as possible.
3. Replace System.out.println with SLF4J logging.
4. Move configuration to application.yml.
5. Follow Alibaba Java coding style principles.
6. Use clear package structure.
7. Do not implement Phase 2 PostgreSQL validation yet.
8. Do not connect to PostgreSQL.
9. Do not modify business service modules.
10. Do not rewrite the whole logic.

Only apply the Spring Boot CLI wrapper and coding-standard refactor described in docs/08 and docs/09.
```

---

## 16. Acceptance Criteria

The refactor is successful when:

1. Maven build succeeds.
2. `--validator.phase=inventory` runs Phase 1 only.
3. `--validator.phase=sanity` runs Phase 1.5 only.
4. Existing CSV output format is not broken.
5. Existing dashboard can still read `sql-inventory.csv`.
6. Logs use SLF4J instead of `System.out.println`.
7. Configuration is loaded from `application.yml`.
8. No PostgreSQL connection exists yet.
9. No business service module is modified.
10. No company data is committed.

---

## 17. Next Step After This Refactor

After this Spring Boot CLI refactor is verified, the next phase should be:

```text
Phase 2.1 Binding Plan + PostgreSQL EXPLAIN Validation Design
```

That future step will introduce:

- SQL placeholder parser.
- Java method parameter resolver.
- Binding plan report.
- PostgreSQL EXPLAIN executor.
- SQLState-based error classifier.

Do not merge those into this refactor step.
