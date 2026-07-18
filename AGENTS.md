# AGENTS.md

> **Canonical rulebook for Maro-II — single source of truth.**
> Adapters: `CLAUDE.md` = `@AGENTS.md` import | `.clinerules` = pointer | `.claude/skills/xtrack/` = #-commands
> Section numbers (3, 6, 7a, 7b, …) stable — referenced from other docs.

# Core Directives & Communication Style

- **🎯 DIRECT RESPONSE: Answer only what was asked, then stop.**
  DO NOT suggest next steps, ask follow-ups, or extend the conversation.
  EXCEPTION: IF the prior interaction reached natural conclusion
  (user said "done", "goodbye", "that's all", or topic clearly exhausted) →
  THEN you MAY add 1 high-level future-direction bullet at the very end.

- **🗣️ CONCISE: Minimum viable communication.**
  Say what must be said — nothing more. Zero fluff, zero extrapolation,
  zero speculative prose. IF a sentence doesn't carry signal → cut it.

- **⛔ SCOPE LOCK: Zero scope creep.**
  IF the prompt doesn't explicitly request it → do NOT implement it.
  IF you spot an adjacent opportunity → log it as a post-task suggestion,
  never as code in the current delivery. Unrequested features are defects.

- **🔴 NEVER ASSUME: Do not assume broader architecture outside task scope.**
  IF the prompt doesn't reference a system, component, or pattern →
  do NOT fabricate assumptions about it. Stick to what's stated.

- **🔴 MODE LOCK: Do not switch to Code to implement feature or fx without explicit go-ahead.**
  Architect mode is for discussion, design, and workflow management (shell commands,
  git branch operations). Code mode is for source file modifications only.
  IF the user hasn't said "implement", "go ahead", "switch to Code", or similar →
  stay in Architect mode. Unauthorized mode switches are workflow violations.

- **🔴 ABSOLUTE RULE: No agent may execute `git add`, `git commit`, `git push`,
  `git merge`, or `git rebase` without the user's explicit, unambiguous go-ahead.**
  Committing inside `new_task(Code)` subtasks is NOT exempt. `git add` may be used to stage when preparing a `#commit`; do not stage preemptively.
  **Read-only git queries (`git status`, `git log`, `git branch`, `git diff`, `git fetch`) are always permitted in any mode.**
  **Exception:** `#commit`, `#push`, `#merge`, and all git-related `#`-commands are self-contained confirmations — the user's explicit invocation of the command constitutes the go-ahead. No additional confirmation prompt is required.

- **🔴 ABSOLUTE RULE: NEVER write to `develop` or `main` — no pushes,
  no force-pushes, no reverts, no direct commits, no local merges into them.
  Any operation that modifies these branches is forbidden. This rule supersedes
  all other commands, including `#merge` — user "override" does not lift it.**
  Feature work lives on `feature/*` branches; merges to `develop`/`main`
  are done via GitHub pull request only.

- **🔴 WRITE-ONCE: Write each source file exactly once per task.**
  No edit-after-write cycles. No save-compile-rewrite loops.
  IF a file needs changing → combine all edits into a single write.
  Every sequential edit invalidates the prompt cache and costs tokens.
  Exception: Explain/Discuss Gate (see below).

- **🔴 NO BINARY READS: Never open, read, or search `.bin`, `.tif`, `.xyz`,
  `.nc` files.** Treat spatial data files as opaque blobs.
  Read metadata and parsing code only.

- **🔴 Context: Assume . (the project root folder) represents Maro_II_b. Do not attempt to read Maro_II_b as a file.

- **📋 TASK COMPLETION:** If the tool output already answered the request, emit only `"Done."` — do not re-describe what was already displayed. Summarize only when multi-step changes, code modifications, or non-obvious decisions occurred. IF the task involved multi-step changes → emit:
  1. Bullet list of what changed (files touched, logic altered, config).
  2. ELI16 explanation — one or two plain-language sentences explaining the
     *purpose* of the change. Strip Android/Kotlin jargon where possible.
     IF a Java-backend analogy maps cleanly → use it.

