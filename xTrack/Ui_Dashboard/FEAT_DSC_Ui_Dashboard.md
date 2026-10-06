---
name: Ui_Dashboard
status: active
created: 2026-06-06 00:00
modified: 2026-10-03 17:51
---

# Feature: Dashboard

**Description:**
Redesign the bottom panel into a proper dashboard for quick reading of indicators: distance from coast, distance to 300m zone with speed warning, and depth under the boat with data source.

## Sections

### size dash

Investigate and fix dashboard sizing in immersive edge-to-edge mode (portrait height behind navigation bar; map content fills status-bar area without overlapping controls).

#### Todos
- [ ] Check the landscape layout, the W18 inset change included — plan §14 W18
- [ ] Verify no content clipped or obscured by the system bars in either orientation — portrait's root carries no `navigationBars` inset, only a fixed bottom pad (plan §14 W19)

#### Rules
- `dashboardBaseHeight = maxWidth * 3/5` must be maintained as the canonical formula
- Map content must not overlap dashboard controls; status bar area must stay interactive

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — BoxWithConstraints layout, dashboard positioning
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — Dashboard composable sizing

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — the 2×2 grid, its cards and the off-water caption
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — the dashboard's strings, both locales
- `app/src/main/java/ykws/android/maro/ui/components/DashboardBandGeometry.kt` — the pure band coercion the map's height reads

## Walk
**Level 1 — Date:** 2026-10-03 · **Source:** pending set (the feature's open todos + the plan's unimplemented items) · **Closed:** 2026-10-03 — exhausted
- [x] 1 · The band ceiling's non-wrap cap is an unexercised guard and a landscape trap — child walk closed: recorded as §14 W1, the non-wrap guard goes and the wrap branch stays the only holder of R5
- [x] 2 · `MarkerDrawer`'s private helpers still default `minPanelHeight` to `0.dp` — closed: recorded as §14 W2, the default comes off both private helpers
- [x] 3 · `MeasureHeight` is dead with two stale KDoc citations — closed: recorded as §14 W3, the component goes and both citations are corrected
- [x] 4 · `onMeasuredHeight`'s KDoc still reads wrap-only — closed: the shared API doc updated, the KDoc itself recorded as §14 W4 for a Code hop
- [x] 5 · The `ui.components` → `ui.map` import inversion — closed: planned as recommended, §14 W5
- [x] 6 · The landscape wizard writes a band value nothing reads — closed: planned as recommended, §14 W6
- [x] 7 · Device-validate the normalised frame and the map sync — ran: the round-once-grown corner effect is withdrawn, corners stay square — recorded as §14 W7
- [x] 8 · tweak — the dashboard must not resize when the validation info shows — closed: the badge has no code behind it, so the residue is retired — §14 W8
- [x] 9 · tweak — the Zone tile shows a neutral background and a not-at-sea caption off water — closed: planned as recommended, §14 W9
- [x] 10 · tweak — the full migration to the unified `ZoneSituation` model — closed: planned, §14 W10, verify and retire
- [x] 11 · readability — reduce the paddings — closed: planned, §14 W11, verify and retire
- [x] 12 · readability — bump the title and subtitle weights — closed: planned, §14 W11, verify and retire
- [x] 13 · readability — the `strings.xml` integer formats — closed: planned, §14 W11, verify and retire
- [x] 14 · readability — the French strings for the same formats — closed: planned, §14 W11, verify and retire
- [x] 15 · readability — the speed and depth gates — closed: planned, §14 W11, verify and retire
- [x] 16 · readability — the smart-km distance text — closed: planned, §14 W11, verify and retire
- [x] 17 · size dash — verify the panel height behind the navigation bar — closed: planned, §14 W12, verify on device then retire the section
- [x] 18 · size dash — the map padding accounts for the dashboard height — closed: planned, §14 W12
- [x] 19 · size dash — landscape unaffected by the edge-to-edge changes — closed: planned, §14 W12
- [x] 20 · size dash — no content clipped or obscured by the system bars — closed: planned, §14 W12

**Closed 2026-10-03 — level exhausted:** 20 items resolved, none dropped and no level parked. W1–W12 are recorded in the plan's §14; one device verdict was taken, one doc updated in place, and the thirteen fixes wait on an order.

## Implemented

- **dashboard-padding-normalization (2026-10-06, `feature/ui-small`)** — the dashboard's vertical padding was raised to its horizontal twin, on the user's word that the band above the top row read tighter than everything else. [`DashboardPanel`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:129)'s `padV` went **2 dp → 4 dp** — the frame's `contentPadding`, the grid's bounded height (`dashboardBaseHeight − padV × 2`, now 8 dp) and the landscape branch with it — and the cards' `padding(horizontal = 4.dp, vertical = 2.dp)` became **4 dp on both axes**, so the panel's vertical spaces now equal its 4 dp sides and its 4 dp grid gaps. [`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:671) §5.3's tile pad moved from `4×2dp` to `4dp`. `apk-build.bat` BUILD SUCCESSFUL; nothing device-validated → [`261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md`](261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md)

