# Context Hydration — Performance — 2026-06-07

**Active Subfeature:** none

## State
Branch `feature/performance` is now **merged up to date with `origin/develop`** (merge
commit `6999ee0`) so its PR is mergeable. The only conflict was in `xTrack/GLOBAL_CONTEXT.md`
(both sessions baked different active features): resolved by keeping **both** routing rows
(Performance + MapDisplay) and Performance as the active pointer; app code (`MapScreen.kt`,
`AndroidManifest.xml`) auto-merged cleanly. Feature work itself is complete and unchanged this
session: four levers live — (1) **tunable acquisition** (Haute 1s/1m · Équilibrée 2s/5m ·
Économie 4s/10m → `gpsActiveIntervalSec`/`MinDistanceM`); (2) **always-on adaptive idle**
(`AdaptiveGpsPolicy`, anchor 20 m / 30 s window, idle 6 s, instant wake) via `_acquisitionMode`
+ `GpsParams`; (3) **map-refresh cap** (`cameraUpdates` sampled to `mapRefreshFps` 5–50/def 25,
one `setCenter`+`mapOrientation` replacing `animateTo`); (4) **compass-only-when-needed** via
`_needsCompass` + `SENSOR_DELAY_NORMAL`. `AdaptiveGpsPolicyTest` 6/6 green; `assembleDebug`
green. No new deps; foreground-only. 4/5 subfeatures done; `settings-ui` still `[ ]` pending
on-device verification.

## Target Files
- `ui/map/MapScreen.kt` — settings UI (3 sections, frequency slider, expander), capped follow applier
- `ui/map/CoastlineViewModel.kt` — `GpsParams`, `_acquisitionMode`, `_needsCompass`, `cameraUpdates`
- `data/location/AdaptiveGpsPolicy.kt` (+ test), `CompassSource.kt`, `data/settings/SettingsManager.kt`

## Next Step
PR into `develop` is now conflict-free — merge it. Then on-device verification: fix cadence vs
preset, idle drop after ~30 s + instant wake on movement, compass register/unregister while
moving, refresh-cap tracking, settings scroll/labels. Then mark `settings-ui` done.
