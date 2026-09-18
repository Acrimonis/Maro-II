# Context Hydration — Markers — 2026-09-18

**Last Bake:** 2026-09-18 21:22 UTC

**Directive trace:** No dependency was added, no machine-shaped data file was opened, no work started without an order and the device was untouched; the one claim about the code written without a read behind it — the framing values reached through `AppSettings` — was corrected in the same session once the compiler rejected it, so all five classes were met and none stopped.

## State

`marker-focus-zoom` shipped on `feature/mrkrs` (off `origin/develop` at `35c66c0`): opening a marker's
dashboard now frames that marker to a stated share of the smaller displayed map dimension — corridor
0.50, circle zone 0.30, and a pin through a nominal 200 m footprint at the zone's share, never below the
current zoom. The fit is the pure `MarkerFocus.kt`, `MarkerFocusTest` is 8/8 green and `apk-build.bat`
succeeded, with the scoped `ui.map` + `config` run at 197 tests and only the five pre-existing
properties-versus-code-default reds. Two behaviours no test covers: whether the framing hop can flip
`inspectMapMovedByUser` at the wrong moment, and the GPS auto-follow takeover.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MarkerFocus.kt` — the fit, and the only place the share rule is expressed
- `app/src/main/assets/maro.properties` — the three `marker.focus.*` values behind the behaviour
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the navigate flow's hop and the select-driven effect that frames every other select
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the read-only accessors and their clamps

## Next Step

Pin the three `marker.focus.*` keys to their parse with a properties test, the Ask pass's open finding, then run the device pass over a corridor, a circle and a pin with the card open — watching the share, whether GPS auto-follow steals the frame, and whether closing the card leaves the camera alone.
