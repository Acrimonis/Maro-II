# Markers — Debug WhereAmI: Intersection Diagnosis

> **Feature:** Markers | **Subfeature:** debug-wia
> **Created:** 2026-06-25 | **Status:** Design
> **Invocation:** `#checkout feature/debug-wia` + `#sub debug whereaMi()`

---

## 1. Problem Statement

When the boat is at the intersection of several overlapping marker zones, `whereAmI()` returns only one result instead of all intersecting markers. The root cause is unknown — it could be BBox pre-filter, land-blocking, containment tree nesting, or a display issue.

## 2. Goal

Add a **debug mode** that instruments the entire `whereAmI()` pipeline with per-decision logcat output. The user taps any spot on the map → the system runs `whereAmI()` at that position and emits a structured diagnostic trace showing exactly which markers passed/failed each gate and why.

Additionally, dump all markers in the visible map viewport so the user can cross-reference what should have matched.

## 3. Root Cause Hypotheses

| # | Hypothesis | Where | Likelihood |
|---|-----------|-------|------------|
| H1 | BBox pre-filter rejects overlapping markers because their `bbox` doesn't cover the boat+1km fence | [`boatInExpandedBbox()`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:320) | Medium |
| H2 | Land-blocking rejects a zone-contained marker because `closestUnblockedPoint()` returns null for all sampled boundary points | [`closestUnblockedPoint()`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:213) | Low (zone check skips land) |
| H3 | Containment tree nests markers incorrectly — a marker's center is inside another zone, so it's made a child but depth-first traversal still outputs it; display might be confusing | [`resolveAllMarkers()` tree build](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:154) | Medium |
| H4 | `MAX_RESULTS` cap (8) + containment tree produces unexpected ordering | [`depthFirstLeavesFirst()`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:339) | Low |
| H5 | `isInsideGeometry()` for Corridor has edge case at intersection where boat is inside both geometrically but one fails | [`isInsideGeometry()`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:239) | Low |
| H6 | Drawer display (`MatchResultContent`) only renders first match due to UI bug | [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:414) | Low |

## 4. Design

### 4.1 Debug Toggle

- **State:** `MarkersViewModel._debugWiaEnabled: MutableStateFlow<Boolean>` — default `false`
- **Activation:** Long-press on the whereAmI crosshair/boat marker toggles debug mode
- **Visual indicator:** Crosshair changes to a debug icon (⊕ with yellow tint, or text badge "DEBUG") when active
- **Deactivation:** Long-press again, or auto-reset on drawer close

### 4.2 Diagnostic Logging in MarkerMatcher

Add a `debugResolveAllMarkers()` method (or a `debug: Boolean` parameter) to [`MarkerMatcher`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt):

```kotlin
fun debugResolveAllMarkers(
    boat: LatLng,
    markers: List<UserMarker>,
    spatialIndex: CoastlineSpatialIndex,
    config: ProximityConfig
): WhereAmIResult {
    Log.d(TAG, "══════════════════════════════════════")
    Log.d(TAG, "DEBUG WhereAmI at (%.6f, %.6f)", boat.latitude, boat.longitude)
    Log.d(TAG, "Total markers loaded: %d", markers.size)

    // 1. BBox pre-filter
    val inBbox = mutableListOf<UserMarker>()
    val outBbox = mutableListOf<String>()
    for (marker in markers) {
        if (boatInExpandedBbox(boat, marker, MAX_SEARCH_RADIUS_M)) {
            inBbox.add(marker)
        } else {
            val bbox = marker.bbox
            outBbox.add("${marker.name} [${marker.id.take(8)}] bbox=(${bbox.latSouth},${bbox.lonWest})-(${bbox.latNorth},${bbox.lonEast}) boat=(${boat.latitude},${boat.longitude})")
        }
    }
    Log.d(TAG, "BBox filter: %d IN, %d OUT", inBbox.size, outBbox.size)
    outBbox.forEach { Log.d(TAG, "  ✗ BBOX-OUT: %s", it) }

    // 2. Per-marker resolve
    val results = mutableListOf<WhereAmIMatch>()
    for (marker in inBbox) {
        Log.d(TAG, "── %s [%s] type=%s ──", marker.name, marker.id.take(8), marker.geometry::class.simpleName)
        val match = resolveMatch(boat, marker, spatialIndex, config)
        if (match != null) {
            results.add(match)
            when (match) {
                is WhereAmIMatch.ZoneMatch ->
                    Log.d(TAG, "  ✓ ZONE  size=%.0fm dist=%.0fm bearing=%.0f°", match.zoneSizeM, match.distanceToCenterM, match.bearingDeg)
                is WhereAmIMatch.ProximityMatch ->
                    Log.d(TAG, "  ✓ PROX  seaDist=%.0fm bearing=%.0f°", match.seaDistanceM, match.bearingDeg)
            }
        } else {
            Log.d(TAG, "  ✗ NO-MATCH — not in zone, proximity range exceeded or land-blocked")
        }
    }
    Log.d(TAG, "Resolved: %d matches from %d candidates", results.size, inBbox.size)

    if (results.isEmpty()) {
        Log.d(TAG, "══════════════════════════════════════")
        return WhereAmIResult(emptyList())
    }

    // 3. Containment tree
    // ... (same logic, logging which markers nest under which)
    Log.d(TAG, "Containment tree:")
    // Log each root with its children tree

    // 4. Final display list
    Log.d(TAG, "Display list (%d items):", display.size)
    display.forEachIndexed { i, match ->
        val name = markerOf(match).name
        Log.d(TAG, "  [%d] %s", i, name)
    }
    Log.d(TAG, "══════════════════════════════════════")

    return WhereAmIResult(display)
}
```

