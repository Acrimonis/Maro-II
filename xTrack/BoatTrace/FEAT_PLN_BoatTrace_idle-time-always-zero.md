# FEAT_PLN: Idle Time Always Zero — Root Cause & Fix Plan

**Feature:** BoatTrace
**Date:** 2026-06-28
**Branch:** feature/track-idling
**Status:** discussion

## Problem

In the track list, the "idle time" field always displays 0. It should show the duration the boat spent stationary (`isIdle()`) during recording.

## Root Cause: Three Bugs

### Bug 1 — `pausedDurationSec` hardcoded to 0
[`TrackRecorder.kt:555`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:555):
```kotlin
pausedDurationSec = 0,
```
`finalizeTrack()` always writes 0.

### Bug 2 — No idle duration accumulator
[`TrackRecorder.kt:126-164`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:126):
No field tracks cumulative idle seconds. The `isStopped` signal gates point capture correctly (line 319-326), but the duration spent in the stopped state is never measured.

### Bug 3 — Live card hardcodes 0
[`TrackHistoryOverlay.kt:916`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:916):
```kotlin
StatCell(stringResource(R.string.track_stat_idle), fmtDuration(0))
```

### Bonus Bug — `navigatingDurationSec` not adjusted
[`TrackRecorder.kt:558`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:558):
```kotlin
navigatingDurationSec = totalElapsedSec
```
Since idle is always 0, navigating == total. Once idle is tracked, this must become `totalElapsedSec - idleDurationSec`.

## Conceptual Confusion: "paused" vs "idle"

- [`Track.kt:16`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:16): `pausedDurationSec` documented as "PAUSED state"
- State machine has only `OFF` / `ON` — no PAUSED state
- UI label uses "idle" (`track_stat_idle`)
- [`Track.kt:23`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:23): `navigatingDurationSec` doc says `= elapsedWallClockSec - pausedDurationSec`

**Intent:** `pausedDurationSec` was meant to be idle time but was never wired up.

## Fix Plan

### Step 1 — Add `idleDurationSec` to data model
- [`Track.kt`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt): new proto field `idleDurationSec: Long` (proto #15)
- [`TrackSummary.kt`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:48): new proto field `idleDurationSec: Long` (proto #14)

### Step 2 — Add idle accumulator to TrackRecorder
- New fields: `idleDurationSec: Long`, `idleStartMs: Long`, `wasStopped: Boolean`
- In `addPoint()` (around line 319): track stopped→moving and moving→stopped transitions, accumulating delta into `idleDurationSec`
- In `finalizeTrack()`: flush any open idle period, write `idleDurationSec` to Track

### Step 3 — Add `idleDurationSec` to TrackRecorderUiState
- [`TrackRecorder.kt:67`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:67): add `val idleDurationSec: Long = 0L`
- Update `_uiState` in `addPoint()` to expose current idle accumulator

### Step 4 — Fix `navigatingDurationSec` in finalizeTrack()
```kotlin
navigatingDurationSec = totalElapsedSec - idleDurationSec
```

### Step 5 — Update display in TrackHistoryOverlay
- Line 703 (`TrackCardContent`): `summary.idleDurationSec` (was `summary.pausedDurationSec`)
- Line 911 (`LiveTrackCard`): `fmtDuration(liveState.elapsedSeconds - liveState.idleDurationSec)` for Nav stat (was `liveState.elapsedSeconds`)
- Line 916 (`LiveTrackCard`): `liveState.idleDurationSec` (was `fmtDuration(0)`)

### Step 6 — Add idle time to menu drawer recording status
- [`MenuDrawerOverlay.kt:226-235`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:226): add `StatRow("Idle", formatDuration(recorderState.idleDurationSec))` to the live stats block. Once `idleDurationSec` is in `TrackRecorderUiState`, this is a one-liner.

### Step 7 — Update TrackRepository mappings
- [`TrackRepository.kt:117`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:117): `finalizeOrphanedCheckpoint()` — change `pausedDurationSec` → `idleDurationSec` in navigating formula
- [`TrackRepository.kt:137-151`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:137): `rebuildIndex()` — add `idleDurationSec = track.idleDurationSec` to TrackSummary constructor call

### Step 8 — Keep `pausedDurationSec` as deprecated
Keep the field on `Track`/`TrackSummary` for protobuf compatibility (don't reuse proto numbers). It stays 0 until a future pause feature is implemented.

## Idle Accumulation Pseudocode

```kotlin
// In addPoint(), before "if (stopped) return":
val stopped = isStopped.value
val now = System.currentTimeMillis()

if (stopped && !wasStopped) {
    idleStartMs = now  // transition: moving → idle
} else if (!stopped && wasStopped && idleStartMs > 0) {
    idleDurationSec += (now - idleStartMs) / 1000
    idleStartMs = 0L  // transition: idle → moving
    _uiState.update { it.copy(idleDurationSec = idleDurationSec) }
}
wasStopped = stopped

if (stopped) return  // existing gate
```

```kotlin
// In finalizeTrack(), before building finalized Track:
if (wasStopped && idleStartMs > 0) {
    idleDurationSec += (System.currentTimeMillis() - idleStartMs) / 1000  // flush open idle
}
```

## Implemented

**Date:** 2026-06-28 | **Build:** ✅ passed | **Branch:** feature/track-idling

### Changes
- [`Track.kt:41,63`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:41) — Added `idleDurationSec: Long` to `Track` (proto #15) and `TrackSummary` (proto #14). `pausedDurationSec` kept for protobuf compat.
- [`TrackRecorder.kt:80,167-170,306-308,334-343,572-583`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:80) — Added `idleDurationSec` to UiState + three accumulator fields + transition tracking in `addPoint()` + flush in `finalizeTrack()` + reset in `beginRecording()`. Fixed `navigatingDurationSec = totalElapsedSec - idleDurationSec`.
- [`TrackRepository.kt:117,148`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:117) — Mapped `idleDurationSec` in `rebuildIndex()`. Updated orphan formula to reference `idleDurationSec`.
- [`TrackHistoryOverlay.kt:703,911,916`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:703) — Summary cards: `idleDurationSec`. Live card Nav: `elapsedSeconds - idleDurationSec`. Live card Idle: `idleDurationSec`.
- [`MenuDrawerOverlay.kt:235`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:235) — Added `StatRow("Idle", ...)` to recording status.

### How it works
The `isStopped` signal (from [`NavigationViewModel`](../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:351)) already correctly detects when the boat is stationary via `AdaptiveGpsPolicy`. The fix adds a stopwatch: every time `isStopped` transitions `false→true`, a timestamp is captured; on `true→false`, the delta is added to `idleDurationSec`. On track finalization, any open idle period is flushed. The accumulated value flows through `TrackRecorderUiState` → live UI and is persisted to `Track`/`TrackSummary` for history display.

## Files Touched
| File | Change |
|------|--------|
| `app/.../data/track/Track.kt` | Add `idleDurationSec` to Track + TrackSummary |
| `app/.../data/track/TrackRecorder.kt` | Accumulator fields + logic + UiState field + beginRecording init |
| `app/.../data/track/TrackRepository.kt` | Map `idleDurationSec` in rebuildIndex; update orphan formula |
| `app/.../ui/map/TrackHistoryOverlay.kt` | Lines 703, 911, 916 — all three stat refs |
| `app/.../ui/map/MenuDrawerOverlay.kt` | Add Idle stat row to recording status |
