可以。下面这套就是给你现在 VS Code + GitHub Copilot + PG 迁移项目 用的 MVP。目标不是搞复杂自动化，而是做到：

> Copilot 自动提醒上下文风险，自动帮你更新 HANDOFF，新会话能干净接上。



你只需要建这几个文件：

.github/
├── copilot-instructions.md
├── prompts/
│   ├── context-checkpoint.prompt.md
│   └── update-handoff.prompt.md
└── agents/
    └── session-guardian.agent.md

HANDOFF.md


---

1. .github/copilot-instructions.md

这个放长期规则。

# Copilot Project Instructions

## Project Context

This repository contains React frontend and Spring Boot microservices.

The current major workstream is H2 to PostgreSQL migration.

The migration work must be done gradually, safely, and phase by phase.

## Hard Constraints

- Do not change production business behavior unless explicitly approved.
- Do not introduce Docker.
- Do not require admin rights.
- Do not depend on external uploads.
- Do not assume access to production database.
- Keep changes small, reviewable, and reversible.
- Prefer local file-based analysis and generated reports.
- Do not perform large refactors unless explicitly requested.

## Migration Phases

### Phase 1

SQL discovery and extraction only.

Allowed:
- scan Java / Spring Boot source files
- identify native SQL
- extract SQL locations
- generate local reports
- create Spring Boot CLI skeleton

Not allowed:
- SQL rewrite
- Flyway migration generation
- PostgreSQL compatibility transformation
- production code modification

### Phase 1.5

PostgreSQL compatibility classification.

Allowed:
- classify extracted SQL
- identify H2 / Oracle incompatible syntax
- suggest rewrite categories

Not allowed:
- mass rewrite without approval
- schema migration ownership assumptions

### Phase 2

Verification and test runner.

Allowed:
- create test skeletons
- create verification runner
- generate execution reports

Not allowed:
- force full automation before Phase 1 and Phase 1.5 are validated

## Coding Rules

- Follow existing Java and Spring Boot style.
- Prefer clear names over clever abstractions.
- Avoid unsafe SQL string concatenation.
- Prefer parameterized SQL.
- Do not silently swallow exceptions.
- Add comments only where logic is non-obvious.
- Keep generated code easy to review.

## Context Management Policy

Actively protect the conversation context.

When the session becomes large or tool results accumulate:

- Warn before broad workspace searches.
- Warn before reading many files.
- Prefer targeted inspection over full repository scans.
- Summarize long logs and tool results.
- Do not paste full logs unless requested.
- If context appears heavily loaded, recommend `/compact`.
- If the session has already been compacted multiple times, recommend updating HANDOFF.md and starting a new session.
- If obsolete decisions or rejected approaches start influencing the answer, warn that the context may be polluted.
- Never silently continue broad context expansion.

## Output Expectations

For every implementation task, provide:

1. changed files
2. what changed
3. how to run or verify
4. assumptions
5. risks
6. next step

Do not mix unrelated phases in one answer.


---

2. HANDOFF.md

这个放当前交接状态。第一次可以先用这个模板。

# HANDOFF

## 1. Current Objective

Build a phased H2 to PostgreSQL migration helper for Spring Boot microservices.

Current focus: Phase 1 SQL discovery and extraction.

## 2. Current Phase

- Phase: Phase 1
- Scope: SQL discovery and extraction only
- Not in scope:
  - PostgreSQL compatibility rewrite
  - Flyway migration generation
  - full verification runner
  - production business code modification

## 3. Last Known Good State

- Code compiles: Unknown
- Tests passed: Unknown
- Last command: Unknown
- Last result: Unknown

## 4. Completed Work

- Project direction confirmed: H2 to PostgreSQL migration.
- Migration should be phase-based.
- Phase 1, Phase 1.5, and Phase 2 should be executable independently.
- HANDOFF mechanism introduced to support clean Copilot sessions.

## 5. Changed Files

| File | Status | Purpose |
|---|---|---|
| .github/copilot-instructions.md | planned | Long-term Copilot rules |
| .github/prompts/context-checkpoint.prompt.md | planned | Context risk check |
| .github/prompts/update-handoff.prompt.md | planned | Semi-automatic handoff update |
| .github/agents/session-guardian.agent.md | planned | Session/context guardian |
| HANDOFF.md | planned | Current project handoff |

## 6. Important Decisions

- Long-term rules belong in `.github/copilot-instructions.md`.
- Current phase status belongs in `HANDOFF.md`.
- Do not rely on long chat history as project memory.
- Use `/compact` when context becomes large.
- Start a new Copilot session after phase completion, repeated compaction, or context pollution.
- Avoid full workspace scanning unless explicitly approved.

## 7. Known Issues / Blockers

