# Context Hydration — Route — 2026-10-09

**Last Bake:** 2026-10-09 12:43 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-09 09:58 UTC) the session ran on the user's own words: an assessment order, `#impl` twice, `update docs and #impl`, then `#bake` and `#commit`. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: `feature/route-swap-direction`, cut from `origin/develop` at `6eb9017b` with `--no-track`, the first landing committed as `ae0b98f4`; the redesign, the symmetry fix and the arrival paint stand uncommitted at bake time with `#commit` invoked.

## State

**The branch.** `feature/route-swap-direction`, cut from `origin/develop` (`6eb9017b`) with `--no-track`; `ae0b98f4` holds the heading-away landing, and the loss redesign, the symmetric cue and the arrival paint sit on top, uncommitted.

**The followed route turns on its own time-to-go, and asks on arrival.** The heading trigger is gone, replaced by the route's own **loss** (**R99**): while `Following` the reading **toggles** once `remainingFrom(boat).durationSec` is `route.follow.swap.lossSec` (30) worse than its look-back low, the low being the smallest sample in a window **derived as twice the loss**, the history bucketed to about one a second on `SystemClock` elapsed-realtime, **cleared at every flip** and on entering `Following`, with `route.follow.swap.debounceSec` (15) spacing the toggles; a fall or a flat reading does nothing. The predicate is one pure [`routeEtaToggle`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt) with [`routeEtaStep`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt) advancing the history, and the mirror is the one transform [`RoutePlan.reversed`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt), fed from `MapScreen`'s own fix, with the identity bridge so `isRouteSaved`, the session link and the persisted R82 end ids do not fork with the read direction.

**The arrival cue rides the exit dialog (R100).** Below `route.follow.arrival.etaSec` (60 s) on a new-low crossing the mode raises the exit dialog under a title-only `route_arrival_title` prompt, with the slame doors; it is **armed on entering `Following` from above the threshold and armed again by every flip**, so both ends of the route ask while a route taken up already inside the minute never prompts, and a reversal closes an open cue as a **no action** dismissal before the flip proceeds.

**The arrival paint reads done (R93, inverted).** Fewer than two points remaining is **zero points** — an empty run — so slot 0 and its casing stand down and the travelled run alone draws the covered line in the shared dimming: the trace reads *done* rather than jumping to full at the destination. The followed face is one pure [`routeFollowFace`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt) beside the shared `routeRemainingStands`, and the arrival discriminator stays in the paint key so the flip to arrival still repaints.

**What is owed.** The device pass (R97): a followed line in demo, panning back to read the flip and onto each end to read the cue, then repeated on GPS water. Also open as health, none behavioural: the dead `mirrored` field, the untested clear-on-entering-`Following`, the unpinned flip `arrivalCue`, two over-strong KDoc claims about arming, and the symmetry test's mirrored crossing that steps 340 s to 45 s at once.

**No open walk.** The feature file holds no `## Walk` section, so nothing bars a fold.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `routeEtaToggle`, `routeEtaStep`, `RouteEtaState` and the route history helpers
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `RoutePlan.reversed`, the flip and cue in `onBoatFix`, the identity bridge, and `splitAt`'s corrected KDoc
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — the followed face, the travelled guard and the arrival paint
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MapDialogHost.kt` — the fix feed, `RouteExitReason.ARRIVAL` and the prompt
- `app/src/main/assets/maro.properties`, `config/AppConfig.kt` — `route.follow.swap.lossSec`, `debounceSec` and `route.follow.arrival.etaSec`
- `app/src/test/java/ykws/android/maro/ui/map/` — `RoutePlanTest`, `RouteAcquisitionTest`, `RouteArrivalFaceTest`
- `xTrack/Route/FEAT_DSC_Route.md` — R93 inverted, R99 rewritten, R100 added
- `xTrack/Route/261008_FEAT_PLN_Route_heading-away-end-swap.md` — the design of record

## Next Step

The device pass (R97) that reads the flip and the arrival cue at both ends, and the arrival paint's done reading, in demo and on water.
