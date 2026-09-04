<!-- scope: reference -->
## #bake

Snapshot + consolidation into per-feature hydration memory.

  [no param]  1. Update section checkmarks in the current feature file.
              2. Consolidate sections (criteria C1–C12 below): fold-done, trim-empty,
                 split, merge, rename-normalize.
              3. Update the feature's row in the ## Feature Summaries table in
                 GLOBAL_CONTEXT.md (recompute one_liner if needed, bump modified date).
              4. Set modified in front-matter to current YYYY-MM-DD HH:mm (UTC)
                 — only if the feature was actually modified this session.
              5. Create/overwrite xTrack/[Feature]/FEAT_HYD_[Feature].md with a
                 ~200-word micro-state summary (state, target files, next step).
              6. Prune the ## Focus History stack in GLOBAL_CONTEXT.md beyond cap 10.
              7. Prompt user to clear the workspace.
              Also triggered automatically by closing phrases (done, goodbye, etc.).

  Section criteria (canonical):
  C1 Cohesion — one section = one bounded theme.
  C2 Threshold — ≥2 items or multi-turn theme.
  C3 Emergent — no create command; formed from #todo/#rule targeting.
  C4 Stable heading — ### [noun-phrase], routing vocabulary.
  C5 Targeting — #todo/#rule [Section]:...; #focus [Feature] [Section].
  C6 No state — no active_subfeature, #sub, or #focus sub/out.
  C7 Optional slots — keep only populated #### Todos/Rules/Key Files/Docs.
  C8 Fold-done — done sections fold into ## Implemented.
  C9 Trim-empty — empty sections fold too.
  C10 Split — >8 items or two themes.
  C11 Merge — duplicate headings.
  C12 Keep-if-signal — survives only with open todo / retained rule / doc-key-file mapping.
