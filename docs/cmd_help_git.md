<!-- scope: reference -->
## Git — workflow shortcuts

Convenience wrappers over standard git. **🛑 The rules are single-sourced in `AGENTS.md` — `GIT WRITES AND DEPLOYS ARE THE USER'S CALL` and `PROTECTED BRANCHES`; this page keeps the command detail, and [`docs/GIT_WORKFLOW.md`](GIT_WORKFLOW.md) the branch model.**

A git `#`-command **is** its own go-ahead: the invocation authorises the operation, so the agent executes it — never re-confirmed as a permission question, never handed back for the user to run. `#commit` / `#push` / `#merge` / `#cherry` confirm only the action's scope; `#new` / `#move` / `#move new` / `#rename` ask nothing, save `#new`'s one question when the branch already exists and `#rename`'s one before it deletes a published old name.

  #new [branch_name]       fetch `origin/develop`, checkout `--no-track -b feature/[branch_name]`
                      from it — the new branch carries no upstream, so a bare `git push` refuses with
                      git's own suggestion (`git push --set-upstream origin feature/[branch_name]`)
                      until `#push` writes it. If `feature/[branch_name]` exists locally, name it with
                      the commits it holds beyond `origin/develop` — `git rev-list --count origin/develop..feature/[branch_name]`,
                      reported beside the published flag so a branch already merged reads as "the
                      copies are elsewhere" rather than as "nothing to lose" — and whether it is
                      published, then offer:
                        1. recreate from origin/develop   (default) — `checkout --no-track -B` from
                           origin/develop, then clear whatever upstream it carried (`git branch
                           --unset-upstream`; exit 128 with a `fatal:` prefix is what "nothing to
                           clear" looks like). The flag is mandatory and its order matters: `-B
                           --no-track` reads the flag as the branch name, and a flagless `-B`
                           re-points the upstream at origin/develop — the very tracking this command
                           exists to remove. `--no-track` itself neither re-points nor clears what
                           the branch already carried, and git's closing line names that surviving
                           upstream, so the transcript can read as a success while the branch still
                           points at the base it was cut from (all measured 2026-09-21).
                        2. a different branch name
                        3. abort — nothing changes
                      The recreate runs only on the answer; an invoked `#new` otherwise asks nothing.
                      A dirty working tree is reported before the offer and never stashed — a
                      force-create checkout carries the modifications across, measured 2026-09-19 on
                      the flagless form and again 2026-09-21 on the `--no-track -B` form.
  #commit             git add -A && git commit. Offers a bake first when the active feature's
                      state moved since its last bake. Confirms the staged set and the message
                      — a scope gate, never a permission one. 🚫 refuses on develop/main.
  #push               git push -u origin [current-branch] — the `-u` is what writes the upstream to
                      the branch's own name, and the refusal a bare push meets is stated under `#new`.
                      User-invoked only — the agent never proposes or reminds. Confirms the branch
                      and the remote — a scope gate, never a permission one. 🚫 refuses on develop/main.
  #move [branch_name]      stash → switch (existing) → pop. Bare = list local branches, newest first,
                      pick one.
  #move new [branch_name]  stash → create 'feature/[branch_name]' from origin/develop with
                      `--no-track` → pop. Bare = prompt for the name, prefilled 'feature/'.
                      An existing branch is not handled the way `#new` does it — this entry fails
                      there, the known sibling left unfixed by the `#new` branch-recreate change.
  #cherry [target]    list unpushed commits, interactive pick to cherry-pick to [target].
                      Asks for confirmation.
  #copy [target]      alias for #cherry.
  #rename [topic]     rename the current branch to `feature/[topic]` — `feature/` added when omitted —
                      via `git branch -m`, no stash: a ref move touches neither index nor worktree.
                      Then the upstream: one that does not name the new branch is cleared
                      (`git branch --unset-upstream`; exit 128 with a `fatal:` prefix means there was
                      nothing to clear); and when the old name is published — `git ls-remote --heads
                      origin feature/[old]` exits 0 whether or not the branch exists, so the test is
                      whether its output is non-empty (measured 2026-09-21) — the rename is carried to the
                      origin — `git push -u origin feature/[new]`, then `git push origin --delete
                      feature/[old]`, then `git fetch --prune origin` — the deletion only on an
                      answer, the question naming that it closes any open pull request on that
                      branch.
                      Bare = report the current branch and the topic it implies; writes nothing.
                      Refuses 🚫 develop/main as the current branch, a target equal to the current
                      name, a target that exists locally (the commits it holds are reported, with no
                      recreate offered — a rename has nothing to restore) or on origin, and a
                      detached HEAD.
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

  🛑 NEVER writes to develop/main — PR handles integration.
  🛑 No auto-push unless branch was already on remote before #merge.
