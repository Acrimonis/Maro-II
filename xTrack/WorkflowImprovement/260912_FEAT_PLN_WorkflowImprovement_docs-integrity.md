<!-- scope: feature -->
# Docs & Rulebook Integrity Pass

**Feature:** WorkflowImprovement · **Date:** 2026-09-12 · **Branch:** `feature/wrKFl` · **Status:** **EXECUTED** — baseline `2b74f09`, pass commit `5558f3b`. The phase tables below are the executed record, not instructions; see `## Outcome` at the end for what shipped versus what was planned.
**Source:** review of [`AGENTS.md`](../../AGENTS.md:1) and its linked references — 12 stale refs, 11 duplicate/contradiction classes, 7 orphan classes, 15 review defects (R1–R15).
**Nature:** documentation/registry layer only. No `.kt` source changes, no build, no new dependencies.

## Scope (final)

**In:** the rulebook, all `cmd_help_*` pages, git docs, templates, SKILL, README, SETUP, the eight never-audited docs, feature-file placement and orphan cleanup, `plans/` removal, and the `#doctor` regression checks.
**Out:** any source code; historical plan/record files (except where A16 disposes of them); the `#archive` command itself — designed in Annex B, implemented in its own pass.

## Problem

The rulebook is coherent but **over-mirrored**. The command registry exists in four places — [`AGENTS.md` §7b](../../AGENTS.md:112), [`docs/cmd_help.md`](../../docs/cmd_help.md:1), [`docs/cmd_help_git.md`](../../docs/cmd_help_git.md:6), [`docs/GIT_WORKFLOW.md`](../../docs/GIT_WORKFLOW.md:29) — and each mirror has drifted alone: `#doc sync` vs `#doc update`, the auto `feature/` prefix, `#merge` semantics. The same pattern hits behaviour (`#bake`'s trigger, `#commit`'s confirm rule) and placement (`plans/` empty of files but referenced 49 times).

## Principles

1. **One normative source per fact.** [`AGENTS.md`](../../AGENTS.md:1) is normative; `docs/cmd_help_*` is detail; anything derivable is stamped *derived* and never hand-edited.
2. **Source at the right place.** Feature-scoped files live in `xTrack/[Feature]/`; `docs/` holds cross-cutting reference only.
3. **Fix over document.** Where the correct value is knowable from disk, fix it. Policy choices were arbitrated in Phase 0.
4. **No regrowth.** Every dedup adds the check that would catch its return (Phase 5).

## Cache discipline

- **C1** Prefix-cache zone = [`AGENTS.md`](../../AGENTS.md:1) + [`xTrack/GLOBAL_CONTEXT.md`](../../xTrack/GLOBAL_CONTEXT.md:1). Touched **once per session**, in Phase 6. This is a per-session rule, not a lifetime cap — a later session's edit costs it one invalidation, which any edit would.
- **C2** Findings accumulate in this file; nothing is applied opportunistically mid-review.
- **C3** One comprehensive write per file (WRITE-ONCE); the file → steps matrix enforces it.
- **C4** Phase order is deliberate: lazy docs → placement/restructure → checks → cached trio → bookkeeping.
- **C5** No compile step exists at any point, so no save-build-rewrite churn is possible.
- **C6** Phase 6's text is pre-approved in Annex A — the improvisation risk R2 identified is closed by review, not by splitting the write.
- **C7 Mode:** Phases 2, 3, 4b, 5, 6 and 7 are markdown-only and may run in Architect; O1, O2, O3, O5, O6 and V5 need `execute_command` (`git mv`, `git rm`, `git commit`) and therefore Code mode.

## Phase 0 — Arbitrations (all closed)

