---
name: Dashboard
status: active
created: 2026-06-06
modified: 2026-06-06
active_subfeature: none
subs_total: 1
subs_done: 0
one_liner: Redesign the bottom dashboard panel into visual gauge cards for quick reading of key indicators.
---

# Feature: Dashboard

**Description:**
Redesign the bottom panel into a proper dashboard for quick reading of indicators: distance from coast, distance to 300m zone with speed warning, and depth under the boat with data source.

## Subfeatures

### Display  [ ]

#### Todos
- [ ] Extract `DashboardPanel` from `MapScreen.kt` into standalone `DashboardPanel.kt` file
- [ ] Create reusable `DashboardCard` composable with card shape, background, and responsive sizing
- [ ] Build `DepthCard` — green→red gradient based on depth value, with source badge and confidence
- [ ] Build `Zone300Card` — pulsing border animation when inside zone, distance to boundary
- [ ] Build `DistanceCard` — distance to shore with "de la côte" / "de la mer" label
- [ ] Add `ValidationBadge` overlay as separate element (not inside depth card)
- [ ] Implement responsive layout: 3-card row in landscape, stack in portrait, collapse at 240dp breakpoint
- [ ] Wire existing StateFlow data from `CoastlineViewModel` and `DepthViewModel` into new composables
- [ ] Verify portrait, landscape, and narrow-width layouts render without clipping

#### Rules
- Keep the bottom action row (Generate + Earth/Water toggle) at the bottom of the dashboard
- No new ViewModel or data-layer changes — only UI composable refactoring
- All colors should reference theme constants or named values, not magic hex literals
- Edge case: all cards should handle null/loading states gracefully (show skeleton/shimmer)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — current DashboardPanel location, needs extraction
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — StateFlows for distance, zone, isWater
- `app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt` — depthAtCenter StateFlow
- `app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt` — DepthSample model

## Todos

## Rules

## Key Files

## Docs
