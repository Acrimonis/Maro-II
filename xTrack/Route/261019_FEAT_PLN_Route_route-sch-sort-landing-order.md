<!-- scope: feature -->
# Route search panel — the order moves on landings, the last landing selects the winner

Follow-up to [`261019_FEAT_PLN_Route_route-sch-sort.md`](261019_FEAT_PLN_Route_route-sch-sort.md), written the same day after the user's answers. Where the two disagree, this one stands: the earlier plan's one-second gate, its `route.panel.orderRefreshMs` key and its *provisional rows by time* order group are withdrawn.

## Rules (user, 2026-10-19)

1. **The row order is rewritten only when a route lands, is refused or is merged away.** A stage word, a provisional figure or a rank number updates its row in place and moves nothing. With at most three routes a row moves at most three times, so no gate is needed; the gate class, its tests and the property are gone.
2. **The order is ranked rows by rank, then landed rows without a rank by time, then the rest in the ladder's order.** A provisional pair sorts nothing.
3. **Untouched, the selection follows the best landed route** — the top row — at each landing (unchanged).
4. **A hand pick holds through the search.** The highlight stays on the picked route as the rows reorder and as a fold shifts the indices (re-found by lookup id; a pick that was the folded twin moves to its survivor).
5. **When the last route lands, the selection moves to the top row**, in the same step as the crown's check, overriding a hand pick. A committed early select is left alone.
6. **A toast says so** — `route_best_selected_toast` on the map banner, shown only when that move changed a hand pick.

## Design

- `RouteViewModel`: one collector keys on *(each page's lookup id and whether it is settled, the verdict, the folds)* and rewrites the order snapshot on a change. `settleSeat(pages)` runs from the landing, fold and refusal branches; it moves the selection to the crown's page once no page is unsettled, clears the hand-pick hold and emits `seatMovedAtEnd` only when it moved a pick. `MapScreen` collects that flow into the existing `routeNoticeToast`.
- `routeTargetOrder` drops its provisional group.

## Open

- `settleSeat` does nothing when the set crowned nothing, so a pick then holds for good — accepted.
- A hand pick on a route still computing is held, and a press on *Select* is then refused by the panel until that route has a line.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the order key, `settleSeat`, the fold re-find
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `routeTargetOrder`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the toast collector
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `route_best_selected_toast`
- `app/src/main/assets/maro.properties`, `config/AppConfig.kt` — the interval key removed again
- `app/src/test/java/ykws/android/maro/ui/map/RouteAcquisitionTest.kt` — order on landings only, the end-of-search move and toast, the pick across a fold

## Implemented

2026-10-19, on `feature/route-gogo`; not yet committed. `:app:testDebugUnitTest` (full suite) and `:app:assembleDebug` green; the device pass is the user's.

- Rules 1–6 as above; `RouteOrderGate.kt`, `RouteOrderGateTest.kt`, the property and its `AppConfig` field deleted.
- The Ask review found no blocker; it flagged the hand pick across a fold, fixed here with a test.
