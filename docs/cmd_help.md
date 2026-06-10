View:     #now                 current context: active feature + subs list + working path [alias #context #here #feat #feature]
          #list                compact feature table (from GLOBAL_CONTEXT.md summaries) [alias #features]
          #status              feature details (active, subfeature-scoped when focused)
          #status [name]       feature details (named)
          #status diff [name]  changes since last #bake (active/named)
Manage:   #track [name]        create feature (xTrack/[name]/FEAT_DSC_, YAML front-matter + summaries row)
          #focus [name]        switch to feature
          #focus sub [name]    drill into subfeature
          #focus out           exit subfeature → parent
          #sub [name]          add subfeature (active)
          #sub                 list subfeatures (active←focused)
Track:    #todo                list todos (scope-aware)
          #todo [desc]         add todo (scope-aware)
          #todo [tgt]:[desc]   add todo (tgt: feature|parent|global)
          #rule                list rules (scope-aware)
          #rule [desc]         add rule (scope-aware)
          #rule [tgt]:[desc]   add rule (tgt: feature|parent|global)
Docs:     #doc                 feature docs (scope-aware)
          #doc list            list docs grouped by source (DOC / PLN / docs/)
          #doc create [name]   create doc (prompts: feature-scoped → xTrack/*/FEAT_DOC_ or cross-cutting → docs/)
          #doc read [name]     load doc into context (scans xTrack/*/FEAT_DOC_/xTrack/*/FEAT_PLN_/docs/)
          #doc attach [name]   link doc → feature ## Docs (bare = prompt)
          #doc detach [name]   unlink doc (bare = prompt)
          #doc sync [name]     generate/update xTrack/[name]/FEAT_DOC_[name]_profile.md from feature + summaries
          #doc audit           check docs for missing scope tags, orphans, invalid scopes
Session:  #bake                snapshot session (updates summaries table + xTrack/[Feature]/FEAT_HYD_)
          #help                this list
Git:      #new [branch]        fetch develop, create + switch to new branch from remote develop
          #commit              run #bake + git add -A && git commit
          #push                push current branch to remote
          #move [branch]       stash uncommitted changes, switch to existing branch, pop stash
          #move new [branch]   stash uncommitted changes, create new branch from develop, pop stash
          #cherry [target]     interactive: list unpushed commits, select which to copy to [target]
          #copy [target]       alias for #cherry
          #rename [branch]     rename current branch to [branch]
          #merge               rebase current branch onto origin/develop + force-push (PR-ready)
          #merge [branch]      rebase current branch onto [branch] + force-push
Health:   #doctor              lint xTrack for drift
          #doctor fix          auto-repair

## #now

Display lightweight orientation or a compact feature dashboard.

  [no param]         Show: active feature name + status + one-liner (from the Feature Summaries
                     table in GLOBAL_CONTEXT.md), active subfeature (or "none"), current working
                     directory, Last Bake timestamp.
                     Then list all subfeatures of the active feature, marking the focused
                     subfeature with ← focused.
                     Does NOT fetch git branch or worktree info.

  list               Print a table of all features from the ## Feature Summaries table in
                     GLOBAL_CONTEXT.md: name, one-liner, created, modified (with time), status.
                     Sorted by modified descending (newest first). Active feature highlighted
                     (bold + ← active). No per-file reads needed.
                     Alias: #features

## #status

Show a detailed single-feature dashboard, or diff since last bake.

  [no param]      Full dashboard of the active feature: front-matter, one-liner (from the
                  Feature Summaries table), status/dates.
                  If a subfeature is focused, read only that subfeature's section (### header +
                  nested todos/rules/key-files/docs) — omit the rest of the checklist to save
                  tokens.
                  If no subfeature is focused, print the full subfeature checklist with
                  completion status plus all parent-level todos, rules, and docs.
                  If all subfeatures, todos, and rules are complete, appends:
                  "All clear. #bake to snapshot."

  [name]          Fuzzy-resolve name against existing features and show the same
                  detailed dashboard for the named feature.

  diff [name]     Show what changed since the last `#bake` of a feature. Read the
                  feature's xTrack/[Feature]/FEAT_HYD_[Feature].md hydration file as baseline, compare with
                  current file and report: subfeatures toggled (`[ ]` ↔ `[x]`), new/removed
                  todos/rules/key-files/docs, changed front-matter fields (status, dates).
                  If [name] is omitted, diff the active feature.
                  If never baked: "No hydration snapshot found — run #bake first."

