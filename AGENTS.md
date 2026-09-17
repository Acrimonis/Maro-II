# AGENTS.md

> **Canonical rulebook for Maro-II — single source of truth.**
> Adapters — thin pointers, no content: `CLAUDE.md`, `.clinerules/`, `.claude/skills/xtrack/`. AGENTS.md wins on any conflict.
> Section numbers (3, 6, 7a, 7b, …) stable — referenced from other docs.

**Tier legend — the marker states a rule's force, never its subject.**

- 🛑 AUTHORISATION — the user decides. Acting without their explicit word voids the action: undo it, report it.
- ⛔ BOUNDARY — never cross the requested surface, never assert what was not given, never open opaque data. A breach ships a defect, or spends the user's time and tokens for nothing.
- 💬 CONDUCT — how to answer, and when to stop. A breach makes the reply unusable.
- 🧹 AUTHORSHIP — how the corpus and the delivery are written. A breach rots the record.
- 🟢 GUIDELINE — preferred; deviating is allowed when it is stated.

The higher tier wins when two rules compete, and the more specific wins within a tier. A rule's own
trigger — `when <X>, never <Y>` — is its scope wherever it states one; the tier states only its force.

The tiers mark the Core Directives. In `## Output Contract` the bullets are 💬 CONDUCT, and the glyph
opening one names its subject rather than its force.

Five action classes carry a verdict line before the action, every other rule being enforced by the command
that owns it: adding a dependency, opening a machine-shaped data file, starting work without an order,
touching the device, and stating a claim about the code with no file read behind it.

# Core Directives & Communication Style

## Output Contract

- **📏 Bullets are the unit.** One bullet = one idea, and a bullet stays within roughly two
  sentences — needing a third means it is two bullets. No limit on how many bullets a reply has.
- **🏗️ Long content lives in files.** No headings and no nested bullets in a reply; a list, table or
  code block appears only as a containment block, and anything that does not fit the cap belongs in
  the file the reply points at.
- **🩹 Clarity exception.** A bullet may exceed the cap only to *contain* something — a list, a table,
  a code block. Padding prose never qualifies.
- **🎯 Answer first.** The first bullet is the answer, so a reader who stops after it has the result.
- **🔍 Item-by-item reviews.** List every item as a flat marker of under ten words, then expand only
  the active one into a full paragraph, so the reply stays the same size as the list shortens.
- **📋 Report only what changed.** If the tool output already answered the request, emit only `Done.`
  A report states the problem and the fix, never the mechanism, unless a rule requires the evidence; for
  multi-step changes, add an ELIJP — one or two plain sentences on purpose, jargon stripped — and an
  ELI20, the same thing in twenty words.
- **🗣️ Recommendations argue against themselves.** State the strongest objection to your own
  recommendation, or say none was found. A question is asked only when its answer changes what
  happens next.
- **🎯 Answer only what was asked, then stop.** No next steps, no follow-ups, no extending the
  conversation. EXCEPTION: once the interaction has reached natural conclusion (user said "done",
  "goodbye", or the topic is clearly exhausted) → you MAY add 1 high-level future-direction bullet
  at the very end.

- **⛔ SCOPE LOCK: Zero scope creep.**
  IF the prompt doesn't explicitly request it → do NOT implement it — what the request necessarily
  implies is inside, an adjacent improvement is not.
  IF you spot an adjacent opportunity → never implement it. Mention it now, in one bullet, only when
  ignoring it would leave the delivery wrong or needing rework, or when it contradicts something
  written in the repo — a contradiction is named as one.
  Everything else goes in the task's closing message as a short list, the one exception to *Answer
  only what was asked, then stop*: findings, never a next action for the user to take — no follow-up
  message, no tracking unless asked.
  Unrequested features are defects.

- **⛔ ASK, DON'T GUESS: an unknown is stated, never filled in.**
  WHEN a detail is not in the prompt and not in a file you read → answer with what you have and name
  the gap in one line, or read the source and cite it. Being asked about a component is no exemption,
  and neither is a plausible reconstruction.
  WHEN the request itself is ambiguous → stop and ask, naming the action it authorises; what implied
  approval does not authorise is `MODE LOCK`'s.

- **🛑 MODE LOCK: implementation waits for an order.**
  WHEN the next step would change a file, a branch or a mode → it happens only on an explicit
  directive naming the action — "implement", "go ahead", "switch to Code" — never on a plan being approved, and never on a question.
  WHEN a `#`-command that necessarily entails edits is invoked — conflict resolution is the case —
  → those edits are authorised, and only those: the entitlement is scoped to the operation, not to  the mode it needs.
  With no order, ask. When a discussion closes, state what was resolved and that nothing is
  outstanding — naming the points, never asserting their absence — and never name the next action:
  "ready for `#implement`" is the user's sentence to write.

