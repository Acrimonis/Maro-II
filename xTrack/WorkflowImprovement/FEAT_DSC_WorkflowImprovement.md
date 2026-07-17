---
name: WorkflowImprovement
status: active
created: 2026-06-03 00:00
modified: 2026-06-28 16:01
active_subfeature: none
---

# Feature: WorkflowImprovement

**Description:**
Improving the xTrack workflow and command system (canonicalized in AGENTS.md) — trigger syntax, templates, lifecycle protocols, and bootstrap logic.

## Subfeatures
### Trigger phrase syntax redesign (colon-delimited commands)  [x]
### Feature file template (Status/Created/Description/Subfeatures/Key Files/Notes)  [x]
### Turn 1 protocol with intent gate + scope question  [x]
### Memory Bake event-driven triggers (closing phrases, task wind-down, #bake)  [x]
### Hash-prefix command dispatcher (#) with fuzzy fallback  [x]
### xTrack bootstrap logic (auto-create on first `track:`)  [x]
### Active focus pivot ambiguity with cross-feature intercept + #todo + #instruction  [x]
### #-only command system (colon-delimited triggers removed)  [x]
### #instruction command for context attachments  [x]
### Bare #todo/#instruction list mode  [x]
### Smart subfeature nesting for #todo  [x]
### Bare `#doc` — show active feature's Key Files (scoped, not global)  [x]
### #doc create [name] — create doc in docs/, prompt for scope tag  [x]
### #doc list — scan docs/*.md, display filename + scope tag + heading  [x]
### #doc read [name] — hydrate doc into AI context  [x]
### #doc attach [name] — add doc; bare = prompt pick-list  [x]
### #doc detach [name] — remove doc; bare = prompt pick-list  [x]

### #doc list attachment column  [x]
`#doc list` now scans each FEATURE_SCOPE_*.md's `## Docs` (and subfeature `#### Docs`) sections to show which feature/subfeature each doc is attached to, as an extra column in the table.

#### Todos
- [x] Update AGENTS.md §7b.11 `#doc list` spec to add "Attached to" column
- [x] Update docs/cmd_help.md `## #doc` section to document the column

#### Key Files
- `AGENTS.md` — §7b.11
- `docs/cmd_help.md` — `## #doc` section

### xTrack system review — #doc sync, #doc audit, #diff, spec consolidation  [x]
Complete review of the xTrack system: identified spec fragmentation, orphan docs, missing ## Docs sections, cache optimization gaps, and template drift. Implemented all fixes across 3 phases.

#### Todos
- [x] Phase 1: Quick Fixes — flip #doc list attachment column [x], pivot active feature to WorkflowImprovement, add ## Docs to 5 feature files, attach most orphan docs
- [x] Phase 2: Spec Consolidation — deprecate commands.md (redirect to AGENTS.md), add Always-Loaded Context to GLOBAL_CONTEXT.md + templates.md, fix template drift
- [x] Phase 3: New Capabilities — add #doc sync, #doc audit, #diff specs to AGENTS.md §7b and docs/cmd_help.md

#### Rules
- AGENTS.md §7b is the sole canonical spec for all #-commands. references/commands.md is deprecated.
- Always-Loaded Context section ensures cache-critical files are loaded every session.

#### Key Files
- `AGENTS.md` — §7b updated with #doc sync, #doc audit, #diff
- `xTrack/GLOBAL_CONTEXT.md` — Always-Loaded Context + Global Instructions
- `docs/cmd_help.md` — new command sections
- `.claude/skills/xtrack/references/commands.md` — deprecated

#### Docs
- *(no plans attached)*

### AGENTSmdNormalization  [x]
Vendor-neutral consolidation onto the AGENTS.md standard + xTrack hardening (this branch). All 6 fixes implemented + committed.

#### Todos
- [x] Fix 1 — AGENTS.md canonical rulebook + adapters (CLAUDE.md @import, .clinerules pointer, skill defers); fuzzy reconciled. Committed `288563a`.
- [x] Fix 2 — YAML front-matter on feature files + date normalize. Committed `e806c22`.
- [x] Fix 3 — `#doctor` lint command. Committed `e806c22`.
- [x] Fix 4 — per-feature hydration under `xTrack/hydration/`. Committed `e806c22`.
- [x] Fix 5 — docs: recursive `#doc list`, `## Docs` section, tolerant scope scan, `archived` scope. Committed `e806c22`.
- [x] Fix 6 — `#feature` orientation command (active feature/sub + working path) + rename `#list` → `#features` (alias kept). Committed `e806c22`.

