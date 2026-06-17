# BoatTrace — Fresh Import Implementation Plan

> **Feature:** BoatTrace | **Branch:** `feature/new-tracking`
> **Strategy:** Full fresh implementation (no cherry-pick) on current branch

---

## Naming Convention

**Decision:** Use **`Track`** prefix throughout. Feature is BoatTrace = it records *tracks*.

| Concept | Class/File |
|---------|-----------|
| Single GPS point in a track | `TrackPoint` |
| A recorded journey | `Track` |
| State machine | `TrackRecorder` |
| Persistence layer | `TrackRepository` |
| UI bridge | `TrackViewModel` |

---

## Step 1 — Data Model Layer

### 1a. Add `timestampEpochMs` to `GpsFix`

**File:** [`app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt)

- Add field `val timestampEpochMs: Long = System.currentTimeMillis()` to `GpsFix` data class (line ~29)
- **Decision:** Use phone clock (`System.currentTimeMillis()`) at the moment the GPS fix is read — not `loc.time`. This keeps all timestamps consistent with user-facing phone time, avoids timezone confusion, and aligns with other app timestamps.
- Capture `System.currentTimeMillis()` at entry to `emitFix()` and `emitNoLock()` and pass as the `timestampEpochMs` value
- Track `lastTimestamp` variable for `emitNoLock()` fallback

### 1b. Add maro.properties entries

**File:** [`maro.properties`](maro.properties)

Append at end:
```properties
# BoatTrace / Track recording defaults
track.originLat.default=43.55
track.originLon.default=7.00
track.geofenceRadiusM=500
track.enabled.default=false
```

### 1c. Add BuildConfig fields

**File:** [`app/build.gradle.kts`](app/build.gradle.kts)

In `defaultConfig` block (after the speed-zone section, ~line 77), add:
```kotlin
// ── Track recording defaults from maro.properties ──────────
buildConfigField("double", "TRACK_ORIGIN_LAT", propDouble("track.originLat.default", 43.55).toString())
buildConfigField("double", "TRACK_ORIGIN_LON", propDouble("track.originLon.default", 7.00).toString())
buildConfigField("double", "TRACK_GEOFENCE_RADIUS_M", propDouble("track.geofenceRadiusM", 500.0).toString())
buildConfigField("boolean", "TRACK_ENABLED_DEFAULT", propBool("track.enabled.default", false).toString())
```

### 1d. Create `TrackPoint.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackPoint.kt`

```kotlin
@Serializable
data class TrackPoint(
    @ProtoNumber(1) val lat: Double,
    @ProtoNumber(2) val lon: Double,
    @ProtoNumber(3) val speedMps: Float? = null,
    @ProtoNumber(4) val bearingDeg: Float? = null,
    @ProtoNumber(5) val timeOffsetSec: Int = 0  // seconds since track start
)
```

### 1e. Create `Track.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/Track.kt`

```kotlin
@Serializable
data class Track(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val comment: String = "",
    @ProtoNumber(4) val startTimeMs: Long,
    @ProtoNumber(5) val endTimeMs: Long? = null,
    @ProtoNumber(6) val pausedDurationSec: Long = 0,
    @ProtoNumber(7) val fastestSpeedMps: Float = 0f,
    @ProtoNumber(8) val averageSpeedMps: Float = 0f,
    @ProtoNumber(9) val trackColorArgb: Int = 0xFFFF6F00.toInt(),  // amber default
    @ProtoNumber(10) val trackPoints: List<TrackPoint> = emptyList(),
    @ProtoNumber(11) val visibleOnMap: Boolean = true,
    @ProtoNumber(12) val distanceNm: Float = 0f,
    @ProtoNumber(13) val navigatingDurationSec: Long = 0   // ⛵ computed = elapsedWallClockSec - pausedDurationSec
)

@Serializable
data class TrackSummary(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val comment: String = "",
    @ProtoNumber(4) val startTimeMs: Long,
    @ProtoNumber(5) val endTimeMs: Long? = null,
    @ProtoNumber(6) val fastestSpeedMps: Float = 0f,
    @ProtoNumber(7) val distanceNm: Float = 0f,
    @ProtoNumber(8) val visibleOnMap: Boolean = true,
    @ProtoNumber(9) val navigatingDurationSec: Long = 0,   // ⛵ time under way
    @ProtoNumber(10) val pausedDurationSec: Long = 0       // ⏸️ time stopped/anchored
)

@Serializable
data class TrackSummaryList(
    @ProtoNumber(1) val tracks: List<TrackSummary>
)
```

### 1f. Duration Fields — Single Source of Truth

- `pausedDurationSec` (field #6 in `Track`, field #10 in `TrackSummary`) — accumulated pause time from the recorder state machine
- `navigatingDurationSec` (field #13 in `Track`, field #9 in `TrackSummary`) — computed at finalize: `elapsedWallClockSec - pausedDurationSec`
- Single source: `pausedDurationSec` in the recorder. No duplicate tracking.

### 1g. Create `TrackEvent.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackEvent.kt`

```kotlin
sealed class TrackEvent {
    data object Started : TrackEvent()
    data object Paused : TrackEvent()
    data object Resumed : TrackEvent()
    data object Stopped : TrackEvent()
    data class PointCaptured(val point: TrackPoint) : TrackEvent()
}
```

### 1h. Add track settings to `AppSettings`

**File:** [`app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt)

