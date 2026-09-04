<!-- scope: reference -->
## #todo

Track todos at three scope levels.

  [no param]          List todos for the active scope (section #### Todos if a section
                      is targeted, else parent feature ## Todos).

  [description]       Fuzzy-match description against existing section names.
                      If matched, offer to nest the todo under that section.
                      If no match, append - [ ] description to the active scope's
                      Todos list.

  [target]: [desc]    Route todo to a specific target:
                      • global → GLOBAL_CONTEXT.md ## Global Todos
                      • parent → one level up (section→feature, feature→global)
                      • feature-name → fuzzy-resolve, write to that feature's scope
                      • section-name → write to that ### section's #### Todos
                      If target is unrecognized, ask: (a) #track it, (b) use global,
                      (c) cancel.
