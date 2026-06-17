# Fix: 300m Zone Auto-Show Stutter

## Problem

When the 300m zone layer auto-shows during navigation, the map stutters/freezes. This happens because the current overlay-diffing mechanism in [`CoastlineMapView`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1135) triggers a **full rebuild of all 6 OSMdroid overlay layers** whenever any dependency changes — including a visibility-only toggle.

### Root Cause Chain

1. Auto-show fires → [`settingsManager.update { it.copy(zone300Visible = true) }`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:506)
2. [`visibleZone300`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:717) changes from `null` → `zone300` data object (because `if (visible) data else null`)
3. `overlayKey` identity changes → `update` block fires
4. [`mapView.overlays.removeAll { ... }`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1200) — removes **every** `Polyline`, `Polygon`, and `GroundOverlay`
5. Re-adds all 6 layers from scratch: depth raster (8 GroundOverlay bands), isobaths, regulated zones, zone300, coastline
6. [`mapView.invalidate()`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1212) — full osmdroid repaint

**The data hasn't changed** — only its visibility flag. All that remove+rebuild work is wasted.

## Solution: Isolated Zone300 Toggle

Keep zone300 overlays persistent and toggle them in-place, bypassing the full overlay rebuild entirely.

### Changes Required

All in [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

#### Step 1: Decouple `visibleZone300` from `overlayKey`

**File:** [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

In [`CoastlineMapView`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1109):
- Add a `zone300Visible: Boolean` parameter (separate from the zone300 data)
- Remove `zone300` from the `overlayKey` dependencies — zone300 data changes should NOT trigger blanket overlay rebuilds
- Pass `zone300Visible` from `MapContent` alongside `visibleZone300`

**Call site** (in `MapContent`, line 729):
```kotlin
CoastlineMapView(
    ...
    zone300 = visibleZone300,          // the data (may be null if not loaded yet)
    zone300Visible = appSettings.zone300Visible,  // visibility toggle
    ...
)
```

#### Step 2: Track zone300 overlays with a persistent reference

Add a `remember { mutableListOf<Any>() }` to hold references to the zone300 overlay objects (Polygons + Polylines). This list persists across recompositions.

#### Step 3: Draw zone300 overlays once in factory

In the `factory` block, draw zone300 overlays **unconditionally** (if data is available) into the tracked list instead of directly onto `mapView.overlays`:

```kotlin
val zone300FillList = remember { mutableListOf<Polygon>() }
val zone300LineList = remember { mutableListOf<Polyline>() }
```

Modify `drawZone300` to accept an optional overlay sink parameter, or create a wrapper that adds to both the list and `mapView.overlays`.

#### Step 4: Toggle visibility without rebuild

In the `update` block, instead of the blanket `removeAll`+rebuild, handle zone300 visibility separately:

```kotlin
// Inside update block — runs on overlayKey change (all other layers)
if (lastOverlayKey.value !== overlayKey) {
    lastOverlayKey.value = overlayKey
    // Remove all overlays EXCEPT zone300 ones (keep references)
    mapView.overlays.removeAll {
        (it is Polyline || it is Polygon || it is GroundOverlay) &&
        it !in zone300FillList && it !in zone300LineList &&
        (it !is Polyline || it.title != "zoneAheadOuter") &&
        (it !is Polygon || it.title != "zoneAheadCone")
    }
    // Rebuild non-zone300 layers
    drawDepthMap(...)
    drawLowDepthWarning(...)
    drawIsobaths(...)
    drawRegulatedZones(...)
    drawCoastline(...)
    mapView.invalidate()
}

// Separate: toggle zone300 visibility without triggering full rebuild
if (zone300Visible != lastZone300Visible) {
    lastZone300Visible = zone300Visible
    zone300FillList.forEach { it.isVisible = zone300Visible }
    zone300LineList.forEach { it.isVisible = zone300Visible }
    mapView.invalidate()  // only invalidate, no structural changes
}
```

#### Step 5: Handle first-time zone300 data arrival

When zone300 data loads for the first time (data was null, now non-null), create the overlays in the tracked lists. This can happen in the `update` block:

```kotlin
// First-time zone300 data arrival
if (zone300 != null && zone300FillList.isEmpty()) {
    drawZone300IntoList(mapView, zone300, zoomLevel, zone300FillList, zone300LineList)
    // Apply current visibility
    zone300FillList.forEach { it.isVisible = zone300Visible }
    zone300LineList.forEach { it.isVisible = zone300Visible }
    mapView.invalidate()
}
```

### Edge Cases

| Case | Handling |
|------|----------|
| Zone data not loaded yet at factory time | Lazy: created on first `update` when data arrives |
| Zoom crosses `ZONE_MIN_ZOOM` threshold | Zoom change already triggers full overlay rebuild (zoom is in overlayKey via `zoneVisible`/`depthVisible`/etc.) — zone300 will be recreated then |
| Manual toggle (layer fan) | Same path as auto-show — `zone300Visible` changes, tracked-list toggle fires, no full rebuild |
| Zone data reloads (e.g., settings change) | If zone300 data reference changes, clear and recreate in tracked lists |

### Performance Gain

| Before | After |
|--------|-------|
| `removeAll` ~hundreds of overlay objects | No structural change to overlay list |
| Re-add 6 layers (depth×8 bands + isobaths×N + regulated×M + zone300×K + coastline×P) | Only toggle `.isVisible` on K zone300 overlays |
| Full `invalidate()` repaint | Minimal invalidate (same overlays, just paint flag change) |

### Future Extension

If the same stutter appears for other layer toggles (regulated zones, depth raster, low-depth warning), the same pattern can be applied per-layer. For now, zone300 is the only layer that auto-shows during active navigation, so it's the priority.

### Files Changed

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `CoastlineMapView` composable + `drawZone300` wrapper
