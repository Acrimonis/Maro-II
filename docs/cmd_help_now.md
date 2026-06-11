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
