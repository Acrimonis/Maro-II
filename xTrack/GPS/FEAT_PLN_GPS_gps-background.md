# Plan: GPS Background Hardening + Recording-Aware Exit Guard

**Feature:** GPS → `gps-background` subfeature
**Branch:** `feature/gps-background`
**Date:** 2026-07-06

---

## Objective

Harden background GPS recording to match industry best practices (Waze/GMaps pattern) and make the exit guard recording-aware so users can't accidentally kill the app while recording.

## Background

Maro II already has the foundational pieces:
- `TrackRecordingService` — foreground service, `START_STICKY`, keeps process alive
- `MapScreen` ON_PAUSE — already gates `setGpsActive(false)` behind `trackRecorderState != ON`
- Double-back-to-exit — 2s window with banner
- **Checkpoint system** — periodic saves every 30s, orphan recovery dialog on startup (Save/Discard)

Gaps vs best practices:
1. Foreground service type is `specialUse` — should be `location` for GPS tracking
2. No `ACCESS_BACKGROUND_LOCATION` permission
3. No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — Doze can kill GPS
4. Exit guard doesn't differentiate recording vs non-recording
5. Recovery dialog has Save/Discard but no **Continue** option
6. No dashed gap line when resuming a track after a crash gap

---

## A. Background GPS Hardening

### A1. Foreground Service Type: `specialUse` → `location`

**File:** `app/src/main/AndroidManifest.xml`

Change the service declaration:
```xml
<service
    android:name=".data.track.TrackRecordingService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

Remove the `specialUse` justification `<property>` block.

Add required permission (Android 14+):
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

**Why:** `location` type = highest process priority from Android. Waze/GMaps use this.

### A2. `ACCESS_BACKGROUND_LOCATION` Permission

**File:** `app/src/main/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

**Runtime prompt on every recording start (blocking).** Android 11+ requires the user to explicitly choose "Allow all the time" in system Settings. On every "Start Recording" press, check if `ACCESS_BACKGROUND_LOCATION` is granted. If not, show a dialog:

```
"Background tracking needs 'Allow all the time' location permission."
[Open Settings] [Not Now]
```

Recording does NOT start until permission is granted. Prompt reappears on every recording attempt while denied.

### A3. Notification "Stop Recording" Action

**Files:** `TrackRecordingService.kt`, `StopRecordingReceiver.kt` (new)

**Architecture:** Service owns the notification → Service handles the action. Chain:

```
Notification "Stop" tapped
  → StopRecordingReceiver (manifest, always alive, thin bridge)
    → startService(ACTION_STOP_RECORDING)
      → TrackRecordingService.onStartCommand()
        → activeRecorder?.stop()
```

**TrackRecordingService changes:**
- Add `Notification.Action` to recording notification: label "Stop" / "Arrêter", icon `ic_media_pause`, PendingIntent → broadcast `ACTION_STOP_RECORDING`
- Add companion field: `@Volatile var activeRecorder: TrackRecorder? = null`
- Handle `ACTION_STOP_RECORDING` in `onStartCommand()`: call `activeRecorder?.stop()` then update notification to "Ready"
- New constant: `ACTION_STOP_RECORDING`

**StopRecordingReceiver.kt** (new, manifest-registered):
- 3-line bridge: receives `ACTION_STOP_RECORDING` → `context.startService(intent)` with same action
- No business logic — just routes to the service that owns the notification

**TrackViewModel.initRecorder()** sets `TrackRecordingService.activeRecorder = rec` after creation, clears to `null` on stop.

**Why service-owned:** The service already owns the notification lifecycle — it's the natural handler. No separate singleton. Clean separation: receiver→service→recorder.

**Sync with UI:** When the service stops recording (via notification action), it must also update `TrackRecorderUiState` so the UI reflects the stop. The `activeRecorder?.stop()` call inside `finalizeTrack()` updates `_uiState`, which flows to the UI via the existing StateFlow chain. If the app is in the foreground, the UI updates immediately.

**Notification update after stop:** After `activeRecorder?.stop()`, rebuild the notification with `recording = false` to show "Maro II • GPS • Idle • Ready • On Water".

### A4. Battery Optimization Exemption

