# Context Hydration — Route — 2026-10-10

**Last Bake:** 2026-10-10 15:24 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-09 12:43 UTC) the session ran on the user's own words: the acquisition door's two-state word, the distance cell's one pulsing border and the exit act's cost-following fix implemented, then `#bake`, `#commit` and `#push` invoked together. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: `#new small-potatoes` cut `feature/small-potatoes` from `origin/develop` at `63ca7c07` with `--no-track`; `#merge` then took develop's five commits by **stash → `git rebase origin/develop` → stash pop** with no conflict, and `git diff --check` is clean. Nothing was pushed.

## State

**The branch.** `feature/small-potatoes`, fast-forwarded onto `origin/develop` (`7d0c4a5d`), the session's work uncommitted and the `app/` tree develop's own.

**The acquisition's third door stopped borrowing the exit dialog's long loss word, and became two-state.** While the selected line is unwritten it reads its own short `Discard` / `Abandonner` (`route_acq_discard`) — it holds a third of the weighted footer row, where the dialog's longer loss word would not fit — and once the line is a track it hands over to the exit dialog's own `Leave` / `Quitter`, accent and not red, derived from `routeExitDoors(written = true).loss`: the word follows the **cost**, never the surface. Both faces are one pure home, [`RouteExitDoors.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteExitDoors.kt), where the dialog's three doors and the acquisition's ending door each carry their act.

**The distance cell's two faces wear the one pulsing border.** The trip reading and the shore reading alike wear `AppConfig.routeNavigateColor` at the app's one beat — the border marks the *followed route*, so a flip changes the reading and never the border. `DashboardCard` now takes a whole `BorderStroke` rather than a colour plus a width, the width a fixed `2.dp` constant (`DASHBOARD_CARD_BORDER_WIDTH`) beside the card.

**The fix: the ending's act follows its cost too.** `RouteExitDoor` carries a documented `discards`, true only where the press would throw the line away; a loss-free *Leave* on either surface ends the mode **silently** (`endRouteMode()`), with no `pendingDiscard` window and no *Route discarded* toast. The acquisition panel gained `onLeave` for it, where the discarding press still defers on the toast (R92).

**A withdrawn change, recorded.** The tile border's width was briefly moved into `ui.properties` with a fade floor of its own and then **withdrawn on the user's verdict that it added no value** — the width is a fixed `2dp` constant again, with no key and no accessor anywhere.

**No open walk.** The feature file holds no `## Walk` (and no `## Implemented`: it is state-only by design, the log stripped on the user's word of 2026-10-07), so nothing bars a fold.

**What is owed.** The device passes the feature file itself lists (R97) — the tile-layer pass, the `selective` coastal pass, the fine-tail measurement, the fan's arc and enablement, the acquisition's face and paint order, the paging table, the mark count's Phase 3 reading, the rung ranking's acceptance, and the loss mirror with the arrival cue. None gates a code landing.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteExitDoors.kt` — `RouteExitDoor.discards`, `routeExitDoors` and the new `routeAcquisitionEndingDoor`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — the `BorderStroke` parameter, `DASHBOARD_CARD_BORDER_WIDTH` and the cell's one pulsing border
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the ending door resolved from `frontSaved` and the new `onLeave`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MapDialogHost.kt` — the `onLeave = { endRouteMode() }` wiring and the door call sites
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — `route_acq_discard` (both locales)
- `app/src/test/java/ykws/android/maro/ui/map/RouteExitDoorsTest.kt` — the two-state door's cases
- `docs/ui-component-guidelines.md` — the door family's cost-following note (§5.6)
- `xTrack/Route/FEAT_DSC_Route.md` — the Concept door clause, R90's action clause, one Rules line and the two Key Files rows; R92's clause was already corrected and left as it stands

## Next Step

No code task is open on the feature; the owed device passes (the feature file's own list) are the user's own.