- **💬 QUESTIONS: Answer before acting.** Answer the question, then wait for direction. Applies in every mode.

- **💬 TECHNICAL CHOICE IS THE AGENT'S: a choice that changes nothing the user can see is not asked about.**
  WHEN a decision touches the implementation of work already ordered → decide it, state it in one line, and
  judge it on code health and performance. A new dependency stays §4's, no file is deleted or renamed and
  nothing is committed on this rule, the write still waiting on `MODE LOCK`.
  WHEN the choice would change what the user sees → it is the user's, and `ASK, DON'T GUESS` applies.

- **🛑 GIT WRITES AND DEPLOYS ARE THE USER'S CALL: run them only on the word, and never raise them.**
  WHEN a command would change the tree, the index or a ref → it runs only on the user's explicit word:
  `add`, `commit`, `push`, `merge`, `rebase` and their kin, staging included — `git add` may stage when
  preparing a `#commit`, never preemptively — and a `new_task(Code)` subtask is no exemption.
  WHEN it changes nothing → it always runs, in every mode: `status`, `log`, `diff`, `fetch`,
  `rev-list`, and anything else read-only.
  WHEN a git `#`-command is invoked → the invocation is the go-ahead: execute it rather than asking
  again, asking the user to run it, or reading it as a request for one. `#commit`, `#push`, `#merge`
  and `#cherry` confirm the operation's scope and never re-litigate authorisation; `#new`, `#move`,
  `#move new` and `#rename` ask nothing.
  WHEN push, commit or deploy was not asked for → never ask, offer, list or remind, in any mode: no
  "want me to push?", no `apk-deploy.bat` or `apk-push.bat`, no note that commits are unpushed.
  When those happen is not the agent's concern: its delivery is complete when the work is, and it
  never waits on, tracks or reports a commit, a push or a deploy.
  `MODE LOCK` owns the edits an operation entails; this rule owns the operation.
- **💬 A gate names its action.** A confirmation gate states the exact action it authorises, and if
  the proposal has moved since the question was asked the gate is re-asked rather than assumed.

- **🛑 PROTECTED BRANCHES: `develop` and `main` are never written to — by anyone, on any instruction.**
  WHEN the target is `develop` or `main` → no push, force-push, revert, direct commit or local merge,
  and the refusal is mechanical: `#push`, `#commit` and `#merge` decline rather than ask.
  The user cannot lift it in conversation, because the branches are shared and a bad write lands on
  everyone else's base; integration happens through a pull request — and no `#`-command overrides it,
  the invocation that grants a git operation granting nothing against this rule.

- **🟢 WRITE-ONCE: Prefer one comprehensive write per source file; batch related edits.**
  Avoid full-file rewrite loops and save-compile-rewrite churn — each rewrite invalidates the prompt cache.
  Targeted apply_diff patches for build errors, review feedback, or discovered edge cases are normal.

- **🧹 ONE HOME PER FACT: a value, a claim or a reader list is written once.** `*.properties` is the source
  of truth for every value — code defaults and documentation follow it, never the reverse — and a duplicate
  that cannot be avoided is deleted rather than kept in sync.

- **🧹 CENTRALISE AND TRIM: docs describe, they do not restate.** A doc points at the key, the accessor or the
  code instead of repeating a number or a caller list; secondary copies are trimmed as a matter of course.

- **⛔ NO BINARY READS: a spatial data file is an opaque blob.**
  WHEN it is machine-shaped and too large to read as text → never open, read or search it —
  `.bin`, `.tif`, `.xyz`, `.nc`, `.asc`, gzipped variants included. Read its metadata, or the
  code that parses it.

- **⛔ REPO ROOT: Treat `.` (the project root folder) as the repository.** Never attempt to open the root itself as a file; read individual files by their path relative to it.

- **⛔ DEVICE EVIDENCE ON REQUEST: the user's device, the user's timing.**
  Committing, deploying and validating are never the agent's concern: it does not ask, offer or track
  them, and its own work is finished when the change is — a deliberate restatement of `GIT WRITES AND
  DEPLOYS ARE THE USER'S CALL` for the device case; keep it, do not trim it.
  WHEN a debug session needs on-device evidence → the logcat case: say the build carrying the logcat
  is ready, then stop, leaving the deployment and the test to the user.
  WHEN the user says it is deployed and the test has run → fetch the logcat, and nothing before that.

- **💬 You may challenge ideas, but defer to my judgement.**

- **💬 Explain/Discuss Gate:** Prompt ending with "explain"/"discuss" → discussion only,
  no code edits/tool modifications. Exceptions: (a) one `FEAT_PLN_` file may capture
  the discussion; (b) `#focus`/`#focus [name] [section]` permitted during discussion.

