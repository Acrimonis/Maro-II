# BoatTrace — Populate Track Info

> **Feature:** BoatTrace | **Subfeature:** populate-track-info
> **Created:** 2026-07-05 07:44
> **Status:** implemented

## Summary

During track recording, auto-populate track **title** (name) and **description** (comment) from silent `whereAmI()` calls at idle-stop positions. The title reflects the boat's destination; the description is a living bullet log recomputed from the full BoatMarker history — so newly-added markers retroactively name past stops. MANUAL BoatMarkers (explicit user taps) have absolute title priority.

## Design Decisions

### "Named" vs "nowhere"
A `whereAmI()` result is **named** if it contains ≥1 match whose `marker.origin != MarkerOrigin.IDLE_AUTO`. IDLE_AUTO markers (🕐 pins) are excluded — they don't represent real locations. `resolveAllMarkers()` already partitions user markers before auto markers (MarkerMatcher.kt:205).

### Title priority tiers (highest to lowest)

| Tier | Condition | Title behaviour |
|------|-----------|----------------|
| 1. 🤿 Diving | Any BoatMarker where `whereAmI()` matches a UserMarker with `pinned=true` AND `icon == "🤿"` (`\uD83E\uDD3F`) | That location name wins unconditionally |
| 2. MANUAL | BoatMarker with `trigger == MANUAL` | Most recent MANUAL marker wins (if no 🤿 match) |
| 3. IDLE duration | BoatMarker with `trigger == IDLE` | Longest idle duration wins (if no 🤿 or MANUAL) |

The 🤿 icon is the only diving-priority icon from `ICON_SET` (IconPickerDialog.kt:29). The check is: `whereAmI` result contains ≥1 match where `marker.pinned && marker.icon == "\uD83E\uDD3F"`.

### IDLE vs MANUAL BoatMarkers

| Aspect | IDLE | MANUAL |
|--------|------|--------|
| Trigger | Auto-detected stillness (60s threshold) | User taps boat-marker button |
| Description zone names | `whereAmI()` at position → top 2 non-IDLE_AUTO names | Pre-captured `MarkerSnapshot` names (filtered to non-IDLE_AUTO) |
| Title priority | Lowest — longest duration wins (after 🤿 and MANUAL) | Middle — most recent MANUAL marker wins (after 🤿) |
| `endTimeMs` | Set when boat moves | Always `null` (instant snapshot, no duration) |

### Recompute, not append
The description is always a **pure function** of `track.boatMarkers` — rebuilt from scratch each time anything changes. New markers retroactively name past stops.

### Triggers for recomputation

| Trigger | What happens |
|---|---|
| Idle threshold reached (BoatMarker added) | Recompute description + poll title |
| Idle period ends (BoatMarker closed) | Recompute description + poll title |
| MANUAL BoatMarker added | Recompute description + poll title |
| Title poll tick (every 3 min) | Recompute description + poll title |
| Markers added/edited (external flow) | Recompute description + poll title |

### whereAmI integration
TrackRecorder receives a `(LatLng) -> WhereAmIResult` lambda injected by MapScreen. Synchronous (`whereAmISync` already exists). Nullable — when null, the feature is dormant.

### Marker change notification
TrackRecorder accepts a `Flow<Unit>` for external "markers changed" events. MapScreen provides a flow that emits when a marker is saved or edited.

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
- IDLE: zone names from `whereAmI()` at position (top 2 non-IDLE_AUTO, whereAmI sort order)
- MANUAL: zone names from pre-captured `MarkerSnapshot` names (top 2 non-IDLE_AUTO)
- Time = `HH:mm` (24h) of stop start
- Open idle periods (no `endTimeMs`): omit `for Xmin` suffix
- Closed idle periods: append `for Xh Ymin` duration
- If 0 non-IDLE_AUTO matches → no zone names: `  - stopped @ 14:22 for 5min`
- If only 1 zone matches → `  - stopped at [NameA] @ 14:22 for 5min`
- MANUAL markers always have `endTimeMs = null` → never show duration suffix

### 2. Title — during recording (poll every 3 min)

Priority: 🤿 diving > MANUAL > IDLE duration.

