# Release Acceptance Checklist

## Build
- [ ] JDK 17 and executable Spring Boot Fat JAR.
- [ ] `java -jar` works with external YAML.
- [ ] All tests pass.
- [ ] No source or test files in Release.

## Cross DB
- [ ] Capture creates real Baseline rows.
- [ ] Existing Baseline is not silently overwritten.
- [ ] Compare uses `baselineKey` and saved parameters.
- [ ] Compare does not connect to H2.
- [ ] Comparison CSV contains unresolved rows as explicit statuses.

## Excel and Performance
- [ ] Three Excel sheets work.
- [ ] Missing performance CSV does not fail Excel.
- [ ] Performance disabled shows `Not Generated`.
- [ ] Missing comparator affects only performance execution.
- [ ] Minimal result JSON v2 is accepted.
- [ ] SUCCESS requires a real HTML file.

## Scripts
- [ ] Paths with spaces work.
- [ ] Missing Java/JAR/YAML gives a clear error.
- [ ] Java exit code is preserved.
- [ ] Real Execution requires confirmation.

## Security
- [ ] Passwords are not logged.
- [ ] Release has no `.git`, `.java`, tests, IDE files or real company source paths.

## Delivery
- [ ] VERSION.txt, SHA256SUMS.txt and ZIP generated.
- [ ] USER_GUIDE and TROUBLESHOOTING finalized.
- [ ] Performance comparator documented as OPTIONAL / NOT INCLUDED.
