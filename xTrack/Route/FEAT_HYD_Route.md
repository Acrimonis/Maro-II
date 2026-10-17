# Context Hydration — Route — 2026-10-17

**Last Bake:** 2026-10-17 06:48 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session opened on the user's `#new route-fan` and their route-fan rework order, cut the branch from `origin/develop` at its fetched head, and ran a plan-then-implement pass ending in `#bake`; no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code stands behind a file read.

## State

The route fan is rebuilt on `feature/route-fan`. Its unused **Save to track and exit** child is gone, the `Route` child now arms the acquisition alone, and a new **Follow** child sits directly below it — wearing a new `navigation` vector and the reused `route_action_select` name — firing the same selection the acquisition panel's Follow action does; the arc reads **Route auto · Route · Follow · Save · Discard**, and the fan now closes on the follow, acquire and auto children (R91). `RouteFanSaveExitIcon` retired from `FanIconComponents.kt`, `RouteFanFollowIcon` joined, and `ui/icons/Navigation.kt` is the new standalone vector; the `cd_route_fan` comment in both locales names the reused keys, and R91/R92 in `FEAT_DSC_Route.md` were amended — the closers, and the Save-and-exit exception dropped so every ending is two-phase.

`:app:assembleDebug` and `:app:testDebugUnitTest` are green; nothing is device-validated, and `ui/icons/Logout.kt` is now uncalled and was kept rather than deleted.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the fan's enablement list, action list and children reordered, and the close guard moved to follow · acquire · auto
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — `RouteFanSaveExitIcon` out, `RouteFanFollowIcon` in
- `app/src/main/java/ykws/android/maro/ui/icons/Navigation.kt` — the new standalone Material Symbols vector
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — the `cd_route_fan` comment block names the reused keys
- `xTrack/Route/FEAT_DSC_Route.md` — R91, R92, the two concept bullets, a Key Files line and the Docs pointer
- `xTrack/Route/261017_FEAT_PLN_Route_route-fan-decouple.md` — the plan of record

## Next Step

The device pass is the user's: the fan's arc and enablement and its close rule on the follow, acquire and auto children. `app/src/main/java/ykws/android/maro/ui/icons/Logout.kt` awaits the user's word if it is to be deleted.
