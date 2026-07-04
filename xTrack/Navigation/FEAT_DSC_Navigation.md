---
name: Navigation
status: active
created: 2026-06-10 08:40
modified: 2026-07-04 08:53
active_subfeature: dash distance
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
- Arrow always draws straight up (screen-top) — heading-up map rotation aligns heading with screen-up via `mapOrientation = -bearingDeg`.
- Direction line is a thin dashed line (12px dash / 6px gap, 1dp stroke) from screen center straight to top edge.
- Navigation state (bearing, speed, demo speed) uses a single NavigationState data class for atomic Compose reads.
- Heading line visible (default ON) and Variable Arrow (default OFF) are toggled from Settings → Display → Navigation.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — CenterMarkerOverlay + DirectionLine composables, settings UI
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — NavigationState data class, atomic StateFlow updates
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — directionLineColor field + loading
- `app/src/main/assets/maro.properties` — direction.line.color property
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — headingLineVisible, capArrowVisible in AppSettings

#### Docs
- `xTrack/Navigation/FEAT_PLN_Navigation_cap-arrow.md` — original cap arrow design spec
- `xTrack/Navigation/FEAT_PLN_Navigation_atomic-render.md` — atomic data flow + rendering analysis

### dash distance  [x]

Unified distance tile logic: single-branch compare replaces P1-P4 cascade. Shows nearest zone boundary on heading cone, compares speed limits, renders amber (more restrictive) / green (less restrictive) / coastline (default).

#### Todos
- [x] Audit all distance setters feeding the dashboard distance tile
- [x] Decide P1–P4 logic → unified compare (amber=more restrictive, green=less restrictive, coastline=default)
- [x] Implement: add etaSeconds to SHOM exit ZoneBoundaryInfo
- [x] Implement: rewrite DistanceCard with unified compare logic
- [x] Implement: remove dead nextZoneAhead variable
- [x] Implement: always positive distance, always ↑ prefix
- [x] Build green

#### Rules
- Alert thresholds: `autoRevealDistanceM` (100m) + `autoRevealTimeS` (10s)
- Heading cone: ±15° (CONE_HALF_ANGLE), heading-ray only
- SHOM exit beyondType kept as OPEN_SEA (deferred)
- LAND beyond exit → excluded → coastline
- Same-limit transition → coastline (no alert)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — DistanceCard unified logic, lines 315–457
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — etaSeconds added to SHOM exit (line 496–501), ShoreState pipeline (lines 460–545)

#### Docs

## Implemented

| Date | Summary |
|------|---------|
| 2026-07-04 | **dash distance:** Unified distance tile — single-branch speed-limit compare replaces 4-priority cascade. Amber = more restrictive ahead, green = less restrictive ahead, coastline = default. Added ETA to SHOM zone exit. Removed dead `nextZoneAhead`. Always positive `↑` prefix. Build green. |

## Todos
- [ ] On-device visual verification of all navigation features

## Rules

## Key Files

## Docs
