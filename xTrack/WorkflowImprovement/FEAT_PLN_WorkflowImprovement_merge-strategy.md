<!-- scope: feature -->
# #merge — Smart Pre-Flight + Hybrid Execution Strategy

**Feature:** WorkflowImprovement
**Date:** 2026-06-28
**Branch:** feature/workflow

## Goal

Replace the current hardcoded rebase-only `#merge` with a smart pre-flight analysis that classifies the merge as trivial or non-trivial, auto-selects rebase vs merge, and offers two execution paths: direct shell (trivial) or `#implement` pipeline (non-trivial, for full validation).

## Pre-Flight Checks

```
1. git fetch origin develop
2. Uncommitted changes?     → git status --porcelain
3. Commit count ahead:       → git rev-list --count origin/develop..HEAD
4. File overlap:             → git diff --name-only HEAD...origin/develop
5. Branch pushed to remote?  → git branch -r | grep "origin/[current-branch]"
6. Overlap category:         → classify each file (doc, config, build, source, xTrack, other)
```

## Classification: Trivial vs Non-Trivial

| Overlap Type | Classification |
|---|---|
| Zero overlapping files | **Trivial** |
| Only docs (`docs/*.md`), properties (`*.properties`), scripts (`*.bat`) | **Trivial** |
| Any `.kt` source files | **Non-Trivial** |
| Build files (`gradle/*`, `libs.versions.toml`, `build.gradle.kts`) | **Non-Trivial** |
| `AGENTS.md` or `GLOBAL_CONTEXT.md` | **Non-Trivial** |
| `xTrack/` feature files | **Non-Trivial** |
| > 20 develop commits being pulled in | **Non-Trivial** (regardless of overlap) |

## Strategy Selection (Rebase vs Merge)

| Condition | Strategy | Rationale |
|-----------|----------|-----------|
| Uncommitted changes exist | Stash first, then strategy | Clean working tree required |
| Not pushed AND commits < 10 | **Rebase** | Few commits, safe to replay |
| Not pushed AND zero file overlap | **Rebase** | No conflict risk, instant |
| Already pushed to remote | **Merge** | Avoid force-push disrupting remote |
| Commits >= 10 AND overlap > 0 | **Merge** | Avoid per-commit conflict hell |
| Overlap is docs/config only | **Rebase** | Safe even with many commits |
| Default fallback | **Rebase** | Clean linear history preferred |

## User Interaction

### Trivial Merge

```
#merge

  Fetching origin/develop...
  Branch: feature/workflow (not pushed)
  Ahead of develop: 4 commits
  Develop changes: 2 files
    docs/GIT_WORKFLOW.md        (doc)
    maro.properties             (config)
  Overlap with your branch: none

  → Classification: TRIVIAL — no code overlap
  → Strategy: REBASE (safe, instant)

  Proceed? [yes/no]
```

User says `yes` → executes rebase directly (shell commands, no mode switch).

### Non-Trivial Merge

```
#merge

  Fetching origin/develop...
  Branch: feature/workflow (pushed to origin)
  Ahead of develop: 7 commits
  Develop changes: 12 files
    app/.../MapViewModel.kt         (source) ⚠ OVERLAP
    app/.../TrackDrawerOverlay.kt   (source) ⚠ OVERLAP
    app/.../NavigationViewModel.kt  (source)
    AGENTS.md                       (rules)  ⚠ OVERLAP
    gradle/libs.versions.toml       (build)
    xTrack/GLOBAL_CONTEXT.md        (xTrack) ⚠ OVERLAP
    ... 6 more files (no overlap)

  → Classification: NON-TRIVIAL — 4 overlapping files
  → Strategy: MERGE (pushed branch, single conflict pass)

  ⚠ Sticky issues:
    1. MapViewModel.kt — both branches modified (your feature vs develop refactor)
    2. AGENTS.md — merge conflict likely, requires manual review
    3. GLOBAL_CONTEXT.md — routing table merge needed

  Proceed? [yes / #implement]

  yes         → execute merge now, resolve conflicts inline
  #implement  → Code→Ask→Architect pipeline with full validation
```

### User Options for Non-Trivial

| Response | Action |
|----------|--------|
| `yes` | Execute merge/rebase directly. AI resolves conflicts per strategy table. User accepts risk. |
| `#implement` | Hands off to the pipeline: **Code** executes git + resolves conflicts → **Ask** reviews resolution + build → **Architect** reports + updates hydration + ## Implemented |

### Manual Override (skip classification)

| Command | Action |
|---------|--------|
| `#merge rebase` | Force rebase, skip auto-select, still shows pre-flight |
| `#merge merge` | Force merge, skip auto-select, still shows pre-flight |

## Execution Path 1: Direct Shell (Trivial or user says `yes`)

```
Rebase:
  1. git stash (if dirty)
  2. git rebase origin/develop
  3. git stash pop (if stashed)
  4. git push --force-with-lease (if already on remote)
  5. Print GitHub PR link

Merge:
  1. git stash (if dirty)
  2. git merge origin/develop
  3. git stash pop (if stashed)
  4. git push (if already on remote)
  5. Print GitHub PR link
```

## Execution Path 2: #implement Pipeline (Non-Trivial + user says `#implement`)

```
Code mode:
  1. git stash (if dirty)
  2. Execute git rebase/merge
  3. Resolve conflicts per strategy table
  4. git stash pop
  5. Push (force-with-lease for rebase, regular for merge)
  6. switch_mode(Ask, summary)

Ask mode:
  1. Verify conflict resolution — no leftover markers, build compiles
  2. Review AGENTS.md / GLOBAL_CONTEXT.md merge results
  3. switch_mode(Architect, findings)

Architect mode:
  1. Report: what was merged, conflicts resolved, files changed
  2. Update FEAT_DSC ## Implemented
  3. #bake
```

## Conflict Resolution Strategy

| Conflict Type | Resolution |
|---------------|------------|
| `xTrack/GLOBAL_CONTEXT.md` | Merge both Routing Map rows (dedupe), accept incoming Active Session Pointers format |
| `xTrack/FEAT_DSC_*.md` | Keep both feature entries, sort by Modified desc |
| `AGENTS.md` | Manual — flag for user review in Ask pass |
| Build files (gradle, toml) | Accept incoming version catalog, keep local dependency additions |
| Source code (*.kt) | Feature-owned files: feature wins. Shared files: attempt merge, flag failures |
| Docs (`docs/*.md`) | Manual — review both sides |

## Files to Update

| File | Change |
|------|--------|
| `AGENTS.md` §7b | Update `#merge`: "Pre-flight → trivial/non-trivial classification → auto-select rebase/merge → confirm (yes or #implement)" |
| `docs/cmd_help_git.md` | Rewrite: full spec with both execution paths |
| `docs/cmd_help.md` | Update `#merge` one-liner in reference table |

## What Does NOT Change

- 🔴 `#merge` still never touches `develop`/`main`
- 🔴 Integration to `develop`/`main` is still via GitHub PR only
- `#merge` is still self-confirming (#-command invocation = go-ahead)
- Stash/pop still protects uncommitted work
