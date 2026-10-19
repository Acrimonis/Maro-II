# Context Hydration — Route — 2026-10-19

**Last Bake:** 2026-10-19 18:43 UTC — written by `#bake` on `feature/route-gogo`; the panel work is committed in `8f728c58`, its follow-up (order on landings, end-of-search selection, toast) is built and not yet committed.

**Directive trace:** two covered action classes were met and none stopped. Authorisation held — the implementation ran on the user's `#impl`, and on their word "Implement as described" at the confirmation gate for the follow-up; `#commit` was confirmed at its scope gate and `#bake` is its own invocation. No dependency was added; no machine-shaped data file was opened; the device was not touched; every claim about the code came from a file read.

## State

**The route search panel reports each route's own search.** Every rung narrates its own stage chain (opening on a `GRID` word for the wait on the shared grid) and a landed row prints its `#rank` where the stage word stood; ranks are live over the rungs landed so far and count a merged route once, the crown's check waits for the last rung, and the header reads `Landed x/n · cruising speed · preference` (the counter only while a route computes, `Any (12 kn)` under `Any`). A first provisional pair now rides the search → pull boundary. **The rows reorder only when a route lands, is refused or is merged away** (the one-second gate of the first plan was built, committed and then withdrawn on the user's word); a hand pick holds through the search, the last landing selects the top row and raises `route_best_selected_toast` when it moved a pick. The full unit suite and `assembleDebug` are green; the device pass is the user's.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the order key, `settleSeat`, the fold re-find, `rankView`, `seatMovedAtEnd`
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `RoutePage.stage`, `routeRankView`, `routeTargetOrder`, `routeApplyOrder`
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the header line, the slot, the crown gate
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt`, `spatial/multipass/RoutePassRunner.kt` — the per-lookup stage chain and the PULL-boundary pair
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the order, the rank view and the toast wiring
- `xTrack/Route/261019_FEAT_PLN_Route_route-sch-sort-landing-order.md` — the plan that stands where the two disagree

## Next Step

The user's three follow-ups, still unscheduled in the code: remove the menu drawer's live summary card while a route search runs (the followed-route card stays), decide whether *Save to track* waits until the selected route has landed (it opens today on a partial line, R89), and shorten the new-search notice to *New* — the toast it means is not yet pinned.
