<!-- scope: reference -->
## #focus

Switch the active feature for the current session.

  [no param]      List all existing features and prompt to pick one.

  [name]          Fuzzy-resolve name against existing features. Push a new entry onto
                  the ## Focus History stack in GLOBAL_CONTEXT.md (newest-first, cap 10).
                  Constrain all subsequent operational context to that feature.

  [name] [section]  After resolving the feature, hydrate only that ### [section] block
                  (transient — no persistent state stored).

  Retired: #focus sub / #focus out (subfeatures removed). Sections are addressed
  directly via [name] [section] or #todo/#rule [section]:...
