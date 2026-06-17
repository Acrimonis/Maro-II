<!-- scope: feature -->
# Plan Migration — Bulk Relocation to `xTrack/*/FEAT_PLN_*`

## Problem

62 markdown plans live in `plans/` root. Per the Documentation feature's spec, feature-scoped plans belong in `xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md`. The Explain/Discuss Gate exception still writes new plans to `plans/` instead of the feature's xTrack directory.

## Step (a): Fix the Rule

### Change Explain/Discuss Gate in AGENTS.md Core Directives

Current:
```
exactly one `plans/[topic].md` file may be created to capture the discussion, auto-attached to the active feature's `## Docs`
```

New:
```
exactly one `xTrack/[ActiveFeature]/FEAT_PLN_[Feature]_[topic].md` file may be created to capture the discussion, auto-attached to the active feature's `## Docs`
```

### Change `#doc create` rule

When a plan discussion creates a feature-scoped doc, it should use `xTrack/[Feature]/FEAT_PLN_` naming, not `plans/`.

## Step (b): Bulk Migration — Plan-to-Feature Mapping

### RegulatedZones (×13)
| Plan | Target |
|---|---|
| `regulated-zones-category-icon-mapping.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_category-icon-mapping.md` |
| `regulated-zones-data-lookup-plan.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_data-lookup-plan.md` |
| `regulated-zones-filter-design.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_filter-design.md` |
| `regulated-zones-fixes-discussion.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_fixes-discussion.md` |
| `regulated-zones-hexagon-fix-plan.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_hexagon-fix-plan.md` |
| `regulated-zones-icon-gap-analysis.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_icon-gap-analysis.md` |
| `regulated-zones-icon-warnings-plan.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_icon-warnings-plan.md` |
| `regulated-zones-multi-source-normalization.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_multi-source-normalization.md` |
| `regulated-zones-readme.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_readme.md` |
| `regulated-zones-reqs-formalized.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_reqs-formalized.md` |
| `regulated-zones-toggle-merge-design.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_toggle-merge-design.md` |
| `regulated-zones-vessel-filter-design.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_vessel-filter-design.md` |
| `regulated-zones-vessel-size-filtering.md` | `xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_vessel-size-filtering.md` |

### ArcLayout (×6)
| Plan | Target |
|---|---|
| `arclayout-button-analysis.md` | `xTrack/ArcLayout/FEAT_PLN_ArcLayout_button-analysis.md` |
| `arclayout-feature-plan.md` | `xTrack/ArcLayout/FEAT_PLN_ArcLayout_feature-plan.md` |
| `fan-btn-hide-ozers-plan.md` | `xTrack/ArcLayout/FEAT_PLN_ArcLayout_fan-btn-hide-ozers-plan.md` |
| `fanlayout-child-centering-rule.md` | `xTrack/ArcLayout/FEAT_PLN_ArcLayout_child-centering-rule.md` |
| `fanlayout-equidistance-rule.md` | `xTrack/ArcLayout/FEAT_PLN_ArcLayout_equidistance-rule.md` |
| `fanlayout-extension-discussion.md` | `xTrack/ArcLayout/FEAT_PLN_ArcLayout_extension-discussion.md` |

### GPS (×3)
| Plan | Target |
|---|---|
| `gps-loss-fix-plan.md` | `xTrack/GPS/FEAT_PLN_GPS_loss-fix-plan.md` |
| `gps-loss-investigation.md` | `xTrack/GPS/FEAT_PLN_GPS_loss-investigation.md` |
| `boat-marker-offset-discussion.md` | `xTrack/UI_Map/FEAT_PLN_UI_Map_boat-marker-offset-discussion.md` |