#### Rules
- Edit canonical rules in `AGENTS.md` only; `.clinerules`/`CLAUDE.md` are pointers.

#### Key Files
- `AGENTS.md`, `CLAUDE.md`, `.clinerules`
- `.claude/skills/xtrack/{SKILL.md, references/commands.md, references/templates.md}`
- `docs/cmd_help.md`

### normalize commands  [x]
Rationalize the xTrack command set per Option C: merge `#features` into `#context list`, rename `#feature` to `#context`, merge `#diff` into `#status diff`, move `#sub focus`/`#sub out` to `#focus sub`/`#focus out`, keep `#doc` singular, no flag syntax.

#### Todos
- [x] Update AGENTS.md §7b — rename `#feature`→`#context`, merge `#features`/`#list` into `#context list`, merge `#diff` into `#status diff`, move `#sub focus`→`#focus sub` and `#sub out`→`#focus out`, update `#help` command list, remove `#list` alias from item 8
- [x] Update docs/cmd_help.md — rewrite reference table, add `## #context` section, add `#status diff` sub-command, remove `## #diff` section, update `## #sub` (remove focus/out), add `## #focus` section (add sub/out), remove `#list` from help
- [x] Update templates.md — FEAT_HYD location corrected (2026-06-28 cleanup pass)

#### Rules
- §7b in AGENTS.md and docs/cmd_help.md must be updated atomically — both describe the same command surface.
- `#sub` bare (list) and `#sub [name]` (create) stay as-is; only `focus`/`out` move to `#focus`.

#### Key Files
- `AGENTS.md` — §7b items 1,2,8,9,13,14
- `docs/cmd_help.md` — reference table + sections
- `.claude/skills/xtrack/references/templates.md` — if applicable

### planning  [x]
Define the Zero-Piecemeal Writes discussion exception: allow one plan file per discussion, auto-attach to feature ## Docs, permit context-changing commands during discussion.

#### Todos
- [x] Update AGENTS.md §3 Zero-Piecemeal Writes with discussion exception
- [x] Update AGENTS.md Explain/Discuss Gate with plan-file + context-commands exceptions
- [x] Create plans/planning.md and attach to WorkflowImprovement ## Docs

