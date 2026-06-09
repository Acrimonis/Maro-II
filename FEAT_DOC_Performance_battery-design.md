<!-- scope: feature -->

# Performance & Battery Design

> Design reference for the **Performance** feature (`xTrack/FEATURE_SCOPE_Performance.md`).
> Battery/performance pass over the GPS plugin. Captures the hotspot analysis, the chosen
> settings/defaults, the adaptive-policy contract, and the map refresh-cap mechanism — enough to
> implement from cold. No code is changed by this doc.

## 1. Why

Maro-II in GPS mode is an always-on, on-the-water navigation app — a boat phone may run it for
hours. The existing architecture is already disciplined: GPS/compass are torn down on `ON_PAUSE`,
spatial recomputes are `sample()`-throttled on `Dispatchers.Default`, and there are **no wake
locks, no foreground service, no background location**. Nothing is broken. The remaining drains
are inherent to the live-navigation workload, and this feature makes them **tunable**,
**movement-adaptive**, and **render-capped**.

## 2. Battery hotspots (measured by inspection)

| # | Hotspot | Where | Lever |
|---|---------|-------|-------|
| 1 | GPS fix rate fixed at 1 s / 1 m, even moored | `GpsLocationSource.locationUpdates()` defaults, called at `CoastlineViewModel.kt:256` | Tunable preset/sliders + adaptive idle |
| 2 | GPS-follow uses `animateTo` → ~60 fps repaint burst per fix while moving straight (worse on 90/120 Hz screens) | `MapScreen.kt:170-179` (recenter) + `:184-190` (rotate) | Capped `setCenter`+`mapOrientation` — one repaint per tick |
| 3 | Rotation-vector compass always registered while GPS mode on, at `SENSOR_DELAY_UI` (~16 Hz), even moving (output discarded when GPS course valid) | `CompassSource.kt:42`, collector `CoastlineViewModel.kt:271` | Gate on "needs compass"; drop to `SENSOR_DELAY_NORMAL` |

**Key finding on #2:** the heading-deadband only governs *rotation*; straight-line movement
repaints via the *recenter* path, and `animateTo` self-drives a frame-paced (~60 fps) scroll
animation per fix. That animation, not rotation, is the larger underway render cost.

The Zone-300 pulse animation is **intentionally left running** — it is a safety attention cue.

## 3. Design decisions

- **No "battery-saver" master toggle.** Adaptive frequency and compass-gating are *unconditional
  correct behaviour*, not opt-in modes. A user-facing knob is added only where there is a genuine
  visible trade-off (map refresh rate).
- **Acquisition UX:** presets **+** advanced sliders.
- **Map re-render:** one unified **refresh-rate cap** (ceiling, not floor), applied to the GPS
  auto-follow path only — manual gestures stay full native rate.
- **Compass:** register only when there is no valid GPS course.
- **No new dependencies** (framework `LocationManager`/`SensorManager` + SharedPreferences; no
  DataStore, no play-services). No manifest/lifecycle change — stays foreground-only.

## 4. Settings model (`AppSettings` / `SettingsManager`)

New fields (mirror the `recenterDelaySeconds` SharedPreferences pattern: `getInt`/`putInt`,
`getFloat`/`putFloat`, a `KEY_*` const each, read in `load()`, write in `update()`):

| Field | Type / default | Range (slider) | Meaning |
|-------|----------------|----------------|---------|
| `gpsActiveIntervalSec` | `Int` = 2 | 1–10 s | Moving fix interval |
| `gpsActiveMinDistanceM` | `Float` = 5f | 1–25 m | Moving min movement between fixes |
| `adaptiveWindowSec` | `Int` = 30 | 15–60 s | Stationary-detector window |
| `adaptiveDistanceM` | `Int` = 20 | 10–30 m | Stationary displacement threshold |
| `adaptiveIdleIntervalSec` | `Int` = 6 | 4–15 s | Fix interval when idle |
| `mapRefreshFps` | `Int` = 25 | 5–50 fps | GPS-follow re-render ceiling |

**Acquisition presets** are setter shortcuts that write the two `gpsActive*` fields; the UI derives
the highlighted preset by matching the stored values, else shows "Personnalisé":

| Preset | Interval | Min distance |
|--------|----------|--------------|
| Haute | 1 s | 1 m |
| Équilibrée (default) | 2 s | 5 m |
| Économie | 4 s | 10 m |

## 5. Tunable acquisition wiring

`GpsLocationSource.locationUpdates(minIntervalMs, minDistanceM)` already accepts both params; only
the call site hardcodes defaults. Feed the GPS `flatMapLatest` from a params stream that also folds
in the adaptive mode:

