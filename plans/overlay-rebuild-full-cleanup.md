# Full Cleanup: Per-Layer Overlay Rebuild

## Current Problem

[`CoastlineMapView`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1135) uses a single monolithic [`overlayKey`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1135) that packs ALL layer dependencies into one `remember { Any() }`. Any single change — whether a visibility toggle, new data, or a zoom-gate crossing — triggers a **full `removeAll` + rebuild of all 6 layers + blanket `invalidate()`**.

This affects every layer toggle, not just zone300.

## Solution: Per-Layer Persistent Overlay Tracking

Each draw function returns references to the overlays it created. The `update` block checks each layer independently and only rebuilds the ones whose data actually changed. Overlays for unchanged layers stay in place.

### Architecture

```
CoastlineMapView
├── layerOverlays: LayerOverlaySets     ← persistent ref storage
│   ├── depthOverlays: List<GroundOverlay>
│   ├── lowDepthOverlays: List<GroundOverlay>
│   ├── isobathOverlays: List<Polyline>
│   ├── regulatedZoneOverlays: List<Polygon>
│   ├── zone300FillOverlays: List<Polygon>
│   ├── zone300LineOverlays: List<Polyline>
│   └── coastlineOverlays: List<Polyline|Polygon>
│
├── factory: draw ALL layers unconditionally     ← first paint
└── update:
    ├── if depthBitmap changed → rebuild depthOverlays only
    ├── if lowDepthWarningBitmap changed → rebuild lowDepthOverlays only
    ├── if isobaths changed → rebuild isobathOverlays only
    ├── if regulatedZones changed → rebuild regulatedZoneOverlays only
    ├── if zone300 data/visibility changed → rebuild zone300Overlays only
    ├── if segments changed → rebuild coastlineOverlays only
    └── single mapView.invalidate() at end
```

### Data Class

```kotlin
private data class OverlaySets(
    val depth: MutableList<GroundOverlay> = mutableListOf(),
    val lowDepth: MutableList<GroundOverlay> = mutableListOf(),
    val isobaths: MutableList<Polyline> = mutableListOf(),
    val regulatedZones: MutableList<Polygon> = mutableListOf(),
    val zone300Fill: MutableList<Polygon> = mutableListOf(),
    val zone300Lines: MutableList<Polyline> = mutableListOf(),
    val coastline: MutableList<Any> = mutableListOf()
)
```

### Refactored Draw Functions

Each draw function changes signature: instead of appending directly to `mapView.overlays`, it takes an `OverlaySets` sink and appends to both the sink list AND `mapView.overlays`:

```kotlin
// Before
private fun drawZone300(mapView: MapView, zone: Zone300Data?, zoomLevel: Double) {
    ...
    mapView.overlays.add(fill)
    ...
    mapView.overlays.add(redLine)
}

// After
private fun drawZone300(
    mapView: MapView,
    zone: Zone300Data?,
    zoomLevel: Double,
    sink: OverlaySets
) {
    if (zone == null || zoomLevel < ZONE_MIN_ZOOM) return
    sink.zone300Fill.clear()
    sink.zone300Lines.clear()
    for (poly in zone.fillPolygons) {
        ...
        val fill = Polygon() ...
        mapView.overlays.add(fill)
        sink.zone300Fill.add(fill)
    }
    for (line in zone.seawardLines) {
        ...
        val redLine = Polyline() ...
        mapView.overlays.add(redLine)
        sink.zone300Lines.add(redLine)
    }
}
```

### Update Block Logic

```kotlin
val overlays = remember { OverlaySets() }

update = { mapView ->
    var dirty = false

    // Depth layer
    if (depthBitmap !== overlays.lastDepthBitmap || depthVisible != overlays.lastDepthVisible) {
        mapView.overlays.removeAll(overlays.depth)
        overlays.depth.clear()
        drawDepthMap(mapView, depthBitmap, depthBox, zoomLevel, overlays)
        overlays.lastDepthBitmap = depthBitmap
        overlays.lastDepthVisible = depthVisible
        dirty = true
    }

    // Low-depth warning layer
    if (lowDepthWarningBitmap !== overlays.lastLowDepthBitmap || ...) {
        mapView.overlays.removeAll(overlays.lowDepth)
        overlays.lowDepth.clear()
        drawLowDepthWarning(...)
        dirty = true
    }

    // Isobaths layer
    if (isobaths !== overlays.lastIsobaths || isobathVisible != overlays.lastIsobathVisible || ...) {
        ...
    }

    // ... same pattern for each layer ...

    if (dirty) mapView.invalidate()
}
```

### Zoom-Gate Handling

