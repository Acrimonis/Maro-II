# GPS Tracking Loss — Fix Plan

> Branch: `feature/GPS-Fix`  
> Active Feature: GPS  
> Subfeature: `gps-loss`  
> Derived from: [`plans/gps-loss-investigation.md`](plans/gps-loss-investigation.md)

---

## Phase 1 — Stop the tracking loss (critical)

### 1.1 Add `GnssStatus.Callback` to `GpsLocationSource`

**File:** [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt)

- Add a new field `hasLock: Boolean` to `GpsFix` data class (default `true`)
- Register `GnssStatus.Callback` via `LocationManager.registerGnssStatusCallback()`
- Track `GnssStatus.getSatelliteCount()` — when it drops below `MIN_SATELLITES_FOR_LOCK` (constant = 4), emit `GpsFix` with `hasLock = false`
- When satellite count returns >= 4, emit `GpsFix` with `hasLock = true` + position
- Handle `GnssStatus.Callback` lifecycle in `awaitClose` (unregister)

**Why:** Lets the app detect weakening GPS lock before complete loss, and emit a "no lock" signal the ViewModel can react to.

### 1.2 Implement stale-fix watchdog in `CoastlineViewModel`

**File:** [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

- Add `_gpsStale: MutableStateFlow<Boolean>` (default `false`)
- Add `val gpsStale: StateFlow<Boolean>` to expose it
- In the GPS collector (lines 382-416), on each fix arrival: reset a debounce timer
- If no fix arrives within `GPS_STALE_TIMEOUT_MS` (max of `HEADING_FALLBACK_MS` [3s] and `intervalMs * 3`), set `_gpsStale.value = true`
- On the next fix arrival, set `_gpsStale.value = false` and reset timer
- Also gate: if `hasLock == false` from `GpsFix`, immediately set stale

**Why:** The core fix for "frozen map" — ensures the app detects when GPS has stopped delivering fixes.

### 1.3 Handle `onStatusChanged` in `GpsLocationSource`

**File:** [`GpsLocationSource.kt:68`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:68)

- Implement `onStatusChanged` body:
  - `LocationProvider.TEMPORARILY_UNAVAILABLE` → emit `GpsFix` with `hasLock = false`
  - `LocationProvider.AVAILABLE` → emit `GpsFix` with `hasLock = true`
  - `LocationProvider.OUT_OF_SERVICE` → emit `GpsFix` with `hasLock = false`
- Keep the `@Suppress("DEPRECATION")` annotation — this is still the simplest path pre-API 31

**Why:** Without this, the app has zero visibility into GPS provider state changes.

### 1.4 Improve GPS flow error handling

**File:** [`CoastlineViewModel.kt:415`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:415)

- Replace `.catch { /* silent */ }` with:
  ```kotlin
  .catch { e ->
      if (e is SecurityException) {
          // Permission revoked — stop silently as before
      } else {
          Log.w(TAG, "GPS flow error", e)
          _gpsStale.value = true
          // Reconnect: emit nothing, the flatMapLatest will retry on next gpsParams change
      }
  }
  ```
- Add `private const val TAG = "CoastlineVM"` to companion object

**Why:** Currently ALL errors are silently swallowed. At minimum log them and set stale flag.

---

## Phase 2 — Robustness

### 2.1 Add `PASSIVE_PROVIDER` supplement

**File:** [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt)

- Register a second `LocationListener` for `LocationManager.PASSIVE_PROVIDER` in the same `callbackFlow`
- Merge both listeners into a single channel — use a shared `trySend` from both callbacks
- Deduplicate: skip passive fix if it's the same position as the last `GPS_PROVIDER` fix (within 1 m, within last 2 s)
- The `MIN_SPEED_MPS` / `hasCourse` logic applies only to `GPS_PROVIDER` fixes (passive fixes may not have bearing/speed)

**Why:** Provides free "keep alive" fixes when other apps on the device are using GPS, bridging brief `GPS_PROVIDER` dropouts.

### 2.2 Add `gpsStale` to dashboard

**Files:** [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt), [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

- Add a GPS status indicator tile to the dashboard (small, unobtrusive):
  - Green dot + "GPS" text when healthy
  - Red dot + "GPS perdu" (lost) in red when stale
  - Gray dot + "GPS" when in demo mode
- Pass `gpsStale` from ViewModel through MapScreen to DashboardPanel
- When stale, show a brief overlay toast/banner: "Signal GPS perdu — recherche..."

**Why:** Gives the user visibility into GPS health instead of just seeing a frozen map.

---

## Phase 3 — Polish (optional, can defer)

### 3.1 Reduce idle minimum distance (Issue #6 — adaptive cadence feels like tracking paused)

**File:** [`AdaptiveGpsPolicy.kt`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt), [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

Currently the idle mode reuses `gpsActiveMinDistanceM` (default **5 m**) — meaning even at 6 s interval, the position stays frozen until the user moves 5+ m. Two-part fix:

1. **Add a separate `gpsIdleMinDistanceM` setting** (default 0 m) so tiny drifts still update position at idle cadence. This prevents the perception of a "stuck" position when anchored/drifting slowly.
2. **Add an `acquisitionMode` indicator to the dashboard** — show "GPS ACTIVE" / "GPS IDLE" (or just a small text label) so the user knows when the system has deliberately reduced the fix rate rather than lost signal entirely.

**Settings to add:**
- `AppSettings.gpsIdleMinDistanceM: Float = 0f`
- Wire through `SettingsManager`, add to settings UI if desired (or keep as internal default)

### 3.2 Debounce rapid settings re-subscriptions (Issue #7)

**File:** [`CoastlineViewModel.kt:369-384`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:369)

The `gpsParams` flow uses `distinctUntilChangedBy` watching 3 settings fields (`gpsActiveIntervalSec`, `gpsActiveMinDistanceM`, `adaptiveIdleIntervalSec`). Changing any of them triggers `flatMapLatest` to cancel the old subscription and create a new one. During a slider drag, this can fire many times rapidly.

**Fix:** Add `.debounce(100)` before `.flatMapLatest` to coalesce rapid changes into a single re-subscription at the end of the drag:

```kotlin
gpsParams
    .debounce(100)
    .flatMapLatest { p -> ... }
```

Import `kotlinx.coroutines.flow.debounce` (part of `FlowPreview` which is already opted-in).

**Why:** Prevents brief teardown/recreate gaps during slider interaction. The 100 ms debounce is imperceptible to the user.

### 3.3 Migrate to `LocationCallback` on API 31+ (Issue #8)

**File:** [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt)

The `LocationListener` interface is deprecated in Android 12 (API 31). Conditionally use the modern `LocationCallback` + `LocationRequest` API:

- Check `Build.VERSION.SDK_INT >= 31`
- If yes: use `LocationManager.requestLocationUpdates(LocationRequest, LocationCallback, Looper.getMainLooper())`
  - `LocationRequest` supports `setMinUpdateIntervalMillis`, `setMinUpdateDistanceM`, `setQuality(POWER_HIGH)`
  - `LocationCallback` has `onLocationResult` instead of `onLocationChanged`
- If no: keep the existing `@Suppress("DEPRECATION") LocationListener` path
- Both paths feed into the same `trySend` channel

**Why:** Better API 31+ compatibility and access to newer location features (like `LocationRequest.setGranularity`). The deprecated API still works but may have less predictable behavior on newer devices.

---

## Execution order

```
1.1 GnssStatus.Callback ──→ 1.3 onStatusChanged ──→ 1.2 stale watchdog ──→ 1.4 error logging
                                                                                │
                                                                                ▼
                                                                     2.1 PASSIVE_PROVIDER
                                                                                │
                                                                                ▼
                                                                     2.2 UI indicator
                                                                                │
                                                                                ▼
                                                                     3.1 idle distance (if time)
```

Each step builds on the previous one. Steps 1.1–1.4 are the critical path for fixing the tracking loss symptom. Step 2.1 adds extra robustness but isn't strictly required. Step 2.2 gives the user visibility. Step 3.1 is tuning.

---

## Files to modify

| File | Changes |
|------|---------|
| [`GpsLocationSource.kt`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt) | 1.1 GnssStatus.Callback, 1.3 onStatusChanged, 2.1 PASSIVE_PROVIDER |
| [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) | 1.2 stale watchdog, 1.4 error logging, expose gpsStale |
| [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) | 2.2 GPS status indicator tile |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | 2.2 wire gpsStale to dashboard |
| [`AdaptiveGpsPolicy.kt`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt) | 3.1 idle min distance (optional) |
| [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | 3.1 add gpsIdleMinDistanceM setting (optional) |
