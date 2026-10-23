# Context Hydration — Route — 2026-10-23

**Last Bake:** 2026-10-23 14:47 UTC — written by `#bake` on `feature/route-tweeks`; the `Any`-pace bake of 2026-10-22 18:05 on `feature/speed-history-2` stands merged beside it.

**Directive trace:** no dependency was added, no binary file opened and the device never touched; the session started on the user's `#impl` order, every write was code, tests, strings, docs and the xTrack files, each claim about the code had a read behind it, and the one `ask` review hop ran under `#implement`; one question (which gaps change what the user sees) was rejected and answered in the user's own next message; none stopped.

## State

The marker price's strength now ships in `maro.properties` alone: the **Marker pricing** section in Settings › Routing, its four strings in both locales and the persisted `route_marker_strength` value (field, key, read, write) were removed, so no screen adjusts what a grade is worth any more. The shipped value moved from 1.0 to `.2` — one grade a fifth of the mark's own crossing — and the engines, the auto-pick effect and the pre-warm read `AppConfig.routeMarkerCostPerStep` directly; the fine tile key still carries the strength. Two commits landed on `feature/route-tweeks`, `compileDebugKotlin` and the scoped marker and tile suites green.

The `Any` pace is **built** on this branch from the plan's decisions D1–D15 and every item F, A, B, G, C, E is ticked: `PacePlateau` (middle-half average over a 2–10 min window, a gap held at the pace shown or — once a quarter of the window is read — at the readings' own average), the trip store (15 per kind, cruising speed, open-water minutes, demo flag), the day's pace, the pace's source, the Speed History section with its Pace-now line, window slider and Include-demo switch, and a Calculate that disables itself and toasts. `assembleDebug` is green and the unit suite is 1496 tests, 0 failures. Two known unverified edges: the recorder's sample path rests on `Track.markedFor` and the device, and an unchanged GPS speed value is now re-read once a second by `RouteHost`.

## Target Files

- `app/src/main/assets/maro.properties` — the strength's only home, now `.2` with the crossing-time reading documented
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `routeMarkerCostPerStep` documented as the sole home; the two slider bounds removed
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the field, key, read and write removed
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt` — the factor law's wording now names the shipped value, no screen behind it
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MapRouteEffects.kt` — the providers read `AppConfig.routeMarkerCostPerStep`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the section removed
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — the four keys removed
- `xTrack/Route/261021_FEAT_PLN_Route_observed-pace-reference.md` — the plan, every item ticked, `## Open points` holding the gap rule
- `app/src/main/java/ykws/android/maro/data/route/` — `PacePlateau.kt`, `RoutePace.kt`, `SpeedHistory.kt`, `SpeedHistorySeeder.kt`, `SpeedHistoryProfile.kt`
- `app/src/main/java/ykws/android/maro/ui/map/` — `RouteViewModel.kt`, `RouteHost.kt`, `SpeedHistoryActions.kt`, `MapScreen.kt`, `OverlayLayer.kt`, `MapScreenSettingsOverlay.kt`

## Next Step

Judge the `.2` strength on the water — rating 1 leaning, rating 9 all but refusing — and move the single number in `maro.properties` if either end reads wrong.

The device pass, the user's own: a GPS drive reading `Now (x kn, Live)` once a quarter of the window is read, a stop, a second trip the same day starting on `Today`, Calculate, and the Include-demo switch.
