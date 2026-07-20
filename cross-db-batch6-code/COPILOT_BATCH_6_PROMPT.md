# Copilot Batch 6 Prompt

Read:

1. the authoritative Cross DB v2 documents;
2. the attached performance integration MVP v2 report;
3. `PERFORMANCE_TOOL_RESULT_CONTRACT.md`;
4. `BATCH_6_IMPLEMENTATION_GUIDE.md`;
5. the supplied Java reference code.

Implement ONLY Batch 6:

```text
Excel third Cross DB sheet
+
optional performance-report phase
+
JDBC SQL + Typed Parameters request generation
+
serial external JAR invocation
+
minimal result.json parsing
+
static HTML links
```

## Precondition

Confirm Batch 1–5 compile and these real files can be generated:

```text
sql-select-baseline.csv
sql-select-comparison.csv
```

Do not continue if Batch 5 regression tests fail.

## Performance result contract

Use exactly:

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

Do not require average/min/max timing fields from result.json. Detailed timing
belongs only to the HTML.

## Required implementation

1. Add `PERFORMANCE_REPORT("performance-report")` to ValidatorPhase.

2. Add configuration with default:

```yaml
enabled: false
```

3. When disabled:

- log a clear SKIPPED message;
- return success;
- do not call the JAR;
- do not overwrite existing performance output.

4. Read comparison CSV first; use baseline CSV only when comparison is absent
or has no data rows.

5. Select eligible SQL:

- SELECT;
- H2 SUCCESS;
- PostgreSQL SUCCESS;
- MATCH by default;
- H2 and PostgreSQL JDBC SQL present;
- saved parameter tuple decodes;
- placeholder counts match.

6. Choose one representative eligible sample per SQL:

- default Sample 1;
- otherwise smallest eligible sample index.

7. Select:

```text
top 20 slowest
UNION
include-sql-ids
```

using `max(h2ObservedTimeMs, postgresObservedTimeMs)`.

8. Reuse Batch-3 `ParameterTupleCodec` and current parameter model.
Do not render literal SQL.

9. Generate Request JSON v2 with typed parameters. Expand repeated JDBC
indexes and already-expanded collection parameters correctly.

10. Invoke the independent JAR with ProcessBuilder:

```text
java -jar <toolJar>
--request-file=<request.json>
--output-dir=<case directory>
--config=<performance-tool.yml>
```

Arguments must be separate ProcessBuilder list entries, so Windows paths with
spaces work.

11. Run cases sequentially.

12. Clean only the selected case's report directory before invoking it, to
avoid accepting stale index.html.

13. Success requires:

```text
exit code 0
result.json responseVersion v2
matching caseId
status SUCCESS
HTML exists inside the case output directory
```

Reject path traversal in htmlReport.

14. Continue after individual case failure and write a status row.

15. Generate:

```text
sql-performance-report.csv
```

with no detailed timing columns from the external tool.

16. Extend the existing Excel phase with a third sheet:

```text
Cross DB Validation
```

Keep Summary and Detail unchanged.

17. If performance CSV is absent, display `Not Generated` and still generate
Excel successfully.

18. For successful reports, write a static FILE hyperlink. Do not start a JAR,
localhost service or Excel macro.

19. Tests at minimum:

- disabled performance phase skips cleanly;
- comparison input preferred over baseline;
- eligibility rules;
- default Sample 1 selection;
- fallback to smallest eligible sample;
- top N plus manual union and de-duplication;
- repeated parameter JDBC indexes;
- NULL, decimal, date, timestamp, UUID and bytes mapping;
- placeholder mismatch;
- minimal result JSON parsing;
- mismatched caseId;
- missing HTML after SUCCESS;
- timeout;
- one case failure does not stop next case;
- all source samples receive performance CSV statuses;
- Excel third sheet exists;
- performance file absent → Not Generated;
- successful HTML creates a FILE hyperlink;
- Summary and Detail remain unchanged;
- Batch 1–5 regression tests remain green.

## Hard restrictions

Do not:

- alter baseline capture;
- alter baseline comparison correctness logic;
- reconnect to H2 inside Validator performance phase;
- create complete literal SQL;
- parse HTML for machine status;
- require detailed timings in result.json;
- run performance tests concurrently;
- make performance mandatory;
- implement DML performance tests;
- build the final release package.

Do not continue to Batch 7.

## Completion report

After implementation, report:

- files created/modified;
- compilation and test results;
- whether the colleague's JAR was available;
- enabled/disabled behavior;
- Cross DB input file selected;
- total source rows;
- eligible SQL groups;
- top-N selections;
- manual selections;
- successful HTML reports;
- tool failures/timeouts/missing reports;
- performance CSV path;
- Excel output path;
- third-sheet row count;
- unresolved parameter types or CSV header adaptations.

Do not claim completion when compilation or tests fail.
