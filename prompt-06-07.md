先用 **Ask 模式读**，不要一上来 Plan 或 Agent。

你现在应该分三步走：

## 第一步：Ask 模式，只让它理解文档

在 Copilot Chat 里选 **Ask**，发这个：

```text
Please read these two documents first:

docs/10_Phase2_PostgreSQL_Validation_Final_Design.md
docs/11_Phase2_PostgreSQL_Validation_Code_Guide.md

Do not modify any code yet.

This project is already a working JDK 17 Spring Boot CLI tool.
Phase 1 and Phase 1.5 are already implemented and tested.

Your task now is only to understand:
1. The Phase 2 target architecture.
2. The required validation phases:
   - validation-explain
   - validation-select-smoke
   - validation-dml-safety
   - real-execution
3. The required reports:
   - sql-binding-plan.csv
   - sql-execution-report.csv
4. The existing package structure under org.rosetta.sqlvalidator.
5. The safety rules and coding standards.

After reading, summarize your understanding and list the files you think would need to be added or changed.
Do not implement anything yet.
```

这一步目的：**让它先读懂，不动代码。**

---

## 第二步：Plan 模式，让它出实施计划

等 Ask 总结没问题后，再切 **Plan**，发：

```text
Now create an implementation plan for Phase 2.1 only.

Scope:
- Implement validation-explain only.
- Generate sql-binding-plan.csv.
- Generate sql-execution-report.csv.
- Add PostgreSQL EXPLAIN validation.
- Add basic Binding Plan generation.
- Add identifierStrategy configuration.
- Add SQLState-based error classification.

Do not implement:
- validation-select-smoke
- validation-dml-safety
- real-execution
- automatic SQL rewrite
- business code modification

Please provide a step-by-step plan and the exact files to add or modify.
Do not change code yet.
```

这一步目的：**确认它不会一次性把 2.2、2.3、2.4 全做了。**

---

## 第三步：Agent 模式，才让它改代码

Plan 你看完没问题后，才用 **Agent**：

```text
Implement Phase 2.1 validation-explain only according to the approved plan.

Hard constraints:
1. Do not rewrite existing Phase 1 or Phase 1.5 logic.
2. Do not modify business service modules.
3. Do not implement select smoke, DML safety, or real execution.
4. Do not auto-fix SQL.
5. Keep JDK 17 and Spring Boot CLI architecture.
6. Follow existing package structure and coding style.
7. Use SLF4J logging.
8. Generate both:
   - sql-binding-plan.csv
   - sql-execution-report.csv
9. Make it runnable by:
   --validator.phase=validation-explain
```

结论就是：

```text
Ask 读文档
Plan 出计划
Agent 改代码
```

你现在这一步，**先用 Ask**。
