# Markers — Area Tap, Wizard Buttons, Corridor Caps & Color Settings

**Feature:** Markers
**Subfeature:** marker-overlay-improvements
**Status:** designed
**Created:** 2026-06-23

---

## 1. Area-Based Marker Tap

### Problem

Only the small pin-dot icons (OSMdroid `Marker.setOnMarkerClickListener`) are tappable.
Tapping the circle circumference, corridor lines, or proximity zone does nothing.
On a moving boat, hitting a 6dp dot is impractical.

### Design

**Tap area = per-marker proximity zone.** Uses the same formula as the match-resolution
engine ([`MarkerMatcher.proximityRange()`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:420)):

- If [`UserMarker.proximityOverrideM`](app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt:29) is set → use that exact value
- Otherwise compute from geometry + config:
  - Pin → `marker.proximity.pin_m` (200m default)
  - Circle → `radiusM × zone_multiplier` (× 3.0 default)
  - Corridor → `widthM × zone_multiplier` (× 3.0 default)

**Tap area by type** (using per-marker proximity range `R`):

| Type | Tap area |
|---|---|
| Pin | Haversine(tap, center) ≤ R |
| Circle | Haversine(tap, center) ≤ R (where R = override ?? radius × mult) |
| Corridor | Inside corridor polygon expanded to halfWidth = R/2 |

**Algorithm** — `MapEventsOverlay.onSingleTapConfirmedHelper`:

1. Convert screen tap → `GeoPoint`
2. For each confirmed marker, compute `closestPointOnGeometry(tap, marker)`:
   - **Pin:** Haversine distance to center; ∞ if > R
   - **Circle:** `max(0, Haversine(center, tap) − radius)` if inside zone R, else ∞
   - **Corridor:** project tap onto segment p1→p2, lateral distance; `max(0, lateral − halfWidth)` if inside expanded polygon
3. Select marker with minimum distance
4. Call `onMarkerTap(bestId)`

**Nearest-to-tap wins.** Ties broken by list order.

**Gesture pass-through:** `onSingleTapConfirmedHelper` returns `false` when no marker
is hit. The MapView's native gesture detector handles drag, rotate, and pinch
independently — `MapEventsOverlay` only intercepts confirmed single taps.

**Keep existing** `setOnMarkerClickListener` on pin dots as fast path — both dispatch
to the same `onMarkerTap` callback.

### Selected Marker Rendering

`MarkerOverlay` gains a `selectedMarkerId: String?` parameter. When non-null and
matches a marker:

- **Pin:** larger dot (10dp vs 6dp), brighter ring
- **Circle/Corridor:** thicker stroke (×2.0), distinct highlight color (`semanticInfo`
  at full alpha, no dimming)

Clears when wizard closes (`selectedMarkerId` → null).

### State Plumbing

- `MarkersViewModel` exposes `selectedMarkerId: StateFlow<String?>` — set on
  `openEditDrawer()`, cleared on wizard cancel/finish
- `MapScreen` passes `selectedMarkerId` from ViewModel to `MarkerOverlay`

### Files

| File | Change |
|---|---|
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) | + `selectedMarkerId` param, + `MapEventsOverlay` with hit-test, + closest-point functions, + selected rendering branch |
| [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | + `selectedMarkerId: MutableStateFlow<String?>` |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | pass `selectedMarkerId` to `MarkerOverlay` |

---

## 2. Wizard Buttons — Circle Icons → Text Pills

### Problem

The wizard navigation buttons ([`WizardButtonRow`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:293))
are 48dp circle icon buttons (←, →, ✓). The user wants text labels for clarity.

### Design

Replace with text pills styled identically to
[`SettingsLanguageRow`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3469):

```
┌──────────────────────────────────────────────────┐
│   ← Previous      Next →      ✓ Finish           │
│   [accent]        [accent]    [accent/dimmed]    │
└──────────────────────────────────────────────────┘
```

- Container: `RoundedCornerShape(12.dp)` Row, `uiSettingsCardBackground` background, 6dp padding
- Each pill: `Box` with `RoundedCornerShape(8.dp)`, `weight(1f)`, 10dp vertical padding
- Enabled: `uiSettingsAccent` background, bold white text (14sp)
- Hidden: spacer replacing Previous on first step, Next on last step
- Finish dimmed: `alpha(0.4f)` + `enabled = false` when `!canFinish`

Removes Material3 `Button` import from WizardDrawer (replaced by `Box` + `clickable`).

### Files

| File | Change |
|---|---|
| [`WizardDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt) | rewrite `WizardButtonRow`, remove `Button`/`ButtonDefaults` imports |

---

## 3. Marker Color Settings — Settings Page

### Problem

Marker rendering colors (confirmed blue, unconfirmed amber, proximity preview cyan)
are hardcoded in [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:44-50).
No user-facing configuration exists.

### Design

Add a collapsible **"Markers"** section in the main Settings page, following the
same pattern as BoatTrace's Tracking section (`SectionHeader` + expandable card).

| Setting | Default | `colors.properties` token |
|---|---|---|
| Confirmed marker color | `semanticInfo` blue | `marker.color.confirmed` |
| Unconfirmed marker color | `semanticCaution` amber | `marker.color.unconfirmed` |
| Proximity preview color | cyan 50% alpha | `marker.color.proximity` |

Each row is a color swatch + label, tappable to open the existing Canvas HSV
color picker (same as tracking color pickers). Persisted via `SettingsManager` →
`SharedPreferences`.

**Rendering:** `MarkerOverlay` reads colors from `SettingsManager`/`AppConfig`
instead of hardcoded constants. Falls back to current hardcoded values if not set.

### Files

| File | Change |
|---|---|
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | + Marker Settings section (collapsible, color pickers) |
| [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | + `markerColorConfirmed`, `markerColorUnconfirmed`, `markerColorProximity` fields |
| [`colors.properties`](app/src/main/assets/colors.properties) | + 3 marker color tokens |
| [`AppConfig.kt`](app/src/main/java/ykws/android/maro/config/AppConfig.kt) | + 3 marker color properties |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) | read colors from config instead of hardcoded constants |

---

## 4. Corridor Semi-Circle Caps

### Problem

Corridor renders as 2 parallel open polylines
([`addCorridorParallels`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:394)).
The ends are visually open — no closure at p1 or p2. Looks incomplete.

### Design

Add semi-circular arc polylines at each end of the corridor, forming a closed
pill/racetrack shape:

```
     \╭──────────────────────╮/
p1 ●  │    corridor band     │  ● p2
     /╰──────────────────────╯\
```

**Implementation:**

- At p1: sample a 180° semi-circle of radius `halfWidth` centered at p1,
  oriented perpendicular to the corridor bearing. Connect left edge → right edge.
- At p2: same semi-circle, reversed orientation.
- Arc sampling: ~18 points per semi-circle (same density as `CIRCLE_SAMPLES`/4).
- Proximity preview corridor gets the same semi-circle caps (dashed, preview color).

The existing centerline, parallel edges, and pin markers remain unchanged.

### Files

| File | Change |
|---|---|
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) | + `addCorridorCaps()` function, called from `addCorridorOverlay()`; arc sampling helper |

---

## Implementation Order

1. **Wizard buttons** — standalone UI change, no dependencies
2. **Corridor semi-circle caps** — standalone rendering change, no state dependencies
3. **Area tap + selected rendering** — hit-test functions, MapEventsOverlay, state plumbing, selected visuals
4. **Marker color settings** — Settings page + config plumbing, depends on MarkerOverlay reading from config
