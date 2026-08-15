# Notification Lifecycle — Plan

## Goal
1. Recording active → notification persists **and the track keeps recording** in background.
2. Not recording → notification goes when the app task is gone.
3. Exit while recording → after double-back, dialog offers three choices: save track, continue recording, discard track. No dialog when not recording.

## Current vs Target

| Case | Current | Target |
|---|---|---|
| Not recording, swipe away | notification stays | notification gone |
| Recording, swipe away | notification stays but track **stops** (`TrackViewModel.onCleared()` → `stopRecorder()`) | notification + recording both continue |
| Exit while recording | double-back dialog (Stop & Exit / Keep Recording) | 3-button dialog (Save / Continue / Discard) |

Root causes:
- Service is always-on and never stopped on task removal (no `onTaskRemoved`, no `stopWithTask`).
- Recorder lives in `TrackViewModel` (`viewModelScope`), so it dies with the Activity.

## Changes

### A. Idle notification cleanup — rule 2 (small)
Add `onTaskRemoved(rootIntent)` to [`TrackRecordingService`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:43):
- recording → keep running
- not recording → `stopSelf()`

`onTaskRemoved` fires on swipe-away only; the double-back path already calls `stopService`.

### B. Background recording survives — rule 1 (large)
Move recorder ownership out of `TrackViewModel` into the service (or a process-scoped holder):
- Service holds the authoritative recording state + `TrackRecorder` instance.
- Start/stop routing goes through service intents (mirroring the existing `ACTION_STOP_RECORDING` chain).
- `TrackViewModel` becomes an observer of service state, not the owner.
- Notification "Stop" action already routes through the service — reuse it.

### C. Exit dialog — rule 3 (needs work)
Current dialog in [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1509) has 2 buttons (Stop & Exit, Keep Recording). Add a third "Discard track":
- **Save track:** `trackViewModel.stopRecording()` → `stopService` → `finishAffinity` (existing).
- **Continue recording:** `moveTaskToBack(true)` (existing).
- **Discard track:** new path — `TrackRecorder` has no discard method; add one that stops without finalizing and deletes the in-progress track + checkpoint (via `TrackRepository.delete` / `deleteCheckpoint`), then `stopService` + `finishAffinity`.

Dialog already renders only while recording (`showExitDialog` gated by `isRecording` at [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1488)).

## Steps
1. Add `onTaskRemoved()` → `stopSelf()` when idle.
2. Introduce service-owned recording state (replace static `activeRecorder`).
3. Move `TrackRecorder` lifecycle into the service (start/stop via intents).
4. Rewire `TrackViewModel` to observe service state; drop `onCleared` stop path for active recording.
5. Update `MapScreen` notification/dialog wiring to the service state.
6. Build + manual verification (swipe-away while recording, swipe-away while idle, double-back while recording).

## Files
- [`TrackRecordingService.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt)
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt)
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)
- [`MainActivity.kt`](app/src/main/java/ykws/android/maro/MainActivity.kt)

## Open
- Shared `isRecording` flag: prefer service-held state over the `@Volatile activeRecorder` static.

## Implemented
- A ✓ [`TrackRecordingService.onTaskRemoved()`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:144) → `stopSelf()` when idle.
- B ✓ service-owned [`TrackRecorder` + GPS sampling](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:202); [`TrackViewModel`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:34) is a pure observer (no `onCleared` stop path); `StateFlow isRecording` replaces the `@Volatile activeRecorder` static.
- C ✓ 3-button dialog [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1501) (Save / Continue / Discard) + [`TrackRecorder.discard()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:427).
- Build: SUCCESSFUL (`apk-build.bat`, `assembleDebug`).
- Regression: service-owned recorder built without `whereAmI`/`idleThresholdCallback`/`markerChangeNotifier` → idle 🕐 BoatMarkers and live title polling are dead.