- **dashboard-layout-walk (2026-10-03)** — the closed walk's fixes landed: the corners stay square at every size, the round-once-grown effect withdrawn on the device's word; the dead `MeasureHeight` probe, the unused non-wrap ceiling, the validation-badge residue and the stray landscape measurement are gone; `bandHeightFor` lives in `ui/components`; and the dashboard's outer inset and grid spacing tightened to 4h/2v and 4 dp. `apk-build.bat` and `gradlew :app:testDebugUnitTest` green; the fourth and fifth Ask hops recorded their residue as §14 W20 and the storage as amended. Nothing here is device-validated → [`261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md`](261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md) §14–§16

- **dashboard-layout-addressing (2026-10-03)** — §11's findings closed: `DrawerScaffold` holds R5's one map-ceiling seam and the portrait track panel moved onto the wrap branch, its `MeasureHeight` probe retired; `MarkerDrawer`'s floor is stated per call, `DashboardPanel` shares one `padV` across both orientations, and the band coercion became the pure, unit-tested `bandHeightFor` in a new `DashboardBandGeometry.kt`, with `MapLockLayer` reading the band only while locked (its `panelGrown` companion retired later with the corner rule). `apk-build.bat` and `gradlew :app:testDebugUnitTest` both BUILD SUCCESSFUL with `DashboardBandGeometryTest` green; the third Ask hop confirmed G1–G8 close §11 with one Medium (F10's own wording had gone stale) and several Lows. Nothing here is device-validated → [`261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md`](261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md) §12–§13

- **dashboard-layout-finalise (2026-10-03)** — the normalisation's finalise pass: the grid's padding sum, the dashboard's own background token and the map ceiling each gained one home, the band moved into a `DashboardBandState` holder so a measurement no longer re-runs `MapScreen`, and every portrait bottom panel now caps at the one band ceiling (R5) and keeps square top corners at every size (R8 — the round-once-grown effect it first shipped was withdrawn on the device's word, plan §14 W7). `apk-build.bat` BUILD SUCCESSFUL and nothing committed; the second Ask hop held portrait to R1–R8 with no High and recorded one Medium (the ceiling's enforcement still lives in two seams) and several Lows. Nothing here is device-validated → [`261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md`](261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md) §10–§11

- **dashboard-layout-normalization (2026-10-03)** — every portrait dashboard now shares one auto-resizing frame with one named base size, and a dashboard taller than that base pushes the map down instead of covering it: `portraitDashboardHeight` became `dashboardBaseHeight` through every consumer, `DrawerScaffold` gained a nullable `onClose` plus an optional header and an `onMeasuredHeight`, the Layer-0 2×2 grid and the Where-Am-I card moved onto that frame, and `MapScreen` drives an animated `dashboardBandHeight` from the open panel's measured height. `apk-build.bat` BUILD SUCCESSFUL with `app-debug.apk` produced and nothing committed; the Ask hop found no High and recorded two Medium departures (the landscape frame stays bespoke, the base grid is pinned to a fixed height) plus a Medium where a dashboard taller than the band ceiling still covers the map strip. Nothing here is device-validated → [`261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md`](261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md) §9

- **Display** — `DashboardPanel` extracted; `DashboardCard`/`DepthCard`/`Zone300Card`/`DistanceCard` + `ValidationBadge`; responsive 3-card row / stack / 240dp breakpoint
- **ButtonsInDash** — Côte/Bande/at-sea/aground action buttons removed from the entire UI; coastline auto-loads
- **color tile 300m** — Zone300Card outside-zone background grey `zoneNormal` → default navy `cardBg`
- **tile titles** — title 13.sp + textPrimary + uppercase
- **tile subdued font** — `dullAlpha = 0.33f`; subdued states (far-zone, "Deep!", no-data) use grey `zoneNormal`
- **zone tile display evolution** — shared 100m/10s thresholds; exit preview when close; zone tile shows limit + beyond type + next zone
- **distance tile** — nearest-boundary logic (shore / exit / entry), `-` prefix, on-land subdued; removed `isNearEntry` gate
- **tile bottom line** — title 13→15sp, subtitle 9→13sp, textMutedBright → `xTrack/Ui_Dashboard/260711_FEAT_PLN_Ui_Dashboard_tile-bottom-line.md`

## Docs
- `xTrack/Ui_Dashboard/261003_FEAT_PLN_Ui_Dashboard_dashboard-layout-normalization.md` — the layout normalisation, one frame and one base size, with its Ask review in §9
- `xTrack/Ui_Dashboard/260610_FEAT_PLN_Ui_Dashboard_readability-improvements.md` — Readability & space management discussion
- `xTrack/Ui_Dashboard/260610_FEAT_PLN_Ui_Dashboard_tile-titles.md` — Dashboard tile titles sizing & prominence
- `xTrack/Ui_Dashboard/260704_FEAT_PLN_Ui_Dashboard_dash-distance-combined-fixes.md` — Dash distance combined fixes
- `xTrack/Ui_Dashboard/260704_FEAT_PLN_Ui_Dashboard_dash-distance-shom-beyondtype-fix.md` — Dash distance SHOM beyond type fix
- `xTrack/Ui_Dashboard/260704_FEAT_PLN_Ui_Dashboard_dash-distance-shore-bound-300m-gate.md` — Dash distance shore bound 300m gate
