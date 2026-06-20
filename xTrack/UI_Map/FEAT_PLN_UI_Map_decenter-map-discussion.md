# Decenter Map — Dynamic Downward Offset of the Map Centre

## The Concept

Shift the boat position (currently dead-centre of the map) downward so the boat sits in the **lower third** of the viewport. This gives the user proportionally more screen area looking **ahead** of the boat — matching the UX convention of car navigation apps (Google Maps, Waze) and marine chartplotters.

The offset should be **dynamic**: applied only when the boat is moving (GPS mode with meaningful speed) and the heading is known. When stationary, in demo mode, or when heading is unknown, the boat should stay centred.

## Current Architecture

```
MapScreen (Box.fillMaxSize)
  └── BoxWithConstraints (fillMaxSize)
       └── MapContent (Box modifier.clipToBounds)
            ├── CoastlineMapView (Modifier.fillMaxSize)   ← osmdroid MapView
            ├── DirectionLine (Modifier.fillMaxSize)
            ├── CenterMarkerOverlay (Modifier.fillMaxSize)
            └── Row (Modifier.fillMaxSize)                 ← overlay controls
                 ├── Column (weight=1f)                    ← LEFT: GPS, dash, etc.
                 └── Column                                ← RIGHT: control stack
```

### How osmdroid Controls the Map Centre

The map centre is set via **two mechanisms**:

1. **`mv.controller.setCenter(GeoPoint)`** — absolute positioning (used at init, line 1183)
2. **`mv.controller.animateTo(point)`** — smooth animated targeting (used in GPS auto-follow, lines 324/332)

Both shift the entire map such that the given `GeoPoint` appears at the **pixel-centre** of the `MapView`. There is **no native osmdroid API** to apply a fixed pixel offset to the centre point — the centre IS the centre.

## Approach Options

### Option A: Compose-Level Canvas Offset (Shifting the MapView)

Since `MapView` is embedded via `AndroidView`, and the parent `MapContent` uses `Box(clipToBounds = true)`, the map canvas itself could be scrolled/shifted via a **`Modifier.offset(y)`** applied to the `CoastlineMapView` composable.

- Apply a negative `y` offset to `CoastlineMapView` so the map surface extends **below** the visible clip area of `MapContent`.
- The boat position (set via osmdroid as map centre) visually moves **up** relative to the visible frame — which is the opposite of what we want.
- You'd actually want a **positive** `y` offset — shift the MapView DOWN so the actual centre is below the visual area, making the boat appear in the lower portion of the visible clip.

**Problem:** This would clip the top of the map. The map tiles above the visible area would be cut off. You could compensate by expanding the MapView size upward, but `AndroidView` sizing is determined by its `Modifier` — you can't easily make it larger than its parent clip area.

### Option B: osmdroid `mapOffset` / Projection Shift

osmdroid `MapView` has an internal `mapOffset` concept (used in the `MapController` for animation targeting). Looking at the osmdroid source:

- `MapView.controller.animateTo(GeoPoint, Long, Boolean)` doesn't support an offset.
- However, osmdroid's `BoundingBox` and `Projection` classes can convert between pixel and geo coordinates.

One could **manually compute** a new centre point:

```
Given:  boat at (lat, lon), desired offset = 30% screen height downward
1. Convert boat (lat, lon) to screen pixel (px, py) via projection
2. Compute new pixel centre: (px, py - 0.3 * screenHeight)
3. Convert (px, py - 0.3 * screenHeight) back to geo → newCentre
4. Call mv.controller.setCenter(newCentre)
```

This is applied **every frame** during GPS auto-follow — on every `cameraUpdates` emission (throttled to `mapRefreshFps`). The boat's geo-position in `CenterMarkerOverlay` stays the same; only the map scroll target changes.

**Advantage:** No clipping, no layout changes. Pure math on osmdroid's projection.

**Disadvantage:** Must re-calculate on every frame AND on every zoom/rotation change. The `animateTo` smooth-glide effect would need adjustment — jerky if we fight the animation with a new target each tick.

### Option C: Compose-Level Decenter via Layout Padding / Inset

Instead of modifying the map position, modify the **visible viewport** of the `MapContent` box itself:

```
MapContent(
    modifier = Modifier
        .fillMaxSize()
        .padding(top = someFraction * height)   // push map content down
)
```

This would cause the `MapView` (which fills `Modifier.fillMaxSize()`) to render smaller — the entire map surface is shifted down. The boat centre (set via osmdroid) would remain at the **visual centre of the smaller MapView**, not the overall screen.

**Problem:** osmdroid's `MapView` would report its visible area as the reduced-size rect, so the map centre (which osmdroid sets as the centre of its visible area) would end up higher than intended. You'd need to **recalculate** the `setCenter` target so the boat ends up at the right geo-position despite the offset. This doubles up the complexity of Option B with layout complications.

### Option D: Scrolling the MapView within a Larger Parent

Make the `CoastlineMapView` larger than its parent `MapContent` box (e.g., 100% width × 130% height), position it so the extra height extends **downward** off-screen, then use osmdroid's `scrollTo` or `setCenter` with a compensated target.

The MapView would render tiles in the overscroll area, which osmdroid may or may not support well. Tile loading penalises performance.

## Recommendation

**Option B (projection-shifted centre) is the cleanest path forward for discussion.** Here's why:

1. **No layout changes** — `MapContent`, `CoastlineMapView`, and all overlay controls remain in their current positions.
2. **No clipping** — the full map is visible, just focused on a different geo-point.
3. **Reversible per-frame** — when the boat stops, the offset can smoothly animate back to 0.
4. **Only affects the auto-follow path** — manual panning, demo mode panning, and rotation are unaffected (or opt-in).

### Key Challenges

| Challenge | Mitigation |
|---|---|
| **Smooth transitions** | When offset changes (boat starts/stops), animate the offset value with a tween rather than snapping. The `animateTo` target would be gradually adjusted. |
| **Zoom changes** | Offset in pixels = constant screen fraction regardless of zoom. The geo-space equivalent changes with zoom, so re-compute `setCenter` target on every zoom change. |
| **Rotation** | When map is rotated (heading-up), "downward" in screen space is still toward the bottom of the device — no special handling needed. |
| **Overlay positions** | Dashboard and control overlays are in a separate `Row(modifier = Modifier.fillMaxSize())` that sits ON TOP of the map. They would NOT move with the map — the user sees the map content shifted but the controls stay fixed. This is the correct UX. |
| **Boat marker** | `CenterMarkerOverlay` draws at the pixel-centre of its `Canvas` (which fills `Modifier.fillMaxSize()`). This will still be at **screen centre**, but the map geo-centre has been shifted DOWNWARD. This means the boat marker appears above the map's actual geo-centre — which is EXACTLY what we want (boat at bottom of screen, map shows area ahead). |

Wait — this is a critical subtlety. Let me re-examine.

### The Boat Marker Position

The `CenterMarkerOverlay` composable:

```kotlin
Box(modifier = modifier.size(finalSizeDp)) {
    // boat marker composable drawn at the box centre
}
```

This composable is drawn inside `MapContent`, in a `Box(modifier = Modifier.fillMaxSize())` — so it draws at the **screen centre** of the visible area. If we use Option B (shift the map's geo-centre downward), the boat marker stays at **screen-centre** position, while the map has been shifted so that screen-centre now corresponds to a geo-point FURTHER AHEAD of the boat.

**This is the opposite of what we want.** We want:
- Boat icon at **lower third** of screen
- Map shows area ahead of boat in the upper two-thirds

With Option B alone, the boat icon would stay at screen-centre, and the geo-point at screen-centre would be the shifted-ahead point. We'd need to ALSO move the `CenterMarkerOverlay` composable downward by the same offset fraction.

### Revised Plan: Dual Offset

1. **Map offset** — adjust the `setCenter`/`animateTo` target so the geo-point corresponding to the boat's actual position is at the **lower third** of the screen (offset upward in geo-space).
2. **Marker offset** — shift the `CenterMarkerOverlay` composable DOWN by the same fraction so the boat icon aligns with its true geo-position.

The overlay icons (Dashboard, controls) stay fixed. The map content shifts, the boat marker shifts with the map content, and the user sees:

```
┌──────────────────────┐
│  open water ahead    │
│  (sea / hazards)     │
│                      │
│              ⛵       │ ← boat at lower third
│  ┌────────────┐      │
│  │ Dashboard  │      │ ← fixed overlay
│  └────────────┘      │
└──────────────────────┘
```

## Implementation Sketch

```
In MapScreen.kt, LaunchedEffect for GPS auto-follow (line ~314):

1. Compute offsetFraction (0.0 = centred, 0.25 = boat at lower quarter)
   - Dynamic: proportional to speed, capped at 0.3
   - 0 when stationary, demo mode, or heading unknown

2. Convert boat (lat, lon) → screen pixel via projection
   val proj = mv.projection
   val boatScreen = proj.toPixels(GeoPoint(boatLat, boatLon), null)
   val offsetPx = offsetFraction * mv.height
   val targetPixel = Point(boatScreen.x, boatScreen.y - offsetPx)  // up in screen = ahead
   val targetGeo = proj.fromPixels(targetPixel.x, targetPixel.y)

3. mv.controller.setCenter(targetGeo)   // or animateTo(targetGeo)

4. Pass offsetFraction to CenterMarkerOverlay
   - Apply Modifier.offset(y = offsetFraction * availableHeight)
   - This shifts the boat image DOWN by the offset so it aligns with the true geo-position
```

## Open Questions for Discussion

1. **Dynamic behaviour:** Should the offset kick in only above a speed threshold (e.g., > 5 kn), or linearly ramp from 0 at 0 kn to max offset at e.g., 15 kn?

2. **Max offset:** What fraction of screen height is comfortable? Marine chartplotters typically place the vessel at ~⅓ from bottom. A quarter (25%) or third (33%)?

3. **Demo mode:** Should this also apply in demo mode when panning with a computed speed? Or only in GPS mode?

4. **Rotation interaction:** When the map is rotated (heading-up in GPS mode or demo-heading-up), "ahead" is still screen-top. The pixel offset direction (upward in screen space = ahead) is unchanged. Correct?

5. **Manual pan override:** Should the offset temporarily disable when the user manually pans (auto-follow suppressed), and re-engage on re-centre?

6. **Smooth animation:** Should offset changes (boat→stop, or speed threshold cross) animate over 500–800 ms instead of snapping?

7. **Overscroll rendering:** With the map shifted, do we need to worry about tiles beyond osmdroid's normal viewport not being loaded? osmdroid loads a margin of tiles by default, but a large offset might show grey areas above the boat.