| ID | Decision |
|---|---|
| A1 | Plan owned by WorkflowImprovement on `feature/wrKFl` |
| A2 | Baseline commit of the prior cleanup — **done**, `2b74f09` |
| A3 | [`docs/GIT_WORKFLOW.md`](../../docs/GIT_WORKFLOW.md:1) rewritten in place; it stays the git detail home |
| A4 | **Tiered prompts** — `#commit`, `#push`, `#merge`, `#cherry` prompt; `#new`, `#move`, `#move new`, `#rename` stay silent (`#checkout` is de-registered, so it is named nowhere) |
| A5 | FEAT_HYD is written by `#bake` (two of three files already said so; [`AGENTS.md:98`](../../AGENTS.md:98) was the outlier) |
| A5b | `#bake` is **explicit-only**; `#commit` **offers** a bake when the feature's state has moved since its hydration baseline; a `Last Bake` stamp joins the FEAT_HYD template; no backfill (absence = unknown = offer once) |
| A6 | `#checkout` **de-registered** from [`AGENTS.md:128`](../../AGENTS.md:128) and [`docs/cmd_help.md:29`](../../docs/cmd_help.md:29) — `#new` and `#move` cover it |
| A7 | The generator-less profile doc is **deleted** with its one reference |
| A8 | Wizard plan → Markers; listable-item plan → Ui_General; **Tasker becomes its own feature**; `plans/` removed |
| A9 | `docs/map-lib-migration-plan.md` **deleted** (never to be executed) with its two mentions |
| A10 | SKILL.md **dropped from the always-loaded trio**; body reworded positively |
| A11 | README pointer-isation, GDAL block moved into SETUP, two BakeNormalization refs repaired — all in this pass |
| A12 | Migration framing retired; §1 becomes **Code Practice**; Async Rule absorbed; wording sweep |
| A13 | `#todo` / `#rule` page pair kept — per-command pages are structural for `#help` resolution |
| A14 | The three output rules **merged** under one `## Output Contract` |
| A15 | `.clinerules/rules/` is canonical (reverted today) |
| A16 | **Aggressive cleanup** — obsolete references are disposed of, not repaired |
| A17 | Plan lifecycle designed (Annex B); `#archive` implemented in its own pass |
| R6 | All five `docs/oZer/` files re-home to `xTrack/DepthMapping/` now, with link repointing |
| R7 | **(3)** Sweep and fix the seven never-audited docs inline in this pass |

## Phase 1 — Baseline (done)

`2b74f09 docs(xtrack): move agent rule file to .clinerules and clean stale adapter/registry references` — 9 files, +21/−22, `.roo/rules/` → `.clinerules/rules/` as a 100% rename. Working tree clean apart from this plan file.

## Phase 2 — Registry single-sourcing (lazy docs, one write per file)

| ID | Work | File |
|---|---|---|
| S1 | Stamp the table as *derived from §7b — regenerate, do not hand-edit* | [`docs/cmd_help.md`](../../docs/cmd_help.md:1) |
| S2 | `#merge` → current spec (pre-flight → classify → auto-select → confirm → push + PR) | [`docs/GIT_WORKFLOW.md:34`](../../docs/GIT_WORKFLOW.md:34) |
| S3 | Replace the `## OwnedFiles` conflict spec with the live matrix; delete the mechanism (used by only 2 of 25 features) | [`docs/GIT_WORKFLOW.md:36`](../../docs/GIT_WORKFLOW.md:36) |
| S4 | Fix the dangling `AGENTS.md §7b.15` pointer | [`docs/GIT_WORKFLOW.md:50`](../../docs/GIT_WORKFLOW.md:50) |
| S5 | Collapse the Quick Reference duplicate to a pointer, and rewrite the header claim ("Source of truth for all git rules — edit rules here only") to name git *detail*, since the hard rule stays in AGENTS.md Core Directives | [`docs/GIT_WORKFLOW.md:27`](../../docs/GIT_WORKFLOW.md:27) |
| S6 | Remove the `#checkout` row | [`docs/cmd_help.md:29`](../../docs/cmd_help.md:29) |
| S7 | **Registry ↔ help-page parity sweep** — every §7b row maps to a `cmd_help_*` page or is de-registered | `docs/` |
| S8 | C1–C12 → C1–C13; delete the auto-trigger clause; keep the clear-workspace step | [`docs/cmd_help_bake.md:7`](../../docs/cmd_help_bake.md:7) |
| S9 | De-dup the 9-step `#doc update` to a pointer | [`docs/cmd_help_doc.md:42`](../../docs/cmd_help_doc.md:42) |
| S10 | Tiered prompt policy + `#commit` offer-a-bake wording | [`docs/cmd_help_git.md:6`](../../docs/cmd_help_git.md:6) |
| S11 | Split the orphan-lint overlap: `#doctor` = xTrack stack, `#doc audit` = docs/ footprint | both pages |

