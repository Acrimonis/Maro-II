# Hydration — GPS

**Last Bake:** 2026-07-18 05:12
**State:** resolution-display: 7 items implemented across 6 files. Build SUCCESSFUL. On-device car-speed verification pending.

## Summary
- **resolution-display** — GPS interval 2→1s, minDistance 5→0m (doubles fix rate). Boat/land speed caps → maro.properties (32/90 kn via propDouble). Land detection 5→2 rejections. Gate 2 speed-gate: bypass sideways ×0.5 at ≥10 kn GPS speed. Accuracy gate speed-aware: 50m moving / 30m stationary. Trailing polyline: semi-transparent segment from last accepted point to _displayPosition at 20 Hz.
- **poor-reception** — 9 items: accuracy plumbing, speed/accuracy-aware policy, IDLE floor, accuracy recording gate, extended dedup, WEAK icon, BoatMarker merge.
- **checks** — _displayPosition + 20 Hz DR + setCenter map smoothness.
- **fix-spike** — Fix A-D: dedup, stale-timeout, GPS speed gate, stationary drift cap.
- yarefact Phase A+B+C — TrackSample, unified isStopped, incremental polyline
- background-recording — ON_PAUSE GPS-kill gated behind recording state
- **gps-background** — FGS location, bg permission, notification Stop, battery exemption, recording-aware exit guard, crash-resilient resume w/ GAP markers.
- Build: ✅ SUCCESSFUL

## Modified Files (6 — this session)
- `SettingsManager.kt` — gpsActiveIntervalSec 2→1, gpsActiveMinDistanceM 5f→0f
- `maro.properties` — tracking.boatMaxSpeedKn=32, tracking.landMaxSpeedKn=90
- `build.gradle.kts` — TRACKING_BOAT_MAX_SPEED_KN, TRACKING_LAND_MAX_SPEED_KN (propDouble)
- `TrackRecorder.kt` — caps→BuildConfig, LAND_DETECTION 5→2, Gate 2 speed-gate, accuracy gate speed-aware
- `NavigationViewModel.kt` — exposed displayPosition StateFlow
- `MapScreen.kt` — trailing polyline LaunchedEffect (title "track_trailing")

## Next Step
On-device car-speed verification: highway, turns, stops, acceleration.
