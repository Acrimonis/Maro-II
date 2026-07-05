# BoatTrace — Populate Track Info

> **Feature:** BoatTrace | **Subfeature:** populate-track-info
> **Created:** 2026-07-05 07:44
> **Status:** planned

## Summary

During track recording, auto-populate track **title** (name) and **description** (comment) from silent `whereAmI()` calls at idle-stop positions. The title reflects the boat's destination; the description is a living bullet log recomputed from the full BoatMarker history — so newly-added markers retroactively name past stops.

## Design Decisions

### "Named" vs "nowhere"
A `whereAmI()` result is **named** if it contains ≥1 match whose `marker.origin != MarkerOrigin.IDLE_AUTO`. IDLE_AUTO markers (🕐 pins) are excluded — they don't represent real locations. `resolveAllMarkers()` already partitions user markers before auto markers (MarkerMatcher.kt:205), so filtering is trivial.

### No hard duration gate
Title logic is purely rank-based (longest idle wins), no 30-minute threshold. Any stop can become the title if it's the longest.

### Recompute, not append
The description is always a **pure function** of `track.boatMarkers` — rebuilt from scratch each time anything changes. This means:
- If a user creates a new marker mid-trip, past "nowhere" stops retroactively get named
- The description never goes stale relative to the current marker set
- No append/update logic — a single `recomputeDescription()` handles all cases

### Triggers for recomputation
| Trigger | What happens |
|---|---|
| Idle threshold reached (BoatMarker added) | Recompute description + poll title |
| Idle period ends (BoatMarker closed) | Recompute description + poll title |
| Title poll tick (every 3 min) | Recompute description + poll title |
| Markers added/edited (external flow) | Recompute description + poll title |

### whereAmI integration
TrackRecorder receives a `(LatLng) -> WhereAmIResult` lambda injected by MapScreen. This avoids coupling TrackRecorder to MarkersViewModel/CoastlineSpatialIndex directly. The lambda is synchronous (`whereAmISync` already exists).

### Marker change notification
TrackRecorder accepts a `Flow<Unit>` for external "markers changed" events. MapScreen provides a flow that emits when a marker is saved or edited. TrackRecorder observes this flow and triggers recomputation.

---

## Behaviour Specification

### 1. Description — recomputed from BoatMarker history

The `comment` field is fully rebuilt on every trigger:

```
  - stopped at [Calanque de l'Est, Cap d'Antibes] @ 14:22 for 1h 15min
  - stopped at [Plage de la Garoupe] @ 16:10 for 45min
  - stopped @ 17:30 for 5min
```

**Format:**
- Each BoatMarker = one bullet line prefixed with `  - `
- Zone names comma-separated within brackets (top 2 non-IDLE_AUTO matches)
- Time = `HH:mm` (24h) of idle start
- Open idle periods (no `endTimeMs`): omit `for Xmin` suffix
- Closed idle periods: append `for Xh Ymin` duration
- If `whereAmI` returns 0 non-IDLE_AUTO matches → no zone names: `  - stopped @ 14:22 for 5min`
- If only 1 zone matches → `  - stopped at [NameA] @ 14:22 for 5min`

### 2. Title — during recording (poll every 3 min)

1. Scan all `currentTrack.boatMarkers`, compute idle duration for each:
   - Closed: `(endTimeMs - startTimeMs) / 1000`
   - Open: `(now - startTimeMs) / 1000`
2. Find the one with the **maximum** duration
3. Run `whereAmI()` at `(boatLat, boatLon)`
4. If named (≥1 non-IDLE_AUTO match) → set `track.name = topMatch.marker.name`
5. If unnamed → keep existing title
6. If a different stop later becomes the longest, title updates accordingly

**Edge case: no BoatMarkers yet** → keep default auto-name.

### 3. Title — on track finalize

In `finalizeTrack()`, after computing the finalized track but before saving:

1. Collect all BoatMarkers with their idle durations
2. Run `whereAmI()` at each position, filter to **named only**
3. Sort named stops by idle duration descending
4. Format title:
   - **≥2 named stops:** `[longestName -> secondLongestName]`
   - **1 named stop:** `[longestName]`
   - **0 named stops:** keep default auto-name (`yyyy-MM-dd HH:mm`)

---

## Implementation Steps

### Step 1 — Add whereAmI lambda + markerChangeNotifier to TrackRecorder

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

