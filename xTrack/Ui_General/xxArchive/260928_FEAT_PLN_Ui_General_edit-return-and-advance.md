<!-- scope: feature -->
# The editor hands its card back, and the advance reaches the item's own departure

Moved here from `xTrack/Markers/` on the user's word: the rule spans both cards, so it belongs to
Ui_General, and the rule's own home is the advance plan it widens. Session 2026-09-28 on
`feature/misc-ui-n-routing`, after three independent reviews of its drafts.

> **Status:** shipped, corrected a third time — the decisions are settled and the two corrections in §2
> are written in, so this plan is buildable as it stands; §6 lists what is owed rather than done, and two
> calls in it are still the user's, neither of which blocks the writing of the change.
> **Scope:** Ui_General's rule and both cards; the marker editor's return is the Markers half of it, and
> the track card takes the delete half alone since it has no editor.
> **Widens:** `260816_FEAT_PLN_Ui_General_delete-advance-next.md`; R2's close in
> `260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md` stands as written.

## 1. What the user settled

- **Editing a marker from its card returns to that card** — on a save and on a cancel alike.
- **The camera flies back to the marker** on that return, and a step frames its target the way opening it
  does, zoom included.
- **A dashboard never points at nothing.** A deletion advances to the adjacent item, and an edit that
  leaves the item out of the world its card walks advances the same way, closing only when that world is
  left empty.
- **A filter edit, a sort or a reset still closes the card.**
- **Whoever has the hand to notice a change that would filter the item out acts on it.**
- **A card that cannot find its marker closes** instead of rendering an empty one.
- **The card's neighbours are rebuilt** from what the screen shows after a removal, with the cursor on
  the neighbour.
- **The match-result card closes on Back, or on any action that needs the dashboard slot**, and never
  advances. The moved Markers plan is archived, and the next bake writes the front-matter date and the
  pointer list.

## 2. The two corrections this plan now opens with

- **The card and the editor must resolve in the world the card walks**, not in `_markers`: today both
  read the list world ([`MarkerDrawer.kt:164`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:164),
  [`MarkersViewModel.kt:629`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:629)),
  which is why a map-tapped marker — seated in the walk world by the tap itself
  ([`MapScreen.kt:2583`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2583)) — can render
  `marker_not_found` at [`MarkerDrawer.kt:296`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:296).
  Fixing the resolution is what makes the not-found close the *genuinely gone* case; keeping the list
  world leaves an unlinked map tap opening straight into a close.
- **The close is issued from the state layer, never from composition.** A composable cannot close its own
  drawer while it composes, so the resolve-and-close lives in the view model's refresh path — the same
  place that already re-tests membership in
  [`applyScopeGuard`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:520) — and the
  drawer's not-found branch stops being reachable for a live card.

## 3. The rule's real home, and the four sources

- **A pure function, not a shared view-model helper.** The rule that can be one home is the *ordering*:
  departed id + the ordered world + the set to exclude → the next id, else the previous, else none. It
  belongs beside the other pure policies, because the two cards live in different state owners — the
  marker advance in [`MarkersViewModel`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:868)
  and the track one in the screen over `TrackViewModel`
  ([`MapScreen.kt:3115`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3115)) — so no single
  view-model helper can serve both, and the inspect re-seat is screen state besides
  ([`MapScreen.kt:3156`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3156)).
- **The exclusion stays with its callers**: the screen's pending-deletion set
  ([`MapScreen.kt:664`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:664), keys `t:`/`m:`) and
  the view model's own pending set ([`MarkersViewModel.kt:974`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:974)).
  Each passes what it holds into the pure function, and the undo path and the startup orphan cleanup
  ([`MapMarkerEffects.kt:47`](../../app/src/main/java/ykws/android/maro/ui/map/MapMarkerEffects.kt:47)) are
  named as callers whose exclusion is "none" — the wizard's save is the case the previous draft claimed
  an exclusion it cannot receive.
- **The four sources, named**: a list-opened card walks the list world; a map-tapped one the map set the
  tap came from; an inspect-opened one the **frozen ladder**, held in the screen for
  `DrawerSource.INSPECT` ([`MapScreen.kt:538`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:538),
  resolver at [`:1453`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1453)), whose
  departure test is the map world ([`MarkersViewModel.kt:97`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:97));
  and a match-result card walks a match set and never advances. The earlier draft's "the ladder for a
  list-opened card" was wrong.
- **An id absent from the ladder closes, it does not hold still.** Both the track twin
  ([`MapScreen.kt:3122`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3122) with its
  `onNone` at [`:3128`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3128)) and the marker
  path ([`:3169`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3169)) close in that case, so
  the test asserts the close and the earlier "hold still" claim is withdrawn.

## 4. The work

