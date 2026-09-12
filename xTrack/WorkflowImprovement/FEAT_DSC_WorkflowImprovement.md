---
name: WorkflowImprovement
status: active
created: 2026-06-03 00:00
modified: 2026-09-12 12:30
---

# Feature: WorkflowImprovement

**Description:**
Improving the xTrack workflow and command system (canonicalized in AGENTS.md) — trigger syntax, templates, lifecycle protocols, and bootstrap logic.

## Sections
### gitting-it

Git command shortcuts: #new / #commit / #push / #move / #cherry·#copy / #rename / #merge — canonical in AGENTS.md §7b + docs/cmd_help_git.md. Prompts are tiered: `#commit`, `#push`, `#merge` and `#cherry` ask before acting, while branch operations (`#new`, `#move`, `#move new`, `#rename`) do not. `#merge` runs pre-flight → classify → auto-select → confirm → push + PR, and none of them auto-executes — the no-git-write rule holds in every mode.

#### Todos
- [ ] On-device / real-repo verification of all git shortcuts — deferred, low priority

#### Key Files
- `docs/cmd_help_git.md` — git shortcut detail
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-move-command.md` — #move/#cherry design

## Todos
- [ ] **Post-merge reconcile xTrack/ across branches** — deferred. Procedure documented in FEAT_DSC; execute when first cross-branch xTrack conflict occurs.
- [ ] **Second retirement gate** — `docs-integrity` (self-retiring; its `## Outcome` says Annex B has migrated) and `archive-lifecycle` (unblocked by the first real `#archive` run). The first pass moved seven files to `xxArchive/` with INDEX rows.
- `AGENTS.md` is the canonical rulebook and directly writable; edit without prompting (`.clinerules/`/`CLAUDE.md` are pointers).

## Key Files
- `AGENTS.md` — canonical rules incl. § 7a/7b xTrack; `.clinerules/`/`CLAUDE.md` are adapters
- `docs/cmd_help.md` — command reference summary + per-command sections
- `docs/cmd_help_git.md` — git workflow shortcuts
- `xTrack/GLOBAL_CONTEXT.md` — state only: routing map, feature summaries, focus history, global todos, doc index (rules live in `AGENTS.md`)
- `.claude/skills/xtrack/references/templates.md` — file templates

## Docs
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_archive-lifecycle.md` — plan lifecycle + `#archive` command (xxArchive tier, digest floor, `#doctor` checks q–r)
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_docs-integrity.md` — docs & rulebook integrity pass (registry single-sourcing, `plans/` removal, `#doctor` checks a–p)
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_command-flow.md` — Output Contract rules plus the `#go` / `#review` / `#walk` / `#brief` rows and their ship order
- `docs/cmd_help.md` — derived printed view of §7b
- `docs/cmd_help_now.md` — #now / #list detail
- `docs/cmd_help_status.md` — #status / #status diff detail
- `docs/cmd_help_track.md` — #track detail
- `docs/cmd_help_focus.md` — #focus / #focus [section] detail
- `docs/cmd_help_todo.md` — #todo detail
- `docs/cmd_help_rule.md` — #rule detail
- `docs/cmd_help_doc.md` — #doc detail
- `docs/cmd_help_bake.md` — #bake detail
- `docs/cmd_help_help.md` — #help detail
- `docs/cmd_help_doctor.md` — #doctor detail
- `docs/cmd_help_archive.md` — #archive detail (xxArchive tier, digest floor, retirement candidates)
- `docs/cmd_help_list.md` — #list dashboard detail
- `docs/cmd_help_git.md` — git workflow shortcuts detail
- `docs/cmd_help_doc_audit.md` — #doc audit detail
- `docs/cmd_help_doc_update.md` — #doc update detail
- `docs/cmd_help_the-c-word.md` — #the-c-word help stub
- `docs/GIT_WORKFLOW.md` — Git workflow conventions
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` — FEAT_ summary layer token optimization discussion
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_xtrack-reorg.md` — xTrack FEAT_* file reorganization implementation spec
- `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_hard-rules-enforcement.md` — Hard rules enforcement discussion
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_merge-conflict-resolution.md` — AI-assisted #merge conflict resolution spec
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_cmd-dispatch-refactor.md` — Command lookup dispatch refactor plan
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_newtask-delegation.md` — new_task delegation: Architect → Code without mode switch
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-merge-command.md` — Git merge command design

## Implemented

