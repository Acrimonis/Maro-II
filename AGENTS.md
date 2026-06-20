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

- **⛔ SCOPE LOCK: Zero scope creep.**
  IF the prompt doesn't explicitly request it → do NOT implement it.
  IF you spot an adjacent opportunity → log it as a post-task suggestion,
  never as code in the current delivery. Unrequested features are defects.

- **🔴 MODE LOCK: Do not switch to Code or implement without explicit go-ahead.**
  Architect mode is for discussion/design. Code mode is for execution only.
  IF the user hasn't said "implement", "go ahead", "switch to Code", or similar →
  stay in discussion mode. Unauthorized mode switches are workflow violations.

- **🔴 ABSOLUTE RULE: No agent may execute `git add`, `git commit`, `git push`,
  `git merge`, or `git rebase` without the user's explicit, unambiguous go-ahead.**
  Committing inside `new_task(Code)` subtasks is NOT exempt — see §5.
  **Read-only git queries (`git status`, `git log`, `git branch`, `git diff`, `git fetch`) are always permitted in any mode.**

- **🔴 ABSOLUTE RULE: NEVER write to `develop` or `main` — no pushes,
  no force-pushes, no reverts, no direct commits, no local merges into them.
  Any operation that modifies these branches is forbidden. This rule supersedes
  all other commands, including `#merge` — user "override" does not lift it.**
  Feature work lives on `feature/*` branches; merges to `develop`/`main`
  are done via GitHub pull request only.

- **🗣️ CONCISE: Minimum viable communication.**
  Say what must be said — nothing more. Zero fluff, zero extrapolation,
  zero speculative prose. IF a sentence doesn't carry signal → cut it.

- **🔴 NEVER ASSUME: Do not assume broader architecture outside task scope.**
  IF the prompt doesn't reference a system, component, or pattern →
  do NOT fabricate assumptions about it. Stick to what's stated.

- **🔴 WRITE-ONCE: Write each source file exactly once per task.**
  No edit-after-write cycles. No save-compile-rewrite loops.
  IF a file needs changing → combine all edits into a single write.
  Every sequential edit invalidates the prompt cache and costs tokens.
  Exception: Explain/Discuss Gate (see below).

- **🔴 NO BINARY READS: Never open, read, or search `.bin`, `.tif`, `.xyz`,
  `.nc` files.** Treat spatial data files as opaque blobs.
  Read metadata and parsing code only.

- **📋 TASK COMPLETION: Summarize only if it adds information.**
  IF the response already answered clearly and concisely → stop.
  No summary needed. IF the task involved multi-step changes, code modifications,
  or non-obvious decisions → emit:
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

# 1. Modern Android Development (MAD) Replication Rule
- **Rewrite on Replication:** When replicating or porting an existing legacy feature (Activity, Fragment, XML layout), rewrite it entirely using MAD patterns: Jetpack Compose UI + ViewModel + StateFlow + Coroutines/Flow. Do not copy-paste legacy patterns. Do not reuse legacy layout files, Activities, or Fragments.

# 2. Isolated Greenfield Component Extraction
You are executing a 100% Greenfield rewrite in a fresh workspace, using the legacy repository purely as a static reference.
- **Extraction Sequence:** Port one component at a time: (1) domain logic isolated as pure Kotlin, (2) ViewModel with StateFlow/Coroutines, (3) stateless Compose UI bound to ViewModel state.

# 3. Token & Prompt-Cache Optimization (Strict Operational Enforcement)
- **Cache Optimization:** Group changes into bulk writes across multiple files. Avoid unrelated task switching mid-session. Every sequential edit invalidates the prefix-cache and multiplies token cost.
- **Strict Context Isolation:** Open only the files the user asks for or that directly import the target class. For context, read interfaces/public methods, not full implementations.

# 4. Loop Control & Feedback Guardrails
- **Max Iteration Cap:** Limit autonomous agent loops to 3–5 consecutive turns per task, then force checkpoint.
- **Error Back-off:** If compilation/Gradle error persists 2 consecutive attempts, halt and ask developer.
- **Design Deviation Gate:** Await explicit approval before new dependencies, third-party libraries, or data flow changes.

