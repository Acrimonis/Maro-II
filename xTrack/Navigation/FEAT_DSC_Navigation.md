---
name: Navigation
status: active
created: 2026-06-10 08:40
modified: 2026-06-10 14:21
active_subfeature: cap
---

# Feature: Navigation

**Description:**
Navigation aids on the map overlay — heading/speed indicator and direction line projecting from the boat marker.

## Subfeatures

### cap  [x]

#### Todos
- [x] Add `bearingDeg` and `speedKnots` parameters to `CenterMarkerOverlay` composable call site (MapScreen.kt)
- [x] Compute arrow length: `speedKnots × 0.514444 × 5s` → meters, convert to dp at zoom level
- [x] Draw arrow on Canvas: line + arrowhead triangle extending from top-center of boat icon in bearing direction
- [x] Add minimum arrow length and maximum cap with zoom scaling (same ZOOM_EXPONENT as boat marker)
- [x] Handle zero-speed / no-bearing / no-GPS: hide arrow
- [x] Wire `mapBearing` from ViewModel through `MapContent` to the overlay
- [x] Fix demo speed computation: sqrt compression instead of linear /10 divisor
- [x] Fix atomic data flow: merge 3 separate StateFlows into single NavigationState data class
- [x] Fix visual offset: remove `.offset(y = -arrowDp)` causing arrow to float disconnected above boat
- [x] Add direction line: thin dashed line from boat to map edge in heading direction
- [x] Add Navigation settings block under Display with Heading + Variable Arrow toggles (regression: toggle rows missing from develop, re-added in fix)
- [x] Add `direction.line.color` to maro.properties
- [x] Tune arrow rendering: 1dp min @3kn → 65dp max @30kn, 2.5kn threshold, thicker stroke ×1.5
- [x] Build green (assembleDebug)
- [ ] Re-verify build after restoring missing settings toggle rows

#### Rules
- Arrow hides below 2.5 kn (drifting threshold).
- Arrow length = speed(kn) × 2.17 dp, clamped to [1, 65] dp at REF_ZOOM, scaled with zoom via ZOOM_EXPONENT.
- Arrow direction = bearingDeg (GPS course or compass azimuth).
- Direction line is a thin dashed line (12px dash / 6px gap, 1dp stroke) from screen center to map edge in heading direction.
- Navigation state (bearing, speed, demo speed) uses a single NavigationState data class for atomic Compose reads.
- Heading line visible (default ON) and Variable Arrow (default OFF) are toggled from Settings → Display → Navigation.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — CenterMarkerOverlay + DirectionLine composables, settings UI
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — NavigationState data class, atomic StateFlow updates
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — directionLineColor field + loading
- `app/src/main/assets/maro.properties` — direction.line.color property
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — headingLineVisible, capArrowVisible in AppSettings

#### Docs
- `plans/navigation-cap-arrow.md` — original cap arrow design spec
- `plans/navigation-atomic-render.md` — atomic data flow + rendering analysis

## Todos
- [ ] On-device visual verification of all navigation features

## Rules

## Key Files

## Docs
