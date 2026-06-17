# BoatTrace Step 1 — Data Model Implementation

**Date:** 2026-06-15  
**Status:** ✅ Complete — all requirements met, reviewed and validated

## Scope

Implement the data model layer for the BoatTrace feature: data classes with `kotlinx-serialization-protobuf` annotations, configuration properties, settings persistence, and timestamp tracking on `GpsFix`.

## Files Created (4)

### `app/src/main/java/ykws/android/maro/data/tack/TackPoint.kt`
- `@Serializable @ProtoNumber` data class with 5 proto-indexed fields:
  - `lat: Double` (1), `lon: Double` (2) — WGS84 position
  - `speedMps: Float?` (3), `bearingDeg: Float?` (4) — nullable GPS telemetry
  - `timeOffsetSec: Int` (5) — seconds since tack start (relative, saves ~5 bytes/pt)

### `app/src/main/java/ykws/android/maro/data/tack/Tack.kt`
- `@Serializable @ProtoNumber` data class with 10 proto-indexed fields:
  - `id: String` (1), `name: String` (2), `comment: String` (3)
  - `startTimeMs: Long` (4), `endTimeMs: Long?` (5)
  - `pausedDurationSec: Long` (6), `fastestSpeedMps: Float` (7)
  - `trackColorArgb: Int` (8), `tackPoints: List<TackPoint>` (9)
  - `visibleOnMap: Boolean` (10, default true)
- Also contains non-serializable `TackSummary` data class for list display

### `app/src/main/java/ykws/android/maro/data/tack/TackEvent.kt`
- Sealed class with 5 event variants:
  - `Started`, `Paused`, `Resumed`, `Stopped` — state machine triggers
  - `PointCaptured(point: TackPoint)` — data-carrying event

### `app/src/test/java/ykws/android/maro/data/tack/TackSerializerTest.kt`
- Protobuf round-trip encode/decode for `TackPoint` + `Tack`
- Edge cases: empty `tackPoints` list, null `endTimeMs`, null `speedMps`/`bearingDeg`
- `TackSummary` construction verification

## Files Modified (4)

### `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`
- Added `timestampEpochMs: Long = System.currentTimeMillis()` field to `GpsFix`
- Updated `emitFix()` to pass `System.currentTimeMillis()`
- Added `lastTimestamp` tracking variable; updated `emitNoLock()` to use it

### `maro.properties` (root)
- Appended 4 tack properties:
  - `tack.originLat.default=43.55`, `tack.originLon.default=7.00`
  - `tack.geofenceRadiusM=500`, `tack.enabled.default=false`

### `app/build.gradle.kts`
- Added 4 `BuildConfig` fields via `propDouble()`/`propBool()`:
  - `TACK_ORIGIN_LAT`, `TACK_ORIGIN_LON`, `TACK_GEOFENCE_RADIUS_M`, `TACK_ENABLED_DEFAULT`

### `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- Added 4 fields to `AppSettings`: `tackEnabled`, `tackOriginLat`, `tackOriginLon`, `tackGeofenceRadiusM`
- Added corresponding reads in `load()`, writes in `update()`, key constants in `companion object`

## Validation

| Check | Result |
|-------|--------|
| Review (Ask mode) | ✅ All clear — code is correct, follows project patterns |
| Validation against spec | ✅ All 10 requirements met |
| Build (`assembleDebug`) | ✅ BUILD SUCCESSFUL |

## Next Step

Proceed to **Step 2: recorder** — implement `TackGeofenceChecker`, `TackRecorder` (coroutine state machine), `TackRepository` (protobuf CRUD), `TackViewModel` (StateFlow bridge).
