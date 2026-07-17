# Batch 3 Implementation Guide

## Goal

Batch 3 adds **H2 real-parameter sampling only**.

It must:

1. Extend the binding-plan input so every JDBC position exposes:
   - `jdbcIndex`
   - `logicalParameterIndex`
   - `parameterName`
   - `javaType`
   - `collection`
2. Build a conservative source-column mapping for simple single-table SELECT.
3. Generate a safe H2 sampling query.
4. Sample up to three distinct real tuples.
5. Keep all values in one tuple from the same H2 row.
6. Apply `LOWER(column)` transformation.
7. Support `IN (?)` with collection size one.
8. Serialize tuples deterministically and reversibly.
9. Reuse one logical value for repeated JDBC positions.
10. Log sampling counts.

## Deliberate boundaries

Batch 3 rejects or defers:

- JOIN
- CTE
- OR
- subqueries
- LIKE without an explicit wildcard construction rule
- dynamic table/column identifiers
- expressions such as `? * column`
- multiple parameters in one predicate
- one repeated logical parameter mapped to different columns

These become `MANUAL_MAPPING_REQUIRED` or `SKIPPED_UNSUPPORTED`; never guess.

## Runtime flow

Only `CAPTURE_BASELINE` performs H2 sampling:

```text
read current CSVs
→ Batch-2 eligibility
→ open H2
→ build mapping plan
→ sample up to 3 tuples
→ serialize tuples in memory
→ log totals
→ keep baseline CSV header-only
```

Do not write baseline data rows yet. A valid baseline row requires execution of
the actual H2 business SELECT and row count/hash, which belongs to Batch 4.

`COMPARE_POSTGRES_WITH_BASELINE` stays header-only in Batch 3.

## Connection

The reference includes a DriverManager provider. If the project already uses
Hikari/DataSource, reuse that instead of introducing duplicate connection code.

The H2 connection is read-only. The only SQL executed in this batch is the
generated parameter-sampling SELECT.

## Parameter serialization

`ParameterTupleCodec` produces a reversible `v1:` representation for the
future `parameterValues` field. Values are not masked.

The same codec must be reused in Batch 5 when saved values are bound to the
migrated PostgreSQL SQL.

## Critical integration point

The exact binding-plan CSV headers are project-specific. Inspect the current
binding-plan CSV writer and map its real headers. Do not blindly rely on the
aliases shown in `BINDING_PLAN_READER_PATCH.md`.
