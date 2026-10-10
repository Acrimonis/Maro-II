# Context Hydration — Ui_General — 2026-10-10

**Last Bake:** 2026-10-10 08:25 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (the reported regression, the delete normalization's plan and its implementation, then this bake), the device was never touched (the builds and suites are named below), and every claim about the code rests on a file read. Named rather than hidden: the pipeline's Ask hop on the delete pass was **declined by the user**, so that pass carries no independent review, and one claim of the agent's own was refuted by the code earlier in the session and retracted in the plan.

## State

Two passes shipped on `feature/rte-n-trck-list`.

**The tracks/routes split's regression on the dashboard walk.** The card's walked list, its delete-advance and the R2 close now all read the **card's own kind** — the two lists being kind-locked, the kind an item carries is the list it came from. `CardWalkPolicy` gained the pure `cardWalkListIds(...)` beside a kind-aware `trackScopeClosed(source, cardIsRoute, listWorld: ListScope?, mapWorld: ListScope?)`; `MapScreen` collects `routeSummaries`, routes the walk world and the delete-advance through the helper, deletes the tracks-only `trackListIdsOf`, and carries the kind as `ListScope?` tokens through `CloseDashboards` and its fifteen call sites. A track or route map write no longer names the marker world — a behaviour change on the marker side, named in the plan rather than carried silently.

**The delete normalization.** The delete family now has **one pending set** and **one hiding rule**, with its surfaces kept: the map's toast for the cards, the scaffold's inline snackbar for the list rows. `ListOverlayScaffold` takes `sharedPending: MutableList<PendingDeletion>` plus a `pendingKeyPrefix`, so the shell's set and the lists' are one instance, and `PendingDeletion(key, hideFromMap)` carries the one difference the doors have. `hiddenMapIdsOf(pending, prefix)` is the hiding rule's single home: only an entry a **card** entered leaves the map, so the stored-tracks pass's `visibleStoredSummaries(allTrackSummaries, hiddenTrackIds)` — with `hiddenTrackIds` in its rebuild keys — and the marker overlay's filter each hide a deferred delete and **neither hides a list swipe**, which is the user's own correction. Every dismissal trigger and every Undo is behaviourally verbatim; the multiselect confirm keeps its immediate commit and the route discard its R92 two-phase inside the route mode. `MarkersViewModel.pendingDeletes` is left alone as the post-create undo's own concept — the plan's first draft claimed it was dead, the code refuted that, and the retraction stands in the plan's own record.

Both passes: `apk-build.bat` SUCCESSFUL with `app-debug.apk` produced, and the scoped `ui.map` (+ `ui.components`) suites green, the walk pass at 41 suites / 416 tests / 0 failures and the delete pass with `PendingDeletionHidingTest`'s three cases.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt` — `cardWalkListIds`, the kind-aware `trackScopeClosed`, `hiddenMapIdsOf`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeSummaries`, the walk world and delete-advance through the helper, the kind through `CloseDashboards`, the typed pending set with its two card doors and the two hidden-id sets
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — `visibleStoredSummaries` and `hiddenTrackIds` in the rebuild keys
- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — `PendingDeletion`, `sharedPending`, `pendingKeyPrefix`; now `internal`, as `MarkerManagementOverlay` also is
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` · `TrackHistoryOverlay.kt` · `MarkerManagementOverlay.kt` — the shared set and prefix to both list surfaces
- `app/src/test/java/ykws/android/maro/ui/map/` — `PendingDeletionHidingTest.kt` (new), `CardWalkDecisionsTest.kt`, `DashboardScopeClosedTest.kt`
- `xTrack/Ui_General/261010_FEAT_PLN_Ui_General_route-card-walk-scope.md` · `261010_FEAT_PLN_Ui_General_delete-normalization.md` — the two plans of record

## Next Step

The device pass, owed to the user: a route opened from the Routes list with its counter and both pills stepping routes; one list's filter write leaving the other list's card standing; a card's delete hiding its item at once and returning on Undo; a list swipe leaving the map untouched; the multiselect confirm still deleting at once; and the route discard unchanged.