## Phase 3 — Contradictions and duplicates

| ID | Work | Lands |
|---|---|---|
| D1 | Delete the verbatim repeat of the read-only-git bullet | Phase 6 |
| D2 | One FEAT_HYD owner | Phase 6 |
| D3 | `#bake` trigger behaviour becomes explicit-only | Phase 2 + Phase 6 |
| D4 | 9-step `#doc update` single-sourced in its own page | Phase 2 |
| D5 | Orphan-lint responsibilities split | Phase 2 |
| D6 | Criteria count corrected | Phase 2 |
| D7 | `#todo` / `#rule` page pair kept — structural for `#help` | no change |
| D8 | `#commit` confirm rule resolves with A4 | Phase 2 + Phase 6 |
| D9 | Stale `xTrack/FEAT_DSC_[Name].md` heading → per-feature path | [`templates.md:41`](../../.claude/skills/xtrack/references/templates.md:41) |
| D10 | GLOBAL_CONTEXT template gains the `## Cross-Reference Docs` section the live file has | [`templates.md:12`](../../.claude/skills/xtrack/references/templates.md:12) |
| D11 | `docs/DepthMappingBake.md` → [`FEAT_DOC_DepthMapping_bake.md`](../../xTrack/DepthMapping/FEAT_DOC_DepthMapping_bake.md:1); drop the deleted `GLOBAL_TODOS.md` citation; keep the single canonical Overpass todo at [`GLOBAL_CONTEXT.md:77`](../../xTrack/GLOBAL_CONTEXT.md:77) | [`FEAT_DSC_BakeNormalization.md:51`](../../xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md:51) |

## Phase 4 — Placement, orphans, restructure

| ID | Work |
|---|---|
| O1 | [`plans/wizard-drawerslot-separation-plan.md`](../../plans/wizard-drawerslot-separation-plan.md) → `xTrack/Markers/260625_FEAT_PLN_Markers_wizard-drawerslot-separation.md` (`git mv`), attach in `## Docs` |
| O2 | [`plans/listable-item-interface-migration.md`](../../plans/listable-item-interface-migration.md) → `xTrack/Ui_General/260701_FEAT_PLN_Ui_General_listable-item-interface.md`, attach |
| O3 | **Create the `Tasker` feature** (`#track`): dir, `FEAT_DSC_Tasker.md`, routing row, summary row; re-home the plan as `xTrack/Tasker/<yyyymmdd>_FEAT_PLN_Tasker_tasker-water-state-integration.md` (date from `git log --diff-filter=A`) |
| O4 | Delete [`FEAT_DOC_WorkflowImprovement_profile.md`](../../xTrack/WorkflowImprovement/FEAT_DOC_WorkflowImprovement_profile.md:1) and its reference at [`:56`](../../xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md:56) |
| O5 | Delete [`docs/map-lib-migration-plan.md`](../../docs/map-lib-migration-plan.md:1) plus its mentions at [`FEAT_DSC_UI_Map.md:186`](../../xTrack/UI_Map/FEAT_DSC_UI_Map.md:186) and [`260911_..._tab-finalization.md:71`](../../xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_tab-finalization.md:71) |
| O6 | The five `docs/oZer/BARO - *.md` → `xTrack/DepthMapping/FEAT_DOC_DepthMapping_baro-*.md` (`git mv`), scope tags added, internal parent links repointed, references fixed in `FEAT_DSC_DepthMapping` ×2, `_sources`, `_plan`, `_design` ×2 |
| O7 | README pointer-isation: versions → version catalog; bake-script table deleted; structure tree collapsed; doc index reduced; deploy data-sync noted; charter line added; `greenfield` wording retired |
| O8 | GDAL block moved README → [`docs/SETUP.md`](../../docs/SETUP.md:1); fix `GRADDLE_HOME` typo; reconcile "Java 17+" with `JAVA_HOME`/Corretto 21 |
| O9 | `plans/` references swept: ~16 live pointers ([ArcLayout](../../xTrack/ArcLayout/FEAT_DSC_ArcLayout.md:10) ×6 incl. its Rule, [BoatTrace](../../xTrack/BoatTrace/FEAT_DSC_BoatTrace.md:31) ×2, [UI_Map](../../xTrack/UI_Map/FEAT_DSC_UI_Map.md:125), [BakeNormalization](../../xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md:45), [DepthMapping](../../xTrack/DepthMapping/FEAT_DSC_DepthMapping.md:92), [Documentation Rule](../../xTrack/Documentation/FEAT_DSC_Documentation.md:24)) fixed or dropped; historical provenance mentions disposed of (A16) |

