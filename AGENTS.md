# AGENTS.md

> **Canonical rulebook for Maro-II — single source of truth.**
> Adapters — thin pointers, no content: `CLAUDE.md`, `.clinerules/`, `.claude/skills/xtrack/`. AGENTS.md wins on any conflict.
> Section numbers (3, 6, 7a, 7b, …) stable — referenced from other docs.

# Core Directives & Communication Style

## Output Contract

- **🎯 Answer only what was asked, then stop.** No next steps, no follow-ups, no extending the
  conversation. EXCEPTION: once the interaction has reached natural conclusion (user said "done",
  "goodbye", or the topic is clearly exhausted) → you MAY add 1 high-level future-direction bullet
  at the very end.
- **🗣️ Minimum viable communication.** Say what must be said — nothing more. Zero fluff, zero
  extrapolation, zero speculative prose. IF a sentence doesn't carry signal → cut it.
- **📋 Summarize only what changed.** If the tool output already answered the request, emit only
  `"Done."` When the task involved multi-step changes or non-obvious decisions → emit:
  1. Bullet list of what changed (files touched, logic altered, config).
  2. ELIJP (ELIJP = "Explain Like I'm a Junior Programmer") — one or two plain-language sentences
     explaining the *purpose* of the change. Strip Android/Kotlin jargon where possible.
     IF a Java-backend analogy maps cleanly → use it.

- **⛔ SCOPE LOCK: Zero scope creep.**
  IF the prompt doesn't explicitly request it → do NOT implement it.
  IF you spot an adjacent opportunity → log it as a post-task suggestion,
  never as code in the current delivery. Unrequested features are defects.

- **🔴 NEVER ASSUME: Do not assume broader architecture outside task scope.**
  IF the prompt doesn't reference a system, component, or pattern →
  do NOT fabricate assumptions about it. Stick to what's stated.

- **🔴 MODE LOCK: Do not switch to Code to implement a feature or fix without an explicit go-ahead.**
  Architect mode is for discussion, design, and workflow management (shell commands,
  git branch operations). Code mode is for source file modifications only.
  IF the user hasn't said "implement", "go ahead", "switch to Code", or similar →
  stay in Architect mode. Plan approval ≠ implementation authorization — approving a
  design is not a green light to edit. If a directive is ambiguous or merely implies
  approval, STOP and ask permission. Unauthorized mode switches are workflow violations.
  Never suggest "ready for `#implement`" — the suggestion itself implies permission.

- **🔴 QUESTIONS: Answer before acting.** A question is not an implicit implementation
  order — answer it, then wait for direction. Applies in every mode.

- **🔴 ABSOLUTE RULE: No agent may execute `git add`, `git commit`, `git push`,
  `git merge`, or `git rebase` without the user's explicit, unambiguous go-ahead.**
  Committing inside `new_task(Code)` subtasks is NOT exempt. `git add` may be used to stage when preparing a `#commit`; do not stage preemptively.
  **Read-only git queries (`git status`, `git log`, `git branch`, `git diff`, `git fetch`) are always permitted in any mode.**
  **Exception:** git-related `#`-commands are self-contained confirmations — the explicit invocation is the go-ahead. `#commit`, `#push`, `#merge` and `#cherry` still ask before acting, even when chained; `#new`, `#move`, `#move new` and `#rename` do not.

- **🔴 ABSOLUTE RULE: NEVER write to `develop` or `main` — no pushes,
  no force-pushes, no reverts, no direct commits, no local merges into them.
  Any operation that modifies these branches is forbidden. This rule supersedes
  all other commands, including `#merge` — user "override" does not lift it.**
  Feature work lives on `feature/*` branches; merges to `develop`/`main`
  are done via GitHub pull request only.

- **🟡 WRITE-ONCE (guideline): Prefer one comprehensive write per source file; batch related edits.**
  Avoid full-file rewrite loops and save-compile-rewrite churn — each rewrite invalidates the prompt cache.
  Targeted apply_diff patches for build errors, review feedback, or discovered edge cases are normal.

- **🔴 NO BINARY READS: Never open, read, or search `.bin`, `.tif`, `.xyz`,
  `.nc` files.** Treat spatial data files as opaque blobs.
  Read metadata and parsing code only.

- **🔴 REPO ROOT: Treat `.` (the project root folder) as the repository.** Never attempt to open the root itself as a file; read individual files by their path relative to it.

- **🪲 DEVICE LOGCAT WORKFLOW: If a debug session needs on-device logcat evidence, do NOT capture it unprompted.
  ASK the user to deploy the build and perform the operation, then WAIT — fetch the logcat only once the user tells you to (the user drives the device; the agent pulls the evidence on command).

- You may challenge ideas, but defer to my judgement.

- **Explain/Discuss Gate:** Prompt ending with "explain"/"discuss" → discussion only,
  no code edits/tool modifications. Exceptions: (a) one `FEAT_PLN_` file may capture
  the discussion; (b) `#focus`/`#focus [name] [section]` permitted during discussion.

