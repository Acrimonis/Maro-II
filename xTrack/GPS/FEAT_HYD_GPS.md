# Hydration — GPS

**Last Bake:** 2026-07-14 08:41
**State:** checks: implemented, built. 2 files, ~60 lines.

## Summary
- **checks** — Unified continuous dead reckoning + setCenter map smoothness. `_displayPosition` StateFlow + 20 Hz DR coroutine (gated <3kn, capped 30m). `cameraUpdates` reads `_displayPosition`. MapScreen: `setCenter` replaces `animateTo`. `_gpsPosition` stays raw truth.
- **fix-spike** — Fix D: dedup identical positions within 500ms. Fix A: stale-timeout two-tier cap (48/96 kn) + course clear. Fix B: GPS-reported speed gate (40 kn, !isOnLand guard). Fix C: stationary distance cap (150m at <2 kn). Bonus: fixed dead sea-recovery (`landModeAcceptCounter`), added missing `checkLandDetection` calls, reset `consecutiveRejections` on stale/Gate 0 acceptance.
- yarefact Phase A+B+C — TrackSample, unified isStopped, incremental polyline
- background-recording — ON_PAUSE GPS-kill gated behind recording state
- **gps-background** — A1: FGS location + FOREGROUND_SERVICE_LOCATION. A2: ACCESS_BACKGROUND_LOCATION + native permission dialog. A3: notification Stop via StopRecordingReceiver→service pattern. A4: battery exemption dual-trigger prompt. B1: recording-aware BackHandler (red border, ⚠, 300ms delay). C2: recovery dialog Continue/Save, full recorder resume with Resumed event for polyline restore. C3: GAP markers + DashPathEffect on live+saved tracks.
- Fixes: stale remember closure, Coastline→Navigation rename, notification explicit intent, onDismiss→save, background location native dialog, eventsForwardingJob ordering, Resumed polyline creation.
- Build: ✅ SUCCESSFUL

## Modified Files (16)
- `AndroidManifest.xml` — FGS location, FOREGROUND_SERVICE_LOCATION, ACCESS_BACKGROUND_LOCATION, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, StopRecordingReceiver
- `TrackRecordingService.kt` — ACTION_STOP_RECORDING, activeRecorder, notification Stop action, onStartCommand handler
- `StopRecordingReceiver.kt` — new manifest BroadcastReceiver
- `TrackRecorder.kt` — resume() with gap detection, detectAndInsertGap(), Resumed event emission
- `TrackViewModel.kt` — resumeOrphanedCheckpoint (full resume), cachedSettings, activeRecorder wiring, eventsForwardingJob ordering
- `TrackPoint.kt` — PointType enum (NORMAL/GAP), type field ProtoNumber 10
- `TrackEvent.kt` — Resumed(points) event
- `MapScreen.kt` — BackHandler (recording-aware), exit banner (red border), recovery dialog (Continue/Save), bgLocationLauncher, battery prompt, Resumed handler (polyline restore), GAP polyline (live+saved)
- `MainActivity.kt` — BatteryOptimizationDialog (stringResource), battery prompt on startup
- `AppConfig.kt` — trackingGapDistanceThresholdM, trackingGapTimeThresholdSec
- `maro.properties` — tracking.gapDistanceThresholdM=200, tracking.gapTimeThresholdSec=120
- `strings.xml` (EN+FR) — 10 new strings

## Next Step
On-device verification: crash during recording → restart → Continue → verify track resumes with polyline restored.