## Phase 4b — Audit sweep of the eight never-read docs (R7 = fix inline)

[`docs/MARO_ARCHITECTURE.md`](../../docs/MARO_ARCHITECTURE.md:1) · [`docs/maro-code.md`](../../docs/maro-code.md:1) · [`docs/FAQ.md`](../../docs/FAQ.md:1) · [`docs/color-scheme.md`](../../docs/color-scheme.md:1) · [`docs/material-icons-standalone-guide.md`](../../docs/material-icons-standalone-guide.md:1) · [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:1) · [`docs/ui-drawer-guidelines.md`](../../docs/ui-drawer-guidelines.md:1) · [`docs/ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md:1)

Same classes as the main review — stale paths, facts stated twice, retired vocabulary (`subfeature`, `#sub`, `active_subfeature`, `.clinerules`, `plans/`), dangling `plans/…` links, dead `#doc sync`/`DepthMappingBake`/`GLOBAL_TODOS` references. Fixes applied inline; anything structural is logged back into this file before Phase 6.

**Sweep result (executed):** MARO_ARCHITECTURE held five stale items — `tools/bake_*.bat` (the scripts were renamed to `bake-*.bat`), the asset-storage paragraph (baked data now lives in the gitignored `data/app-assets/` tree, not checked-in assets), a 2026-06-06 status narration still listing `CoastlinePrebakeTest` + `apk-build.bat` prompts as pending, and two dead `settings-page-guidelines.md` links (the hub table plus one historical BoatTrace plan, both repointed to `ui-component-guidelines.md`). All fixed. The other seven docs returned **zero** hits for retired vocabulary, dead paths or dangling `plans/` links. Two link targets in `cmd_help.md` / `cmd_help_git.md` were repo-root-relative while living in `docs/` — corrected. `maro.prebake` was verified still live in `app/build.gradle.kts` and left alone. **Logged, not changed:** MARO_ARCHITECTURE's `## Reference Docs` table overlaps the AGENTS.md Lazy-Load Index — kept as two audiences (human hub vs agent routing).

## Phase 5 — Regression checks (defined here, AGENTS.md row written in G5)

Applied in Phase 5 and now canonical in [`docs/cmd_help_doctor.md`](../../docs/cmd_help_doctor.md:6) — the summary below is the record of what was defined:

**(k)** registry divergence — diff command rows across §7b / `cmd_help.md` / `cmd_help_git.md`. **(l)** path existence — every backticked path in AGENTS.md and `docs/*.md` resolves; scope limited to those files; skip bracketed placeholders and `*` globs (R9). **(m)** duplicate bullets in AGENTS.md. **(n)** `plans/` residue and `*plan*.md` outside `xTrack/`. **(o)** `## Docs` index ↔ disk mismatch. **(p)** semver-like tokens (`\d+\.\d+\.\d+`) in README — the fastest-drifting class in a pointer-only file.

Definitions land in [`docs/cmd_help_doctor.md`](../../docs/cmd_help_doctor.md:6) (lazy, cache-neutral); the `#doctor` row edit in [`AGENTS.md:124`](../../AGENTS.md:124) happens once, in G5.

## Phase 6 — Cached-trio consolidation (one write, text pre-approved in Annex A)

| ID | Work |
|---|---|
| G1 | Core Directives: D1 deletion, D8 git exemption rewrite, A12 §1/§2 + Developer Profile trim, A14 Output Contract, A10 trio line |
| G2 | §5 collapses to a pointer at the `#merge`/`#push`/`#commit` rows |
| G3 | `#bake` row: explicit-only trigger + closing-phrase removal (D3) |
| G4 | `#commit` row: offers a bake on stale state (A5b) |
| G5 | `#doctor` row: checks a–p |
| G6 | Remove the `#checkout` row (A6); reword the `cmd_help.md` authority claim (R5); Lazy-Load Index gains `docs/ui-lists-guidelines.md` (R6) |
| G7 | [`GLOBAL_CONTEXT.md`](../../xTrack/GLOBAL_CONTEXT.md:17): drop the `plans` routing keyword, rewrite the completed-migration todo line, add the Tasker routing + summary rows |
| G8 | Verify whether `#bake` / `#doc update` still instruct `plans/` writes — neither page mentions it today, so this is expected to be a no-op; drop the step if confirmed |
| G9 | Retire the `plans/` warning itself — after O9 the directory is gone, so the rule becomes "feature-scoped files live in `xTrack/[Feature]/`" |

## Phase 7 — Bookkeeping and validation

| ID | Work |
|---|---|
| V1 | Feature file: `## Docs` + `## Implemented` updated, sections folded |
| V2 | Focus History entry for the session (still unwritten since session start) |
| V3 | `#doctor` + `#doctor fix`, then re-run checks k–p by hand over the changed files |
| V4 | Validate against the criteria below; `git diff --stat` per phase for rollback granularity (R15) |
| V5 | Single `#commit` for the pass — **explicit go-ahead required**; needs Code mode |

## File → steps matrix (R8 — one write per file)

| File | Steps |
|---|---|
| [`AGENTS.md`](../../AGENTS.md:1) | G1–G6 alone |
| [`xTrack/GLOBAL_CONTEXT.md`](../../xTrack/GLOBAL_CONTEXT.md:1) | G7, then V2 |
| [`docs/GIT_WORKFLOW.md`](../../docs/GIT_WORKFLOW.md:1) | S2–S5 |
| [`docs/cmd_help.md`](../../docs/cmd_help.md:1) | S1, S6 |
| [`docs/cmd_help_git.md`](../../docs/cmd_help_git.md:1) | S10 |
| [`docs/cmd_help_bake.md`](../../docs/cmd_help_bake.md:1) | S8 |
| [`docs/cmd_help_doc.md`](../../docs/cmd_help_doc.md:1) | S9 |
| [`docs/cmd_help_doctor.md`](../../docs/cmd_help_doctor.md:1) / [doc_audit](../../docs/cmd_help_doc_audit.md:1) | S11, Phase 5 definitions |
| [`templates.md`](../../.claude/skills/xtrack/references/templates.md:1) | D9, D10, `## OwnedFiles` removal, Last Bake stamp, `plans/` warning removal (the G9 pair) |
| [`SKILL.md`](../../.claude/skills/xtrack/SKILL.md:1) | A10 body |
| [`README.md`](../../README.md:1) | O7 |
| [`docs/SETUP.md`](../../docs/SETUP.md:1) | O8 |
| [`FEAT_DSC_BakeNormalization.md`](../../xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md:1) | D11 |
| ArcLayout / BoatTrace / UI_Map / Documentation feature files | O9 |
| Markers / Ui_General / Tasker | O1, O2, O3 |
| DepthMapping feature files (`FEAT_DSC_` + the four `FEAT_DOC_` targets) | O6 and O9, written once |
| WorkflowImprovement feature file | O4, V1 |
| The eight docs in Phase 4b | one pass each |

## Annex A — Phase 6 target text (APPLIED — approved draft snapshot)

> These blocks were applied verbatim into `AGENTS.md`, `templates.md` and `SKILL.md`. The live files
> are normative; this annex is retained only as the record of what was approved. Any future change
> goes to the live file, never here — see `## Outcome` for the two places where what shipped differs.

**G1 · Code Practice (replaces §1 MAD Replication)**
```
# 1. Code Practice — write idiomatic Kotlin for this codebase; never copy-paste from elsewhere.
- **Async:** Coroutines + Flow only — no raw threads or executors.
- **Idioms:** data classes for state, immutable collections, functional transforms over manual loops, `val` unless mutation is required.
- **No copy-paste:** adapting code means rewriting it into this project's patterns and naming — never pasting a block and patching it.

# 2. Architecture Layering — pure Kotlin domain → ViewModel + StateFlow/coroutines → stateless Compose UI.
```

**G1 · Developer Profile (trimmed — Async Rule and Kotlin idioms moved to §1)**
```
# Developer Profile & Architectural Translation
- User: Senior Java backend dev → Android/Kotlin. Map ViewModels/Repos ↔ Spring Beans/Services, StateFlow ↔ reactive streams.
```

**G1 · Output Contract (merge of DIRECT RESPONSE + CONCISE + TASK COMPLETION)**
```
## Output Contract
- **Answer only what was asked, then stop** — no next steps, no follow-ups. Exception: once the
  interaction has reached a natural conclusion (the user said "done"/"goodbye", or the topic is
  exhausted) you MAY add one high-level future-direction bullet at the very end.
- **Minimum viable communication** — cut any sentence that carries no signal; no fluff, no
  extrapolation, no speculative prose.
- **Summarize only what changed** — if the tool output already answered the request, emit only
  `Done.` When the task involved multi-step changes or non-obvious decisions, emit (1) a bullet
  list of what changed (files touched, logic altered, config) and (2) an ELIJP: one or two
  plain-language sentences on the purpose, jargon stripped, Java-backend analogy where one maps.
  (ELIJP = "Explain Like I'm a Junior Programmer".)
```

**G2 · §5 Git Operations**
```
# 5. Git Operations — see the `#merge` / `#push` / `#commit` rows in §7b, the Core Directives above, and `docs/GIT_WORKFLOW.md` for detail.
- **🔴 NO GIT EDITOR: Never open an interactive editor for git commands.** Always use `-m "message"`, `--no-edit`, and non-interactive flags; if a command would spawn vim/nano, re-run it with them.
```

**G1 · git exemption (replaces the blanket exception at line 44)**
```
**Exception:** git-related `#`-commands are self-contained confirmations — the explicit invocation is the go-ahead. `#commit`, `#push`, `#merge` and `#cherry` still ask before acting, even when chained; `#new`, `#move`, `#move new` and `#rename` do not.
```

**G3 · §7a Memory Stack**
```
- **Memory Stack:** Context footprint: `xTrack/` (features) + `GLOBAL_CONTEXT.md` (routing), `xTrack/[Feature]/FEAT_DSC_[Feature].md` (epics), `xTrack/[Feature]/FEAT_HYD_[Feature].md` (session state, written by `#bake`). The feature directory + `FEAT_DSC_` are auto-created on first `#track`/`#focus`; `FEAT_HYD_` appears at first `#bake`.
```

**G3 · `#bake` row (append)**
```
Fires only on explicit invocation.
```

**G4 · `#commit` row**
```
| `#commit` | Stage + commit; if the active feature's `xTrack/[Feature]/` state has moved since its hydration baseline, offer a bake first. Asks before committing. |
```

**G5 · `#doctor` row**
```
| `#doctor` | Lint xTrack (checks a–p); `#doctor fix` auto-repairs safe classes |
```

**G6 · [`AGENTS.md:136`](../../AGENTS.md:136) (R5)**
```
Full detail per command in `docs/cmd_help_*.md` — loaded by `#help`. `docs/cmd_help.md` is a derived printed view of §7b.
```

**G9 · plan placement rule**
```
- **🔴 PLAN FILE PLACEMENT: All `FEAT_PLN_*.md`, `FEAT_DOC_*.md` and feature-scoped design files live in `xTrack/[Feature]/`, named `YYMMDD_FEAT_PLN_[Feature]_[topic].md`.**
```

**A10 · trio + SKILL body**
```
- **Always-loaded (prefix-cache zone):** `AGENTS.md`, `xTrack/GLOBAL_CONTEXT.md`. Keep both small and free of duplication.
```
```
This file is the Claude-side entry point for the xTrack system; the specification lives in AGENTS.md §7a/§7b.
```

**A5b · FEAT_HYD template gains**
```
**Last Bake:** YYYY-MM-DD HH:mm UTC
```

**O7 · README charter line**
```
> This file is orientation only: what the app is, how to run it, roughly where things live.
> Versions, file inventories and derivable facts are not repeated here — follow the pointers.
```

## Annex B — migrated

The plan-lifecycle design and the `#archive` command spec now live in their own plan of record: [`260912_FEAT_PLN_WorkflowImprovement_archive-lifecycle.md`](260912_FEAT_PLN_WorkflowImprovement_archive-lifecycle.md:1) — migrated there per P7, before implementation began. This file keeps no copy, so nothing here can drift against it.

## Out of scope

- No `.kt` edits, no build, no new dependencies.
- No push, no merge; `develop`/`main` never written.
- The `#archive` implementation (Annex B) and the 198-file backlog triage.
- Historical plan/record files, except where A16 explicitly disposes of a reference.

## Validation criteria

No fact about a command or git rule stated in more than one normative place · every path in AGENTS.md and `docs/*.md` resolves · `plans/` gone · no file carries a dangling `plans/`, `docs/DepthMappingBake.md`, `GLOBAL_TODOS.md`, `#doc sync` or `.clinerules`-as-dead reference · each registered command resolves via `#help` · checks k–p all pass, and would fail if any of the above regressed.

## Outcome

**Shipped as planned.** Four hops, committed as `5558f3b` on the baseline `2b74f09`: the registry was single-sourced (AGENTS.md §7b normative, `cmd_help.md` stamped derived, GIT_WORKFLOW demoted to detail), `#checkout` de-registered and `#list` given its missing page, `#bake` made explicit-only with `#commit` offering a stale bake, the git prompt policy tiered, `plans/` and `docs/oZer/` deleted (8 renames, 2 deletions, the Tasker feature created), README pointer-ised with GDAL moved into SETUP, MARO_ARCHITECTURE plus seven docs audited, and `#doctor` extended to checks a–p. Checks k–p passed; the Ask review verified 19 of 20 ledger decisions.

**Deviations from plan.**

1. **No build** — C5 anticipated it: a docs-only pass cannot change build output, so the pipeline's build step was skipped as a no-op. This is the precedent behind the proposed `#implement` refinement ("build when source changed").
2. **S2 and S5 merged** — GIT_WORKFLOW's stale `#merge` spec was deleted rather than rewritten, since the live spec is single-sourced in `cmd_help_git.md`.
3. **The confirmation-policy paragraph was dropped** from `cmd_help_git.md` — the normative text lives in the Core Directives, so a third copy was avoided.
4. **Three files took a second write** — `MARO_ARCHITECTURE.md`, `cmd_help.md`, `cmd_help_git.md`, for fixes found after their first pass. A WRITE-ONCE deviation, judged cheaper than deferring.
5. **A10 landed partially, then closed** — the trio edit shipped in Phase 6; SKILL.md's body was missed and corrected only after the Ask review caught it.
6. **One self-inflicted typo was reverted** — a pointer briefly rewritten to `..._global-rules-migration.md` was restored to `..._global-context-rules-migration.md` in the same hop.

**Counted differently than planned.** O9 estimated ~16 live `plans/` pointers; execution repaired roughly 24 across 15 files.

**Recorded, not changed (pre-existing cosmetics).** The ABSOLUTE RULE continuation at [`AGENTS.md:47`](../../AGENTS.md:47) sits outside its bold span; the `## Output Contract` heading nests inside the bullet section.

**Deferred.** Annex B is this file's only live content. When the `#archive` pass starts it migrates to its own plan file, after which this file retires with its digest extracted.