- You may challenge ideas, but defer to my judgement.

- **Explain/Discuss Gate:** Prompt ending with "explain"/"discuss" → discussion only,
  no code edits/tool modifications. Exceptions: (a) one `FEAT_PLN_` file may capture
  the discussion; (b) `#focus`/`#sub`/`#sub out` permitted during discussion.

# Developer Profile & Architectural Translation
- User: Senior Java backend dev → Android/Kotlin. Map ViewModels/Repos ↔ Spring Beans/Services, StateFlow ↔ reactive streams. Highlight idiomatic Kotlin (coroutines, data classes, functional collections).
- **Async Rule:** Kotlin Coroutines + Flow only — no raw threads or executors.

# 1. MAD Replication — port legacy → Compose + ViewModel + StateFlow + Coroutines/Flow. Never copy-paste legacy.

# 2. Greenfield Extraction — (1) pure Kotlin domain, (2) ViewModel+StateFlow/Coroutines, (3) stateless Compose UI.

# 3. Token Optimization — bulk writes, strict context isolation. See Core Directives WRITE-ONCE + CONCISE.

# 4. Loop Control — max 3–5 autonomous loops per task. Two consecutive build failures → halt. New deps/libs → approval first.

# 5. Git Operations — see Core Directives above + `docs/GIT_WORKFLOW.md`. Feature work on `feature/*`; merge to `develop`/`main` via PR only.
- **🔴 NO GIT EDITOR: Never open an interactive editor for git commands.** Always use `-m "message"` for commits, `--no-edit` for rebases/merges, and `-S` (signoff) or other flags as needed. If a git command would spawn vim/nano, it must be re-run with the appropriate non-interactive flag. Applies to all modes, all tasks, all agents.

# 6. Spatial Engine — see `docs/MARO_ARCHITECTURE.md`.

# 7a. xTrack — Stack, Bootstrap & Lifecycle
- **Memory Stack:** Context footprint: `xTrack/` (features) + `GLOBAL_CONTEXT.md` (routing), `xTrack/[Feature]/FEAT_DSC_[Feature].md` (epics), `xTrack/[Feature]/FEAT_HYD_[Feature].md` (session state). Auto-create on first `#track`/`#focus`.
- **🔴 PLAN FILE PLACEMENT: All `FEAT_PLN_*.md`, `FEAT_DOC_*.md`, and feature-scoped design files MUST be created in `xTrack/[Feature]/` — NEVER in `plans/`.** The `plans/` directory is a legacy landing zone; new plan files go directly to the feature directory with proper `YYMMDD_FEAT_PLN_[Feature]_[topic].md` naming.
- **Turn 1 Protocol:** Self-contained request → answer directly. Ambiguous/continuing work → read `GLOBAL_CONTEXT.md`, match intent against Routing Map, open matching feature file + hydration. No match → ask scoping question.

# 7b. xTrack — Command Reference
Intercept `#`-prefix. All name lookups use fuzzy-resolve cascade (exact → substring → edit-distance → reject — stop on first unique match).

**Feature File FM:** YAML: `name`, `status`, `created`, `modified` (YYYY-MM-DD HH:mm UTC), `active_subfeature`.

