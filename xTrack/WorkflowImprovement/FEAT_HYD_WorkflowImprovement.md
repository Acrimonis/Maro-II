# Context Hydration — WorkflowImprovement — 2026-09-12

**Last Bake:** 2026-09-12 09:36 UTC

## State
The docs & rulebook integrity pass is COMPLETE on `feature/wrKFl` (baseline commit `2b74f09`; this pass commits immediately after this bake). The command registry is single-sourced: AGENTS.md §7b is normative, `docs/cmd_help.md` is stamped *derived*, `docs/GIT_WORKFLOW.md` is demoted to git *detail* with the `## OwnedFiles` mechanism retired. `#checkout` is de-registered and `#list` finally has a page; `#bake` is explicit-only and `#commit` offers a stale bake (a `Last Bake` stamp joined the FEAT_HYD template); the git prompt policy is tiered. `plans/` and `docs/oZer/` are gone — 8 renames (3 plan re-homes including the new **Tasker** feature, 5 BARO research re-homes), 2 deletions, ~24 pointer repairs. README is pointer-only with GDAL moved into SETUP; MARO_ARCHITECTURE plus seven previously-unread docs were audited; `#doctor` now defines checks a–p. **No build was run** — no `.kt` file changed. The Ask review verified 19 of 20 ledger decisions; its three findings were fixed.

## Target Files
- `AGENTS.md` — §7b registry, Output Contract, Code Practice, tiered git policy, checks a–p
- `docs/cmd_help_doctor.md` — the 16 check definitions
- `xTrack/GLOBAL_CONTEXT.md` — Focus History, Tasker routing/summary rows, `plans` keyword dropped
- `xTrack/WorkflowImprovement/260912_FEAT_PLN_WorkflowImprovement_docs-integrity.md` — plan of record, Annex A text, Annex B design

## Next Step
Implement `#archive` + the `xxArchive/` tier from Annex B as its own pass, then triage the ~198 `FEAT_PLN_*` backlog that retirement introduces.