Add to `AppSettings` data class (~line 156):
```kotlin
val trackEnabled: Boolean = BuildConfig.TRACK_ENABLED_DEFAULT,
val trackOriginLat: Double = BuildConfig.TRACK_ORIGIN_LAT,
val trackOriginLon: Double = BuildConfig.TRACK_ORIGIN_LON,
val trackGeofenceRadiusM: Double = BuildConfig.TRACK_GEOFENCE_RADIUS_M,
val trackGeofenceEnabled: Boolean = true,   // gate: false = always record when speed > 2.5kn
```

Add to `load()` (~line 253):
```kotlin
trackEnabled = prefs.getBoolean(KEY_TRACK_ENABLED, BuildConfig.TRACK_ENABLED_DEFAULT),
trackOriginLat = prefs.getFloat(KEY_TRACK_ORIGIN_LAT, BuildConfig.TRACK_ORIGIN_LAT.toFloat()).toDouble(),
trackOriginLon = prefs.getFloat(KEY_TRACK_ORIGIN_LON, BuildConfig.TRACK_ORIGIN_LON.toFloat()).toDouble(),
trackGeofenceRadiusM = prefs.getFloat(KEY_TRACK_GEOFENCE_RADIUS_M, BuildConfig.TRACK_GEOFENCE_RADIUS_M.toFloat()).toDouble(),
trackGeofenceEnabled = prefs.getBoolean(KEY_TRACK_GEOFENCE_ENABLED, true),
```

Add to `update()` (~line 325):
```kotlin
.putBoolean(KEY_TRACK_ENABLED, updated.trackEnabled)
.putFloat(KEY_TRACK_ORIGIN_LAT, updated.trackOriginLat.toFloat())
.putFloat(KEY_TRACK_ORIGIN_LON, updated.trackOriginLon.toFloat())
.putFloat(KEY_TRACK_GEOFENCE_RADIUS_M, updated.trackGeofenceRadiusM.toFloat())
.putBoolean(KEY_TRACK_GEOFENCE_ENABLED, updated.trackGeofenceEnabled)
```

Add to `companion object` (~line 386):
```kotlin
private const val KEY_TRACK_ENABLED = "track_enabled"
private const val KEY_TRACK_ORIGIN_LAT = "track_origin_lat"
private const val KEY_TRACK_ORIGIN_LON = "track_origin_lon"
private const val KEY_TRACK_GEOFENCE_RADIUS_M = "track_geofence_radius_m"
private const val KEY_TRACK_GEOFENCE_ENABLED = "track_geofence_enabled"
```

---

## Step 2 — Recording Subsystem

**Note:** `trackGeofenceEnabled` (added in Step 1h) — when false, recording starts on speed > 2.5kn regardless of geofence. This gives users an "always record" option.

### Key Architectural Decisions

#### Demo Mode Interaction
- In demo mode, the `TrackRecorder` observes pan-speed-derived bearing/speed from `CoastlineViewModel.demoSpeedKnots` / `CoastlineViewModel.demoBearingDeg`
- **Position** is derived from the map center camera position (where the user is looking)
- Geofence check uses map-center position against Port Salis origin — same logic as GPS mode
- Recording triggers when demo pan-speed exceeds 2.5 kn for 10s (same debounce as GPS mode)
- Manual Start/Stop toggle in the UI drawer overrides auto-detection

#### `MutableSharedFlow<GpsFix>` Buffer Policy
```kotlin
val _gpsFixFlow = MutableSharedFlow<GpsFix>(
    replay = 1,                // new subscriber gets latest fix immediately
    extraBufferCapacity = 16,  // absorb bursts without blocking
    onBufferOverflow = BufferOverflow.DROP_OLDEST  // never block the GPS pipeline
)
```
This guarantees the GPS pipeline never stalls. Old fixes are dropped, never the latest.

