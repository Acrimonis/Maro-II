# FEAT_PLN — Ui_Dashboard — Dashboard layout normalization

**Feature:** Ui_Dashboard · **Branch:** `feature/dash-map-layout` · **Date:** 2026-10-03 · **Status:** implemented 2026-10-03 — device pass pending, review issues to finalise (§9–§10)
**Scope:** two phases — normalise every bottom dashboard onto the existing auto-resizing frame with one base size, then make a dashboard that grows past its base shrink the map instead of covering it.

## 1. Report

The app's bottom dashboards are one family with two frames and one duplicated geometry. The Layer-0 grid ([`DashboardPanel`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:95)) is a bare `Box` pinned to a fixed height, while the wizard, the marker and track cards, Where-Am-I and the route panel all wear the shared [`DrawerScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:152), which already auto-resizes to its content and floors at the dashboard size. Phase 1 puts every dashboard on that one frame with one named base size; Phase 2 makes a dashboard that grows taller than that base shrink the map rather than overlay it.

This is a layout-management normalisation, not a new resize feature: no drag gesture, no snap points, no new component.

## 2. Requirements — settled 2026-10-03

| # | Requirement |
|---|---|
| R1 | Every **portrait** bottom dashboard renders through the existing auto-resizing `DrawerScaffold`; no portrait dashboard keeps a bespoke frame. Landscape's full-height left column is deliberately left as it is (§10 F1). |
| R2 | One named base size equals today's regular dashboard size and is the floor every dashboard uses; none renders shorter. |
| R3 | No new gesture: the resize stays content-driven wrap-content, exactly as it is today. |
| R4 | The Layer-0 2×2 grid migrates with its content and read behaviour unchanged; only its frame changes. |
| R5 | Phase 2 is portrait-only: a dashboard taller than base shrinks the map rather than covering it. |
| R6 | One geometry source: the base and the current height resolve once in `MapScreen` and feed `MapContent`, `OverlayLayer`, `MapLockLayer` and `MapSnackbarHost`. |
| R7 | The selected-item close rules — a surface wanting the slot, a change of the world a walk reads — must not regress. |
| R8 | A bottom dashboard's top corners are always square — the round-once-grown effect was withdrawn after the device look (2026-10-03, §14 W7). |

**Decisions taken:** landscape is not applicable to Phase 2 (its full-height left column is untouched); the base size is a named constant at today's value, not a configurable key; the map band animates rather than resizing the map per frame; the epic is homed in `Ui_Dashboard`.

## 3. Current state — evidence

| Site | Code | Today |
|---|---|---|
| Base geometry | [`MapScreen.kt:1559`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1559) · [`MapScreen.kt:1560`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1560) | `portraitDashboardHeight = maxWidth * 3 / 5`, `landscapeDashboardWidth = maxHeight`, computed inline and copied as plain `Dp` into four readers. |
| Map band | [`MapScreen.kt:2858`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2858) | `MapContent` padded `bottom = portraitDashboardHeight` (portrait) / `start = landscapeDashboardWidth` (landscape) — a constant band. |
| Offset math | [`MapScreen.kt:1566`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1566) | `visibleMapHeightDp = maxHeight - portraitDashboardHeight`, feeding the speed offset. |
| Layer-0 grid | [`MapScreen.kt:2943`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2943) | `DashboardPanel` bottom-anchored, `.fillMaxWidth().height(portraitDashboardHeight)` — its own frame. |
| Where-Am-I | [`OverlayLayer.kt:427`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:427) | Fixed `.height(portraitDashboardHeight)`; the other detail panels use wrap-content. |
| Shared frame | [`DrawerScaffold.kt:152`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:152) | `wrapContent` + `wrapContentMinHeight` + `bottomAnchoredContent`; measures header/body/footer at [`DrawerScaffold.kt:199`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:199). |
| Band mirrors | [`MapLockLayer.kt:59`](../../app/src/main/java/ykws/android/maro/ui/map/MapLockLayer.kt:59) · [`MapSnackbarHost.kt:41`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:41) | Both re-apply the same constant band. |

