<!-- scope: reference -->
## #track

Create a new tracked feature.

  [name]          Fuzzy-resolve name against existing features. If no match:
                  1. Create xTrack/name/ directory and xTrack/name/FEAT_DSC_name.md with
                     YAML front-matter (name, status: active, created/modified = now)
                  2. Append a keyword-to-path row to the Routing Map in GLOBAL_CONTEXT.md
                  3. Add a row to the ## Feature Summaries table with a derived one_liner
                  If input is a descriptive phrase rather than PascalCase, derive
                  a clean feature name and confirm before creating.

  Opening prints the command delta: the newest (max 3) WorkflowImprovement ## Implemented
  entries that add or change a command — once per open, under one plain label, silent
  when empty.
