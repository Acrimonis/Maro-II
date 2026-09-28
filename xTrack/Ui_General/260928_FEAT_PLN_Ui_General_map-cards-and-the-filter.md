<!-- scope: feature -->
# One rule for every card the map opens

Session 2026-09-28 on `feature/misc-ui-n-routing`, from the user's word after a device run of the pinned
filter, then two independent reviews. Consolidated to the three entry kinds, and corrected for what the
completeness review found missing. The line numbers are the reviews' reads; the implementing hop
re-derives each before it writes.

> **Status:** in design, corrected twice. Nothing is open, and the work items are the ones below rather
> than the rule's summary.
> **Widens:** `260928_FEAT_PLN_Ui_General_edit-return-and-advance.md`, implemented earlier the same day.

## 1. The feature set, as the user stated it

- **The item's list** — opened from a list, and **the corresponding filter applies**.
- **The spy toggle** — **no filter of its own**, and the dashboard walks markers and tracks **by
  proximity order**.
- **A click on the object on the map** — **no list; just one item**.

With them, **"the filter has a purpose. So it has to be enforced"**.

## 2. What each entry kind gives a card

- **The item's list**: the card walks that list under that surface's own filter and order — the
  management list's filter and sort, the menu chevron's filter and order, the track history's filter and
  sort — and its ends read alike. A list card whose world empties closes, which the earlier plan already
  supplies ([`edit-return-and-advance.md:73`](260928_FEAT_PLN_Ui_General_edit-return-and-advance.md:73)).
- **The spy toggle**: the card walks the proximity list — nearest to the map centre first — and keeps its
  arrows. **It has no filter of its own, but the map's still bounds what it can reach**: the candidate set
  is the map-filtered drawn set ([`MapScreen.kt:1095`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1095),
  [`:1098`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1098)), and a filter change keeps its
  close under R2 ([`MarkersViewModel.kt:97`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:97)),
  which §3's deletion below does not touch.
- **The spy card's two halves are not equally built**, and the plan says so instead of claiming otherwise:
  the marker half re-seats its cursor ([`MapScreen.kt:1970`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1970)),
  while a **track** card's delete walks the list world ([`:3259`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3259))
  and its undo reopens a plain drawer with no ladder ([`:781`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:781)).
- **A click on the map**: one item, no arrows. Its consequences are written rather than assumed — Delete
  survives ([`MarkerDrawer.kt:186`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:186)) and its
  undo reopens that one item because the Snackbar captured the handed-over world
  ([`MapScreen.kt:3267`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3267)); the pills vanish
  by themselves ([`MarkerDrawer.kt:204`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:204));
  Back still closes ([`:132`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:132)).

## 3. The drawing

- **The map draws its filter's set and nothing else — three sites, not two.** The marker reveal
  ([`MapScreen.kt:2626`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2626)); the policy's
  highlighted track ([`MapSelectionPolicy.kt:49`](../../app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt:49),
  whose cap escape at [`:58`](../../app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt:58)
  then goes dead); and **the pinned carve-out** that repeats the same OR
  ([`MapTrackOverlayEffects.kt:528`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:528)),
  which the first draft left out and without which a pinned track the filter excludes still draws.
- **R2's map-world close goes for a map-opened card — and only for that one.** `scopeClosed`'s MAP arm
  becomes `false` ([`MarkersViewModel.kt:96`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:96)),
  so a map tap's card stands when a filter hides its item; the LIST arm keeps its close, because the list's
  filter governs the list's card; and **the INSPECT arm is untouched**, since the spy card's close on a
  filter change is the earlier plan's rule and this pass does not overturn it. The two map-filter callbacks
  ([`MapScreen.kt:3173`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3173),
  [`:3189`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3189)) and the track's linked arm
  ([`:3008`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3008),
  [`:3020`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3020)) follow the same split: a
  linked map-filter write no longer closes a map-opened card, while the track list's own filter and sort
  still close the track card.
- **An item's own departure is not a filter's business**, and the two must not be confused: the reconciler
  tests map-world membership even for a one-item card
  ([`MarkersViewModel.kt:678`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:678)), so an
  edit that stops the item matching the map filter closes it — and §4 says the card stands. The
  implementing hop must make the one-item card's departure hold its card, and that is a work item, not a
  consequence.