# Developer Profile & Architectural Translation
- User: Senior Java backend dev → Android/Kotlin. Map ViewModels/Repos ↔ Spring Beans/Services, StateFlow ↔ reactive streams.

# 1. Code Practice — write idiomatic Kotlin for this codebase; never copy-paste from elsewhere.
- **Async:** Coroutines + Flow only — no raw threads or executors.
- **Idioms:** data classes for state, immutable collections, functional transforms over manual loops, `val` unless mutation is required.
- **No copy-paste:** adapting code means rewriting it into this project's patterns and naming — never pasting a block and patching it.

# 2. Architecture Layering — pure Kotlin domain → ViewModel + StateFlow/coroutines → stateless Compose UI.

# 3. Token Optimization — prefer bulk writes and strict context isolation; targeted follow-up patches allowed. See Core Directives: WRITE-ONCE + the Output Contract.

# 4. Loop Control — max 3–5 autonomous loops per task. Two consecutive build failures → halt. New deps/libs → approval first.

# 5. Git Operations — see the `#merge` / `#push` / `#commit` rows in §7b, the Core Directives above, and `docs/GIT_WORKFLOW.md` for detail.
- **🔴 NO GIT EDITOR: Never open an interactive editor for git commands.** Always use `-m "message"`, `--no-edit`, and non-interactive flags; if a command would spawn vim/nano, re-run it with them. Applies to all modes, all tasks, all agents.

# 6. Spatial Engine — see `docs/MARO_ARCHITECTURE.md`.

# 7a. xTrack — Stack, Bootstrap & Lifecycle
- **Memory Stack:** Context footprint: `xTrack/` (features) + `GLOBAL_CONTEXT.md` (routing), `xTrack/[Feature]/FEAT_DSC_[Feature].md` (epics), `xTrack/[Feature]/FEAT_HYD_[Feature].md` (session state, written by `#bake`). The feature directory + `FEAT_DSC_` are auto-created on first `#track`/`#focus`; `FEAT_HYD_` appears at first `#bake`.
- **Sections:** Feature files group work under `### [Section]` headings (no subfeature state). Keep a section only while it holds an open todo, a retained rule, or a doc/key-file mapping; `#bake` folds the rest into `## Implemented` (one-liner + plan pointer; planless = bare one-liner). Full criteria in `docs/cmd_help_bake.md`.
- **Focus History:** `GLOBAL_CONTEXT.md` keeps an append-only newest-first stack (cap 10) of `[timestamp] [Feature] — one-liner → FEAT_HYD_[Feature].md`. Top = current focus. `#focus` pushes; `#bake` prunes.
- **🔴 PLAN FILE PLACEMENT: All `FEAT_PLN_*.md`, `FEAT_DOC_*.md` and feature-scoped design files MUST be created in `xTrack/[Feature]/`, named `YYMMDD_FEAT_PLN_[Feature]_[topic].md`.**
- **🔴 GLOBAL_CONTEXT.md IS STATE-ONLY:** it carries the routing map, feature summaries, focus history, global todos and the doc index — never rules, instructions or process specs. All rules live in this file; the `#rule` `global` target appends to Core Directives above.
- **Feature scoping:** Route docs, key files and todos to the owning feature. Keep feature files lean — `## Docs` for references, `## Key Files` for source paths.
- **Always-loaded (prefix-cache zone):** `AGENTS.md`, `xTrack/GLOBAL_CONTEXT.md`. Keep both small and free of duplication.
- **Turn 1 Protocol:** Self-contained request → answer directly. Ambiguous/continuing work → read `GLOBAL_CONTEXT.md`, match intent against Routing Map, open matching feature file + hydration. No match → ask scoping question.

# 7b. xTrack — Command Reference
Intercept `#`-prefix. All name lookups use fuzzy-resolve cascade (exact → substring → edit-distance → reject — stop on first unique match).

**Feature File FM:** YAML: `name`, `status`, `created`, `modified` (YYYY-MM-DD HH:mm UTC).