#### Rules
- Exception applies only during Explain/Discuss gate. Outside discussion, normal Zero-Piecemeal Writes enforcement is in full effect.
- Context-changing commands (#focus, #sub, #sub out) are permitted during discussion and must be auto-followed.

#### Key Files
- `AGENTS.md` — Core Directives + §3

### gitting-it  [x]

#### Todos
- [x] Define `#new [branch]` — fetch origin/develop, checkout new branch from it
- [x] Define `#commit` — run `#bake`, then `git add -A && git commit`
- [x] Define `#push` — run `git push` (respect current branch)
- [x] Define `#move [branch]` — stash uncommitted changes, switch branch, pop stash
- [x] Define `#move new [branch]` — stash, create branch from develop, pop stash
- [x] Define `#cherry [target]` / `#copy [target]` — interactive cherry-pick of unpushed commits
- [x] Define `#rename [branch]` — rename current branch via `git branch -m`
- [x] Define `#merge` — rebase onto origin/develop + force-push; D/F/M priority prompt on conflict
- [x] Define `#merge [branch]` — rebase onto specific remote branch
- [x] Update `docs/cmd_help.md` with all git commands in reference table + Git section
- [ ] On-device / real-repo verification of all git shortcuts — deferred, low priority

#### Rules
- Git commands are convenience shortcuts for the xTrack workflow, not replacements for manual git operations.
- `#new` always branches from the latest remote `develop` — never from the current branch.
- `#commit` always includes `#bake` first to snapshot session state.
- `#push` uses the current branch name — no safety prompt (use explicit git commands for safety).
- `#move` operates on uncommitted changes only (stash-based). Committed work stays on source branch.
- `#cherry`/`#copy` does NOT delete source commits — user cleans up with `git reset --hard HEAD~N`.

#### Key Files
- `docs/cmd_help.md` — Git section + updated reference table
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-move-command.md` — full design spec for #move and #cherry

#### Docs
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-move-command.md` — design spec for #move stash + #cherry interactive copy

### hard rules  [x]
Enforce the "no git write until green-lit" rule across all agents — audit rule placement, detect bypass vectors, harden the wording, and add automated enforcement.

#### Todos
- [x] Audit all extant rule locations — AGENTS.md §5, GLOBAL_CONTEXT.md Global Rules, .clinerules, CLAUDE.md — for consistency and enforcement strength
- [x] Identify bypass vectors — exceptions (§5 `new_task` exception, `#commit` chained commands, mode-handoff Protocol §8b)
- [x] Harden wording: from "no auto commit/push" to "no git write operations without explicit user green light" (including `git add`, `git commit`, `git push`, `git merge`, `git rebase` without explicit permission)
- [x] Remove or tighten the `new_task(mode=code,...)` commit exception
- [x] Add a compliance check to the `#doctor` command (check j — staged changes on develop/main)

#### Rules
- No agent may execute `git add`, `git commit`, `git push`, `git merge`, or `git rebase` without explicit user permission — even inside `new_task` subtasks.
- `#commit` command must not auto-execute; it must prompt for confirmation after `#bake`.
- This rule must be stated in the first ~10 lines of AGENTS.md (Core Directives section) so it's always in the AI's prefix-cache.
- `#doctor` must flag any unsanctioned git writes as a lint warning.
- Architect mode may execute shell commands (bat scripts, git read-only queries, etc.) directly — no mode switch needed for CLI operations.

#### Key Files
- `AGENTS.md` — §5 Git Operations, Core Directives
- `GLOBAL_CONTEXT.md` — Global Rules
- `docs/cmd_help_git.md` — git command docs
- `.clinerules` — adapter file

#### Todos
- [x] Add compliance check to `#doctor`

## Todos
- [ ] **Post-merge reconcile xTrack/ across branches** — deferred. Procedure documented in FEAT_DSC; execute when first cross-branch xTrack conflict occurs.
- `AGENTS.md` is the canonical rulebook and directly writable; edit without prompting (`.clinerules`/`CLAUDE.md` are pointers).

## Key Files
- `AGENTS.md` — canonical rules incl. § 7a/7b xTrack; `.clinerules`/`CLAUDE.md` are adapters

## Docs
- `docs/cmd_help.md` — command reference summary table
- `docs/cmd_help_now.md` — #now / #list detail
- `docs/cmd_help_status.md` — #status / #status diff detail
- `docs/cmd_help_track.md` — #track detail
- `docs/cmd_help_focus.md` — #focus / #focus sub / #focus out detail
- `docs/cmd_help_sub.md` — #sub detail
- `docs/cmd_help_todo.md` — #todo detail
- `docs/cmd_help_rule.md` — #rule detail
- `docs/cmd_help_doc.md` — #doc detail
- `docs/cmd_help_bake.md` — #bake detail
- `docs/cmd_help_help.md` — #help detail
- `docs/cmd_help_doctor.md` — #doctor detail
- `docs/cmd_help_git.md` — git workflow shortcuts detail
- `docs/cmd_help_doc_sync.md` — #doc sync detail
- `docs/cmd_help_doc_audit.md` — #doc audit detail
- `docs/GIT_WORKFLOW.md` — Git workflow conventions
- `xTrack/WorkflowImprovement/260608_FEAT_PLN_WorkflowImprovement_planning.md` — Zero-Piecemeal Writes discussion exception design
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` — FEAT_ summary layer token optimization discussion
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_xtrack-reorg.md` — xTrack FEAT_* file reorganization implementation spec
- `xTrack/WorkflowImprovement/FEAT_DOC_WorkflowImprovement_profile.md` — feature profile (auto-generated by #doc sync)
- `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_core-directives-promotion.md` — Core Directives promotion implementation plan
- `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_hard-rules-enforcement.md` — Hard rules enforcement discussion
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_merge-conflict-resolution.md` — AI-assisted #merge conflict resolution spec
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_cmd-dispatch-refactor.md` — Command lookup dispatch refactor plan
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_agents-md-optimization-plan.md` — AGENTS.md optimization pass findings
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_newtask-delegation.md` — new_task delegation: Architect → Code without mode switch
- `plans/plan-migration-plan.md` — Plan-to-FEAT_PLN_ bulk migration plan (62 files)
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-merge-command.md` — Git merge command design
- `xTrack/WorkflowImprovement/260628_FEAT_PLN_WorkflowImprovement_merge-strategy.md` — #merge hybrid strategy: pre-flight + trivial/non-trivial classification + auto-select + #implement pipeline
- `xTrack/WorkflowImprovement/260628_FEAT_PLN_WorkflowImprovement_trunk-to-leaf.md` — AGENTS.md trunk-to-leaf token optimization: deduplicate, condense, Lazy-Load Index

## Implemented

### hard rules — Core Directives promotion (2026-06-20)

- **Core Directives rewritten** — 10 rules promoted to prefix-cache zone: 🎯 DIRECT RESPONSE, ⛔ SCOPE LOCK, 🔴 MODE LOCK, 🔴 NO GIT WRITES, 🔴 NO BRANCH, 🗣️ CONCISE, 🔴 NEVER ASSUME, 🔴 WRITE-ONCE, 🔴 NO BINARY READS, 📋 TASK COMPLETION
- **§5 Git Operations hardened** — `new_task(Code)` exception removed; `#commit` always prompts; scope expanded to all write ops (add, commit, push, merge, rebase)
- **§3 duplicate rules removed** — WRITE-ONCE and NO BINARY READS live only in Core Directives now
- **`#doctor` check (j)** — detects staged changes on develop/main (autofix: unstage)
- **GLOBAL_CONTEXT.md Global Rules** — condensed git/Code-switch lines to reference pointer to AGENTS.md
- **`docs/cmd_help_git.md`** — `#commit` updated with always-confirm note

### AGENTS.md token optimization (2026-06-20)

- **AGENTS.md compressed** — 207 → 149 lines (28% reduction)
- **Header** condensed from 14→5 lines (adapter table, quote block)
- **Dev Profile** merged from 5→3 lines
- **§2 Extraction** compressed from 6→3 lines
- **§7b Command Reference** collapsed from 58→23 lines (compact table, detail delegated to `docs/cmd_help_*.md` via `#help`)
- **§4/§7a/§8** tightened
- No rules removed — all commands, protocols, and behavioral directives preserved

### Workflow management cleanup pass (2026-06-28)

- **8 git commands added to AGENTS.md §7b** — `#new`, `#checkout`, `#commit`, `#push`, `#move`, `#move new`, `#cherry`/`#copy`, `#rename` now canonical in the command table
- **MODE LOCK clarified** — Architect mode permits shell commands + all git branch operations; Code mode is for source file modifications only
- **#help dispatch** — updated to filename-scan (`docs/cmd_help_*.md`) instead of inline list
- **WorkflowAmbiguityFix absorbed** — all 5 todos marked done, feature status→done, concerns already addressed in prior WorkflowImprovement passes
- **Lingering todos closed** — templates.md sync, #doctor check (j), post-merge reconcile deferred
- **templates.md** — FEAT_HYD path corrected (project root → xTrack/[Feature]/)
- **docs/cmd_help.md** — `#checkout` entry added, sync verified against AGENTS.md §7b
- **GLOBAL_CONTEXT.md** — branch→feature/workflow, summaries updated, global rules reference AGENTS.md

### #merge hybrid strategy (2026-06-28)

- **Pre-flight analysis** — fetch + commit count + file overlap + push status + classification (trivial vs non-trivial)
- **Auto-select** — rebase (few commits, no overlap, docs-only) vs merge (pushed branch, many commits, source overlap)
- **Hybrid execution** — trivial: direct shell with yes/no confirmation; non-trivial: sticky issues listed, `yes` for direct or `#implement` for Code→Ask→Architect pipeline
- **Conflict resolution strategy** — per-file-type rules (GLOBAL_CONTEXT.md auto-merge, AGENTS.md manual, source .kt by feature ownership)
- **Files:** AGENTS.md §7b, docs/cmd_help_git.md (full rewrite), docs/cmd_help.md one-liner

### AGENTS.md trunk-to-leaf token optimization (2026-06-28)

- **§5 merged into Core Directives** — git rules no longer duplicated; 5 lines → 1 pointer line
- **§§1-4 condensed** — MAD (2→1), Extraction (3→1), Token (3→1), Loop (4→2) — saved 7 lines
- **§8 compressed** — preamble removed (1 line saved)
- **Lazy-Load Index added** — 9-domain routing table: spatial, UI, drawers, colors, icons, git, setup, FAQ, #-commands — all docs discoverable from AGENTS.md trunk
- **GLOBAL_CONTEXT.md** — Global Instructions tightened, references AGENTS.md Lazy-Load Index

## Notes
<!-- blockers, design decisions, context for next session -->
Original items 1–17 complete. Tooling spec canonicalized in AGENTS.md (Fix 1). WorkflowAmbiguityFix absorbed 2026-06-28. Remaining deferred: git shortcut on-device verification, post-merge xTrack reconcile.