1. Scan all BoatMarkers. For each, run `whereAmI()` at position.
2. If any whereAmI result contains a pinned 🤿 marker → use that location name as title.
3. Else if ≥1 MANUAL BoatMarker exists → use most recent MANUAL's whereAmI name.
4. Else → use longest IDLE stop's whereAmI name.
5. If unnamed at any tier → fall through to next tier.
6. If no tier produces a name → keep existing title.

**Edge case: no BoatMarkers yet** → keep default auto-name.

### 3. Title — on track finalize

In `finalizeTrack()`, after computing the finalized track but before saving:

Priority: 🤿 diving > MANUAL > IDLE duration.

1. Collect all BoatMarkers, run `whereAmI()` at each, classify:
   - **Diving:** whereAmI contains pinned 🤿 marker
   - **MANUAL:** trigger == MANUAL, not diving
   - **IDLE:** trigger == IDLE, not diving
2. Sort each group by duration desc (MANUAL by recency = now - startTimeMs)
3. Build title from highest-priority stops first:

| Diving | MANUAL | IDLE | Format |
|--------|--------|------|--------|
| ≥2 | - | - | `[🤿1 -> 🤿2]` |
| 1 | ≥1 | - | `[🤿 -> manualName]` |
| 1 | 0 | ≥1 named | `[🤿 -> longestIdleName]` |
| 1 | 0 | 0 | `[🤿]` |
| 0 | ≥2 | - | `[M1 -> M2]` |
| 0 | 1 | ≥1 named | `[M -> longestIdle]` |
| 0 | 1 | 0 | `[M]` |
| 0 | 0 | ≥2 named | `[longestIdle -> secondLongestIdle]` |
| 0 | 0 | 1 named | `[name]` |
| 0 | 0 | 0 | default auto-name |

---

### 4. Error reporting

