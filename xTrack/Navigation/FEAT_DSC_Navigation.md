---
name: Navigation
status: active
created: 2026-06-10 08:40
modified: 2026-09-08 16:49
---

# Feature: Navigation

**Description:**
Navigation aids on the map overlay — heading/speed indicator and direction line projecting from the boat marker.

## Sections

### cap

Heading/speed cap arrow + dashed direction line projecting from the boat marker.

#### Todos
- [ ] Re-verify build after restoring missing settings toggle rows

#### Rules
- Arrow hides below 2.5 kn (drifting threshold).
- Arrow length = speed(kn) × 2.17 dp, clamped to [1, 65] dp at REF_ZOOM, scaled with zoom via ZOOM_EXPONENT.
- Arrow always draws straight up (screen-top) — heading-up map rotation aligns heading with screen-up via `mapOrientation = -bearingDeg`.
- Direction line is a thin dashed line (12px dash / 6px gap, 1dp stroke) from screen center to top edge.
- Navigation state uses a single `NavigationState` data class for atomic Compose reads.
- Heading line (default ON) and Variable Arrow (default OFF) toggled from Settings → Display → Navigation.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — CenterMarkerOverlay + DirectionLine composables, settings UI
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — NavigationState data class, atomic StateFlow updates
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — directionLineColor field + loading
- `app/src/main/assets/maro.properties` — direction.line.color property
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — headingLineVisible, capArrowVisible in AppSettings

#### Docs
- `xTrack/Navigation/260610_FEAT_PLN_Navigation_cap-arrow.md` — original cap arrow design spec
- `xTrack/Navigation/260610_FEAT_PLN_Navigation_atomic-render.md` — atomic data flow + rendering analysis

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
- `xTrack/Navigation/260908_FEAT_PLN_Navigation_auto-show-demo-mode.md` — demo-mode auto-show validation + fix plan (current task)
- Settings owner: `xTrack/Ui_Settings/260902_FEAT_PLN_Ui_Settings_approach-redisplay.md`, `xTrack/Ui_Settings/260617_FEAT_PLN_Ui_Settings_auto-show-settings-decoupling-design.md`

## Implemented

- **auto-show validation (2026-09-08)** — demo-mode auto-show investigated on feature/auto-show-zones (instrumented build + logcat, tag `AutoShow`): root cause was **no code defect** — the drawer "Auto-show zones" master override (`autoShowMasterOverride`) was OFF, so `globalEnabled` was false and auto-show was suppressed in both modes; demo SOG/cone/data all healthy. Stale demo-heading code comments corrected (`demoBearingDeg` never set — demo heading stays 0°/north; pan derives speed only). Auto-show knowledge consolidated → `xTrack/Navigation/FEAT_DOC_Navigation_auto-show.md`; plan `xTrack/Navigation/260908_FEAT_PLN_Navigation_auto-show-demo-mode.md`
- **dash distance (2026-07-04)** — unified distance tile: single-branch speed-limit compare, ETA for SHOM exit, shore-bound 300m gate, Lérins override

## Todos
- [ ] On-device visual verification of all navigation features

## Rules

## Key Files

## Docs
