# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 02:17 UTC — written by `#bake`; absence means never baked

**Directive trace:** One session on `feature/route-dash-n-flow` (`#new`, `#focus route`, then ordered edits on the user's word): the functional flow (early select, early save), the acquisition panel rework onto the shared `DrawerScaffold`, and the UI guideline docs trimmed to their current state. Of the five covered action classes none ran unasked — no dependency was added, no machine-shaped data file was opened, no file was written without the user's word, and the device was never touched; every claim about the code followed its own read.

## State

**A route is selectable and saveable while its line is still drawing.** `Select route` opens once the main lookup has a drawable partial line; the press commits to the main — the candidate lookups are cancelled, the main keeps running, and the mode enters `Following` when the line lands — while a refusal un-commits. `Save to track` opens on the same line, writes it immediately, and re-saves the same track id at each main iteration until the landed full line is written once more and the session links it, shutting the save door. One `Mutex` serializes the draft writes so a stale partial line can never be written after the full one. Requirements **R88–R90** live in `FEAT_DSC_Route.md`'s `## Requirements`.

**The acquisition panel is the shared dashboard scaffold.** It rides `DrawerScaffold` (`showBack = false`, `wrapContent = !isLandscape`, `wrapContentMinHeight = portraitDashboardHeight`) and auto-grows to its content. The body is a bordered three-column table — the description (0.75 of the comparison column), the route's Dist · ETA as value · unit pairs, and a candidate's delta with the forced-crossing note — with hairline column separators, wrapping top-aligned rows, the selected row on the taken-choice face whose corners adapt to the row's position, and the whole table paging laterally by swipe or the header's ‹ › pair. The three actions — `Save` · `Select` · `Discard` — share one weighted bottom row.

## Verification

`:app:testDebugUnitTest` BUILD SUCCESSFUL on every pass; `RouteEngineSeamTest` and `PartialPlanOfTest` cover the new paths. Nothing device-validated.

## Target Files

- [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt) — `Choosing.committed`, `commitToMain`, the auto-follow on the main's terminal update
- [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt) — `partialPlanOf`
- [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — the draft save, its mutex, the panel call sites
- [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt) — the DrawerScaffold panel and the paging table
- [`DrawerScaffold.kt`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt) — `showBack`
- [`strings.xml`](../../app/src/main/res/values/strings.xml) + [`values-fr`](../../app/src/main/res/values-fr/strings.xml) — the shortened labels and the table's unit/value strings
- tests: [`RouteEngineSeamTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/RouteEngineSeamTest.kt), [`PartialPlanOfTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/PartialPlanOfTest.kt)

## Next Step

The device pass over the paging table, the early select/save and the committed status. The `GLOBAL_CONTEXT.md` Route summary row and the Focus History prune stay blocked by the 2 000-character line cap — the standing record item.