```kotlin
data class GpsParams(val on: Boolean, val intervalMs: Long, val minDistanceM: Float)

val gpsParams = combine(
    enabled,                                    // gpsMode && foreground
    settings.distinctUntilChangedBy { Triple(it.gpsActiveIntervalSec, it.gpsActiveMinDistanceM, it.adaptiveIdleIntervalSec) },
    _acquisitionMode
) { on, s, mode ->
    val (ivl, md) = if (mode == AcquisitionMode.IDLE)
        s.adaptiveIdleIntervalSec * 1_000L to s.gpsActiveMinDistanceM
    else
        s.gpsActiveIntervalSec * 1_000L to s.gpsActiveMinDistanceM
    GpsParams(on, ivl, md)
}.distinctUntilChanged()

gpsParams
    .flatMapLatest { p -> if (p.on) gpsSource.locationUpdates(p.intervalMs, p.minDistanceM) else emptyFlow() }
    .onEach { fix -> /* existing body + §6 mode update */ }
    .catch { }
    .launchIn(viewModelScope)
```

`flatMapLatest` disposes the old `LocationListener` (`removeUpdates`) and re-subscribes — the same
mechanism the on/off gate already relies on.

## 6. Adaptive movement-based frequency (always on)

New pure-Kotlin, unit-testable component `data/location/AdaptiveGpsPolicy.kt`:

```kotlin
enum class AcquisitionMode { ACTIVE, IDLE }

/** IDLE once net displacement over a FULL [windowMs] stays under [thresholdM];
 *  wakes to ACTIVE on the first fix with speed > [wakeSpeedMps] or a jump >= [thresholdM]. */
class AdaptiveGpsPolicy {
    private val window = ArrayDeque<Pair<Long, LatLng>>()   // (elapsedMs, pos)
    fun onFix(nowMs: Long, pos: LatLng, speedMps: Float?,
              windowMs: Long, thresholdM: Double, wakeSpeedMps: Float = 0.8f): AcquisitionMode { … }
    fun reset() = window.clear()
}
```

Contract:
- Thresholds are passed **live from `settings.value`** each call → Advanced sliders take effect
  immediately, no policy rebuild.
- Distance: reuse the great-circle helper used by the shore pipeline
  (`spatial/SpatialOperations.kt` / `CoastlineRepository.distanceToCoastMeters`); if none is
  public, add a small private haversine.
- Hysteresis: drop to IDLE only after a *full* window below threshold; wake to ACTIVE on the
  *first* qualifying fix — biased to responsiveness for a nav app.

Wiring in `CoastlineViewModel`: hold `adaptivePolicy` + `_acquisitionMode = MutableStateFlow(ACTIVE)`.
In the GPS `onEach`:

```kotlin
val s = settings.value
_acquisitionMode.value = adaptivePolicy.onFix(
    SystemClock.elapsedRealtime(), fix.position, fix.speedMps,
    s.adaptiveWindowSec * 1_000L, s.adaptiveDistanceM.toDouble()
)
```

The policy instance lives **outside** `flatMapLatest`, so an interval re-subscribe never resets its
window. The mode→params→re-subscribe loop is stable (re-subscription only changes the timer
cadence). The `adaptiveIdleIntervalSec` slider doubles as the "how aggressive" control — set it
equal to the active interval to effectively neutralise adaptive without a toggle.

## 7. Capped map auto-follow

Today: `LaunchedEffect(gpsPosition…)` → `animateTo` (self-drives ~60 fps) and a separate
`LaunchedEffect(mapBearing…)` → `invalidate`. Replace **both** with one capped camera stream so
position and orientation are applied together, once per tick.

`CoastlineViewModel` (gated on `enabled && !autoFollowSuppressed`):

```kotlin
data class CameraTarget(val pos: LatLng, val bearing: Float)

val cameraUpdates: Flow<CameraTarget> =
    settings.map { 1_000L / it.mapRefreshFps.coerceIn(5, 50) }.distinctUntilChanged()
        .flatMapLatest { periodMs ->
            combine(_gpsPosition.filterNotNull(), _mapBearing) { p, b -> CameraTarget(p, b) }
                .sample(periodMs)        // ceiling = mapRefreshFps
        }
```

`MapScreen` — one `LaunchedEffect` collects `cameraUpdates` and applies **instantly** (no
animation):

```kotlin
mv.controller.setCenter(GeoPoint(t.pos.latitude, t.pos.longitude))
mv.mapOrientation = -t.bearing
mv.invalidate()
depthViewModel.updateMapCenter(t.pos.latitude, t.pos.longitude)
```

Notes:
- Repaint rate ≤ `mapRefreshFps`; `sample()` emits nothing when neither position nor bearing
  changed (steady boat ⇒ zero repaints). In practice the real repaint rate ≈ the rate of
  meaningful change, well under the ceiling.
