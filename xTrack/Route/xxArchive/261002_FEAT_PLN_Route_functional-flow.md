# Route functional flow — early select, early save, and the acquisition panel's layout

## 1. What was asked — 2026-10-02

- A route line must be selectable **early in its drawing** — the user must not wait for the full search to land.
- The acquisition panel's layout buries the relevant info and forces a **scroll to change page**; propose improvements.
- `plan report and discuss` — this file is the report; built on 2026-10-02 (§10).

## 2. Decisions — the user's word, 2026-10-02

- **Early select does not freeze the partial line.** The press commits to the selected track, lets it **continue until finalization**, and **drops the other tracks' computations**.
- **Save to track is allowed before finalization.** The partial line is written immediately, and the saved track is **updated at each iteration** as the line grows.
- **Auto-pick will take the best answer** — **parked**, returned to later; untouched in this pass.
- **Panel layout: A + drop the coordinates + add swipe pagination.**

## 3. Early select — commit and finalize

### 3.1 Today

- [`Select route`](app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:163) is enabled only when the selected page has a landed plan; the partial line the map already draws cannot be acted on.

### 3.2 The change

- The button enables when the selected page has a plan **or** the main lookup has a drawable partial line — [`provisionalLine.size >= 2`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:272).
- [`selectRoute()`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:444) branches:
  - **Plan landed** — the existing path: dispose all lookups, enter `Following`.
  - **Early** — cancel the **candidate** lookups only (every lookup id except the main's), set a new `Choosing.committed = true`, and **keep the main running**.
- [`onUpdate()`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:388): when the main's terminal result lands while `committed`, build the final plan, clear the provisional line and enter `Following(finalPlan)` automatically.
- The committed phase's panel: the candidate block is gone, the status reads **"selected — finishing"** (a new string), `Select route` is disabled because it already happened, and `Discard route` still ends the mode — [`end()`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:477) cancels whatever is left, the main included.
- `saveRouteTrack` and the acquisition's `frontSaved` gate need no change for this section.

## 4. Early save — the growing track

### 4.1 The change

- `Save to track` enables when the selected page has a plan **or** the selected page is the main and `provisionalLine.size >= 2`.
- On an early press, a **partial plan** is built from the provisional points:
  - `start` = the acquisition's `Choosing.start`, `destination` = the last drawn point, `points` = the provisional points.
  - `legTimesSec` per segment = `haversine(segment) / plannedPace`, `distanceM` = the segments' sum, `durationSec` = the leg-times' sum.
  - `computedAtMs` = the **early-save instant** — fixed for the draft's whole life, so its name and id stay one identity.
  - `destinationMoved = false`, `forcedCrossingZoneNames = emptyList()`.
- The partial plan is written through the existing [`saveRouteTrack`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1975) path; the written id is remembered as the **draft id**.
- Each subsequent main `update.line` re-saves the **same draft id** with the grown points — the repo already overwrites by id, [`saveOrReplace`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:79) being [`save`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:64). Each re-save builds its track with the draft's fixed name and `createdAtMs`; only the points and legs grow.
- On the main's terminal result: one last overwrite with the full line under the draft id, and the session registers the **landed plan → draft id**, so [`isRouteSaved`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:508) greys the save door for the finished plan.
- The partial-plan builder is a **pure helper** (in [`RouteOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt)), beside `routePlanOf` / `mirroredPlanOf`, so it is unit-tested without a ViewModel.
- If the user never saves early, nothing changes.

## 5. The panel's layout

### 5.1 Today

- The candidate block sits **last** in the scrollable column ([`PanelColumn`](app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:187)), under the 2×2 Start/Destination/Dist/ETA table, the notes and the pin option — so changing page needs a scroll.
- The coordinates repeat what the drawer's Route sub-section already shows.

### 5.2 The new anatomy

- Header (title + status word) — unchanged.
- A **horizontal swipe pager** directly under the header, one page per `RoutePage` (main = page 0): each page shows the route's identity and its **Dist · ETA**, or `Acquiring…` while that page has no plan; pager dots show position and the existing ‹ › pair stays as a tap alternative.
- The comparison ("Route #n is x less/more") moves into the candidate page's own content.
- The refusal sentence stays, when present.
- Notes and the pin option stay **below the pager**, only while the selected page has a plan.
- The actions row stays pinned below the scroll.
- **Start/Destination coordinates are dropped.**

### 5.3 Parameters

- [`RouteConfirmationPanel`](app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:78) gains what the new enablement needs: the main's partial-line availability (or the provisional line itself) and the `committed` flag, so the two buttons and the status word read the right state.

## 6. Strings

- One new status word — `route_status_selected` ("Selected — finishing the line" / its French form) — through `res/values/strings.xml` and `res/values-fr/strings.xml` by `@StringRes` id.
- The pager reuses the existing `cd_route_candidate_prev` / `cd_route_candidate_next` descriptions; any further label follows the same two-locale rule.

## 7. Tests

- ViewModel: an early select cancels the candidates and **not** the main, sets `committed`, and auto-enters `Following` on the main's terminal update; a discard during commit ends everything.
- ViewModel: an early save writes the draft once, re-saves the same id on iteration, and links the landed plan so the save door shuts.
- Pure: the partial-plan builder's points, synthesized leg times and fixed instant.
- Panel: the two buttons' enabled states and the committed status word.

## 8. Out of scope

- Auto-pick — parked (§2).
- The `Following` phase's ordinary dashboard, the exit dialog, the drawer's Route sub-section, the stored-route match, and the save/select flow of already-landed plans.

## 9. Review corrections — 2026-10-02

- The draft's "same id" needs an explicit stable `id`, not the builder's fresh UUID: each re-save passes the draft's fixed `id`, `createdAtMs` and `name` into [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:69), so the one file is overwritten rather than a new one written per iteration.
- The final overwrite writes the **landed full line** under the draft id — the provisional line clears on the terminal update, so the last partial line is not the final snapped line — then links the landed plan to shut the save door.
- The commit clears the candidate pages so `searching` cannot stay true on cancelled lookups.

## 10. Outcome — 2026-10-02

- Built as this file states: `Choosing.committed` + `commitToMain` + the auto-follow on the main's terminal update in [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt); the pure [`partialPlanOf`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt); the draft save and its two effects in [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt); the swipe pager and the dropped coordinates in [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt); the `route_status_selected` string in both locales; tests in [`RouteEngineSeamTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/RouteEngineSeamTest.kt) and [`PartialPlanOfTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/PartialPlanOfTest.kt). `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.
- Requirements **R88–R90** live in [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md)'s `## Requirements`.
- The review's draft-write race is closed: one `Mutex` in [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) serializes the per-iteration re-save and the final overwrite, so a stale partial line can never be written after the full one.

## 11. Follow-up layout — 2026-10-03 (built)

- **Remove the pin-to-track checkbox** — [`RoutePinOption`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt) leaves the panel, and [`RouteConfirmationPanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt) drops its `pinned` and `onPinnedChange` parameters; the two call sites in [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) stop passing them. `routePinned` stays — it still feeds the save — so a saved route track is now always unpinned.
- **Paging controls to the top-right** — the ‹ › pair and the dots leave `RoutePager`'s bottom row and sit at the header's trailing edge, shown only while more than one page stands.
- **Stage status between title and paging** — the header row becomes title · status · paging, the status centred in the space between.
- **The three actions share one bottom row** — `Save to track · Select route · Discard route` sit in a single weighted row, each `weight(1f)`, instead of the Discard's full-width second row.
- The pager stays the swipe surface below the header; the refusal sentence and the notes stay.
- **Out of scope** — `RoutePinOption` and `route_pin_label` become dead and are left for the user's call rather than deleted unasked.
- **Built 2026-10-03** — the panel and both call sites carry all four moves; `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.

## 12. Condensed route summaries — 2026-10-03 (built)

**Assess.** The pager gives every route a whole page of big Dist · ETA, so the alternatives are not scannable at a glance.

**Decisions — the user's word, 2026-10-03.**

- Keep the pager; the table sits **below** it.
- The table is the scannable list: the pager page keeps only the selected route's big Dist · ETA, and the description + comparison move into the table.

**Proposal.**

- A condensed **two-column table** below the pager: one row per route, one line, no wrap.
- Column 1 — the description: the engine's own (`descriptionResId`), e.g. `Normal · Ignore Speed Zones · Ignore zone`.
- Column 2 — the comparison: the route's own `distance nm · duration`, and for a candidate the delta against the main (`x min more/less`).
- The selected row is **bold**; tapping a row selects it, and the header's ‹ › dots still step.
- A row without a landed plan reads `Acquiring…` in column 2.
- The table shows only while more than one page stands.
- **Built 2026-10-03** — the pager page is slimmed to Dist · ETA and the table sits below it; `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.

## 13. Panel polish — 2026-10-03 (built)

- **Tighter table** — the row vertical padding goes 4 → 2 dp and the panel's vertical padding 12 → 8 dp.
- **Forced crossing into the right column** — the note leaves `PlanNotes` and prints as a second line in the route's own right column, under the comparison; the destination-moved note stays below the table.
- **One pager for the whole page** — the pager and the table merge into one pager whose page carries the route's Dist · ETA **and** the table with that row selected, so a swipe or the ‹ › pair pages the whole page and the two never look out of step.
- **Shorter labels** — `route_action_save_track` → Save, `route_action_select` → Select, `route_exit_discard` → Discard, in both locales; the fan and the exit dialog share those keys and shorten with them.
- **Built 2026-10-03** — all four moves carry; `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.

## 14. Migrate the route panel to the shared scaffold — 2026-10-03 (built)

**Plan.**

- [`DrawerScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:149) and its [`DrawerHeader`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:68) gain `showBack: Boolean = true`, threaded through, so the route panel can omit the back button the other dashboards keep.
- [`RouteConfirmationPanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt) is rewritten onto `DrawerScaffold`: title `route_acq_title`; `headerActions` carry the stage status and the ‹ › dots; `wrapContent = !isLandscape`, `wrapContentMinHeight = portraitDashboardHeight`, `bottomAnchoredContent = true`, `statusBarsInset = isLandscape`; the three actions move to the `footer`; the refusal, the pager and the table move to the `content`.
- The panel gains `isLandscape` and `portraitDashboardHeight` parameters, like [`WizardDrawer`](../../app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:68).
- Both [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) call sites pass the two parameters and drop the fixed `.height(portraitDashboardHeight)`.
- `PanelColumn` and `PanelHeaderRow` retire; `PanelSentence` and `PanelDivider` adapt or go.

**Review.**

- The shared header always renders a back button, hence the new `showBack` flag; the route panel's `onClose` becomes a no-op.
- **Visible change**: the surface becomes the shared `uiBackground` with rounded top corners and a 48 dp header, leaving the dashboard's own background — the price of one scaffold, and the same look the wizard and the track drawer already have.
- The centred status becomes trailing in `headerActions`, so "status between title and paging" degrades to "status before paging, right-aligned".
- The map's bottom inset stays `portraitDashboardHeight` while the panel may grow taller — the same behaviour as the wizard and track drawers, which already cover more map as they grow.
- The content scrolls only when it exceeds the screen height; otherwise the panel auto-grows with no internal scroll — the autogrow the question implied.
- **Built 2026-10-03** — the panel rides `DrawerScaffold` with `showBack = false`, `wrapContent = !isLandscape` and `wrapContentMinHeight = portraitDashboardHeight`; `PanelColumn` and `PanelHeaderRow` are retired; `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.

## 15. Table rework — 2026-10-03 (built)

- **Drop the pager and the top Dist · ETA display** — `RoutePager` and `RoutePageBody` retire, so the table is the body's first block; the swipe goes with them and the header's ‹ › pair keeps stepping the selection.
- **Three columns** — left the description, middle the route's Dist and ETA as bare values one per line and left-aligned, right the comparison (a candidate's `x min more/less`) and the forced-crossing note.
- **Rows top-aligned, columns wrap** — no `maxLines`/ellipsis truncation; long descriptions and values wrap.
- **A slight border** around the table, the shared hairline colour.
- **Selected row** — white bold text on the app's taken-choice face: a `ui.select.container` fill with its 1 dp `ui.accent` edge, the `MultiSelectRow` on-face the user chose.
- **Built 2026-10-03** — the table is the body, three columns, bordered, wrapping and top-aligned; `RoutePager` and `RoutePageBody` are retired; `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.

## 16. Table look-and-feel — 2026-10-03 (built)

- The table **stays a table**; only its look borrows the [`MultiSelectRow`](../../app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt:50) base — one connected bar.
- Container: a 1 dp [`ui.divider`](../../app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt:59) hairline border with [`uiRadiusCard`](../../app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt:58) corners.
- Selected row: the taken-choice face — a `ui.select.container` fill, a 1 dp `ui.accent` edge, white bold text; unselected rows stay fill-free with normal text.
- Rows are vertical cells in that one bar, exactly as today's three-column table; no check glyph is added.
- Delta from the current tree: swap the hand-picked 6 dp radius for [`uiRadiusCard`](../../app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt:58) so the bar matches the shared component.
- **Built 2026-10-03** — the container and the selected-row edge read `uiRadiusCard`; `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.
- **Correction 2026-10-03** — the table now renders as a real table: all three columns carry fixed weights (1.2 · 1 · 1) and the comparison column is left-aligned, so the column boundaries stay consistent across every row.

## 17. Table grid and swipe — 2026-10-03 (built)

- The selected row's corners adapt to its position — top row rounded on top, middle rows square, bottom row rounded on the bottom, a lone row fully rounded.
- A full-row-height hairline separates the three columns, so the vertical grid is visible.
- The table pages laterally again: a `HorizontalPager` wraps the table, each page the same table with that page's row selected, and a swipe or the header's ‹ › pair pages the whole thing.
- `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.

## 18. Middle-column formatting — 2026-10-03 (built)

- The middle column shows Dist and ETA as **value · unit** pairs: the value right-aligned, the unit left-aligned, one pair per line, stacked vertically.
- The pairs split into unit and value resources — `NM` and `min` units, the `%.1f` and `M:SS` value formats — added to both locales.
- The middle column wraps to its content; while a route has no landed plan it shows a placeholder pair (`-- NM` · `-- min`) so the column keeps its width.
- The left description column becomes **0.75 of the right comparison column**; the middle column takes its own content width instead of a weight.
- **Built 2026-10-03** — the value · unit pairs, the placeholder and the 0.75 description column carry; `:app:testDebugUnitTest` BUILD SUCCESSFUL, nothing device-validated.