| #cmd | Action |
|------|--------|
| `#list` | Dashboard of all features from GLOBAL_CONTEXT.md Feature Summaries table — includes Summary and Modified columns, sorted by Modified desc. Alias: `#features` |
| `#focus [name]` | Pivot active feature (push Focus History entry); bare=prompt pick. Optional `#focus [name] [section]` hydrates only that section |
| `#track [name]` | Create new feature file + GLOBAL_CONTEXT.md routing/summary rows |
| `#bake` | Snapshot + consolidation: checkmarks, section rules (fold-done, trim-empty, split, merge, rename-normalize), feature summary, front-matter date, hydration, prune Focus History > 10. Fires only on explicit invocation |
| `#todo` | Bare=list, `[desc]`=append, `[target]:[desc]`=cross-feature. Same 3-tier for `#rule` |
| `#rule` | Same 3-tier as `#todo`. `global` → appends to this file's Core Directives; parent/feature/section → the feature file |
| `#doc` | Sub-commands: create, list, read, attach, detach, audit, update. Docs attach to `## Docs` |
| `#status` | Dashboard of active/named feature (reads top Focus History entry). `#status diff` for changes since last bake |
| `#now` | Lightweight orientation: top Focus History entry (feature), CWD, Last Bake. Aliases: `#context`, `#here`, `#feat`, `#feature` |
| `#help [cmd]` | Scan `docs/cmd_help_*.md` filenames, fuzzy-resolve `[cmd]` against stem, read match. Bare=print reference table |
| `#doctor` | Lint xTrack (checks a–p); `#doctor fix` auto-repairs safe classes |
| `#merge` | Pre-flight analysis → trivial/non-trivial classification → auto-select rebase/merge → confirm (yes for direct, `#implement` for full validation pipeline). Push + PR link. **Never touches `develop`/`main`.** |
| `#implement` | Pipeline: Code→implement+build → Ask→review → Architect→report+## Implemented |
| `#new [branch]` | Create `feature/[branch]` from `origin/develop` |
| `#commit` | Stage + commit; if the active feature's `xTrack/[Feature]/` state has moved since its hydration baseline, offer a bake first. Asks before committing |
| `#push` | Push current branch to origin. Asks before pushing. Refuses on `develop`/`main` |
| `#move [branch]` | Stash → switch → pop (existing branch) |
| `#move new [branch]` | Stash → create `feature/[branch]` from `origin/develop` → pop |
| `#cherry [target]` | Interactive cherry-pick of unpushed commits (alias: `#copy`) |
| `#rename [branch]` | Rename current branch via `git branch -m` |

Full detail per command in `docs/cmd_help_*.md` — loaded by `#help`. `docs/cmd_help.md` is a derived printed view of §7b.

# 8. Mode Handoff Protocol — all modes return control to Architect on completion.

## 8a. Agent-Specific Adapter Files
`.claude/`, `.clinerules/`, `CLAUDE.md` are thin adapters — pointer to this file only. Any info beyond redirect is stale — ignore and flag.

## 8b. Handoff Rules by Mode

| Entered via | Mode | On completion |
|---|---|---|
| Direct user session | **Code** | `switch_mode("architect", report)` |
| Direct user session | **Ask** | `switch_mode("architect", findings)` |
| Direct user session | **Debug** | `switch_mode("architect", root cause + evidence)` |
| Direct user session | **Architect** | No handoff — home base. Summarize, wait for direction. Git read/write permitted without mode switch when appropriate. |
| `new_task(Code)` from Orchestrator | **Code** | Auto-returns (parent Orchestrator resumes) |
| `#implement` pipeline | **Code → Ask → Architect** | Per `docs/cmd_help_implement.md`, each hop with summary payload |
| User asks implementation without `#implement` | **Architect** | `new_task(mode=code, ...)` with plan path + todos |

## 8c. Summary Payload Format
When calling `switch_mode`, include 1-3 bullet summary in the reason field:
- **Code:** what was implemented, build status, files changed, deviations
- **Ask:** scope covered, code health observations (spaghetti, factorization, maintenance)
- **Debug:** root cause, evidence, fix/non-fix recommendation

# 9. Environment & Tooling
- **Shell:** Windows `cmd.exe`. Use CMD built-ins (`dir`, `del`, `type`, `findstr`) — not PowerShell, and not Unix utilities (`sed`, `grep`, `cat`, `rm`, `cp`, `mv`).
- **ADB:** `adb.exe` is on PATH — call `adb` directly, no path qualifier. Device workflow in `docs/SETUP.md`.
- **APK pipeline:** `apk-bake.bat` bakes data → `apk-build.bat` packages only (`gradlew assembleDebug`) → `apk-deploy.bat` installs + relaunches. Command detail in `README.md`; the bake-vs-build contract lives in the BakeNormalization feature file.

## Lazy-Load Index

Load these only when the task domain matches:

| Task domain | Load |
|-------------|------|
| Code navigation, source structure, package layout, feature-to-code mapping | `docs/maro-code.md` |
| Spatial, bathymetry, depth, coastline | `docs/MARO_ARCHITECTURE.md` |
| UI components, layouts, theme | `docs/ui-component-guidelines.md` |
| Drawers, bottom sheets, overlays | `docs/ui-drawer-guidelines.md` |
| Lists, filters, swipe actions, multiselect | `docs/ui-lists-guidelines.md` |
| Color tokens, theming, palette | `docs/color-scheme.md` |
| Material Symbols icons (standalone) | `docs/material-icons-standalone-guide.md` |
| Git workflow, merge strategy, conflicts | `docs/GIT_WORKFLOW.md` |
| Project setup, build, deploy | `docs/SETUP.md` |
| FAQs, common issues | `docs/FAQ.md` |
| Any #-command detail | `docs/cmd_help_[cmd].md` via `#help` |
