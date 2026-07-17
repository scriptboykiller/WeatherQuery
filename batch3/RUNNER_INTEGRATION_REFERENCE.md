# Batch 3 runner integration reference

Only `CAPTURE_BASELINE` performs H2 sampling in Batch 3.

After Batch-2 candidate aggregation and static eligibility:

```java
List<CandidateAnalysis> eligibleForSampling = analyses.stream()
        .filter(item -> switch (item.eligibility().status()) {
            case AUTO_COMPARABLE, BASELINE_ONLY, EXECUTION_ONLY -> true;
            default -> false;
        })
        .toList();
```

Open one H2 connection for the batch when practical. For every eligible
candidate:

```java
List<BindingParameter> bindings = bindingsBySqlId
        .getOrDefault(candidate.sqlId(), List.of());

SamplingPlan plan = mappingPlanner.plan(candidate.jdbcSql(), bindings);
SamplingOutcome outcome = sampleProvider.sample(
        h2Connection,
        plan,
        properties.getSelectSampleSize(),
        properties.isPreferNonNullValues(),
        properties.getStatementTimeoutMs()
);
```

Log totals for:

```text
sampling candidates
sampled SQL
sampled tuples
no sample data
manual mapping required
unsupported
H2 query failures
H2 connection failures
```

Do not write data rows to `sql-select-baseline.csv` yet. Until Batch 4 executes
the actual H2 business SELECT and produces row count/hash, the CSV remains
header-only.

`COMPARE_POSTGRES_WITH_BASELINE` remains header-only in Batch 3.
