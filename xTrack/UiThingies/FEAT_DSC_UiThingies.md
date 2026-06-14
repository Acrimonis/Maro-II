---
name: UiThingies
status: active
created: 2026-06-06 00:00
modified: 2026-06-14 11:43
active_subfeature: arc-layout-button
---

# Feature: UiThingies

**Description:**
UI layout refinements for the Maro map — reorganizing on-screen elements for better ergonomics.

## Subfeatures

### onwater-button  [x]

#### Todos
- [x] Move the isOnWater() icon from the bottom info panel to the top-left corner of the map area

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### settings  [x]

#### Todos
- [x] Add SettingsButton matching ZoomButton style (64dp circle, white bg, blue icon) to top-right of MapContent
- [x] Implement settings page/overlay that opens on tap
- [x] Create SettingsManager with SharedPreferences persistence
- [x] Wire coastline visibility toggle to actual map rendering
- [x] Add default map center lat/lon text fields

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### zoom-and-position  [x]

#### Todos
- [x] Persist map center lat/lon and zoom level on every pan/zoom via SharedPreferences
- [x] Restore persisted position on app start and after rotation

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### hideLAyers  [x]

#### Todos
- [x] Remove initial positions (lat/lon fields) from settings UI
- [x] Add hide/show 300m Zone toggle to settings (second position in first section)

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

#### Docs

### remove-actions  [x]

#### Todos
- [x] Remove all buttons from the dashboard panel (cote, Bande, water/ground)

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`

#### Docs

### arc-layout-button  [ ]

#### Todos
- [ ] Create `FanConfig` data class (thetaDeg, currentCount, direction, buttonSizeDp, edgeGapDp, isOpen) + `FanDirection` enum (UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT)
- [ ] Create `MapControlButton` composable — 64 dp circle, white bg `0xCCFFFFFF`, theme blue `0xFF1565C0`, zero padding, contentDescription on Button
- [ ] Create `FanLayout` composable — parent at center, children on arc at radius R computed from θ + button size + edge gap
- [ ] Implement geometry: `R = (buttonSize + edgeGap) / (2 × sin(θ/2))`, children positioned at `startAngle + offset + i × θ`, centered in the directional arc
- [ ] Implement z-ordering: parent at bottom, children drawn ON TOP (higher z-index)
- [ ] Standardise icon size to 28 dp across all control-stack buttons
- [ ] Create Canvas icon composables: CircleRingIcon (300m zone), WarningTriangleIcon (danger layer), PlusIcon, MinusIcon, GearIcon
- [ ] Port existing 5 buttons to `MapControlButton` + `FanLayout` (first fan = layer toggles group)
- [ ] Add second fan button in the right-edge control stack (close to the layer fan)
- [ ] Replace hardcoded Spacer(136.dp) with computed value
- [ ] Verify fan layout and spacing on portrait / landscape / narrow-width layouts

#### Rules — STRONG (Geometry)
- **θ is the primary parameter.** It defines the angular spacing between adjacent children as seen from the parent at center. R is DERIVED from θ + button size + edge gap.
- **Parent at center.** Parent button sits at the center of the circle. Children sit on the arc at radius R around it.
- **Equidistance per relationship type.** All parent→child distances = R (internally consistent). All child↔child chords = 2R × sin(θ/2) (internally consistent). These two values differ numerically unless θ = 60°. This is INTENTIONAL.
- **R formula:** `R = (buttonSizeDp + edgeGapDp) / (2 × sin(θ/2))` — ensures no button overlap with a consistent visual gap.
- **Children centered in arc.** The N children are centered in the directional arc using offset from the start angle.
- **Children on top.** Children render at higher z-order than the parent. On fan open, animate from parent center outward to arc positions.

#### Rules — STRONG (Visual)
- All control-stack buttons use `MapControlButton` composable (64 dp circle, white bg `0xCCFFFFFF`, theme blue `0xFF1565C0`)
- Icon size standardised to 28 dp across all control-stack buttons
- Canvas drawing for icons (no material-icons-extended dependency); exception: Settings gear may reuse Material `Icons.Default.Settings`
- Active/inactive toggle: icon alpha 1.0f / 0.25f, button background stays `0xCCFFFFFF` regardless

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — control stack layout, existing button composables
- New: `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt` — FanLayout composable
- New: `app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt` — FanConfig + FanDirection
- New: `app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt` — shared button composable
- New: `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — Canvas icon composables

#### Docs
- `plans/arclayout-button-analysis.md` — full UI analysis, geometry spec, and design decisions

## Todos

## Rules

## Key Files

### compact-dash  [x]

#### Todos
- [x] Cherry-pick dashboard card compaction commit (fb4af4b) into branch

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`

#### Docs

### dash-size  [ ]

#### Todos
- [ ] In landscape mode, change `landscapeDashboardWidth` from `maxHeight * 2 / 3` to `maxHeight` — the dashboard width equals the full screen height
- [ ] Verify all 4 cards (Distance, Zone300, Depth, Speed) still render without clipping at the wider landscape dashboard width
- [ ] Confirm the map padding `PaddingValues(start = landscapeDashboardWidth)` correctly shrinks the map area to accommodate the wider dashboard
- [ ] Test on narrow-landscape devices (e.g. small phone in landscape) to ensure the dashboard doesn't overflow

#### Rules
- The 2×2 card grid layout inside `DashboardPanel.kt` does not need changes — only the sizing modifier passed from `MapScreen.kt`
- No new composables or parameters — purely a dimension constant change

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — line 378: `landscapeDashboardWidth = maxHeight * 2 / 3` → `maxHeight`

#### Docs

## Docs
