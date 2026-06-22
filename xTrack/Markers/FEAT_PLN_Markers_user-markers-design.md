<!-- scope: feature -->
# User-Defined Markers — Sea-Distance-Gated Zones

> **Feature:** Markers | **Status:** Design — confirmed
> **Created:** 2026-06-22 | **Updated:** 2026-06-22

---

## 1. Concept

User places named markers on the map. Three types: **Pin** (point), **Circle** (point + radius), **Corridor** (2 points + width). Zones can englobe land (e.g., circle around Cap d'Antibes).

Matching is **on-demand**: user triggers "where am I?" → app finds all markers whose geometry is reachable via a sea path (no land crossing). Islands block same as mainland. 10m grazing tolerance for peninsula tips.

Two match levels per marker:
- **Zone match**: boat is geometrically inside the zone → always matches (no land check)
- **Proximity match**: boat is outside but within derived range of the closest unblocked point on the zone boundary

Overlapping zones are allowed and displayed as a nested hierarchy.

### Example

```
Circle R=500m around Cap d'Antibes (englobes land):
  Boat west of cape, 300m from circle boundary → proximity match via western arc
  Boat east of cape, inside circle → zone match (geometric)
  Boat south, 2km away → no match (too far)
```

---

## 2. Marker Types

| Type | Definition | Zone Match | Proximity Match |
|------|-----------|------------|-----------------|
| **Pin** | point only | Never | ≤ 200m from pin, sea path clear |
| **Circle** | center + radius | Boat inside radius (geometric) | ≤ radius×3 from closest unblocked boundary point |
| **Corridor** | 2 points + width, rounded ends | Boat inside corridor (geometric) | ≤ width×3 from closest unblocked boundary point |

Proximity ranges are defaults — configurable in `maro.properties` and overridable per marker.

### 2.1 Corridor end caps

Rounded — being within `width/2` of p1 or p2 counts as inside the corridor, even if the perpendicular projection falls past the segment end.

```
Corridor p1 ●━━━━━━━━━━● p2, width 300m

   Boat A: perpendicular to centerline, 100m away → ✓ (100 < 150)
   Boat B: past p2, 100m from p2 → ✓ (100 < 150, rounded cap)
```

---

## 3. Match Resolution — On-Demand, Tiered Display

### 3.1 On-demand only

Matching is **not continuous**. No 1Hz pipeline. The user triggers a "where am I?" query manually. The app runs the land-blocking + proximity check across all markers and returns a tiered result.

This means:
- No real-time performance constraint (user-triggered, < 500ms is fine)
- No live color-coded marker states on the map overlay
- Markers always render in neutral/default style
- Match result displayed in a panel/overlay when requested

### 3.2 Tiered display — all matches shown

Instead of picking a single winner, **all matching markers are displayed**, nested by precision:

```
📍 Plongée de la Tradelière (pin) — 30m
   └─ Plateau du Milieu (circle, R=200m) — inside zone
      └─ Îles de Lérins (corridor, W=1000m) — inside zone
```

Precision order (most → least precise):
1. **Pin** (closest first by distance)
2. **Circle** (smallest radius first)
3. **Corridor** (narrowest width first; equal width → shorter length first)

Within each tier, markers are sorted by precision metric. The hierarchy is spatial containment: if a pin is inside a circle zone, it nests under that zone.

### 3.3 Match levels per marker

| Level | Condition |
|-------|-----------|
| **Zone match** | Boat inside the defined geometry — purely geometric, no land check |
| **Proximity match** | Boat outside zone but closest unblocked boundary point ≤ proximity range |
| **No match** | Too far away or land blocks all paths to boundary |

---

## 4. Land-Blocking Algorithm

### 4.1 Core concept

Zones can englobe land (e.g., circle around Cap d'Antibes). The match distance is measured from the **closest point on the marker geometry that has a clear sea line-of-sight** to the boat — not from the marker center.

### 4.2 Finding the closest unblocked point

For a given marker, sample candidate points on its geometry boundary. For each candidate, test if `boat → candidate` crosses land. Pick the closest unblocked candidate.

```
closestUnblockedPoint(boat, marker, coastline) → LatLng?:
    candidates = sampleGeometry(marker)
    best = null, bestDist = ∞
    for each P in candidates:
        if !segmentIntersectsLand(boat, P, coastline):
            d = distanceM(boat, P)
            if d < bestDist: best = P, bestDist = d
    return best  // null → entirely blocked
```

### 4.3 Sampling strategy

| Type | Candidates | Count |
|------|-----------|-------|
| Pin | `position` | 1 |
| Circle | `center + bearing(angle) × radiusM` for angle in 0..350 step 10° | 36 |
| Corridor | N evenly-spaced points along centerline + p1 + p2 | ~20 |

10° step chosen because: max distance error at R=500m is ~1.9m — below GPS accuracy (~3-5m). 5° would halve the error to ~0.5m but double the worst-case cost, with no practical benefit.

### 4.4 Segment-land intersection test

```
segmentIntersectsLand(A, B, coastline) → Boolean:
    for each edge E in coastline:
        if segmentsIntersect(A, B, E.start, E.end):
            I = intersectionPoint(A, B, E.start, E.end)
            if distance(I, E.start) > 10m AND distance(I, E.end) > 10m:
                return true  // non-grazing intersection → blocked
    return false
```

### 4.5 Grazing tolerance

An intersection is "grazing" (ignored) if within **10m** of a coastline vertex. This handles peninsula tips. Chunks of land (intersection far from both endpoints of the crossed edge) always block.

### 4.6 Edge cases

| Case | Behavior |
|------|----------|
| Boat at marker (≤1m) | Skip land check — always match |
| Zone englobes land (Cap d'Antibes circle) | Works — unblocked arcs on circle boundary match from reachable sides |
| Segment clips peninsula tip ≤10m | Grazing → allowed |
| Segment crosses island | Blocked (islands = land) |
| Segment crosses mainland chunk | Blocked |
| Segment entirely over water | Allowed |
| All 36 circle candidates blocked | Marker unreachable from this position → NoMatch |

---

## 5. Data Model

```kotlin
data class UserMarker(
    val id: String,                        // UUID
    val name: String,                      // "Phare de la Fourmigue"
    val geometry: MarkerGeometry,
    val description: String = "",          // optional rule/note
    val proximityOverrideM: Double? = null // null → use default formula
)

sealed class MarkerGeometry {
    /** Point-only marker — no zone, proximity info only. */
    data class Pin(
        val position: LatLng
    ) : MarkerGeometry()

    /** Circular zone around a center point. */
    data class Circle(
        val center: LatLng,
        val radiusM: Double                // > 0
    ) : MarkerGeometry()

    /** Corridor between two points with width. Rounded end caps. */
    data class Corridor(
        val p1: LatLng,
        val p2: LatLng,
        val widthM: Double                 // > 0
    ) : MarkerGeometry()
}
```

### 5.1 Proximity range — default formula + optional override

```
proximityRange(marker, config):
    if marker.proximityOverrideM != null:
        return marker.proximityOverrideM
    when (marker.geometry):
        Pin      → config.markerProximityPinM
        Circle   → geometry.radiusM × config.markerProximityZoneMultiplier
        Corridor → geometry.widthM  × config.markerProximityZoneMultiplier
```

Defaults from `maro.properties`:

```properties
marker.proximity.pin_m=200
marker.proximity.zone_multiplier=3.0
```

| Marker | Default proximity |
|--------|-------------------|
| Pin | 200m |
| Circle R=500m | 1500m (500 × 3) |
| Circle R=2000m | 6000m (2000 × 3) |
| Corridor W=300m | 900m (300 × 3) |

User can override per marker in the edit dialog.

### 5.2 Marker-to-boat distance definition

Distance is measured from the **closest point on the marker geometry boundary that has a clear sea path** to the boat — not from the marker center. See §4 for the search algorithm.

| Geometry | "Closest unblocked point" search domain |
|----------|----------------------------------------|
| `Pin` | The pin position itself (single point) |
| `Circle` | 36 samples on circle boundary at 10° steps |
| `Corridor` | ~20 samples along centerline + both endpoints |

---

## 6. Match Resolution Algorithm (On-Demand)

### 6.1 Entry point

```
resolveAllMarkers(boat: LatLng, markers: List<UserMarker>, coastline: CoastlineData)
    → TieredMatchResult
```

Called once when user triggers "where am I?" — not in a continuous pipeline.

### 6.2 Single-marker match

```
resolveMatch(boat, marker, coastline, config) → MatchResult:
    1. Zone check (purely geometric, no land test):
       If boat inside geometry → ZoneMatch
    
    2. Compute proximity range:
       R = proximityRange(marker, config)  // override or formula
    
    3. Find closest unblocked point P on geometry boundary:
       P = closestUnblockedPoint(boat, marker, coastline)
       if P == null → NoMatch (land blocks entirely)
    
    4. D = distanceM(boat, P)
       If D ≤ R → ProximityMatch(D)
       Else → NoMatch
```

Key: Zone match is purely geometric — if the boat is inside the circle or corridor, it matches regardless of land. Land-blocking only gates proximity (outside the zone, trying to reach it).

### 6.3 Inside-geometry check

| Geometry | Boat is inside if |
|----------|-------------------|
| Pin | Never (radius = 0) |
| Circle | `distanceM(boat, center) ≤ radiusM` |
| Corridor | `distanceToSegment(boat, p1, p2) ≤ widthM/2` OR `distanceM(boat, p1) ≤ widthM/2` OR `distanceM(boat, p2) ≤ widthM/2` |

Note: inside-geometry check is purely geometric (no land test). The land test is on the segment to the closest unblocked boundary point. A boat can be geometrically inside a circle but have its closest boundary point blocked → still gets a ZoneMatch (inside the zone), just with a land-adjusted distance.

### 6.4 Tiered result assembly

```
resolveAllMarkers(boat, markers, coastline):
    results = []
    for each marker:
        match = resolveMatch(boat, marker, coastline)
        if match != NoMatch → results.add(match)
    
    // Sort by precision (most precise first)
    results.sortBy:
        1. Pin: distance (ascending)
        2. Circle: radiusM (ascending), then distance
        3. Corridor: widthM (ascending), then length (ascending), then distance
    
    // Nest by spatial containment
    for each result (outer to inner):
        for each more-precise result:
            if morePrecise is inside outer's geometry:
                nest morePrecise under outer
    
    return TieredMatchResult(tiers)
```

### 6.5 Performance

| Operation | Worst case | Expected |
|-----------|-----------|----------|
| Per circle marker (36 samples) | 36 × 1000 = 36K edge tests | ~18K (early exit) |
| Per corridor marker (~20 samples) | 20 × 1000 = 20K edge tests | ~10K |
| Per pin marker (1 sample) | 1000 edge tests | ~500 |
| Total 20 markers (mix) | ~600K edge tests | ~300K |
| Wall time (ARM) | **< 30ms** | **< 15ms** |

---

## 7. Persistence

- JSON file: `Internal storage/markers/user_markers.json`
- Loaded at startup, written on every create/edit/delete
- 15-20 markers max → JSON is fine, no need for protobuf
- **No export/import** — markers are device-local

---

## 8. UI Integration

### 8.1 Layer architecture

User markers render as a map layer **below** the boat marker and heading/speed arrow:

```
Map rendering stack (bottom → top):
  Depth tiles → Coastline → Regulated zones → User markers → Boat/Arrow → Overlays
```

- **Fan button**: pin icon in FanLayout, toggles `userMarkersVisible`
- **Settings section**: marker list management in Settings
- **Always neutral rendering** — no live color-coding (matching is on-demand)

### 8.2 Marker rendering

| Type | Visual |
|------|--------|
| Pin | Filled dot, name label |
| Circle | Dashed circle at radius, filled dot at center, name label |
| Corridor | Two parallel dashed lines at ±width/2, centerline, name label at midpoint |

Pin dots at circle centers and corridor endpoints.

### 8.3 "Add Pin" button

Button below FanLayout buttons. Behavior:

1. If layer hidden → auto-show layer
2. Drops **unconfirmed pin at map center** (orange/yellow, distinct from confirmed color)
3. Opens bottom confirmation panel

### 8.4 Placement UX (before confirmation)

While the bottom panel is open and marker is unconfirmed:
- Pin stays locked at **map center**
- User **pans the map** to position the pin at desired location
- Pin renders in unconfirmed color
- For Circle: dashed preview circle shown at specified radius
- For Corridor: after setting 2nd point, **temporary dashed line** drawn between p1 and p2 (placement guide only — NOT part of final zone rendering)

### 8.5 Creation / match result drawer

Animated drawer covering the dashboard area. Responsive direction:

| Orientation | Direction | Behavior |
|-------------|-----------|----------|
| Portrait | Slides up from bottom | Covers dashboard; map stays visible above |
| Landscape | Slides from left | Uses horizontal space; preserves map |

Same drawer is reused for both **creation/editing** and **match results** (on boat tap).

**Creation mode:**

```
┌─────────────────────────────────┐
│  Name: [_____________________]  │
│                                 │
│  Type:                          │
│  ○ Pin        (point only)      │
│  ○ Circle     radius: [___] m   │
│  ○ Corridor   [Set 2nd point]   │
│                                 │
│  Proximity:  auto (200m)  [___] │
│  Description: [_____________]   │
│                                 │
│       [Cancel]    [Save]        │
└─────────────────────────────────┘
```

- Circle: radius field appears when selected; dashed preview circle on map
- Corridor: "Set 2nd point" → drawer minimizes, map enters 2nd-point mode (pin at center, temporary dashed line from p1 to p2), user pans, confirms → drawer returns
- Proximity: shows computed default (pin=200m, circle=radius×3, corridor=width×3) with optional override
- Cancel → preview removed, nothing saved, drawer closes
- Save → marker confirmed, final color, drawer closes

**Match result mode:**

```
┌─────────────────────────────────┐
│  📍 Plongée de la Tradelière    │
│     pin — 30 m                  │
│    └─ Plateau du Milieu         │
│       circle 200m — inside zone │
│       └─ Îles de Lérins        │
│          corridor 1000m         │
│          — inside zone          │
└─────────────────────────────────┘
```

If no matches: "No markers nearby" or "Land blocks all markers."

### 8.7 "Where am I?" trigger

**Tap the boat marker** (`CenterMarkerOverlay`) → fires `resolveAllMarkers()` at current boat position → same drawer opens in match result mode.

No extra UI element needed. Boat marker currently has no click handler — clean addition.

### 8.8 Menu integration

```
Hamburger drawer:
  ┌──────────────────────────┐
  │ TRACK RECORDING          │
  │  ● Start/Stop Recording  │
  │  ● Track List            │
  ├──────────────────────────┤
  │ MARKERS                  │  ← NEW section
  │  ● Manage Markers        │  → opens management page
  └──────────────────────────┘
```

### 8.9 Management page

Full list of all markers. **Delete uses the exact same paradigm as track list** (per [`FEAT_PLN_BoatTrace_TrackList_Design.md`](xTrack/BoatTrace/FEAT_PLN_BoatTrace_TrackList_Design.md)):

| Behavior | Detail |
|----------|--------|
| Swipe left | Card slides out left, snackbar slides in right (inline) |
| Snackbar | `"{MarkerName}" deleted` + Undo button |
| Swipe snackbar | Permanent delete |
| Panel close | Pending deletes commit |
| Undo | Card re-appears from opposite direction |
| Tap item | Opens edit panel pre-filled |
| Visibility | **Layer toggle only** — no per-marker eye icon |

---

## 9. Integration Points

| Component | Role |
|-----------|------|
| `CoastlineViewModel` / `CoastlineData` | Provides coastline edges for land-blocking test |
| `MapScreen` map overlay | Renders marker geometries (neutral, below boat) |
| `CenterMarkerOverlay` | Tap target for "where am I?" query |
| FanLayout | Pin icon toggle for `userMarkersVisible` |
| "Add Pin" button | Below FanLayout, creates marker at map center |
| Bottom confirmation panel | Name, type, radius, proximity override |
| Match result panel | Tiered match display on boat-marker tap |
| Hamburger drawer | MARKERS section → Manage Markers |
| Management page | List with swipe-to-delete, inline snackbar undo |
| Settings | Marker list management (view, edit, delete) |

### 9.1 Non-integrations (explicitly excluded)

| System | Decision |
|--------|----------|
| Continuous 1Hz pipeline | **No** — matching is on-demand |
| Auto-show / warning strip | **No** — user markers are independent |
| Ahead-cone / ahead-line | **No** — user markers are separate from regulated zones |
| Export/import | **No** — device-local only |
| Per-marker visibility toggle | **No** — layer toggle only |
| Live color-coded rendering | **No** — markers always neutral |

---

## 10. Performance

On-demand query — see §6.5 for detailed breakdown. Summary:

| Metric | Value |
|--------|-------|
| Per circle marker (36 samples × 1000 edges) | 36K edge tests worst, ~18K avg |
| 20 markers mixed (circles + corridors + pins) | ~600K edge tests worst, ~300K avg |
| Wall time (ARM) | **< 30ms** worst, **< 15ms** typical |
| User perception | Instant — single button press

---

## 11. Implementation Phases

### Phase A — Data model + persistence
- [ ] `UserMarker` data class + `MarkerGeometry` sealed class (Pin, Circle, Corridor)
- [ ] `UserMarkerRepository` — JSON load/save, CRUD
- [ ] Unit tests for JSON round-trip

### Phase B — Land-blocking engine
- [ ] `segmentIntersectsLand()` — segment vs coastline edges
- [ ] Grazing tolerance (10m vertex proximity)
- [ ] `isBlockedByLand()` — combines intersection test with tolerance
- [ ] Unit tests with synthetic coastline + real Cap d'Antibes scenario

### Phase C — Match resolution
- [ ] `resolveMatch(boat, marker, coastline, config)` → single `MatchResult`
- [ ] `resolveAllMarkers(boat, markers, coastline, config)` → `TieredMatchResult`
- [ ] Zone match: purely geometric inside-geometry check (no land test)
- [ ] Proximity range: override or formula (pin=200m, circle=radius×3, corridor=width×3)
- [ ] Load `marker.proximity.pin_m` and `marker.proximity.zone_multiplier` from maro.properties
- [ ] `closestUnblockedPoint()` — 10° sampling + land-intersection test
- [ ] Precision sort: Pin (distance) > Circle (radius) > Corridor (width→length)
- [ ] Spatial nesting for tiered display
- [ ] Corridor rounded-cap containment check
- [ ] Unit tests: single match, multi-match, nesting, land-blocked, grazing, override

### Phase D — Map overlay rendering (neutral, below boat)
- [ ] Pin/Circle/Corridor polyline/Canvas drawing as map layer
- [ ] Renders below boat marker and heading/speed arrow in Z-order
- [ ] Pin dots at circle centers and corridor endpoints
- [ ] Always neutral style — no live match coloring
- [ ] Name labels
- [ ] Unconfirmed marker color (orange/yellow) during placement

### Phase E — Creation/editing UI
- [ ] FanLayout pin icon toggle for `userMarkersVisible`
- [ ] "Add Pin" button below FanLayout
- [ ] Map-center placement: unconfirmed pin at center, user pans map
- [ ] Animated drawer (portrait: bottom; landscape: left) covering dashboard area
- [ ] Creation mode: name, type selector (Pin/Circle/Corridor), radius, proximity override
- [ ] Circle: radius field + dashed preview circle during placement
- [ ] Corridor: 2nd-point mode with temporary dashed line between p1 and p2
- [ ] Match result mode: tiered display, nested by precision
- [ ] Edit mode: same drawer pre-filled, + delete button
- [ ] Hamburger drawer MARKERS section → "Manage Markers"

### Phase F — "Where am I?" + management page
- [ ] `CenterMarkerOverlay` clickable → fires `resolveAllMarkers()` at boat position
- [ ] Opens drawer in match result mode
- [ ] Handle "no matches" and "land blocked" states
- [ ] Management page: LazyColumn with swipe-to-delete (track list paradigm)
- [ ] Inline snackbar undo, permanent delete on snackbar swipe
- [ ] Tap item → opens drawer in edit mode
- [ ] Layer toggle only — no per-marker visibility