Add constructor parameters:
```kotlin
class TrackRecorder(
    // ... existing params ...
    private val whereAmI: ((ykws.android.maro.data.model.LatLng) -> ykws.android.maro.spatial.WhereAmIResult)? = null,
    private val markerChangeNotifier: kotlinx.coroutines.flow.Flow<Unit>? = null
)
```

Both nullable — when null, the feature is dormant (backward compatible).

### Step 2 — Helper functions

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

```kotlin
/** Extract UserMarker from a WhereAmIMatch (local helper, mirrors MarkerMatcher.markerOf). */
private fun markerOf(match: ykws.android.maro.spatial.WhereAmIMatch): ykws.android.maro.data.model.markers.UserMarker = when (match) {
    is ykws.android.maro.spatial.WhereAmIMatch.ZoneMatch -> match.marker
    is ykws.android.maro.spatial.WhereAmIMatch.LineOfSightMatch -> match.marker
}

/** Extract top 2 non-IDLE_AUTO zone names from a WhereAmIResult. */
private fun topZoneNames(result: ykws.android.maro.spatial.WhereAmIResult): List<String> {
    return result.allMatches
        .filter { markerOf(it).origin != ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO }
        .take(2)
        .map { markerOf(it).name }
}

/** Check if a WhereAmIResult has at least one named (non-IDLE_AUTO) match. */
private fun isNamed(result: ykws.android.maro.spatial.WhereAmIResult): Boolean {
    return result.allMatches.any { markerOf(it).origin != ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO }
}

/** Extract top named location from WhereAmIResult, or null if unnamed. */
private fun topLocationName(result: ykws.android.maro.spatial.WhereAmIResult): String? {
    return result.allMatches
        .firstOrNull { markerOf(it).origin != ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO }
        ?.let { markerOf(it).name }
}

/** Human-readable duration: "3min" or "1h 15min". */
private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}min"
        h > 0 -> "${h}h"
        else -> "${m}min"
    }
}

/** Format a single description bullet line for one BoatMarker. */
private fun formatStopLine(
    bm: BoatMarker,
    zoneNames: List<String>,
    nowMs: Long
): String {
    val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(bm.startTimeMs))
    val zones = if (zoneNames.isEmpty()) "" else " at [${zoneNames.joinToString(", ")}]"
    val durSuffix = if (bm.endTimeMs != null) {
        val dur = (bm.endTimeMs - bm.startTimeMs) / 1000
        " for ${formatDuration(dur)}"
    } else {
        ""
    }
    return "  - stopped$zones @ $time$durSuffix"
}
```

### Step 3 — recomputeDescription()

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

```kotlin
/**
 * Rebuild the track description from the full BoatMarker history.
 * Called on every trigger: idle start, idle end, title poll, marker change.
 */
private fun recomputeDescription() {
    val wia = whereAmI ?: return
    val track = currentTrack ?: return
    val now = System.currentTimeMillis()

    val lines = track.boatMarkers.map { bm ->
        val result = wia(ykws.android.maro.data.model.LatLng(bm.boatLat, bm.boatLon))
        val zones = topZoneNames(result)
        formatStopLine(bm, zones, now)
    }

    val newComment = lines.joinToString("\n")
    if (track.comment != newComment) {
        currentTrack = track.copy(comment = newComment)
        scope?.launch { currentTrack?.let { repository.saveCheckpoint(it) } }
    }
}
```

### Step 4 — Triggers: call recomputeDescription() at each event

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

- In `startIdleTimer()` → after BoatMarker is appended to `currentTrack` → call `recomputeDescription()`
- In `closeOpenBoatMarker()` → after `endTimeMs` is set → call `recomputeDescription()`
- In `pollTitle()` (see Step 5) → call `recomputeDescription()` before title check

### Step 5 — Title polling + description recompute (every 3 min)

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

Add fields:
```kotlin
private var titlePollJob: Job? = null
```

Add methods:
```kotlin
private fun startPolling() {
    val wia = whereAmI ?: return
    titlePollJob?.cancel()
    titlePollJob = scope?.launch {
        while (isActive) {
            delay(180_000L) // 3 minutes
            recomputeDescription()
            pollTitle(wia)
        }
    }
}

private fun stopPolling() {
    titlePollJob?.cancel()
    titlePollJob = null
}

private fun pollTitle(wia: (ykws.android.maro.data.model.LatLng) -> ykws.android.maro.spatial.WhereAmIResult) {
    val track = currentTrack ?: return
    val markers = track.boatMarkers
    if (markers.isEmpty()) return

    val now = System.currentTimeMillis()
    val longest = markers.maxByOrNull { bm ->
        (bm.endTimeMs ?: now) - bm.startTimeMs
    } ?: return

    val result = wia(ykws.android.maro.data.model.LatLng(longest.boatLat, longest.boatLon))
    val name = topLocationName(result) ?: return  // unnamed → no update

    if (track.name != name) {
        currentTrack = track.copy(name = name)
        scope?.launch { currentTrack?.let { repository.saveCheckpoint(it) } }
        _uiState.update { it.copy(currentTrackName = name) }
    }
}
```