## 4. Phase 1 — one frame, one base size

1. **Name the base once.** In [`MapScreen.kt:1559`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1559) rename `portraitDashboardHeight` to `dashboardBaseHeight`, keeping the value `maxWidth * 3 / 5`. Rename the parameter everywhere it is threaded — [`OverlayLayer.kt:90`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:90), [`WizardDrawer.kt:75`](../../app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:75), [`RouteConfirmPanel.kt:99`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:99), [`MapLockLayer.kt:31`](../../app/src/main/java/ykws/android/maro/ui/map/MapLockLayer.kt:31), [`MapSnackbarHost.kt:30`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:30) — so the name states base, not the height of the slot that happens to occupy it. The two band readers are the exception: `MapLockLayer` and `MapSnackbarHost` take the live band and are named `dashboardBandHeight` (§5.5 supersedes this list for those two).
2. **Give the shared frame a header-less mode.** [`DrawerScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:152) always draws [`DrawerHeader`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:68) with a title and a back button. Add an optional header slot defaulting to today's `DrawerHeader`, with `onClose` required only when the header is drawn, so a title-less dashboard can wear the same frame with no change to existing callers.
3. **Migrate the grid into the frame.** Render [`DashboardPanel`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:95)'s 2×2 content inside the scaffold with `wrapContent = true`, `wrapContentMinHeight = dashboardBaseHeight`, no header and `scrollable = false`; drop the panel's own `.background()`/`.padding()` root, which the scaffold's `contentPadding` replaces — but keep the dashboard's own `ui.dashboard.background` through the scaffold's new background parameter (§10 F4).
4. **Keep the grid's bounded sizing.** The cells use `weight(1f)`/`fillMaxHeight()` and need a bounded height, so the grid keeps one equal to the base less the vertical `contentPadding` — and that height must derive from the same padding value the scaffold applies, so the two cannot drift (§10 F3). Only the *floor* is the scaffold's `wrapContentMinHeight`. This supersedes the earlier content-sized wording: the base grid never grows (§8 V3).
5. **Migrate Where-Am-I off its fixed height.** Replace [`OverlayLayer.kt:428`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:428)'s `.height(portraitDashboardHeight)` with the wrap-content path its sibling detail panels use.
6. **Unify the floor.** Every dashboard's `wrapContentMinHeight` reads the one `dashboardBaseHeight`, so no call site invents the floor.

## 5. Phase 2 — the map follows a growing dashboard

Portrait only; landscape keeps its current full-height left column.

1. **Surface the measured height.** Add an optional `onMeasuredHeight` callback to [`DrawerScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:152), which already measures header/body/footer, and forward the open bottom panel's height from [`OverlayLayer`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:89) up to `MapScreen`.
2. **Hold one band value.** In `MapScreen`, derive `dashboardBandHeight` = `dashboardBaseHeight` when no bottom dashboard is open, else the measured height coerced into `[dashboardBaseHeight, maxHeight - topChrome]`.
3. **Animate the band.** Wrap it in `animateDpAsState` so the map eases to the new size instead of the osmdroid AndroidView resizing on every measurement frame.
4. **Push the map.** Drive `MapContent`'s portrait padding `bottom` from the animated band at [`MapScreen.kt:2860`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2860), and read the same value in `visibleMapHeightDp` and the speed-offset math at [`MapScreen.kt:1566`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1566).
5. **Align the mirror overlays.** Feed the animated band to [`MapLockLayer.kt:59`](../../app/src/main/java/ykws/android/maro/ui/map/MapLockLayer.kt:59) and [`MapSnackbarHost.kt:41`](../../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:41) in place of the constant.
6. **The route panel's own contribution.** The route panel is composed by `MapScreen` rather than `OverlayLayer`, so its measured height reaches the band through its own reader (`routeBandHeight` in the code) — a second writer of §5's one band value, not a separate rule.

