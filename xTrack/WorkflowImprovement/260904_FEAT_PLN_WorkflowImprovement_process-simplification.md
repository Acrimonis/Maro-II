# FEAT_PLN — Process Simplification: WRITE-ONCE softening + subfeature → section model

- **Date:** 2026-09-04
- **Branch:** feature/process
- **Status:** implemented — deferred cleanup pending (see below)
- **Scope:** AGENTS.md rulebook + xTrack command/file model. No app code changes.

## Context

Three process-level changes agreed in discussion:

1. Soften the WRITE-ONCE absolute rule into a guideline.
2. Remove the subfeature machinery; replace it with lightweight, self-consolidating **sections**.
3. Replace GLOBAL_CONTEXT's mutable session pointer with an append-only Focus History stack.

Both converge on one principle: **batch mutations at explicit events** (`#bake`), keep file structure lean, and keep the always-loaded rulebook (`AGENTS.md`) minimal for prompt-cache stability.

---

## Implementation gate (no-bypass)

- **Plan approval ≠ implementation authorization.** Approving the design and green-lighting edits are two separate gates.
- Implementation starts only on an explicit directive — `implement`, `go ahead`, `#implement`, or an unambiguous equivalent.
- If a directive merely *implies* approval or is ambiguous, STOP and ask permission before touching any tracked file.
- This gate hardens AGENTS.md §Core Directives MODE LOCK (lines 28–32) and applies to every mode, every task, every agent.

---

## Decision 1 — Soften WRITE-ONCE

### Current (AGENTS.md §Core Directives, lines 47–51)

```text
- 🔴 WRITE-ONCE: Write each source file exactly once per task.
  No edit-after-write cycles. No save-compile-rewrite loops.
  IF a file needs changing → combine all edits into a single write.
  Every sequential edit invalidates the prompt cache and costs tokens.
  Exception: Explain/Discuss Gate (see below).
```

### Proposed (cache-lean)

```text
- 🟡 WRITE-ONCE (guideline): Prefer one comprehensive write per source file; batch related edits.
  Avoid full-file rewrite loops and save-compile-rewrite churn — each rewrite invalidates the prompt cache.
  Targeted apply_diff patches for build errors, review feedback, or discovered edge cases are normal.
```

Dropped the `Exception: Explain/Discuss Gate` line — the gate at AGENTS.md:68 already applies globally, so the clause is redundant token spend in a cache-critical file.

### §3 pointer (line 80)

Current: `# 3. Token Optimization — bulk writes, strict context isolation. See Core Directives WRITE-ONCE + CONCISE.`

Proposed: `# 3. Token Optimization — prefer bulk writes and strict context isolation; targeted follow-up patches allowed. See Core Directives WRITE-ONCE + CONCISE.`

---

## Decision 2 — Remove subfeatures; introduce sections

### Rationale

Subfeatures add a persistent `active_subfeature` state, a `#sub` command, `#focus sub`/`#focus out`, and a 4-level `## Subfeatures → ### → ####` nesting — but in practice `active_subfeature` is always `none` and the sections are frozen, all-done history. The real unit of navigation is feature + todos.

Sections keep *partial-context action* without the state machine: plain `### [Section]` headings, addressable on demand, consolidated automatically at `#bake`.

### Section management criteria (C1–C12)

**Creation**
- C1 Cohesion — one section = one bounded theme/outcome.
- C2 Threshold — create only at ≥2 related items or a theme spanning multiple turns; a lone todo stays at feature level.
- C3 Emergent — no create command; the heading materializes when `#todo`/`#rule` targets a new theme, formalized at `#bake`.

**Addressing & partial context**
- C4 Stable heading — `### [noun-phrase]`, fuzzy-resolve friendly, vocabulary aligned with the routing map.
- C5 Targeting — `#todo [Section]:desc` / `#rule [Section]:desc`; optional `#focus [Feature] [Section]` hydrates only that block (transient, no persistent state).
- C6 Drop the state — remove `active_subfeature`, `#sub`, `#focus sub`, `#focus out`.

