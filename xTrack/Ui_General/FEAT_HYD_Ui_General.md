# Context Hydration — Ui_General — 2026-10-10

**Last Bake:** 2026-10-10 08:05 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (the reported regression, the approved fix, then this bake), the device was never touched (the build and its suites are named below), and every claim about the code rests on a file read. Named rather than hidden: the fan-master work baked beside this one is UI_Map's, and its only footprint here is the plan's marker-side note.

## State

The tracks/routes split's regression on the dashboard walk was found and fixed, on `feature/rte-n-trck-list`. The card's walked list, its delete-advance and the R2 close now all read the **card's own kind** — the two lists being kind-locked, the kind the item carries is the list it came from.

`CardWalkPolicy` gained the pure `cardWalkListIds(walkWorld, cardIsRoute, trackSummaries, routeSummaries, pendingDeleteIds)` beside a kind-aware `trackScopeClosed(source, cardIsRoute, listWorld: ListScope?, mapWorld: ListScope?)`. `MapScreen` collects `routeSummaries`, routes the card's walk world and its delete-advance through the helper, deletes the tracks-only `trackListIdsOf`, and carries the kind as `ListScope?` tokens through `CloseDashboards` and its fifteen call sites — so either list's write no longer closes the other list's card, and a track or route map write no longer names the marker world. That last part is a behaviour change on the marker side, taken deliberately and named in the plan rather than carried silently. The derived reads — `currentTrackIndex`, the pill ends and the two Prev/Next bodies — followed the corrected world with no edit of their own, so the fix stayed one cause.

`apk-build.bat` BUILD SUCCESSFUL with `app-debug.apk` produced, and `:app:testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` green at 41 suites / 416 tests / 0 failures, `CardWalkDecisionsTest` and `DashboardScopeClosedTest` among them.

**Approved, in design, not started — the delete normalization.** One pending set in the shell (hoisted out of `ListOverlayScaffold`, with `MarkersViewModel`'s never-filled twin retired), one hiding rule reaching the map while each surface keeps its own snackbar — the map's toast for the cards, the inline snackbar for the list rows — and every dismissal trigger and Undo preserved verbatim. Its inventory and rule are settled; its plan file is not yet written.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt` — `cardWalkListIds` and the kind-aware `trackScopeClosed`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeSummaries` collected, the walk world and the delete-advance through the helper, `trackListIdsOf` deleted, the kind through `CloseDashboards` and its fifteen call sites
- `app/src/test/java/ykws/android/maro/ui/map/CardWalkDecisionsTest.kt` · `DashboardScopeClosedTest.kt` — the walk world per kind and the close's per-kind cases
- `xTrack/Ui_General/261010_FEAT_PLN_Ui_General_route-card-walk-scope.md` — the plan of record, including the marker-side behaviour change it names

## Next Step

The device pass, owed to the user: a route opened from the Routes list with its counter and both pills stepping routes, a recorded track the same the other way, a delete from either list advancing inside its own kind, and one list's filter write leaving the other list's card standing. The approved delete normalization stands next, awaiting its plan file.
