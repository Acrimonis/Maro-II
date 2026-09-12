# Context Hydration — WorkflowImprovement — 2026-09-12

**Last Bake:** 2026-09-12 11:14 UTC

## State
The docs & rulebook integrity pass is COMPLETE and COMMITTED (`5558f3b`) on `feature/wrKFl`. The archive lifecycle + `#archive` pass is IMPLEMENTED but UNCOMMITTED — `AGENTS.md` §7a xxArchive rule and §7b row, `docs/cmd_help_archive.md`, `#doctor` checks q–r plus the retirement nudge, `templates.md` INDEX schema, `#bake` retirement candidates — and still owes the Architect report and a commit. The command-flow design was captured in its own plan, reviewed by Ask, and its seven findings walked to closure: six accepted (single-statement §7a fold rule with bake C12 as a pointer; challenge clause scoped to recommendations with MODE LOCK untouched; objection sentence on the contract's reporting bullet; a gate must name the action it authorises; an open walk is a fourth `#archive` gate with close / resume / park, so it blocks both `#bake`'s fold and retirement; the output mode is session-lived, reset by `#focus`) and one no-action. Those six resolutions are NOT yet written into the plan, whose status line still reads "awaiting Ask review". No build was run — no `.kt` file changed in either pass.

## Target Files
- `AGENTS.md` — Core Directives (contract, challenge, gate), §7a xxArchive + fold rule, §7b `#archive` row, checks a–r
- `docs/cmd_help_archive.md`, `docs/cmd_help_doctor.md` — archive spec and the a–r check set
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_command-flow.md` — design awaiting the six amendments
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_archive-lifecycle.md` — digest floor + the open-walk gate sentence

## Next Step
Write the six accepted resolutions into the command-flow plan and the walk gate into the archive plan (md-only), then close the archive pipeline with the Architect report and commit the archive pass. Only after that commit: ship the rules, then `#go`, `#review` with its guard lines, `#walk`, and `#brief`. Deferred: the ~198-plan backlog triage and the `#implement` "build when source changed" refinement.