# 5. Git Operations — STRICT
- **🔴 ABSOLUTE RULE: No agent may execute `git add`, `git commit`, `git push`, `git merge`, or `git rebase` without the user's explicit, unambiguous go-ahead.** This applies to ALL modes and ALL subtasks — including `new_task(mode=code, ...)` subtasks. No exception. **Read-only git queries (`git status`, `git log`, `git branch`, `git diff`, `git fetch`) are always permitted in any mode.**
- **`#commit` ALWAYS requires confirmation.** Even when chained with other commands (e.g. `#bake #commit #implement`), the agent MUST pause at `#commit` and ask the user before executing. Chained commands do NOT constitute implicit consent.
- **`#push` is NEVER automatic** — always ask for confirmation.
- **`git add` may be used to stage** but only after the user confirms they want a commit prepared. Do not stage preemptively.
- Feature work lives on `feature/*` branches; merges to `develop`/`main` are done via pull request only.

# 6. Spatial Engine Constraints
- See `docs/MARO_ARCHITECTURE.md` for Maro-II spatial engine constraints (bounding box, memory-mapped I/O, async rendering).

# 7a. xTrack — Stack, Bootstrap & Lifecycle
- **Memory Stack:** Context footprint: `xTrack/` (features) + `GLOBAL_CONTEXT.md` (routing), `xTrack/[Feature]/FEAT_DSC_[Feature].md` (epics), `xTrack/[Feature]/FEAT_HYD_[Feature].md` (session state). Auto-create on first `#track`/`#focus`.
- **Turn 1 Protocol:** Self-contained request → answer directly. Ambiguous/continuing work → read `GLOBAL_CONTEXT.md`, match intent against Routing Map, open matching feature file + hydration. No match → ask scoping question.

# 7b. xTrack — Command Reference
Intercept `#`-prefix. All name lookups use fuzzy-resolve cascade (exact → substring → edit-distance → reject — stop on first unique match).

**Feature File FM:** YAML: `name`, `status`, `created`, `modified` (YYYY-MM-DD HH:mm UTC), `active_subfeature`.

| #cmd | Action |
|------|--------|
| `#list` | Dashboard of all features from GLOBAL_CONTEXT.md Feature Summaries table |
| `#focus [name]` | Pivot active feature; bare=prompt pick. Sub: `#focus sub [name]` / `#focus out` |
| `#track [name]` | Create new feature file + GLOBAL_CONTEXT.md routing/summary rows |
| `#sub [name]` | Decompose subfeature under active feature; bare=list with focus marker |
| `#bake` | Snapshot: update checkmarks, feature summary, front-matter date, hydration file |
| `#todo` | Bare=list, `[desc]`=append, `[target]:[desc]`=cross-feature. Same 3-tier for `#rule` |
| `#rule` | Same 3-tier as `#todo`. Global/parent/feature routing by target |
| `#doc` | Sub-commands: create, list, read, attach, detach, audit, update. Docs attach to `## Docs` |
| `#status` | Dashboard of active/named feature. `#status diff` for changes since last bake |
| `#now` | Lightweight orientation: active feature, subfeature, CWD, Last Bake |
| `#help [cmd]` | Lazy-load `docs/cmd_help_[cmd].md`. Bare=print reference table |
| `#doctor` | Lint xTrack (a-j checks); `#doctor fix` auto-repairs safe classes |
| `#merge` | Pull `origin/develop` into current feature branch (merge or rebase), push feature branch, provide GitHub PR link. **Never touches `develop`/`main`.** |
| `#implement` | Pipeline: Code→implement+build → Ask→review → Architect→report+## Implemented |

Full detail per command in `docs/cmd_help_*.md` — loaded by `#help`. See `docs/cmd_help.md` for the complete reference table.

# 8. Mode Handoff Protocol

Every mode follows this protocol at completion to return control to Architect.

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
