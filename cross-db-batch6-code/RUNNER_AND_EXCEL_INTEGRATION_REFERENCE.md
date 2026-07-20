# Runner and Excel Integration Reference

## ValidatorPhase

Add:

```java
PERFORMANCE_REPORT("performance-report")
```

## Dispatcher

Conceptual branch:

```java
case PERFORMANCE_REPORT -> {
    if (!performanceProperties.isEnabled()) {
        log.info("Performance report phase is disabled. Phase skipped.");
        return;
    }
    performanceReportService.run(performanceProperties);
}
```

Do not automatically run performance from cross-db-validation or excel-report.
It remains an independent, explicitly selected phase.

## Excel report

Inside the existing Excel phase:

```java
List<PerformanceSourceRow> crossDbRows =
        crossDbInputReader.readPreferred(
                performanceProperties.getCrossDbComparisonInput(),
                performanceProperties.getCrossDbBaselineInput());

boolean performanceFilePresent = Files.isRegularFile(
        performanceProperties.getResultOutput());

List<PerformanceReportRecord> performanceRows =
        performanceReportCsvReader.readOptional(
                performanceProperties.getResultOutput());

List<CrossDatabaseExcelRow> excelRows =
        crossDatabaseExcelRowAssembler.assemble(
                crossDbRows,
                performanceRows,
                performanceFilePresent);

crossDatabaseSheetWriter.replaceSheet(
        workbook,
        excelProperties.getCrossDatabaseSheetName(),
        excelRows);
```

The existing Summary and Detail sheets must remain unchanged.

The workbook continues to be recreated as one file:

```text
sql-postgres-migration-report.xlsx
```