- **The editor's return.** `startWizard(markerId)` gains an explicit door parameter — the two callers are
  known ([`MarkerDrawer.kt:286`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:286) and
  [`MapScreen.kt:2979`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2979)), so nothing is
  inferred: card-entry restores `Viewing` with the same selection on save and on cancel; create keeps its
  close and Snackbar; list-entry keeps today's ending.
- **The card's neighbours, rebuilt.** "What the screen shows" is the world of §3: rebuilt by the state
  owner of that world, minus the departed id and minus the caller's exclusions, with the inspect cursor
  re-seated or dropped exactly as the delete advance already does
  ([`MapScreen.kt:3156`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3156)).
- **The departure fires from the write.** The item writes and the wizard's save end by asking the pure
  function whether the selected id is still in that card's world, and act on its answer — advance, else
  close. The filter, sort and reset writes stay with R2's close.
- **The camera, two framings.** The return: the guard is cleared on the framing effect's own `Editing`
  key, and the effect steps aside while an inspect open is landing
  ([`MapScreen.kt:2649`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2649)), so that gate
  is respected rather than removed. The step: the effect's lookup widens from the list world to the card's
  own world, which is what the user asked for; it must also answer the rule that a card's close restores
  the frame it was opened on, and the track card's "a step keeps the frame the open captured"
  ([`:3096`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3096)) — a step's zoom is a
  deliberate exception that has to be written into both.
- **The delete leftovers.** The management list's delete
  ([`:2981`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2981)) closes every open card: a
  card showing a *different* marker must stand, and the deleted item's own card must advance rather than
  close. The ladder-absent case is not a leftover but the close §3 settles.
- **Boundaries.** The match-result card keeps its four closes — Back, a slot handoff, a map drag
  ([`:1037`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1037)) and the recorder's idle
  exit ([`:1132`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1132)) — and an inspect
  walk keeps R2's close on a filter change.

## 5. Verification

- Unit tests over the pure function: next-then-previous ordering, the exclusions honoured, a last item
  falling back to the previous, an emptied world yielding none, and an id absent from the world yielding
  none.
- View-model tests: the door is recorded per entry and cleared on create; a card-entered edit returns to
  `Viewing` with the same id on a save and on a cancel; a list-entered edit ends hidden; an item write
  that leaves the world advances; a filter edit still closes; a card whose marker cannot be resolved
  closes.
- Device moves: edit-save-return and edit-cancel-return with the camera watched; an inline pin or icon
  edit that leaves the item out of its world; a step across the list world's edge, which must frame its
  target; a filter edit under an open card; the management delete on a *different* marker; and an
  inspect-opened card, whose filter close stays.

## 6. Owed, not done

- **One call still the user's**, not blocking the writing: whether the step's zoom stands as an
  exception to a card's close restoring the frame it was opened on, or the close restores the last step
  instead. The second call is answered — the not-found branch and its string are deleted, see `## Outcome`.

- The moved Markers plan is still a tombstone under a live `FEAT_PLN_` name, and `xTrack/Markers/xxArchive/`
  does not exist yet: the archive with its index row is work, not a description.
- The epic's front-matter date is stale at 2026-09-23; the second rule bullet the amendment added is
  already in the epic, so only the date is owed to `#bake`.
- Written on the day: the Ui_General rule sentence, the close-conditions plan, the advance plan's
  decision 8, and the epic's closed walk of five items.

## Outcome

Implemented on `feature/misc-ui-n-routing` on 2026-09-28, in this plan's own order.

