---
name: Ui_Dashboard
status: active
created: 2026-06-06 00:00
modified: 2026-07-11 10:59
---

# Feature: Dashboard

**Description:**
Redesign the bottom panel into a proper dashboard for quick reading of indicators: distance from coast, distance to 300m zone with speed warning, and depth under the boat with data source.

## Sections

### tweak

#### Todos
- [ ] Dashboard must not resize when "données validées (RMSE...)" info is displayed — make layout ready for it
- [ ] When !isOnWater, Zone tile must display neutral background with "Not at sea" caption instead of a value
- [ ] Full migration to unified `ZoneSituation` model (Phase 1 BL: `ZoneSituation` + `infoToZoneAroundBoat()` + single `_zoneSituation` StateFlow; Phase 2 UI: collect in MapScreen, rewrite SpeedLimitCard; Phase 3 Cleanup)

### readability

#### Todos
- [ ] Reduce paddings: outer 12→4.h / 10→2.v, card 8→4.h / 6→2.v, grid 8→4.dp, corner 10→8.dp
- [ ] Bump title font weight Medium → SemiBold; subtitle Normal → Medium
- [ ] Update `strings.xml`: `%4.1f kn`, `%4.1f m`, `%4.0f m`; add `dash_value_km_int`, `dash_value_depth_m_int`, `dash_depth_deep`
- [ ] Update `values-fr/strings.xml`: add `dash_depth_deep` = "Fond!" + int format resources
- [ ] Add speed > 99.9 kn gate → dash in `SpeedCard`; depth ≥ 100m gate → `dash_depth_deep` in `DepthCard`
- [ ] `distanceText()` smart km (`%.1f km` < 10 km, `%d km` ≥ 10 km)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`

### size dash

Investigate and fix dashboard sizing in immersive edge-to-edge mode (portrait height behind navigation bar; map content fills status-bar area without overlapping controls).

#### Todos
- [ ] Verify dashboard panel height (`maxWidth * 3/5`) still works behind the navigation bar
- [ ] Ensure map padding (`bottom = portraitDashboardHeight`) accounts for dashboard height
- [ ] Check landscape layout unaffected by edge-to-edge changes
- [ ] Verify no content clipped or obscured by system bars in either orientation

#### Rules
- `portraitDashboardHeight = maxWidth * 3/5` must be maintained as the canonical formula
- Map content must not overlap dashboard controls; status bar area must stay interactive

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — BoxWithConstraints layout, dashboard positioning
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — Dashboard composable sizing

## Implemented

- **Display** — `DashboardPanel` extracted; `DashboardCard`/`DepthCard`/`Zone300Card`/`DistanceCard` + `ValidationBadge`; responsive 3-card row / stack / 240dp breakpoint
- **ButtonsInDash** — Côte/Bande/at-sea/aground action buttons removed from the entire UI; coastline auto-loads
- **color tile 300m** — Zone300Card outside-zone background grey `zoneNormal` → default navy `cardBg`
- **tile titles** — title 13.sp + textPrimary + uppercase
- **tile subdued font** — `dullAlpha = 0.33f`; subdued states (far-zone, "Deep!", no-data) use grey `zoneNormal`
- **zone tile display evolution** — shared 100m/10s thresholds; exit preview when close; zone tile shows limit + beyond type + next zone
- **distance tile** — nearest-boundary logic (shore / exit / entry), `-` prefix, on-land subdued; removed `isNearEntry` gate
- **tile bottom line** — title 13→15sp, subtitle 9→13sp, textMutedBright → `xTrack/Ui_Dashboard/260711_FEAT_PLN_Ui_Dashboard_tile-bottom-line.md`

## Todos
- [x] Fix AutoSizeValue px/dp unit mismatch — `LocalDensity` conversion
- [x] Fix AutoSizeValue vertical centering — moved `onSizeChanged` to outer Box
- [x] Fix direction arrow thresholds — 45° → CONE_HALF_ANGLE (15°)
- [x] Remove space between direction arrow and distance value

## Rules

## Key Files

## Docs
- `xTrack/Ui_Dashboard/260610_FEAT_PLN_Ui_Dashboard_readability-improvements.md` — Readability & space management discussion
- `xTrack/Ui_Dashboard/260610_FEAT_PLN_Ui_Dashboard_tile-titles.md` — Dashboard tile titles sizing & prominence
- `xTrack/Ui_Dashboard/260704_FEAT_PLN_Ui_Dashboard_dash-distance-combined-fixes.md` — Dash distance combined fixes
- `xTrack/Ui_Dashboard/260704_FEAT_PLN_Ui_Dashboard_dash-distance-shom-beyondtype-fix.md` — Dash distance SHOM beyond type fix
- `xTrack/Ui_Dashboard/260704_FEAT_PLN_Ui_Dashboard_dash-distance-shore-bound-300m-gate.md` — Dash distance shore bound 300m gate
