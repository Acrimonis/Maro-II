---
name: Ui_Dashboard
status: active
created: 2026-06-06 00:00
modified: 2026-06-10 15:24
active_subfeature: none
---

# Feature: Dashboard

**Description:**
Redesign the bottom panel into a proper dashboard for quick reading of indicators: distance from coast, distance to 300m zone with speed warning, and depth under the boat with data source.

## Subfeatures

### Display  [x]

#### Todos
- [x] Extract `DashboardPanel` from `MapScreen.kt` into standalone `DashboardPanel.kt` file
- [x] Create reusable `DashboardCard` composable with card shape, background, and responsive sizing
- [x] Build `DepthCard` — green→red gradient based on depth value, with source badge and confidence
- [x] Build `Zone300Card` — pulsing border animation when inside zone, distance to boundary
- [x] Build `DistanceCard` — distance to shore with "de la côte" / "de la mer" label
- [x] Add `ValidationBadge` overlay as separate element (not inside depth card)
- [x] Implement responsive layout: 3-card row in landscape, stack in portrait, collapse at 240dp breakpoint
- [x] Wire existing StateFlow data from `CoastlineViewModel` and `DepthViewModel` into new composables
- [x] Verify portrait, landscape, and narrow-width layouts render without clipping

#### Rules
- All colors should reference theme constants or named values, not magic hex literals
- Edge case: all cards should handle null/loading states gracefully (show skeleton/shimmer)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — standalone dashboard composable
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — StateFlows for distance, zone, isWater
- `app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt` — depthAtCenter StateFlow
- `app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt` — DepthSample model

### ButtonsInDash  [ ]

#### Todos
- [x] Remove action buttons row (Côte + Bande + EarthWaterIcon) from `DashboardPanel.kt`
- [x] Update `MapScreen.kt` to stop passing action callbacks to `DashboardPanel`
- [x] Remove Côte/Bande buttons from MapContent overlay area (completely removed from UI)
- [x] Add global rule: no côte/bande/at-sea/aground controls anywhere in the UI
- [x] Remove the old `Keep the bottom action row` rule from Display subfeature

#### Rules
- The Côte (generate coastline) and Bande (regenerate 300m band) buttons are completely removed from the user interface — not just from the dashboard, but from the entire app
- The Earth/Water icon display is removed from the dashboard (map overlay's EarthWaterIcon display remains)
- The coastline loads automatically on init (`initCache`); no manual refresh button is exposed

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — remove action row, simplify params
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — relocate action buttons to MapContent

### color tile 300m  [x]

#### Todos
- [x] Investigate: Zone300Card uses grey `zoneNormal` background when on water but outside zone — consider using default blue (`cardBg`) instead
- [x] Implement: change `Zone300Card` outside-zone background from grey `zoneNormal` to default `cardBg` (navy blue)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — Zone300Card composable, lines 389-400

### tweak  [ ]

#### Todos
- [ ] Dashboard must not resize when "données validées (RMSE...)" info is displayed — make layout ready for it
- [ ] When !isOnWater, Zone tile must display neutral background with "Not at sea" caption instead of a value
#### Key Files

### tile titles  [x]

#### Todos
- [x] Bump title fontSize 10.sp → **13.sp** (was 12.sp, increased one more size)
- [x] Change title color from `textMuted` to `textPrimary`
- [x] All-caps titles via `title.uppercase()`
- [x] Keep title fontWeight SemiBold (unchanged)
- [x] Leave subtitle (9.sp, Medium, textMuted) untouched

#### Rules
- Do not increase title size beyond what measurably reduces the value's auto-sized font — the value is the primary display element
- All changes must be in `DashboardCard` composable only, not per-card overrides

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — DashboardCard composable, lines 189-197
- `plans/dashboard-tile-titles.md` — sizing analysis

#### Docs
- [`plans/dashboard-tile-titles.md`](plans/dashboard-tile-titles.md) — sizing & prominence analysis for tile titles

### tile subdued font  [x]

#### Todos
- [x] Set `dullAlpha = 0.33f` — all subdued states use consistent 33% alpha
- [x] Zone300Card far-from-zone: grey `zoneNormal` background (like on-land state)
- [x] DepthCard "Deep!": grey `zoneNormal` background instead of dimmed cardBg
- [x] DepthCard no-data: grey `zoneNormal` background instead of default cardBg
- [x] Add `alertDistanceM` param to DashboardPanel → Zone300Card, wire from MapScreen

#### Rules
- Do not affect the normal data display colors — only the "low priority" / secondary states
- The 300m zone tile should use alert-distance threshold (settings.zoneAutoRevealDistanceM) to decide normal vs dull rendering

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`

### readability  [ ]

#### Todos
- [x] Finalize format specs (speed cap, depth tag, smart km, padding cuts, font weights) — plan locked
- [ ] Reduce paddings: outer 12→4.h / 10→2.v, card 8→4.h / 6→2.v, grid 8→4.dp, corner 10→8.dp
- [ ] Bump title font weight: Medium → SemiBold
- [ ] Bump subtitle font weight: Normal → Medium
- [ ] Update `strings.xml`: `%4.1f kn`, `%4.1f m`, `%4.0f m`; add `dash_value_km_int`, `dash_value_depth_m_int`, `dash_depth_deep`
- [ ] Update `values-fr/strings.xml`: add `dash_depth_deep` = "Fond!", plus int format resources
- [ ] Add speed > 99.9 kn gate → show dash in `SpeedCard`
- [ ] Add depth ≥ 100m gate → show `dash_depth_deep` in `DepthCard`
- [ ] Update `distanceText()`: smart km (`%.1f km` < 10 km, `%d km` ≥ 10 km)
- [ ] Reduce outer panel padding: 12.h → 8.dp, 10.v → 6.dp
- [ ] Reduce card internal padding: 8.h → 6.dp, 6.v → 4.dp
- [ ] Reduce grid row/column spacing: 8.dp → 6.dp
- [ ] Bump title font weight: Medium → SemiBold
- [ ] Bump subtitle font weight: Normal → Medium

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`

## Todos

## Rules

## Key Files

## Docs
- [`plans/dashboard-readability-improvements.md`](plans/dashboard-readability-improvements.md) — Readability & space management discussion plan (format padding, reduced paddings, font weight bumps)
- `plans/dashboard-tile-titles.md` — Dashboard tile titles sizing & prominence design
