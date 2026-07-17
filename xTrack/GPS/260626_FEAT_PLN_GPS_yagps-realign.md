# GPS — Re-align feature/yagps-fix with #118 yarefact

> **Feature:** GPS | **Subfeature:** yagps-fix
> **Created:** 2026-06-26 08:32 | **Status:** Plan

## Background

The `feature/yagps-fix` branch was created from `origin/develop` which contains commit `5c9bd9e` ("Feature/gps refact (#118)" — the yarefact consolidation). The branch already has all the #118 source files, but the MapScreen.kt TrackSample combine flow was partially reverted by the fan-marks merge (#124). Several #118 features need re-alignment.

## Gap Analysis (#118 commit vs current code)

| # | Feature | #118 | Current | Status |
|---|---------|------|---------|--------|
| 1 | `isEstimating` guard | `return@combine null` | Restored ✓ | ✓ |
| 2 | `hasLock = isGps` | Demo=false, GPS=true | `hasLock = true` | ✗ |
| 3 | `setStoppedSource` | `trackViewModel.setStoppedSource(viewModel.isStopped)` | Missing | ✗ |
| 4 | Periodic demo feed loop | `while(true) { feedDemoPosition }` | Missing | ✗ |
| 5 | `onCenterChanged` feed | `viewModel.feedDemoPosition(lat, lon)` | Missing | ✗ |
| 6 | Incremental polyline | `newPointStream.collect { addPoint }` | Missing | ✗ |
| 7 | Active trace state read | `snapshotFlow { trackRecorderState.state }` | `snapshotFlow { trackRecorderState }` | ✗ |
| 8 | TrackViewModel | `setStoppedSource`, `newPointStream` | ✓ Already correct | ✓ |
| 9 | TrackRecorder | `isStopped`, `_newPoint`, `GpsFix→TrackSample` | ✓ Already correct | ✓ |
| 10 | NavigationViewModel | Full file present | ✓ Already present | ✓ |

## Changes (MapScreen.kt only)

### 2. `hasLock = isGps` (line ~597)
Change `hasLock = true` → `hasLock = isGps`. In demo mode, track samples correctly report no GPS lock (spike rejection knows it's demo).

### 3. `setStoppedSource` (after `startRecorder`)
Add `trackViewModel.setStoppedSource(viewModel.isStopped)` right after `trackViewModel.startRecorder(sampleFlow, appSettings)`.

### 4. Periodic demo position feed loop (after setStoppedSource)
Add `while(true)` loop that re-feeds `viewModel.feedDemoPosition(center.lat, center.lon)` every 1s when GPS mode is off. This keeps the adaptive stop-detection timer advancing in demo mode even when the user has stopped panning.

### 5. `onCenterChanged` demo feed (line ~729)
Add `if (!appSettings.gpsMode) viewModel.feedDemoPosition(lat, lon)` inside `onCenterChanged`. Already captures `appSettings` via remember key.

### 6. Incremental polyline via `newPointStream` (after active trace)
Replace the current `snapshotFlow { trackRecorderState }` block that reads `recordingPoints` with two LaunchedEffects:
- One keyed on `trackRecorderState.state` to create/remove the polyline
- One keyed on `trackRecorderState.state` to collect `newPointStream` and append points incrementally

### 7. Active trace state fix
Change `snapshotFlow { trackRecorderState }` to `snapshotFlow { trackRecorderState.state }` — only the `.state` field matters for create/remove lifecycle.

## Files Changed
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — 6 specific edits

## Build
- assembleDebug expected ✅
