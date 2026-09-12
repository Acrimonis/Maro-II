# Context Hydration — WorkflowImprovement — 2026-09-12

**Last Bake:** 2026-09-12 11:30 UTC

## State
Three change sets sit on `feature/wrKFl`: the docs & rulebook integrity pass (`5558f3b`, committed), the archive lifecycle + `#archive` pass together with both plan files (`cd2f635`, committed), and — uncommitted right now — the **command delta on feature open**: `AGENTS.md` §7a states it once, the `#focus` and `#track` rows point at it, both pages and the two `cmd_help.md` lines mirror it, and the WorkflowImprovement `## Implemented` head carries its fold line; the Ask hop approved with no fixes and no build ran (docs-only). The command-flow design is fully walked — seven findings, six accepted resolutions applied to its plan, including the §7a/C12 single statement, the gate rule, the `## Walk` gates and the session-lived output mode. Nothing from it is implemented: three Core Directive texts plus `#go`, `#review`, `#walk`, `#brief`/`#full` remain unshipped, and the archive plan carries one design-only amendment (an unresolved walk as `#archive`'s fourth gate).

## Target Files
- `AGENTS.md` — new §7a command-delta bullet and the two §7b row pointers; the `xxArchive/` rule and `#doctor` a–r from the previous pass
- `docs/cmd_help_focus.md`, `docs/cmd_help_track.md`, `docs/cmd_help.md` — the delta's mirrored surfaces
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_command-flow.md` — the unshipped design; Part 5 holds the ship order
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_archive-lifecycle.md` — digest floor plus the fourth-gate amendment

## Next Step
Commit the command-delta change. Then implement the command-flow rules in ship order — the three Core Directive texts first, then `#go`, `#review` with its two guard lines, `#walk` with the `## Walk` template, and `#brief` — each as its own pass. Deferred: the retirement candidates this bake reported, the ~198-plan backlog triage, and the `#implement` build-only-when-source-changed refinement.