- **Named as deliberate, so they are not rediscovered as leaks**: the marker being created or edited
  ([`MarkerOverlay.kt:184`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:184)); the live
  recording ([`MapScreen.kt:1152`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1152)); the
  route's furniture ([`RouteHost.kt:185`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:185));
  the debug rays ([`MapMarkerEffects.kt:121`](../../app/src/main/java/ykws/android/maro/ui/map/MapMarkerEffects.kt:121));
  and **the layer write that opening a card performs** ([`MapScreen.kt:1617`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1617)
  reaching [`showLayer()`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:440)), which is a
  layer and not a filter, and is named here so the distinction is on the record.

## 4. Every door, against the three kinds

| Door | Kind | What it carries, and the site |
|---|---|---|
| Marker management list, its wizard entry, the track history | the item's list | the list's filter and sort |
| **Menu chevron** | the item's list | **the menu's filter and order** — today the *first item* is read from the list ([`MapScreen.kt:2865`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2865) for tracks, [`:2866`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2866) for markers), and the opener hands no world at all: the marker chevron goes through [`openMarkerDetail`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2921) and so walks the management world, while the track chevron opens a one-item world ([`:2920`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2920)). The world and source belong in that opener's parameters ([`:1589`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1589)) |
| Delete Snackbar's undo reopen, the track delete's undo | the item's list | the captured ladder ([`MapScreen.kt:799`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:799), [`:3259`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3259)) |
| Spy toggle pick, and an armed tap | the spy toggle | proximity, no filter of its own |
| **Plain map tap on a marker** | a click on the map | none — **today the tap hands over the whole map world** ([`:2668`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2668)), so this is a change to `openEditDrawer(listOf(sel))` |
| A marker's track link, the track resume's card | a click on the map | none — one item ([`:3065`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3065), [`:3413`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3413)) |
| **The recorder's idle auto-open** | ~~a click on the map~~ **the match panel** | it opens the where-am-i result, not a marker card ([`:1127`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1127)), and that panel never walks |
| `DrawerSource.WHERE_AM_I` | **dead, and its removal is an item** | the enum ([`MarkersViewModel.kt:65`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:65)), `walkWorldName` ([`:114`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:114)), `cardWalkWorld` ([`CardWalkPolicy.kt:81`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt:81)), the two defaults ([`:466`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:466), [`:471`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:471)), the wrap branches ([`:534`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:534), [`:555`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:555)), `matchWorld()` ([`:668`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:668)), and the navigate flow's set ([`MapScreen.kt:2784`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2784)). The match panel itself is live and walks nothing ([`MarkerDrawer.kt:318`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:318)) |
| Create-first doors: the list's empty-state action and the fan's Add Zone | the item's list | they open the wizard, not a card ([`:3209`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3209), [`:2287`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2287)) |
| Outside the rule — panels that take no walk | — | the marker→track link inside the management list ([`MarkerManagementOverlay.kt:203`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:203)), the GPX import sheet ([`:3360`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3360)), the notification tap ([`TrackRecordingService.kt:409`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:409)), the zone tiles and info text ([`DashboardPanel.kt:81`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:81), [`RegulatedZoneComponents.kt:233`](../../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:233)), the route panel ([`RouteConfirmPanel.kt:1`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:1)) and the lock and fan scrims ([`:3465`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3465), [`:3724`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3724)) |

## 5. The work items, as distinct from the rules

- Delete the marker reveal, the policy's highlighted-track escape and the pinned carve-out (§3).
- Delete R2's map-world close at its four sites, and amend the epic's rule sentence **there** — which is
  [`FEAT_DSC_Ui_General.md:119`](FEAT_DSC_Ui_General.md:119) — and not a reveal sentence the epic does not
  have ([`:118`](FEAT_DSC_Ui_General.md:118)).
- Make a one-item card hold through its item's own departure (§3).
- Read the chevron's first item from the menu, and give its opener the world and source it lacks (§4).
- Open a map tap on the item it clicked, not on the map world (§4).
- Delete the dead `WHERE_AM_I` machinery at every site listed (§4).

## 6. Verification

- A pure test over the three kinds, including the proximity list carrying no filter of its own and the
  ends of a list walk reading alike.