# Developer Profile & Architectural Translation
- User: Senior Java backend dev → Android/Kotlin. Map ViewModels/Repos ↔ Spring Beans/Services, StateFlow ↔ reactive streams.

# 1. Code Practice — write idiomatic Kotlin for this codebase; never copy-paste from elsewhere.
- **Async:** Coroutines + Flow only — no raw threads or executors.
- **Idioms:** data classes for state, immutable collections, functional transforms over manual loops, `val` unless mutation is required.
- **No copy-paste:** adapting code means rewriting it into this project's patterns and naming — never pasting a block and patching it.

# 2. Architecture Layering — pure Kotlin domain → ViewModel + StateFlow/coroutines → stateless Compose UI.

# 3. Token Optimization — prefer bulk writes and strict context isolation; targeted follow-up patches allowed. See `WRITE-ONCE` (🟢) + the Output Contract.

# 4. Loop Control — max 3–5 autonomous loops per task. Two consecutive build failures → halt.
- **🛑 NEW DEPENDENCIES NEED APPROVAL: adding a dep or lib is the user's call.**
  WHEN a dependency or library the project does not already carry is needed → ask before adding it, and
  never add it on your own initiative.

# 5. Git Operations — see the `#merge` / `#push` / `#commit` rows in §7b, the Core Directives above, and `docs/GIT_WORKFLOW.md` for detail.
- **⛔ NO GIT EDITOR: never leave a git command waiting on an interactive editor.**
  WHEN a command would open one → pass the message inline (`-m "message"`) or suppress the editor
  (`--no-edit`), with the non-interactive flags; if it spawns vim or nano, re-run it that way.
  Applies in every mode, to every task and every agent.

# 6. Spatial Engine — see `docs/MARO_ARCHITECTURE.md`.

# 7a. xTrack — Stack, Bootstrap & Lifecycle
- **Memory Stack:** Context footprint: `xTrack/` (features) + `GLOBAL_CONTEXT.md` (routing), `xTrack/[Feature]/FEAT_DSC_[Feature].md` (epics), `xTrack/[Feature]/FEAT_HYD_[Feature].md` (session state, written by `#bake`). The feature directory + `FEAT_DSC_` are auto-created on first `#track`/`#focus`; `FEAT_HYD_` appears at first `#bake`.
- **Sections:** Feature files group work under `### [Section]` headings (no subfeature state). Keep a section only while it holds an open todo, a retained rule, a doc/key-file mapping **or an open walk** (`## Walk`, any level); `#bake` folds the rest into `## Implemented` (one-liner + plan pointer; planless = bare one-liner). This sentence is the sole statement of the criterion — `docs/cmd_help_bake.md` C12 points here.
- **Focus History:** `GLOBAL_CONTEXT.md` keeps an append-only newest-first stack (cap 10) of `[timestamp] [Feature] — one-liner → FEAT_HYD_[Feature].md`. Top = current focus. `#focus` pushes; `#bake` prunes.
- **🧹 PLAN FILE PLACEMENT: All `FEAT_PLN_*.md`, `FEAT_DOC_*.md` and feature-scoped design files MUST be created in `xTrack/[Feature]/`, named `YYMMDD_FEAT_PLN_[Feature]_[topic].md`.** Extend the existing plan while the topic continues and start a new one on a context switch; a plan is in design until its pointer appears in the feature's `## Implemented`.
- **`xxArchive/` (retired files):** retired plans and docs live in `xTrack/[Feature]/xxArchive/` beside an `INDEX.md`; cross-cutting retirements go to `docs/xxArchive/`. Every feature-summarising command (`#bake`, `#status`, `#doctor`, `#doc list`, `#doc audit`, `#doc update`) **excludes these folders**. `#archive` is the only command that may enter one, and inside it only `INDEX.md` is read — a body needs an explicit per-file request.
- **🧹 GLOBAL_CONTEXT.md IS STATE-ONLY:** it carries the routing map, feature summaries, focus history, global todos and the doc index — never rules, instructions or process specs. All rules live in this file.
- **Feature scoping:** Route docs, key files and todos to the owning feature. Keep feature files lean — `## Docs` for references, `## Key Files` for source paths.
- **Always-loaded (prefix-cache zone):** `AGENTS.md` rides in every request; `xTrack/GLOBAL_CONTEXT.md` is read on demand and must stay small enough for that. Keep both free of duplication — the one marked restatement is the sole exception.
- **Turn 1 Protocol:** Self-contained request → answer directly. Ambiguous/continuing work → read `GLOBAL_CONTEXT.md`, match intent against Routing Map, open matching feature file + hydration. No match → ask scoping question. An open `## Walk` in the matched feature file is reported before anything else — the one piece of state a cold open silently misses.
- **Command delta on open:** Opening a feature — `#focus`, `#track`, or Turn 1 once it resolves a feature — prints a short delta of new and updated commands, once per open and silently when empty. The delta is the newest-first (max 3) WorkflowImprovement `## Implemented` entries that add or change a command, under one plain label.

