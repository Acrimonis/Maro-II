# `#merge` Command Design

> Discussion for subfeature `gitting-it` under [`WorkflowImprovement`](xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md).

## Problem

A pushed feature branch doesn't auto-merge into `develop`. You need either:
- **A)** Merge develop into your feature branch (to stay up to date)
- **B)** Merge your feature branch into develop (to ship)

## Proposed syntax

```
#merge                Merge current feature branch into develop (ship)
#merge develop        Merge develop into current feature branch (update)
```

### `#merge` (bare) — ship feature to develop

```
1. git fetch origin develop
2. git checkout develop
3. git merge feature/bag-o-stuff
4. If no conflict: git push origin develop; git checkout feature/bag-o-stuff
5. If conflict: prompt user for resolution strategy
```

### `#merge develop` — update feature with latest develop

```
1. git fetch origin develop
2. git merge origin/develop
3. If no conflict: done
4. If conflict: prompt user for resolution strategy
```

## Conflict resolution: "who is the reference?"

The prompt is phrased as a domain question, not a git technicality.

### Scenario A: Feature 1 vs Feature 2 (no overlap)

No conflict — the rebase/merge succeeds silently. Two different features on different files don't collide.

### Scenario B: Both branches touched the same code

Both you and develop changed the same files. The rebase hits a conflict. The prompt asks:

```
> #merge develop

Rebasing feature/bag-o-stuff onto origin/develop
Conflicts in 3 files:
  src/SettingsManager.kt
  src/CoastlineViewModel.kt
  src/MapScreen.kt

These files changed on both your branch and develop.
Who is the reference for this functionality?

  [D]evelop is the reference  — keep develop's version
  [F]eature is the reference  — keep your version
  [M]anual                    — abort, I'll resolve by hand
```

| Your choice | What happens |
|-------------|-------------|
| **D** | `git checkout --ours` on all conflicted files → develop's version wins. Your conflicting commits are skipped (`git rebase --skip`). |
| **F** | For each conflicted file, keep your version → `git rebase --continue` with your changes intact. |
| **M** | `git rebase --abort` — no changes made. You resolve manually. |

### Why this phrasing matters

"Who is the reference on feature 1?" is the right question because:

- If **develop** has the canonical version of feature 1 (e.g., it was merged first), then your conflicting changes in that file should give way to develop's version, and your commit should adapt to work with what develop has.
- If **your feature branch** has the canonical version (you're actively developing it and develop somehow got a stale copy), then your version should override.

This avoids the confusing `--ours`/`--theirs` terminology swap during rebase (where `--ours` = develop, `--theirs` = your branch — the opposite of merge!).

## Edge cases

| Scenario | Behaviour |
|----------|-----------|
| Uncommitted changes | Stash first (same as `#move`), merge, pop stash |
| Already up to date | Print "Already up to date" — no-op |
| Merge aborted (M) | `git merge --abort`, user resolves manually |
| Not on a feature branch | Prompt confirmation before merging to `develop` |