- Tests that assert what this deletes, named so they change deliberately:
  [`MapSelectionPolicyTest.kt:103`](../../app/src/test/java/ykws/android/maro/data/model/MapSelectionPolicyTest.kt:103)
  (the highlighted override), [`DashboardScopeClosedTest.kt:29`](../../app/src/test/java/ykws/android/maro/ui/map/DashboardScopeClosedTest.kt:29)
  and [`CardWalkDecisionsTest.kt:121`](../../app/src/test/java/ykws/android/maro/ui/map/CardWalkDecisionsTest.kt:121)
  (the dead arm). **The marker reveal has no pure home, and cannot have a composable test** — the
  deletion lives in `MapScreen`'s Compose body, and this module carries no Compose UI-test dependency —
  so what guarantees it is the policy pin alone (the drawn set is exactly the filter's), never a test of
  the deleted branch; the pinned carve-out's test, which does have a pure home, is written. The first
  draft's "checked against both shipped bypasses" was wrong.
- One device pass per kind, and one per deleted exception: a list card, a spy pick across a marker and a
  track, a map click, and a pinned track hidden by the filter.

## 7. The record

- Amend the epic's R2 clause ([`FEAT_DSC_Ui_General.md:119`](FEAT_DSC_Ui_General.md:119)) and its earlier
  restatement ([`:102`](FEAT_DSC_Ui_General.md:102)); amend [`dashboard-close-conditions.md:86`](260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md:86),
  [`filters-link-decoupling.md:63`](260909_FEAT_PLN_Ui_General_filters-link-decoupling.md:63) and its
  [`:110`](260909_FEAT_PLN_Ui_General_filters-link-decoupling.md:110) and [`:168`](260909_FEAT_PLN_Ui_General_filters-link-decoupling.md:168),
  [`render-focus-vs-map-filter.md:180`](../../xTrack/TracksImport/260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md:180)
  with its own pinned non-goal at [`:39`](../../xTrack/TracksImport/260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md:39)
  and [`:164`](../../xTrack/TracksImport/260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md:164),
  [`FEAT_DSC_TracksImport.md:33`](../../xTrack/TracksImport/FEAT_DSC_TracksImport.md:33), and the two
  Tracks plans that carry the pinned carve-out —
  [`260911_FEAT_PLN_Tracks_live-track-paint-regression.md:126`](../../xTrack/Tracks/260911_FEAT_PLN_Tracks_live-track-paint-regression.md:126)
  and [`260921_FEAT_PLN_Tracks_position-count-sentinel.md:113`](../../xTrack/Tracks/260921_FEAT_PLN_Tracks_position-count-sentinel.md:113).
- `260928_FEAT_PLN_Ui_General_edit-return-and-advance.md` is not superseded: its return, its advance and
  its not-found close stand, and this pass changes what a walk is, not what a departure does.

## Outcome

Implemented on `feature/misc-ui-n-routing` on 2026-09-28, in this plan's own order.

- **The drawing (§3)** — three escapes deleted: the marker reveal-on-select in `MapScreen` (and the `revealedExtra` it fed), `TrackSelectionPolicy`'s highlighted eligibility OR and its now-dead cap rescue (the ranking that puts the highlighted first kept), and the pinned carve-out's repeated OR in `storedTrackSelection` — whose `highlightedTrackId` parameter then went with it, so no value describes a filter escape that no longer exists. The five deliberate draws are untouched. The reveal had no pure home, so the deletion is pinned at the policy level (the drawn set is exactly the filter's) rather than by a test that never could have existed.
- **R2's split (§3)** — `scopeClosed`'s `MAP` arm is `false`; the `LIST` arm keeps its close and the `INSPECT` arm is untouched, so a filter write leaves a map tap standing while the spy card still closes. The four call sites keep their flags; the outcome changed through the one predicate, and their comments were amended to say so.
- **A one-item card holds (§3)** — `cardWalkWorld` now takes the map's unfiltered source as its fourth collection (the deleted `matchWorld` slot), read by `DrawerSource.MAP` while `INSPECT` keeps the map-filtered world, so a map-opened card resolves and stands through a write that leaves the map filter and closes only on a genuine deletion. One home for the choice, shared by the drawer's render and the state layer's reconcile.
- **The chevron (§4)** — `firstTrackId`/`firstMarkerId` now read the menu's own referential (the map filter) rather than the list world, and `onOpenFirstMarker` hands `openMarkerDetail` the map world on the menu's own source (`DrawerSource.MENU`). **The menu turned out to carry a filter and a count but no order of its own**, so the map-filtered collection's own order stands in for it. The track chevron now hands its card that same set: the track drawer's world took a third source through the new `TrackCardSource` (the card carries the handed list in `walkWorld`, steps it with the list arithmetic, and closes it through the pure `trackScopeClosed`), so the gap the row assumed away is closed — the menu's own set reaches the track card at last (2026-09-28).
- **The map tap (§4)** — a plain tap opens `openEditDrawer(listOf(sel))` on the `MAP` source: one item, no arrows.
- **The dead where-am-i machinery (§4)** — `DrawerSource.WHERE_AM_I` deleted from the enum with its `scopeClosed`, `walkWorldName` and `cardWalkWorld` arms; the two `openEditDrawer` defaults and the `drawerSource` field moved to `LIST`; the wrap branches and `isClampedSource()` collapsed to a plain clamp; `matchWorld()` deleted; and the navigate flow's dead `whereAmISync` computation — whose `matchedIds` was written and never read — deleted. The match panel stays, walking nothing.
- **Verification (§6)** — a pure test over the three kinds (`cardWalkWorld` + `scopeClosed`: list closes on the list, map stands, inspect closes on the map); the three named tests changed deliberately; the pinned carve-out's new test in `TrackRouteRoleTest`; and the two tests written that never existed — the pinned carve-out's, and (for the reveal, which has no pure home) the drawn-set-is-the-filter's pin. `gradlew :app:assembleDebug` SUCCESS; the scoped `Marker`/`Track`/`Dashboard`/`Inspect`/`CardWalk`/`ScopeClosed`/`SelectionPolicy`/`AdvanceAfterDeparture` run green at **260 tests**, the whole unit suite green at **767 tests, 0 failures**.
- **The record (§7)** — the epic's R2 clause and its `filter everywhere` restatement amended, a new `## Implemented` entry, and the contrary documents amended in place: `dashboard-close-conditions` (its table row and a second amendment note), `filters-link-decoupling` (the reveal clauses), `render-focus-vs-map-filter` (its eligibility line, its pinned non-goal and the "highlighted track keeps both" decision), `FEAT_DSC_TracksImport`, and the live-track paint plan's reveal line.
- **What the code proved the plan wrong about** — (1) the "cap escape goes dead" is only true for `cap ≥ 1`: at `cap == 0` the rescue still fired, so the deletion also drops the cap-0 rescue, which the drawing rule now governs. (2) The plan's "one-item card holds" needs the unfiltered source at *both* readers, not just the reconciler — the drawer's own render would have drawn an empty card — so the fix is in the shared `cardWalkWorld`, not the reconcile alone. (3) The *armed tap* was `MAP`-sourced, so with the `MAP` arm now `false` it stood on a filter change where §4 reads it as the spy card. §4 names "the spy toggle pick, **and an armed tap**" in the spy kind, so the case was **silent, not uncovered**: the pick's `INSPECT` source was written and the tap's was not. Corrected 2026-09-28 — a tap that finds no frozen ladder asks the mode for one, so the card opens on the proximity ladder on the spy source and closes on a map-filter change like any spy pick. (4) The epic's ":102" restatement is the `filter everywhere` one-liner, not an R2 sentence; the epic's other R2 statement is the `dashboard-close-conditions` entry, amended too.
- **The review fold (2026-09-28)** — the `#implement` review's blockers and should-fixes, folded: the menu door named (`DrawerSource.MENU`, whose close follows the world it was opened from — `scopeClosed(MENU) = inMapWorld`, `cardWalkWorld(MENU)` the map-filtered set); the track card given that third source (`TrackCardSource` + `TrackDrawerState.walkWorld`, closed by the pure `trackScopeClosed`); the armed tap made the spy kind through its own pick (`MapInspectEffects`' `tapPickId`, run through the same bounded ladder pass as a dwell pick); the two stale comments corrected (the policy's "focus override", the overlay's "reveal"); the reveal's test limit stated in §6; and the `position-count-sentinel` plan's pinned carve-out non-goal amended, this section's "two Tracks plans" pointer naming both.
- **The ends read alike, both doors (§2, 2026-09-28)** — the card's Prev/Next greying keyed on `DrawerSource.LIST` alone, so the menu chevron's card (`DrawerSource.MENU`) drew both pills live at its ends and a press on the last end did nothing, where the panel's greys. The decision is now the pure `cardStepEnds` ([`CardWalkPolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt)): every door of the item's-list kind — the panel's and the menu's alike — greys the index the card lands on, while the inspect ladder keeps its own ends, and an inspect card with no cursor now reads at both ends — the reading its own plan asks for, both pills at their end rather than enabled and dead — while a one-item walk draws no bars. §2's "its ends read alike" is therefore true of both doors at last, and `CardWalkDecisionsTest` pins the four. The track half carries no equivalent condition: its ends are the index into `trackListIds` ([`MapScreen.kt:2878`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2878)), which already is the handed walk world — the menu's own set for a menu-opened card — so the track side never had the gap.
