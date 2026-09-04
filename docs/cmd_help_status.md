<!-- scope: reference -->
## #status

Show a detailed single-feature dashboard, or diff since last bake.

  [no param]      Full dashboard of the active feature (top Focus History entry): front-matter,
                  one-liner (from the Feature Summaries table), status/dates, plus the feature's
                  sections with completion status and all parent-level todos, rules, and docs.
                  If all sections, todos, and rules are complete, appends:
                  "All clear. #bake to snapshot."

  [name]          Fuzzy-resolve name against existing features and show the same
                  detailed dashboard for the named feature.

  diff [name]     Show what changed since the last `#bake` of a feature. Read the
                  feature's xTrack/[Feature]/FEAT_HYD_[Feature].md hydration file as baseline, compare with
                  current file and report: sections toggled (`[ ]` ↔ `[x]`), new/removed
                  todos/rules/key-files/docs, changed front-matter fields (status, dates).
                  If [name] is omitted, diff the active feature.
                  If never baked: "No hydration snapshot found — run #bake first."
