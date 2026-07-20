# Batch 4 Implementation Guide

## Confirmed environment

- H2 is accessed through a remote JDBC URL.
- No special schema is required.
- Any required H2 connection parameters stay in the JDBC URL.
- The exact H2 version is unknown.
- Reuse the Batch-3 H2 connection configuration.
- Leave `source.schema` blank when not needed.
- Do not introduce version-specific H2 SQL.

## Batch 4 goal

For each Batch-3 real parameter tuple:

### PostgreSQL validation status = PASSED

```text
execute H2 business SELECT
execute the same current JDBC SQL on PostgreSQL
use the same tuple
normalize both ResultSets
calculate row count, column signature and SHA-256 hash
record one observed execution time per side
compare results
write a permanent baseline row
```

### PostgreSQL validation status != PASSED but H2 baseline is eligible

```text
execute H2 business SELECT only
save H2 result and exact tuple
PostgreSQL status = PENDING_SQL_MIGRATION
sample status = PENDING_SQL_MIGRATION
overall status = BASELINE_CAPTURED
```

## Key rules

1. Do not resample parameters.
2. Do not use Mock fallback.
3. Fully consume ResultSet before stopping timing.
4. Read at most `maxSelectRows + 1`.
5. If the extra row exists, mark `RESULT_TOO_LARGE`.
6. Preserve duplicate rows.
7. Without top-level ORDER BY, sort normalized rows before hashing.
8. With top-level ORDER BY, preserve row order.
9. One SQL/sample failure must not stop the batch.
10. Do not write credentials.
11. Do not silently overwrite an existing real baseline.

## Baseline file protection

Batch 1–3 may have created a header-only skeleton CSV. Batch 4 must distinguish
that skeleton from a real baseline:

- header-only skeleton may be replaced during the first real capture;
- a baseline containing data rows must not be overwritten when
  `failIfBaselineExists=true`.

Perform this check before database execution.

## Runner flow

```text
read current CSVs
→ Batch-2 eligibility
→ Batch-3 H2 tuple sampling
→ open H2
→ open PostgreSQL only when at least one candidate is currently PASSED
→ Batch-4 execute and compare
→ assemble SelectBaselineRecord rows
→ write sql-select-baseline.csv once
```

`COMPARE_POSTGRES_WITH_BASELINE` remains for Batch 5.

## Adaptation points

Copilot must adapt:

- actual package and constructor/builder of `SelectBaselineRecord`;
- actual enum package names;
- actual Batch-3 model names;
- existing PostgreSQL DataSource/provider;
- existing CSV writer method;
- exact execution status used for PASSED.

Do not create duplicate project models.

## Not in Batch 4

- Baseline CSV reader.
- Current-to-baseline matching.
- Future PostgreSQL-only comparison mode.
- Excel changes.
- Performance comparator integration.
- DML.
