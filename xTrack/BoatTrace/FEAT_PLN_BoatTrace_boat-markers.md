# BoatTrack Markers — Architecture Plan

**Created:** 2026-07-01 12:45 UTC
**Updated:** 2026-07-01 15:38 UTC
**Branch:** feature/track+markers
**Status:** finalised

## Goal

Extend the Track data model so that during recording, marker relevance is captured at key moments:
1. **IDLE**: when the boat has been idle ≥ threshold, snapshot nearby markers into the track
2. **MANUAL**: when the user taps the boat-marker button, snapshot nearby markers into the track
3. **Auto-marker**: create a 🕐 Pin on the map at the idle position (temporary → permanent if idle was long enough)

---

## 1. Data Model

### New types (all in `data/track/` — zero marker imports)

```kotlin
enum class BoatMarkerTrigger { IDLE, MANUAL }

@Serializable
data class MarkerSnapshot(
    @ProtoNumber(1) val markerId: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val geometryType: String,    // "Pin", "Circle", "Corridor"
    @ProtoNumber(4) val lat: Double,             // Pin: marker position. Circle: center. Corridor: center point.
    @ProtoNumber(5) val lon: Double,
    @ProtoNumber(6) val distanceNm: Double,
    @ProtoNumber(7) val bearingDeg: Double,
    @ProtoNumber(8) val zoneSizeM: Double = 0.0, // Circle: radius. Pin/Corridor: 0.0 (n/a).
    @ProtoNumber(9) val icon: String? = null,    // marker's emoji icon at snapshot time
)

@Serializable
data class BoatMarker(
    @ProtoNumber(1) val trigger: BoatMarkerTrigger,
    @ProtoNumber(2) val startTimeMs: Long,
    @ProtoNumber(3) val endTimeMs: Long? = null,
    @ProtoNumber(4) val markers: List<MarkerSnapshot> = emptyList(),
    @ProtoNumber(5) val boatLat: Double = 0.0,
    @ProtoNumber(6) val boatLon: Double = 0.0,
    @ProtoNumber(7) val sequenceIndex: Int = 0,     // ordinal within the track: 0, 1, 2...
)
```

> **Corridor geometry is NOT preserved.** The snapshot captures only the center point, label, distance, and bearing. Corridor endpoints and width are lost. Historical display renders Corridors as a single dot with label.

### Track extension

```kotlin
@ProtoNumber(16) val boatMarkers: List<BoatMarker> = emptyList()
```

`TrackSummary` does **not** need this field.

### Why snapshot?

Markers can be edited or deleted after a track is recorded. The `MarkerSnapshot` captures what was true *at that moment*. Survives marker mutations and deletions — it's a historical record.

---

## 2. Abstraction Design

### Principle: TrackRecorder owns the timer + BoatMarker lifecycle

