# Context Hydration — WorkflowImprovement — 2026-06-09

**Active Subfeature:** none

## State
The feat-summary-layer plan has been fully implemented across 14 steps spanning 2 phases. Phase 1 restructured the xTrack stack: FEATURE_SCOPE_ → FEAT_DSC_ rename (×15), front-matter slimming (dropped one_liner/subs_total/subs_done), Feature Summaries table in GLOBAL_CONTEXT.md, Always-Loaded Context reduced 6→3, GLOBAL_TODOS folded into GLOBAL_CONTEXT. Phase 2 migrated content: 10 docs/ → FEAT_DOC_, 25 plans/ → FEAT_PLN_, 4 hydration → FEAT_HYD_, deleted xTrack/hydration/. All ## Docs sections updated to new paths. AGENTS.md §7b, docs/cmd_help.md, SKILL.md, and templates.md all updated.

## Target Files
- `AGENTS.md` — updated §7a/7b with new taxonomy and command semantics
- `docs/cmd_help.md` — full rewrite
- `xTrack/GLOBAL_CONTEXT.md` — Feature Summaries table + Global Todos + Always-Loaded 3
- `xTrack/FEAT_DSC_*.md` — 15 renamed + slimmed front-matters
- `FEAT_DOC_*.md` — 10 migrated from docs/
- `FEAT_PLN_*.md` — 25 migrated from plans/
- `FEAT_HYD_*.md` — 4 migrated from xTrack/hydration/

## Next Step
Rename branch to feature/workflow-slimmer, commit, and push. Then prompt user to clear workspace.
