# Context Hydration — WorkflowImprovement

**Last Bake:** 2026-06-08 09:22 UTC

**State:**
- All 20 subfeatures done (19/19 → 20/20). The `#doc list attachment column` subfeature is now `[x]`. New subfeature "xTrack system review — #doc sync, #doc audit, #diff, spec consolidation" added and completed.
- Three new commands spec'd: `#doc sync` (generate feature profile doc from front-matter), `#doc audit` (scan docs for structural issues), `#diff` (show changes since last bake). All in AGENTS.md §7b + cmd_help.md.
- Spec consolidation: commands.md deprecated, AGENTS.md §7b is sole canonical source. Always-Loaded Context section added to GLOBAL_CONTEXT.md + templates.md.
- 5 feature files now have `## Docs` sections (WorkflowImprovement, DepthMapping, Coastline, CodeReview, Zone300). DepthMapping has 9 docs attached.

**Target files:**
- `AGENTS.md` — §7b items 8, 11, 14 updated
- `docs/cmd_help.md` — reference table + 3 new detailed sections
- `xTrack/GLOBAL_CONTEXT.md` — active feature pivoted, Always-Loaded Context, Global Instructions
- `xTrack/FEATURE_SCOPE_WorkflowImprovement.md` — front-matter, new subfeature, ## Docs
- `xTrack/FEATURE_SCOPE_{DepthMapping,Coastline,CodeReview,Zone300}.md` — ## Docs added
- `.claude/skills/xtrack/references/commands.md` — deprecated
- `.claude/skills/xtrack/{SKILL.md,templates.md,fuzzy-resolve.md}` — updated refs
- `plans/xTrack-review-plan.md` — review analysis document

**Next step:**
- Outstanding: UiThingies malformed section (`### compact-dash` outside `## Subfeatures`), DepthMapping non-standard `[~]` marker.
- Parent todo "Post-merge reconcile xTrack/ across branches" still open.
