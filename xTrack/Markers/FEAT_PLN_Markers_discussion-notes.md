# Markers — Implementation Plan Evaluation Discussion

> **Started:** 2026-06-22 | **Feature:** Markers

---

## Gap 1 — Plan Location Mismatch ✅ RESOLVED

**Decision:** Move plan file from `xTrack/ZoneTile/FEAT_PLN_ZoneTile_user-markers-design.md` → `xTrack/Markers/FEAT_PLN_Markers_user-markers-design.md`. Fix DSC doc reference accordingly.

**Action:** File move + DSC update — deferred to implementation.

---

## Gap 2 — Missing `segmentsIntersect` Utility ✅ RESOLVED

**Decision:** Add `fun segmentsIntersect(p1, p2, q1, q2): Boolean` to [`SpatialOperations`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt). The existing private implementation in [`SpeedZoneIndex`](app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt:393) provides the math — extract the planar-projection + cross-product logic into a reusable public function.

**Action:** Add to SpatialOperations in Phase A or B — deferred to implementation.

---

## Gap 3 — Performance Estimates Off by ~15× ✅ RESOLVED

**Finding:** Plan assumes ~1K coastline edges; actual is ~15K. But proximity ranges (pin=200m, circle max=radius×3=6000m, corridor max=width×3) act as a natural guardrail — only 1-3 markers are within reach at any boat position.

**Decision:** Add a pre-computed `bbox` (axis-aligned lat/lon bounding box) to `UserMarker`, computed on create/edit. `resolveAllMarkers()` gates on cheap bounding-box overlap (4 float comparisons per marker, no haversine) before calling expensive `closestUnblockedPoint()`. Brute-force over 15K edges × 1-3 markers = 15-50ms. No `CoastlineSpatialIndex` integration needed.

**Action:** Add `bbox` field to data model, bbox-gate logic to algorithm — deferred to implementation.

---

## Gap 4 — CoastlinePoint (Float) vs LatLng (Double) ✅ RESOLVED

**Finding:** Coastline data uses `CoastlinePoint(lat: Float, lon: Float)` while marker geometry uses `LatLng(latitude: Double, longitude: Double)`. The land-blocking engine must bridge the two.

**Decision:** Convert on the fly at the call site (`CoastlinePoint` → `LatLng` via `.toDouble()`). The `segmentsIntersect` function stays clean with `LatLng` parameters (usable by both marker and coastline code). Float→Double conversion is a single CPU instruction — zero measurable overhead at any scale.

**Action:** Trivial conversion in land-blocking loop — deferred to implementation.

---

## Gap 5 — Missing ViewModel Design ✅ RESOLVED

**Decision:** New `MarkersViewModel` (separate from `CoastlineViewModel`). Owns:

| StateFlow | Purpose |
|-----------|---------|
| `markers: List<UserMarker>` | Loaded from JSON, observed by map overlay + management page |
| `userMarkersVisible: Boolean` | FanLayout toggle, persisted via SettingsManager |
| `drawerState: MarkerDrawerState` | Hidden / Creating / Editing / MatchResult |
| `matchResult: TieredMatchResult?` | Result of "where am I?" query |

Injects `CoastlineViewModel.coastlineData` for land-blocking. Runs `resolveAllMarkers()` on `Dispatchers.Default`. Created via `ViewModelProvider.Factory`.

**File segregation:** All marker classes in own `.kt` files under a `markers/` package:
- `data/model/UserMarker.kt` — data class + sealed geometry
- `data/markers/UserMarkerRepository.kt` — JSON persistence
- `spatial/MarkerMatcher.kt` — resolveMatch / resolveAllMarkers / closestUnblockedPoint
- `ui/map/MarkersViewModel.kt` — StateFlow bridge
- `ui/map/MarkerOverlay.kt` — map rendering composables
- `ui/map/MarkerDrawer.kt` — creation/editing/result drawer

**Action:** Deferred to implementation.

---

## Gap 7 — Drawer Integration in Overlay Stack ✅ RESOLVED

**Decision:** Marker drawer sits in `MapContent` Box as sibling of Dashboard/FanLayout at the bottom zone. Uses `AnimatedVisibility` with `slideInVertically` (portrait) / `slideInHorizontally` (landscape). Reads `drawerState` from `MarkersViewModel`. Map remains pannable above the drawer area. No conflict with hamburger drawer (`ModalNavigationDrawer`) or settings overlay — those are at different hierarchy levels.

**Action:** Deferred to implementation.

---

## Gap 6 — Threading/Coroutine Strategy ✅ RESOLVED

**Decision:** Normalize on async — single consistent rule. All marker operations that touch disk or run computation go through:

```kotlin
viewModelScope.launch {
    val result = withContext(Dispatchers.Default) { /* work */ }
    _stateFlow.value = result  // thread-safe, on main thread
}
```

This covers JSON load/save (repository CRUD) and `resolveAllMarkers()` (land-blocking). No "small enough for main thread" exceptions — one path, predictable.

**Action:** Deferred to implementation.
