# BoatTrace — Hydration Snapshot

**Baked at:** 2026-07-14 08:02 UTC
**Active Subfeature:** more-stuff (implemented — GPX round-trip)
**Branch:** feature/track-save-finalization

## Session Summary

Implemented GPX extension round-trip export/import:

**Export:** `GpxExporter.toGpx()` now includes `<maro:data>` base64-encoded protobuf blob in GPX `<extensions>`. Full Track fidelity including BoatMarkers, pinned state, color, durations. Standards-compatible — other tools ignore unknown extensions.

**Import:** New `GpxImporter` — parses GPX, extracts MaroII blob for lossless round-trip, falls back to standard `<trkpt>` for foreign files. ZIP archive support with path-traversal guard. Name-based anti-collision (`"Name (2)"`).

**Multi-select ZIP:** Filenames use sanitized track names with `ZipEntry.time` set to track `startTimeMs`.

**UI:** Import button in track list header via new `headerActions` slot in `ListOverlayScaffold`. File picker via `ActivityResultContracts.OpenDocument`. Toast feedback.

**Files:** 10 changed (GpxExporter, GpxImporter new, ListAction, TrackViewModel, ListOverlayScaffold, TrackHistoryOverlay, OverlayLayer, MapScreen, strings EN+FR).

**Build:** ✅

## Previous Session (boat-markers)

BoatMarker infrastructure fully implemented — design finalised, all 12 steps coded and built:

### Data model
- `BoatMarkerTrigger`, `MarkerSnapshot`, `BoatMarker` (ProtoNumber 1-8, including `autoMarkerId`)
- `IdleCaptureResult`, `IdleThresholdCallback` interface, `IdleSessionContext` class
- Track `@ProtoNumber(16) boatMarkers: List<BoatMarker>`
- TrackEvent: `IdlePeriodStarted/Completed`, `DrawerAutoOpenRequested/CloseRequested`
- UserMarker: `MarkerOrigin` enum, `origin`, `keepable` fields

### Logic
- TrackRecorder: idle threshold timer (60s default), session context lifecycle, BoatMarker append/close with checkpoint saves, `addManualBoatMarker()`, `setBoatMarkerAutoMarkerId()`, `setActiveSessionAutoMarkerId()`
- TrackViewModel: persistent `_events` MutableSharedFlow with forwarding coroutine, passthrough methods, idle callback wiring
- MarkersViewModel: `whereAmISync()`, `addTempAutoMarker()`, `confirmAutoMarker(id, name, desc)`, top-level `toMarkerSnapshot()`

### UI
- MapScreen: idle callback (whereAmISync snapshots + drawer auto-open), event observation (IdlePeriodStarted → temp 🕐 pin, IdlePeriodCompleted → confirm/delete), MANUAL boat-marker button snapshots, startup cleanup of `keepable=false`
- MarkerOverlay: proximity ring suppressed for IDLE_AUTO, icon tooltip suppressed, icon opacity from config
- ICON_SET: 16 icons, 4×4 grid (🐬🐚🏖️🕐)
- History tracks: 🕐 pins rendered with track polyline transparency

### Config
- `track.boatMarker.autoMarker.idleThresholdSec=10`, `minDurationSec=30`, `opacity=50`
- Single `maro.properties` in assets (root copy deleted, `syncMaroProperties` Gradle task removed)

## Key Files (implemented)

### New
- `app/src/main/java/ykws/android/maro/data/track/BoatMarker.kt`
- `app/src/main/java/ykws/android/maro/data/track/IdleThresholdCallback.kt`
- `app/src/main/java/ykws/android/maro/data/track/IdleSessionContext.kt`

### Modified
- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — ProtoNumber 16
- `app/src/main/java/ykws/android/maro/data/track/TrackEvent.kt` — 4 new events
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — MarkerOrigin + keepable
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — idle timer, BoatMarker lifecycle
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — events forwarding, passthrough
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — whereAmISync, auto-marker CRUD
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — callback wiring, event observation
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — proximity/tooltip/opacity
- `app/src/main/java/ykws/android/maro/ui/map/IconPickerDialog.kt` — 16 icons
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — parsed config
- `app/src/main/assets/maro.properties` — auto-marker config keys
- `app/build.gradle.kts` — single maro.properties reference

### Deleted
- `maro.properties` (root — consolidated to assets)

## Next Steps
- [ ] Build + deploy to device
- [ ] E2E: Verify idle auto-marker 🕐 pin appears during recording
- [ ] E2E: Verify proximity ring hidden, icon dimmed
- [ ] E2E: Verify history track shows 🕐 pins at idle positions
