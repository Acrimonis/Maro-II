---
name: Dashboard
status: active
created: 2026-06-06
modified: 2026-06-07
active_subfeature: none
subs_total: 2
subs_done: 1
one_liner: Redesign the bottom dashboard panel into visual gauge cards for quick reading of key indicators.
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

## Todos

## Rules

## Key Files

## Docs