- **Manual gestures keep their own osmdroid path** (full native rate); the cap governs only this
  GPS-follow flow. `notifyUserInteraction()`/`autoFollowSuppressed` still pause it.
- Keep a tiny internal jitter deadband in `setMapBearing` (1°) so steady headings don't churn the
  `combine`; it is no longer a user setting.
- Trade-off (accepted): `setCenter` steps to each fix instead of gliding. At boat speeds and a
  25 fps ceiling this is smooth, and it removes the 60 fps animation burst entirely.

## 8. Compass — register only when needed (always on)

- `CompassSource.azimuthUpdates(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL)` —
  default lowered from `_UI` (~16 Hz → ~5 Hz); the downstream `sample(200 ms)` made the extra
  events pure waste.
- Replace the always-on compass collector (`CoastlineViewModel.kt:271`) with one gated on a
  `_needsCompass` StateFlow: `combine(enabled, _needsCompass) { on, needs -> on && needs }`.
- Drive `_needsCompass` from the GPS `onEach`: `false` when a GPS course arrives; `true` once
  `elapsedRealtime - lastGpsBearingMs > HEADING_FALLBACK_MS` (3 s). Add a ~1–2 s debounce before
  flipping back to `false`/unregistering so threshold-straddling speeds don't thrash the sensor
  (the rotation-vector sensor needs a moment to settle on each re-register).
- Net: heading output is identical to today (compass was ignored while moving anyway), but the
  magnetometer path is unpowered whenever GPS course is good.

## 9. Settings UI (`SettingsOverlay`)

Reuse `SectionHeader` / `SettingsToggleRow` / `SettingsSliderRow`; one new `SettingsPresetRow`.

- **"Source de position"** (existing) — Mode GPS toggle + Délai de recentrage (unchanged).
- **NEW "Acquisition GPS"** — preset row (Haute/Équilibrée/Économie, active highlighted) +
  **Advanced** expander: Intervalle GPS 1–10 s, Distance minimale 1–25 m, Fenêtre adaptative
  15–60 s, Distance adaptative 10–30 m, Intervalle au repos 4–15 s.
- **NEW "Rendu carte"** — Fréquence de rafraîchissement slider 5–50 fps (default 25); the
  description carries the inline battery hint ("plus bas = moins de batterie").
- **"Affichage"** (existing) — coastline / Zone 300 toggles (unchanged).

## 10. Files

| File | Change |
|------|--------|
| `data/settings/SettingsManager.kt` | +6 fields, keys, load/update |
| `data/location/GpsLocationSource.kt` | none (params already exist) |
| `data/location/CompassSource.kt` | `samplingPeriodUs` param, default `SENSOR_DELAY_NORMAL` |
| `data/location/AdaptiveGpsPolicy.kt` | **new** pure-Kotlin policy |
| `ui/map/CoastlineViewModel.kt` | `GpsParams` upstream, `_acquisitionMode`, adaptive wiring, `_needsCompass` gate, `cameraUpdates` capped flow |
| `ui/map/MapScreen.kt` | new settings sections; replace the two follow effects with one capped applier |
| `ui/map/DashboardPanel.kt` | none (pulse kept) |
| `app/src/test/.../data/location/AdaptiveGpsPolicyTest.kt` | **new** unit test |
| `AndroidManifest.xml` | none (foreground-only confirmed) |

## 11. Verification

- **Build:** `apk-build.bat` (wraps `gradlew assembleDebug`).
- **Unit:** `gradlew test` — `AdaptiveGpsPolicyTest`: ACTIVE→IDLE after a full 30 s / <20 m window;
  immediate IDLE→ACTIVE on a fast fix or >20 m jump; window persists across sequential `onFix`.
- **On-device (logcat):**
  - Presets/sliders change observed fix cadence (log fix timestamps).
  - Stationary ~30 s → fixes space out to the idle interval; movement resumes tight cadence within
    one fix.
  - Map refresh tracks the slider while moving (low fps vs high fps); manual pinch/pan stays smooth
    regardless.
  - Compass: moving with valid GPS course ⇒ no rotation-vector callbacks; stationary >3 s ⇒
    re-registered; no flapping near walking pace.
- **Battery measurement:** `adb shell dumpsys batterystats` (or Battery Historian) over fixed
  moored and underway sessions, before vs after, to quantify GPS-radio + render reduction.

## 12. Out of scope

- No background/foreground-service tracking, no wake locks, no `keepScreenOn` — deliberately
  foreground-only.
- No DataStore migration; no Google Play fused-location (both trip AGENTS.md §4).
- No battery-saver master toggle; no Zone-300 pulse change.

## Related

- `docs/MARO_ARCHITECTURE.md` — spatial-engine constraints (async render rules) these changes
  operate within.
- `docs/MARKER_SIZING.md` — the centred boat marker the capped follow positions.
