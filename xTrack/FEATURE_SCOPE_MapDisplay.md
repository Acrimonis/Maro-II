---
name: MapDisplay
status: active
created: 2026-06-07
modified: 2026-06-07
active_subfeature: zone proximity auto-reveal
subs_total: 4
subs_done: 3
one_liner: Map display layer management — depth layer, color depth layer, and orientation-aware rendering.
---

**Description:** Map display layer management — depth layer, color depth layer, and orientation-aware rendering.

## Subfeatures

### layer refresh  [x]

#### Todos
- [x] Refactor MapScreen to single Box parent — stable MapContent slot, overlaid DashboardPanel via Modifier.align()
- [x] Apply orientation-aware padding to MapContent (left in landscape, bottom in portrait)
- [x] Add android:configChanges to manifest — prevents Activity destruction/recreation on rotation
- [x] Verify overlays survive orientation switch (no spurious redraw)
- [x] Test both landscape→portrait and portrait→landscape transitions

#### Rules
- MapContent must remain at a stable Compose slot position — never inside an if/else branch
- `Modifier.align()` is the correct mechanism for dashboard overlay positioning
- Use `PaddingValues` for map content offset, not Row/Column structural swap

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

### depth color  [ ]

#### Todos
- [ ] Align DepthCard background color with DepthColorRamp palette
- [ ] Map depthM → ARGB using same interpolation as the map overlay

#### Rules
- Dashboard depth tile color must match the map's hypsometric depth gradient
- Use DepthColorRamp.argb() as the single source of truth for depth→color mapping

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt`

### zone proximity auto-reveal  [x]

#### Todos
- [x] Track `zone300ManuallyHidden` state in CoastlineViewModel (session-only, not persisted)
- [x] Inject auto-re-enable logic in the shore pipeline onEach block, watching `_distanceToZone` threshold
- [x] Single-shot: once auto-re-enabled, normal toggle behavior resumes
- [x] Extract toggle into `viewModel.toggleZone300Visibility()` to manage the manual-hide flag

#### Rules
- Single-shot only — the system should not harass the user by re-enabling repeatedly
- Works in both GPS and demo modes (no GPS gating) — distance-based trigger only
- Uses a ~400m buffer (100m before the 300m boundary) as the activation threshold
- The auto-re-enable IS the alert — no separate toast/sound needed
- Design note: `distanceToZone = distanceToCoast - 300.0` already computed every 150ms

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`

### speed in demo  [x]

#### Todos
- [x] Compute simulated speed in knots from map pan velocity during demo mode
- [x] Display in SpeedCard instead of "—" when in demo mode
- [x] Throttle computation to the existing 150ms shore pipeline cadence
- [x] Handle pan start/stop transitions gracefully (zero speed when map settles)

#### Rules
- Only active in demo mode (gpsMode == false) — GPS mode uses actual GPS speed
- Use Haversine distance between successive map center samples ÷ elapsed time
- The speed should settle to zero shortly after the user stops dragging (no persistent phantom speed)
- Surface via a new StateFlow (e.g., `demoSpeedKnots`) in CoastlineViewModel
- Dashboard SpeedCard merges: GPS speed (non-null) → demo speed (non-null) → "—"

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Todos

## Rules

## Key Files

## Docs
