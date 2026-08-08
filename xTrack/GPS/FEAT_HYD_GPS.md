# Hydration — GPS

**Last Bake:** 2026-08-08 15:24
**State:** position-dash: gated onCenterChanged scroll callback during GPS auto-follow. Build SUCCESSFUL. On-device verification pending.

## Summary
- **position-dash** — Gated onCenterChanged scroll callback in MapScreen.kt during GPS auto-follow: `setMapCenterOffset` was shifting osmdroid's native `mapCenter` property, scroll events fed offset coordinate into `_mapCenter`, shore pipeline queried wrong position. Fix: skip `updateMapCenter` from scroll/zoom events when GPS auto-follow active; only accept during manual panning or demo mode. Dashboard (isOnWater, distanceToShore, zoneSituation, depthAtCenter) now uses GPS position.
- **resolution-display** — GPS interval 2→1s, minDistance 5→0m (doubles fix rate). Boat/land speed caps → maro.properties (32/90 kn via propDouble). Land detection 5→2 rejections. Gate 2 speed-gate: bypass sideways ×0.5 at ≥10 kn GPS speed. Accuracy gate speed-aware: 50m moving / 30m stationary. Trailing polyline: semi-transparent segment from last accepted point to _displayPosition at 20 Hz.
- **poor-reception** — 9 items: accuracy plumbing, speed/accuracy-aware policy, IDLE floor, accuracy recording gate, extended dedup, WEAK icon, BoatMarker merge.
- **checks** — _displayPosition + 20 Hz DR + setCenter map smoothness.
- **fix-spike** — Fix A-D: dedup, stale-timeout, GPS speed gate, stationary drift cap.
- yarefact Phase A+B+C — TrackSample, unified isStopped, incremental polyline
- background-recording — ON_PAUSE GPS-kill gated behind recording state
- **gps-background** — FGS location, bg permission, notification Stop, battery exemption, recording-aware exit guard, crash-resilient resume w/ GAP markers.
- Build: ✅ SUCCESSFUL

## Modified Files (1 — this session)
- `MapScreen.kt` — gated `onCenterChanged` callback: skip `updateMapCenter`/`depthViewModel.updateMapCenter` during GPS auto-follow (`!appSettings.gpsMode || viewModel.autoFollowSuppressed.value`)

## Next Step
On-device verification: GPS mode with offset active — confirm dashboard values match marker position.