- **The rule (§1, §3)** — `advanceAfterDeparture(departedId: String, world: List<String>, excluded: Set<String> = emptySet()): String?` in [`CardWalkPolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt): the next id, else the previous, else none; an id absent from the world yields none. Pure, Android-free, pinned by `AdvanceAfterDepartureTest` (12 tests). The same function serves the marker advance (`MarkersViewModel.reconcileOpenCard`) and the track delete (`MapScreen.onDeleteTrack`), so the two cards cannot disagree about "the next item".
- **The two corrections (§2)** — the card and the editor resolve in the world the card walks, and which of the four sources reads which collection is the pure `cardWalkWorld`, one home for `MarkersViewModel.cardWorldContains` / `resolveCardMarker` and `MarkerDrawer.ViewingContent`; the editor's own two writers (`updateMarker`, `updateMarkerText`) read the unfiltered source of truth like `setMarkerIcon`, so a write on a card the list world does not hold is never dropped. The not-found close is issued from the state layer (`reconcileOpenCard`), whose decision is the pure `cardReconcile`, and whose **one owner is the write path** — every item write, both inline writes and the wizard's save — rather than the `markerChanges` reload as well: one call per write, ordered after the state that write left behind, since a test taken on that channel can run while the wizard's save still reads `Editing` and so see nothing to reconcile. The drawer's `marker_not_found` branch and its `<string name="marker_not_found">` key in `values/` and `values-fr/` are **deleted** (2026-09-28): no live card could reach them, and what replaced them is a plain early return in `MarkerDetailContent` for a marker that is momentarily null, which draws nothing.
- **The inspect card's return, and the ladder it loses (folded review, blocker 3)** — an inspect-opened card edited from its own card comes back on the **map world**, not the frozen ladder. The cause is pre-existing and not this plan's: entering the wizard takes the drawer off `Viewing`, and that is the inspect mode's single exit, which drops the cursor together with the ladder it carries — the mode's disarm on the wizard's door does the same. The return therefore has no cursor left to re-seat: `returnToCardView` opens through the pure `cardWalkSource`, which drops an inspect source to the map world — the collection the ladder is ranked from, so both pills come back **live** rather than disabled, which is the blocker — and offers the screen a one-shot `cardReturnRequest`, in the shape of `mapCenterRequest`, which re-seats the cursor and lifts the card back onto the ladder the moment that ladder outlives an edit. Both branches are pinned by `CardWalkDecisionsTest`; only the drop runs today. Recovering the ladder means keeping the mode's cursor across its own exit, a change to the inspect exit rather than to this plan.
- **The rest of the folded review** — the track delete now hands `openSelectedTrack` the whole ordered remainder (`advanceCandidatesAfterDeparture`, the ordering rule's own home, of which `advanceAfterDeparture` is the head) instead of one id, so a candidate whose geometry cannot load no longer swallows the advance; the match-marker resolution and the four-source world rule each have one home (`matchedMarker` — read by all five former copies in `ui.map`, two more than the review counted: `MarkerOverlay`'s highlight set and the click-N-move navigation set — and `cardWalkWorld`); `startWizard(markerId, door)`'s door is required and both call sites name it; and the drawer's duplicated imports are gone. The rule keeps two further homes outside `ui.map` (`MarkerMatcher.markerOf`, `TrackRecorder.markerOf`), which cannot read this one: the module direction forbids it, and unifying them would mean moving the rule into `spatial` beside `WhereAmIMatch`.
- **The editor's return (§4)** — `startWizard(markerId, door)` gained `WizardDoor.CARD`/`LIST`. Card entry restores `Viewing` with the same selection on a save and on a cancel; create keeps its close and Snackbar; list entry keeps today's ending. The four sources of §3 are the resolution worlds.
- **The delete leftovers (§4, item 9)** — the management list's delete now leaves a card showing a *different* marker standing and advances the deleted item's own card, re-seating or dropping the inspect cursor (`MapScreen.advanceMarkerCardFrom`, shared with the drawer's own delete, and through the same `cardWalkSource` the editor's return reads), rather than closing every open card; an id absent from the walk world closes.
- **The one open call, implemented as the default named** — the marker card's step frames, and so zooms onto, its target, and that zoom stands as the deliberate exception to a card's close restoring the frame it was opened on, written where both rules can be read together (`closeTrackDrawer` and the track step's own comment). It remains reversible. The second call is no longer open: the not-found branch and its string are deleted, as the bullet above records.
- **Verification (§5, after the fold)** — `gradlew :app:assembleDebug` SUCCESS; the scoped `*Marker*` / `*Track*` / `*Dashboard*` / `*Inspect*` / `*CardWalk*` / `*AdvanceAfterDeparture*` / `*ScopeClosed*` suites green at **249 tests, 0 failures**, the new `AdvanceAfterDepartureTest` (12) and `CardWalkDecisionsTest` (18) among them. The fold made the two decisions the review named testable in this module by extraction rather than by a dependency: `cardWalkSource(source, cursorReSeated)`, `cardReconcile(cardOpen, selectedId, worldHoldsSelected, source, advanceTo)`, `advanceCandidatesAfterDeparture(departedId, world, excluded)` and `cardWalkWorld(source, listWorld, mapWorld, matchWorld)`, all in [`CardWalkPolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt) beside `advanceAfterDeparture`, all Android-free.
- **Deviation from §5, and what the fold could not reach** — the "view-model and card tests" are still not added. The module carries only JUnit and `kotlinx-coroutines-test`; there is no Robolectric and no Compose UI-test dependency, so an `AndroidViewModel` and a `@Composable` card cannot be exercised in a JVM unit test without adding one (AGENTS.md §4, the user's word). The scenarios the fold makes reachable are covered at their decisions: the return on save and on cancel (`wizardEditReturnsToCard`, `cardWalkSource`), the advance after an item write and the not-found close (`cardReconcile`), the four-source world rule (`cardWalkWorld`) and the filter edit still closing (`scopeClosed`, pinned already). What remains **out of reach in this module**: the door as *recorded* state and its clearing on create, the drawer's own transitions (a card-entered edit landing back on `Viewing` with the same id, a list-entered edit ending hidden), and the camera on a save and a cancel — all of which need the ViewModel or the composable to run, plus §5's device moves. Adding Robolectric (and, for the card, Compose UI-test) is the one line that would close that gap.
- **Owed to `#bake`, not done here** — the moved Markers plan's archive row, and the epic's stale front-matter date.
