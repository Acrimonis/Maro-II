# Context Hydration — Route — 2026-10-18

**Last Bake:** 2026-10-18 12:44 UTC — written by `#bake` on `feature/route-naming` (cut from `origin/develop` by the user's `#new route-naming`). The naming session is split: the route-end half lives here, the shared rule and the track half in Tracks.

**Directive trace:** one covered action class was met and none stopped. Authorisation held — the implementation ran only on the user's `#impl`, the plan settled by `assess, plan, discuss` first, and `#bake` / `#commit` are their own invocations. No dependency was added; no machine-shaped data file was opened; the device was not touched; and every claim about the code came from a file read.

## State

**A saved route's end now resolves by the smallest zone (2026-10-18).** [`MapScreen.routeEndName`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5458) dropped its zone-only filter and resolves each end through [`MarkerMatcher.topNamingMatch`](../../app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:548) — the smallest zone it stands inside, or a near zone only when that zone is strictly smaller — so a route end recognises a nearby place exactly as a track end does. The flagged-selection fallback and the Port Salis refusal stay, and [`composeRouteName`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2468) still composes the three shapes: `A to B`, `To B`, `From A`, falling back to `Route <instant>` when neither end is named.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeEndName`, `composeRouteName`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — `topNamingMatch` (the shared rule's one home)

## Next Step

The route-end half is settled and green; the open points — no unit test for the idle-only-body rule and the inside-or-near reading — are recorded in the Tracks hydration and plan.