**File:** `app/src/main/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

**File:** `app/src/main/java/ykws/android/maro/MainActivity.kt`

**Dual-trigger timing:**
1. Immediately when user presses "Start Recording" (first time only)
2. On app startup if recording was active last session (recovery scenario — user may have force-stopped)

Track prompt-shown state via SharedPreferences (`battery_opt_prompted`). Show once only across both triggers.

Dialog:
```
"To keep GPS tracking when the screen is off,
 Maro II needs to be exempt from battery optimization."
[Open Settings] [Not Now]
```

**Why:** Doze mode kills GPS after ~15 min of screen-off, even with a `location` FGS. Exemption prevents this.

---

## B. Recording-Aware Exit Guard

### B1. Double-Back with Recording Warning

**File:** `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (lines 1127-1172, 2054-2081)

**Current banner styling:**
| Property | Value |
|----------|-------|
| Surface | `buttonActionBgColor` (#CC16213E navy 80%) |
| Border | `uiDashboardBackground` (#1A1A2E dark navy) |
| Inner Box | `uiCardBackground` |
| Text | `uiSettingsToastText` (white) |

**New behavior (NOT recording):** Unchanged — "Press back again to exit", same banner.

**New behavior (WHEN recording):**
- Text: "⚠ Recording active — press back again to stop & exit" (EN) / "⚠ Enregistrement en cours — appuyez encore pour arrêter et quitter" (FR)
- Border: `uiDashboardZoneDanger` (#C62828 red) instead of `uiDashboardBackground`
- Everything else unchanged (surface, inner box, text color)
- On second back: call `trackViewModel.stopRecording()` → `stopService` → `finishAffinity()`

**Implementation:**
- Read `trackRecorderState` (`TrackRecorderState.ON`) in the `BackHandler` scope
- Conditional string resource + conditional border color
- On second back while recording: `trackViewModel.stopRecording()` → `delay(300)` → `stopService` + `finishAffinity()`
- 300ms delay ensures `finalizeTrack()` file I/O completes before process death

**String resources:**
```xml
<!-- values/strings.xml -->
<string name="exit_press_back_again_recording">⚠ Recording active — press back again to stop & exit</string>
<!-- values-fr/strings.xml -->
<string name="exit_press_back_again_recording">⚠ Enregistrement en cours — appuyez encore pour arrêter et quitter</string>
```

---

## C. Track Resilience (Crash / Force-Stop Recovery)

### C1. Checkpoint System — Already Exists ✅

- `TrackRecorder.startCheckpointJob()` — saves to `tracks/{id}_checkpoint.bin` every 30s
- `TrackRepository.recoverOrphanedCheckpoints()` — scans for orphaned checkpoints on startup
- Dialog at [`MapScreen.kt:1736`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1736) — "Unfinished Recording" with Save + Discard

### C2. Add "Continue" Button to Recovery Dialog

**File:** `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (lines 1736-1753)

Replace Save/Discard with Continue/Save:

```kotlin
AlertDialog(
    title = { Text("Unfinished Recording") },
    text = { Text("Found an unfinished recording from ${track.name}.") },
    confirmButton = {
        TextButton(onClick = { trackViewModel.resumeOrphanedCheckpoint(track) }) {
            Text("Continue")
        }
    },
    dismissButton = {
        TextButton(onClick = { trackViewModel.saveOrphanedCheckpoint(track) }) {
            Text("Save")
        }
    }
)
```

**Behavior per button:**
| Button | Action |
|--------|--------|
| **Continue** | Full resume — re-init TrackRecorder with checkpoint track data, restart sampleFlow pipeline. Append new points to existing track. If time/distance gap > threshold, insert GAP marker. |
| **Save** | Finalize checkpoint as a completed track (existing behavior). |

**resumeOrphanedCheckpoint() must be rewritten** — currently just deletes checkpoint and refreshes summaries. New behavior:
1. Load checkpoint track data (points already preserved in `Track` object from proto)
2. Re-initialize `TrackRecorder` with the loaded track as `currentTrack`
3. Restart the `sampleFlow` pipeline (same flow as `startRecorder()`)
4. In `TrackRecorder`, detect if `currentTrack` already has points → compute gap between last saved point and first new point → insert GAP marker if threshold exceeded
5. Delete checkpoint file after successful resume

**TrackRecorder changes:**
- New method `resume(track: Track, sampleFlow: Flow<TrackSample>)` — like `start()` but with pre-loaded track
- Gap detection on first new point after resume

### C3. Dashed Gap Line on Resume

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

When resuming after a gap, insert a **gap marker** into the track point list:
- If `distance(lastPoint, firstNewPoint) > 200m` OR `timeGap > 2 min` → insert `GAP_MARKER` between them
- `GAP_MARKER` is a special point with `type = GAP` (new field in `TrackPoint` proto)

**File:** map rendering — live polyline AND saved history tracks

When rendering any track polyline that contains GAP markers, split into segments:
- Normal segments → solid polyline
- Gap segments → dashed polyline (`DashPathEffect` with `intervals = floatArrayOf(20f, 10f)`)

**Live polyline** (MapScreen.kt lines 975-1014): incremental append already handles `_newPoint`. When a GAP point arrives, the existing polyline is finalized and a new dashed polyline is created for the gap, followed by a new solid polyline for the resumed segment.

**Saved history tracks** (MapScreen.kt line 805+): the rendering code that builds polylines from `TrackSummary` data must detect GAP markers in the point list and split into separate `Polyline` overlays with appropriate solid/dashed styles.

**New fields:**
- `TrackPoint.type`: enum `NORMAL | GAP` (default NORMAL, backward-compatible)
- Protobuf field: `optional PointType type = 10;`

**Properties (in `maro.properties`):**
```properties
tracking.gapDistanceThresholdM=200
tracking.gapTimeThresholdSec=120
```

---

## Files Touched

| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | FGS type `specialUse`→`location`, add `FOREGROUND_SERVICE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` | Notification "Stop" action + `ACTION_STOP_RECORDING` constant |
| `app/src/main/java/ykws/android/maro/data/track/StopRecordingReceiver.kt` | **New** — manifest BroadcastReceiver, stops recording from notification |
| `app/src/main/java/ykws/android/maro/MainActivity.kt` | Battery optimization exemption prompt |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Recording-aware BackHandler + red banner + "Continue" button in recovery dialog |
| `app/src/main/res/values/strings.xml` | `exit_press_back_again_recording`, battery prompt, "Continue" button |
| `app/src/main/res/values-fr/strings.xml` | French translations |
| `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` | Gap marker insertion on resume |
| `app/src/main/assets/maro.properties` | `tracking.gapDistanceThresholdM`, `tracking.gapTimeThresholdSec` |
| Map polyline rendering (MapScreen/overlay) | Dashed segments for gap markers |

## Decisions Log

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Exit while recording = exit app (stop+exit), not stop-only | User preference — stronger warning, same mechanic |
| 2 | Notification stop action = manifest BroadcastReceiver | Always active, works when app is backgrounded |
| 3 | Screen-off GPS = keep alive | Boating use case — phone in pocket, tracking continues |
| 4 | Track resilience = continue or save (not discard-only) | User wants option to resume after crash/force-stop |
| 5 | Dashed gap threshold = 200m / 2min | Conservative — only dashes when clearly discontinuous |
| 6 | Battery exemption = prompted once | Doze kills GPS after ~15min screen-off; exemption prevents this |

## Risks

| # | Risk | Mitigation |
|---|------|------------|
| 1 | `location` FGS type changes Android's process priority | Already `START_STICKY` + persistent notification; `location` only improves |
| 2 | `ACCESS_BACKGROUND_LOCATION` may trigger extra prompt | User already grants location; "All the time" is a one-time upgrade |
| 3 | `resumeOrphanedCheckpoint()` may not work end-to-end | Test: crash during recording → restart → Continue → verify track appends |
| 4 | Dashed gap rendering on osmdroid | osmdroid `Polyline` supports `DashPathEffect` natively |
| 5 | Battery exemption prompt timing | Show only when recording starts first time, not on every launch |

## Out of Scope

- Predictive back gesture (Android 13+) — handled by existing `BackHandler`
- Android 14 notification dismissal timeout — `START_STICKY` restarts; checkpoint survives
- Multi-segment track editing (merge/split) — separate feature
