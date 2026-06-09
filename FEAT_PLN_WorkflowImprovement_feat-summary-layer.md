<!-- scope: feature -->
# xTrack Token Optimization — Final Plan

## Decisions Summary

| # | Decision |
|---|---|
| 1 | Rename `FEATURE_SCOPE_*.md` → `FEAT_DSC_*.md` |
| 2 | Embed feature summaries as markdown table in `GLOBAL_CONTEXT.md` |
| 3 | Reduce Always-Loaded Context 6→3 (lazy-load cmd_help, fuzzy-resolve, templates; remove commands.md) |
| 4 | Smart `#status`: subfeature-scoped reads when subfeature focused |
| 5 | Fold `GLOBAL_TODOS.md` into `GLOBAL_CONTEXT.md` |
| 6 | Slim FEAT_DSC_ front-matter: drop `one_liner`, `subs_total`, `subs_done` |
| 7 | `#doc create`: always prompt "Feature-scoped or cross-cutting?" |
| 8 | `#doc list`: grouped by source (DOC / PLN / docs/ reference) |
| 9 | PLN migration: AI auto-maps plans→features, presents confirmation table before executing |
| 10 | Two-phase implementation: Phase 1 (structural, atomic), Phase 2 (content migration) |

## File Taxonomy

```
FEAT_[TYPE]_[FeatureName]_[topic].md
```

| Type | Meaning | Example |
|---|---|---|
| `DSC` | Feature description (was FEATURE_SCOPE_) | `FEAT_DSC_DepthSafety.md` |
| `DOC` | Feature-scoped reference doc (was docs/ feature-specific) | `FEAT_DOC_DepthMapping_design.md` |
| `PLN` | Plan / design discussion (was plans/*.md) | `FEAT_PLN_DepthSafety_caching-strategy.md` |
| `HYD` | Hydration snapshot (was xTrack/hydration/) | `FEAT_HYD_DepthSafety.md` |

For subfeature-scoped DOC/PLN: `FEAT_[TYPE]_[Feature]_[subfeature]_[topic].md`

`docs/` becomes purely cross-cutting reference (MARO_ARCHITECTURE, SETUP, FAQ, cmd_help, GIT_WORKFLOW, oZer/).
`plans/` retains only binary assets (.png, .ico, .bat, .pdn).

## Phase 1 — Structural (atomic, ~8 steps)

1. Update `AGENTS.md` §7b — all commands: rename refs, `#context list` reads GLOBAL_CONTEXT.md table, `#status` subfeature-scoped, `#doc create` prompt, `#doc list` grouped, `#bake` updates summary table + FEAT_HYD_, `#doctor` new checks
2. Update `docs/cmd_help.md` — all command sections
3. Update `.claude/skills/xtrack/SKILL.md` — file path refs
4. Update `.claude/skills/xtrack/references/templates.md` — new templates for DSC, DOC, PLN, HYD
5. Rewrite `xTrack/GLOBAL_CONTEXT.md`:
   - Add `## Feature Summaries` markdown table (all 15 features, maintained by `#bake`)
   - Add `## Global Todos` section (from GLOBAL_TODOS.md)
   - Update `## Always-Loaded Context` (6→3: AGENTS.md, GLOBAL_CONTEXT.md, SKILL.md)
   - Update Routing Map file refs (FEATURE_SCOPE_ → FEAT_DSC_)
6. Rename `FEATURE_SCOPE_*.md` → `FEAT_DSC_*.md` (×15)
7. Slim FEAT_DSC_ front-matters: drop `one_liner`, `subs_total`, `subs_done` (keep `name`, `status`, `created`, `modified`, `active_subfeature`)
8. Delete `xTrack/GLOBAL_TODOS.md`

## Phase 2 — Content Migration (~6 steps)

9. Migrate docs/ feature-specific → FEAT_DOC_ (×~10):
   - `docs/300MLineDesign.md` → `FEAT_DOC_Coastline_300m-line-design.md`
   - `docs/300MLinePlan.md` → `FEAT_DOC_Coastline_300m-line-plan.md`
   - `docs/DepthMappingBake.md` → `FEAT_DOC_DepthMapping_bake.md`
   - `docs/DepthMappingDesign.md` → `FEAT_DOC_DepthMapping_design.md`
   - `docs/DepthMappingPlan.md` → `FEAT_DOC_DepthMapping_plan.md`
   - `docs/depthMappingSources.md` → `FEAT_DOC_DepthMapping_sources.md`
   - `docs/isOnWater-nearest-segment-design.md` → `FEAT_DOC_isOnWaterAgain_nearest-segment-design.md`
   - `docs/MARKER_SIZING.md` → `FEAT_DOC_MapDisplay_marker-sizing.md`
   - `docs/PerformanceBatteryDesign.md` → `FEAT_DOC_Performance_battery-design.md`
   - `docs/prebake-batch.md` → `FEAT_DOC_BakeNormalization_prebake-batch.md`
10. Migrate plans/*.md → FEAT_PLN_ (×~25): AI reads each plan, infers feature owner, presents confirmation table, executes renames
11. Migrate hydration → FEAT_HYD_ (×~4):
    - `xTrack/hydration/CONTEXT_HYDRATION_AppBakFlow.md` → `FEAT_HYD_AppBakFlow.md`
    - `xTrack/hydration/CONTEXT_HYDRATION_BakeNormalization.md` → `FEAT_HYD_BakeNormalization.md`
    - `xTrack/hydration/CONTEXT_HYDRATION_CodeReview.md` → `FEAT_HYD_CodeReview.md`
    - `xTrack/hydration/CONTEXT_HYDRATION_DepthSafety.md` → `FEAT_HYD_DepthSafety.md`
12. Delete `xTrack/hydration/` directory
13. Update all `## Docs` and `## Key Files` cross-references in FEAT_DSC_ files (old paths → new paths)
14. Run `#doctor` to verify

## Token Win Summary

| Optimization | When | Impact |
|---|---|---|
| Summary table in GLOBAL_CONTEXT.md | `#context list` | 14 fewer file reads |
| Always-Loaded 6→3 | Every session turn | ~3 fewer files per turn |
| Subfeature-scoped `#status` | When sub-focused | ~80% reduction on large features |
| Slimmer front-matter | Every FEAT_DSC_ read | 3 fewer lines per file |
| Folded GLOBAL_TODOS | `#todo` listing | 1 fewer file |

## Always-Loaded Context (updated)

```
AGENTS.md                    — canonical rulebook
xTrack/GLOBAL_CONTEXT.md     — routing + summaries + global todos + hydration
.claude/skills/xtrack/SKILL.md — skill dispatch
```

Lazy-loaded on `#help` only: `docs/cmd_help.md`, `references/fuzzy-resolve.md`, `references/templates.md`.
Removed: `references/commands.md` (deprecated).
