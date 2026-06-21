# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-21 08:45 UTC

## Session Summary

Completed gps-background, settings-page-rules, gps-line-acquisition subfeatures.

### gps-line-acquisition (2026-06-20)
- Removed PASSIVE_PROVIDER listener from GpsLocationSource — dual listener race caused zigzag ordering on active track polyline
- Added 50 kn implied-speed spike gate to TrackRecorder.addPoint() — rejects GPS multipath spikes

### gps-background (2026-06-20)
- Rewrote TrackRecordingService as always-on foreground service (specialUse type)
- Started in MainActivity.onCreate, stopped on double-back exit via MapScreen BackHandler
- Notification: "Maro II — Ready" (or "Ready (Demo)") / "Maro II — Recording • {speed} kn • {elapsed} • {dist} nm" (or "Recording (Demo)")
- Added currentSpeedKn to TrackRecorderUiState
- MapScreen LaunchedEffect sends notification updates every 5s during recording
- Channel maro_persistent with IMPORTANCE_DEFAULT
- Notifications updates re-fire on GPS↔demo toggle (LaunchedEffect keyed on both trackRecorderState + gpsMode)
- Fixed: Android 14 FGS permissions — specialUse + FOREGROUND_SERVICE_SPECIAL_USE

### settings-page-rules (2026-06-20)
- ColorSwatchRow/ColorSwatchPairRow padding: 2dp → 8dp
- Replaced 13 hairline divider Boxes with Spacer(8.dp)
- SettingsExpander labels and SettingsToggleRow usage were already compliant
- Removed dead FOREGROUND_SERVICE_LOCATION from manifest

## Key Files Modified
- GpsLocationSource.kt — removed passive listener
- TrackRecorder.kt — currentSpeedKn, spike gate
- TrackRecordingService.kt — full rewrite
- MainActivity.kt — startForegroundService
- MapScreen.kt — LaunchedEffect, BackHandler stopService, settings rendering rules
- AndroidManifest.xml — specialUse, FOREGROUND_SERVICE_SPECIAL_USE, removed FOREGROUND_SERVICE_LOCATION

## Next Steps
- [ ] E2E verification on device
- [ ] Track list UI polish per FEAT_PLN_BoatTrace_TrackList_Design.md
- [ ] settings-page-rules R4 font tokens (deferred)
- [ ] Commit pending changes
