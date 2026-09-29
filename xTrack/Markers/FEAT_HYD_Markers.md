# Context Hydration — Markers — 2026-09-28

**Last Bake:** 2026-09-28 20:35 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met and none stopped this session — no dependency was added, no machine-shaped data file was opened, the branch was cut only on the user's `#new` and every implementation ran on an explicit order, the device was touched once for a read-only `adb logcat -d` fetched after the user said the navigation was theirs, and every claim written about the code carries a file read behind it. The archive pass and the log sweep were each ordered in the same session. The gap that stays open is named: the Markers and Ui_General rows of `## Feature Summaries` still carry their old dates.

## State

One session, 2026-09-28, opened on this feature by `#focus marker` on a branch cut from `origin/develop` (`feature/misc-ui-n-routing`), its marker pass committed as `840841c`, and the branch closed on `8ac9086` and `5a0ec28` after the work spilled into Ui_General.

**The filter validated.** The pinned axis was reported inert on the map. The reads of the predicate, of the two referentials and of the wiring found no defect, and the device log settled it: the map filter was correct — 12 of 42 markers, every one pinned — so the two leaks lay elsewhere, in the map's reveal-on-select force-drawing the open card's marker and in the walk following the world of the door it was opened from. Both were removed by the Ui_General pass; the report and the evidence live in the plan of record.

**The route axis.** The marker filters' routing criteria are **two independent axes**, because a single-select panel can never hold a cost and a role at once: `routeCost` — ALL · WITH_COST · WITHOUT_COST — restored as it shipped, and beside it the new `routeRole` — ALL · ROLE · NONE; `UserMarker.matchesFilter` reads both, the cost through the shared `validRoutingCost` guard, and the two AND together. The single-axis `Route` form built earlier the same session, and the retired-key prune that came with it, were **withdrawn before leaving the branch**. The ROLE option ships as **`Origin or Dest`** / **`Origine ou destination`**.

**The header's marks.** The marker card header's two route-role marks are the true colour emoji 🚩 while `routeOrigin` and 🏁 while `routeDestination`, set apart from the geometry line by one ` | ` separator emitted before the first mark alone; the settled order is start · 💲 cost · end.

**Dead code swept** under the same order: `MarkersViewModel.allMarkerIds` with its ghost-pin KDoc, `MarkerOverlay`'s `markerLayerState` and `modifier` parameters with their call-site argument, and the `drawGeometry` local pinned `true`.

**The diagnostics gone.** The `MaroFilter` lines added for the pinned investigation — four `filterWrite` lines and `open source=MAP` in `MapScreen`, the `publish` pair with `open` and `step` in `MarkersViewModel`, and the added `onMarkerMapFilterChange` line — were removed together with the three helpers they orphaned (`filterAxes`, `walkWorldName`, `logStep`); the pre-existing `MaroMapRefresh` lines stay. No `reveal` line ever existed. `gradlew :app:assembleDebug` SUCCESSFUL and the scoped suites green at 16 classes and 157 tests.

**Three plans archived.** `#archive` created this feature's first `xTrack/Markers/xxArchive/` and filed the moved plan's tombstone there as `superseded`, its `Superseded-by` naming the Ui_General plan; the two shipped Ui_General plans went to their own archive in the same pass, and every live pointer to the three was dropped or repointed.

## Target Files

- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt` — the `routeCost` and `routeRole` axes and their `matchesFilter` arms
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — `routeOrigin` / `routeDestination`, `ROUTING_COST_MAX`, `ROUTING_COST_BLOCKED`, `validRoutingCost`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — `coordinateHeader()`'s marks and its separator
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — the log lines and the three helpers removed
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the `MaroFilter` lines removed
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the two axes' keys, both locales
- `app/src/test/java/ykws/android/maro/data/model/MarkerFilterMigrationTest.kt` · `MarkerRoutingCostHeaderTest.kt` — the axes and the marks
- `xTrack/Markers/xxArchive/INDEX.md` — the new index and its single `superseded` row
- `xTrack/Markers/260928_FEAT_PLN_Markers_filter-validation-and-route-axis.md` — the filter report and the pin evidence
- `xTrack/Markers/260928_FEAT_PLN_Markers_route-role-flags.md` — the two roles, the cost wall and the two-column step

## Next Step

The walk of 2026-09-18 is **closed** as of 2026-09-28: its item 11 was the three-feature bake, and its last point, the date-point coverage, shipped the same night — a pin's segment is now recorded before its range gate answers, so every date point is tested and drawn while the 300 m rule keeps deciding the match. What the ruling raised, and what the walk closed with rather than dropped, is carried in `## Todos`: the overlay gives a tested-but-rejected date point no marking of its own. The marker header's mark line still carries its own open record entry.
