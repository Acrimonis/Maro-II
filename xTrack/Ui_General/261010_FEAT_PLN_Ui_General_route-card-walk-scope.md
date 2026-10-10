# Ui_General — The card's walked list and the close, made kind-aware

**Status:** approved 2026-10-10 — implementation under way on `feature/rte-n-trck-list`
**Feature:** Ui_General — the card-walk rules and the dashboard close entry point
**Caused by:** Ui_Menu's list split → [`261005_FEAT_PLN_Ui_Menu_route-list-separation.md`](../Ui_Menu/261005_FEAT_PLN_Ui_Menu_route-list-separation.md) (S2, S4, S11)
**Trigger:** the user's report, 2026-10-10 — the tracks/routes split regressed how the lists behave when walked in the dashboard

## Request

- Make the dashboard card's walked list the card's **own kind**, so a route opened from the Routes list steps routes and a recorded track steps tracks.
- Return the delete-advance to the same kind's list.
- Fold in the R2 close's blindness of the same origin: a write to one kind's list must not close the other kind's card.

## Root cause

Four reads kept pointing at one kind after the lists became two, each with the same shape — a kind-blind lookup where a kind-locked one is now required:

- **The walked list.** [`trackListIdsOf()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4127) builds the card's world as `walkWorld ?: trackSummaries…`, and [`MapScreen.kt:3555`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3555) hands it `trackSummaries` — the flow that holds recorded tracks alone, `route` filtered out at [`TrackViewModel.kt:119`](../../app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:119).
- **The door that hands nothing.** A list card is opened through [`ListAction.NavigateToItem`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3646) with no `walkWorld`, so the fallback above applies — and a route's own id is therefore absent from its walk world: `currentTrackIndex` coerces to 0 and the pills read one of N recorded tracks ([`MapOverlayData.kt:100`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:100), [`OverlayLayer.kt:536`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:536)).
- **The delete-advance.** Its world is `trackSummaries.filter { !it.isLive }` ([`MapScreen.kt:3870`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3870)), so a delete from the Routes list advances onto a recorded track.
- **The R2 close.** [`trackScopeClosed()`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt:228) takes bare booleans, so a list write of either kind closes either list's card — [`applyRouteFilterChange`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4270) passes `trackListWorld = true` exactly as the track callbacks do.

The row lists themselves are correct: [`OverlayLayer.kt:272`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:272) picks `routeSummaries` at `ListScope.ROUTES`. The defect is the card, never the list's content.

## Target

```text
The card's world (source LIST, no handed-over world)
  a route card   → routeSummaries   (its own kind's ids)
  a track card   → trackSummaries   (recorded tracks)
  walkWorld non-null                → the handed world, unchanged (menu / inspect)

The delete-advance world            → the deleted item's own kind, same rule
The derived reads                   → unchanged: they follow the world above
  currentTrackIndex · the pill ends · the two Prev/Next bodies

The R2 close                        → the card's kind must equal the write's kind
  a TRACKS list write               → closes a track card alone
  a ROUTES list write               → closes a route card alone
  a TRACKS map write                → closes a track menu/spy card (+ its list when linked)
  a ROUTES map write                → closes a route menu/spy card (+ its list when linked)
  a marker write                    → unchanged (its own two flags)
```

## Steps

- **S1 — the pure rules.** In [`CardWalkPolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt): `cardWalkListIds(walkWorld, cardIsRoute, trackSummaries, routeSummaries, pendingDeleteIds)` for the walked list, and `trackScopeClosed(source, cardIsRoute, listWorld: ListScope?, mapWorld: ListScope?)` for the close. Both pure, both Android-free, so a JVM test pins each.
- **S2 — the card's own kind reaches both readers.** Collect `routeSummaries` in `MapScreen` beside `trackSummaries`; feed `cardWalkListIds` the open card's kind (`trackDrawerState.track?.route`) at [`MapScreen.kt:3555`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3555) and, for the delete-advance, the departed item's kind read from `allTrackSummaries` ([`MapScreen.kt:3870`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3870)).
- **S3 — the close names its kind.** `CloseDashboards` and `closeDashboardsForScopeChange` carry `listWorld: ListScope?` / `mapWorld: ListScope?` in place of the two kind-blind booleans, and the fifteen call sites name the kind they write: the track callbacks `ListScope.TRACKS`, the route callbacks `ListScope.ROUTES`, a linked write passing both the list and the map token, the marker callbacks `null`.
- **S4 — the derived reads verified, not edited.** `currentTrackIndex`, the pill ends and the two Prev/Next bodies all derive from `trackListIds`, so they follow S2 with no change of their own; the step is a read-back, so the fix stays one cause.
- **S5 — the tests.** Extend `DashboardScopeClosedTest` to the new signature with a per-kind case, and add `cardWalkListIds` cases to `CardWalkDecisionsTest`: each kind walks its own ids, a handed-over world wins, the live recording and the pending deletions stay out.
- **S6 — the record.** `FEAT_DSC_Ui_General.md` on the pass, with the pointer to this plan; Ui_Menu's own file carries the split it came from.

## Decisions

- **D1 — the kind is read off the card, not carried as a new field.** `trackDrawerState.track?.route` already answers it, so no new plumbing at every open site. **Objection:** it assumes an item's kind equals its list's — which holds only because the split made both lists kind-locked (Ui_Menu plan D2), and would break the moment a list were allowed to hold both kinds again.
- **D2 — the write's kind travels as `ListScope?`, not as extra booleans.** One token per kind makes the cross-kind typo class impossible; the two-boolean shape is what let a *track* map write name the *marker* map world in [`applyTrackMapFilterChange`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4220).
- **D3 — the rules stay pure and in the walk policy's own file**, beside `cardWalkWorld` / `cardStepEnds`, so both are unit-tested without a device — the reason `CardWalkPolicy.kt` exists.

## Finding fixed on the way

- [`applyTrackMapFilterChange`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4220) and its route twin passed `markerMapWorld = true` for a track/route map write, so a track map-filter change closed a marker spy or menu card. The kind-explicit flags drop it; it is a behaviour change on the marker side and is named as such rather than carried.

## Verification

- The scoped `ui.map` suite (with `DashboardScopeClosedTest` and `CardWalkDecisionsTest` extended), then `apk-build.bat`.
- Device pass owed: a route opened from the Routes list with its counter and both pills stepping routes; a recorded track the same the other way; a delete from either list advancing inside its own kind; a Tracks filter write leaving a route card standing, and the reverse.
