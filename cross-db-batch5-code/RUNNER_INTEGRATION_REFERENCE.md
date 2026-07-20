# Batch 5 Runner Integration Reference

Conceptual flow:

```java
List<SelectBaselineSnapshot> baselines =
        baselineReader.read(properties.getBaselineInput());

List<CrossDatabaseCandidate> currentCandidates =
        candidateAggregator.aggregate(
                inventoryRows,
                bindingPlans,
                executionReports
        );

CurrentSqlBaselineMatcher matcher =
        new CurrentSqlBaselineMatcher(currentCandidates);

List<PreparedComparison> prepared = prepare(
        baselines,
        matcher,
        currentNormalizationVersion
);

boolean needsPostgres = prepared.stream()
        .anyMatch(item -> item.eligibility().executable());

try (Connection postgres = needsPostgres
        ? postgresProvider.openConnection()
        : null) {

    List<BaselineComparisonOutcome> outcomes = new ArrayList<>();

    for (PreparedComparison item : prepared) {
        outcomes.add(comparisonService.compare(
                item.baseline(),
                item.match(),
                item.eligibility(),
                postgres,
                maxSelectRows,
                statementTimeoutMs
        ));
    }

    comparisonWriter.write(
            properties.getComparisonOutput(),
            comparisonRecordAssembler.assemble(
                    outcomes,
                    postgresDatabase,
                    postgresSchema
            )
    );
}
```

Do not touch the CAPTURE branch or the baseline CSV.
