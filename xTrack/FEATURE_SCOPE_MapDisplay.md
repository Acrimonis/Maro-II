---
name: MapDisplay
status: active
created: 2026-06-07
modified: 2026-06-07
active_subfeature: none
subs_total: 3
subs_done: 2
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
- [x] Align DepthCard background color with DepthColorRamp palette
- [x] Map depthM → ARGB using same interpolation as the map overlay

#### Rules
- Dashboard depth tile color must match the map's hypsometric depth gradient
- Use DepthColorRamp.argb() as the single source of truth for depth→color mapping

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DepthColorRamp.kt`

### zonetile  [x]

#### Todos
- [x] Add speedKnots param to Zone300Card
- [x] Implement distance+speed color rules
- [x] Test near-zone + speed compliance colors

#### Rules
- Zone card color = f(distanceToZone, speedKnots)
- dist<200m & speed>5kn → dark red (very close + speeding)
- dist<300m & speed>10kn → dark red (near + very fast)
- dist<300m & 5<speed<10 → orange (near + moderate)
- dist<300m & speed<5 → green (near + compliant)
- dist≥300m → default muted gray

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt`
- `app/src/main/assets/zone.properties`

## Todos

## Rules

## Key Files

## Docs
