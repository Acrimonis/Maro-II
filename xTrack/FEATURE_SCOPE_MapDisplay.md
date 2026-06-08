---
name: MapDisplay
status: active
created: 2026-06-07 00:00
modified: 2026-06-08 12:32
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
- [x] Track `zone300ManuallyHidden` (armed) + `zone300AutoRevealed` + `bandEnteredSinceReveal` flags (session-only)
- [x] Hybrid reveal in the shore pipeline: closing + (within distance OR time-to-band at SOG)
- [x] Auto-hide: stopped & not closing, compliant inside (≤ reg speed), exited seaward, retreated past margin
- [x] Suppress auto-show while inside the band (reveal only from outside, dist > 0)
- [x] Configurable distance/time in zone.properties + Settings → Avancé; reg/stop speeds in ZoneConfig
- [x] Demo support via pan-derived speed (paused = 0 kn inside / unknown outside)
- [x] Extract pure, unit-tested `zone300Decision()`; `toggleZone300Visibility()` manages the armed flag

#### Rules
- Reveal only while OUTSIDE the band (dist > 0) and closing; hybrid = distance (default 200 m) OR time-to-band (default 20 s at SOG), whichever fires first
- Auto-hide on any of: stopped & not closing (≤ 1 kn), compliant inside (≤ 5 kn = `ZoneConfig.zoneRegulatorySpeedKn`), exited seaward, retreated past the reveal margin
- `armed` persists through an auto-hide → re-approaching re-reveals; a manual toggle disarms
- Speed source: GPS real SOG; demo pan-speed (null/unknown is never read as stopped/compliant). Decision logic is shared across modes (no gpsMode branch)
- Thresholds live in `zone.properties` AND Settings → Avancé (distance/time); regulatory + stopped speeds in `ZoneConfig`
- Decision is a pure, side-effect-free `zone300Decision()` covered by `Zone300DecisionTest`; shore pipeline samples every 150 ms
- Known edge: anchored within the reveal margin with GPS distance jitter could flap (deadband/cooldown not yet added)
- Replaces the old single-shot / fixed-400 m heuristic (`ZONE_AUTO_REVEAL_M` removed)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — shore pipeline + `zone300Decision()` + flags
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Settings → Avancé sliders
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — defaults (distance/time/reg speed)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — persisted thresholds
- `app/src/main/assets/zone.properties` — tunable defaults
- `app/src/test/java/ykws/android/maro/ui/map/Zone300DecisionTest.kt` — unit tests

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
