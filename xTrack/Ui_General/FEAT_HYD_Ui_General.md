# Context Hydration — Ui_General — 2026-09-28

**Last Bake:** 2026-09-28 19:45 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met and none stopped this session — no dependency was added, no machine-shaped data file was opened, every write followed an order (the branch on `#new`, each pass on `#implement`), the device was touched only for a read-only logcat after the user's own navigation, and every claim written about the code rests on a file read in the session. The one gap left open is named in the Next Step: the Ui_General row of `## Feature Summaries` is longer than a tool can read back whole, so its Modified date and its map clause are stale.

## State

One branch, `feature/misc-ui-n-routing`, cut from `origin/develop` on the user's `#new`: the map's filter is now the only thing the map draws, the editor returns to the card it came from, an item's departure advances, and every list-kind card greys its own ends.

**The map draws its filter alone.** Three escapes were deleted — the marker's reveal-on-select, the highlighted track's eligibility override in `TrackSelectionPolicy` (its dead cap rescue with it, the ranking that puts the highlighted first kept), and the pinned carve-out's repeated OR in `storedTrackSelection` (its `highlightedTrackId` parameter gone) — so no pinned, highlighted or opened item rides past the map filter.

**Which write closes which card.** `scopeClosed`'s `MAP` arm is `false`: a click on the map seats a single item whose standing is not the filter's business, so a filter write leaves it standing, where `LIST` closes on the list's write and `INSPECT` on the map's. The map-opened card now reads the map's own unfiltered source (`cardWalkWorld`'s map collection) so it holds through its own write and closes only on a genuine deletion; the menu chevron gained `DrawerSource.MENU`, its first item and its walk reading the menu's own referential — the map filter — rather than the list world; and the dead `DrawerSource.WHERE_AM_I` went at every site, the match panel staying and walking nothing.

**The editor returns, a departure advances.** One pure rule in [`CardWalkPolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt) — `advanceAfterDeparture(departedId, world, excluded)`, next then previous else none — serves both cards; the card and the editor resolve in the world the card walks rather than in the list world, and `startWizard(markerId, door)` gained its required door, so a card-entered edit restores `Viewing` on a save and on a cancel while a list-entered one keeps its close. The close is issued from the state layer's `reconcileOpenCard`, one call per write, which is what let `MarkerDrawer`'s `marker_not_found` branch and its string in both locales be deleted.

**The ends grey.** `cardStepEnds(source, …)` names both doors of the item's-list kind — LIST and MENU — so a card opened from the menu chevron greys both pills at its ends exactly as the panel's card does, removing the click rather than leaving it enabled and dead.

**Carried.** The route-dialogs section still holds two open todos: its device pass over the panel, the exit dialog and the aim ring, and the removal of the three diagnostic log lines in `RouteHost` once that pass has answered.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt` — `advanceAfterDeparture`, `cardWalkWorld`, `cardWalkSource`, `CardStepEnds` / `cardStepEnds`, `cardReconcile`, `TrackCardSource`, `trackScopeClosed`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `DrawerSource`, `scopeClosed`, `WizardDoor`, `reconcileOpenCard`, `returnToCardView`, `cardReturnRequest`, the item writes resolving off `_allMarkers`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — `ViewingContent`'s resolution in the card's own world and `MarkerPrevNext`'s `CardStepEnds`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `openSelectedTrack`, `TrackDrawerState`, the chevron's ids read from the map filter, `inspectTapPickId` and its bounded ladder pass, the `MaroFilter` instrumentation
- `app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt` · `ui/map/MapTrackOverlayEffects.kt` — the deleted escapes
- Tests: `CardWalkDecisionsTest`, `AdvanceAfterDepartureTest`, `MapSelectionPolicyTest`, `DashboardScopeClosedTest`, `TrackRouteRoleTest`
- `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_map-cards-and-the-filter.md` · `260928_FEAT_PLN_Ui_General_edit-return-and-advance.md`

## Next Step

The device pass this session's work owes: a card opened from the menu chevron greys both pills at its ends, an armed tap on a marker opens the card that matches, both chevrons show what their own referential holds, and the map draws nothing but its filter's set — no reveal, no pinned or highlighted escape. Then the section's own carried work: the route panel, exit dialog and aim-ring pass, and the three `RouteHost` log lines removed once it has answered. Owed to a `findstr`-based patch, since the line is longer than a tool can read back whole: this feature's row in `## Feature Summaries`, whose Modified date and whose map-close clause are stale.