**Structure**
- C7 Optional slots — a section may hold `#### Todos / Rules / Key Files / Docs`; empty slots dropped at `#bake`.

**Consolidation (all at `#bake`, the single trigger)**
- C8 Fold-done — all todos done and outcome captured → fold into `## Implemented`, delete heading.
- C9 Trim-empty — no open todos AND no retained rule/doc/key-file → same fold.
- C10 Split — section > ~8 items or two distinct themes → split into coherent children.
- C11 Merge — near-duplicate/overlapping headings → merge.

**Retention test**
- C12 Keep-if-signal — a section survives only while it carries ≥1 of: open todo, retained decision/rule, doc/key-file mapping. Otherwise it is history → folded.

"Done" is a condition `#bake` evaluates, not a timer.

---

## Decision 3 — GLOBAL_CONTEXT Focus History stack

Replace the mutable `## Active Session Pointers` block with an append-only **Focus History** stack (newest-first, cap 10).

- Entry: `[YYYY-MM-DD HH:mm UTC] [Feature] — one-liner → xTrack/[Feature]/FEAT_HYD_[Feature].md`
- Top entry = current focus. `#focus` pushes a new entry; `#now`/`#status` read the top.
- `#bake` prunes entries beyond cap 10 (older entries are redundant with FEAT_HYD).
- Append-only → merges are additive; rare conflicts resolve by keeping both, ordered by timestamp.

Field mapping:

| Current field | Fate |
|---|---|
| Active Feature | derived from top entry |
| Active Subfeature | dropped (section model) |
| Last Updated | derived from top entry timestamp |
| Last Bake | moved per-feature into FEAT_HYD |
| Branch | dropped from file — derive from git |

Spec changes: `#focus`, `#now`, `#status`, `#bake` switch from read/write of the Active Feature field to push/read-top/prune.

---

## Efficiency & cache review (adjustments applied)

- **A1 — AGENTS.md is cache-critical.** C1–C12 are NOT enumerated there (it is in Always-Loaded Context). AGENTS.md carries a one-line pointer; full criteria live here + summarized in `docs/cmd_help_bake.md` (lazy-loaded via `#help`).
- **A2 — WRITE-ONCE wording compressed** to three tight lines; redundant Explain/Discuss exception line dropped.
- **A3 — `#bake` as single mutation point** is cache-friendly (batched edits) — confirmed.
- **A4 — Migration is a one-time cache-bust.** All FEAT_DSC files updated in one bulk pass; files stabilize afterward.
- **A5 — `#focus [Feature] [Section]` kept.** Partial hydration reads one block, not the whole feature — a net efficiency win.
- **A6 — Micro-decisions resolved:** 🟡 marker for WRITE-ONCE; section state is transient (GLOBAL_CONTEXT line dropped, no replacement).
- **A7 — Rule name "WRITE-ONCE" kept** for reference stability (§3 + GLOBAL_CONTEXT reference it); flagged 🟡 guideline.
- **A8 — Focus History capped at 10** to keep GLOBAL_CONTEXT (always-loaded) bounded; detailed state stays in per-feature FEAT_HYD.

---

## Exact wording changes

### AGENTS.md

| Location | Change |
|---|---|
| §Core Directives MODE LOCK (28–32) | harden: plan approval ≠ implementation go-ahead; in doubt, ask permission |
| §Core Directives (47–51) | WRITE-ONCE → guideline wording (above) |
| §3 (80) | token-optimization pointer softened |
| §7b FM (97) | drop `active_subfeature` from YAML field list |
| §7b `#focus` (102) | drop `Sub: #focus sub / #focus out`; add optional `[section]` hydration; push Focus History entry |
| §7b `#sub` (104) | remove row |
| §7b `#bake` (105) | add consolidation duties + prune Focus History beyond cap 10 |
| §7b `#status` (109) | read top Focus History entry |
| §7b `#now` (110) | read top Focus History entry; drop `subfeature` |
| §7a (after Memory Stack) | add one-line **Sections** bullet + one-line **Focus History** bullet (pointers, not full criteria) |

