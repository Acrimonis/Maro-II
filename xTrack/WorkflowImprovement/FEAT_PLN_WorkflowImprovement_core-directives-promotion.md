# Core Directives Promotion — Implementation Plan

**Feature:** WorkflowImprovement — subfeature `hard rules`
**Date:** 2026-06-20
**Branch:** `feature/ymwflow`
**Status:** Draft — awaiting Ask review

---

## Overview

Promote 10 hard rules into [`AGENTS.md`](AGENTS.md) Core Directives (prefix-cache zone, lines 16-25), harden §5 Git Operations, sync [`GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md) Global Rules, and add `#doctor` compliance check. **After promotion, remove duplicated rules from §3 to avoid drift.**

---

## Changes

### File 1: `AGENTS.md` — Core Directives (§1)

**Replace** lines 16-25 with the following structure. All `🔴`/`⛔`/`🎯`/`🗣️`/`📋` rules grouped at top for prefix-cache retention.

```
# Core Directives & Communication Style

- **🎯 DIRECT RESPONSE: Answer only what was asked, then stop.**
  DO NOT suggest next steps, ask follow-ups, or extend the conversation.
  EXCEPTION: IF the prior interaction reached natural conclusion
  (user said "done", "goodbye", "that's all", or topic clearly exhausted) →
  THEN you MAY add 1 high-level future-direction bullet at the very end.

- **⛔ SCOPE LOCK: Zero scope creep.**
  IF the prompt doesn't explicitly request it → do NOT implement it.
  IF you spot an adjacent opportunity → log it as a post-task suggestion,
  never as code in the current delivery. Unrequested features are defects.

- **🔴 MODE LOCK: Do not switch to Code or implement without explicit go-ahead.**
  Architect mode is for discussion/design. Code mode is for execution only.
  IF the user hasn't said "implement", "go ahead", "switch to Code", or similar →
  stay in discussion mode. Unauthorized mode switches are workflow violations.

- **🔴 ABSOLUTE RULE: No agent may execute `git add`, `git commit`, `git push`,
  `git merge`, or `git rebase` without the user's explicit, unambiguous go-ahead.**
  Committing inside `new_task(Code)` subtasks is NOT exempt — see §5.

- **🔴 ABSOLUTE RULE: NEVER write to `develop` or `main` — no pushes,
  no force-pushes, no reverts, no direct commits. Any operation that modifies
  these branches is forbidden.** Feature work lives on `feature/*` branches;
  merges to `develop`/`main` are done via pull request only.

- **🗣️ CONCISE: Minimum viable communication.**
  Say what must be said — nothing more. Zero fluff, zero extrapolation,
  zero speculative prose. IF a sentence doesn't carry signal → cut it.

- **🔴 NEVER ASSUME: Do not assume broader architecture outside task scope.**
  IF the prompt doesn't reference a system, component, or pattern →
  do NOT fabricate assumptions about it. Stick to what's stated.

- **🔴 WRITE-ONCE: Write each source file exactly once per task.**
  No edit-after-write cycles. No save-compile-rewrite loops.
  IF a file needs changing → combine all edits into a single write.
  Every sequential edit invalidates the prompt cache and costs tokens.
  Exception: Explain/Discuss Gate (see below).

- **🔴 NO BINARY READS: Never open, read, or search `.bin`, `.tif`, `.xyz`,
  `.nc` files.** Treat spatial data files as opaque blobs.
  Read metadata and parsing code only.

- **📋 TASK COMPLETION: Summarize only if it adds information.**
  IF the response already answered clearly and concisely → stop.
  No summary needed. IF the task involved multi-step changes, code modifications,
  or non-obvious decisions → emit:
  1. Bullet list of what changed (files touched, logic altered, config).
  2. ELI16 explanation — one or two plain-language sentences explaining the
     *purpose* of the change. Strip Android/Kotlin jargon where possible.
     IF a Java-backend analogy maps cleanly → use it.

- You are allowed to challenge what I say; push back on bad ideas being clear
  on why, but in fine defer to my judgement.

- **Explain/Discuss Gate:** If a user prompt ends with "explain" or "discuss"
  (any casing), the response must be explanation/discussion only — no code edits,
  file writes, or tool-based modifications. Exceptions: (a) one FEAT_PLN_ file
  may be created to capture the discussion; (b) xTrack `#focus`/`#sub` commands
  are permitted during discussion.
```

**Remove from Core Directives** (now covered by promoted rules):
- Line 17: "Be concise. Provide to-the-point answers..." → replaced by `🗣️ CONCISE`
- Line 18: "Answer the prompt directly..." → replaced by `🎯 DIRECT RESPONSE`
- Line 19: "Never make assumptions..." → kept as-is or absorbed into SCOPE LOCK
- Line 20: "Do not implement unrequested features..." → replaced by `⛔ SCOPE LOCK`
- Line 25: "At the end of task, provide a concise bullet summary..." → replaced by `📋 TASK COMPLETION`

**Keep as-is:**
- Line 21: Explain/Discuss Gate (moved to end of Core Directives)
- Line 24: "You are allowed to challenge what I say..." (last)

---

### File 1b: `AGENTS.md` — §5 Git Operations

**Replace** current §5 with hardened version:

```diff
- **🔴 ABSOLUTE RULE: No agent may `git commit` or `git push` without...
-   `git add` may be used to stage...
-   Even `#commit` requires confirmation unless the user chains it...
- **`#push` is NEVER automatic** — always ask for confirmation.
- Feature work lives on `feature/*` branches...
- **Exception:** `new_task(mode=code, ...)` subtasks may commit...
+ **🔴 ABSOLUTE RULE: No agent may execute `git add`, `git commit`,
+   `git push`, `git merge`, or `git rebase` without the user's explicit,
+   unambiguous go-ahead. This applies to ALL modes and ALL subtasks —
+   including `new_task(mode=code, ...)` subtasks. No exception.
+ **`#commit` ALWAYS requires confirmation.** Even when chained with other
+   commands (e.g. `#bake #commit #implement`), the agent MUST pause at
+   `#commit` and ask the user before executing. Chained commands do NOT
+   constitute implicit consent.
+ **`#push` is NEVER automatic** — always ask for confirmation.
+ **`git add` may be used to stage** but only after the user confirms they
+   want a commit prepared. Do not stage preemptively.
+ Feature work lives on `feature/*` branches; merges to `develop`/`main`
+   are done via pull request only.
```

---

### File 1c: `AGENTS.md` — §7b.12 `#doctor` compliance check

**Replace** with one check that `#doctor` can actually evaluate:

```diff
   (h) routing rows → missing files, or feature files with no routing row;
   (i) Feature Summaries table rows with no matching `xTrack/*/FEAT_DSC_`
       file (or vice versa).
+  (j) staged changes on `develop` or `main` branches (autofix: unstage).
```

Note: The original proposal (unsanctioned staged changes via session history) is impractical — `#doctor` cannot access conversation state. A branch-based check is both checkable and actionable.

---

### File 2: `xTrack/GLOBAL_CONTEXT.md` — Global Rules

**Replace** existing Global Rules entry for git/Code-switching:

```diff
- **NO auto git commit/push** — `git add` only when directed; wait for user to
-   say "commit" or "push" before executing those commands (per AGENTS.md §5).
- **Do not switch to Code mode to implement until explicitly instructed.**
+ **Hard rules enforced in AGENTS.md Core Directives.**
+   Key: 🎯 DIRECT RESPONSE | ⛔ SCOPE LOCK | 🔴 MODE LOCK | 🔴 NO GIT WRITES
+   | 🔴 NO BRANCH | 🗣️ CONCISE | 🔴 WRITE-ONCE | 🔴 NO BINARY READS
+   | 📋 TASK COMPLETION.
+   Refer to AGENTS.md Core Directives for full text of each rule.
```

This keeps GLOBAL_CONTEXT.md as a routing reference while removing duplicated rule text that can drift from AGENTS.md.

---

### File 3: `docs/cmd_help_git.md` — git command docs

**Update** the `#commit` entry to reflect the new always-confirm requirement:

```diff
-   #commit             #bake + git add -A && git commit. 🚫 refuses on develop/main.
+   #commit             #bake + git add -A && git commit. ALWAYS prompts for
+                       confirmation — even when chained. 🚫 refuses on develop/main.
```

---

---

### §3 Cleanup — Remove Duplicated Rules After Promotion

After promoting WRITE-ONCE and NO BINARY READS to Core Directives, remove them from §3 to prevent drift:

**Remove from §3 (Token & Prompt-Cache Optimization):**
- Line 44: `- **Zero-Piecemeal Writes:** Write each source file exactly once per task...`
- Line 47: `- **Binary & Media Asset Exclusion:** You are STRICTLY FORBIDDEN from opening...`

**Keep in §3:**
- Line 45: `- **Cache Optimization:** Group changes into bulk writes across multiple files...` (complementary to WRITE-ONCE, not duplicate)
- Line 46: `- **Strict Context Isolation:** Open only the files the user asks for...` (distinct rule)

**Update stale cross-reference:**
- Line 44's `Exception: Explain/Discuss Gate (see §1)` — this reference was to the old Explain/Discuss Gate position. After promotion, the Explain/Discuss Gate is still in §1 but WRITE-ONCE is also in §1, so the exception note in §3 is no longer needed since the rule itself is removed from §3.

---

### Clarifying Notes

- **MODE LOCK vs `#implement` pipeline:** The `#implement` command IS the user's explicit go-ahead. MODE LOCK blocks *unsolicited* mode switches, not pipeline-triggered ones.
- **Explain/Discuss Gate position:** Remains in Core Directives (bottom of the block). Its `(see §1)` cross-references were to itself — after promotion it still lives in §1, so references remain valid.

---

## Execution Order

1. Edit `AGENTS.md` Core Directives — bulk replace lines 16-25 with promoted rules
2. Edit `AGENTS.md` §3 — remove WRITE-ONCE and NO BINARY READS lines (now in Core Directives)
3. Edit `AGENTS.md` §5 — harden Git Operations wording
4. Edit `AGENTS.md` §7b.12 — replace `#doctor` compliance check with branch-based check (j)
5. Edit `xTrack/GLOBAL_CONTEXT.md` Global Rules — condense git/Code-switch lines to reference pointer
6. Edit `docs/cmd_help_git.md` — update `#commit` entry
7. Review all edits for consistency (no drifted duplicates)
8. `#bake` to snapshot

---

## Files Touched

| File | Change |
|------|--------|
| [`AGENTS.md`](AGENTS.md:16) | Core Directives — 10 promoted rules (+ "never assume") |
| [`AGENTS.md`](AGENTS.md:43) | §3 — remove WRITE-ONCE and NO BINARY READS (now in Core Directives) |
| [`AGENTS.md`](AGENTS.md:53) | §5 Git Operations — hardened wording |
| [`AGENTS.md`](AGENTS.md:101) | §7b.12 `#doctor` — replace check with branch-based (j) |
| [`xTrack/GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md:55) | Global Rules — condense git/Code-switch lines to reference |
| [`docs/cmd_help_git.md`](docs/cmd_help_git.md:6) | `#commit` — always-confirm note |

## Key Files
- [`AGENTS.md`](AGENTS.md) — primary target
- [`xTrack/GLOBAL_CONTEXT.md`](xTrack/GLOBAL_CONTEXT.md) — sync Global Rules
- [`docs/cmd_help_git.md`](docs/cmd_help_git.md) — git command docs
- [`xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md`](xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md:146) — `hard rules` subfeature