Wire `startPolling()` in `startTrack()` (state ON entry).
Wire `stopPolling()` in `finalizeTrack()` and `transitionTo(OFF)`.

### Step 6 — Marker change observer

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

In `startTrack()` (or init), launch a coroutine to observe `markerChangeNotifier`:

```kotlin
// In startTrack(), after startPolling():
markerChangeNotifier?.let { flow ->
    scope?.launch {
        flow.collect {
            if (state == TrackRecorderState.ON && currentTrack != null) {
                recomputeDescription()
                whereAmI?.let { pollTitle(it) }
            }
        }
    }
}
```

### Step 7 — Finalize title

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

In `finalizeTrack()`, after computing `finalized` but before `currentTrack = finalized`:

```kotlin
// Compute destination-based title
val finalName = computeFinalTitle(finalized)
val finalizedWithTitle = if (finalName != null) finalized.copy(name = finalName) else finalized
```

Add method:
```kotlin
private fun computeFinalTitle(track: Track): String? {
    val wia = whereAmI ?: return null
    if (track.boatMarkers.isEmpty()) return null

    data class NamedStop(val name: String, val durationSec: Long)
    val now = System.currentTimeMillis()
    val namedStops = track.boatMarkers
        .map { bm ->
            val dur = ((bm.endTimeMs ?: now) - bm.startTimeMs) / 1000
            val result = wia(ykws.android.maro.data.model.LatLng(bm.boatLat, bm.boatLon))
            val name = topLocationName(result)
            if (name != null) NamedStop(name, dur) else null
        }
        .filterNotNull()
        .sortedByDescending { it.durationSec }

    return when (namedStops.size) {
        0 -> null
        1 -> "[${namedStops[0].name}]"
        else -> "[${namedStops[0].name} -> ${namedStops[1].name}]"
    }
}
```

### Step 8 — Wire in TrackViewModel

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`

Add `whereAmI` and `markerChangeNotifier` passthrough params to the TrackRecorder construction chain.

### Step 9 — Wire in MapScreen

**File:** `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

1. Create a `MutableSharedFlow<Unit>` for marker change notifications:
   ```kotlin
   val markerChangeFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
   ```

2. Pass `markersViewModel::whereAmISync` and `markerChangeFlow` to TrackViewModel/Recorder.

3. After marker save/edit operations, emit to the flow:
   ```kotlin
   // After saveMarker() / updateMarker() / confirmAutoMarker():
   markerChangeFlow.tryEmit(Unit)
   ```

---

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` | +whereAmI param, +markerChangeNotifier param, +helper functions, +recomputeDescription, +title polling, +finalize title logic, +marker change observer |
| `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` | +whereAmI + markerChangeNotifier passthrough params |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Wire whereAmISync + markerChangeFlow → TrackRecorder; emit on marker save/edit |

## Key Files (reference)

- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — `name` (title), `comment` (description), `boatMarkers: List<BoatMarker>`
- `app/src/main/java/ykws/android/maro/data/track/BoatMarker.kt` — `boatLat`, `boatLon`, `startTimeMs`, `endTimeMs`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — state machine, idle timer, finalize
- `app/src/main/java/ykws/android/maro/data/track/IdleThresholdCallback.kt` — existing callback interface (unchanged)
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — `WhereAmIResult`, `WhereAmIMatch`, `resolveAllMarkers`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `whereAmISync()`
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — `saveCheckpoint()` (unchanged)

## Rules

- whereAmI lambda is nullable — when null, the feature is entirely dormant
- markerChangeNotifier is nullable — when null, only idle/poll events trigger recomputation
- Description replaces `comment` field during recording (auto-content overwrites user text)
- Title polling respects the 3-minute interval; also triggered immediately on idle-end and marker changes
- `markerOf()` local helper mirrors `MarkerMatcher.markerOf()` (private in MarkerMatcher, so duplicated)
- IDLE_AUTO filter uses `MarkerOrigin.IDLE_AUTO` — same constant used by `resolveAllMarkers` partition
- `recomputeDescription()` is idempotent — checks `track.comment != newComment` before writing
