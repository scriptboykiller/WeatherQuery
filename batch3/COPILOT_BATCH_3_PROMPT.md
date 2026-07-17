# Copilot Batch 3 Prompt

Read the authoritative v2 documents:

- `docs/CROSS_DB_VALIDATION_MVP_REQUIREMENTS_AND_DESIGN.md`
- `docs/CROSS_DB_VALIDATION_MVP_IMPLEMENTATION_HANDOFF.md`

Read the supplied Batch 3 code pack and `BATCH_3_IMPLEMENTATION_GUIDE.md`.

Implement **ONLY Batch 3**:

```text
H2 real-parameter mapping
+ H2 tuple sampling
+ deterministic parameter serialization
```

## Precondition

Inspect Batch 1 and Batch 2 first and confirm:

- the project compiles;
- Batch-2 CSV aggregation works;
- one candidate is produced per `sqlId`;
- `baselineKey` and static eligibility are available;
- CAPTURE mode logs eligibility counts;
- baseline/comparison CSVs remain header-only;
- no Cross DB DML code exists.

If Batch 2 is incomplete or broken, stop and report it.

## Required work

1. Add/adapt the reference code:

- `ParameterSamplingModels`
- `ParameterTupleCodec`
- `SimpleSelectParameterMappingPlanner`
- `H2SamplingQueryBuilder`
- `H2ParameterSampleProvider`
- `SourceConnectionProvider`
- `DriverManagerSourceConnectionProvider`

2. Extend the existing binding-plan reader.

For every JDBC binding position, read the actual project fields for:

```text
sqlId
jdbcIndex
logicalParameterIndex or equivalent
parameterName
javaType
collection flag
```

Inspect the real binding-plan CSV writer and use the exact headers.
Do not create a duplicate reader when the existing reader can be extended.

3. Supported automatic mappings:

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

Scope:

- one simple source table;
- optional alias;
- predicates connected with AND;
- every parameter traceable to an H2 source column.

Reject/defer:

- JOIN;
- CTE;
- OR;
- subqueries;
- LIKE without a known wildcard rule;
- expressions;
- dynamic identifiers;
- repeated logical parameter mapped to different columns.

4. H2 sampling query requirements:

- select all mapped columns in one row;
- use `DISTINCT`;
- prefer non-null values when configured;
- return at most `selectSampleSize` rows;
- execute SELECT only;
- never modify data.

5. Connection handling:

- reuse existing Hikari/DataSource code if available;
- otherwise adapt the supplied provider;
- source database is H2;
- set schema when configured;
- use read-only connection;
- do not open PostgreSQL in Batch 3;
- never log credentials.

6. Parameter serialization:

- deterministic and reversible;
- version `v1`;
- preserve parameter name, type, logical index, every JDBC index and exact value;
- support special characters, numbers, dates/times, Boolean, UUID, bytes and collection size one;
- do not mask values;
- this exact codec must be reused later in Batch 5.

7. Repeated logical parameter:

- sample one value;
- store one logical value;
- retain all JDBC indexes;
- later binding reuses that same value.

If it maps to different source columns, return manual mapping instead of guessing.

8. Integrate only into `CAPTURE_BASELINE`.

Eligible Batch-2 statuses:

```text
AUTO_COMPARABLE
BASELINE_ONLY
EXECUTION_ONLY
```

Perform planning and H2 sampling, then log:

```text
sampling candidates
sampled SQL
sampled tuples
no sample data
manual mapping required
unsupported
H2 sampling query failures
H2 connection failures
```

Do not write incomplete baseline rows. Keep the baseline CSV header-only until
Batch 4 executes the H2 business SELECT.

`COMPARE_POSTGRES_WITH_BASELINE` remains header-only.

9. Add/adapt tests for:

- equality mapping;
- multi-column same-row tuple;
- LOWER transformation;
- IN collection size one;
- up to three distinct tuples;
- no non-null sample;
- repeated logical parameter;
- serialization round trip with special characters and types;
- JOIN rejected;
- OR rejected;
- LIKE rejected;
- subquery rejected;
- H2 query failure isolation;
- no PostgreSQL connection;
- existing tests remain green.

## Hard restrictions

Do not implement:

- H2 business SELECT execution;
- PostgreSQL connection or execution;
- baseline data rows;
- business ResultSet normalization;
- row count/hash/column signature;
- baseline CSV reader;
- current-to-baseline matching;
- Excel changes;
- UPDATE, DELETE or INSERT;
- transactions, rollback or affected rows.

Do not continue to Batch 4.

## Completion actions

1. Compile the project.
2. Run all tests.
3. Run CAPTURE mode using configured H2 and existing CSVs.
4. Confirm:
   - H2 connection succeeds;
   - sampling plans are produced;
   - real tuples are sampled;
   - serialization round-trips;
   - baseline CSV remains header-only;
   - no PostgreSQL connection is opened.
5. Report:
   - files created;
   - files modified;
   - exact binding-plan headers mapped;
   - sampling totals;
   - mapping failure reasons;
   - H2 query failures;
   - compilation and test results;
   - assumptions and unresolved SQL patterns.

Do not claim completion if compilation or tests fail.
