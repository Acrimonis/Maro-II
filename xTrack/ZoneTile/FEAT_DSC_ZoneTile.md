---
name: ZoneTile
status: active
created: 2026-06-17 09:45
modified: 2026-09-02 21:35
active_subfeature: none
---

# Feature: ZoneTile

**Description:**
Zone information tiles and map overlay rendering — zone-ahead cone/line, zone information cards, speed zone display, ETA calculations, and zone state management on the map.

## Subfeatures

## Todos

## Rules

## Key Files

## OwnedFiles

## Docs
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_ahead-cone-implementation.md` — Zone ahead cone implementation
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_ahead-line-implementation.md` — Zone ahead line implementation
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_border-uniformity-discussion.md` — Zone tile border uniformity discussion
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_data-migration-full-plan.md` — Zone data migration full plan
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_distance-tile-rendering-plan.md` — Distance tile rendering plan
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_entry-exit-methods-plan.md` — Entry/exit methods plan
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_eta-matrix-final.md` — ETA matrix final design
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_eta-to-exit-analysis.md` — ETA to exit analysis
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_exit-distance-approach.md` — Exit distance approach
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_exit-preview-threshold.md` — Exit preview threshold
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_exiting-caption-discussion.md` — Exiting caption discussion
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_final-formatting.md` — Final formatting
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_info-architecture-plan.md` — Info architecture plan
- `xTrack/ZoneTile/260612_FEAT_PLN_ZoneTile_info-text-discussion.md` — Info text discussion
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_inside-zone-functionality-discussion.md` — Inside zone functionality discussion
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_lookup-around-boat-discussion.md` — Lookup zone around boat discussion
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_methods-performance-analysis.md` — Zone methods performance analysis
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_rotate-map-demo-mode.md` — Rotate map in demo mode implications
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_situation-unified-model.md` — Unified zone model
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_speed-zones-design.md` — Speed zones design
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_speed-zones-heading-distance.md` — Speed zones heading distance discussion
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_speed-zones-side-zone-display.md` — Speed zones side zone display design
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_state-normalization.md` — Zone tile state normalization
- `xTrack/ZoneTile/260614_FEAT_PLN_ZoneTile_zones-around-boat-unified.md` — Zones around boat unified
- `xTrack/ZoneTile/260617_FEAT_PLN_ZoneTile_speed-enforcement-zone-auto-show-plan.md` — Speed enforcement zone auto show plan
- `xTrack/ZoneTile/260617_FEAT_PLN_ZoneTile_zone300-auto-show-stutter-fix.md` — Zone 300 auto show stutter fix
- `xTrack/ZoneTile/260708_FEAT_PLN_ZoneTile_zones-alerts-nested-zone-distance-tile.md` — Zones alerts nested zone distance tile

## Implemented

- **Zone info text per-line scrim** — Each zone info line now sits on a 50%-transparent black rounded scrim (radius 4dp, padding 3/1dp) so the white text stays readable over any map tile; lines spaced 2dp apart. Scrim color is runtime-configurable via `ui.settings.text.scrim`.
  *Files:* `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/colors.properties`
