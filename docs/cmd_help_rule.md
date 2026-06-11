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
