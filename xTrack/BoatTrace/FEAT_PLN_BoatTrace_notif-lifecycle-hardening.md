# FEAT_PLN_BoatTrace — Notification Lifecycle Hardening

**Created:** 2026-07-14 09:14 UTC
**Branch:** feature/notif
**Status:** planning

## Context

Maro II uses a single foreground service (`TrackRecordingService`, START_STICKY) to hold a persistent notification. Three gaps identified:

1. **No tap-to-open** — notification has no `setContentIntent()`, tapping does nothing
2. **Post-kill zombie** — after process death, service recreates but `lastKnownOnWater` resets to false and there's no path back to the app
3. **Back = hard kill** — double-back calls `stopService()` + `finishAffinity()`, removing all background capability

## Plan

### Fix 1: Tap-to-Open — `setContentIntent`

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt`
**Location:** `buildNotification()`, before `return builder.build()`

Add a `PendingIntent.getActivity()` to `MainActivity`:

```kotlin
val openIntent = Intent(this, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
val pendingIntent = PendingIntent.getActivity(
    this, 0, openIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
builder.setContentIntent(pendingIntent)
```

`SINGLE_TOP | CLEAR_TOP` brings existing activity to front instead of creating a duplicate.

Requires adding `import ykws.android.maro.MainActivity` to `TrackRecordingService.kt`.

### Fix 2: Post-Kill Resilience

**2a. Persist `lastKnownOnWater`**

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt`

- In `onCreate()`: read `lastKnownOnWater` from `SharedPreferences` (key: `pref_last_water_state`, default `false`)
- In `onStartCommand()`: on water-state toggle, write to `SharedPreferences` before firing broadcast

**2b. Orphan detection on recreate**

- In `onCreate()`: lightweight file-existence check — scan `filesDir/tracks/` for `*.checkpoint` files via `File.listFiles()` (no protobuf deserialization, no blocking I/O risk)
- Set `hasOrphans: Boolean` field
- In `buildNotification()`: `val recLabel = if (hasOrphans) "Recovery available" else if (isRecording) "Recording" else "Ready"`
- Tapping notification (Fix 1) opens app which triggers full `TrackRepository.recoverOrphanedCheckpoints()` in `TrackViewModel`

> **Why not `TrackRepository.recoverOrphanedCheckpoints()` directly?** `Service.onCreate()` runs on the main thread. Full protobuf deserialization of checkpoint files risks ANR. A lightweight `File.listFiles { it.extension == "checkpoint" }` is sub-millisecond and sufficient to signal "recovery needed."

### Fix 3: Recording-Aware Exit on Double-Back

**File:** `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
**Location:** double-back handler (~line 1385)

**Logic:**
1. First back press → existing "Press back again to exit" toast (no change)
2. Second back press within window:
   - **If NOT recording**: current behavior — `stopService()` + `finishAffinity()` (no change)
   - **If recording**: show an `AlertDialog`:
     - Title: "Recording in progress"
     - Message: "A track is being recorded. What would you like to do?"
     - "Stop & Exit" → stops recording, stops service, finishes
     - "Keep Recording" → `moveTaskToBack(true)` — app goes to background, recording continues via foreground service

This avoids accidentally killing a recording with a muscle-memory double-back, while keeping the clean exit path when nothing is active.

If the user chooses "Keep Recording", the notification stays, GPS runs, recording continues. Return via notification tap (Fix 1) or recent apps.

## What Does NOT Change

| Element | Rationale |
|---------|-----------|
| `START_STICKY` | Already correct — service shell recreates post-kill |
| "Stop" action button | Already works via `StopRecordingReceiver` → `activeRecorder?.stop()` |
| Notification channel | Silent, `IMPORTANCE_DEFAULT`, appropriate for nav app |
| 5s update throttle | Already in `MapScreen` recording update loop |
| `StopRecordingReceiver` | Manifest-registered bridge, survives process death |

## Files Touched

| File | Change |
|------|--------|
| `TrackRecordingService.kt` | Fix 1: add `setContentIntent`. Fix 2a: persist `lastKnownOnWater`. Fix 2b: orphan check on create |
| `MapScreen.kt` | Fix 3: `moveTaskToBack` instead of `stopService`+`finishAffinity` |

No new files, no new permissions, no manifest changes.
