---
name: Markers
subfeature: auto-marker-dedup
created: 2026-07-14 08:44
status: implemented
---

# Auto-Marker Proximity Dedup

**Problem:** During track recording, GPS jitter causes idle→moving→idle transitions. Each cycle creates a new 🕐 auto-marker at nearly the same position. Over an anchorage session, multiple identical markers accumulate within ~50m.

**Root cause:** [`addTempAutoMarker()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:642) creates a new marker unconditionally on every `IdlePeriodStarted` event, with no proximity check against existing auto-markers.

**Strategy:** Proximity dedup at creation time — before creating a new 🕐 pin, scan existing `IDLE_AUTO` markers within a configurable radius.

## Design

### Dedup Logic (in `addTempAutoMarker`)

```
addTempAutoMarker(lat, lon, startTimeMs):
    nearest = find nearest IDLE_AUTO marker within dedupRadiusM

    if nearest != null && nearest.confirmed == false:
        // Reuse the existing temp marker — update its position
        repo.update(nearest.copy(lat=lat, lon=lon))
        return nearest.id

    if nearest != null && nearest.confirmed == true:
        // Already have a confirmed auto-marker here — skip
        return ""  // empty string signals "no new marker"

    // No nearby auto-marker → create normally
    ...
```

### Config

| Key | Default | Description |
|-----|---------|-------------|
| `track.boatMarker.autoMarker.dedupRadiusM` | `50` | Max distance (meters) between auto-markers to consider them duplicates |

### Caller Handling (`MapScreen.kt`)

```kotlin
// IdlePeriodStarted handler (line 659-663)
val markerId = markersViewModel.addTempAutoMarker(...)
if (markerId.isNotEmpty()) {
    pendingAutoMarkerId = markerId
    trackViewModel.setActiveSessionAutoMarkerId(markerId)
}
// else: skip — existing confirmed marker already covers this spot
```

### Distance Calculation

Use `SpatialOperations.haversine()` — already available in `MarkersViewModel` via import. O(N) scan over `_allMarkers.value` filtered to `origin == IDLE_AUTO`. Typical marker count is <20, so performance is negligible.

## Files

| File | Change |
|------|--------|
| `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` | `addTempAutoMarker()` — proximity scan before creation, return type changes from `String` to `String` (empty = skip) |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | `IdlePeriodStarted` handler — guard `setActiveSessionAutoMarkerId` on non-empty return |
| `app/src/main/assets/maro.properties` | New key `track.boatMarker.autoMarker.dedupRadiusM=50` |
| `app/src/main/java/ykws/android/maro/config/AppConfig.kt` | Load `boatMarkerAutoMarkerDedupRadiusM` |

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Boat drifts 30m within same anchorage | Nearest existing unconfirmed marker updated to new position |
| Boat re-anchors at same spot after moving away | Nearest existing confirmed marker found → skip (no duplicate) |
| Boat moves 200m to new anchorage | No marker within 50m → new 🕐 created (correct) |
| First idle period of recording | No existing IDLE_AUTO markers → new 🕐 created (correct) |
| App killed during idle, restarted | `keepable=false` markers cleaned up on startup (existing behavior). New idle period starts fresh. |
| Multiple idle periods at <50m, all confirmed | First period creates marker A (confirmed). Second period sees A within 50m → skips. Correct: one marker per anchorage. |

## Non-Goals

- No post-hoc merge of existing duplicates (user can manually delete)
- No hysteresis/debounce on `isStopped` transitions (unnecessary blast radius)
- No cross-session dedup (markers from different recording sessions at same anchorage are intentionally distinct)