## #track

Create a new tracked feature.

  [name]          Fuzzy-resolve name against existing features. If no match:
                  1. Create xTrack/name/ directory and xTrack/name/FEAT_DSC_name.md with
                     YAML front-matter (name, status: active, created/modified = now,
                     active_subfeature: none)
                  2. Append a keyword-to-path row to the Routing Map in GLOBAL_CONTEXT.md
                  3. Add a row to the ## Feature Summaries table with a derived one_liner
                  If input is a descriptive phrase rather than PascalCase, derive
                  a clean feature name and confirm before creating.

## #focus

Switch the active feature/subfeature for the current session.

  [no param]      List all existing features and prompt to pick one.

  [name]          Fuzzy-resolve name against existing features. Update the Active
                  Session Pointers section in GLOBAL_CONTEXT.md. Constrain all
                  subsequent operational context to that feature.

  sub [name]      Fuzzy-resolve [name] against subfeatures in the active feature.
                  Set the Active Subfeature pointer. All subsequent bare #todo,
                  #rule, #doc, and #doc attach target that subfeature's subsection.

  out             Clear the Active Subfeature pointer. Subsequent commands target
                  the parent feature level again.

## #sub

Add or list subfeatures within the active feature.

  [no param]          List all subfeatures of the active feature with completion
                      status. Mark the focused subfeature with ← focused.

  [name]              Fuzzy-resolve name against existing subfeatures. If no match,
                      insert a new H3 subfeature subsection (### name [ ]) under
                      ## Subfeatures with nested #### Todos, #### Rules, and
                      #### Key Files.

## #todo

Track todos at three scope levels.

  [no param]          List todos for the active scope (subfeature #### Todos if
                      focused, else parent feature ## Todos).

  [description]       Fuzzy-match description against existing subfeature names.
                      If matched, offer to nest the todo under that subfeature.
                      If no match, append - [ ] description to the active scope's
                      Todos list.

  [target]: [desc]    Route todo to a specific target:
                      • global → GLOBAL_CONTEXT.md ## Global Todos
                      • parent → one level up (sub→feature, feature→global)
                      • feature-name → fuzzy-resolve, write to that feature's scope
                      If target is unrecognized, ask: (a) #track it, (b) use global,
                      (c) cancel.

## #rule

Manage context rules at three scope levels.

  [no param]          List rules for the active scope (subfeature #### Rules if
                      focused, else parent feature ## Rules).

  [text]              Append text as a rule to the active scope. Auto-refine
                      wording for clarity and conciseness.

  [target]: [text]    Route rule to a specific target:
                      • global → GLOBAL_CONTEXT.md ## Global Rules
                      • parent → one level up
                      • feature-name → fuzzy-resolve, write to that feature's scope
                      If target is unrecognized, ask: (a) #track it, (b) use global,
                      (c) cancel.

## #doc

Manage documentation attached to a feature scope.

  [no param]          Display docs attached to the active scope. If a subfeature
                      is focused, shows that subfeature's #### Docs; otherwise the
                      parent feature's ## Docs. Prints each doc as relative path
                      and description.

  list                Scan docs/**/*.md (recursive), xTrack/*/FEAT_DOC_*.md, and
                      xTrack/*/FEAT_PLN_*.md. Read first ~5 lines for <!-- scope: ... -->
                      tag and first # heading.
                      Also scan every xTrack/*/FEAT_DSC_*.md for ## Docs (parent) and
                      #### Docs (subfeature) sections to determine which feature/subfeature
                      each doc is attached to.
                      Print a table grouped by source:
                      DOC (xTrack/*/FEAT_DOC_), PLN (xTrack/*/FEAT_PLN_),
                      docs/ (cross-cutting reference).
                      README.md is implicit scope: core.

  create [name]       Prompt: "Feature-scoped or cross-cutting?"
                      Feature-scoped → create xTrack/[ActiveFeature]/FEAT_DOC_[name].md
                      Cross-cutting → create docs/[name].md, prompt for scope:
                      (a) core, (b) onboarding, (c) reference, (d) archived.
                      Write <!-- scope: chosen --> as line 1, # Name as title on line 3.
                      Feature-scoped docs use <!-- scope: feature -->.

  read [name]         Fuzzy-resolve name against docs/**, xTrack/*/FEAT_DOC_*,
                      xTrack/*/FEAT_PLN_* filenames (.md optional). Load the doc
                      into AI context. Print filename, scope, line count.

  attach [name]       Fuzzy-resolve name against docs/**/*.md, xTrack/*/FEAT_DOC_*.md,
                      xTrack/*/FEAT_PLN_*.md and link it to the active feature's ## Docs
                      (or #### Docs if subfeature focused). Skip if already present.
                      Bare (no name): run #doc list then prompt which to attach.

  detach [name]       Fuzzy-resolve name and remove from the active feature's
                      ## Docs (or #### Docs).
                      Bare (no name): show current docs and prompt which to detach.

## #bake

Snapshot the current session into per-feature hydration memory.

  [no param]  1. Update subfeature checkmarks in the current feature file.
              2. Update the feature's row in the ## Feature Summaries table in
                 GLOBAL_CONTEXT.md (recompute one_liner if needed, bump modified date).
              3. Set modified in front-matter to current YYYY-MM-DD HH:mm (UTC)
                 — only if the feature was actually modified this session.
              4. Create/overwrite xTrack/[Feature]/FEAT_HYD_[Feature].md with a
                 ~200-word micro-state summary (state, target files, next step).
              5. Update Last Bake in GLOBAL_CONTEXT.md.
              6. Prompt user to clear the workspace.
              Also triggered automatically by closing phrases (done, goodbye, etc.).

## #help

Display command reference.

  [no param]  Print the full reference table from docs/cmd_help.md.

  [cmd]       Fuzzy-resolve cmd against known commands (now, list, status, track,
              focus, sub, todo, rule, doc, bake, help, doctor,
              new, commit, push, move, cherry, copy
              + sub-commands status diff / doc sync / doc audit).
              Find the matching ## <cmd> section in docs/cmd_help.md and print only
              that section as a code block.
              If no match, print the full table and suggest the closest match.

## #doctor

Lint the xTrack stack for structural drift.

  [no param]  Run all checks and print findings grouped by severity.
              Checks: routing row duplicates, duplicate rules, stale active_subfeature,
              date format, status vs completion, malformed sections,
              orphan docs, missing routing rows, Feature Summaries ↔ FEAT_DSC_ mismatch.

  fix         Auto-repair safe classes: dedupe routing rows, normalize dates,
              reset stale active_subfeature, sync Feature Summaries ↔ FEAT_DSC_ files.
              Report-only items (malformed sections, orphan docs, status/rule-dedupe
              judgment calls) are printed but not auto-fixed.
              #bake runs the auto-fix subset first.


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

## #doc sync

Generate or update a feature profile document from the feature's front-matter
and the Feature Summaries table.

  [name]          Fuzzy-resolve [name] against existing features. Create or overwrite
                  xTrack/[name]/FEAT_DOC_[name]_profile.md with: name, status, one_liner,
                  created/modified dates, subfeature completion ratio, list of attached docs.
                  Scope tag: feature.
                  If [name] is omitted, sync the active feature.

## #doc audit

Audit all documentation for structural issues.

  [no param]      Scan docs/**/*.md (recursive), xTrack/*/FEAT_DOC_*.md,
                  xTrack/*/FEAT_PLN_*.md.
                  Report:
                  (a) docs missing <!-- scope: ... --> tag
                  (b) orphan docs (attached to no feature ## Docs / #### Docs, not README.md)
                  (c) docs with invalid scope (not core|onboarding|feature|reference|archived)
                  Print findings grouped by severity. Does NOT auto-fix.