- Need to confirm actual module names.
- Need to inspect current project structure.
- Need to decide Phase 1 output format.
- Need to verify whether Spring Boot CLI skeleton already exists.

## 8. Next Immediate Step

1. Read `.github/copilot-instructions.md`.
2. Read this `HANDOFF.md`.
3. Inspect only the project structure needed for Phase 1.
4. Propose a minimal next-step plan before editing files.

## 9. Do Not Do

- Do not implement Phase 1.5 yet.
- Do not implement Phase 2 yet.
- Do not create Flyway scripts yet.
- Do not rewrite SQL yet.
- Do not modify production business logic.
- Do not scan the whole workspace without approval.
- Do not introduce Docker or admin-required tools.

## 10. Resume Prompt

Read `.github/copilot-instructions.md` and `HANDOFF.md` first.

Summarize:

1. current objective
2. current phase
3. next immediate step
4. files you need to inspect
5. assumptions and risks

Do not edit files until I approve the plan.


---

3. .github/prompts/context-checkpoint.prompt.md

这个是你手动触发的“上下文体检”。以后你觉得会话变长，就让 Copilot 跑它。

---
description: Check whether the current Copilot session should continue, compact, update handoff, or start a new session
agent: agent
---

You are performing a context checkpoint.

Do not modify production code.

Review the current session state and decide one of the following actions:

- CONTINUE
- LIMIT_SCOPE
- COMPACT_RECOMMENDED
- COMPACT_REQUIRED
- UPDATE_HANDOFF
- NEW_SESSION_REQUIRED

Use the following decision policy:

## Decision Policy

### CONTINUE

Use when the current task is still clear, context is not overloaded, and no obsolete decisions are influencing the work.

### LIMIT_SCOPE

Use when the session is still usable, but tool results, broad searches, or file reads are increasing context usage.

Recommended action:
- avoid whole-workspace scanning
- inspect only task-related files
- summarize long outputs

### COMPACT_RECOMMENDED

Use when the session is becoming large, but not yet unstable.

Recommended command:

`/compact focus on current phase, changed files, pending tasks, constraints, test status, and do-not-change rules.`

### COMPACT_REQUIRED

Use when the session is clearly large or tool results are dominating the context.

Recommended command:

`/compact focus on current phase, changed files, pending tasks, constraints, test status, known issues, and obsolete attempts to ignore.`

### UPDATE_HANDOFF

Use when:
- a milestone has completed
- the next task is different
- the current session contains many useful decisions
- the user is about to stop work
- the session should be prepared for clean continuation

Recommended action:
Run the update-handoff prompt.

### NEW_SESSION_REQUIRED

Use when:
- the session has already been compacted multiple times
- the assistant repeats obsolete decisions
- the assistant violates project constraints
- the task has switched to a different phase or domain
- context pollution is likely
- the user is moving from one phase to another

Recommended action:
1. update HANDOFF.md
2. close the current Copilot chat
3. start a new empty Copilot chat
4. use the Resume Prompt from HANDOFF.md

## Output Format

Return exactly this structure:

# Context Checkpoint Result

## Decision

One of:
CONTINUE / LIMIT_SCOPE / COMPACT_RECOMMENDED / COMPACT_REQUIRED / UPDATE_HANDOFF / NEW_SESSION_REQUIRED

## Reason

Short explanation.

## Current Risks

- risk 1
- risk 2

## Recommended Next Action

One concrete action only.

## Suggested Prompt

Provide the exact prompt the user should run next.


---

4. .github/prompts/update-handoff.prompt.md

这个是半自动更新 HANDOFF.md 的核心。

---
description: Update HANDOFF.md for clean Copilot session continuation
agent: agent
---

Update `HANDOFF.md` based on the current session, current files, changed files, latest decisions, known issues, and next immediate step.

Do not modify production code.

Only update `HANDOFF.md`.

## Rules

- Keep the handoff concise.
- Do not include long logs.
- Do not include obsolete attempts unless they must be explicitly avoided.
- Do not duplicate all long-term rules from `.github/copilot-instructions.md`.
- Preserve important decisions.
- Preserve changed file list.
- Preserve current phase.
- Preserve known issues and blockers.
- Preserve exact next step.
- If information is uncertain, write `Unknown`.
- Do not guess test results.
- Do not invent completed work.
- Do not include irrelevant chat history.

## Required HANDOFF Structure

Use this exact structure:

# HANDOFF

## 1. Current Objective

## 2. Current Phase

## 3. Last Known Good State

## 4. Completed Work

## 5. Changed Files

| File | Status | Purpose |
|---|---|---|

## 6. Important Decisions

## 7. Known Issues / Blockers

## 8. Next Immediate Step

## 9. Do Not Do

