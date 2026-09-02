---
feature: Ui_Settings
topic: approach re-display — settings reorg + per-zone proximity reveal
status: planned
created: 2026-09-02 17:21
---

# Approach Re-display — plan (rev 2)

## Decisions
- Speed zones ARE wired: a speed zone = a regulated zone with speed-limit enforcement, already extracted by SpeedZoneBuilder and indexed by SpeedZoneIndex.
- Three re-display types: 300 m band, Speed zones (speed-limit subset), Regulated zones (non-speed categories).
- Global mode pair (GPS / Demo) + 3 per-type switches + 2 shared always-visible knobs.
- Drawer: rename "Position source" -> "Position mode"; add persisted master "Auto-show zones" override switch, visible only when the feature is enabled in Settings for the current mode.

## UI
```
RE-DISPLAY ON APPROACH
  Enable in   [x] GPS mode   [x] Demo mode
  [x] 300 m zone
  [x] Speed zones
  [x] Regulated zones
  Reveal distance    50–500 m   (always visible)
  Reveal time         5–120 s   (always visible)
```

## Data model (SettingsManager.kt)
Remove: zone300AutoShowGps/Demo, speedZoneAutoShowGps/Demo, regulatedZoneAutoShowGps/Demo, speedZonesVisible (dead overlay flag).
Add: approachAutoShowGps = true, approachAutoShowDemo = true, zone300AutoShow = true, speedZoneAutoShow = true, regulatedZoneAutoShow = true, autoShowMasterOverride = true (persisted).
Keep zoneAutoRevealDistanceM / zoneAutoRevealTimeS. Migration CURRENT_VERSION 5 -> 6.

## Logic (NavigationViewModel.kt)
- global = (if (gpsMode) approachAutoShowGps else approachAutoShowDemo) && autoShowMasterOverride.
- Band auto-show gated by global && zone300AutoShow.
- Speed/regulated auto-show gated by global && (speedZoneAutoShow || regulatedZoneAutoShow); revealed subset split speed vs non-speed by category/speedLimitKn.

## Drawer (MenuDrawerOverlay.kt)
- Rename section "Position source" -> "Position mode" (menu_section_position).
- Add "Auto-show zones" master switch below the GPS toggle; visible only when global enable for the current mode is true; toggles autoShowMasterOverride.

## Render (Phase 2)
- User-visible: full list (existing filterRegulatedZones).
- Auto-shown: proximity-filtered subset (bbox prefilter + boundary test), split by speed/non-speed per switches.
- Proximity helper in spatial package; reuse SpeedZoneIndex for speed zones.

## Files
- SettingsManager.kt, NavigationViewModel.kt, MapScreen.kt, MenuDrawerOverlay.kt, spatial/, strings.xml (en/fr).

## Decision — inside behavior
- Band (300 m) + speed zones: hide when inside AND speed-compliant (hideOnCompliantInside = true).
- Regulated zones (non-speed): stay visible while inside; hide on exit with hysteresis.

## Directional distance — unify (taxonomy)
Two primitives, parameterized by ZoneKind (BAND_300 | SPEED | REGULATED):
- zoneStatus(origin) -> ZoneStatus { inside, nearestBoundaryM }  — replaces SpeedZoneIndex.query.
- boundaryInCone(origin, headingDeg, halfAngleDeg, maxM) -> BoundaryHit? { zoneId, kind, distanceM }
  — replaces firstSpeedZoneAhead + distanceTo300mAlongHeading + findBandExitAlongHeading; halfAngle 0 = laser, ~30deg = forward cone.
Consumers (composed): dashboardExit = boundaryInCone(current kind); zonesAround = boundaryInCone + zoneStatus;
approachTrigger = boundaryInCone (distance/time) + zoneStatus.inside (hide/compliance).
Remove cosine-projection in infoToZoneExitAlongHeading; band keeps one private ray-march helper.
Reveal trigger: distance OR time (along-travel distance / SOG), first match.

## Multi-zone semantics
- boundaryInCone returns the NEAREST hit in the cone (first wall wins).
- ZoneStatus = { insideAny, nearestBoundaryM, insideZones, strictestSpeedKn } — inside 2+ speed zones, strictest limit governs compliant-hide.
- Per-type decisions are independent and OR'd into the overlay flag; render splits which zones draw.
- Classification (both buckets): a zone shows if (isSpeed && speedZoneAutoShow) OR (isNonSpeed && regulatedZoneAutoShow);
  a dual-category zone (speed + no-anchor etc.) appears under whichever switch is on. isSpeed = speedLimitKn != null;
  isNonSpeed = displayCategories contains any category other than SPEED_LIMIT.

## Junior-engineer handoff — technical + functional

### New types
- enum ZoneKind { BAND_300, SPEED, REGULATED }
- data class ZoneStatus(val insideAny: Boolean, val nearestBoundaryM: Double?, val insideZones: List<String>, val strictestSpeedKn: Double?)
- data class BoundaryHit(val zoneId: String, val kind: ZoneKind, val distanceM: Double)

### Primitives (roles + params)
1. zoneStatus(origin: LatLng): ZoneStatus
   - Role: where am I relative to a zone kind (no direction). inside-any + nearest edge + all-inside + strictest speed.
   - Implementations: BAND_300 (coastline-derived), SPEED (index), REGULATED (index).
2. boundaryInCone(origin: LatLng, headingDeg: Double, halfAngleDeg: Double, maxM: Double): BoundaryHit?
   - Role: first zone wall inside the forward cone (nearest hit wins). halfAngle 0 = laser, ~30 = forward cone.
   - Implementations: BAND_300 (one merged ray-march for entry+exit), SPEED + REGULATED (shared edge ray-cast).
3. Generalize SpeedZoneIndex grid + point-query + ray-cast into a reusable class for SPEED and REGULATED.

### Removed / renamed
- SpeedZoneIndex.query() -> zoneStatus (SPEED)
- firstSpeedZoneAhead() -> boundaryInCone (SPEED, halfAngle=0)
- distanceTo300mAlongHeading + findBandExitAlongHeading -> one BAND_300 boundaryInCone impl
- infoToZoneExitAlongHeading cosine projection -> removed (use boundaryInCone for current kind)

### Consumers (composed)
- dashboardExit(kind) = boundaryInCone(current kind)
- zonesAround(kind) = boundaryInCone (ahead) + zoneStatus (radial)
- approachTrigger(kind) = boundaryInCone (distance/time) + zoneStatus.insideAny (hide/compliance)

### Functional — dashboard (legacy)
- Exit ETA now via boundaryInCone (directional, more accurate) instead of cosine projection. No UI change; no regression to list/arrows.

### Functional — auto-show (new)
- Reveal: boundaryInCone distance <= Reveal distance OR distance/SOG <= Reveal time (first match).
- Hide: BAND_300 + SPEED hide when inside+compliant (strictest limit); REGULATED stays while inside, hides on exit.
- Per-type independent, OR'd into overlay flag; render draws both buckets per switches.
- Master override gates everything and must clear the overlay when toggled off.

### UI
- Settings "Auto-show zones": Enable GPS / Demo, 300 m / Speed / Regulated, collapsible "When to reveal" (distance + time), collapsed by default, rememberSaveable.
- Drawer "Position mode": "Auto-show zones" master switch (accent blue when on), visible only when enabled for current mode.
