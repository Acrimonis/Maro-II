# Hydration: Ui_General

**Session:** notification-lifecycle — implemented. Foreground notification follows recording state (task-removed + idle → stopSelf; recording → notification + background recording persist via service-owned recorder + GPS). Double-back exit dialog: Save track / Continue recording / Discard track. Idle BoatMarker + title polling re-wired via process-scoped WhereAmIProvider. Startup NPE fixed (service deps `by lazy`). BUILD SUCCESSFUL; verified on-device (no crash).

**Target files:**
- `TrackRecordingService.kt`, `TrackViewModel.kt`, `TrackRecorder.kt`, `MapScreen.kt`, `StopRecordingReceiver.kt`, `WhereAmIProvider.kt`

**Plans:**
- `xTrack/Ui_General/260815_FEAT_PLN_Ui_General_notification-lifecycle.md`

**Last Bake:** 2026-08-15 14:13 UTC
