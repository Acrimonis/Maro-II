# Context Hydration — Route — 2026-10-19

**Last Bake:** 2026-10-19 19:15 UTC — written by `#bake` on `feature/route-gogo`; the panel work, the landing-order follow-up and the menu/save/snack changes are committed (`8f728c58`, `d187b74f`, `46b72a65`), the fan's *Route (auto)* change is built and not yet committed.

**Directive trace:** two covered action classes were met and none stopped. Authorisation held — each implementation ran on the user's `#impl` or on their "Implement as described" at the confirmation gate, and `#bake` / `#archive` / `#commit` are their own invocations, the commits confirmed at their scope gates. No dependency was added; no machine-shaped data file was opened; the device was not touched; every claim about the code came from a file read.

## State

**The route search panel reports each route's own search.** Every rung narrates its own stage chain (opening on a `GRID` word) and a landed row prints its live `#rank` where the stage word stood, a merged route counted once; the crown's check waits for the last rung and the header reads `Landed x/n · speed · preference` (the counter only while a route computes). A first provisional pair rides the search → pull boundary. **The rows reorder only when a route lands, is refused or is merged away**; a hand pick holds through the search, the last landing selects the top row and raises a toast when it moved a pick. **The fan's *Route (auto)* child is live mid-search**: a press keeps the search, closes the fan, raises `route_auto_follow_toast` and follows the winner at the end; a too-short pair clears the intent. **The menu drawer shows no summary card during a search** (it stays while a route is followed), **`Save to track` waits for a landed line** (the early-save draft machinery stays in the code, unreachable from the doors), and **the snack's action reads *New* / *Nouvelle***. The full unit suite and `assembleDebug` are green; the device pass is the user's.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the order key, `settleSeat`, the fold re-find, `rankView`, `seatMovedAtEnd`
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `RoutePage.stage`, the rank view, the order functions, `routeAutoChildEnabled`
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the header line, the slot, the crown gate, the save gate
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the fan's enablement and actions, the summary flag, the toast wiring
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt`, `spatial/multipass/RoutePassRunner.kt` — the per-lookup stage chain and the PULL-boundary pair
- `xTrack/Route/261019_FEAT_PLN_Route_route-sch-sort-landing-order.md` — the plan that stands where the two sort plans disagree

## Next Step

The device pass on the panel: stage words then ranks on a real ladder, a hand pick holding until the last landing and its toast, the header fitting in portrait, the fan's *Route (auto)* pressed mid-search, and `Save to track` greyed until a line lands.
