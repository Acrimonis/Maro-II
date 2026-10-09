# Context Hydration — Route — 2026-10-09

**Last Bake:** 2026-10-09 09:58 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-08 21:46 UTC) the session ran on the user's own words: `#focus route`, an assessment order, then `#bake` and `#commit`; one implementation was ordered (`#impl`) and is the pipeline in flight. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: `feature/route-swap-direction` cut from `origin/develop` at `6eb9017b` with `--no-track`, the session's changes carried across and uncommitted at bake time.

## State

**The branch.** `feature/route-swap-direction`, cut from `origin/develop` (`6eb9017b`) with `--no-track`, holding the R99 heading-away work uncommitted beside its plan.

**What shipped.** The followed route now **reverses its two ends in place** when the boat heads away from the destination: **R99**, the pure `routeHeadingAway` trigger over the boat's course against the bearing to the armed destination (`route.follow.swap.deadBandDeg` 15, `minSpeedKn` 1.5, `hysteresisDeg` 15), the one transform [`RoutePlan.reversed`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt), the detector [`RouteViewModel.onBoatFix`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt) fed from `MapScreen`'s fix, and the identity bridge so `isRouteSaved`, the session link and the persisted R82 end ids do not fork with the read direction. `testDebugUnitTest` and `apk-build.bat` green.

**What the trigger was then redesigned to — in design, not implemented.** The user found the heading rule inert in demo, where there is no course, and wrong on a curving route; the trigger becomes the route's own **time-to-go loss**: a **toggle** when the trip figure's `remainingFrom(boat).durationSec` is `route.follow.swap.lossSec` (30) worse than its look-back low — the low being the smallest sample in a window derived as twice the loss — with the history bucketed to about one a second on elapsed-realtime and **cleared at every flip**, and `route.follow.swap.debounceSec` 15 spacing the toggles. Beside it a new **arrival cue**: below `route.follow.arrival.etaSec` (60 s) on a new-low crossing, the exit dialog's own doors under a `route_arrival_title` prompt, one per approach, closed as a no-action dismissal whenever a reversal outranks it. All of it is settled in [`261008_FEAT_PLN_Route_heading-away-end-swap.md`](../../xTrack/Route/261008_FEAT_PLN_Route_heading-away-end-swap.md), which also records the three heading keys to delete and the `routeLeadFix` feed coupling to drop.

**What is owed.** The heading-away swap's device pass (R99): a followed route, a real turn away from the destination and back, reading the flip of the ETA, the remaining run and the destination pin — and once the loss model lands, the demo pan-back and the arrival cue. The tile-layer and fine-tail passes from the previous session stand unchanged.

**No open walk.** The feature file holds no `## Walk` section, so nothing bars a fold.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `RoutePlan.reversed`, the `mirrored` flag, `onBoatFix` and the identity bridge; the loss toggle and the arrival latch land here next
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `routeHeadingAway` and `angularOffDeg`, to be replaced by `routeEtaToggle`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the `LaunchedEffect` feeding `onBoatFix`; the `routeLeadFix` coupling to drop
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties` — the three heading keys to delete and `route.follow.swap.lossSec` / `debounceSec` / `route.follow.arrival.etaSec` to add
- `xTrack/Route/FEAT_DSC_Route.md` — R99 rewritten, R100 added, the concept's arrival line
- `xTrack/Route/261008_FEAT_PLN_Route_heading-away-end-swap.md` — the design of record

## Next Step

Implement the loss redesign on `feature/route-swap-direction`: the `routeEtaToggle` trigger, the arrival cue, the three heading keys deleted and the three new values wired.
