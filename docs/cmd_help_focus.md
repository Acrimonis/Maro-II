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
