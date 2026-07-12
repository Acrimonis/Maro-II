# BoatTrace — Hydration Snapshot

**Baked at:** 2026-07-14 08:45 UTC
**Active Subfeature:** merge-tracks (Resume + Merge implemented, checkmark bottom-right + unified timestamp)
**Branch:** feature/merge-tracks

## Session Summary

**Resume Existing Track — IMPLEMENTED.** TrackRecorder.resume() extended with `fromCheckpoint` flag (default true). Gap-aware: `resumeGapDurationSec` computed from inter-session wall-clock gap, subtracted from navigating duration at finalize. Title pattern guard preserves user-edited names. `visibleOnMap` forced true on resume + finalize. New `TrackViewModel.resumeTrack(trackId)` — 9-step setup mirroring `resumeOrphanedCheckpoint()`. ▶ PlayArrow icon on finalized track cards in TrackHistoryOverlay. Two post-implementation fixes: (1) `stopRecording()` invalidates `trackDetailCache` to prevent stale map polylines, (2) `onResumeTrack` dismisses overlay after tap. 4 files changed, BUILD SUCCESSFUL.

**Merge Tracks — IMPLEMENTED.** New `TrackMerger` utility — concatenate points with `timeOffsetMs` rebasing, GAP markers between segments, renumbered BoatMarkers, synthesized stats. `TrackViewModel.mergeTracks(ids, name, keepOriginals)`. Multi-select UI via existing `ListOverlayScaffold` with `MultiActionSpec("merge")` — self-contained AlertDialog with auto-generated name and "Keep original tracks" checkbox. New `ListAction.MergeTracks` + `TrackEvent.TracksMerged`. Scaffold extended with `confirmContent` slot.

**Checkmark bottom-right — IMPLEMENTED.** Multi-select check mark badge moved from `Alignment.TopEnd` to `Alignment.BottomEnd` in `SwipeableItemCard` (`ListOverlayScaffold.kt`). Reduces visual collision with card titles.

**Unified apk timestamp — IMPLEMENTED.** New `_timestamp.bat` helper — locale-independent `yyMMMdd HH:mm:ss` via `for /f` parsing. `apk-push.bat`, `apk-deploy.bat`, `apk-build.bat` all call it for completion timestamps. apk-push.bat literal `!` escape fixed.

**Merge conflict resolution:** Absorbed `gpx-extension-roundtrip` subfeature (GPX `<maro:data>` blob, `GpxImporter`, ZIP import/export, `headerActions` scaffold slot) and `more-stuff` subfeature from develop into BoatTrace feature file. Build: ✅

**Design decisions (Resume):**
- fromCheckpoint flag on resume() — default true for backward compat
- resumeGapDurationSec prevents navigating-duration inflation across session gaps
- Title pattern guard (D6): only auto-rename if name matches `yyyy-MM-dd HH:mm`
- visibleOnMap forced true (D7): user explicitly chose to resume
- Concurrent guard: two-level (ViewModel state check + UI hides button)

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
- `app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt`
- `app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt`
- `_timestamp.bat`

### Modified
- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — ProtoNumber 16
- `app/src/main/java/ykws/android/maro/data/track/TrackEvent.kt` — 4 new events + TracksMerged
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — MarkerOrigin + keepable
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — idle timer, BoatMarker lifecycle, resume, spike rejection
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — events forwarding, passthrough, resumeTrack, mergeTracks
- `app/src/main/java/ykws/android/maro/data/model/MultiActionSpec.kt` — confirmContent slot
- `app/src/main/java/ykws/android/maro/data/model/ListAction.kt` — MergeTracks action
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — whereAmISync, auto-marker CRUD
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — callback wiring, event observation
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — proximity/tooltip/opacity
- `app/src/main/java/ykws/android/maro/ui/map/IconPickerDialog.kt` — 16 icons
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — resume wiring
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — resume icon, merge action, headerActions, import
- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — confirmContent, headerActions, checkmark BottomEnd
- `app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt` — maro:data blob
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — parsed config
- `app/src/main/assets/maro.properties` — auto-marker config keys
- `app/build.gradle.kts` — single maro.properties reference
- `apk-push.bat`, `apk-deploy.bat`, `apk-build.bat` — unified timestamp

### Deleted
- `maro.properties` (root — consolidated to assets)

## Next Steps
- [ ] Build + deploy to device
- [ ] E2E: Verify idle auto-marker 🕐 pin appears during recording
- [ ] E2E: Verify proximity ring hidden, icon dimmed
- [ ] E2E: Verify history track shows 🕐 pins at idle positions
