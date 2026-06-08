---
name: WorkflowImprovement
status: active
created: 2026-06-03 00:00
modified: 2026-06-06 00:00
active_subfeature: none
subs_total: 18
subs_done: 18
one_liner: xTrack #command system, trigger syntax, fuzzy matching, bootstrap, and memory bake lifecycle
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

## Todos
- [ ] **Post-merge reconcile xTrack/ across branches.** After `feature/ai-tooling` lands on `develop`, other branches with their own `xTrack/` evolutions (e.g. `feature/300M-Claude-II` carrying Coastline/DepthMapping) will conflict on merge. Procedure: (1) **tooling-system files** (`AGENTS.md`, `CLAUDE.md`, `.clinerules`, `.claude/skills/xtrack/**`, `docs/cmd_help.md`) — accept ai-tooling's version on conflict; (2) **`FEATURE_SCOPE_*.md`** added or edited on spatial — add YAML front-matter (`name`/`status`/`created`/`modified`/`active_subfeature`/`subs_total`/`subs_done`/`one_liner`), normalize all dates to `YYYY-MM-DD`, remove duplicated prose header lines, and split any attached docs out of `## Key Files` into a new `## Docs` section; (3) **`xTrack/CONTEXT_HYDRATION.md`** — resolve the delete/modify conflict in favor of the deletion and split its content into per-feature `xTrack/hydration/CONTEXT_HYDRATION_[Feature].md` files (one per active feature); (4) **`GLOBAL_CONTEXT.md`** — merge Routing Map rows (dedupe), normalize the Active Session Pointers block (add `Last Bake` if missing), normalize dates; (5) run `#doctor fix` to sweep residual drift, then `#doctor` to confirm clean.
- `AGENTS.md` is the canonical rulebook and directly writable; edit without prompting (`.clinerules`/`CLAUDE.md` are pointers).

## Key Files
- `AGENTS.md` — canonical rules incl. § 7a/7b xTrack; `.clinerules`/`CLAUDE.md` are adapters

## Notes
<!-- blockers, design decisions, context for next session -->
Original items 1–17 complete. Tooling spec canonicalized in AGENTS.md (Fix 1). Follow-on normalization (front-matter, #doctor, per-feature hydration, docs) is the AGENTSmdNormalization workstream on branch feature/ai-tooling.