## 6. Files

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` | Optional header slot (Phase 1); optional `onMeasuredHeight` (Phase 2). |
| `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` | The 2×2 content adapted to render inside the shared frame. |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | `dashboardBaseHeight` named; the grid composed in the frame; the live band state and the animated map padding (Phase 2). |
| `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` | Where-Am-I onto wrap-content; the active bottom height surfaced (Phase 2). |
| `app/src/main/java/ykws/android/maro/ui/map/{WizardDrawer,RouteConfirmPanel,MapLockLayer,MapSnackbarHost}.kt` | Parameter rename to `dashboardBaseHeight`; band readers take the live value. |
| `xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md` | Section, rules and `## Docs` pointer on completion. |
| `docs/ui-drawer-guidelines.md` | The `Portrait Drawer Height Floor` section names the base size. |

## 7. Verification

- `apk-build.bat` packages; the scoped `ui.map` unit suite stays green.
- Portrait, each dashboard: renders at ≥ base, grows with content, never shorter than base.
- Portrait, a tall detail card open: the map shrinks to the card's height and returns to base when it closes.
- Portrait, Where-Am-I: measured at its content like its siblings, with the map following.
- Landscape: its frame unchanged; its inner inset tightened with W18 (§14), so the device pass should include the landscape column's spacing.
- The selected-item close rules (R1/R2) behave exactly as before.

## 8. Review

- **V1 — the header-less variant touches a shared component.** The objection: `DrawerScaffold` gains a branch for the one dashboard with no title, which is a shape it did not have. The alternative — extracting a lower-level frame the scaffold and the dashboard both consume — moves more code for the same single home; either way the frame is written once, and the optional slot keeps every existing caller untouched.
- **V2 — the band lags the panel by a measurement.** The measured height arrives after the panel lays out, so the band is one frame behind; the animation of §5.3 absorbs it rather than the map jumping.
- **V3 — the base dashboard does not actually grow.** Its 2×2 grid is fixed content, so Phase 2 in practice reacts to the detail cards that already auto-resize; the base dashboard's migration is a framing change that makes the floor real, not a growth case.
- **V4 — resizing the map is resizing an AndroidView.** The band animates the `MapContent` bounds, which resizes the osmdroid view; the animation bounds the number of resize frames, and landscape is untouched, but the device pass of §7 is what proves the drawing does not glitch.
- **V5 — one fact, one home.** The base size leaves the four hand-copied `Dp` values for one named value read through `MapScreen`; if a future pass wants it configurable, that is a properties key and a separate decision, deliberately not taken here.

## 9. Implementation review — Ask hop (2026-10-03)

Built on `feature/dash-map-layout`; `apk-build.bat` BUILD SUCCESSFUL, `app-debug.apk` produced, nothing committed. Portrait satisfies R1–R7 and no High finding was raised. The hop recorded:

- **Medium — R1 unmet in landscape:** `DashboardPanel` keeps a bespoke bare-`Box` full-height column for `isLandscape`, contradicting R1's "no dashboard keeps a bespoke frame" while the landscape decision leaves it untouched. Either R1 is scoped to portrait or the landscape column migrates in a later pass.
- **Medium — §4.4 contradicted:** the grid is pinned to `dashboardBaseHeight - 16.dp` instead of being content-sized, so the base panel can never grow (V3), and that fixed height double-homes the 8 + 8 vertical `contentPadding`.
- **Medium — R4 gap:** portrait paints the scaffold's `ui.background` with a rounded top while landscape still paints `ui.dashboard.background`; the keys are distinct, so a theme separating them splits the orientations.
- **Medium — the band can still be covered:** the panel body's ceiling is the full screen while the band is capped at `maxHeight - chromeTopInset`, so a dashboard taller than the cap covers the strip R5 protects.
- **Medium — recomposition cost:** `onSizeChanged` writes state read by the same `MapScreen` scope, so the whole file re-runs on each frame of the 250 ms tween; no feedback loop, only cost.
- **Low:** the route panel's own `routeBandHeight` is beyond §5; the remembered measurement survives rotation; the generic `onMeasuredHeight` and the track card's `MeasureHeight` probe report the same fact twice; the base parameter defaults to a collapsing `0.dp`; the landscape band is dead work; the duplicated grid call site; §4.1 and §5.5 disagree on the reader names; and `docs/ui-component-guidelines.md` still says `portraitDashboardHeight` at two lines this plan did not name.

