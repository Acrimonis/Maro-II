---
name: Markers
status: active
created: 2026-06-22 11:52
modified: 2026-07-14 08:50
active_subfeature: auto-marker-dedup
---

# Feature: Markers

**Description:**
User-defined markers on the map — Pin, Circle, and Corridor geometries. Line-of-sight matching with proximity-zone pre-filter (land-blocking via coastline spatial index + 10m grazing tolerance). On-demand "where am I?" query via boat marker tap. Step-by-step wizard for creation/editing. Viewing drawer (card layout) + match result display. Management page with swipe-to-delete. OSMdroid native overlays. Tri-state layer toggle (HIDDEN/SHOW_ALL/SHOW_PINNED). POI emoji icons on pinned markers.

## Subfeatures
15/15 complete.

### auto-marker-dedup  [x]

### data-model  [x]
### persistence  [x]
### land-blocking  [x]
### match-resolution  [x]
### overlay-rendering  [x]
### creation-ui  [x]
### management-page  [x]
### create-zones-flow  [x]
### whereami-rework  [x]
### debug-wia  [x]
### menu-markers-normalization  [x]
### marker-pin  [x]
### marker-pin-tri-state  [x]
### icon  [x]
### 2-gate-simplification  [x]

### setting-markers  [x]

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

### marker-card  [x]

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

### marker-card (2026-07-03)
- List card: removed `markerFormatText()` line + function, removed divider. Type icon in `coordinateHeader` prefix. Card: accent bar → icon+coords+controls → name → description.
- Viewing drawer: removed `markerFormatText()`. Card: direction → divider → name+icon → description → counter. Icon always visible (user-set emoji only), counter on desc row (hasMultiple).
- Match cards: normalized `MatchRow` — accent bar + direction (LineOfSightMatch) + name+icon row. Deleted `buildMatchText()`. Icon = `marker.icon` only, hidden when null.
- 2 files. Build: ✅

### wizard-cleanup-race (2026-07-02)
- Moved `_wizardStep`/`editingMarkerId`/`_selectedMarkerId` cleanup from `wizardFinish()` into `saveMarker()`/`updateMarker()` coroutines
- Fixes race: wizard state was nulled synchronously while IO save was still in-flight, causing markers to disappear until app restart
- Build: ✅

### auto-marker-proximity (2026-07-02)
- 🕐 auto idle markers now use `AppConfig.boatMarkerAutoMarkerProximityM` (300m default) instead of `null` in `addTempAutoMarker()`
- Defensive fallback in `proximityRange()`: `IDLE_AUTO` markers with `null` proximity fall back to 300m (handles existing on-disk markers)
- `null` → `Double.MAX_VALUE` meant every auto marker matched `whereAmI()` regardless of distance
- New config key `track.boatMarker.autoMarker.proximityM=300` in `maro.properties`
- Build: ✅

### Data Model & Persistence
- `UserMarker` with `MarkerGeometry` sealed class (Pin/Circle/Corridor), `bbox` pre-computed on create/edit
- `UserMarkerRepository` — JSON load/save in internal storage, CRUD operations
- `proximityOverrideM` always set by creation wizard (Pin→200m, Circle→radius×3, Corridor→width×3); matching engine reads stored value directly, no formula defaults
- `createdAtEpochMs` timestamp, `icon: String?` (non-null → auto-pinned), 16-color `MarkerColors` palette

### Match Resolution — Two-Gate Model
1. **Zone check** — boat inside geometry → `ZoneMatch` (no line-of-sight test)
2. **Proximity pre-filter** — `minBoundaryDist > proximityOverrideM` → skip (avoids testing far-away markers)
3. **Line-of-sight** — `closestUnblockedPoint()` samples boundary points (from zone center, guaranteeing tangent coverage) → `LineOfSightMatch` if clear sea path exists. No distance cap — proximity gate removed.
- `WhereAmIMatch` sealed class: `ZoneMatch` / `LineOfSightMatch` (renamed from ProximityMatch)
- Pin markers: proximityOverrideM distance gate only, no land check
- Depth-first leaves-first traversal, capped at 8; `sortScore` uses stored proximityOverrideM for display ordering
- Boat marker tap → `whereAmI()` → drawer opens in MatchResult mode

### Boundary Point Sampling
- Points generated directly on geometry boundary via `pointAlongBearing` — never via ray-intersection from boat
- Circle: 16 points on visible arc from center (`90°−asin(r/d)` half-arc); tangents guaranteed at samples 0/15
- Corridor: 16 points per end-cap (full 360°, 22.5° spacing) + 5 per edge line (25% spacing)
- `circlePointAtBearing` removed (ray-intersection dropped tangent samples due to planar floating-point)

### Overlay Rendering
- OSMdroid native overlays: Pin (filled-circle `Marker`), Circle (`Polygon` fill + `Polyline` ring + center `Marker`), Corridor (`Polygon` fill pill + parallel `Polyline`s + centerline at 50% alpha + semi-circle caps + p1/p2 `Marker`s)
- Area-based marker tap via `MapEventsOverlay` with proximity-zone hit-test; returns list for overlapping markers
- Match result highlighting: matched markers 1.67× stroke, unmatched dimmed to 30% alpha; selected marker 2.5× stroke
- POI emoji icons at geometry positions for pinned markers (12 icons + default red 📍)
- Tri-state layer toggle via `MarkerLayerState` (HIDDEN → SHOW_ALL → SHOW_PINNED → HIDDEN)

### Creation & Editing
- `WizardDrawer` — step-by-step wizard: TypeSelect → Position → [PositionP2] → [Radius] → Proximity → Title → Description
- `WizardButtonRow` — text pill buttons (Previous/Next/Finish) with `AnimatedContent` slide transitions
- Runtime keyboard mode toggle: `adjustNothing` on text steps, restore `adjustPan` on exit
- Post-save undo via Snackbar (3s, soft-delete pattern)
- Icon picker embedded in Title wizard step + standalone `IconPickerDialog`

### Viewing & Management
- `MarkerDrawer` — Viewing mode (card layout with accent bar, direction/distance from boat, icon/edit/delete actions) + MatchResult mode
- `MarkerManagementOverlay` — full-screen list with swipe-to-delete + inline snackbar undo, multi-marker Previous/Next navigation
- Empty state: "No markers yet" + "Create First Marker" CTA
- Live marker count in hamburger menu + management page heading

### Color System
- `COLOR_CONFIRMED` = `semantic.info` (blue #1565C0), `COLOR_UNCONFIRMED` = `semantic.caution` (amber #EF6C00)
- Proximity preview: cyan at 50% alpha (stroke) + 10% alpha (fill)
- Corridor centerline: 50% alpha of marker color

### Known Issues
See [`FEAT_HYD_Markers.md`](xTrack/Markers/FEAT_HYD_Markers.md) for current bug list.

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
