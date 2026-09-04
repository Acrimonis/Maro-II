---
name: Markers
status: active
created: 2026-06-22 11:52
modified: 2026-09-04 21:41
---

# Feature: Markers

**Description:**
User-defined markers on the map — Pin, Circle, and Corridor geometries. Line-of-sight matching with proximity-zone pre-filter (land-blocking via coastline spatial index + 10m grazing tolerance). On-demand "where am I?" query via boat marker tap. Step-by-step wizard for creation/editing. Viewing drawer (card layout) + match result display. Management page with swipe-to-delete. OSMdroid native overlays. Tri-state layer toggle (HIDDEN/SHOW_ALL/SHOW_PINNED). POI emoji icons on pinned markers.

## Sections
16/16 complete; 1 in progress (icon-pin-decoupling).

### icon-pin-decoupling

### multi-select-merge

### auto-marker-dedup

### data-model
### persistence
### land-blocking
### match-resolution
### overlay-rendering
### creation-ui
### management-page
### create-zones-flow
### whereami-rework
### debug-wia
### menu-markers-normalization
### marker-pin
### marker-pin-tri-state
### icon
### 2-gate-simplification

### setting-markers

#### Todos
- [x] Replace standalone SettingsToggleRow with grouped card (inline toggle + SettingsExpander "Appearance")
- [x] Remove SHOW_PINNED from MarkerLayerState enum → binary HIDDEN/SHOW_ALL
- [x] Simplify drawGeometry in MarkerOverlay (always true in SHOW_ALL)
- [x] Add SettingsManager migration v5: SHOW_PINNED → SHOW_ALL
- [x] Build ✅

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — grouped card at ~line 2807
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — MarkerLayerState enum line 57
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — drawGeometry line 168
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — migration + version bump

### marker-card

#### Todos
- [x] List card: remove [type] [size/prox] line, remove divider, type icon in coordinateHeader
- [x] Viewing drawer: remove markerFormatText, add title+icon row, direction+divider+name+icon+desc+counter
- [x] Match cards: normalize to accent bar + direction + name+icon row, delete buildMatchText
- [x] Icon: use marker.icon only (user-set emoji), no geometry fallback, hidden when null

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — MarkerCardContent, coordinateHeader
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — ViewingContent, MatchRow

## Todos
- [ ] fix proximity of date points — rays hit/test all of them

## Implemented

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
- **Known issues** — see `xTrack/Markers/FEAT_HYD_Markers.md`

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
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
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/`

## Docs
- `xTrack/Markers/260904_FEAT_PLN_Markers_icon-pin-decoupling.md` — icon/pin decoupling + pin re-implementation plan (approved, pending implementation)
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
