---
name: Markers
status: active
created: 2026-06-22 11:52
modified: 2026-06-30 16:35
active_subfeature: 2-gate-simplification
---

# Feature: Markers

**Description:**
User-defined markers on the map — Pin, Circle, and Corridor geometries. Sea-distance-gated proximity matching (land-blocking via tangent-guided angular shadow projection + 10m grazing tolerance). On-demand "where am I?" query via boat marker tap. Step-by-step wizard for creation/editing (replaces dashboard). Viewing drawer (card layout) + match result display. Management page with swipe-to-delete (track list paradigm). OSMdroid native overlays for rendering (Polyline + Polygon + Marker). Tri-state layer toggle (HIDDEN/SHOW_ALL/SHOW_PINNED). POI emoji icons on pinned markers.

## Subfeatures
14/14 complete. One active issue: whereami-gaps (~50% match rate on Sainte Marguerite corridor).

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

## Implemented

### Data Model & Persistence
- `UserMarker` with `MarkerGeometry` sealed class (Pin/Circle/Corridor), `bbox` pre-computed on create/edit
- `UserMarkerRepository` — JSON load/save in internal storage, CRUD operations
- Proximity override per marker (null → default formula: pin=200m, circle=radius×3, corridor=width×3)
- `createdAtEpochMs` timestamp, `icon: String?` (non-null → auto-pinned), 16-color `MarkerColors` palette

### Match Resolution
- `MarkerMatcher.resolveAllMarkers()` — 1km search fence, BBox pre-filter, on-demand only (no 1Hz pipeline)
- `WhereAmIMatch` sealed class (ZoneMatch/ProximityMatch) with depth-first leaves-first traversal, capped at 8
- Tangent-guided angular shadow projection via `CoastlineSpatialIndex.segmentIntersectsLand()` with 10m grazing tolerance
- Boat marker tap → `whereAmI()` → drawer opens in MatchResult mode

### Overlay Rendering
- OSMdroid native overlays: Pin (filled-circle `Marker`), Circle (`Polygon` fill + `Polyline` ring + center `Marker`), Corridor (`Polygon` fill pill + parallel `Polyline`s + centerline at 50% alpha + semi-circle caps + p1/p2 `Marker`s)
- Area-based marker tap via `MapEventsOverlay` with proximity-zone hit-test; returns list for overlapping markers
- Match result highlighting: matched markers 1.67× stroke, unmatched dimmed to 30% alpha; selected marker 2.5× stroke
- POI emoji icons at geometry positions for pinned markers (12 icons + default red 📍)
- Tri-state layer toggle via `MarkerLayerState` (HIDDEN → SHOW_ALL → SHOW_PINNED → HIDDEN)

### Creation & Editing
- `WizardDrawer` — step-by-step wizard replacing single-form drawer: TypeSelect → Position → [PositionP2] → [Radius] → Proximity → Title → Description
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

### whereami-fixes (2026-06-30)
- Direct-line fast path: `closestUnblockedPoint()` checks geometrically-closest boundary point → clear LOS → return immediately, skip angular analysis
- `closestGeometricBoundaryPoint()` helper — closest boundary point ignoring land
- Pin `bestBoundaryPoint` now checks unblocked intervals (previously always returned position regardless of land)
- `proximityRange()` safe fallback: `proximityOverrideM ?: defaultFormula()` (was force-unwrap `!!`)
- Bearing direction fixed: `initialBearing(boat, marker)` (was reversed)
- Removed hardcoded debug: "Sainte Marguerite " marker dump + bearing 80-87° gate
- Edge line sampling: 5 bearings per unblocked interval (was midpoint only)
- Coroutine cancellation on rapid whereAmI() taps
- **Build:** compileDebugKotlin ✅

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
- `xTrack/Markers/FEAT_PLN_Markers_user-markers-design.md` — original design plan (all phases ✅)
- `xTrack/Markers/FEAT_PLN_Markers_create-zones-flow.md` — wizard creation flow design
- `xTrack/Markers/FEAT_PLN_Markers_whereami-rework.md` — whereAmI rework design (implemented)
- `xTrack/Markers/FEAT_PLN_Markers_debug-wia.md` — debug instrumentation design (implemented)
- `xTrack/Markers/FEAT_PLN_Markers_whereami-gaps.md` — current angular shadow over-blocking analysis
- `xTrack/Markers/FEAT_PLN_Markers_icon.md` — POI icon system design (implemented)
- `xTrack/Markers/FEAT_PLN_Markers_icon-fixes.md` — icon bug-fix plan (partial: management icon picker ✅, duplicate imports ❌)
- `xTrack/Markers/FEAT_PLN_Markers_marker-pin.md` — pin toggle design (implemented)
- `xTrack/Markers/FEAT_PLN_Markers_marker-pin-tri-state.md` — tri-state layer toggle design (implemented)
- `xTrack/Markers/FEAT_PLN_Markers_area-tap-and-wizard-buttons.md` — area tap + wizard buttons + corridor caps + color settings (area tap ✅, buttons ✅, caps ✅, color settings ❌)
- `xTrack/Markers/FEAT_PLN_Markers_next-session-ui-polish.md` — UI polish plan (format ❌, color picker ✅, edit icon ✅, viewing drawer ✅)
