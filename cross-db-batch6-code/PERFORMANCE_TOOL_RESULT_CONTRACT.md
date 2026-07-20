# Performance Tool Result Contract — Final MVP v2

The attached performance integration report remains authoritative except that
its detailed `result.json` example is replaced by this minimal contract.

The performance comparator must return:

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

Detailed timing values remain in the HTML report and are not required in
`result.json`.

Validator success condition:

```text
exitCode = 0
AND responseVersion = v2
AND result.caseId = request.caseId
AND result.status = SUCCESS
AND the returned HTML exists inside the case output directory
```

The tool should still return a non-zero exit code and a result JSON whenever
possible for these statuses:

```text
INVALID_REQUEST
H2_EXECUTION_FAILED
POSTGRES_EXECUTION_FAILED
BOTH_EXECUTION_FAILED
REPORT_GENERATION_FAILED
TIMEOUT
INTERNAL_ERROR
```