Zoom thresholds (`ZONE_MIN_ZOOM`, `DEPTH_MAP_MIN_DRAW_ZOOM`, `ISOBATH_MIN_DRAW_ZOOM`) affect multiple layers. When zoom crosses any threshold, the affected layers' visibility status changes. This is naturally handled because each layer checks its zoom gate inside its draw function — if the gate fails, the function clears the overlay list and draws nothing.

The `zoomLevel` is included in each layer's "last state" check so crossing a threshold triggers a rebuild of only the affected layers.

### Visibility Toggle Handling

Currently, visibility toggles work via `visibleZone300 = if (visible) data else null` — meaning the data reference itself changes (null ↔ data), which changes `overlayKey`. With per-layer tracking, this is fixed by:

1. Always pass the **raw data** (never null it out for visibility)
2. Pass a separate **`zone300Visible: Boolean`** flag
3. The layer's dirty check compares both the data reference AND the visibility flag
4. On visibility-only change: remove old overlays from `mapView.overlays` (by reference in the tracked list), don't add new ones (the draw function returns early when `zone300Visible = false`)

Even simpler: always draw, just set `.isVisible` on each overlay in the tracked list:

```kotlin
if (zone300Visible != overlays.lastZone300Visible) {
    overlays.zone300Fill.forEach { it.isVisible = zone300Visible }
    overlays.zone300Lines.forEach { it.isVisible = zone300Visible }
    overlays.lastZone300Visible = zone300Visible
    mapView.invalidate()  // lightweight — just paint flag change
}
```

### Per-Layer State Tracking

```kotlin
private class OverlaySets(
    // Overlay references
    val depth: MutableList<GroundOverlay> = mutableListOf(),
    val lowDepth: MutableList<GroundOverlay> = mutableListOf(),
    val isobaths: MutableList<Polyline> = mutableListOf(),
    val regulatedZones: MutableList<Polygon> = mutableListOf(),
    val zone300Fill: MutableList<Polygon> = mutableListOf(),
    val zone300Lines: MutableList<Polyline> = mutableListOf(),
    val coastline: MutableList<Any> = mutableListOf(),
    // Last-known data references for dirty checking
    var lastDepthBitmap: Bitmap? = null,
    var lastLowDepthBitmap: Bitmap? = null,
    var lastIsobaths: List<Isobath> = emptyList(),
    var lastRegulatedZones: RegulatedZoneSet? = null,
    var lastZone300: Zone300Data? = null,
    var lastSegments: List<CoastlineSegment> = emptyList(),
    var lastZoomLevel: Double = -1.0,
    var lastZoneVisible: Boolean = false,
    var lastDepthVisible: Boolean = false,
    var lastIsobathVisible: Boolean = false,
    var lastShallowIsobathVisible: Boolean = false,
    var lastZone300Visible: Boolean = false,
    var lastRegulatedZonesVisible: Boolean = false,
    var lastCoastlineVisible: Boolean = false,
)
```

### Factory Block Simplification

The `factory` block draws all layers as before, but the first `update` call will immediately match all "last" state against the current values and skip all rebuilds (since data hasn't changed yet). The tracked overlay lists are populated during the factory call.

To avoid duplicating draw logic, the factory can delegate to the same draw functions with a null previous-state check.

## Changes Required

### Files
- [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

### Draw Functions (signature changes)
| Function | File Line | Change |
|----------|-----------|--------|
| `drawDepthMap` | ~3258 | Add `sink: OverlaySets` param |
| `drawLowDepthWarning` | ~3269 | Add `sink: OverlaySets` param |
| `drawIsobaths` | ~3280 | Add `sink: OverlaySets` param |
| `drawRegulatedZones` | ~3186 | Add `sink: OverlaySets` param |
| `drawZone300` | ~3125 | Add `sink: OverlaySets` param |
| `drawCoastline` | ~2948 | Add `sink: OverlaySets` param |
| `addBandedOverlay` | ~3226 | Add `sink: OverlaySets` param |

### CoastlineMapView
- Replace `overlayKey` + `lastOverlayKey` mechanism with `OverlaySets` + per-layer dirty checks
- Replace blanket `removeAll` + rebuild with per-layer selective remove+rebuild
- Factory delegates to draw functions with OverlaySets sink
- Update block iterates per-layer dirty checks

### MapContent Call Site
- Pass `zone300Visible` and `regulatedZonesVisible` as separate booleans alongside data
- Don't null out data for visibility (always pass the raw data reference)

## Migration Strategy

1. Add `OverlaySets` class with tracked lists + last-known state
2. Refactor all 6 draw functions to accept `OverlaySets` sink
3. Replace `overlayKey` mechanism with per-layer dirty checking in the `update` block
4. Pass visibility booleans separately from data in call site
5. Build + verify

The refactored approach eliminates ALL full overlay rebuilds. Each visibility toggle or data change only touches the specific layer that changed.
