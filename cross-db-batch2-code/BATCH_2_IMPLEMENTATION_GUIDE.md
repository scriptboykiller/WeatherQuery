# Batch 2 Implementation Guide

## Goal

Batch 2 adds only:

- Reading the current inventory, binding-plan and execution-report CSV files.
- Aggregating exactly one candidate per `sqlId`.
- Generating the stable `baselineKey`.
- Detecting missing or duplicate baseline identities.
- Static SELECT-only eligibility decisions.
- Candidate-count logging in the Batch-1 runner.
- Unit tests.

It does not add database access, parameter sampling, execution, hashing,
comparison records or Excel changes.

## Important assumptions

The code uses package root:

```text
org.rosetta.sqlvalidator
```

Batch 1 already contains:

```text
org.rosetta.sqlvalidator.crossdb.eligibility.EligibilityStatus
```

Reuse that enum. If Batch 1 placed it elsewhere, adapt imports; do not create a
second enum.

The inventory headers are based on the confirmed current schema. The exact
binding-plan and execution-report headers were not provided, so the reader
accepts aliases. Copilot must inspect the current CSV writers and align the
aliases with the real headers before committing.

## Runner integration

Update the Batch-1 runner only enough to:

```java
List<CurrentSqlInventoryRow> inventory =
        currentSqlInputReader.readInventory(properties.getInventoryInput());

Map<String, BindingPlanSnapshot> bindings =
        currentSqlInputReader.readBindingPlans(properties.getBindingPlanInput());

Map<String, ExecutionReportSnapshot> executions =
        currentSqlInputReader.readExecutionReports(properties.getExecutionInput());

List<CrossDatabaseCandidate> candidates =
        candidateAggregator.aggregate(inventory, bindings, executions);

List<CandidateAnalysis> analyses =
        candidateAnalysisService.analyze(candidates);

CandidateAnalysisSummary summary =
        candidateAnalysisService.summarize(analyses);
```

Log counts by `EligibilityStatus`.

Do not write partial baseline records in Batch 2. A header-only CSV remains the
correct output until H2 execution is implemented.

For `COMPARE_POSTGRES_WITH_BASELINE`, keep writing the header-only comparison
CSV. Baseline reading and current-to-baseline matching belong to Batch 5.

## Spring registration

Register these stateless objects using the existing project style:

- `BaselineKeyFactory`
- `JdbcPlaceholderCounter`
- `SqlCanonicalizer`
- `CurrentSqlInputReader`
- `CrossDatabaseCandidateAggregator`
- `SelectSafetyInspector`
- `CrossDatabaseEligibilityEvaluator`
- `CrossDatabaseCandidateAnalysisService`

Use explicit `@Bean` methods or component annotations according to the current
project. Do not add Lombok for this batch.

## Compile-first rule

Adapt only package, constructor, getter and existing-reader differences.
Compile, run tests, and stop before Batch 3.
