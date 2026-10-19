# Context Hydration — Route — 2026-10-19

**Last Bake:** 2026-10-19 11:59 UTC — written by `#bake` on `feature/route-gogo` (cut from `origin/develop` by the user's `#new route-gogo`).

**Directive trace:** one covered action class was met and none stopped. Authorisation held — the implementation ran only on the user's `#impl` after a discussion and a review of the plan, and `#bake` / `#archive` / `#commit` / `#push` are their own invocations, the archive taken on the user's "as suggested". No dependency was added; no machine-shaped data file was opened; the device was not touched; and every claim about the code came from a file read.

## State

**The menu's Go restarts the search from any state (R101, 2026-10-19).** [`goRoute`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) settles a standing discard window, ignores the press while a route write is in flight, refuses a too-short pair before anything ends, then restarts: a search or a followed line already a track ends and re-arms silently, while a followed line not yet written raises the exit dialog under its new `RESTART` reason (*Save and restart* · *Keep following* · *Discard route*), *Save and restart* arming only after the write lands. Every arming that took raises `route_started_toast` on the map banner, renamed `routeNoticeToast`. The same session moved the user-facing word from *acquisition* to *route search* and French from *itinéraire* to *route* across the app, and gave the fan's icon-only children self-contained names. `:app:compileDebugKotlin` and the `ui.map` suite are green; the device pass is the user's, and whether the menu stays open behind the restart dialog is unread. `#archive` then retired eight shipped plans into `xxArchive/` (verdict-exchange-rate, ladder-lambda-keys, reclaim-alert-cache-ceiling, warm-area-coverage-and-lifecycle, selective-retirement, no-preferred-speed, route-fan-decouple, home-area-eviction), promoting their open points into the DSC todos, the decisions doc §7 and the owed device passes.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `goRoute`, `restartRoute`, `armFreshRoute`, `saveRouteTrack`'s after-write hook, `routeNoticeToast`
- `app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt` — `RouteExitReason.RESTART` and its two endings
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — the fan children's self-contained names
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the route-search and route copy

## Next Step

The device pass on Go: a press from idle, mid-search, over a saved followed route and over an unwritten one, reading the dialog's three doors and the menu's state behind it.
