## Git — workflow shortcuts

Convenience wrappers over standard git. **🔴 See [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) for the Hard Rule — `#merge`/`#push`/`#commit` refuse on `develop`/`main`.**

  #new [branch]       fetch `origin/develop`, checkout `-b [branch]` tracking it.
  #commit             #bake + git add -A && git commit. ALWAYS prompts for confirmation
                      — even when chained. 🚫 refuses on develop/main.
  #push               git push origin [current-branch]. 🚫 refuses on develop/main.
  #move [branch]      stash → switch (existing) → pop.
  #move new [branch]  stash → create from origin/develop → pop.
  #cherry [target]    list unpushed commits, interactive pick to cherry-pick to [target].
  #copy [target]      alias for #cherry.
  #rename [branch]    git branch -m [branch].
  #merge              Smart sync from origin/develop into current feature branch:

  ## Pre-Flight (automatic)
  1. `git fetch origin develop`
  2. Uncommitted changes? → stash/pop wrapper
  3. Commit count: `git rev-list --count origin/develop..HEAD`
  4. File overlap: `git diff --name-only HEAD...origin/develop`
  5. Push status: is branch on `origin`?
  6. Overlap classification: trivial (docs/config only) vs non-trivial (source/build/xTrack)

  ## Classification
  | Overlap | Verdict |
  |---------|---------|
  | Zero overlap or docs/config only | **TRIVIAL** |
  | Source (.kt), build (gradle/toml), AGENTS.md, GLOBAL_CONTEXT.md | **NON-TRIVIAL** |
  | > 20 develop commits incoming | **NON-TRIVIAL** |

  ## Strategy Selection
  | Condition | Strategy |
  |-----------|----------|
  | Not pushed, < 10 commits ahead | Rebase |
  | Not pushed, zero overlap | Rebase |
  | Already pushed to origin | Merge (no force-push) |
  | ≥ 10 commits + overlap > 0 | Merge (single conflict pass) |
  | Docs/config only overlap | Rebase |
  | Fallback | Rebase |

  ## Confirmation
  **Trivial:** `Proceed? [yes/no]` — `yes` executes directly.
  **Non-Trivial:** Sticky issues listed → `Proceed? [yes / #implement]`
    - `yes` → execute directly, AI resolves conflicts
    - `#implement` → Code→Ask→Architect pipeline with full validation

  ## Execution (direct)
  Rebase: stash → `git rebase origin/develop` → stash pop → `push --force-with-lease` (if on remote)
  Merge:  stash → `git merge origin/develop` → stash pop → `push`

  ## Execution (#implement pipeline)
  Code: execute git + resolve conflicts → Ask: review resolution + build → Architect: report + #bake

  ## Conflict Resolution
  | File | Strategy |
  |------|----------|
  | GLOBAL_CONTEXT.md | Merge routing rows, dedupe |
  | FEAT_DSC_*.md | Keep both, sort by Modified |
  | AGENTS.md | Manual — flag for review |
  | Build files | Accept incoming versions, keep local deps |
  | Source .kt | Feature-owned → feature wins; shared → merge attempt |

  🔴 NEVER writes to develop/main — PR handles integration.
  🔴 No auto-push unless branch was already on remote before #merge.
