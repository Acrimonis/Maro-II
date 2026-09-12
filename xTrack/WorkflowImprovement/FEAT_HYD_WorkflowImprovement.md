# Context Hydration — WorkflowImprovement — 2026-09-12

**Last Bake:** 2026-09-12 12:30 UTC
**Updated:** 2026-09-12 12:30 UTC — bake after the command-flow pass

## State
The command-flow design is fully shipped. The Output Contract now carries a countable unit, the containment rule, the objection bullet and the question threshold, with "minimum viable communication" retired; rule 2 scopes ambiguity to MODE LOCK and QUESTIONS, and rule 3 makes a gate name its action. §7b gained four rows — `#go`, `#review`, `#walk` with `#next`/`#prev`/`#skip`/`#done`, and `#brief`/`#full` — each with a page and a `cmd_help.md` line, plus the `## Walk` template, the §7a/C12 single statement and `#archive`'s fourth gate; `#doctor` stays a–r. The first real `#archive` run retired six candidates as seven files into `xxArchive/`, each with an appended `## Outcome`, an `INDEX.md` row, five `## Docs` detachments and five pointer drops, with checks q–r verified clean. The walk closed with one point still open — the push. Two candidates (`docs-integrity`, `archive-lifecycle`) are held for a second gate, and the whole pass is uncommitted on `feature/wrKFl`.

## Target Files
- `AGENTS.md` — Output Contract rewrite, rules 2–3, §7a keep-criterion, §7b rows for `#go` / `#review` / `#walk` / `#brief`
- `docs/cmd_help_go.md`, `docs/cmd_help_review.md`, `docs/cmd_help_walk.md`, `docs/cmd_help_brief.md` — the four new pages
- `docs/cmd_help.md`, `docs/cmd_help_bake.md`, `docs/cmd_help_archive.md`, `docs/cmd_help_implement.md`, `.claude/skills/xtrack/references/templates.md` — mirrored surfaces
- `xTrack/WorkflowImprovement/xxArchive/INDEX.md` — seven rows from the first pass
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_command-flow.md` — the shipped design

## Next Step
Both retirement gates have run — nine plans in `xxArchive/` with index rows, seventeen left in the feature folder. Next is a `#bake` to fold the two newly retired entries and refresh this state; pushing `feature/wrKFl` is user-owned, never proposed and never reminded. Still deferred: git-shortcut verification, the post-merge `xTrack/` reconcile, the ~198-plan triage and the `#implement` build-only-when-source-changed refinement.