**Key: use `android.util.Log.d("WiaDebug", ...)` for all output.** This appears in `adb logcat -s WiaDebug`.

### 4.3 Map-Tap Handler for Debug Mode

When debug mode is active, tapping the map → runs `whereAmI()` at the tapped position.

**Implementation approach:** Extend the existing [`MapEventsOverlay`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:286) in `MarkerOverlay.kt` to also detect taps when debug mode is active. The overlay already handles `singleTapConfirmedHelper` — add a branch:

```kotlin
override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
    if (p == null) return false
    
    // Debug mode: any tap → whereAmI at tap position
    if (debugWiaEnabled) {
        onDebugTap(LatLng(p.latitude, p.longitude))
        return true
    }
    
    // Existing marker tap logic...
}
```

**Callback chain:** `MarkerOverlay` → new `onDebugTap: (LatLng) -> Unit` parameter → `MapScreen` → `markersViewModel.debugWhereAmI(tapPos)`.

### 4.4 Visible-Map Marker Dump

Add to `MarkersViewModel`:

```kotlin
fun dumpVisibleMarkers(viewport: BoundingBox) {
    val visible = _markers.value.filter { marker ->
        val bbox = marker.bbox
        // Simple overlap check
        bbox.latNorth >= viewport.latSouth && bbox.latSouth <= viewport.latNorth &&
        bbox.lonEast >= viewport.lonWest && bbox.lonWest <= viewport.lonEast
    }
    Log.d("WiaDebug", "── Visible markers in viewport ──")
    Log.d("WiaDebug", "Viewport: (%.4f,%.4f)-(%.4f,%.4f)", viewport.latSouth, viewport.lonWest, viewport.latNorth, viewport.lonEast)
    visible.forEach { marker ->
        val center = zoneCenterOf(marker.geometry) // reuse MarkerMatcher utility
        Log.d("WiaDebug", "  %s [%s] type=%s center=(%.5f,%.5f)", marker.name, marker.id.take(8), marker.geometry::class.simpleName, center.latitude, center.longitude)
    }
    Log.d("WiaDebug", "Total visible: %d / %d loaded", visible.size, _markers.value.size)
}
```

This gets the viewport from `MapScreen`'s current `mapCenter` + `zoomLevel` → projected bounds.

### 4.5 Logcat Query

After tapping in debug mode, the user runs:
```bash
adb logcat -s WiaDebug -d
```

Or for real-time streaming:
```bash
adb logcat -s WiaDebug
```

### 4.6 File Changes Summary

| File | Change |
|------|--------|
| [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) | Add `debugResolveAllMarkers()` with structured Log.d calls; make `zoneCenterOf()` internal (or copy) for visibility dump |
| [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | Add `_debugWiaEnabled` StateFlow, `toggleDebugWia()`, `debugWhereAmI(pos)`, `dumpVisibleMarkers(viewport)` |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) | Add `debugWiaEnabled` + `onDebugTap` params; branch in tap handler |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Pass `debugWiaEnabled`, `onDebugTap` to `MarkerOverlay`; wire `dumpVisibleMarkers` on debug tap; change crosshair icon when debug active |
| [`CenterMarkerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1947) | Long-press handler toggles debug; visual indicator when active |

## 5. Usage Flow

```
1. User long-presses boat marker → debug mode ON (crosshair turns yellow ⊕)
2. User taps a spot on the map (the intersection point)
3. System runs debugResolveAllMarkers() + dumpVisibleMarkers()
4. Drawer shows normal match result
5. User runs: adb logcat -s WiaDebug -d
6. User reads why each marker passed/failed each gate
7. Long-press again → debug mode OFF
```

## 6. Non-Goals

- Auto-refresh timing (deferred per existing whereami-rework plan)
- Persistent debug setting across app restarts
- UI overlay showing debug info on-map (logcat only)
- Fixing the bug itself — this is purely diagnostic instrumentation

## 7. Key Files

| File | Role |
|------|------|
| [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) | Core resolution logic — add debug variant |
| [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | ViewModel — debug state + orchestration |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) | MapEventsOverlay — debug tap intercept |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Screen — wire debug callbacks, viewport, crosshair |
| [`CoastlineSpatialIndex.kt`](app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt) | Land-blocking index (read-only, no changes) |