### ZoneTile / Zones (×24)
| Plan | Target |
|---|---|
| `zone-ahead-cone-implementation.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_ahead-cone-implementation.md` |
| `zone-ahead-line-implementation.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_ahead-line-implementation.md` |
| `zone-data-migration-full-plan.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_data-migration-full-plan.md` |
| `zone-info-architecture-plan.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_info-architecture-plan.md` |
| `zone-info-text-discussion.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_info-text-discussion.md` |
| `zone-methods-performance-analysis.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_methods-performance-analysis.md` |
| `zone-situation-unified-model.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_situation-unified-model.md` |
| `zone-tile-border-uniformity-discussion.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_border-uniformity-discussion.md` |
| `zone-tile-distance-tile-rendering-plan.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_distance-tile-rendering-plan.md` |
| `zone-tile-entry-exit-methods-plan.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_entry-exit-methods-plan.md` |
| `zone-tile-eta-matrix-final.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_eta-matrix-final.md` |
| `zone-tile-exit-distance-approach.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_exit-distance-approach.md` |
| `zone-tile-exit-preview-threshold.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_exit-preview-threshold.md` |
| `zone-tile-exiting-caption-discussion.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_exiting-caption-discussion.md` |
| `zone-tile-final-formatting.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_final-formatting.md` |
| `zone-tile-inside-zone-functionality-discussion.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_inside-zone-functionality-discussion.md` |
| `zone-tile-state-normalization.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_state-normalization.md` |
| `zones-around-boat-unified.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_zones-around-boat-unified.md` |
| `lookup-zone-around-boat-discussion.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_lookup-around-boat-discussion.md` |
| `eta-to-exit-analysis.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_eta-to-exit-analysis.md` |
| `speed-zones-design.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_speed-zones-design.md` |
| `speed-zones-heading-distance-discussion.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_speed-zones-heading-distance.md` |
| `speed-zones-side-zone-display-design.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_speed-zones-side-zone-display.md` |
| `rotate-map-demo-mode-implications.md` | `xTrack/ZoneTile/FEAT_PLN_ZoneTile_rotate-map-demo-mode.md` |

### UI / Settings / Display (×8)
| Plan | Target |
|---|---|
| `settings-tab-reorder-discussion.md` | `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_tab-reorder-discussion.md` |
| `settings-vertical-padding-discussion.md` | `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_vertical-padding.md` |
| `landscape-width-font-sizing-discussion.md` | `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_landscape-font-sizing.md` |
| `portrait-bottom-space-statusbar-discussion.md` | `xTrack/Ui_General/FEAT_PLN_Ui_General_portrait-bottom-space.md` |
| `right-edge-controls-gap-asymmetry-analysis.md` | `xTrack/UI_Map/FEAT_PLN_UI_Map_right-edge-gap-asymmetry.md` |
| `map-overlay-layout-inventory.md` | `xTrack/UI_Map/FEAT_PLN_UI_Map_overlay-layout-inventory.md` |
| `map-overlay-layout-rationalization.md` | `xTrack/UI_Map/FEAT_PLN_UI_Map_overlay-layout-rationalization.md` |
| `icon-rendering-overhaul-plan.md` | `xTrack/UI_Map/FEAT_PLN_UI_Map_icon-rendering-overhaul.md` |

### Colors (×3)
| Plan | Target |
|---|---|
| `btn-color-harmonization.md` | `xTrack/ColorManagement/FEAT_PLN_ColorManagement_btn-color-harmonization.md` |
| `button-colors-discussion.md` | `xTrack/ColorManagement/FEAT_PLN_ColorManagement_button-colors-discussion.md` |
| `color-props-migration-plan.md` | `xTrack/ColorManagement/FEAT_PLN_ColorManagement_props-migration.md` |

### Performance (×2)
| Plan | Target |
|---|---|
| `drag-stutter-complete-event-chain-analysis.md` | `xTrack/Performance/FEAT_PLN_Performance_drag-stutter-event-chain.md` |
| `drag-stutter-performance-analysis.md` | `xTrack/Performance/FEAT_PLN_Performance_drag-stutter-analysis.md` |

### Zone300SpeedBadge (×1)
| Plan | Target |
|---|---|
| `zone300-speed-badge-design.md` | `xTrack/Zone300SpeedBadge/FEAT_PLN_Zone300SpeedBadge_design.md` |

### Badge (×1)
| Plan | Target |
|---|---|
| `badge-clipping-fix.md` | `xTrack/ArcLayout/FEAT_PLN_ArcLayout_badge-clipping-fix.md` |

### General (×2)
| Plan | Target |
|---|---|
| `round-1-summary-and-round-2-plan.md` | `xTrack/Documentation/FEAT_PLN_Documentation_round-1-summary-round-2-plan.md` |
| `zoneconfig-to-appconfig-rename.md` | `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_zoneconfig-to-appconfig.md` |

### Already migrated to FEAT_PLN_ (skip)
- `merge-conflict-resolution.md` — already attached to WorkflowImprovement ## Docs

## Execution Steps

1. Fix AGENTS.md Explain/Discuss Gate exception: `plans/` → `xTrack/[Feature]/FEAT_PLN_`
2. Create target xTrack subdirectories if they don't exist (ZoneTile, ColorManagement — check)
3. For each plan file: `git mv plans/[old] xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md`
4. Add `<!-- scope: feature -->` header line to each moved plan
5. Attach each moved plan to its feature's `## Docs` section in `FEAT_DSC_*.md`
6. Verify: `#doc audit` should show no orphans
7. `#commit` to snapshot
