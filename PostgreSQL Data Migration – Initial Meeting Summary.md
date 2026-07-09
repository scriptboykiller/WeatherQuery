# PostgreSQL Data Migration – Initial Meeting Summary

## 1. Meeting Background

This meeting was the first formal discussion on the **Oracle / H2 to Cloud PostgreSQL data migration** initiative.

The overall objective is to complete the core migration, SQL compatibility remediation, and key performance optimization work within **Q3**, with **end of September** as the main delivery deadline and full production rollout targeted by **year-end**.

### Key Constraints Confirmed

| Area                  | Summary                                                      |
| --------------------- | ------------------------------------------------------------ |
| Production Access     | The team does not have direct production DB access. Production data can only be exported to DEV/SIT/UAT for testing. |
| Cutover Strategy      | Production migration will be performed during a short downtime window, currently expected to be around 4–5 minutes. |
| Environment Readiness | Only one SIT cloud PostgreSQL environment is currently available. DEV/UAT/PROD DB setup is still pending DBA/Infra confirmation. |
| DB Architecture       | No sharding or service-level database split. All business services share the same database. |
| Environment Model     | DBA still needs to confirm whether environments will use separate PostgreSQL instances or separate schemas. |

------

## 2. In-Scope Services

Only services with significant Native SQL usage require focused remediation.

| Service         | Migration Impact |
| --------------- | ---------------- |
| DRM             | High             |
| Workflow        | High             |
| API Integration | High             |
| OS              | Medium           |
| Entitlement     | Medium           |
| Graph           | Medium           |

Infrastructure services such as Gateway, Eureka, and Config are considered out of scope for SQL remediation because they do not contain significant business SQL.

------

## 3. Main Workstreams and Ownership

| #    | Workstream                                        | Owner                | Key Notes                                                    |
| ---- | ------------------------------------------------- | -------------------- | ------------------------------------------------------------ |
| 1    | Obtain PostgreSQL credentials and schema access   | Project Lead         | Highest priority blocker. Required before migration validation and SQL testing can proceed. |
| 2    | Refactor Java migration script                    | Project Lead + Bryan | Existing Oracle-to-Hadoop migration utility must be redesigned for PostgreSQL. |
| 3    | Extract Native SQL and generate JUnit tests       | Dexter               | Automatically identify SQL, infer parameters, and generate executable PostgreSQL validation tests. |
| 4    | Fix SQL dialect and function compatibility issues | Dexter               | Focus on H2/Oracle functions, reserved keywords, date syntax, and PostgreSQL differences. |
| 5    | Build SQL performance monitoring tool             | Jerry                | Test-environment-only AOP monitoring to capture slow SQL and generate Excel reports. |
| 6    | Optimize slow SQL                                 | Development Team     | Based on monitoring results, optimize SQL, indexes, and related DB configuration. |
| 7    | Risk analysis and effort estimation               | Jerry                | Current Sprint includes 3 days for analysis, task breakdown, and hour-based estimation. |
| 8    | Overall coordination                              | Project Lead         | Coordinate DBA, Infra, Bryan, Dexter, Jerry, and supporting developers. |

------

## 4. Key Technical Decisions

### 4.1 Production Cutover Approach

The agreed direction is a **controlled downtime full migration**.

Planned flow:

1. Stop all business services.
2. Execute the Java full migration script.
3. Validate migrated tables, indexes, views, and row counts.
4. Execute additional PostgreSQL index scripts if required.
5. Restart services.
6. Perform smoke testing.

The target migration duration is approximately **3–4 minutes**, which must be repeatedly validated against production-scale data before go-live.

------

### 4.2 Migration Script Requirements

The refactored Java migration script should support:

| Requirement         | Details                                                      |
| ------------------- | ------------------------------------------------------------ |
| Object Migration    | Tables, indexes, views, and full business data.              |
| Data Validation     | Compare source and target object counts and per-table row counts. |
| Performance         | Full migration should complete within the production downtime window. |
| Test Data           | Use production-like data exported to DEV/SIT/UAT.            |
| Production Strategy | Stop all services before migration to avoid incremental data differences. |

Minor row differences in non-production testing may be acceptable only for dynamic technical/logging tables and must be manually reviewed.

------

### 4.3 Native SQL Validation Strategy

The team agreed to use a layered validation approach.

| Layer                  | Purpose                               | Method                                                    |
| ---------------------- | ------------------------------------- | --------------------------------------------------------- |
| Automated Validation   | Detect PostgreSQL syntax errors       | AI/Copilot-assisted JUnit generation with mock parameters |
| Manual SQL Validation  | Validate rewritten complex SQL        | Use realistic business parameters                         |
| Developer Verification | Detect runtime and performance issues | Developers manually operate key business pages            |
| QA Regression          | Final business validation             | Full regression testing                                   |

