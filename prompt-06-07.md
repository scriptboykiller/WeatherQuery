Please read these two documents first:

docs/06_SQL_Text_Sanity_Check_Design.md
docs/07_SQL_Text_Sanity_Check_Code.md

This is an incremental update to an existing working Java project.

Do not rewrite the existing project.
Do not redesign the scanner.
Do not modify business service modules.

The goal is to add Phase 1.5 SQL Text Sanity Check into the existing sql-postgres-validator project.

We are using JDK 17.

Please implement Phase 1.5 by following docs/07_SQL_Text_Sanity_Check_Code.md.

Apply the code into the existing project structure.

Required changes:
1. Add JSqlParser dependency to pom.xml.
2. Add the new package:
   com.company.sqlvalidator.sanity
3. Add the classes described in 07_SQL_Text_Sanity_Check_Code.md.
4. Add optional sanity.input and sanity.outputDir properties to application.properties.
5. Keep existing Phase 1 scanner code unchanged unless compilation requires a minor compatible change.

Do not:
1. Connect to H2.
2. Connect to PostgreSQL.
3. Generate JUnit.
4. Implement SQL rewrite rules.
5. Modify business service modules.
6. Rewrite the whole existing scanner.

Please update Phase 1.5 SqlParserSanityChecker.prepareForParser only.

Before calling JSqlParser:
1. Convert named parameters like :modelId to ?
2. Convert indexed positional parameters like ?1, ?2, ?12 to ?
3. Keep normal JDBC ? unchanged
4. Do not change original effectiveSqlText
5. Only change parserSql

Example:
where a = :name and b = ?1 and c = ?
should become:
where a = ? and b = ? and c = ?

----------
Create a repository-level GitHub Copilot instruction file for this project.

Requirements:
1. Create the directory `.github` at the Git repository root if it does not exist.
2. Create `.github/copilot-instructions.md`.
3. First inspect the project structure and existing coding conventions.
4. Generate only concise, stable, repository-wide instructions.
5. Include:
   - actual technology stack detected from the project
   - existing coding style and naming conventions
   - minimal-change principle
   - do not change business behavior unless explicitly requested
   - do not perform unrelated refactoring
   - follow existing project patterns before introducing new abstractions
   - keep changes small and reviewable
   - do not guess project facts
6. Do NOT include temporary task status.
7. Do NOT include current migration progress.
8. Do NOT create HANDOFF.md yet.
9. Do NOT modify production source code.
10. Show me the proposed content before writing the file.
