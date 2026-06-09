<!-- scope: feature -->
# xTrack FEAT_* File Reorganization — Implementation Spec

Move all `FEAT_*` files from project root into `xTrack/` feature subdirectories, grouped by feature name.

## Target Structure

```
xTrack/
  GLOBAL_CONTEXT.md                    ← stays, cross-cutting
  AppBakFlow/
    FEAT_DSC_AppBakFlow.md
    FEAT_HYD_AppBakFlow.md
  BakeNormalization/
    FEAT_DSC_BakeNormalization.md
    FEAT_DOC_BakeNormalization_prebake-batch.md
    FEAT_HYD_BakeNormalization.md
  Coastline/
    FEAT_DSC_Coastline.md
    FEAT_DOC_Coastline_300m-line-design.md
    FEAT_DOC_Coastline_300m-line-plan.md
    FEAT_PLN_Coastline_distance-algorithm.md
    FEAT_PLN_Coastline_distance-to-shore.md
    FEAT_PLN_Coastline_granularity-metrics.md
    FEAT_PLN_Coastline_migration-design.md
    FEAT_PLN_Coastline_migration-status.md
    FEAT_PLN_Coastline_overpass-race-fix.md
    FEAT_PLN_Coastline_persistence-optimization.md
    FEAT_PLN_Coastline_protobuf-cache-plan.md
    FEAT_PLN_Coastline_zone-strategy.md
  CodeReview/
    FEAT_DSC_CodeReview.md
    FEAT_HYD_CodeReview.md
  Dashboard/
    FEAT_DSC_Dashboard.md
    FEAT_PLN_Dashboard_redesign-plan.md
  DepthMapping/
    FEAT_DSC_DepthMapping.md
    FEAT_DOC_DepthMapping_bake.md
    FEAT_DOC_DepthMapping_design.md
    FEAT_DOC_DepthMapping_plan.md
    FEAT_DOC_DepthMapping_sources.md
    FEAT_PLN_DepthMapping_baro-alt-sources.md
    FEAT_PLN_DepthMapping_litto3d-shallow-coverage.md
  DepthSafety/
    FEAT_DSC_DepthSafety.md
    FEAT_HYD_DepthSafety.md
    FEAT_PLN_DepthSafety_plan.md
  GpsPlugin/
    FEAT_DSC_GpsPlugin.md
  isOnWaterAgain/
    FEAT_DSC_isOnWaterAgain.md
    FEAT_DOC_isOnWaterAgain_nearest-segment-design.md
    FEAT_PLN_isOnWaterAgain_bad-points.md
    FEAT_PLN_isOnWaterAgain_raycast.md
    FEAT_PLN_isOnWaterAgain_redesign.md
  MapDisplay/
    FEAT_DSC_MapDisplay.md
    FEAT_DOC_MapDisplay_marker-sizing.md
  Performance/
    FEAT_DSC_Performance.md
    FEAT_DOC_Performance_battery-design.md
  ProjectDocumentation/
    FEAT_DSC_ProjectDocumentation.md
    FEAT_PLN_ProjectDocumentation_phase1-complete-summary.md
  UiThingies/
    FEAT_DSC_UiThingies.md
    FEAT_PLN_UiThingies_icon-files-needed.md
    FEAT_PLN_UiThingies_icons-list.md
  WorkflowImprovement/
    FEAT_DSC_WorkflowImprovement.md
    FEAT_HYD_WorkflowImprovement.md
    FEAT_PLN_WorkflowImprovement_feat-summary-layer.md
    FEAT_PLN_WorkflowImprovement_help-cmd-extension.md
    FEAT_PLN_WorkflowImprovement_phase2-prompt-for-ai.md
    FEAT_PLN_WorkflowImprovement_planning.md
    FEAT_PLN_WorkflowImprovement_subfeature-context.md
    FEAT_PLN_WorkflowImprovement_xtrack-review.md
  Zone300/
    FEAT_DSC_Zone300.md
```

16 feature directories total. `GLOBAL_CONTEXT.md` stays at `xTrack/` root (cross-cutting).

## Phased Implementation Plan

### Phase 1: Create feature subdirectories

Create 16 empty directories under `xTrack/`:
AppBakFlow, BakeNormalization, Coastline, CodeReview, Dashboard, DepthMapping,
DepthSafety, GpsPlugin, isOnWaterAgain, MapDisplay, Performance,
ProjectDocumentation, UiThingies, WorkflowImprovement, Zone300.

