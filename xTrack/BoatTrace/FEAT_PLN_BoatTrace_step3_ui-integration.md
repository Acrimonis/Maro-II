# BoatTrace Step 3 — UI Integration Implementation

**Date:** 2026-06-16  
**Status:** ✅ Complete — all requirements met, reviewed and validated

## Scope

Wire the BoatTrace recording subsystem into the Compose UI: map overlay rendering, settings controls, recording status icon, track drawer, track history screen, and GPX export.

## Files Created (5 new)

### `app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt`
- 👣 footprint composable with 3 states: IDLE (dimmed grey), RECORDING (pulsing green dot via `InfiniteTransition`), PAUSED (amber dot)
- Clickable — opens TrackDrawer on tap
- Positioned in top-left icon row on MapScreen

### `app/src/main/java/ykws/android/maro/ui/map/TrackDrawer.kt`
- `ModalBottomSheet` with `skipPartiallyExpanded = true`
- Header "Track Recording"
- Start/Stop Recording button (label and color changes based on state)
- "Track List" shortcut button → opens `TrackHistoryOverlay`
- Live stats panel: elapsed time, point count, distance (nm), max/avg speed (kn)
- All stats driven by `StateFlow<TrackRecorderUiState>` from ViewModel

### `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`
- Full-screen overlay with BackHandler support
- `LazyColumn` of track summary cards
- Each card shows: date start, editable name (tap→TextField→Done), editable comment, max speed, distance (nm)
- Visibility toggle (👁️ button per track)
- GPX share button per card
- Delete with confirmation dialog
- Stats chips: "Dist X.XX nm", "Max XX.X kn"

### `app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt`
- Converts `Track` to GPX 1.1 XML string
- Correct namespace and schemaLocation
- `<trk>` / `<trkseg>` / `<trkpt>` with `<ele>`, `<time>`, `<speed>`, `<course>`
- ISO 8601 timestamps with `Z` suffix
- XML entity escaping for name/comment

### `app/src/main/res/xml/provider_paths.xml`
- FileProvider paths for GPX sharing
- `<files-path name="gpx" path="tracks/" />`

## Files Modified (4 existing)

### `app/src/main/AndroidManifest.xml`
- Added `<provider>` for `FileProvider` (`${applicationId}.fileprovider`)

### `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- Wired `TrackViewModel` + `TrackRepository`
- Added `_gpsFixFlow` (`MutableSharedFlow<GpsFix>`) to share GPS fixes between internal pipeline and TrackViewModel
- Exposed `trackRecorderState: StateFlow<TrackRecorderUiState>` and `trackSummaries: StateFlow<List<TrackSummary>>`

### `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- Added `TrackStatusIcon` to top-left icon row
- Track overlay `LaunchedEffect`: observes `trackSummaries`, manages osmdroid `Polyline` per visible track (8f stroke, `trackColorArgb`, titled `"track_$id"`)
- Overlay cleanup protection: excludes `"track_"`-prefixed Polylines from general `removeAll`
- Uses `rememberCoroutineScope()` for GPX share (no leaked scopes)
- Direct `loadTrackSuspend()` call instead of fragile `delay(150)` heuristic
- `TrackDrawer` as modal bottom sheet (activated by status icon click)
- `TrackHistoryOverlay` as full-screen overlay
- "Track Recording" toggle in General settings tab
- "Track Configuration" read-only section showing origin lat/lon/radius

### `app/src/main/java/ykws/android/maro/data/track/Track.kt`
- Added `@ProtoNumber(12) distanceNm: Float` to `Track`
- Added `@ProtoNumber(8) comment: String` and `@ProtoNumber(9) distanceNm: Float` to `TrackSummary`

### `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
- `finalizeTrack()` now sets `distanceNm = cumulativeDistanceNm` on the saved Tack

### `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt`
- `updateIndex()` and `rebuildIndex()` propagate `comment` and `distanceNm` to `TrackSummary`

### `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`
- Added `suspend fun loadTrackSuspend(id: String): Track?` for direct async access

## Review & Validation Cycle

| Phase | Result |
|-------|--------|
| Initial review (Ask) | 3 issues: missing comment editing, leaked CoroutineScope, fragile delay(150) |
| Fix round | All 3 fixed ✅ |
| Validation (Ask) | 2 minor gaps: settings origin/radius fields missing, distance not in cards; 2 cosmetic: no dedicated setTrackVisibility, button-delete instead of swipe |
| Validation fix round | G1+G2 fixed ✅ (origin/radius in settings, distanceNm propagated end-to-end) |
| Final review (Ask) | All clear — no remaining issues ✅ |
| Build (`assembleDebug`) | ✅ BUILD SUCCESSFUL |
| Tests (`testDebugUnitTest`) | ✅ All passing (pre-existing single failure unrelated) |

## Next Step

Proceed to **Step 4: verification** — build APK, deploy to device, run E2E test scenarios.
