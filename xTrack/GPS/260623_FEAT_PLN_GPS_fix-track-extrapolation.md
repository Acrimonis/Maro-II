<!-- scope: feature -->
# Fix Track Extrapolation — Dead Reckoning Leaking Into Track Recording

> **Branch:** `feature/ya-gps-fix`
> **Active Feature:** GPS
> **Subfeature:** `fix-track-extrapolation`
> **Date:** 2026-06-23

---

## Problem Statement

In GPS tracking mode, when the boat stops (or GPS briefly drops), the dead-reckoning system extrapolates forward positions from last-known course/speed. These extrapolated positions are intended for **display only** (keeping the map marker moving during GPS dropout). However, they leak into the persistent track recording, creating parasitic forward spikes that are never cleaned up when real GPS resumes.

**User observation:** "Those points are not handled/removed upon restarting and create parasitic spikes."

---

## Root Cause: The Leak Path

### Layer 1 — [`CoastlineViewModel.startDeadReckoning()`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:809)

Dead reckoning updates `_gpsPosition` every 500ms with extrapolated positions:

```kotlin
// line 824
_gpsPosition.value = estimatedPos   // intended for map display only
```

This was designed purely for visual continuity — the map marker should keep moving during brief GPS dropouts instead of freezing.

### Layer 2 — [`MapScreen.kt` combine flow](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:514-536)

The track recording pipeline is fed by a `combine` flow that includes `viewModel.gpsPosition`:

```kotlin
val gpsFlow = combine(
    viewModel.gpsPosition,    // ← includes dead-reckoned positions!
    viewModel.mapCenter,
    viewModel.navigationState,
    ticker
) { gpsPos, center, nav, _ ->
    GpsFix(
        position = gpsPos ?: center,
        hasLock = true,        // ← HARDCODED TRUE — even for extrapolated positions
        ...
    )
}
trackViewModel.startRecorder(gpsFlow, appSettings)
```

Every time `_gpsPosition` changes (including dead-reckoned updates), this combine fires and creates a `GpsFix` with `hasLock = true`, indistinguishable from a real GPS fix.

### Layer 3 — [`TrackRecorder.addPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:319)

The fake `GpsFix` enters the track recording pipeline and is processed as a legitimate point:

1. `policy.onFix()` — adaptive policy sees position displacement → returns ACTIVE (extrapolated points show movement)
2. `!moving && gpsMode` — not triggered because `moving=true` (extrapolation shows displacement from last real fix)
3. Spike rejection Gates 0-3 — may or may not catch the extrapolated points:
   - **Gate 0** (GPS recovery): `lastHadLock` is still `true` (no `!hasLock` fix was emitted before the stale watchdog fired) → gate bypassed
   - **Gates 1-3**: depended on whether the extrapolated position's implied speed/direction matches recent history — often passes because DR uses the last valid course/speed
4. `captureAcceptedPoint()` writes the extrapolated point into the track

### Trigger Conditions

Dead reckoning starts in two cases:

| Trigger | Condition | Timing |
|---------|-----------|--------|
| **Lock loss** | `fix.hasLock == false` | Immediate |
| **Stale watchdog** | No fix received for `GPS_STALE_TIMEOUT_MS` (5 s) | After 5s gap |

Dead reckoning requires `deadReckoningState != null`, which requires `hasCourse && speedMps > 0` from the last real fix.

### The "Stopping Boat" Scenario

```
1. Boat moving → GPS delivers fixes, DR state saved (bearing + speed > 0)
2. Boat stops → AdaptiveGpsPolicy eventually detects IDLE → deadReckoningState = null (line 714)
3. But there's a race: if GPS idle cadence is slow (e.g., 24s with dormancy),
   the stale watchdog fires after 5s — BEFORE the policy has been updated to IDLE
4. deadReckoningState is still non-null (saved from step 1, policy was ACTIVE)
5. Dead reckoning starts, extrapolating forward from last known motion
6. Extrapolated points flow through MapScreen → TrackRecorder → recorded in track
7. When GPS delivers next real fix, DR is cancelled, but extrapolated points remain
```

---

## Answer to User Questions

### Q1: What are the conditions in which extrapolation tracking is done, and its points added to the track?

Extrapolation starts when:
- **`!fix.hasLock`** (GNSS satellites < 4 or provider unavailable), OR
- **Stale watchdog timeout** (no GPS fix for 5 seconds)

AND `deadReckoningState` is non-null (last fix had `hasCourse && speedMps > 0`).

Points are added to the track because the `combine` flow in [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:514) reacts to every `_gpsPosition` change — including dead-reckoned updates — and packages them as `GpsFix(hasLock = true)`.

### Q2: Shouldn't those points not be added to the track and only to the display until next GPS valid acquisition?

**Yes.** Dead reckoning is a display-only concern. The architecture inadvertently couples the display position flow (`_gpsPosition`) to the track recording flow via the `combine` in MapScreen.kt. These are separate concerns that should have separate data flows.

---

## Fix Options

### Option A: Guard in MapScreen.kt combine (minimal, targeted)

Check `viewModel.isEstimating` in the combine and emit `hasLock = false` or skip entirely:

```kotlin
{ gpsPos, center, nav, _ ->
    if (isEstimating) return@combine  // skip: don't feed DR positions to TrackRecorder
    GpsFix(position = gpsPos ?: center, hasLock = true, ...)
}
```

**Pros:** One-line fix, surgical, no architectural changes.
**Cons:** `isEstimating` is collected separately; need to add it as a combine input or read it from viewModel.

### Option B: Separate flow for TrackRecorder (architectural)

Create a dedicated `SharedFlow<GpsFix>` in CoastlineViewModel that carries only real GPS fixes:

```kotlin
// In CoastlineViewModel — only emit real GPS fixes from the .onEach block
private val _realGpsFix = MutableSharedFlow<GpsFix>()
val realGpsFix: SharedFlow<GpsFix> = _realGpsFix
```

MapScreen.kt then uses `viewModel.realGpsFix` instead of the combine flow.

**Pros:** Clean separation of concerns. Display and recording are independent flows.
**Cons:** Larger change, touches ViewModel + MapScreen.

### Option C: Filter in TrackRecorder.addPoint()

Check `fix.hasLock` in `addPoint()` and reject `hasLock = false` fixes.

**Problem:** The combine currently hardcodes `hasLock = true`, so this doesn't help unless we also fix the combine.

### Recommendation

**Option B** is architecturally correct — separate the display flow from the recording flow. But **Option A** is the pragmatic minimal fix that can be shipped immediately. Consider doing Option A now and refactoring to Option B later.

---

## Files Involved

| File | Role |
|------|------|
| [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:809-831) | `startDeadReckoning()` — emits extrapolated positions to `_gpsPosition` |
| [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:260-262) | `_isEstimating` StateFlow — true when DR is active |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:514-536) | `combine` flow — repackages `_gpsPosition` into `GpsFix`, feeds `TrackRecorder` |
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:319) | `addPoint()` — processes `GpsFix` through spike gates, records accepted points |

---

## Constants

| Constant | Value | Location |
|----------|-------|----------|
| `DEAD_RECKONING_INTERVAL_MS` | 500ms | CoastlineViewModel companion |
| `DEAD_RECKONING_MAX_MS` | 30,000ms | CoastlineViewModel companion |
| `GPS_STALE_TIMEOUT_MS` | 5,000ms | CoastlineViewModel companion |
