# Context Hydration — Markers — 2026-09-28

**Last Bake:** 2026-09-28 19:45 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met and none stopped this session — no dependency was added, no machine-shaped data file was opened, the branch was cut only on the user's `#new` and every implementation ran on an explicit order, the device was touched once for a read-only `adb logcat -d` fetched after the user said the navigation was theirs, and every claim written about the code carries a file read behind it. The gaps that stay open are named, not smoothed: the two superseded `route-role-flags` paragraphs in this epic's `## Implemented`, and the two `## Feature Summaries` rows no bake can patch whole.

## State

One session, 2026-09-28, opened on this feature by `#focus marker` on a branch cut from `origin/develop` (`feature/misc-ui-n-routing`), its marker pass committed as `840841c`, the work then spilling into Ui_General.

**The filter validated.** The pinned axis was reported inert on the map. The reads of the predicate, of the two referentials and of the wiring found no defect, and the device log settled it: the map filter was correct — 12 of 42 markers, every one pinned — so the two leaks lay elsewhere, in the map's reveal-on-select force-drawing the open card's marker and in the walk following the world of the door it was opened from. Both were removed by the Ui_General pass; the report and the evidence live in the plan of record.

**The route axis.** The marker filters' routing criteria are **two independent axes**, because a single-select panel can never hold a cost and a role at once: `routeCost` — ALL · WITH_COST · WITHOUT_COST — restored as it shipped, and beside it the new `routeRole` — ALL · ROLE · NONE; `UserMarker.matchesFilter` reads both, the cost through the shared `validRoutingCost` guard, and the two AND together, so a marker carrying a cost and no role answers both axes at once. The single-axis `Route` form built earlier the same session, and the retired-key prune that came with it, were **withdrawn before leaving the branch**. The ROLE option ships as **`Origin or Dest`** / **`Origine ou destination`**.

**The header's marks.** The marker card header's two route-role marks are the true colour emoji 🚩 while `routeOrigin` and 🏁 while `routeDestination`, set apart from the geometry line by one ` | ` separator emitted before the first mark alone; the settled order is start · 💲 cost · end.

**Dead code swept** under the same order: `MarkersViewModel.allMarkerIds` with its ghost-pin KDoc, `MarkerOverlay`'s `markerLayerState` and `modifier` parameters with their call-site argument and now-unused imports, and the `drawGeometry` local pinned `true`.

**Build.** `gradlew :app:assembleDebug` SUCCESSFUL, and the marker, routing-cost, dashboard and card-walk suites green with the full unit suite green after the Ui_General passes.

## Target Files

- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt` — the `routeCost` and `routeRole` axes and their `matchesFilter` arms
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — `routeOrigin` / `routeDestination`, `ROUTING_COST_MAX`, `ROUTING_COST_BLOCKED`, `validRoutingCost`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — `coordinateHeader()`'s marks and its separator
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — the marker pass, its `MaroMapRefresh` lines, the two parameters removed
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `allMarkerIds` deleted
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the two axes' keys, both locales
- `app/src/test/java/ykws/android/maro/data/model/MarkerFilterMigrationTest.kt` · `MarkerRoutingCostHeaderTest.kt` — the axes and the marks
- `xTrack/Markers/260928_FEAT_PLN_Markers_filter-validation-and-route-axis.md` — the filter report, the pin evidence, the log pass
- `xTrack/Markers/260928_FEAT_PLN_Markers_route-role-flags.md` — the two roles, the cost wall and the two-column step

## Next Step

The epic's open walk of 2026-09-18 (**Active 11**) still owns this feature's bookkeeping and is the section to read; its item 11 — a bake across Markers, UI_Map and Ui_Settings — is not discharged by this bake, which covers Markers alone, and its item 12 duplicates the epic's own `## Todos` line on the proximity of date points. Still owed by the user: the device pass over the marker header's mark line (its line box and the coordinate's ellipsis budget) and the pinned report's logcat on a build carrying its log lines. Owed to `#archive`, on the user's word: the moved plan's tombstone at `xTrack/Markers/260928_FEAT_PLN_Markers_edit-return-and-selection-fallback.md`. Owed to a `findstr`-based patch, since both lines are longer than a tool can read back whole: the two superseded `route-role-flags` paragraphs in `## Implemented`, and the Markers row's stale Modified date in `GLOBAL_CONTEXT.md`.
