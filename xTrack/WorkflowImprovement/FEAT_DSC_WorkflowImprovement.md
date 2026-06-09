---
name: WorkflowImprovement
status: active
created: 2026-06-03 00:00
modified: 2026-06-09 14:52
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
- `plans/xTrack-review-plan.md` — full review analysis and roadmap

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
- [ ] Update templates.md to reflect new command set if template references exist

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

## Todos
- [ ] **Post-merge reconcile xTrack/ across branches.** After `feature/ai-tooling` lands on `develop`, other branches with their own `xTrack/` evolutions (e.g. `feature/300M-Claude-II` carrying Coastline/DepthMapping) will conflict on merge. Procedure: (1) **tooling-system files** (`AGENTS.md`, `CLAUDE.md`, `.clinerules`, `.claude/skills/xtrack/**`, `docs/cmd_help.md`) — accept ai-tooling's version on conflict; (2) **`FEATURE_SCOPE_*.md`** added or edited on spatial — add YAML front-matter (`name`/`status`/`created`/`modified`/`active_subfeature`/`subs_total`/`subs_done`/`one_liner`), normalize all dates to `YYYY-MM-DD`, remove duplicated prose header lines, and split any attached docs out of `## Key Files` into a new `## Docs` section; (3) **`xTrack/CONTEXT_HYDRATION.md`** — resolve the delete/modify conflict in favor of the deletion and split its content into per-feature `xTrack/hydration/CONTEXT_HYDRATION_[Feature].md` files (one per active feature); (4) **`GLOBAL_CONTEXT.md`** — merge Routing Map rows (dedupe), normalize the Active Session Pointers block (add `Last Bake` if missing), normalize dates; (5) run `#doctor fix` to sweep residual drift, then `#doctor` to confirm clean.
- `AGENTS.md` is the canonical rulebook and directly writable; edit without prompting (`.clinerules`/`CLAUDE.md` are pointers).

## Key Files
- `AGENTS.md` — canonical rules incl. § 7a/7b xTrack; `.clinerules`/`CLAUDE.md` are adapters

## Docs
- `docs/cmd_help.md` — command reference (—help output)
- `xTrack/WorkflowImprovement/FEAT_PLN_WorkflowImprovement_planning.md` — Zero-Piecemeal Writes discussion exception design
- `xTrack/WorkflowImprovement/FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` — FEAT_ summary layer token optimization discussion

## Notes
<!-- blockers, design decisions, context for next session -->
Original items 1–17 complete. Tooling spec canonicalized in AGENTS.md (Fix 1). Follow-on normalization (front-matter, #doctor, per-feature hydration, docs) is the AGENTSmdNormalization workstream on branch feature/ai-tooling.