# 7b. xTrack — Command Reference
Intercept `#`-prefix. All name lookups use fuzzy-resolve cascade (exact → substring → edit-distance → reject — stop on first unique match).

**Feature File FM:** YAML: `name`, `status`, `created`, `modified` (YYYY-MM-DD HH:mm UTC).

| #cmd | Action |
|------|--------|
| `#list` | Dashboard of all features from GLOBAL_CONTEXT.md Feature Summaries table — includes Summary and Modified columns, sorted by Modified desc. Alias: `#features` |
| `#focus [name]` | Pivot active feature (push Focus History entry); bare=prompt pick. Optional `#focus [name] [section]` hydrates only that section; prints the command delta |
| `#track [name]` | Create new feature file + GLOBAL_CONTEXT.md routing/summary rows; prints the command delta |
| `#bake` | Snapshot + consolidation: checkmarks, section rules (fold-done, trim-empty, split, merge, rename-normalize), feature summary, front-matter date, hydration, prune Focus History > 10. Fires only on explicit invocation |
| `#todo` | Bare=list, `[desc]`=append, `[target]:[desc]`=cross-feature (`target`: feature, section, parent, global) |
| `#rule` | Bare = print the tier legend. `[tier]` (name or glyph, matched exactly — the fuzzy cascade does not apply) = reload that tier's rules and print a fingerprint line — tier, rule count, short hash. `all` = read the whole file and report whether the copy this session holds is stale, naming the sections that moved — the read is itself the reload. Explicit invocation only |
| `#doc` | Sub-commands: create, list, read, attach, detach, audit, update. Docs attach to `## Docs` |
| `#archive` | Retire a feature-scoped plan or doc: bare = list the feature's plans and offer a multi-select · `[name]` = move to `xxArchive/` with an index row and the digest floor · `search [terms]` = fuzzy-search the index only (`all` sweeps every feature) · `restore [name]` = un-archive. Fires only on explicit invocation |
| `#status` | Dashboard of active/named feature (reads top Focus History entry). `#status diff` for changes since last bake |
| `#now` | Lightweight orientation: top Focus History entry (feature), CWD, Last Bake. Aliases: `#context`, `#here`, `#feat`, `#feature` |
| `#help [cmd]` | Scan `docs/cmd_help_*.md` filenames, fuzzy-resolve `[cmd]` against stem, read match. Bare=print reference table |
| `#doctor` | Lint xTrack (checks a–s); `#doctor fix` auto-repairs safe classes |
| `#merge` | Pre-flight analysis → trivial/non-trivial classification → auto-select rebase/merge → confirm (yes for direct, `#implement` for full validation pipeline). Push + PR link. **Never touches `develop`/`main`.** |
| `#implement` | Pipeline: Code→implement+build → Ask→review → Architect→report+## Implemented |
| `#go` | Agree with the question currently open; re-asks if the proposal moved since it was asked. `#go impl` = agree and run the `#implement` pipeline |
| `#review [target]` | Independent review of the resolved target — walk item → plan in design → last `#implement` run's Target Files → live proposal (a challenge). Prints "Reviewing X because Y"; a target is fuzzy-resolved. Sweeps the session for the five covered action classes that ran without a verdict line, naming the gaps |
| `#walk [source]` · `#next` · `#prev` · `#skip` | Cursor over an enumerated set, one item expanded at a time; exhaustion closes it; a level marked `Closed` is closed by decision, never a bar to resuming its parked point. State lives in the feature file's `## Walk` section; an open walk blocks `#bake`'s fold and `#archive`'s retirement |
| `#brief` · `#full` | Output mode: subtract the contract's three optional parts (ELIJP, containment blocks, verification lists) or restore them, reporting the resulting mode. Session-lived — `#focus` resets to full |
| `#new [branch]` | Create `feature/[branch]` from `origin/develop` |
| `#commit` | Stage + commit; if the active feature's `xTrack/[Feature]/` state has moved since its hydration baseline, offer a bake first. Asks before committing |
| `#push` | Push current branch to origin. Fires only on explicit invocation — never proposed, never reminded. Asks before pushing. Refuses on `develop`/`main` |
| `#move [branch]` | Stash → switch → pop (existing branch); bare = list local branches newest first, then pick |
| `#move new [branch]` | Stash → create `feature/[branch]` from `origin/develop` → pop; bare = prompt for the name, prefilled `feature/` |
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
| xTrack file shapes — FEAT_DSC, FEAT_HYD, FEAT_DOC, FEAT_PLN, GLOBAL_CONTEXT, Walk, INDEX | `docs/xtrack-templates.md` |
| Any #-command detail | `docs/cmd_help_[cmd].md` via `#help` |
