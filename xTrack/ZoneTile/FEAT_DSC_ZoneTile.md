---
name: ZoneTile
status: active
created: 2026-06-17 09:45
modified: 2026-09-16 06:31
---

# Feature: ZoneTile

**Description:**
Zone information tiles and map overlay rendering — zone-ahead cone/line, zone information cards, speed zone display, ETA calculations, and zone state management on the map.

## Sections

## Todos

## Rules

## Key Files

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
- `xTrack/ZoneTile/260617_FEAT_PLN_ZoneTile_speed-enforcement-zone-auto-show-plan.md` — Speed enforcement zone auto show plan (CONSOLIDATED → `xTrack/Navigation/FEAT_DOC_Navigation_auto-show.md`; stub retained)
- `xTrack/ZoneTile/260617_FEAT_PLN_ZoneTile_zone300-auto-show-stutter-fix.md` — Zone 300 auto show stutter fix (overlay-render scope; auto-show context → `xTrack/Navigation/FEAT_DOC_Navigation_auto-show.md`)
- `xTrack/ZoneTile/260708_FEAT_PLN_ZoneTile_zones-alerts-nested-zone-distance-tile.md` — Zones alerts nested zone distance tile

## Implemented

- **Zone info per-line scrim** — 50% black rounded scrim behind each zone info line (config `ui.settings.text.scrim`) → `xTrack/ZoneTile/260612_FEAT_PLN_ZoneTile_info-text-discussion.md`
- **Zone info text on the disabled-surface fill (2026-09-15, `feature/no-black-casing`)** — `RegulatedZoneInfoText`'s per-line card moved off the navy scrim: its background is now `AppConfig.semanticInactive` copied at `AppConfig.buttonDisabledBackgroundAlpha`, the very expression the speed-scale card uses, and its text moved from `uiTextPrimary` to `uiTextSecondary`, so the map's two bottom-left cards share one surface. Geometry is untouched — 4 dp corners, 3/1 dp padding, 2 dp line spacing, 9 sp type with `lineHeight` 14 sp — which still leaves the two cards differing in radius, border, padding, type size, weight and line cap. The move took `ui.text.scrim`'s last reader: the key, its `AppConfig` accessor and parse line and its `color-scheme.md` row are now dead configuration awaiting a decision. The fill's alpha is read from `colors.properties`, where it has been hand-tuned to 0.66 with `AppConfig`'s default following it. Contrast is what the change leaves open: white on navy measured its best over dark water and its worst over pale tiles, and a mid grey on a light panel inverts that, so the deep-water end is the case to look at.
- **Zone info text on the shared text pair (2026-09-15, `feature/no-black-casing`)** — the card's text now takes the disabled surface's own pair rather than a bare token: `AppConfig.buttonDisabledTextColor`, fed by `ui.button.disabled.text.color` in `colors.properties` (which aliases `ui.text.secondary`), and `AppConfig.buttonDisabledTextBold`, fed by `ui.button.disabled.text.bold` and true as shipped. Colour, weight and the fill's alpha are therefore three keys side by side in the palette, the same three the speed-scale card reads, so the map's two bottom-left cards stay in step without a code change.
- **Zone line on the overlay family (2026-09-16, `feature/no-black-casing`)** — supersedes both the geometry and the keys in the two entries above: the line's fill, text colour, weight, size, corner radius, padding and column gap now come from `ui.map.overlay.*` over the shared `ui.map.surface.inactive`, the same family the speed scale reads, so the two cards share one surface by construction rather than by matching numbers. The converged values are what the device must judge: type 9 → 10 sp, padding 3/1 → 6 dp and corners 4 → 8 dp, which takes the gap between two stacked lines from 4 to 14 dp and grows a three-line stack by roughly 30 dp upward from its bottom-aligned seat. `lineHeight = 14.sp` survives as a literal — the one type value not yet in the family, to fold in if the size key is ever retuned.
