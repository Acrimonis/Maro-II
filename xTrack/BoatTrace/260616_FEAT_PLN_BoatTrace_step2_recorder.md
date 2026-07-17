# BoatTrace Step 2 — Recording Subsystem Implementation

**Date:** 2026-06-15  
**Status:** ✅ Complete — all requirements met, bugs fixed, validated

## Scope

Implement the tack recording subsystem: geofence auto-detection, coroutine state machine, protobuf persistence, and ViewModel bridge for Compose UI.

## Files Created (7 source + 4 test)

### Source Files (`app/src/main/java/ykws/android/maro/data/track/`)

#### `TrackGeofenceChecker.kt`
- Object with Haversine `distanceM()` and `isInsideGeofence()` functions
- Used by `TrackRecorder` to detect Port Salis departure/return

#### `TrackRecorder.kt`
- Coroutine-based state machine: `IDLE → RECORDING ↔ PAUSED → FINALIZING → IDLE`
- 10-second speed debounce (2.5 kn threshold) prevents GPS glitch starts
- 30-second checkpoint saves for crash recovery during RECORDING
- Continuous stats accumulation: `maxSpeedKn`, `avgSpeedKn`, `cumulativeDistanceNm`
- Demo mode bypasses geofence (triggers on speed alone)
- `@Volatile` state field for thread safety between collector and manual API
- Elapsed timer freezes during PAUSED state (doesn't tick while paused)
- `TrackRecorderUiState` data class exposes state + stats to UI

#### `TrackRepository.kt`
- Protobuf file CRUD on `Dispatchers.IO` (save, load, list, delete, updateMetadata)
- Index file (`index.bin`) for fast listing via `TrackSummaryList` wrapper
- Checkpoint support: `saveCheckpoint()`, `deleteCheckpoint()`
- `recoverOrphanedCheckpoints()` — scans for `*_checkpoint.bin` on startup, finalizes orphaned tacks
- Index rebuild from `.bin` files on corruption
- `File`-based constructor enables temp-directory testing

#### `TrackViewModel.kt`
- `StateFlow<TrackRecorderUiState>` bridge for Compose UI
- Observes `SettingsManager.trackEnabled` toggle
- Auto-creates `TrackRecorder` with settings from `AppSettings` (origin, radius)
- Manual start/stop delegations
- Startup recovery: calls `recoverOrphanedCheckpoints()` on init
- `refreshSummaries()`, `loadTackDetail()`, `deleteTack()`, `updateTack()` methods

#### `Track.kt`
- `@Serializable @ProtoNumber data class Track` — 11 proto fields including `visibleOnMap`, `averageSpeedMps`
- `@Serializable data class TrackSummary` — 7 fields with explicit `@ProtoNumber` annotations
- `TrackSummaryList` wrapper for index serialization

#### `TrackPoint.kt`
- `@Serializable @ProtoNumber data class TrackPoint` — 5 fields (lat, lon, speedMps?, bearingDeg?, timeOffsetSec)

#### `TrackEvent.kt`
- `sealed class TrackEvent` — Started, Paused, Resumed, Stopped, PointCaptured

### Test Files (`app/src/test/java/ykws/android/maro/data/track/`)

#### `GeofenceCheckerTest.kt` (8 tests)
- Known-distance verification, inside/outside/edge, symmetry, exact-radius boundary

#### `TrackRecorderTest.kt` (15 tests)
- All state transitions: manual start/stop, speed gating, PAUSED→RECORDING resume, auto-start debounce, point accumulation, double-start guard

#### `TrackRepositoryTest.kt` (10 tests)
- Save/load/list/delete, updateMetadata, checkpoint lifecycle, corrupted file handling, orphaned checkpoint recovery

#### `TrackSerializerTest.kt` (4 tests)
- Protobuf round-trip with full/null/empty data

### Modified Files

- `app/build.gradle.kts` — 4 BuildConfig fields (TRACK_ENABLED_DEFAULT, TRACK_ORIGIN_LAT/LON, TRACK_GEOFENCE_RADIUS_M)
- `maro.properties` — 4 tack config properties
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — AppSettings fields, persistence, SettingsProvider interface
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt` — timestampEpochMs on GpsFix

## Review & Validation Cycle

| Phase | Result |
|-------|--------|
| Initial review (Ask) | 3 bugs found: elapsed timer drift, orphaned recorder, missing @OptIn + minor @ProtoNumber |
| Fix round | All 4 fixed ✅ |
| Validation (Ask) | 2 major gaps (speed stats, process-death recovery) + 3 minor gaps |
| Validation fix round | All 5 gaps fixed ✅ |
| Final review (Ask) | All clear — no remaining issues ✅ |
| Build (`assembleDebug`) | ✅ BUILD SUCCESSFUL |
| Tests (`testDebugUnitTest`) | ✅ 269 tests, 0 new failures |

## Next Step

Proceed to **Step 3: ui-integration** — wire into map overlay, settings UI, TrackStatusIcon, Track Drawer, TrackHistoryOverlay, GPX export.
