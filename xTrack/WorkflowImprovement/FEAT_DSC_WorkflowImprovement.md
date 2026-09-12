---
name: WorkflowImprovement
status: active
created: 2026-06-03 00:00
modified: 2026-09-12 11:52
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

### command-flow

The Output Contract's brevity rule had a unit-missing cap problem: "minimum viable communication" is an adjective, with nothing to count and nothing to check. The session's most-used verbs — step through items, request an independent review, grant permission — were also unnamed, so they were re-typed as prose each turn. A seven-item walk closed the Ask review of the design: the contract gains a bullet-as-unit cap, the challenge clause is scoped to recommendations while MODE LOCK keeps authorisation, a gate must name the action it authorises, an open walk blocks both `#bake`'s fold and `#archive`'s retirement, and the output mode is session-lived and reset by `#focus`. The six resolutions are now applied to the plan; the three rules and the four commands (`#go`, `#review`, `#walk`, `#brief`/`#full`) are still unshipped, and `#plan` and `#challenge` were dropped as failing the "adds what context cannot imply" test.

#### Todos
- [ ] Ship the rules first (no registry cost), then `#go`, then `#review` with its guard lines, then `#walk`, then `#brief`

#### Docs
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_command-flow.md` — the design: rules, the four rows, ship order

## Todos
- [ ] **Post-merge reconcile xTrack/ across branches** — deferred. Procedure documented in FEAT_DSC; execute when first cross-branch xTrack conflict occurs.
- [ ] **Retirement candidates reported by the 2026-09-12 11:30 bake** — decide per file with `#archive`: docs-integrity (its `## Outcome` says it retires now that Annex B has migrated), archive-lifecycle (defers itself until `#archive` has run for real), core-directives-promotion, agents-md-optimization, merge-strategy, trunk-to-leaf, process-simplification, planning
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
- `xTrack/WorkflowImprovement/260608_FEAT_PLN_WorkflowImprovement_planning.md` — Zero-Piecemeal Writes discussion exception design
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` — FEAT_ summary layer token optimization discussion
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_xtrack-reorg.md` — xTrack FEAT_* file reorganization implementation spec
- `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_core-directives-promotion.md` — Core Directives promotion implementation plan
- `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_hard-rules-enforcement.md` — Hard rules enforcement discussion
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_merge-conflict-resolution.md` — AI-assisted #merge conflict resolution spec
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_cmd-dispatch-refactor.md` — Command lookup dispatch refactor plan
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_agents-md-optimization-plan.md` — AGENTS.md optimization pass findings
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_newtask-delegation.md` — new_task delegation: Architect → Code without mode switch
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-merge-command.md` — Git merge command design
- `xTrack/WorkflowImprovement/260628_FEAT_PLN_WorkflowImprovement_merge-strategy.md` — #merge hybrid strategy: pre-flight + trivial/non-trivial classification + auto-select + #implement pipeline
- `xTrack/WorkflowImprovement/260628_FEAT_PLN_WorkflowImprovement_trunk-to-leaf.md` — AGENTS.md trunk-to-leaf token optimization: deduplicate, condense, Lazy-Load Index

## Implemented

- **command delta on feature open (2026-09-12, `feature/wrKFl`)** — `#focus`, `#track` and Turn 1 (once a feature resolves) print the newest (max 3) WorkflowImprovement `## Implemented` entries that add or change a command; the rule is stated once as a §7a bullet, referenced from the two §7b rows, and mirrored in `docs/cmd_help_focus.md` / `docs/cmd_help_track.md` / `docs/cmd_help.md` — no new state, `#doctor` unchanged (a–r)
- **archive lifecycle + `#archive` command (2026-09-12, `feature/wrKFl`)** — plan lifecycle defined (Active → Digest → `xxArchive/`; shipped / superseded / promoted exits, abandonment routed to deletion); design migrated to its own plan per P7 so no copy remains; `AGENTS.md` gained the `xxArchive/` never-read rule (§7a) and the `#archive` §7b row (explicit invocation only) in one write; `#doctor` extended to checks a–r with index↔disk drift, the inverse-leak check and a report-only retirement nudge; new `docs/cmd_help_archive.md`; `templates.md` gained the `INDEX.md` schema; `#bake` reports retirement candidates → `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_archive-lifecycle.md`
- **docs & rulebook integrity pass (2026-09-12, `feature/wrKFl`)** — registry single-sourced (AGENTS.md §7b normative; `cmd_help.md` stamped derived; `GIT_WORKFLOW.md` demoted to git detail, `## OwnedFiles` mechanism retired), `#checkout` de-registered and `#list` given its missing page, `#bake` explicit-only with `#commit` offering a stale-bake first (a `Last Bake` stamp added to the FEAT_HYD template), tiered git confirmation policy, `plans/` and `docs/oZer/` deleted (3 plan re-homes incl. the new **Tasker** feature, 5 research re-homes, 2 deletions), README pointer-ised with GDAL moved into SETUP, MARO_ARCHITECTURE plus seven docs audited, `#doctor` extended to checks a–p → `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_docs-integrity.md`
- **GLOBAL_CONTEXT.md rules migration (2026-09-11)** — GLOBAL_CONTEXT.md reduced to state-only (`## Global Rules`, `## Global Instructions`, `## Always-Loaded Context` removed after bullet-by-bullet triage, not bulk deletion); rules consolidated into AGENTS.md — new §9 Environment & Tooling, QUESTIONS directive, MODE LOCK anti-`#implement` clause, §7a state-only invariant, `#rule global` retargeted to Core Directives; downstream sync (`cmd_help_rule`, `cmd_help_doctor` lint, `templates.md`, `cmd_help_implement` dead pointer) → `xTrack/WorkflowImprovement/260911_FEAT_PLN_WorkflowImprovement_global-context-rules-migration.md`
- **hard rules — Core Directives promotion (2026-06-20)** — 10 rules to prefix-cache zone, §5 git ops hardened, #doctor check (j) → `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_core-directives-promotion.md`
- **AGENTS.md token optimization (2026-06-20)** — 207→149 lines, §7b collapsed to table → `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_agents-md-optimization.md`
- **Workflow management cleanup pass (2026-06-28)** — 8 git commands canonical, MODE LOCK clarified, #help filename-scan, WorkflowAmbiguityFix absorbed
- **#merge hybrid strategy (2026-06-28)** — pre-flight + trivial/non-trivial classification + auto-select rebase/merge → `xTrack/WorkflowImprovement/260628_FEAT_PLN_WorkflowImprovement_merge-strategy.md`
- **AGENTS.md trunk-to-leaf (2026-06-28)** — §5 merged into Core Directives, §§1-4 condensed, Lazy-Load Index added → `xTrack/WorkflowImprovement/260628_FEAT_PLN_WorkflowImprovement_trunk-to-leaf.md`
- **Process simplification (2026-09-04)** — WRITE-ONCE guideline, subfeatures→sections, Focus History stack → `xTrack/WorkflowImprovement/260904_FEAT_PLN_WorkflowImprovement_process-simplification.md`
- **Rule enforcement fix (2026-09-05)** — Roo Code `.roo/rules/agents-source-of-truth.md` forces read+enforce of AGENTS.md at every session (project-committed, not plugin-config); deprecated `.clinerules` removed. AGENTS.md stays single authored source.
- **#doc list attachment column** — `#doc list` gained an "Attached to" column
- **xTrack system review** — spec fragmentation, orphan docs, cache gaps fixed (#doc sync/audit/diff)
- **AGENTSmdNormalization** — AGENTS.md canonical rulebook + adapters + #doctor lint
- **normalize commands** — command-set rationalization (Option C)
- **planning** — Zero-Piecemeal Writes discussion exception

