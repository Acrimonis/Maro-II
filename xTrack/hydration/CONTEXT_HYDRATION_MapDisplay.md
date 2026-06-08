# Context Hydration — MapDisplay

**Last Bake:** 2026-06-08 20:43
**Feature Status:** active (3/6 subfeatures done)
**Active Subfeature:** config 300m auto display (impl done, build/verify pending)

## State Summary
Added per-mode control over the 300 m zone auto-show. Two new persisted booleans
`zone300AutoShowGps` + `zone300AutoShowDemo` (`AppSettings`, default **ON** to preserve the prior
always-on behaviour). Settings → Avancé → "300 m zone alert" now renders two toggles (GPS / Demo);
the shared distance + time threshold sliders render only when at least one toggle is on. The shore
pipeline (`CoastlineViewModel`) gates auto-reveal on the active mode's toggle — when off it leaves
`zone300Visible` under manual control and resets `zone300AutoRevealed` / `bandEnteredSinceReveal`.
The pure `zone300Decision()` is unchanged, so `Zone300DecisionTest` is untouched. EN + FR strings
added; the section description dropped its "(GPS)" qualifier since it now covers both modes.
Branch: `feature/300-auto-show` (forked from `feature/UI-icons`).

## Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `zone300AutoShowGps` / `zone300AutoShowDemo` (persist + defaults)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — two toggles + conditional threshold sliders in the alert section
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — per-mode gate in the 150 ms shore pipeline
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — toggle labels/descriptions

## Next Steps
- Build (`apk-build.bat`) + on-device verify: each mode's toggle off suppresses auto-show and hides the sliders; default-on keeps current behaviour.
- Open decision: should Demo auto-show default **off** (currently on)?
- Still pending in this feature: "depth color" subfeature; "layer-zone" depth re-bake + on-device coverage check.
