# Context Hydration — MapDisplay

**Last Bake:** 2026-06-08 20:43
**Feature Status:** active (3/7 subfeatures done)
**Active Subfeature:** none (config 300m auto display impl done, build/verify pending)

## State Summary
Added per-mode control over the 300 m zone auto-show. Two persisted booleans `zone300AutoShowGps`
+ `zone300AutoShowDemo` (`AppSettings`, default **ON** to preserve the prior always-on behaviour).
Settings → Avancé → "300 m zone alert" now renders two toggles (GPS / Demo); the shared distance +
time sliders render only when at least one toggle is on. The shore pipeline (`CoastlineViewModel`)
gates auto-reveal on the active mode's toggle — when off it leaves `zone300Visible` under manual
control and resets `zone300AutoRevealed` / `bandEnteredSinceReveal`. Pure `zone300Decision()`
unchanged (`Zone300DecisionTest` untouched). EN + FR strings added. Branch `feature/300-auto-show`.

## Merged from develop (now in this branch)
- **layer-lowdepth** subfeature: bright low-depth-warning `GroundOverlay` above the depth raster
  (threshold slider 0.5–5.0 m, default 1.5, persisted; own toggle). Mercator offset fixed via
  latitude-banding. Bake-time land mask (`DepthZoneMask` nulls `!isWater`). KNOWN: pink warning laps
  ~½ cell onto land at 25 m granularity — open todo (sub-cell test / vector clip).
- New settings auto-merged into `AppSettings`/`SettingsManager`: `keepScreenOn`,
  `lowDepthWarningVisible`, `lowDepthWarningMaxM` (+ `DepthConstants` import).

## Next Steps
- Build (`apk-build.bat`) FIRST — confirm the auto-merged `SettingsManager` / `MapScreen` compile,
  then on-device verify both the auto-show toggles and the low-depth overlay.
- Open decision: should Demo auto-show default **off** (currently on)?
- Pending: "depth color" + "layer-zone" re-bake; low-depth pink-bleed fix.