Also: `Localisation` if `FEAT_DSC_Localisation.md` exists.

### Phase 2: Move root-level FEAT_DOC_*, FEAT_PLN_*, FEAT_HYD_* into subdirs

| Source (root) | Destination |
|---|---|
| `FEAT_DOC_BakeNormalization_prebake-batch.md` | `xTrack/BakeNormalization/` |
| `FEAT_DOC_Coastline_300m-line-design.md` | `xTrack/Coastline/` |
| `FEAT_DOC_Coastline_300m-line-plan.md` | `xTrack/Coastline/` |
| `FEAT_DOC_DepthMapping_bake.md` | `xTrack/DepthMapping/` |
| `FEAT_DOC_DepthMapping_design.md` | `xTrack/DepthMapping/` |
| `FEAT_DOC_DepthMapping_plan.md` | `xTrack/DepthMapping/` |
| `FEAT_DOC_DepthMapping_sources.md` | `xTrack/DepthMapping/` |
| `FEAT_DOC_isOnWaterAgain_nearest-segment-design.md` | `xTrack/isOnWaterAgain/` |
| `FEAT_DOC_MapDisplay_marker-sizing.md` | `xTrack/MapDisplay/` |
| `FEAT_DOC_Performance_battery-design.md` | `xTrack/Performance/` |
| `FEAT_HYD_AppBakFlow.md` | `xTrack/AppBakFlow/` |
| `FEAT_HYD_BakeNormalization.md` | `xTrack/BakeNormalization/` |
| `FEAT_HYD_CodeReview.md` | `xTrack/CodeReview/` |
| `FEAT_HYD_DepthSafety.md` | `xTrack/DepthSafety/` |
| `FEAT_HYD_WorkflowImprovement.md` | `xTrack/WorkflowImprovement/` |
| `FEAT_PLN_Coastline_distance-algorithm.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_distance-to-shore.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_granularity-metrics.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_migration-design.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_migration-status.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_overpass-race-fix.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_persistence-optimization.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_protobuf-cache-plan.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Coastline_zone-strategy.md` | `xTrack/Coastline/` |
| `FEAT_PLN_Dashboard_redesign-plan.md` | `xTrack/Dashboard/` |
| `FEAT_PLN_DepthMapping_baro-alt-sources.md` | `xTrack/DepthMapping/` |
| `FEAT_PLN_DepthMapping_litto3d-shallow-coverage.md` | `xTrack/DepthMapping/` |
| `FEAT_PLN_DepthSafety_plan.md` | `xTrack/DepthSafety/` |
| `FEAT_PLN_isOnWaterAgain_bad-points.md` | `xTrack/isOnWaterAgain/` |
| `FEAT_PLN_isOnWaterAgain_raycast.md` | `xTrack/isOnWaterAgain/` |
| `FEAT_PLN_isOnWaterAgain_redesign.md` | `xTrack/isOnWaterAgain/` |
| `FEAT_PLN_ProjectDocumentation_phase1-complete-summary.md` | `xTrack/ProjectDocumentation/` |
| `FEAT_PLN_UiThingies_icon-files-needed.md` | `xTrack/UiThingies/` |
| `FEAT_PLN_UiThingies_icons-list.md` | `xTrack/UiThingies/` |
| `FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` | `xTrack/WorkflowImprovement/` |
| `FEAT_PLN_WorkflowImprovement_help-cmd-extension.md` | `xTrack/WorkflowImprovement/` |
| `FEAT_PLN_WorkflowImprovement_phase2-prompt-for-ai.md` | `xTrack/WorkflowImprovement/` |
| `FEAT_PLN_WorkflowImprovement_planning.md` | `xTrack/WorkflowImprovement/` |
| `FEAT_PLN_WorkflowImprovement_subfeature-context.md` | `xTrack/WorkflowImprovement/` |
| `FEAT_PLN_WorkflowImprovement_xtrack-review.md` | `xTrack/WorkflowImprovement/` |

### Phase 3: Move FEAT_DSC_* from xTrack/ root into subdirs

