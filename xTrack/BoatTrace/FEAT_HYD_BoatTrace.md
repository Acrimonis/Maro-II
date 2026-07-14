# BoatTrace — Hydration Snapshot

**Baked at:** 2026-07-14 09:22 UTC
**Active Subfeature:** notif-lifecycle (notification lifecycle hardening: tap-to-open, post-kill resilience, recording-aware exit)
**Branch:** feature/notif

## Session Summary

**Notification Lifecycle Hardening — IMPLEMENTED.** Three fixes across 2 files, BUILD SUCCESSFUL.

### Fix 1: Tap-to-Open
`TrackRecordingService.buildNotification()` now calls `builder.setContentIntent()` with `PendingIntent.getActivity(MainActivity)`, `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`. Tapping the persistent notification opens the app.

### Fix 2: Post-Kill Resilience
- `lastKnownOnWater` persisted to SharedPreferences (`"maro_service_prefs"`, key `"pref_last_water_state"`) — read in `onCreate()`, written on every toggle in `onStartCommand()`. Survives process death.
- Lightweight orphan checkpoint scan in `onCreate()`: `File.listFiles(FileFilter { it.extension == "checkpoint" })` — no protobuf I/O. Sets `hasOrphans` flag. `buildNotification()` shows `"Recovery available"` label when orphans exist.

### Fix 3: Recording-Aware Exit Dialog
Double-back while recording shows `AlertDialog` ("Stop & Exit" / "Keep Recording") instead of auto-killing the recording. "Keep Recording" calls `moveTaskToBack(true)` — app backgrounds, foreground service + GPS + recording continue. Non-recording double-back unchanged.

## Key Files (modified)

- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — Fixes 1, 2a, 2b
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Fix 3

## Plan

- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_notif-lifecycle-hardening.md`
