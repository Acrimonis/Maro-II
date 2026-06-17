<!-- scope: feature -->
# GPS Refresh Rate — App vs Chipset, Real Perf Advantage

> Discussion captured for subfeature `gps-refreshing` under [`Performance`](xTrack/FEATURE_SCOPE_Performance.md).

## The question

The app has a "GPS acquisition refresh rate" setting (presets Haute/Équilibrée/Économie + advanced sliders). Does this control how often the *app reads* already-available GPS data, or does it actually tell the *GPS chipset* to compute fixes less often? And is there really a battery/performance advantage?

## Answer: Both — and yes, there is a real advantage

### 1. What the setting actually controls

The setting flows into [`GpsLocationSource.locationUpdates(minIntervalMs, minDistanceM)`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:46), which calls:

```kotlin
lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, minIntervalMs, minDistanceM, listener)
```

These two parameters are passed **directly to the Android framework**, not a polling loop. The framework delivers them to the GNSS hardware abstraction layer (HAL).

### 2. `minIntervalMs` — a *hint* to the GNSS baseband

From the Android docs: *"This parameter is only a hint for the location provider and may be ignored."*

| Behaviour | What happens |
|-----------|-------------|
| **On well-behaved chipsets** (most modern Qualcomm/Exynos/MediaTek) | The GNSS baseband processor schedules fix computations at the requested cadence. Between fixes it enters a low-power idle state. Going from 1 s → 4 s interval can roughly **quarter the GNSS power draw**. |
| **On less efficient chipsets** (some older/budget SoCs) | The baseband may still fix at 1 Hz internally, but the framework *gates the callback* — so the app receives fewer events regardless. The radio still burns power, but the **app does less work**. |

### 3. `minDistanceM` — a purely software filter

The GPS chipset still computes fixes at its internal cadence, but the callback is suppressed unless movement exceeds this threshold. This saves **app-level CPU** (spatial computations, map renders) but does NOT save GNSS radio power by itself.

### 4. Where the real perf advantage comes from

The savings stack across two independent mechanisms:

| Layer | Mechanism | Savings |
|-------|-----------|---------|
| **GNSS radio** | `minIntervalMs` hint → baseband deep-sleep between fixes | **Primary** — the dominant gain. Radio duty cycle is the #1 battery drain. |
| **App CPU** | Fewer callbacks → fewer spatial recomputes (isOnWater, distance-to-coast, depth lookups), fewer map re-renders | **Secondary** — significant on older devices |
| **Map GPU** | `mapRefreshFps` cap + `sample()` throttling (already implemented in [`Performance`](docs/PerformanceBatteryDesign.md#7-capped-map-auto-follow)) | Eliminates the 60 fps `animateTo` burst per fix |

### 5. The adaptive layer on top

The already-implemented [`adaptive-frequency`](xTrack/FEATURE_SCOPE_Performance.md:46) subfeature adds a second layer that doesn't need user tuning:

- When stationary (< 20 m displacement over 30 s) → drops to `adaptiveIdleIntervalSec` (default 6 s)
- Wakes instantly on the first movement
- This means the aggressive 1 s rate is **only active when actually moving**, which is the best of both worlds

### 6. Summary

| | Is it real? | Magnitude |
|---|---|---|
| `minIntervalMs` 1 s → 4 s | ✅ Yes — GNSS baseband duty cycle | **High** (radio is #1 drain) |
| `minDistanceM` 1 m → 10 m | ⚠️ Software filter only | Low (app CPU only) |
| Adaptive idle 2 s → 6 s | ✅ Both radio + CPU | **High** (most time is stationary) |

**Bottom line:** The tunable acquisition setting is not fake — it genuinely influences the GNSS baseband's fix cadence on modern hardware, producing real battery savings. The existing implementation is architecturally correct.