| Source | Destination |
|---|---|
| `xTrack/FEAT_DSC_AppBakFlow.md` | `xTrack/AppBakFlow/` |
| `xTrack/FEAT_DSC_BakeNormalization.md` | `xTrack/BakeNormalization/` |
| `xTrack/FEAT_DSC_Coastline.md` | `xTrack/Coastline/` |
| `xTrack/FEAT_DSC_CodeReview.md` | `xTrack/CodeReview/` |
| `xTrack/FEAT_DSC_Dashboard.md` | `xTrack/Dashboard/` |
| `xTrack/FEAT_DSC_DepthMapping.md` | `xTrack/DepthMapping/` |
| `xTrack/FEAT_DSC_DepthSafety.md` | `xTrack/DepthSafety/` |
| `xTrack/FEAT_DSC_GpsPlugin.md` | `xTrack/GpsPlugin/` |
| `xTrack/FEAT_DSC_isOnWaterAgain.md` | `xTrack/isOnWaterAgain/` |
| `xTrack/FEAT_DSC_MapDisplay.md` | `xTrack/MapDisplay/` |
| `xTrack/FEAT_DSC_Performance.md` | `xTrack/Performance/` |
| `xTrack/FEAT_DSC_ProjectDocumentation.md` | `xTrack/ProjectDocumentation/` |
| `xTrack/FEAT_DSC_UiThingies.md` | `xTrack/UiThingies/` |
| `xTrack/FEAT_DSC_WorkflowImprovement.md` | `xTrack/WorkflowImprovement/` |
| `xTrack/FEAT_DSC_Zone300.md` | `xTrack/Zone300/` |

### Phase 4: Update AGENTS.md

Changes needed in `AGENTS.md`:

1. **§7a line 60** — Memory Stack Layout:
   - OLD: `root-level FEAT_* files` → NEW: `xTrack/[Feature]/FEAT_* files`
   - OLD: `root-level FEAT_HYD_[Feature].md` → NEW: `xTrack/[Feature]/FEAT_HYD_[Feature].md`
   - OLD: `xTrack/FEAT_DSC_[name].md` → NEW: `xTrack/[Feature]/FEAT_DSC_[name].md`

2. **§7a line 61** — Read-and-Route:
   - OLD: `read its hydration FEAT_HYD_[X].md` → NEW: `read its hydration xTrack/[X]/FEAT_HYD_[X].md`

3. **§7b item 3 (line 69)** — New Epic Feature (`#track`):
   - OLD: `create xTrack/FEAT_DSC_[name].md` → NEW: `create xTrack/[name]/ directory and xTrack/[name]/FEAT_DSC_[name].md inside it`

4. **§7b item 5 (line 73)** — Memory Bake:
   - OLD: `FEAT_HYD_[Feature].md (at project root)` → NEW: `xTrack/[Feature]/FEAT_HYD_[Feature].md`

5. **§7b item 11 (line 81)** — `#doc create`:
   - OLD: `create FEAT_DOC_[ActiveFeature]_[name].md` → NEW: `create xTrack/[ActiveFeature]/FEAT_DOC_[name].md`

6. **§7b item 11 (line 82)** — `#doc list`:
   - OLD: `scan ... FEAT_DOC_*.md, and FEAT_PLN_*.md` → NEW: `scan ... xTrack/*/FEAT_DOC_*.md, and xTrack/*/FEAT_PLN_*.md`
   - OLD: `DOC (FEAT_DOC_), PLN (FEAT_PLN_)` → NEW: `DOC (xTrack/*/FEAT_DOC_), PLN (xTrack/*/FEAT_PLN_)`

7. **§7b item 11 (line 83)** — `#doc read`:
   - OLD: `fuzzy-resolve against docs/**, FEAT_DOC_*, and FEAT_PLN_*` → NEW: `fuzzy-resolve against docs/**, xTrack/*/FEAT_DOC_*, and xTrack/*/FEAT_PLN_*`

8. **§7b item 11 (line 84)** — `#doc attach`:
   - OLD: `fuzzy-resolve against docs/**/*.md, FEAT_DOC_*.md, and FEAT_PLN_*.md` → NEW: `fuzzy-resolve against docs/**/*.md, xTrack/*/FEAT_DOC_*.md, and xTrack/*/FEAT_PLN_*.md`

9. **§7b item 11 (line 86)** — `#doc sync`:
   - OLD: `Generate or update FEAT_DOC_[name]_profile.md` → NEW: `Generate or update xTrack/[name]/FEAT_DOC_[name]_profile.md`