No code was changed after the review — the pipeline forbids ping-pong; the findings are recorded here.

## 10. Finalise — the review's open issues (2026-10-03)

Each item is a change to apply to close §9, plus F10 which adds R8. F3–F8 and F10 are technical; F4 and F5 carry a choice the user may overturn, and F10's base-size shape was settled on 2026-10-03; F1 and F2 are corrections to this plan, already applied above.

| # | Issue (§9) | Resolution to apply |
|---|---|---|
| F1 | R1 unmet in landscape | R1 is scoped to portrait above; the landscape left column keeps its bespoke frame. **Amended 2026-10-03:** *untouched* now means the frame alone — its inner inset moved with W18 (§14). Doc-only otherwise. |
| F2 | §4.4 contradicted the fixed grid height | §4.4 corrected above: the base grid keeps a bounded height so its cell split is unchanged (R4), and only the floor is the scaffold's. |
| F3 | The grid height double-homes the 8 + 8 padding | Single-home it: derive the grid's bounded height from the same vertical `contentPadding` the scaffold applies, so editing one cannot mis-size the panel (ONE HOME PER FACT). |
| F4 | Portrait paints `ui.background`, landscape `ui.dashboard.background` | Give `DrawerScaffold` an optional background defaulting to `ui.background`, and have the base `DashboardPanel` pass `ui.dashboard.background`, so the dashboard's own token survives the frame change. Overturnable: accept the frame's `ui.background` if the dashboard token is to retire. |
| F5 | A panel taller than the band ceiling covers the map strip | Cap the bottom panel's own body at the band ceiling (`maxHeight - chromeTopInset`) so a taller dashboard scrolls rather than covering the map (R5). Overturnable: raise the band cap to the panel's own ceiling instead. |
| F6 | Recomposition cost per tween frame | Move the band state out of the giant `MapScreen` scope into a small holder read only by the padding and offset sites, so a measurement frame does not re-run the whole file. |
| F7 | Beyond-plan `routeBandHeight` | Fold it into §5 as the route panel's own band contribution, the panel living in `MapScreen` rather than `OverlayLayer`. |
| F8 | `docs/ui-component-guidelines.md` still names `portraitDashboardHeight` | Applied 2026-10-03: both lines renamed to `dashboardBaseHeight` in the Architect hop. |
| F9 | The remaining Lows | Applied 2026-10-03: the rotation key closed (the band holder is keyed on the base, so rotation rebuilds it and an IME resize does not) and the grid call site de-duplicated; `dashboardBaseHeight` became required in `DashboardPanel`, `WizardDrawer` and `RouteConfirmPanel`, but `MarkerDrawer`'s `minPanelHeight` still defaults to `0.dp`; the duplicated `MeasureHeight` probe was deliberately left, `onMeasuredHeight` running only in the wrap branch; and the §4.1/§5.5 reader-name note is recorded above. |
| F10 | Top corners are not conditional on size | Applied 2026-10-03: `DrawerScaffold` gained an opt-in `grownShape`, painted in the wrap branch only once the measured panel passes `wrapContentMinHeight` (null keeps every existing caller's always-shape); the base `DashboardPanel` passes none and reads square, its grid being pinned; the marker Viewing and Where-Am-I, wizard and route panels pass `RoundedCornerShape(0.dp)` at the floor and `RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)` once grown; the portrait track-detail panel was moved onto the wrap branch its siblings use (G3 retired its probe there), so its corner comes from the same gate. Landscape shapes are untouched. **Withdrawn 2026-10-03:** the device look rejected the round-once-grown effect, so the panel keeps square corners at every size — §14 W7. |

**Status 2026-10-03:** F1–F10 applied; `apk-build.bat` BUILD SUCCESSFUL and nothing committed. The second Ask hop (§11) held portrait to R1–R8 with no High finding.

## 11. Finalise review — second Ask hop (2026-10-03)

Portrait satisfies R1–R8 with no High finding. R5 passes panel by panel — the base grid is pinned and every other panel caps through `wrapContentMaxHeight`, with the track-detail panel coerced by hand — and R8 passes the same way through `grownShape`. What the hop left:

- **Medium — the ceiling's enforcement has two homes.** The cap is applied inside `DrawerScaffold`'s wrap branch and again by hand for the portrait track-detail panel, which runs the non-wrap branch, so `wrapContentMaxHeight` is silently ignored outside wrap mode and R5 is not yet held by one seam.
- **Low — the base `DashboardPanel` opts out of `grownShape`** rather than passing one; R8 still reads square because its grid is pinned, so the outcome matches while the original F10 wording was tighter than the code.
- **Low — `MarkerDrawer`'s `minPanelHeight` still defaults to `0.dp`**, so a future portrait caller omitting it would drop R2's floor.
- **Low — the duplicated `MeasureHeight` probe remains**, deliberately: `onMeasuredHeight` runs only in the wrap branch, so it cannot replace the track card's hand-measurement.
- **Low — landscape hard-codes the `8.dp` that F3 single-homed** in portrait, so editing `padV` desyncs the two orientations' insets.
- **Low — the shape gate compares a px-derived dp to a raw dp floor**, so a sub-pixel drift can flip a panel resting exactly at the floor; the base dashboard is immune, the others rarely rest there.
- **Low — R5's coercion and R8's gate have no unit coverage**, both inline in composables with no extractable helper.
- **Low — `MapLockLayer` reads the band unconditionally**, recomposing on every tween frame while unlocked with no visible effect.

No code changed after this review — the pipeline forbids ping-pong; the findings are recorded here for the user to order.

## 12. Address — §11's issues (planned 2026-10-03)

Each item closes a §11 finding. G1 and G3 are one seam seen twice and should land together; G6 and G7 share the extraction they need.

| # | Issue (§11) | Resolution to apply |
|---|---|---|
| G1 | The ceiling's enforcement has two homes | Honour `wrapContentMaxHeight` in **both** branches of `DrawerScaffold` — apply the same `heightIn(max)` on the non-wrap Column — then delete the portrait track-detail panel's hand-coercion (`OverlayLayer.kt:628`), so one seam holds R5. |
| G2 | The base `DashboardPanel` opts out of `grownShape` | Closed by the §10 F10 correction, no code: the pinned grid cannot grow, so square is R8's own answer for it. Wire `grownShape` only if the base grid ever becomes able to grow. |
| G3 | The duplicated `MeasureHeight` probe | Once G1 lands, report `onMeasuredHeight` from **both** branches, then retire the track card's `MeasureHeight`/`targetHeight` probe (`OverlayLayer.kt:732`·`:750`) and read the scaffold's measurement instead. |
| G4 | `MarkerDrawer`'s `minPanelHeight` defaults to `0.dp` | Remove the default so the floor is stated at each call: the portrait `OverlayLayer` call passes `dashboardBaseHeight`, the landscape one passes `0.dp` explicitly, its non-wrap branch ignoring the floor. |
| G5 | Landscape hard-codes the `8.dp` F3 single-homed | Use the one `padV` in both branches of `DashboardPanel`, so the two orientations' insets cannot desync. |
| G6 | The shape gate compares a px-derived dp to a raw dp floor | Extract the gate as a pure helper over pixels — `panelGrown(measuredPx, floorPx)` — comparing like with like, which also gives the rule a test seam. |
| G7 | No unit coverage for the coercion and the gate | Extract R5's coercion as a pure `bandHeightFor(base, measured, ceiling)` beside the gate, and add `ui.map` unit tests for both — the plan's one testable pair. |
| G8 | `MapLockLayer` reads the band while unlocked | Read the band only on the locked path, so the layer stops recomposing on every tween frame with no visible effect. |

## 13. Addressing review — third Ask hop (2026-10-03)

G1–G8 close §11 and R1–R8 hold, with no High finding; the wrap branch keeps the track panel floored, capped, bottom-anchored and scrollable, so its visible contract is unchanged. What the hop left:

- **Medium — §10 F10 had gone stale on the track panel.** The row said the portrait track-detail panel "reads its corner from its coerced height, running the non-wrap branch", but G3 moved it onto the wrap branch to retire its probe — corrected in F10 above. As a consequence both new non-wrap paths, the cap and the measurement report, have no portrait consumer today.
- **Low — the non-wrap cap is an unexercised guard and a latent landscape trap.** `panelCeiling` reaches the non-wrap root for every caller, so a future landscape caller re-passing the in-scope cap would silently shrink its full-height column; today every landscape call omits `panelMaxHeight`.
- **Low — G4 is half-applied.** `MarkerDrawer`'s public entry lost its default, but its two private helpers still carry `minPanelHeight: Dp = 0.dp`, so an internal caller can still inherit the silent floor.
- **Low — `MeasureHeight` is now dead.** Its probe retired with G3, so it has no production caller while two KDocs still cite it; delete it, or fix the references.
- **Low — `onMeasuredHeight`'s KDoc contradicts G3.** It still describes a wrap-only measurement, while the non-wrap branch now reports the full column too.
- **Low — the `ui.components` → `ui.map` import direction is a layering inversion**, tolerated because `ConfirmDialog` already imports from `ui.map`, though the shared gate would sit better in a neutral package.
- **Low — the landscape wizard writes a band value nothing reads**, the band being portrait-only; harmless because the holder is re-keyed on the base, but it is a writer with no reader.

No code changed after this review — the pipeline forbids ping-pong; the findings are recorded here for the user to order.

## 14. Walk items — planned (2026-10-03)

Resolved from the `#walk` over the feature's pending set. **Applied 2026-10-03:** W1–W9 and W13–W16 landed on `feature/dash-map-layout` and W18 tightened the dashboard's inset and grid spacing, with the build and unit suite green; W10–W12 were verified and their gaps became W17–W19; W17 retired its `tweak` todo as the inline assembly is the shipped shape; W20 is the fifth review's residue.

| # | Item | Fix to apply |
|---|---|---|
| W1 | The band ceiling's non-wrap cap is an unexercised guard and a landscape trap | Delete `wrapContentMaxHeight`'s application on `DrawerScaffold`'s non-wrap branch, leaving the wrap branch the only holder of R5, and drop the non-wrap mention from its KDoc. |
| W2 | `MarkerDrawer`'s private helpers still default `minPanelHeight` to `0.dp` | Remove the default from both private helpers, so every call path states the floor; the public entry already has none. |
| W3 | `MeasureHeight` is dead with two stale KDoc citations | Delete the component and correct the two citations, in `MarkerDrawer` and `DrawerScaffold`. |
| W4 | `onMeasuredHeight`'s KDoc still reads wrap-only | Correct the KDoc in `DrawerScaffold.kt` (lines 153-155) to say the report comes from both branches — a Kotlin edit, so it needs a Code hop. The shared API doc, `docs/ui-drawer-guidelines.md` §12, was brought current for the same item on 2026-10-03. |
| W5 | The `ui.components` → `ui.map` import inversion | Move `panelGrown` and `bandHeightFor` together out of `ui.map` into a neutral home both `DrawerScaffold` and `MapScreen` can reach, so the shared frame stops importing from the map package; the `ui.map` test moves with them. |
| W6 | The landscape wizard writes a band value nothing reads | Pass `onMeasuredHeight` only in `OverlayLayer`'s portrait branch, so no writer lacks a reader; the landscape calls keep their own shape and floor as they are. |
| W7 | The round-once-grown corner effect is withdrawn (device look, 2026-10-03) | Delete `grownShape` from `DrawerScaffold` and the `grownShape = …` argument from the marker Viewing and Where-Am-I, wizard, route and track panels, so every portrait bottom panel is square at every size; `panelGrown` and its test retire with it, `docs/ui-drawer-guidelines.md` §12 loses the parameter, and the feature's `## Implemented` corner claim is amended. |
| W8 | A validation badge that is not rendered cannot resize the dashboard | Retire the residue: drop `dash_valid_ok` and `dash_valid_incomplete` from both locale files, the `validationOk`/`validationWarn` colour aliases in `DashboardPanel.kt`, and the stale KDoc sentence on its line 92. |
| W9 | The zone tile's off-water state captions itself as out-of-zone | Add one `dash_not_at_sea` key in both locales and use it as the off-water caption on both the zone and the depth cards, keeping the neutral `zoneNormal` face; `dash_out_of_zone` then has no reader left and retires, unless something else still reads it. |
| W10 | `tweak` — the unified `ZoneSituation` migration | Verify the three phases against the code — `ZoneSituation`, `infoToZoneAroundBoat()` and the one `_zoneSituation` flow, then the UI collect and the `SpeedLimitCard` rewrite — and retire the todo if they are in place; its two siblings closed as W8 and W9. |
| W11 | `readability` — the six tile todos | Verify each against the code: the feature's `## Implemented` already records the tile titles, the subdued font and the bottom-line sizes, so the paddings, weights, formats, gates and smart-km text retire unless one is still unmet. |
| W12 | `size dash` — the four edge-to-edge todos | This work replaced the fixed `maxWidth * 3/5` slot with `dashboardBaseHeight` and a measured band, so verify the nav-bar behaviour, the landscape layout and any system-bar clipping on the device, then retire the section. |
| W13 | W6 is not global — the landscape route panel reports a height nothing reads | Stop passing `onMeasuredHeight` to the landscape route panel in `MapScreen`, so the portrait reader is the only one wired. |
| W14 | `DashboardBandGeometry`'s rationale is stale | Correct its KDoc: `DrawerScaffold` has no `bandHeightFor` call, so the shared-frame reason no longer holds and the file is simply a shared home. |
| W15 | `DrawerScaffold` keeps an unused `wrapContentHeight` import | Delete it — residue of the retired grown-shape logic. |
| W16 | `DrawerScaffold`'s `Shape` import sits after the project imports | Move it into the Compose import group, the grouping the other files follow. |
| W17 | W10's gap — `infoToZoneAroundBoat()` does not exist | The situation is assembled inline in the flow; either name that entry point as the todo's Phase 1 asked, or amend the todo to record that the inline assembly is the shipped shape and retire it. |
| W18 | W11's gap — the outer padding and the grid spacing | The card interior and the corner shipped as asked, but the outer panel stays 8h/8v and the grid 8 dp; either apply the stated 4h/2v/4 dp or retire the todo as overtaken. |
| W19 | W12's gap — the nav-bar item | Portrait's root carries a fixed bottom pad and no `navigationBars` inset, so only the device pass can say whether the dashboard sits behind the nav bar. |
| W20 | W18's horizontal inset has two homes | Hoist the 4 dp horizontal inset into one value beside `padV`, so editing a branch cannot desync the orientations' outer insets. |

**Folding note:** once W10–W12 retire their todos, the `tweak`, `readability` and `size dash` sections hold nothing but stale context, so the next `#bake` folds their remaining Rules and Key Files up to feature level and drops the sections (C8, C12) — no section is folded while a todo under it is open.

## 15. Walk-fix review — fourth Ask hop (2026-10-03)

The Kotlin shipped clean — no `MeasureHeight`, `panelGrown`, `grownShape`, `dash_valid_ok`, `dash_valid_incomplete` or `dash_out_of_zone` survives under `app/src`; `bandHeightFor` exists once with its test; W1, W2, W4, W8 and W9 hold as written; and R8's square corners hold panel by panel in portrait with landscape untouched. What the hop left:

- **Medium — W7's doc half was undone:** `docs/ui-drawer-guidelines.md` §12 still declared `grownShape` in both the signature and the parameter table, fixed in this hop.
- **Medium — W7's record half was undone:** the feature's finalise bullet and this plan's Outcome still read "rounding them 16 dp once grown", both amended in this hop.
- **Low — W6 is not global:** `MapScreen` still passes `onMeasuredHeight` to the landscape route panel while the band returns the base in landscape, so one writer still lacks a reader (W13).
- **Low — `DashboardBandGeometry`'s rationale is stale:** `DrawerScaffold` has no `bandHeightFor` call, so the "neither reaches into the other's package" reason no longer holds (W14).
- **Low — `DrawerScaffold`** keeps an unused `wrapContentHeight` import (W15) and puts the `Shape` import after the project imports (W16).
- **Low — `docs/ui-drawer-guidelines.md`** still cited the deleted `MeasureHeight`, fixed in this hop.
- **Low — the hydration is stale on the same facts:** it names `grownShape`, `panelGrown` and the `ui/map` path; bake-owned, so the next `#bake` refreshes it.

No code changed after this review — the pipeline forbids ping-pong.

## 16. Residue review — fifth Ask hop (2026-10-03)

W13–W16 and W18 satisfy §14, with no High finding. The Kotlin is sound: the landscape route arm no longer reports a height, `OverlayLayer`'s four callbacks are all portrait, `bandHeightFor`'s readers match its corrected KDoc, the portrait panel lays out at exactly the base, and a cell gains roughly 6 dp of content box. What the hop left:

- **Medium — W18 moved the landscape inset, which the plan called untouched:** the landscape column's outer inset went 8h/8v → 4h/2v and its grid gaps 8 → 4 dp with the shared `padV`, so §7's "Landscape: unchanged" and F1's "untouched" are amended above to mean the frame alone.
- **Low — the horizontal inset has two homes:** `4.dp` sits in each branch while the vertical is single-homed in `padV`, so one branch can drift (W20).
- **Low — `DrawerScaffold`'s import block still orders `R.string` inside the `unit.*` run**, pre-existing and the only ordering blemish left.
- **Low — the Outcome listed the outer padding among the open gaps** after W18 had applied it, amended above.

No code changed after this review — the pipeline forbids ping-pong.

## Outcome

Shipped 2026-10-03 on `feature/dash-map-layout`, nothing committed. Both phases landed, were finalised and addressed across three Ask reviews, and their residue was then walked and landed: every portrait bottom dashboard rides `DrawerScaffold` with `dashboardBaseHeight` as its floor, the map band is one animated value so the map is pushed rather than covered, the ceiling is enforced in the wrap branch alone, the corners stay square at every size (the round-once-grown effect it first shipped was withdrawn on the device's word, §14 W7), the dead `MeasureHeight` probe and the validation-badge residue are gone, the off-water caption is `dash_not_at_sea`, and the dashboard's outer inset and grid spacing tightened to 4h/2v and 4 dp (W18, which moved the landscape inset with them). `apk-build.bat` and `gradlew :app:testDebugUnitTest` are green. Deviations: the landscape frame was left bespoke, R1 scoped to portrait; the base grid keeps a bounded height rather than becoming content-sized; the portrait track panel moved onto the wrap branch; `bandHeightFor` now lives in `ui/components`; the two resolved sections' todos are retired while `size dash` keeps its two device items; and the open gaps — the missing `infoToZoneAroundBoat()` and the nav-bar item — stand recorded with the device pass unrun.
