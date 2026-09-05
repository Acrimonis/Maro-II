---
name: Markers
status: active
created: 2026-06-22 11:52
modified: 2026-09-05 10:42
---

# Feature: Markers

**Description:**
User-defined markers on the map — Pin, Circle, and Corridor geometries. Line-of-sight matching with proximity-zone pre-filter (land-blocking via coastline spatial index + 10m grazing tolerance). On-demand "where am I?" query via boat marker tap. Step-by-step wizard for creation/editing. Viewing drawer (card layout) + match result display. Management page with swipe-to-delete. OSMdroid native overlays. Binary layer toggle (HIDDEN/SHOW_ALL). POI emoji icons on markers; pin is a real persisted flag.

## Sections

### pin-halo-rendering

Static settings-driven halo ring differentiates pinned (white, strong) from unpinned (light-blue, faint) markers; corridor always-on colored line + pinned under-line halo; selected-marker gold driven by `selectedMarkerId`.

#### Docs
- `xTrack/Markers/260905_FEAT_PLN_Markers_pin-halo-rendering.md` — plan (implemented)

## Todos
- [ ] fix proximity of date points — rays hit/test all of them

## Implemented

- **pin-halo-rendering (2026-09-05)** — static halo ring (pinned white / unpinned light-blue, absolute size 18-60px, opacity pairs); pin dimming removed (search dimming kept); corridor always-on colored line + pinned under-line halo; selected-marker gold driven by `selectedMarkerId` (forces zones, folds `navigationZonesVisible`, removes `highlightedMarkerId`); corridor/circle focus zoom-to-fit; icon centered on halo; code split into `MarkerAppearance`/`MarkerHalo` → `xTrack/Markers/260905_FEAT_PLN_Markers_pin-halo-rendering.md`
- **icon-pin-decoupling (2026-09-04)** — icon fully decoupled from pin (pure POI emoji, no pin semantics); pin re-implemented as real persisted `UserMarker.pinned` mirroring tracks (repo/VM/card/drawer/multi-select/filter/rendering); removed obsolete `migratePinnedToIcon`; settings v7 migration → `xTrack/Markers/260904_FEAT_PLN_Markers_icon-pin-decoupling.md`
- **multi-select-merge (2026-07-18)** — multi-select delete/pin/merge on marker list → `xTrack/Markers/260718_FEAT_PLN_Markers_multi-select-and-merge.md`
- **marker-card (2026-07-03)** — normalized list/viewing/match cards, removed `markerFormatText()`
- **wizard-cleanup-race (2026-07-02)** — moved wizard-state cleanup into save/update coroutines
- **auto-marker-proximity (2026-07-02)** — 🕐 auto markers use `boatMarkerAutoMarkerProximityM` (300m) → `xTrack/Markers/260702_FEAT_PLN_Markers_auto-marker-proximity-fix.md`
- **Data model & persistence** — `UserMarker` + sealed `MarkerGeometry`, JSON repo, 16-color palette → `xTrack/Markers/260622_FEAT_PLN_Markers_user-markers-design.md`
- **Match resolution — two-gate model** — zone check + proximity pre-filter + line-of-sight → `xTrack/Markers/FEAT_DOC_Markers_decisions.md`
- **Boundary point sampling** — `pointAlongBearing` direct boundary sampling
- **Overlay rendering** — Pin/Circle/Corridor overlays, tri-state `MarkerLayerState`
- **Creation & editing** — `WizardDrawer` step wizard + `WizardButtonRow` → `xTrack/Markers/260623_FEAT_PLN_Markers_create-zones-flow.md`
- **Viewing & management** — `MarkerDrawer` + `MarkerManagementOverlay`
- **Color system** — `COLOR_CONFIRMED`=blue, `COLOR_UNCONFIRMED`=amber
- **setting-markers** — grouped Appearance card, binary HIDDEN/SHOW_ALL layer toggle, settings migration v5
- **Known issues** — see `xTrack/Markers/FEAT_HYD_Markers.md`

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerAppearance.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerHalo.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/WizardButtonRow.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt`

## OwnedFiles
- `app/src/main/java/ykws/android/maro/data/model/markers/`
- `app/src/main/java/ykws/android/maro/data/markers/`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerAppearance.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerHalo.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/`

## Docs
- `xTrack/Markers/260905_FEAT_PLN_Markers_pin-halo-rendering.md` — pin halo rendering plan (implemented)
- `xTrack/Markers/260904_FEAT_PLN_Markers_icon-pin-decoupling.md` — icon/pin decoupling + pin re-implementation plan (implemented)
- `xTrack/Markers/FEAT_DOC_Markers_decisions.md` — architectural decisions with rationale and source references
- `xTrack/Markers/FEAT_HYD_Markers.md` — hydration snapshot + known issues (single source of truth)
- `xTrack/Markers/260622_FEAT_PLN_Markers_user-markers-design.md` — original design plan (all phases ✅)
- `xTrack/Markers/260623_FEAT_PLN_Markers_create-zones-flow.md` — wizard creation flow design
- `xTrack/Markers/260625_FEAT_PLN_Markers_whereami-rework.md` — whereAmI rework design (implemented)
- `xTrack/Markers/260625_FEAT_PLN_Markers_debug-wia.md` — debug instrumentation design (implemented)
- `xTrack/Markers/260630_FEAT_PLN_Markers_whereami-gaps.md` — current angular shadow over-blocking analysis
- `xTrack/Markers/260626_FEAT_PLN_Markers_icon.md` — POI icon system design (implemented)
- `xTrack/Markers/260628_FEAT_PLN_Markers_icon-fixes.md` — icon bug-fix plan (partial: management icon picker ✅, duplicate imports ❌)
- `xTrack/Markers/260626_FEAT_PLN_Markers_marker-pin.md` — pin toggle design (implemented)
- `xTrack/Markers/260626_FEAT_PLN_Markers_marker-pin-tri-state.md` — tri-state layer toggle design (implemented)
- `xTrack/Markers/260624_FEAT_PLN_Markers_area-tap-and-wizard-buttons.md` — area tap + wizard buttons + corridor caps + color settings (area tap ✅, buttons ✅, caps ✅, color settings ❌)
- `xTrack/Markers/260625_FEAT_PLN_Markers_next-session-ui-polish.md` — UI polish plan (format ❌, color picker ✅, edit icon ✅, viewing drawer ✅)
- `xTrack/Markers/260702_FEAT_PLN_Markers_auto-marker-proximity-fix.md` — Auto marker proximity fix
- `xTrack/Markers/260711_FEAT_PLN_Markers_marker-hilite-dual-outline-plan.md` — Marker highlight dual outline plan
- `xTrack/Markers/260623_FEAT_PLN_Markers_marker-proximity-wizard-normalization.md` — Marker proximity wizard normalization
- `xTrack/Markers/260624_FEAT_PLN_Markers_markers-section-normalization.md` — Markers section normalization
- `xTrack/Markers/260701_FEAT_PLN_Markers_remove-proximity-range-gates.md` — Remove proximity range gates
- `xTrack/Markers/260711_FEAT_PLN_Markers_track-hilite-dual-outline-plan.md` — Track highlight dual outline plan
- `xTrack/Markers/260701_FEAT_PLN_Markers_whereami-flow.md` — WhereAmI flow