## 10. Resume Prompt

## Last Updated

Use current date and time if available. Otherwise write `Unknown`.

## Quality Bar

The updated HANDOFF.md must allow a new Copilot session to continue safely without reading the old chat history.

Before saving, verify:

- Is the current phase clear?
- Is the next step clear?
- Are forbidden actions clear?
- Are changed files listed?
- Are uncertain facts marked as Unknown?
- Are old failed attempts removed or clearly marked as obsolete?


---

5. .github/agents/session-guardian.agent.md

这个是更智能一点的 Agent。它不写业务代码，只负责判断是否该继续、压缩、交接、新开。

---
name: session-guardian
description: Protect Copilot context, decide when to compact, update handoff, or start a new session
tools: ['search', 'editFiles']
---

You are the Session Guardian for this repository.

Your job is to protect context quality and prevent long-session pollution.

You do not implement features.
You do not modify production source code.
You may only inspect project context and update handoff-related files when explicitly asked.

## Responsibilities

- Detect when the current session is becoming too large.
- Warn before broad workspace searches.
- Warn before reading many unrelated files.
- Recommend `/compact` when context is likely overloaded.
- Recommend updating `HANDOFF.md` before ending or switching tasks.
- Recommend a new session when context pollution is likely.
- Keep the user focused on the current phase.
- Prevent mixing Phase 1, Phase 1.5, and Phase 2.

## Decision Levels

### GREEN

The session can continue.

Criteria:
- current task is clear
- no repeated obsolete decisions
- no broad unnecessary scans
- no phase mixing

Action:
Continue.

### YELLOW

The session should limit scope.

Criteria:
- tool results are accumulating
- many files have been read
- task is still clear but context is growing

Action:
Recommend targeted inspection only.

### ORANGE

Compaction is recommended.

Criteria:
- session is long
- logs or tool results are large
- user is still on the same task

Action:
Recommend:

`/compact focus on current phase, changed files, pending tasks, constraints, test status, and do-not-change rules.`

### RED

Compaction or handoff is required.

Criteria:
- context appears overloaded
- old attempts are influencing new answers
- task has shifted
- phase boundary is unclear

Action:
Recommend updating `HANDOFF.md`.

### BLACK

New session required.

Criteria:
- repeated compaction
- context pollution
- repeated violation of constraints
- phase switch
- different business area
- assistant keeps using rejected approaches

Action:
1. Update `HANDOFF.md`
2. Start a new Copilot session
3. Use Resume Prompt from `HANDOFF.md`

## Output Format

Always respond with:

# Session Guardian Decision

## Level

GREEN / YELLOW / ORANGE / RED / BLACK

## Recommendation

CONTINUE / LIMIT_SCOPE / COMPACT / UPDATE_HANDOFF / NEW_SESSION

## Reason

Short reason.

## Next Prompt

Exact prompt the user should run next.


---

6. 你以后怎么用

日常开始新会话

复制这个给 Copilot：

Read .github/copilot-instructions.md and HANDOFF.md first.

Summarize:
1. current objective
2. current phase
3. next immediate step
4. files you need to inspect
5. assumptions and risks

Do not edit files until I approve the plan.


---

会话变长时

让 Copilot 执行：

Run the context-checkpoint prompt.

或者手动发：

Use .github/prompts/context-checkpoint.prompt.md to decide whether we should continue, compact, update HANDOFF, or start a new session.


---

准备收工时

发：

Use .github/prompts/update-handoff.prompt.md to update HANDOFF.md.
Only update HANDOFF.md.
Do not modify production code.


---

它建议 compact 时

发：

/compact focus on current phase, changed files, pending tasks, constraints, test status, known issues, and obsolete attempts to ignore.


---

它建议新开 session 时

先发：

Use .github/prompts/update-handoff.prompt.md to update HANDOFF.md.
Only update HANDOFF.md.

然后关旧 Copilot Chat，新开一个空白会话，第一句话发：

Read .github/copilot-instructions.md and HANDOFF.md first.

Summarize current phase, next step, constraints, changed files, and risks.

Do not edit files until I approve the plan.


---

7. 最后给你一个最小执行顺序

你现在不要一次搞太多，就这么做：

第一步：创建 .github/copilot-instructions.md

第二步：创建 HANDOFF.md

第三步：创建 .github/prompts/update-handoff.prompt.md

第四步：创建 .github/prompts/context-checkpoint.prompt.md

第五步：先不要创建 agent，等前三个用顺了再加

session-guardian.agent.md 是增强项，不是第一天必须上。

我建议你先落地前三个：

copilot-instructions.md
HANDOFF.md
update-handoff.prompt.md

这三个就已经能解决你 80% 的上下文混乱问题。
