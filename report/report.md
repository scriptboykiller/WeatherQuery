I have copied the generated Excel report MVP code into the existing
sql-postgres-validator project.

Please read:
docs/INTEGRATION_PATCHES.md

Apply only the required integration changes:

1. Add Apache POI poi-ooxml 5.5.1.
2. Add EXCEL_REPORT("excel-report") to ValidatorPhase.
3. Add ReportProperties to ValidatorProperties.
4. Add the validator.report configuration to application.yml.
5. Fix only compilation issues caused by minor differences in the existing project.
6. Preserve the existing inventory, sanity and validation implementation.
7. Do not redesign or rewrite existing classes.
8. Do not modify business microservice modules.
9. Keep JDK 17 compatibility.

After compiling, set:

validator:
  phase: excel-report

The expected output is:

./output/sql-postgres-migration-report.xlsx
