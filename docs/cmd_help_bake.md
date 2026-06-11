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
