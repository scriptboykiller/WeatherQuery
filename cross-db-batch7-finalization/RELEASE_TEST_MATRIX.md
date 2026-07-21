# Release Test Matrix

| ID | Scenario | Expected |
|---|---|---|
| B7-01 | Capture first run | Baseline data rows written |
| B7-02 | Capture with protected Baseline | Fails before DB execution |
| B7-03 | Compare valid Baseline | Comparison CSV written |
| B7-04 | Compare mode | No H2 connection |
| B7-05 | Missing/ambiguous baselineKey | Explicit output row |
| B7-06 | One SQL timeout | Remaining SQL continues |
| B7-07 | Excel without performance CSV | Workbook generated |
| B7-08 | Performance disabled | Not Generated |
| B7-09 | Comparator missing | Performance-only clear failure |
| B7-10 | Minimal SUCCESS result + HTML | Accepted |
| B7-11 | SUCCESS result without HTML | REPORT_MISSING |
| B7-12 | caseId mismatch | Rejected |
| B7-13 | Path contains spaces | BAT works |
| B7-14 | Release content scan | No source/Git/tests/secrets |
| B7-15 | Checksum verification | All files pass |