#### Foreground Service Design (`TrackRecordingService`)

A foreground service keeps recording alive when the app is backgrounded.

**Lifecycle:**
- `startService()` called when `TrackRecorder` transitions IDLE → RECORDING
- `stopService()` called when recording finalizes (FINALIZING → IDLE)
- Notification channel `"track_recording"` with low importance (no sound)
- Notification shows: 👣 "Maro II — Recording track" + elapsed time

**Architecture:**
- `TrackRecordingService` extends `Service`, runs on its own coroutine scope
- Service owns the `TrackRecorder` + `TrackRepository` instances during recording
- `TrackViewModel` in the Activity binds to the service via `Binder`
- Observed via `StateFlow` through the binding interface
- Permissions: `FOREGROUND_SERVICE_DATA_SYNC` or `FOREGROUND_SERVICE_LOCATION` added to manifest

**Files:**
- `data/track/TrackRecordingService.kt` — new file (~80 lines)
- `AndroidManifest.xml` — add `<service android:foregroundServiceType="dataSync">` + `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
- `res/drawable/ic_track_notification.xml` — simple 👣 vector icon for notification

### 2a. Create `TrackGeofenceChecker.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackGeofenceChecker.kt`

- Object with Haversine `distanceM(a: LatLng, b: LatLng): Double`
- `isInsideGeofence(pos: LatLng, origin: LatLng, radiusM: Double): Boolean`

### 2b. Create `TrackRecorder.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

Coroutine-based state machine:
```
IDLE → RECORDING ↔ PAUSED → FINALIZING → IDLE
```

