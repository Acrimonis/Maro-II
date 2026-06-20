## #doc

Manage documentation attached to a feature scope.

  [no param]          Display docs attached to the active scope. If a subfeature
                      is focused, shows that subfeature's #### Docs; otherwise the
                      parent feature's ## Docs. Prints each doc as relative path
                      and description.

  list                Scan docs/**/*.md (recursive), xTrack/*/FEAT_DOC_*.md, and
                      xTrack/*/FEAT_PLN_*.md. Read first ~5 lines for <!-- scope: ... -->
                      tag and first # heading.
                      Also scan every xTrack/*/FEAT_DSC_*.md for ## Docs (parent) and
                      #### Docs (subfeature) sections to determine which feature/subfeature
                      each doc is attached to.
                      Print a table grouped by source:
                      DOC (xTrack/*/FEAT_DOC_), PLN (xTrack/*/FEAT_PLN_),
                      docs/ (cross-cutting reference).
                      README.md is implicit scope: core.

  create [name]       Prompt: "Feature-scoped or cross-cutting?"
                      Feature-scoped → create xTrack/[ActiveFeature]/FEAT_DOC_[name].md
                      Cross-cutting → create docs/[name].md, prompt for scope:
                      (a) core, (b) onboarding, (c) reference, (d) archived.
                      Write <!-- scope: chosen --> as line 1, # Name as title on line 3.
                      Feature-scoped docs use <!-- scope: feature -->.

  read [name]         Fuzzy-resolve name against docs/**, xTrack/*/FEAT_DOC_*,
                      xTrack/*/FEAT_PLN_* filenames (.md optional). Load the doc
                      into AI context. Print filename, scope, line count.

  attach [name]       Fuzzy-resolve name against docs/**/*.md, xTrack/*/FEAT_DOC_*.md,
                      xTrack/*/FEAT_PLN_*.md and link it to the active feature's ## Docs
                      (or #### Docs if subfeature focused). Skip if already present.
                      Bare (no name): run #doc list then prompt which to attach.

  detach [name]       Fuzzy-resolve name and remove from the active feature's
                      ## Docs (or #### Docs).
                      Bare (no name): show current docs and prompt which to detach.

  update              Refresh the active feature's documentation to reflect current
                      implementation state. Runs a multi-step cleanup:
                      1. Scan all FEAT_PLN_* files + source code, extract functional
                         decisions with rationale, group by category
                      2. Cross-reference plans vs actual source — plans often deviate
                      3. Create/update FEAT_DOC_[Feature]_decisions.md capturing every
                         architectural/functional decision with source file references
                      4. Rewrite ## Implemented as concise current-state bullet groups —
                         no historical "simplified from" language, just what IS
                      5. Remove completed [x] todo lists from subfeatures (redundant
                         with Implemented + decisions doc)
                      6. Move unchecked items from completed subfeatures to parent ## Todos
                      7. Strip all deprecated terminology (old state names, stale naming)
                      8. Remove empty #### Rules, #### Key Files, #### Docs sections
                      9. Update hydration file, bump modified date, #bake
                      Target: ~60% line reduction on the feature doc.
