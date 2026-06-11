## Git — git workflow shortcuts

Convenience commands for common git operations within the xTrack workflow.
All commands are thin wrappers over standard git commands.

  #new [branch]       1. `git fetch origin develop`
                      2. `git checkout -b [branch] origin/develop`
                      Always branches from the latest remote develop, never from
                      the current branch. The branch name is used as-is (create
                      it with a feature/ prefix yourself).

  #commit             1. Run `#bake` to snapshot current xTrack session state.
                      2. `git add -A`
                      3. `git commit` (opens editor for commit message)
                      Ensures xTrack hydration is always committed alongside code.

  #push               `git push origin [current-branch]`
                      Pushes the current branch to remote. No safety prompt —
                      use explicit git commands for destructive operations.

  #move [branch]      Move uncommitted working-tree changes to an existing branch.
                      1. Check for uncommitted changes — no-op if clean.
                      2. Fuzzy-resolve [branch] against local branches.
                      3. If match: `git stash push`, `git checkout [branch]`,
                         `git stash pop`.
                      4. If no match: print "Branch not found — use `#move new
                         [branch]` to create it or `#new [branch]` for a fresh
                         checkout."

  #move new [branch]  Move uncommitted changes to a new branch from develop.
                      1. `git stash push`
                      2. `git fetch origin develop`
                      3. `git checkout -b [branch] origin/develop`
                      4. `git stash pop`

  #cherry [target]    Copy committed-but-unpushed commits to another branch.
  #copy [target]      Alias for #cherry.

                      Finds commits on the current branch that aren't in
                      `origin/[current-branch]` (or origin/develop if never
                      pushed), then prints a numbered list with one-line
                      summaries and asks:

                        Found 3 unpushed commits:
                          [1] abc1234 Fix typo in GpsLocationSource
                          [2] def5678 Add haversine guard to animateTo
                          [3] ghi9012 Update xTrack subfeature counts
                        Copy all to [target]? [Y/n] or select by number (1,3 or 1-3):

                      On confirm: `git checkout [target]`,
                      `git cherry-pick <selected-commits>`.

                      Does NOT delete commits from source branch —
                      use `git reset --hard HEAD~N` manually.

  #rename [branch]    Rename the current branch to [branch].
                      `git branch -m [branch]`
                      Works regardless of whether the branch has been pushed.
                      If the old name was already pushed, you'll need to push
                      the new name and delete the old remote branch manually.

  #merge              Rebase current branch onto origin/develop + force-push.
                      Makes your branch PR-ready by replaying your commits on
                      top of the latest remote develop.
                      1. `git fetch origin develop`
                      2. `git rebase origin/develop`
                      3. On conflict: prompt D/F/M
                         [D]evelop is the reference → `git rebase --skip`
                         [F]eature is the reference → `git rebase --continue`
                         [M]anual → `git rebase --abort`
                      4. `git push --force-with-lease`

  #merge [branch]     Same, but rebase onto a specific branch instead of develop.
                      `git fetch origin [branch]` then `git rebase origin/[branch]`.
