# Copilot Batch 7 Prompt

Implement ONLY Batch 7: final stabilization, regression testing, and release packaging.

## Confirmed state

- Batch 1–6 are implemented.
- A real H2 baseline has been created.
- Compare mode works.
- Excel and optional performance integration work.
- `sql-performance-comparator.jar` is not available yet.
- Do not wait for it and do not create a dummy JAR.
- Performance remains optional and disabled by default.

## Required work

### 1. Regression

Compile and test all existing phases:

```text
inventory
sanity
validation-explain
validation-select-smoke
validation-dml-safety
real-execution
cross-db-validation
performance-report
excel-report
```

Do not execute risky DML outside an approved, isolated environment and the
existing whitelist.

### 2. Verify flows

Capture:

```text
inventory → sanity → validation-explain
→ CAPTURE_BASELINE → excel-report
```

Compare:

```text
inventory → sanity → validation-explain
→ COMPARE_POSTGRES_WITH_BASELINE → excel-report
```

Confirm Compare mode never opens an H2 connection.

Without the comparator JAR:

- do not include performance-report in the required workflow;
- Excel must still build;
- performance cells must show `Not Generated`;
- no other phase may fail.

### 3. Failure isolation tests

Cover:

- missing or header-only Baseline;
- Baseline overwrite protection;
- H2/PG connection failure;
- output directory not writable;
- one SQL failure/timeout;
- parameter decode failure;
- missing/ambiguous baselineKey;
- missing performance JAR;
- invalid/minimal result.json;
- result caseId mismatch;
- SUCCESS without HTML;
- missing performance CSV during Excel generation.

### 4. Release config

Finalize an external `config/application.yml`:

- no committed real credentials;
- clear comments;
- release-relative paths;
- H2 schema can be blank;
- preserve H2 URL parameters;
- performance disabled by default;
- no passwords in logs.

Provide `config/performance-tool.yml` as a template.

### 5. Windows scripts

Adapt the supplied BAT scripts:

- JDK 17 only at runtime;
- no administrator rights;
- paths with spaces;
- run from any working directory;
- external YAML;
- preserve Java exit code;
- create output/log directories;
- friendly missing-file messages.

`run-performance-report.bat` must fail only that optional action when the
comparator is absent.

`run-real-execution.bat` must retain whitelist protection and require typed
confirmation.

### 6. Fat JAR and release

Build an executable Spring Boot Fat JAR. Do not replace working Maven
configuration unnecessarily.

Create:

```text
build/release/sql-postgres-validator-release/
```

Include executable JAR, config, scripts, public user docs, approved Baseline
only when explicitly selected, and empty output/log folders.

Do not include source, tests, `.git`, IDE metadata or a fake performance JAR.

Generate:

```text
VERSION.txt
SHA256SUMS.txt
sql-postgres-validator-release-<version>.zip
```

### 7. Required tests

At minimum:

- every phase token dispatches correctly;
- all BAT phase tokens match the enum;
- external config loads;
- passwords are not logged;
- Capture does not silently overwrite Baseline;
- Compare does not contact H2;
- performance disabled does not block Excel;
- missing comparator does not affect non-performance phases;
- minimal v2 result JSON is accepted;
- mismatched caseId and missing HTML are rejected;
- Release contains no `.java`, `.git`, tests or secrets;
- Release works under a path containing spaces;
- checksum verification succeeds.

## Fixed performance result contract

```json
{
  "responseVersion": "v2",
  "caseId": "SQL-001",
  "status": "SUCCESS",
  "htmlReport": "index.html",
  "errorCode": "",
  "errorMessage": ""
}
```

Do not require timing fields.

## Hard restrictions

Do not add Web UI, Docker, installer, obfuscation, migration execution,
automatic SQL modification, Git push, concurrent load tests, new DML behavior,
new Cross DB modes, or new Excel sheets.

## Completion report

Report files changed, build command, tests, Capture/Compare/Excel outcomes,
behavior without the comparator JAR, release paths, checksums, source exclusion,
and unresolved issues. Do not claim completion when build or verification fails.