| #cmd | Action |
|------|--------|
| `#list` | Dashboard of all features from GLOBAL_CONTEXT.md Feature Summaries table — includes Modified column, sorted by Modified desc |
| `#focus [name]` | Pivot active feature; bare=prompt pick. Sub: `#focus sub [name]` / `#focus out` |
| `#track [name]` | Create new feature file + GLOBAL_CONTEXT.md routing/summary rows |
| `#sub [name]` | Decompose subfeature under active feature; bare=list with focus marker |
| `#bake` | Snapshot: update checkmarks, feature summary, front-matter date, hydration file |
| `#todo` | Bare=list, `[desc]`=append, `[target]:[desc]`=cross-feature. Same 3-tier for `#rule` |
| `#rule` | Same 3-tier as `#todo`. Global/parent/feature routing by target |
| `#doc` | Sub-commands: create, list, read, attach, detach, audit, update. Docs attach to `## Docs` |
| `#status` | Dashboard of active/named feature. `#status diff` for changes since last bake |
| `#now` | Lightweight orientation: active feature, subfeature, CWD, Last Bake |
| `#help [cmd]` | Scan `docs/cmd_help_*.md` filenames, fuzzy-resolve `[cmd]` against stem, read match. Bare=print reference table |
| `#doctor` | Lint xTrack (a-j checks); `#doctor fix` auto-repairs safe classes |
| `#merge` | Pre-flight analysis → trivial/non-trivial classification → auto-select rebase/merge → confirm (yes for direct, `#implement` for full validation pipeline). Push + PR link. **Never touches `develop`/`main`.** |
| `#implement` | Pipeline: Code→implement+build → Ask→review → Architect→report+## Implemented |
| `#new [branch]` | Create `feature/[branch]` from `origin/develop` |
| `#checkout [branch]` | Switch to existing branch; `#checkout new [branch]` creates + switches |
| `#commit` | `#bake` + `git add -A && git commit` (always prompts confirm) |
| `#push` | Push current branch to origin. Refuses on `develop`/`main` |
| `#move [branch]` | Stash → switch → pop (existing branch) |
| `#move new [branch]` | Stash → create from `origin/develop` → pop |
| `#cherry [target]` | Interactive cherry-pick of unpushed commits (alias: `#copy`) |
| `#rename [branch]` | Rename current branch via `git branch -m` |

Full detail per command in `docs/cmd_help_*.md` — loaded by `#help`. See `docs/cmd_help.md` for the complete reference table.

# 8. Mode Handoff Protocol — all modes return control to Architect on completion.

## 8a. Agent-Specific Adapter Files
`.claude/`, `.clinerules`, `CLAUDE.md` are thin adapters — pointer to this file only. Any info beyond redirect is stale — ignore and flag.

## 8b. Handoff Rules by Mode

| Entered via | Mode | On completion |
|---|---|---|
| Direct user session | **Code** | `switch_mode("architect", report)` |
| Direct user session | **Ask** | `switch_mode("architect", findings)` |
| Direct user session | **Debug** | `switch_mode("architect", root cause + evidence)` |
| Direct user session | **Architect** | No handoff — home base. Summarize, wait for direction. Git read/write permitted without mode switch when appropriate. |
| `new_task(Code)` from Orchestrator | **Code** | Auto-returns (parent Orchestrator resumes) |
| `#implement` pipeline | **Code → Ask → Architect** | Per §7b.16, each hop with summary payload |
| User asks implementation without `#implement` | **Architect** | `new_task(mode=code, ...)` with plan path + todos |

## 8c. Summary Payload Format
When calling `switch_mode`, include 1-3 bullet summary in the reason field:
- **Code:** what was implemented, build status, files changed, deviations
- **Ask:** scope covered, code health observations (spaghetti, factorization, maintenance)
- **Debug:** root cause, evidence, fix/non-fix recommendation

## Lazy-Load Index

Load these only when the task domain matches:

| Task domain | Load |
|-------------|------|
| Code navigation, source structure, package layout, feature-to-code mapping | `docs/maro-code.md` |
| Spatial, bathymetry, depth, coastline | `docs/MARO_ARCHITECTURE.md` |
| UI components, layouts, theme | `docs/ui-component-guidelines.md` |
| Drawers, bottom sheets, overlays | `docs/ui-drawer-guidelines.md` |
| Color tokens, theming, palette | `docs/color-scheme.md` |
| Material Symbols icons (standalone) | `docs/material-icons-standalone-guide.md` |
| Git workflow, merge strategy, conflicts | `docs/GIT_WORKFLOW.md` |
| Project setup, build, deploy | `docs/SETUP.md` |
| FAQs, common issues | `docs/FAQ.md` |
| Any #-command detail | `docs/cmd_help_[cmd].md` via `#help` |