- **command-flow rules + four commands (2026-09-12, `feature/wrKFl`)** — the Output Contract gained a countable unit (`📏 Bullets are the unit`), the containment rule and the `🗣️ Recommendations argue against themselves` bullet, retiring "minimum viable communication"; rule 2 scopes ambiguity to MODE LOCK and QUESTIONS, rule 3 makes a gate name its action and re-ask when the proposal moved; four §7b rows shipped with pages — `#go` (Pipeline group), `#review` (cascade plus the pipeline guard in `cmd_help_implement.md`), `#walk` with `#next` / `#prev` / `#skip` / `#done`, the `## Walk` template and the §7a/C12 single statement, and `#brief` / `#full` with the mode definition and the three optional parts; `#doctor` unchanged at a–r → `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_command-flow.md`
- **first real `#archive` pass (2026-09-12, `feature/wrKFl`)** — six candidates retired as seven files (core-directives-promotion, merge-strategy, trunk-to-leaf, planning, both agents-md-optimization plans, process-simplification) into `xTrack/WorkflowImprovement/xxArchive/`, each with an appended `## Outcome`, plus an `INDEX.md` of seven rows, five `## Docs` detachments and five `## Implemented` pointer drops; checks q–r verified clean; docs-integrity and archive-lifecycle held for a second gate

- **command delta on feature open (2026-09-12, `feature/wrKFl`)** — `#focus`, `#track` and Turn 1 (once a feature resolves) print the newest (max 3) WorkflowImprovement `## Implemented` entries that add or change a command; the rule is stated once as a §7a bullet, referenced from the two §7b rows, and mirrored in `docs/cmd_help_focus.md` / `docs/cmd_help_track.md` / `docs/cmd_help.md` — no new state, `#doctor` unchanged (a–r)
- **archive lifecycle + `#archive` command (2026-09-12, `feature/wrKFl`)** — plan lifecycle defined (Active → Digest → `xxArchive/`; shipped / superseded / promoted exits, abandonment routed to deletion); design migrated to its own plan per P7 so no copy remains; `AGENTS.md` gained the `xxArchive/` never-read rule (§7a) and the `#archive` §7b row (explicit invocation only) in one write; `#doctor` extended to checks a–r with index↔disk drift, the inverse-leak check and a report-only retirement nudge; new `docs/cmd_help_archive.md`; `templates.md` gained the `INDEX.md` schema; `#bake` reports retirement candidates → `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_archive-lifecycle.md`
- **docs & rulebook integrity pass (2026-09-12, `feature/wrKFl`)** — registry single-sourced (AGENTS.md §7b normative; `cmd_help.md` stamped derived; `GIT_WORKFLOW.md` demoted to git detail, `## OwnedFiles` mechanism retired), `#checkout` de-registered and `#list` given its missing page, `#bake` explicit-only with `#commit` offering a stale-bake first (a `Last Bake` stamp added to the FEAT_HYD template), tiered git confirmation policy, `plans/` and `docs/oZer/` deleted (3 plan re-homes incl. the new **Tasker** feature, 5 research re-homes, 2 deletions), README pointer-ised with GDAL moved into SETUP, MARO_ARCHITECTURE plus seven docs audited, `#doctor` extended to checks a–p → `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_docs-integrity.md`
- **GLOBAL_CONTEXT.md rules migration (2026-09-11)** — GLOBAL_CONTEXT.md reduced to state-only (`## Global Rules`, `## Global Instructions`, `## Always-Loaded Context` removed after bullet-by-bullet triage, not bulk deletion); rules consolidated into AGENTS.md — new §9 Environment & Tooling, QUESTIONS directive, MODE LOCK anti-`#implement` clause, §7a state-only invariant, `#rule global` retargeted to Core Directives; downstream sync (`cmd_help_rule`, `cmd_help_doctor` lint, `templates.md`, `cmd_help_implement` dead pointer) → `xTrack/WorkflowImprovement/260911_FEAT_PLN_WorkflowImprovement_global-context-rules-migration.md`
- **hard rules — Core Directives promotion (2026-06-20)** — 10 rules to prefix-cache zone, §5 git ops hardened, #doctor check (j)
- **AGENTS.md token optimization (2026-06-20)** — 207→149 lines, §7b collapsed to table
- **Workflow management cleanup pass (2026-06-28)** — 8 git commands canonical, MODE LOCK clarified, #help filename-scan, WorkflowAmbiguityFix absorbed
- **#merge hybrid strategy (2026-06-28)** — pre-flight + trivial/non-trivial classification + auto-select rebase/merge
- **AGENTS.md trunk-to-leaf (2026-06-28)** — §5 merged into Core Directives, §§1-4 condensed, Lazy-Load Index added
- **Process simplification (2026-09-04)** — WRITE-ONCE guideline, subfeatures→sections, Focus History stack
- **Rule enforcement fix (2026-09-05)** — Roo Code `.roo/rules/agents-source-of-truth.md` forces read+enforce of AGENTS.md at every session (project-committed, not plugin-config); deprecated `.clinerules` removed. AGENTS.md stays single authored source.
- **#doc list attachment column** — `#doc list` gained an "Attached to" column
- **xTrack system review** — spec fragmentation, orphan docs, cache gaps fixed (#doc sync/audit/diff)
- **AGENTSmdNormalization** — AGENTS.md canonical rulebook + adapters + #doctor lint
- **normalize commands** — command-set rationalization (Option C)
- **planning** — Zero-Piecemeal Writes discussion exception

