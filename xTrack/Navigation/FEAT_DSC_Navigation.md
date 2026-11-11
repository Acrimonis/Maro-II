---
name: Navigation
status: active
created: 2026-06-10 08:40
modified: 2026-11-11 16:17
---

# Feature: Navigation

**Description:**
Navigation aids on the map overlay — heading/speed indicator and direction line projecting from the boat marker.

## Summary
- Navigation aids on the map overlay — the heading/speed cap arrow and the dashed direction line from the boat marker, plus auto-show of zones on approach.
- The arrow hides below 2.5 kn, draws straight up (heading-up rotation aligns heading with screen-up), and scales its length with speed and zoom from `maro.properties` keys.
- Auto-show drives memory-only overlay flows and never the user's persisted visibility setting, and the dashboard's shore pipeline reads `dashboardPositionFor(marker, boat, gpsMode)` so a dragged map cannot move it.
- Inside the 300 m band the dashboard's current zone is the band whatever the heading finds, and the look-ahead to a SHOM zone stops at land; the landward-approach case read OK on the device, the land stop awaits its pass.

## Sections

### cap

Heading/speed cap arrow + dashed direction line projecting from the boat marker.

#### Todos
- [ ] Re-verify build after restoring missing settings toggle rows

#### Rules
- Arrow hides below 2.5 kn (drifting threshold).
- Arrow length = speed(kn) × 2.17 dp, clamped to [1, 65] dp at REF_ZOOM, scaled with zoom by `map.marker.size.zoomExponent` — the constant this rule used to name is gone.
- Arrow always draws straight up (screen-top) — heading-up map rotation aligns heading with screen-up via `mapOrientation = -bearingDeg`.
- Direction line is a thin dashed line from screen center to top edge, its stroke width a setting and its dash proportional to that width rather than a fixed pair of pixels.
- Navigation state uses a single `NavigationState` data class for atomic Compose reads.
- Heading line (default ON) and Variable arrow (default OFF) toggled from Settings → Navigation, each with its own Appearance expander directly below its toggle.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — CenterMarkerOverlay + DirectionLine composables, settings UI
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — NavigationState data class, atomic StateFlow updates
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — directionLineColor field + loading
- `app/src/main/assets/maro.properties` — direction.line.color property
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — headingLineVisible, capArrowVisible in AppSettings

### auto-show

Auto-show / re-display of regulated zones and the 300 m band on approach, in GPS and demo mode.

#### Rules
- Auto-show drives memory-only overlay flows (`_zone300OverlayVisible` / `_regulatedZoneOverlayVisible`), never the user's persisted visibility setting.
- Demo-mode position = map centre (pan via `feedDemoPosition`); speed/heading = pan motion, cleared 500 ms after scroll stops.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — auto-show blocks (~610-680), `zoneAutoShowDecision` (1732)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — render OR gate (user setting OR overlay flow)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — auto-show settings fields/keys

#### Docs
- `xTrack/Navigation/FEAT_DOC_Navigation_auto-show.md` — consolidated auto-show reference (engine, settings refs, history)
- Settings owner: `xTrack/Ui_Settings/260902_FEAT_PLN_Ui_Settings_approach-redisplay.md`, `xTrack/Ui_Settings/260617_FEAT_PLN_Ui_Settings_auto-show-settings-decoupling-design.md`

## Implemented

- **zone look-ahead stops at land (2026-11-11, `feature/track-dash-arriving-not-updated`)** — a SHOM zone found ahead is dropped from `zonesAhead` when land lies on the line to it (`landCrossedBefore`, 10 m step, containment via `isWaterOrNull` so an unknown answer keeps the zone), so a zone behind a headland raises no amber; 6 new tests, 1 774 green, `apk-build` green, device pass owed (Ozers)
- **in-band zone from position (2026-11-11, `feature/track-dash-arriving-not-updated`)** — inside the 300 m band `currentZone` is always the band: with no exit along the heading (a landward harbour approach) it is `bandZoneExitUnknown` (exit infinitely far, `BeyondType.UNKNOWN`), so the speed-limit card and speed colouring show 5 kn instead of LIBRE; the dashboard's old early-return for that case removed as dead, and a SHOM boundary ahead no longer stands in for the band's exit. 4 new tests, `apk-build` green, read OK on the device. The bottom-left tag follows the map centre by design and is untouched
- **auto-show validation (2026-09-08)** — demo-mode auto-show investigated on feature/auto-show-zones (instrumented build + logcat, tag `AutoShow`): root cause was **no code defect** — the drawer "Auto-show zones" master override (`autoShowMasterOverride`) was OFF, so `globalEnabled` was false and auto-show was suppressed in both modes; demo SOG/cone/data all healthy. Stale demo-heading code comments corrected (`demoBearingDeg` never set — demo heading stays 0°/north; pan derives speed only). Auto-show knowledge consolidated → `xTrack/Navigation/FEAT_DOC_Navigation_auto-show.md`
- **dash distance (2026-07-04)** — unified distance tile: single-branch speed-limit compare, ETA for SHOM exit, shore-bound 300m gate, Lérins override
- **dashboard position source (2026-09-16, `feature/tracking-more`)** — the shore pipeline reads `dashboardPositionFor(marker, boat, gpsMode)`: the boat in GPS mode, the marker in demo, so a dragged map can no longer move the dashboard; `syncCenterFromBoat` stops the boat's own fixes grabbing the map centre while auto-follow is suppressed, and `recenterNow()` restores it in the same frame. Three cases covered by `DashboardPositionTest`; `apk-build.bat` SUCCESS

## Todos
- [ ] On-device visual verification of all navigation features

## Rules
- Suppressed auto-follow means the user owns the map centre: no fix may write it until the recenter button or the resume timer hands it back, and both restore it in the same frame (`syncCenterFromBoat`).
- The dashboard reads the boat in GPS mode (the marker in demo); the tag stack and the auto-reveal narrowing read the map centre, on purpose — never unify them. Reasons in `FEAT_DOC_Navigation_decisions.md`.
- Inside the 300 m band the dashboard's `currentZone` is the band, decided by position; the heading ray-march only adds the exit detail, and a missing exit never empties the zone.

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — shore pipeline, `zonesAhead`, `bandZoneExitUnknown`, `landCrossedBefore`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — distance and speed-limit cards reading `ZoneSituation`

## Docs
- `xTrack/Navigation/FEAT_DOC_Navigation_decisions.md` — which position feeds which surface, centre ownership, dead reckoning
