# Context Hydration — WorkflowImprovement — 2026-09-19

**Last Bake:** 2026-09-19 08:34 UTC

**Directive trace:** no covered action stopped since the last bake: no dependency was added, no machine-shaped data file opened, no device touched and no work started unordered, the plan file having been written on an explicit "plan this"; the one gap recorded is a claim about list ordering taken from the rulebook already in context while the file it pointed at stayed unopened, named by the session's own review sweep.

## State

The `#archive` command now has a designed extension — `#archive sweep` — which enumerates a feature's plans together with their associated docs, prints a description, relevance and status per row, recommends an action, and disposes of each row on the user's word by leave, follow-up or archive. The plan carries decisions D1–D13, separate state sets for plans and docs, and a stateless design whose pending set is re-derived from disk plus a session decision log rather than remembered. Nothing is built: no command row, no page and no command form has been written. The branch `feature/archive` sits on `origin/develop` at `4c74e6c` with nothing committed yet, and one walk level is open over the two remaining functional points, which blocks both the bake fold and any retirement in this feature.

## Target Files

- `xTrack/WorkflowImprovement/260919_FEAT_PLN_WorkflowImprovement_archive-sweep.md` — the plan in design
- `AGENTS.md` — §7b `#archive` row: the sweep forms and the reserved word
- `docs/cmd_help_archive.md`, `docs/cmd_help.md`, `docs/cmd_help_bake.md`, `docs/cmd_help_doctor.md`
- `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` — the section, `## Docs`, then `## Implemented`

## Next Step

Close walk item 2, whether the bake report tells the user to run the sweep, and item 3, the index word for a plan closed without shipping; then implement Stage B in the plan's §7 order, beginning with the §7b row.