[`TrackRecorder`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) already detects idle entry/exit (lines 336-347). It manages the idle threshold timer, BoatMarker creation/closure, and [`IdleSessionContext`](#idlesessioncontext). The callback abstracts *what happens at threshold*, not the timer logic.

### IdleThresholdCallback — no marker knowledge in TrackRecorder

```kotlin
// In data/track/IdleThresholdCallback.kt — NO marker imports
data class IdleCaptureResult(
    val entries: List<MarkerSnapshot>,
    val shouldOpenDrawer: Boolean
)

fun interface IdleThresholdCallback {
    suspend fun onIdleThresholdReached(position: LatLng): IdleCaptureResult
}
```

### TrackRecorder changes

```
TrackRecorder(
    // ... existing params unchanged ...
    idleThresholdSec: Long = 60,
    idleThresholdCallback: IdleThresholdCallback? = null
)
```

Internal logic in `addPoint()` (alongside existing idle detection):

```
on idle entry (stopped && !wasStopped):
  -> create IdleSessionContext(startTimeMs, entryLat, entryLon)
  -> start idle timer coroutine (thresholdSec, tied to recorder's scope)
  -> emit TrackEvent.IdlePeriodStarted(entryLat, entryLon, startTimeMs)

if timer fires (idle persisted >= threshold):
  -> callback.onIdleThresholdReached(position)
  -> if result.entries.isNotEmpty():
      append BoatMarker(IDLE, startTimeMs=now, endTimeMs=null, entries,
                        boatLat, boatLon, sequenceIndex)
      session.boatMarkerIndex = boatMarkers.lastIndex
      sync boatMarkers to currentTrack + trigger immediate checkpoint save
  -> session.autoMarkerId = result.autoMarkerId
  -> if result.shouldOpenDrawer:
      session.drawerAutoOpened = true
      emit TrackEvent.DrawerAutoOpenRequested

on idle exit (!stopped && wasStopped):
  -> cancel timer
  -> close BoatMarker at session.boatMarkerIndex (endTimeMs = now)
  -> sync to currentTrack + trigger immediate checkpoint save
  -> emit DrawerAutoCloseRequested (iff session.drawerAutoOpened)
  -> emit IdlePeriodCompleted(entryLat, entryLon, startMs, endTimeMs=now,
                               durationSec, autoMarkerId=session.autoMarkerId)
  -> dispose session
```

In `finalizeTrack()`, if track was finalized while idle:
```
  -> emit IdlePeriodCompleted(entryLat, entryLon, startMs, endTimeMs=0, durationSec=0,
                               autoMarkerId=session.autoMarkerId)
  -> dispose session
```

### IdleSessionContext

```kotlin
// Internal to TrackRecorder — one per idle period. Created on idle entry, consumed on exit.
class IdleSessionContext(
    val startTimeMs: Long,
    val entryLat: Double,
    val entryLon: Double,
    var boatMarkerIndex: Int? = null,      // index into boatMarkers list
    var drawerAutoOpened: Boolean = false,
    var autoMarkerId: String? = null       // set by MapScreen via setActiveSessionAutoMarkerId()
)
```

TrackRecorder exposes:
```kotlin
fun setActiveSessionAutoMarkerId(id: String) {
    activeSession?.autoMarkerId = id
}
```

MapScreen calls this after creating the temp 🕐 pin on `IdlePeriodStarted`. The ID flows into `IdlePeriodCompleted.autoMarkerId` when the event is emitted.

### TrackEvent additions

```kotlin
data class IdlePeriodStarted(
    val entryLat: Double,
    val entryLon: Double,
    val startTimeMs: Long
) : TrackEvent()

data object DrawerAutoOpenRequested : TrackEvent()
data object DrawerAutoCloseRequested : TrackEvent()

data class IdlePeriodCompleted(
    val entryLat: Double,
    val entryLon: Double,
    val startTimeMs: Long,
    val endTimeMs: Long,          // 0 = track finalized during idle
    val durationSec: Long,        // delta, NOT cumulative idleDurationSec
    val autoMarkerId: String?     // ID of 🕐 pin, null if none
) : TrackEvent()
```

### What TrackRecorder does NOT know about

- `UserMarker`, `MarkerMatcher`, `WhereAmIMatch` — zero marker imports
- Drawer UI state — only emits events
- `MarkersViewModel` — doesn't exist from its perspective

### Wiring (MapScreen, the composition root)

[`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) already holds both `trackViewModel` and `markersViewModel`:

```kotlin
// Callback for idle threshold:
val idleCallback = IdleThresholdCallback { position ->
    try {
        val result = markersViewModel.whereAmISync(position)
        val snapshots = result.allMatches.map { it.toMarkerSnapshot() }
        IdleCaptureResult(
            entries = snapshots,
            shouldOpenDrawer = snapshots.isNotEmpty(),
            autoMarkerId = null  // auto-marker created separately on IdlePeriodStarted
        )
    } catch (e: Exception) {
        Log.w(TAG, "IdleThresholdCallback failed", e)
        IdleCaptureResult(emptyList(), false)
    }
}

// IdlePeriodStarted → create temporary 🕐 pin
var pendingAutoMarkerId: String? by remember { mutableStateOf(null) }

LaunchedEffect(Unit) {
    trackViewModel.events.collect { event ->
        when (event) {
            is IdlePeriodStarted ->
                pendingAutoMarkerId = markersViewModel.addTempAutoMarker(
                    event.entryLat, event.entryLon, event.startTimeMs)
            is DrawerAutoOpenRequested ->
                markersViewModel.openDrawer()
            is DrawerAutoCloseRequested ->
                markersViewModel.closeDrawer()
            is IdlePeriodCompleted ->
                handleIdleCompleted(event)
        }
    }
}
```

### MANUAL path

Existing boat-marker button (line 1070) adds after whereAmI:

```kotlin
val snapshots = result.allMatches.map { it.toMarkerSnapshot() }
if (snapshots.isNotEmpty()) {
    trackViewModel.addManualBoatMarker(snapshots)
}
```

### Dependency graph

```
MapScreen (composition root)
  |-- TrackViewModel --> TrackRecorder --> IdleThresholdCallback (fun interface)
  |                         |                 |
  |                         |-- IdleSessionContext (per idle period)
  |                         |-- boatMarkers list
  |                         \-- emits 4 TrackEvent variants
  |
  \-- MarkersViewModel --> MarkerMatcher (object)
        |-- whereAmISync(boatPos)    ← NEW
        |-- addTempAutoMarker()      ← NEW
        |-- openDrawer() / closeDrawer()
```

---

## 3. Config

```
# maro.properties
track.boatMarker.idleThresholdSec=60           # when to snapshot markers + open drawer
track.boatMarker.autoMarkerMinDurationSec=120  # minimum idle before 🕐 pin becomes permanent
```

---

## 4. Edge Cases

| Scenario | Handling |
|----------|----------|
| Multiple idle periods | One `IdleSessionContext` per period, BoatMarkers accumulate chronologically |
| Drawer already open (manual tap) | `DrawerAutoOpenRequested` ignored if drawer already showing |
| Drawer close on idle exit | Only close if `session.drawerAutoOpened == true` |
| Track finalized during idle | `IdlePeriodCompleted` with `endTimeMs=0` → auto-marker name `-> ?` |
| App-kill during idle | `confirmed=false` 🕐 pins cleaned up on startup |
| Drift during idle | Snapshot represents idle-detection moment — by design |
| `zoneSizeM=0.0` ambiguity | Consumers check `geometryType` first |
| Interleaved MANUAL+IDLE | `trigger` enum + `sequenceIndex` disambiguate |
| Duplicate marker snapshots | Dedup is display-layer concern |
| Error in callback/whereAmI | `try/catch` → silent skip, returns empty |

---

## 5. Implementation Order

1. **Data model**: `BoatMarkerTrigger`, `MarkerSnapshot`, `BoatMarker`, `IdleCaptureResult`, `IdleThresholdCallback` — all in `data/track/`. Track ProtoNumber 16.
2. **IdleSessionContext**: Internal class in TrackRecorder.
3. **TrackEvent**: `IdlePeriodStarted`, `DrawerAutoOpenRequested`, `DrawerAutoCloseRequested`, `IdlePeriodCompleted`.
4. **Config**: `track.boatMarker.idleThresholdSec` + `track.boatMarker.autoMarkerMinDurationSec` in `maro.properties` + `AppConfig`.
5. **TrackRecorder**: Idle threshold timer, session context lifecycle, BoatMarker append/close, event emission. `addManualBoatMarker()`. Checkpoint saves.
6. **MarkersViewModel**: `whereAmISync()`, `addTempAutoMarker()`, `confirmAutoMarker()`.
7. **TrackViewModel**: `addManualBoatMarker()` passthrough.
8. **ICON_SET**: Add 🐬🐚🏖️🕐, grid 3→4 rows.
9. **MapScreen**: Callback wiring, event observation, MANUAL path integration, startup cleanup of orphaned `confirmed=false` 🕐 markers.
10. **Build**

## Implemented

2026-07-01 — full implementation on `feature/track+markers`:

- **Data model:** `BoatMarkerTrigger`, `MarkerSnapshot`, `BoatMarker` (with `autoMarkerId`), `IdleCaptureResult`, `IdleThresholdCallback` interface, `IdleSessionContext` class. Track `@ProtoNumber(16)`.
- **TrackEvent:** `IdlePeriodStarted`, `IdlePeriodCompleted`, `DrawerAutoOpenRequested`, `DrawerAutoCloseRequested`.
- **Config:** `track.boatMarker.autoMarker.idleThresholdSec`, `minDurationSec`, `opacity` in maro.properties + AppConfig.
- **TrackRecorder:** idle threshold timer (60s default), session context lifecycle, BoatMarker append/close with checkpoint saves, `addManualBoatMarker()`, `setBoatMarkerAutoMarkerId()`.
- **MarkersViewModel:** `whereAmISync()`, `addTempAutoMarker()`, `confirmAutoMarker(id, name, desc)`. Top-level `toMarkerSnapshot()` extension.
- **TrackViewModel:** persistent `_events` forwarding, passthrough methods, idle callback wiring.
- **MapScreen:** idle callback → `whereAmISync` (snapshots) + drawer auto-open. Event observation: `IdlePeriodStarted` → temp 🕐 pin, `IdlePeriodCompleted` → confirm/delete. MANUAL boat-marker button snapshots into track. Startup cleanup of `keepable=false` markers.
- **MarkerOverlay:** proximity ring suppressed for `IDLE_AUTO` markers; icon tooltip suppressed; icon opacity from config.
- **ICON_SET:** 16 icons, 4×4 grid (🐬🐚🏖️🕐).
- **History rendering:** 🕐 pins rendered on history/pinned track polylines with matching transparency.
- **Consolidation:** single `maro.properties` in assets, root copy deleted, `syncMaroProperties` Gradle task removed.