All three new methods (`recomputeDescription`, `pollTitle`, `computeFinalTitle`) are wrapped in try-catch. On failure, the exception message is surfaced via `TrackRecorderUiState.infoError`. MapScreen renders the existing [`ErrorOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2072) at `BottomCenter` with a Dismiss action.

**TrackRecorderUiState** — add field:
```kotlin
val infoError: String? = null
```

**Error handling pattern:**
```kotlin
private fun recomputeDescription() {
    try {
        // ... existing logic ...
    } catch (e: Exception) {
        Log.w(TAG, "recomputeDescription failed", e)
        _uiState.update { it.copy(infoError = "Track info: ${e.message}") }
    }
}
```

**MapScreen** — in the error overlay slot (line 1819), add:
```kotlin
val trackInfoError = trackRecorderState.infoError
if (trackInfoError != null) {
    ErrorOverlay(
        message = trackInfoError,
        onRetry = { /* dismiss — clears the error */ }
    )
}
```

A `LaunchedEffect(trackInfoError)` auto-clears the error after 8 seconds, or the user taps Dismiss.

---

## Implementation Steps

### Step 1 — Add whereAmI lambda + markerChangeNotifier to TrackRecorder

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

```kotlin
class TrackRecorder(
    // ... existing params ...
    private val whereAmI: ((ykws.android.maro.data.model.LatLng) -> ykws.android.maro.spatial.WhereAmIResult)? = null,
    private val markerChangeNotifier: kotlinx.coroutines.flow.Flow<Unit>? = null
)
```

### Step 2 — Fix BoatMarker startTimeMs (existing code bug)

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

In `startIdleTimer()`, the `startMs` parameter is already passed but unused. Currently:
```kotlin
val bm = BoatMarker(startTimeMs = System.currentTimeMillis(), ...)  // BUG: timer fire time
```

Fix:
```kotlin
val bm = BoatMarker(startTimeMs = startMs, ...)  // actual idle start time
```

This is a one-line change. Makes the BoatMarker semantically correct and ensures description times match reality.

### Step 3 — Helper functions

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

```kotlin
/** Extract UserMarker from a WhereAmIMatch (local helper, mirrors MarkerMatcher.markerOf). */
private fun markerOf(match: ykws.android.maro.spatial.WhereAmIMatch): ykws.android.maro.data.model.markers.UserMarker = when (match) {
    is ykws.android.maro.spatial.WhereAmIMatch.ZoneMatch -> match.marker
    is ykws.android.maro.spatial.WhereAmIMatch.LineOfSightMatch -> match.marker
}

/** Extract top 2 non-IDLE_AUTO zone names from a WhereAmIResult, in whereAmI sort order. */
private fun topZoneNames(result: ykws.android.maro.spatial.WhereAmIResult): List<String> {
    return result.allMatches
        .filter { markerOf(it).origin != ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO }
        .take(2)
        .map { markerOf(it).name }
}

/** Extract top 2 non-IDLE_AUTO names from pre-captured MarkerSnapshots. */
private fun topSnapshotNames(snapshots: List<MarkerSnapshot>): List<String> {
    return snapshots
        .filter { it.name.isNotBlank() }
        .take(2)
        .map { it.name }
}
// Note: MarkerSnapshot has no 'origin' field, so we filter by non-blank name as a proxy.
// IDLE_AUTO markers have date-format names like "2026-07-05" which are distinct from
// user-marker names like "Anse de la Salis".

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

/** Check if a WhereAmIResult contains a pinned marker with the 🤿 diving icon. */
private fun hasDivingPinnedMarker(result: ykws.android.maro.spatial.WhereAmIResult): Boolean {
    return result.allMatches.any { match ->
        val m = markerOf(match)
        m.pinned && m.icon == "\uD83E\uDD3F"  // 🤿
    }
}

/** Extract the name of the first pinned 🤿 marker in the result, or null. */
private fun divingLocationName(result: ykws.android.maro.spatial.WhereAmIResult): String? {
    return result.allMatches
        .firstOrNull { match ->
            val m = markerOf(match)
            m.pinned && m.icon == "\uD83E\uDD3F"
        }
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
private fun formatStopLine(bm: BoatMarker, zoneNames: List<String>): String {
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

### Step 4 — recomputeDescription()

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

```kotlin
/**
 * Rebuild the track description from the full BoatMarker history.
 * Called on every trigger: idle start, idle end, manual marker, title poll, marker change.
 * Reuses existing updateCurrentTrackMeta() for comment persistence.
 */
private fun recomputeDescription() {
    val wia = whereAmI ?: return
    val track = currentTrack ?: return

    val lines = track.boatMarkers.map { bm ->
        val zones = when (bm.trigger) {
            BoatMarkerTrigger.IDLE -> {
                val result = wia(ykws.android.maro.data.model.LatLng(bm.boatLat, bm.boatLon))
                topZoneNames(result)
            }
            BoatMarkerTrigger.MANUAL -> {
                topSnapshotNames(bm.markers)
            }
        }
        formatStopLine(bm, zones)
    }

    val newComment = lines.joinToString("\n")
    if (track.comment != newComment) {
        updateCurrentTrackMeta(comment = newComment)
    }
}
```

### Step 5 — Triggers

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

- In `startIdleTimer()` → after BoatMarker appended → `recomputeDescription()`
- In `addManualBoatMarker()` → after BoatMarker appended → `recomputeDescription()`
- In `addPoint()` idle→moving transition → after `closeOpenBoatMarker()` → `recomputeDescription()` + `pollTitle()`
- In `pollTitle()` (Step 6) → `recomputeDescription()` before title check

**Important:** `closeOpenBoatMarker()` itself does NOT trigger recompute. Callers are responsible for calling recompute after close. This avoids async save races in `finalizeTrack()`.

### Step 6 — Title polling (every 3 min)

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

Add fields:
```kotlin
private var titlePollJob: Job? = null
private var markerObserverJob: Job? = null
```

```kotlin
private fun startPolling() {
    val wia = whereAmI ?: return
    titlePollJob?.cancel()
    titlePollJob = scope?.launch {
        while (isActive) {
            delay(180_000L) // 3 minutes
            recomputeDescription()
            pollTitle()
        }
    }
    markerObserverJob?.cancel()
    markerObserverJob = markerChangeNotifier?.let { flow ->
        scope?.launch {
            flow.collect {
                if (state == TrackRecorderState.ON && currentTrack != null) {
                    recomputeDescription()
                    pollTitle()
                }
            }
        }
    }
}

private fun stopPolling() {
    titlePollJob?.cancel()
    titlePollJob = null
    markerObserverJob?.cancel()
    markerObserverJob = null
}

private fun pollTitle() {
    val wia = whereAmI ?: return
    val track = currentTrack ?: return
    val markers = track.boatMarkers
    if (markers.isEmpty()) return

    // ── Tier 1: 🤿 diving pinned marker (highest priority) ──
    for (bm in markers) {
        val result = wia(ykws.android.maro.data.model.LatLng(bm.boatLat, bm.boatLon))
        if (hasDivingPinnedMarker(result)) {
            val name = divingLocationName(result) ?: continue
            if (track.name != name) updateCurrentTrackMeta(name = name)
            return
        }
    }

    // ── Tier 2: MANUAL priority ──
    val manualMarkers = markers.filter { it.trigger == BoatMarkerTrigger.MANUAL }
    if (manualMarkers.isNotEmpty()) {
        val latest = manualMarkers.maxByOrNull { it.startTimeMs } ?: return
        val result = wia(ykws.android.maro.data.model.LatLng(latest.boatLat, latest.boatLon))
        val name = topLocationName(result) ?: return
        if (track.name != name) updateCurrentTrackMeta(name = name)
        return
    }

    // ── Tier 3: IDLE longest duration ──
    val now = System.currentTimeMillis()
    val longest = markers
        .filter { it.trigger == BoatMarkerTrigger.IDLE }
        .maxByOrNull { (it.endTimeMs ?: now) - it.startTimeMs } ?: return

    val result = wia(ykws.android.maro.data.model.LatLng(longest.boatLat, longest.boatLon))
    val name = topLocationName(result) ?: return

    if (track.name != name) updateCurrentTrackMeta(name = name)
}
```

Wire `startPolling()` in `beginRecording()` (after `startCheckpointJob()`).
Wire `stopPolling()` at the **top** of `finalizeTrack()` and in `transitionTo(OFF)`.

### Step 7 — Finalize title

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

In `finalizeTrack()`, after flushing the idle session and closing the open BoatMarker, but before `currentTrack = finalized`:

```kotlin
// Compute destination-based title (after closeOpenBoatMarker, before final copy)
val finalName = computeFinalTitle(finalized)
val finalizedWithTitle = if (finalName != null) finalized.copy(name = finalName) else finalized
```

```kotlin
private fun computeFinalTitle(track: Track): String? {
    val wia = whereAmI ?: return null
    if (track.boatMarkers.isEmpty()) return null

    data class NamedStop(val name: String, val tier: Int, val durationSec: Long)
    // tier: 1 = diving, 2 = manual, 3 = idle

    val now = System.currentTimeMillis()
    val namedStops = track.boatMarkers.mapNotNull { bm ->
        val result = wia(ykws.android.maro.data.model.LatLng(bm.boatLat, bm.boatLon))
        val name = when {
            hasDivingPinnedMarker(result) -> divingLocationName(result)
            else -> topLocationName(result)
        }
        if (name != null) {
            val dur = ((bm.endTimeMs ?: now) - bm.startTimeMs) / 1000
            val tier = when {
                hasDivingPinnedMarker(result) -> 1
                bm.trigger == BoatMarkerTrigger.MANUAL -> 2
                else -> 3
            }
            NamedStop(name, tier, dur)
        } else null
    }.sortedWith(compareBy({ it.tier }, { -it.durationSec }))

    val diving = namedStops.filter { it.tier == 1 }
    val manuals = namedStops.filter { it.tier == 2 }
    val idles = namedStops.filter { it.tier == 3 }

    return when {
        diving.size >= 2 -> "[${diving[0].name} -> ${diving[1].name}]"
        diving.size == 1 && manuals.isNotEmpty() -> "[${diving[0].name} -> ${manuals[0].name}]"
        diving.size == 1 && idles.isNotEmpty() -> "[${diving[0].name} -> ${idles[0].name}]"
        diving.size == 1 -> "[${diving[0].name}]"
        manuals.size >= 2 -> "[${manuals[0].name} -> ${manuals[1].name}]"
        manuals.size == 1 && idles.isNotEmpty() -> "[${manuals[0].name} -> ${idles[0].name}]"
        manuals.size == 1 -> "[${manuals[0].name}]"
        idles.size >= 2 -> "[${idles[0].name} -> ${idles[1].name}]"
        idles.size == 1 -> "[${idles[0].name}]"
        else -> null
    }
}
```

### Step 8 — Wire in TrackViewModel

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`

Add `whereAmI` and `markerChangeNotifier` passthrough params to `startRecorder()` → `TrackRecorder()` construction.

### Step 9 — Wire in MapScreen

**File:** `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

1. Create a `MutableSharedFlow<Unit>` for marker change notifications:
   ```kotlin
   val markerChangeFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
   ```

2. Pass `markersViewModel::whereAmISync` and `markerChangeFlow` to TrackViewModel/Recorder.

3. After marker save/edit operations, emit:
   ```kotlin
   markerChangeFlow.tryEmit(Unit)
   ```

### Step 10 — Wire error display in MapScreen

**File:** `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

In the error overlay slot (around line 1819), add after the existing `CoastlineState.Error` check:

```kotlin
// Track info error (from populate-track-info)
val trackInfoError = trackRecorderState.infoError
if (trackInfoError != null) {
    ErrorOverlay(
        message = trackInfoError,
        onRetry = { trackViewModel.clearInfoError() }
    )
}
```

Add `clearInfoError()` to TrackViewModel → delegates to `recorder?.clearInfoError()`.

In TrackRecorder:
```kotlin
fun clearInfoError() {
    _uiState.update { it.copy(infoError = null) }
}
```

Auto-dismiss: `LaunchedEffect(trackInfoError)` delays 8s then calls `clearInfoError()`.

---

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` | Fix startTimeMs bug; +infoError UiState field; +whereAmI param; +markerChangeNotifier param; +8 helper functions; +recomputeDescription (with try-catch); +title polling with MANUAL priority (with try-catch); +computeFinalTitle (with try-catch); +clearInfoError(); +marker change observer |
| `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` | +whereAmI + markerChangeNotifier passthrough params; +clearInfoError() delegate |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Wire whereAmISync + markerChangeFlow; emit on marker save/edit; render ErrorOverlay for trackInfoError; auto-dismiss LaunchedEffect |

## Key Files (reference)

- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — `name` (title), `comment` (description), `boatMarkers: List<BoatMarker>`
- `app/src/main/java/ykws/android/maro/data/track/BoatMarker.kt` — `boatLat`, `boatLon`, `startTimeMs`, `endTimeMs`, `trigger`, `markers`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — state machine, idle timer, finalize
- `app/src/main/java/ykws/android/maro/data/track/IdleThresholdCallback.kt` — existing callback (unchanged)
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — `WhereAmIResult`, `WhereAmIMatch`, `resolveAllMarkers`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `whereAmISync()`
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — `saveCheckpoint()` (unchanged)

## Rules

- whereAmI lambda is nullable — when null, the feature is entirely dormant
- markerChangeNotifier is nullable — when null, only idle/poll events trigger recomputation
- Description replaces `comment` field during recording (auto-content overwrites user text)
- Title polling: 3-minute interval + immediate trigger on idle-end, manual marker, and marker changes
- MANUAL BoatMarkers have absolute title priority (most recent wins)
- `closeOpenBoatMarker()` does NOT trigger recompute — callers are responsible
- `stopPolling()` is called at the TOP of `finalizeTrack()` to cancel all background jobs before any mutations
- `recomputeDescription()` is idempotent — checks `track.comment != newComment` before writing
- `markerOf()` local helper mirrors `MarkerMatcher.markerOf()` (private in MarkerMatcher, so duplicated)
- IDLE_AUTO filter for `whereAmI` results uses `MarkerOrigin.IDLE_AUTO`
- IDLE_AUTO filter for `MarkerSnapshot` uses non-blank name check (snapshots lack `origin` field)
- `formatStopLine` no longer takes `nowMs` parameter (unused after Step 2 fix)
