# `#move` Command Design

> Discussion for subfeature `gitting-it` under [`WorkflowImprovement`](xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md).

## Proposed syntax

```
#move [branch]        Move uncommitted changes to an existing branch (fuzzy-resolve)
#move new [branch]    Move uncommitted changes to a new branch (same as #new + stash)
```

## What it does

Both variants operate on **uncommitted working-tree changes only** (stash-based). Committed-but-unpushed work stays on the original branch — use explicit git for that.

### `#move [branch]`

1. Check for uncommitted changes → if none, print "Nothing to move" and exit
2. Fuzzy-resolve `[branch]` against existing local branches
3. If match found:
   - `git stash push -m "move-$(date +%s)"`
   - `git checkout [matched-branch]`
   - `git stash pop`
4. If no match:
   - Print "Branch '[branch]' not found. Use `#move new [branch]` to create it, or `#new [branch]` for a fresh checkout."

### `#move new [branch]`

1. `git stash push -m "move-$(date +%s)"`
2. `git fetch origin develop`
3. `git checkout -b [branch] origin/develop`
4. `git stash pop`

Always creates from remote `develop`, never from the current branch.

## Edge cases

| Scenario | Behaviour |
|----------|-----------|
| No uncommitted changes | Print "Nothing to move" — no-op |
| Stash pop conflicts | Git leaves conflicted files in working tree, user resolves manually (same as any `git stash pop` conflict) |
| `[branch]` matches multiple branches | Fuzzy-resolve lists matches and asks user to pick (same protocol as `#focus`) |
| Already on target branch | `git stash` + `git checkout same-branch` + `stash pop` = no-op (changes restored) — print "Already on [branch]" |

## Interaction with `#new`

`#move new [branch]` is effectively `#new [branch]` with a `git stash`/`git stash pop` wrapper around it. The branch-creation logic is identical.

## Git commands expansion

```
#move foo
  → git stash push -m "move-1712345678"
  → git checkout foo
  → git stash pop

#move new bar
  → git stash push -m "move-1712345678"
  → git fetch origin develop
  → git checkout -b bar origin/develop
  → git stash pop
```

## Cherry-pick variant — `#cherry` / `#copy`

Copy committed-but-unpushed commits from current branch to `[target]`. Interactive — lists commits with one-liners and asks what to do.

### Syntax

```
#cherry [target]          Interactive: list commits, ask which to copy
#copy [target]            Alias (identical behaviour)
```

### Interactive flow

```
> #cherry feature/bar

Found 3 unpushed commits on feature/gps-tweak:
  [1] abc1234 Fix typo in GpsLocationSource
  [2] def5678 Add haversine guard to animateTo
  [3] ghi9012 Update xTrack subfeature counts

Copy all to feature/bar? [Y/n] or select by number (e.g. 1,3 or 1-3):
```

User responds:
| Input | Behaviour |
|-------|-----------|
| `Y` / Enter / `all` | Cherry-pick all listed commits |
| `n` / `N` | Abort — no-op |
| `1,3` | Cherry-pick commits #1 and #3 only |
| `1-3` | Cherry-pick commits #1 through #3 |
| `1,2,3` | Same as all |
| `3-5` | Cherry-pick commits #3 through #5 |

### Git expansion

```
#cherry feature/bar
  → commits = $(git cherry origin/feature/gps-tweak)   # find unpushed commits
  → print numbered list with one-liners
  → prompt user
  → git checkout feature/bar
  → git cherry-pick <selected-commits>
  → (optional, if source ≠ target) git checkout <source-branch>
  → print "Copied N commit(s) to feature/bar"
```

### Safety

- Does NOT delete/reset source branch — user does that manually with `git reset --hard HEAD~N`
- Conflicts pause for user resolution (standard git cherry-pick behaviour)
- If `origin/[current-branch]` doesn't exist yet (never pushed), fall back to `origin/develop` as baseline

### Edge cases

| Scenario | Behaviour |
|----------|-----------|
| No unpushed commits | Print "No unpushed commits found" — no-op |
| Target doesn't exist locally | Print branches and ask for correct name |
| Only 1 commit found | Same interactive flow (shows 1 item, user confirms Y/n) |
| `origin/[current-branch]` missing | Fall back to `origin/develop` baseline; note it in output |
