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