Proposed new rows:

```text
| #focus [name] | Pivot active feature (push Focus History entry); bare=prompt pick. Optional #focus [name] [section] hydrates only that section |
| #bake | Snapshot + consolidation: checkmarks, section rules (fold-done, trim-empty, split, merge, rename-normalize), feature summary, front-matter date, hydration, prune Focus History > 10 |
| #now | Lightweight orientation: top Focus History entry (feature), CWD, Last Bake |
```

Proposed §7a bullet:

```text
- Sections: Feature files group work under ### [Section] headings (no subfeature state). Keep a section only while it holds an open todo, a retained rule, or a doc/key-file mapping; #bake folds the rest into ## Implemented. Full criteria in docs/cmd_help_bake.md.
- Focus History: GLOBAL_CONTEXT.md keeps an append-only newest-first stack (cap 10) of [timestamp] [Feature] — one-liner → FEAT_HYD_[Feature].md. Top = current focus. #focus pushes; #bake prunes.
```

### docs/cmd_help*.md

- `cmd_help_sub.md` — remove/retire.
- `cmd_help_focus.md` — remove `sub`/`out`; document `[section]` hydration + Focus History push.
- `cmd_help_bake.md` — add consolidation duties + summarize C1–C12 (the canonical criteria home) + prune Focus History.
- `cmd_help_now.md` — read top Focus History entry (drop subfeature).
- `cmd_help_status.md` — read top Focus History entry.
- `cmd_help_todo.md`, `cmd_help_rule.md` — document section tier (`[Section]:desc`).
- `cmd_help.md` — reference table sync.

### .claude/skills/xtrack/references/templates.md

- FEAT_DSC template: `## Subfeatures` → `## Sections`; drop `active_subfeature` front-matter example.

### GLOBAL_CONTEXT.md

- Replace `## Active Session Pointers` with append-only `## Focus History` (newest-first, cap 10); entry = `[timestamp] [Feature] — one-liner → FEAT_HYD_[Feature].md`.
- Drop `Active Subfeature`, `Last Updated`, `Last Bake`, `Branch` fields (derived or moved per-feature into FEAT_HYD).

---

## Migration pass (one bulk pass — A4)

For every `xTrack/*/FEAT_DSC_*.md`:

1. Strip `active_subfeature` from YAML front matter.
2. `## Subfeatures` → `## Sections`.
3. Each `### Subfeature` heading → `### [Section]` (strip legacy `[x]`/`[ ]` markers). Fold/trim (C8/C9) is deferred to `#bake`.
4. Collapse `#### Todos/Rules/Key Files/Docs` — keep only populated slots.
5. GLOBAL_CONTEXT.md: convert `## Active Session Pointers` → `## Focus History` (seed one entry from current Active Feature).

---

## Validation

- Run `#doctor` — update its lint checks to flag `active_subfeature` / `#sub` remnants (auto-fix strips them).
- Run `#bake` on one migrated feature as a smoke test.
- Review `git diff` for a clean, reviewable change.

---

## Deferred cleanup (follow-up)

- **Legacy heading markers** — ~153 `###` section headings still carry `[x]`/`[ ]` markers. Agreed approach: strip the markers mechanically (headings become plain `### [Section]`), and defer fold/trim (C8/C9/C12) to `#bake` — it is the designed trigger and must judge retained rules/docs/key-files per section.
- **Stale wording** — Markers summary one-liner says "14/14 subfeatures done" → reword to "14/14 work areas complete".
- **Commit** — nothing committed yet; pending explicit go-ahead. `#commit` (runs `#bake` then commits) is the natural path.

---

## Resolved decisions

1. WRITE-ONCE severity marker: 🟡 (guideline).
2. No persistent section state in GLOBAL_CONTEXT — transient only via `#focus [Feature] [Section]`.
3. GLOBAL_CONTEXT session state: Focus History stack, newest-first, cap 10; `#focus`/`#now`/`#status`/`#bake` updated.
