# Hard Rules Enforcement — Discussion

**Feature:** WorkflowImprovement — subfeature `hard rules`
**Date:** 2026-06-20
**Branch:** `feature/ymwflow`

## Problem

The rule "no git write actions until expressly green-lighted" exists in multiple places but is frequently ignored by AI agents. Despite being stated in:

- [`AGENTS.md` §5 — Git Operations — STRICT](AGENTS.md:5)
- [`GLOBAL_CONTEXT.md` — Global Rules](xTrack/GLOBAL_CONTEXT.md:60)
- [`CLAUDE.md`](CLAUDE.md) and [`.clinerules`](.clinerules) (via pointer to AGENTS.md)

## Why It Gets Ignored

### 1. Exception Creep

[`AGENTS.md` §5](AGENTS.md:5) contains a notable exception:

> **Exception:** `new_task(mode=code, ...)` subtasks may commit within their scope since results return to the parent.

This creates a loophole: any task routed through `new_task(Code)` can auto-commit. The `#implement` pipeline (§7b.16) chains `Code → Ask → Architect`, and Code step may commit.

### 2. `#commit` Chained Execution

The [`#commit` git shortcut](docs/cmd_help_git.md:6) spec says:

> `#commit` — `#bake` + `git add -A && git commit`

When chained as `#bake #commit #implement`, the agent auto-executes all three without pause. The rule says "requires confirmation unless the user chains it with other commands showing clear intent" — this is ambiguous and agents interpret chains as implicit consent.

### 3. Language Ambiguity

The current wording in [`AGENTS.md` §5](AGENTS.md:5):

> No agent may `git commit` or `git push` without the user explicitly saying so.

But what about `git add`? `git merge`? `git rebase`? The rule is scoped too narrowly — it covers commit/push but not staging or merging. And [`GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md:60) says:

> **NO auto git commit/push** — `git add` only when directed

This is slightly different — it permits `git add` if directed. But "directed" vs "explicitly saying so" vs "expressly green-lighted" are three different standards.

### 4. Placement — Not in Prefix Cache

The `🔴 ABSOLUTE RULE` in AGENTS.md Core Directives only forbids writing to `develop`/`main`. The git operations rule (§5) comes later in the document. For LLM-based agents, text near the top of the prompt has higher weight (prefix-cache). The "no git write" rule may be pushed out of active context by intervening content.

## Proposed Fixes

### A. Move the rule to Core Directives

Add a prominent bullet in the Core Directives section (first ~15 lines of AGENTS.md):

```
- **🔴 ABSOLUTE RULE: No agent may execute `git add`, `git commit`, `git push`,
  `git merge`, or `git rebase` without the user's explicit, unambiguous go-ahead.**
```

### B. Close the `new_task` exception

Either remove the exception entirely, or change it to:

> **Exception:** `new_task(mode=code, ...)` subtasks may stage but NOT commit. The parent Orchestrator is responsible for committing.

### C. Harden `#commit` behavior

`#commit` should prompt for confirmation even when chained. The `#bake #commit #implement` chain should pause at `#commit` with "Stage and commit? [y/N]".

### D. `#doctor` compliance check

Add a check that scans for unsanctioned git writes (staged changes without explicit user confirmation).

### E. Unify language across all locations

One canonical phrase, used identically in AGENTS.md, GLOBAL_CONTEXT.md, and any adapter files:

> **No git write operation (add, commit, push, merge, rebase) without explicit user green light.**

## Key Files
- [`AGENTS.md`](AGENTS.md) — Core Directives (§1) + Git Operations (§5)
- [`xTrack/GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md:55) — Global Rules
- [`docs/cmd_help_git.md`](docs/cmd_help_git.md) — git command documentation
- [`xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md`](xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md:116) — `gitting-it` subfeature specs