Key rules:
- **Movement detection** uses `AdaptiveGpsPolicy.onFix()` — position-anchored, configurable time+displacement threshold (not a raw speed gate)
- IDLE→RECORDING: `AdaptiveGpsPolicy` returns ACTIVE for 10 consecutive seconds (speed > wakeSpeed OR jumped ≥ thresholdM from anchor)
- RECORDING→PAUSED: `AdaptiveGpsPolicy` returns IDLE (stayed within thresholdM of anchor for adaptiveWindowSec)
- PAUSED→RECORDING: `AdaptiveGpsPolicy` returns ACTIVE again
- Reuses existing `AppSettings.adaptiveWindowSec` (default 30s) and `AppSettings.adaptiveDistanceM` (default 20m) — no duplicate config
- Wake speed = `AdaptiveGpsPolicy` default `0.8 m/s` (~1.55 kn)
- Demo mode: uses pan-speed + map-center position through the same policy
- 30s periodic checkpoint save for crash recovery
- Continuous stats: maxSpeedKn, avgSpeedKn, cumulativeDistanceNm
- Elapsed timer freezes during PAUSED (doesn't tick while paused)
- `@Volatile` state field for thread safety between collector and manual API

Exposes `TrackRecorderUiState` data class:
```kotlin
data class TrackRecorderUiState(
    val state: RecorderState = RecorderState.IDLE,
    val elapsedSec: Long = 0,
    val pointCount: Int = 0,
    val speedKn: Float = 0f,
    val maxSpeedKn: Float = 0f,
    val avgSpeedKn: Float = 0f,
    val distanceNm: Float = 0f
)
```

### 2c. Create `TrackRepository.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt`

Protobuf file CRUD on `Dispatchers.IO`:
- Constructor accepts `File` (tracks directory) for testability — enables temp-directory testing
- `saveTrack(track: Track)` — writes binary `.bin` file
- `loadTrack(id: String): Track?` — reads single track
- `listTracks(): List<TrackSummary>` — reads index, returns summaries
- `deleteTrack(id: String)` — removes `.bin` + updates index
- `updateMetadata(id, name, comment, visibleOnMap)` — partial update
- `saveCheckpoint(track: Track)` — mid-recording checkpoint
- `deleteCheckpoint(id: String)` — cleanup after finalize
- `recoverOrphanedCheckpoints()` — startup scan, finalizes orphaned tacks
- Index file: `index.bin` (serialized `TrackSummaryList`)
- `rebuildIndex()` — rescans `.bin` files when index is corrupted

### 2d. Create `TrackViewModel.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`

- `StateFlow<TrackRecorderUiState>` bridge for Compose UI
- Observes `SettingsManager.trackEnabled` toggle
- Auto-creates `TrackRecorder` with settings (origin, radius)
- Manual `startRecording()` / `stopRecording()` delegations
- `refreshSummaries()` — reloads track list from repository
- `loadTrackDetail(id: String): Track?` — loads full track
- `deleteTrack(id: String)` — delete + refresh
- `updateTrack(id, name, comment)` — update metadata
- `setTrackVisibility(id, visible)` — toggle map visibility
- Startup recovery: calls `recoverOrphanedCheckpoints()` on init

### 2e. Test files (4 files)

1. `TrackGeofenceCheckerTest.kt` — 8 tests: known-distance, inside/outside/edge, symmetry, boundary
2. `TrackRecorderTest.kt` — 15 tests: all state transitions, speed gating, debounce, point accumulation
3. `TrackRepositoryTest.kt` — 10 tests: CRUD lifecycle, checkpoint lifecycle, corrupted file handling
4. `TrackSerializerTest.kt` — 4 tests: protobuf round-trip with full/null/empty data

---

## Step 3 — UI Integration

### 3a. Create `GpxExporter.kt`

**File:** `app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt`

- `Track.toGpx(): String` — GPX 1.1 XML
- `<trk>` / `<trkseg>` / `<trkpt>` with `<ele>`, `<time>`, `<speed>`, `<course>`
- ISO 8601 timestamps with Z suffix
- XML entity escaping for name/comment

### 3b. Create `TrackStatusIcon.kt`

**File:** `app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt`

- 👣 footprint composable
- 3 states: IDLE (dimmed grey), RECORDING (pulsing green dot via `InfiniteTransition`), PAUSED (amber dot)
- Clickable — opens TrackDrawer on tap
- Positioned in top-left icon row on MapScreen

### 3c. Create TrackDrawerOverlay

**File:** `ui/map/TrackDrawerOverlay.kt` — right-side overlay panel using `ModalDrawerSheet`

**Triggers:** Both 🍔 hamburger button (right-edge stack, above settings) and 👣 `TrackStatusIcon` (top-left icon row) open the drawer.

**TrackDrawerOverlay (right-side overlay):**
- Right-aligned panel at 75% width (`Alignment.TopEnd`, `fillMaxWidth(0.75f)`)
- Scrim on left 25% (click to dismiss)
- Uses Material3 `ModalDrawerSheet` with dark background (`0xFF1A1A2E`)
- Animates in/out: `fadeIn/fadeOut` + `slideInHorizontally` from right
- `BackHandler` + arrow back button (`Icons.AutoMirrored.Filled.ArrowBack`) to close
- Title: "Maro II" (24sp Bold White)
- Section header: "TRACK RECORDING" with blue `#1565C0`, uppercase
- Start/Stop as a Row with text label + **Switch toggle** (not just a button)
- "Track List" shortcut → opens TrackHistoryOverlay
- Live stats card: grey background `0x1AFFFFFF`, `RoundedCornerShape 12dp`
  - State (● Recording / ● Paused), Elapsed time, Points, Distance (nm), Max/Avg speed (kn)
- Stats shown via `if (isActive)` guard (not AnimatedVisibility)
- Tight spacing: 8dp outer, 6dp items, 2dp gaps

### 3d. Create `TrackHistoryOverlay.kt`

**File:** `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

Full-screen overlay with BackHandler. `LazyColumn` of track summary cards.

**Card layout:**
```
┌──────────────────────────────────────────┐
│ 📅 2026-06-17 14:32         👁️  🔗      │
│                                          │
│ Name: [Port Salis → Cap d'Antibes   ✏️]  │
│ Comment: [Nice afternoon sail       ✏️]  │
│                                          │
│ 🏁 Dist 4.2 nm    ⚡ Max 8.7 kn         │
│ ⛵ Under way 1h12  ⏸️ Still 8min        │
└──────────────────────────────────────────┘
```

**Icons (all monotone, 28dp, consistent with existing MapControlButton style):**
- 👁️ **Visibility toggle** (`Icons.Default.Visibility` / `Icons.Default.VisibilityOff`) — toggle track on map, persisted to `Track.visibleOnMap` protobuf
- 🔗 **Share** (`Icons.Default.Share`) — exports GPX via Android share sheet
- **Delete** — swipe gesture (left), followed by Snackbar undo

**Card interactions:**
- **Name/comment edit:** tap → inline `TextField` with text pre-selected (`TextFieldValue(text, TextRange(0, text.length))`) + keyboard auto-opened. "Done" commits to protobuf.
- **Visibility toggle:** 👁️ tap → toggles `Track.visibleOnMap` + updates protobuf + refreshes map polyline
- **Share:** 🔗 tap → calls `Track.toGpx()` → FileProvider share intent
- **Delete:** swipe left → item animates out → Snackbar "Track deleted. [Undo]" (4s). Undo restores. Timeout → permanent delete from repository.

### 3e. Add `FileProvider` for GPX sharing

**File:** `app/src/main/res/xml/provider_paths.xml` (new)
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="gpx" path="tracks/" />
</paths>
```

**File:** [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml)
Add `<provider>` inside `<application>`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/provider_paths" />
</provider>
```

### 3f. Add `_gpsFixFlow` SharedFlow to `CoastlineViewModel`

**File:** [`app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

Add a `MutableSharedFlow<GpsFix>` that both the internal GPS pipeline and the `TrackViewModel` collect from:
```kotlin
val _gpsFixFlow = MutableSharedFlow<GpsFix>(
    replay = 1,
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
```
The existing `gpsFixFlow` emission site should also emit to `_gpsFixFlow` via `_gpsFixFlow.trySend(fix)`.

### 3g. Wire `TrackViewModel` into `CoastlineViewModel`

**File:** [`app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

- Add `TrackViewModel` + `TrackRepository` fields
- Add `_gpsFixFlow` (`MutableSharedFlow<GpsFix>`) to share GPS fixes between internal pipeline and TrackViewModel
- Expose `trackRecorderState: StateFlow<TrackRecorderUiState>`
- Expose `trackSummaries: StateFlow<List<TrackSummary>>`

### 3h. Integrate into `MapScreen.kt`

**File:** [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

- Add 🍔 **hamburger button** to the right-edge control stack, **above the Settings button** — `Icons.Default.Menu`, 64dp round `MapControlButton` style
- Add `TrackStatusIcon` to top-left icon row
- Both hamburger and status icon open `TrackDrawerOverlay`
- Track overlay `LaunchedEffect`: observes `trackSummaries`, manages osmdroid `Polyline` per visible track (8f stroke, `trackColorArgb`, titled `"track_$id"`)
- Overlay cleanup protection: exclude `"track_"`-prefixed Polylines from general `removeAll`
- `TrackDrawerOverlay` as right-side panel
- `TrackHistoryOverlay` as full-screen overlay
- "Track Recording" toggle in General settings tab
- "Track Configuration" read-only section (origin lat/lon/radius)

---

## Step 4 — Verification

### 4a. Build check
```bash
gradlew assembleDebug
```

### 4b. Unit tests
```bash
gradlew testDebugUnitTest
```

### 4c. E2E Device Tests (manual)
1. Enable Tracking toggle in Settings
2. Verify 👣 icon appears (IDLE state)
3. Sail out of Port Salis geofence → verify auto-start + real-time map trace
4. Stop sailing → verify pause → sail again → verify resume
5. Return to Port Salis + stop → verify auto-finalize + track history
6. Open track history → tap track → verify trace on map
7. Swipe-to-delete → confirm → verify removed
8. Manual Start/Stop from drawer → verify state
9. Export GPX → verify valid XML
10. Settings persistence across app restart

---

## Implementation Order (for Code mode)

| # | Action | Files |
|---|--------|-------|
| 1 | Add `timestampEpochMs` to `GpsFix` | 1 file modified |
| 2 | Add maro.properties entries | 1 file modified |
| 3 | Add BuildConfig fields | 1 file modified |
| 4 | Add track settings to `SettingsManager` | 1 file modified |
| 5 | Create data model files (`TrackPoint`, `Track`, `TrackEvent`) | 3 new files |
| 6 | Create recording subsystem (`TrackGeofenceChecker`, `TrackRecorder`, `TrackRepository`, `TrackViewModel`) | 4 new files |
| 7 | Create test files for steps 1–2 | 4 new files |
| 8 | Create `TrackRecordingService` (foreground service) | 1 new file |
| 9 | Create notification icon `ic_track_notification.xml` | 1 new file |
| 10 | Create `GpxExporter` | 1 new file |
| 11 | Create `TrackStatusIcon` | 1 new file |
| 12 | Create `TrackDrawerOverlay` | 1 new file |
| 13 | Create `TrackHistoryOverlay` | 1 new file |
| 14 | Add FileProvider (xml + manifest) | 2 files (1 new, 1 mod) |
| 15 | Wire `TrackViewModel` into `CoastlineViewModel` | 1 file modified |
| 16 | Integrate into `MapScreen.kt` | 1 file modified |
| 17 | Build + fix compile errors | — |
| 18 | Run unit tests | — |
