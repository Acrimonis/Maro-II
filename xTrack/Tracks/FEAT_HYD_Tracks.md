# Context Hydration — Tracks — 2026-10-24

**Last Bake:** 2026-10-24 11:52 UTC — written by `#bake` on `feature/track-comment-ignore-time-spend-at-la-salis`.

**Directive trace:** one covered action class was met and none stopped — implementation ran on the user's `#impl` / `#impl fix`, the design settled through `discuss`, and `#bake` / `#commit` were invoked; no dependency was added, no machine-shaped data file was opened, the device was read only for the logcat and its stored settings the user pointed at, and every claim about the code came from a file read.

## State

The home port, its arrival prompt, the port-zone naming exclusion and the route-comment rung removal all shipped and compile green. The home port is one `maro.properties` block (`tracking.homePort.*`: `43.5696, 7.1313`, 100 m, `La Salis`), and `SettingsManager` now reads it from the file rather than from persisted prefs — stale stored coordinates (`43.55, 7.0, 500`) had been shadowing it, which is why the arrival prompt never fired in demo. A marker inside the circle no longer names a trip or route, and the saved route comment head is `#rank • pace • pref`. `compileDebugKotlin` and the scoped `data.track` + `RouteCommentHeadTest` suites green; the device pass is owed.

## Target Files

- `app/src/main/assets/maro.properties` — the one home-port block (`tracking.homePort.*`)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — home port read from BuildConfig, not prefs
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — port-zone filters, arrival prompt, title fallback
- `app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt` — positional-only `namesHomePort`
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `routeCommentHead()`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeCommentFor()`
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — rung state retired

## Next Step

Device pass: confirm the arrival prompt now fires on idle-in-zone with the properties-sourced port, and that no "La Salis" marker names a trip or route.
