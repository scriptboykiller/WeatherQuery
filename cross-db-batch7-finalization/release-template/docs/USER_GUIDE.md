# SQL PostgreSQL Validator — User Guide

## Runtime requirements

- Windows
- JDK 17 in `PATH`
- No administrator rights
- Database network access for the selected phase
- Maven is not required to run the release

## First-time setup

1. Extract the ZIP.
2. Configure `config/application.yml`.
3. Keep H2 schema empty when no special schema is required.
4. Preserve required H2 connection parameters in the JDBC URL.
5. Keep performance disabled until the comparator JAR is provided.

## Recommended workflow

### Inventory

```text
bin\run-inventory.bat
```

### Sanity

```text
bin\run-sanity.bat
```

### PostgreSQL EXPLAIN

```text
bin\run-validation-explain.bat
```

### Capture H2 Baseline

```text
bin\run-capture-baseline.bat
```

`baseline\sql-select-baseline.csv` is a controlled handover asset. Back it up
and do not overwrite it casually.

### Compare migrated PostgreSQL SQL

Regenerate current Inventory/Sanity/EXPLAIN outputs, then run:

```text
bin\run-compare-baseline.bat
```

Compare mode must not connect to H2. It reuses saved parameter tuples and
executes only current PostgreSQL SQL.

### Generate Excel

```text
bin\run-excel-report.bat
```

## Optional performance report

The initial release does not include the comparator JAR. Core validation and
Excel work without it.

Later, copy the delivered JAR to:

```text
tools\sql-performance-comparator.jar
```

Configure `config/performance-tool.yml`, enable performance, then run:

```text
bin\run-performance-report.bat
bin\run-excel-report.bat
```

Expected minimal result:

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

## Output ownership

Controlled asset:

```text
baseline\sql-select-baseline.csv
```

Regeneratable outputs:

```text
output\sql-select-comparison.csv
output\sql-performance-report.csv
output\sql-postgres-migration-report.xlsx
output\performance\
logs\
```

## Important statuses

- `MATCH`: current PostgreSQL matches saved H2.
- `MISMATCH`: row count, column signature, or hash differs.
- `CURRENT_POSTGRES_NOT_READY`: current SQL has not passed PostgreSQL validation.
- `BASELINE_KEY_NOT_FOUND`: no current SQL matches the saved key.
- `AMBIGUOUS_BASELINE_KEY`: multiple current SQL records match the key.
- `PENDING_SQL_MIGRATION`: H2 Baseline exists; PostgreSQL is not ready.
- `RESULT_TOO_LARGE`: result exceeded the configured limit.
- `Not Generated`: optional performance reporting was not run.

## Verify release

```text
bin\verify-release.bat
```

## Real Execution

`run-real-execution.bat` may change data and requires typed confirmation. Use
only with the existing whitelist in an approved environment.
