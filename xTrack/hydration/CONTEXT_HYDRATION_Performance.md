# Context Hydration — Performance — 2026-06-07

**Active Subfeature:** none

## State
Battery/perf pass on branch `feature/performance` — implemented, compiles, unit-tested; on-device
check pending. Four levers live: (1) **tunable acquisition** — "Fréquence GPS" 3-stop slider
Haute 1s/1m · Équilibrée 2s/5m · Économie 4s/10m (writes `gpsActiveIntervalSec`/`MinDistanceM`);
(2) **always-on adaptive idle** — `AdaptiveGpsPolicy` (anchor 20 m / 30 s window, idle 6 s, instant
wake), folded into the GPS subscription via `_acquisitionMode` + `GpsParams`; (3) **map-refresh
cap** — `cameraUpdates` flow sampled to `mapRefreshFps` (5–50, def 25), one
`setCenter`+`mapOrientation` per tick replacing `animateTo`; (4) **compass-only-when-needed** via
`_needsCompass` + `SENSOR_DELAY_NORMAL`. Settings rebuilt into 3 sections (Source de position,
Affichage, Économie d'énergie → sub-groups Acquisition GPS / Rendu carte / Économie à l'arrêt with
an "Avancé" expander hiding the detection thresholds). Type scale 17/16/13. `AdaptiveGpsPolicyTest`
6/6 green; `assembleDebug` green. No new deps; foreground-only.

## Target Files
- `ui/map/MapScreen.kt` — settings UI (3 sections, frequency slider, expander), capped follow applier
- `ui/map/CoastlineViewModel.kt` — `GpsParams`, `_acquisitionMode`, `_needsCompass`, `cameraUpdates`
- `data/location/AdaptiveGpsPolicy.kt` (+ test), `CompassSource.kt`, `data/settings/SettingsManager.kt`

## Next Step
On-device verification: fix cadence vs preset, idle drop after ~30 s + instant wake on movement,
compass register/unregister while moving, refresh-cap tracking, settings scroll/labels. Then mark
`settings-ui` done.
