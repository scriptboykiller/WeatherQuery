# Batch 5 Implementation Guide

## Goal

Implement only:

```text
COMPARE_POSTGRES_WITH_BASELINE
```

The immutable H2 baseline was already created by Batch 4. Batch 5 must:

```text
read sql-select-baseline.csv
→ rebuild current candidates from current inventory/binding/execution CSVs
→ match old and current SQL by baselineKey
→ reuse the exact saved parameterValues
→ execute only the current PostgreSQL jdbcSql
→ compare current PG row count / column signature / hash with saved H2 values
→ write sql-select-comparison.csv
```

## Critical constraints

- Do not connect to H2.
- Do not resample parameters.
- Do not execute `baselineH2JdbcSql`.
- Do not execute `postgresJdbcSqlAtCapture`.
- Do not rewrite or overwrite `sql-select-baseline.csv`.
- Current PostgreSQL validation must be `PASSED` before execution.
- The normalization version must equal the version stored in the baseline.
- One output row must be written for every baseline sample, including unresolved rows.

## Matching

Match only by:

```text
baselineKey
```

Do not fall back to:

- old sqlId;
- SQL text;
- line number;
- SQL hash.

Statuses:

```text
MATCHED
BASELINE_KEY_MISSING
BASELINE_KEY_NOT_FOUND
AMBIGUOUS_BASELINE_KEY
```

## Output

```text
./output/sql-select-comparison.csv
```

This is a current-run artifact and may be overwritten deliberately.

## Runner integration

Only implement the `COMPARE_POSTGRES_WITH_BASELINE` branch. Keep Batch-4
`CAPTURE_BASELINE` unchanged.

Open PostgreSQL only when at least one baseline row is executable. If all rows
are skipped, write the skip rows without opening a DB connection.

## Not in Batch 5

- Excel third sheet.
- Performance-report phase.
- H2 connection.
- Parameter sampling.
- DML.
