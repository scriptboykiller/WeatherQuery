# Batch 6 — Excel + Optional Performance Integration

## Scope

Batch 6 combines the former Excel and performance batches:

```text
A. Add the third Cross DB sheet to the existing workbook
B. Add the optional performance-report phase
C. Call the colleague's independent performance JAR
D. Add static HTML links to the third sheet
```

## Authoritative performance contract

Use the attached `SQL 性能对比工具集成与实现报告（MVP v2）` for:

- JDBC SQL + Typed Parameters;
- PreparedStatement execution by the colleague's tool;
- top 20 slowest plus manual includes;
- one HTML per SQL;
- Sample 1 preference;
- serial JAR invocation;
- optional and failure-isolated integration.

Override its old detailed result JSON with the minimal contract in:

```text
PERFORMANCE_TOOL_RESULT_CONTRACT.md
```

## Phase behavior

Add:

```text
performance-report
```

to `ValidatorPhase`.

It is optional:

```text
cross-db-validation → excel-report
```

and:

```text
cross-db-validation → performance-report → excel-report
```

are both valid.

Default:

```yaml
enabled: false
```

When disabled, log `Performance report phase is disabled. Phase skipped.` and
exit successfully without calling the JAR or overwriting previous reports.

If enabled but the JAR is missing, fail this phase clearly. This must not alter
baseline or comparison CSV files, and the user can still run excel-report
separately.

## Input precedence

For performance and the Excel third sheet:

1. Use `sql-select-comparison.csv` when it exists and contains rows.
2. Otherwise use `sql-select-baseline.csv`.

## Selection

Eligibility:

```text
SELECT
H2 SUCCESS
PostgreSQL SUCCESS
MATCH when require-result-match=true
both JDBC SQL values present
parameters decodable
placeholder count valid
```

Choose one eligible representative sample per baselineKey:

1. configured default sample index, normally 1;
2. if unavailable among eligible rows, smallest eligible sample index.

Final selected set:

```text
top N by max(h2ObservedTimeMs, postgresObservedTimeMs)
UNION
manual include-sql-ids
```

Run sequentially.

## Request

Validator writes Request JSON v2 containing:

```text
H2 JDBC SQL
PostgreSQL JDBC SQL
the same typed parameters
resultMatchedBeforePerformanceTest=true
```

Do not render complete literal SQL.

## Output

Per selected SQL:

```text
output/performance/requests/<caseId>.json
output/performance/reports/<caseId>/index.html
output/performance/reports/<caseId>/result.json
```

Validator output:

```text
output/sql-performance-report.csv
```

The CSV contains statuses for all Cross DB sample rows, so Excel can show:

```text
Open Performance Report
Covered by Sample 1
Not Selected
Not Eligible
Parameter Decode Failed
Timeout
Report Missing
Report Generation Failed
```

## Excel third sheet

Sheet name:

```text
Cross DB Validation
```

Columns:

```text
Baseline Key
Baseline SQL ID
Current SQL ID
Sample Index
Parameter Values
H2 Status
PostgreSQL Status
Result Comparison
H2 Observed Time (ms)
PostgreSQL Observed Time (ms)
Performance Report
```

If performance-report was skipped and the performance CSV does not exist:

```text
Performance Report = Not Generated
```

If HTML exists, create a FILE hyperlink to the static report. Excel must not
start a JAR, localhost service or macro.

## Integration points to adapt

Copilot must adapt the reference code to the actual Batch 1–5 project:

- exact `ParameterTuple` and `ParameterTupleValue` accessor names;
- actual `ParameterTupleCodec.decode` signature;
- actual placeholder counter;
- actual CSV header names from Batch 4 and Batch 5;
- existing Excel workbook service and style factory;
- existing phase runner architecture;
- existing Spring configuration registration.

Do not duplicate existing readers, codecs, enums or workbook utilities.

## Not in Batch 6

- final release ZIP;
- BAT scripts;
- USER_GUIDE;
- checksum generation;
- source-code handover;
- DML performance testing.

Those belong to the final Batch 7.
