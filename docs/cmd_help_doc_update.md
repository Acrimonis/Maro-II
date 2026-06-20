## #doc update

Refresh the active feature's documentation to match current implementation reality.

### What it does (9-step pipeline)

1. **Harvest decisions** — Scan all `FEAT_PLN_*` files + source code for the active feature. Extract every architectural/functional decision with rationale and source file references.

2. **Cross-reference** — Compare plans against actual source code. Plans often deviate during implementation; capture what was actually built, not what was planned.

3. **Create/update decisions doc** — Generate `FEAT_DOC_[Feature]_decisions.md` with decisions grouped by category (data model, recording, UI, rendering, settings, triggers, tech stack, etc.).

4. **Rewrite ## Implemented** — Concise current-state bullet groups only. No historical "simplified from" language, no before/after evolution. Just what IS now.

5. **Strip completed todos** — Remove all `#### Todos` from `[x]` subfeatures. They're redundant with the Implemented section + decisions doc.

6. **Migrate unchecked items** — Move any remaining `[ ]` items from completed subfeatures to the parent-level `## Todos`.

7. **Remove deprecated terminology** — Sweep out old state names (IDLE/RECORDING/PAUSED/FINALIZING → OFF/ON), stale class names, naming mismatches with the codebase.

8. **Strip empty sections** — Remove `#### Rules`, `#### Key Files`, `#### Docs` that have no content.

9. **Bake** — Update hydration file, bump modified date in front-matter, update GLOBAL_CONTEXT.md Last Bake pointer.

### Target
- ~60% line reduction on the feature doc
- Every claim in `## Implemented` verifiable against source code
- Every functional decision has a rationale and file reference
