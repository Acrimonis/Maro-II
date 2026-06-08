View:     #features            compact feature table (front-matter, by modified) [alias #list]
          #feature             current context: active feature + subs list + working path
          #status              feature details (active)
          #status [name]       feature details (named)
Manage:   #track [name]        create feature (with YAML front-matter)
          #focus [name]        switch to feature
          #sub [name]          add subfeature (active)
          #sub                 list subfeatures (active←focused)
          #sub focus [name]    drill into subfeature
          #sub out             exit subfeature → parent
Track:    #todo                list todos (scope-aware)
          #todo [desc]         add todo (scope-aware)
          #todo [tgt]:[desc]   add todo (tgt: feature|parent|global)
          #rule                list rules (scope-aware)
          #rule [desc]         add rule (scope-aware)
          #rule [tgt]:[desc]   add rule (tgt: feature|parent|global)
Docs:     #doc                 feature docs (scope-aware)
          #doc list            list docs with scope tags (recursive docs/**)
          #doc create [name]   create doc + scope prompt (core|onboarding|feature|reference|archived)
          #doc read [name]     load doc into context
          #doc attach [name]   link doc → feature ## Docs (bare = prompt)
          #doc detach [name]   unlink doc (bare = prompt)
Session:  #bake                snapshot session (per-feature hydration)
          #help                this list
Health:   #doctor              lint xTrack for drift (#doctor fix = auto-repair)

## #features

Show a compact dashboard of all tracked features, reading only the YAML front-matter
from each FEATURE_SCOPE_*.md.

  [no param]  Print a table: feature name, one-liner, created, modified (with time),
              subfeature ratio, status. Sorted by modified descending (newest first).
              Active feature row is highlighted (bold + ← active marker).
              Does NOT expand subfeature checklists — use #status for that.

  Alias: #list

## #feature

Print a lightweight orientation block for the active feature.

  [no param]  Show: active feature name + status + one-liner, active subfeature
              (or "none"), current working directory, Last Bake timestamp.
              Then list all subfeatures of the active feature, marking the focused
              subfeature with ← focused.
              Does NOT fetch git branch or worktree info.

## #status

Show a detailed single-feature dashboard.

  [no param]      Full dashboard of the active feature: front-matter, all subfeature
                  checklists with completion status, parent-level todos, rules, and
                  docs. If a subfeature is focused, expand that subfeature's nested
                  todos/rules/key-files/docs.
                  If all subfeatures, todos, and rules are complete, appends:
                  "All clear. #bake to snapshot."

  [name]          Fuzzy-resolve name against existing features and show the same
                  detailed dashboard for the named feature.

## #track

Create a new tracked feature.

  [name]          Fuzzy-resolve name against existing features. If no match:
                  1. Create xTrack/FEATURE_SCOPE_name.md with YAML front-matter
                     (name, status: active, created/modified = now, active_subfeature:
                     none, subs_total: 0, subs_done: 0, one_liner)
                  2. Derive one_liner from the description
                  3. Append a keyword-to-path row to the Routing Map in
                     GLOBAL_CONTEXT.md
                  If input is a descriptive phrase rather than PascalCase, derive
                  a clean feature name and confirm before creating.

## #focus

Switch the active feature for the current session.

  [no param]      List all existing features and prompt to pick one.

  [name]          Fuzzy-resolve name against existing features. Update the Active
                  Session Pointers section in GLOBAL_CONTEXT.md. Constrain all
                  subsequent operational context to that feature.

## #sub

Manage subfeatures within the active feature.

  [no param]          List all subfeatures of the active feature with completion
                      status. Mark the focused subfeature with ← focused.

  [name]              Fuzzy-resolve name against existing subfeatures. If no match,
                      insert a new H3 subfeature subsection (### name [ ]) under
                      ## Subfeatures with nested #### Todos, #### Rules, and
                      #### Key Files.

  focus [name]        Fuzzy-resolve name against existing subfeatures. Set the
                      Active Subfeature pointer. All subsequent bare #todo, #rule,
                      #doc, and #doc attach target this subfeature's subsection.

  out                 Clear the Active Subfeature pointer. Subsequent commands
                      target the parent feature level again.

## #todo

Track todos at three scope levels.

  [no param]          List todos for the active scope (subfeature #### Todos if
                      focused, else parent feature ## Todos).

  [description]       Fuzzy-match description against existing subfeature names.
                      If matched, offer to nest the todo under that subfeature.
                      If no match, append - [ ] description to the active scope's
                      Todos list.

  [target]: [desc]    Route todo to a specific target:
                      • global → xTrack/GLOBAL_TODOS.md
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

  list                Scan docs/**/*.md recursively. Read the first ~5 lines for
                      the <!-- scope: ... --> tag and the first # heading. Also
                      scan every FEATURE_SCOPE_*.md for ## Docs (parent) and
                      #### Docs (subfeature) sections to determine which
                      feature/subfeature each doc is attached to.
                      Print a table grouped by scope:
                      relative path → scope → heading → attached to
                      (feature/subfeature name, or "—" if unattached).
                      README.md is implicit scope: core.

  create [name]       Create docs/name.md. Prompt for scope tag:
                      (a) core, (b) onboarding, (c) feature, (d) reference,
                      (e) archived. Write <!-- scope: chosen --> as line 1 and
                      # Name as the title on line 3.

  read [name]         Fuzzy-resolve name against docs/** filenames (.md optional).
                      Load the doc into AI context. Print filename, scope, line count.

  attach [name]       Fuzzy-resolve name against docs/**/*.md and link it to the
                      active feature's ## Docs (or #### Docs if subfeature focused).
                      Skip if already present.
                      Bare (no name): run #doc list then prompt which to attach.

  detach [name]       Fuzzy-resolve name and remove from the active feature's
                      ## Docs (or #### Docs).
                      Bare (no name): show current docs and prompt which to detach.

## #bake

Snapshot the current session into per-feature hydration memory.

  [no param]  1. Update subfeature checkmarks in the current feature file.
              2. Recompute subs_total/subs_done from checkboxes in front-matter.
              3. Set modified to current YYYY-MM-DD HH:mm (UTC).
              4. Create/overwrite xTrack/hydration/CONTEXT_HYDRATION_[Feature].md
                 with a ~200-word micro-state summary (state, target files, next step).
              5. Update Last Bake in GLOBAL_CONTEXT.md.
              6. Prompt user to clear the workspace.
              Also triggered automatically by closing phrases (done, goodbye, etc.).

## #help

Display command reference.

  [no param]  Print the full reference table from docs/cmd_help.md.

  [cmd]       Fuzzy-resolve cmd against known commands (features, feature, status,
              track, focus, sub, todo, rule, doc, bake, help, doctor + alias list).
              Find the matching ## <cmd> section in docs/cmd_help.md and print only
              that section as a code block.
              If no match, print the full table and suggest the closest match.

## #doctor

Lint the xTrack stack for structural drift.

  [no param]  Run all checks and print findings grouped by severity.
              Checks: routing row duplicates, duplicate rules, stale active_subfeature,
              date format, subs count mismatch, status vs completion, malformed sections,
              orphan docs, missing routing rows.

  fix         Auto-repair safe classes: dedupe routing rows, normalize dates,
              recompute subs counts, reset stale active_subfeature.
              Report-only items (malformed sections, orphan docs, status/rule-dedupe
              judgment calls) are printed but not auto-fixed.
              #bake runs the auto-fix subset first.