The automated JUnit tests only need to confirm that SQL can execute successfully in PostgreSQL. They do not need to validate business result accuracy unless the SQL has been manually rewritten or contains complex functions.

------

### 4.4 PostgreSQL Environment Options

DBA/Infra still needs to confirm the final setup.

| Option   | Description                                                  |
| -------- | ------------------------------------------------------------ |
| Option A | One PostgreSQL instance with separate schemas for DEV/SIT/UAT/PROD |
| Option B | Separate PostgreSQL instances for DEV/SIT/UAT/PROD           |

Application code should remain unchanged where possible. Differences should be handled through environment-specific configuration.

------

## 5. Performance Monitoring and Optimization

Jerry will develop a test-environment-only SQL monitoring tool.

The tool should:

- capture SQL execution time through AOP;
- identify slow queries;
- generate Excel reports sorted by duration;
- distinguish background scheduled jobs from user-facing queries;
- support later integration with Grafana if feasible.

Initial performance guidance:

| Query Type                  | Initial Expectation                                   |
| --------------------------- | ----------------------------------------------------- |
| Background jobs             | 10–30 seconds may be acceptable depending on workload |
| Front-end real-time queries | Should ideally complete within 8 seconds              |

After Dexter completes SQL compatibility remediation, developers will manually exercise business pages and workflows. The collected slow SQL will then be reviewed and optimized through SQL rewriting, PostgreSQL indexes, and database tuning where required.

------

## 6. Task Dependencies

The main dependency chain is:

| Step                             | Dependency                                             |
| -------------------------------- | ------------------------------------------------------ |
| PostgreSQL credentials available | Required to unblock all DB validation work             |
| Migration script ready           | Required before full data migration rehearsal          |
| Production-like data migrated    | Required before SQL validation and performance testing |
| Native SQL tests generated       | Required to detect PostgreSQL incompatibilities        |
| SQL compatibility fixed          | Required before business testing and monitoring        |
| Monitoring deployed              | Required before slow SQL collection                    |
| Slow SQL optimized               | Required before final regression and cutover readiness |

Jerry’s monitoring tool development and risk analysis can proceed in parallel and do not need to wait for Dexter’s SQL remediation work.

------

## 7. Key Risks

| Risk                                            | Impact                          | Mitigation                                           |
| ----------------------------------------------- | ------------------------------- | ---------------------------------------------------- |
| PostgreSQL credentials delayed                  | Blocks migration and validation | Escalate to Cloud/DBA team immediately               |
| Large amount of incompatible SQL                | May increase remediation effort | Use automated extraction and JUnit validation        |
| Parameter inference for SQL tests is inaccurate | May require manual correction   | Improve script logic and manually handle complex SQL |
| PostgreSQL performance issues                   | May affect user experience      | Use AOP monitoring and slow SQL optimization         |
| Multi-environment setup unclear                 | Affects DEV/UAT/PROD planning   | Confirm topology with DBA/Infra                      |
| Migration exceeds downtime window               | High production risk            | Perform repeated production-like rehearsals          |
| Data mismatch after migration                   | Critical                        | Use automated row-count and object-count validation  |
| Test data is not fresh enough                   | May miss production issues      | Request updated production exports before rehearsal  |

------

## 8. Immediate Action Items

| Priority | Action                                                    | Owner                    |
| -------- | --------------------------------------------------------- | ------------------------ |
| P0       | Obtain PostgreSQL account, password, and schema access    | Project Lead             |
| P0       | Confirm DEV/SIT/UAT/PROD PostgreSQL deployment model      | Project Lead + DBA/Infra |
| P1       | Refactor the Java migration script                        | Project Lead + Bryan     |
| P1       | Define migration validation report format                 | Project Lead + Bryan     |
| P1       | Start Native SQL inventory and JUnit generation design    | Dexter                   |
| P1       | Develop SQL performance monitoring tool                   | Jerry                    |
| P1       | Complete risk analysis and effort estimation              | Jerry                    |
| P2       | Run SQL compatibility validation after PG access is ready | Dexter                   |
| P2       | Collect slow SQL through business scenario testing        | Development Team         |
| P2       | Optimize SQL and prepare PostgreSQL index scripts         | Development Team         |
| P2       | Execute full QA regression                                | QA                       |

------

## 9. Summary

The meeting confirmed the initial scope, ownership, technical direction, and major risks for the PostgreSQL migration.

The migration is feasible because the system does not involve sharding or service-level database separation. However, several critical items must be resolved early, especially PostgreSQL access, environment topology, migration script validation, SQL compatibility effort, and performance risk.

The agreed approach is to proceed with a **risk-first and validation-driven migration plan**, using production-like data, automated SQL validation, manual business verification, slow SQL monitoring, and repeated migration rehearsals before final production cutover.