10. **§7b item 11 (line 87)** — `#doc audit`:
    - OLD: `scan all docs/**/*.md (recursive), FEAT_DOC_*.md, and FEAT_PLN_*.md` → NEW: `scan all docs/**/*.md (recursive), xTrack/*/FEAT_DOC_*.md, and xTrack/*/FEAT_PLN_*.md`

11. **§7b item 12 (line 88)** — `#doctor`:
    - OLD: `orphan docs (in docs/** or FEAT_DOC_*/FEAT_PLN_*)` → NEW: `orphan docs (in docs/** or xTrack/*/FEAT_DOC_*/FEAT_PLN_*)`

12. **§7b item 14 (line 90)** — `#status diff`:
    - OLD: `Read the feature's hydration file FEAT_HYD_[Feature].md` → NEW: `Read the feature's hydration file xTrack/[Feature]/FEAT_HYD_[Feature].md`

### Phase 5: Update docs/cmd_help.md

All instances of bare filename patterns need `xTrack/*/` prefix:
- `FEAT_DOC_` → `xTrack/*/FEAT_DOC_`
- `FEAT_PLN_` → `xTrack/*/FEAT_PLN_`
- `FEAT_HYD_` → `xTrack/*/FEAT_HYD_`
- `FEAT_DSC_` → `xTrack/*/FEAT_DSC_` (where referenced)

### Phase 6: Update xTrack/GLOBAL_CONTEXT.md

1. **Last Bake line (line 7)**: Update path descriptions in the Last Bake message
2. **Always-Loaded Context**: If it references specific FEAT_ paths, update them
3. **Routing Map table**: Feature File column paths from `FEAT_DSC_X.md` → `xTrack/X/FEAT_DSC_X.md`

### Phase 7: Update feature ## Docs sections

All `## Docs` entries in FEAT_DSC_ files that reference `FEAT_DOC_*` or `FEAT_PLN_*` need the `xTrack/[Feature]/` prefix. Files to update:

| Feature File | Current Doc Refs | New Path Prefix |
|---|---|---|
| `FEAT_DSC_Performance.md` | `FEAT_DOC_Performance_battery-design.md` | `xTrack/Performance/` |
| `FEAT_DSC_isOnWaterAgain.md` | `FEAT_DOC_isOnWaterAgain_nearest-segment-design.md` | `xTrack/isOnWaterAgain/` |
| `FEAT_DSC_GpsPlugin.md` | `FEAT_DOC_MapDisplay_marker-sizing.md`, `FEAT_DOC_Coastline_300m-line-design.md` | `xTrack/MapDisplay/`, `xTrack/Coastline/` |
| `FEAT_DSC_DepthSafety.md` | `FEAT_PLN_DepthSafety_plan.md` | `xTrack/DepthSafety/` |
| `FEAT_DSC_DepthMapping.md` | `FEAT_DOC_DepthMapping_design.md`, `FEAT_DOC_DepthMapping_plan.md`, `FEAT_DOC_DepthMapping_bake.md`, `FEAT_DOC_DepthMapping_sources.md` | `xTrack/DepthMapping/` |
| `FEAT_DSC_BakeNormalization.md` | `FEAT_DOC_DepthMapping_bake.md`, `FEAT_PLN_DepthMapping_litto3d-shallow-coverage.md` | `xTrack/DepthMapping/` |
| `FEAT_DSC_WorkflowImprovement.md` | `FEAT_PLN_WorkflowImprovement_planning.md`, `FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` | `xTrack/WorkflowImprovement/` |

### Phase 8: Update FEAT_HYD_* files

`FEAT_HYD_WorkflowImprovement.md` contains path references to `FEAT_DOC_*`, `FEAT_PLN_*`, `FEAT_HYD_*` that need updating.

### Phase 9: Update FEAT_PLN_WorkflowImprovement_feat-summary-layer.md

Taxonomy table and file lists reference bare `FEAT_DOC_*/FEAT_PLN_*/FEAT_HYD_*` names — update to `xTrack/*/` paths.

### Phase 10: Cleanup

- Delete `xTrack/FEATURE_SCOPE_GpsPlugin.md` if it exists (stale old-format file — was renamed to `FEAT_DSC_GpsPlugin.md`)
- Verify no root-level `FEAT_*` files remain (except none should)
- Run `#doctor` equivalent check for broken references
