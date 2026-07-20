# Batch 4 Runner Integration Reference

Conceptual integration only:

```java
if (mode != CrossDatabaseMode.CAPTURE_BASELINE) {
    comparisonWriter.write(comparisonOutput, List.of());
    return;
}

baselineProtection.validateBeforeCapture(
        baselineOutput,
        failIfBaselineExists
);

List<CandidateAnalysis> analyses = batch2Analyze();
List<CandidateSamplingResult> sampled = batch3Sample(analyses);

boolean needsPostgres = sampled.stream()
        .anyMatch(result -> "PASSED".equalsIgnoreCase(
                result.analysis().candidate().postgresValidationStatus()));

try (Connection h2 = h2Provider.openConnection();
     Connection postgres = needsPostgres
             ? postgresProvider.openConnection()
             : null) {

    List<SelectBaselineRecord> rows = new ArrayList<>();

    for (CandidateSamplingResult item : sampled) {
        try {
            SqlResult capture = baselineCaptureService.capture(
                    item,
                    h2,
                    postgres,
                    maxSelectRows,
                    statementTimeoutMs
            );
            rows.addAll(recordAssembler.assemble(capture, runMetadata));
        } catch (Exception exception) {
            // Convert this candidate/sample to failure records and continue.
        }
    }

    baselineWriter.write(baselineOutput, rows);
}
```

Write the final CSV once, after processing all candidates.
