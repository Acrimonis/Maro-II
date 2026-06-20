<!-- scope: reference -->
# Map Library Migration Plan — osmdroid 6.1.18 → MapLibre GL Native

> **Status:** Design decisions finalised · **Target library:** `org.maplibre.gl:maplibre-android-sdk:11.x`
> **License:** BSD-3 (fully open, no API key required)
> **Codebase snapshot:** 2026-06-20 (post-pull #2). MapScreen refactored: overlay rendering extracted to `MapOverlayRenderer.kt`, `OverlayTracker` standalone, `RegulatedZoneComponents.kt` for Compose zone UI. Track recording feature in code. No MapLibre dependency added yet.

---

## Table of Contents

1. [Motivation](#1-motivation)
2. [Architecture Overview](#2-architecture-overview)
3. [Dependency Changes](#3-dependency-changes)
4. [Step 1 — MapView Setup](#step-1--mapview-setup)
5. [Step 2 — Camera: GPS Auto-Follow + Decenter + Tilt](#step-2--camera-gps-auto-follow--decenter--tilt)
6. [Step 3 — Depth Colour Raster Overlay](#step-3--depth-colour-raster-overlay)
7. [Step 4 — Low-Depth Warning Overlay](#step-4--low-depth-warning-overlay)
8. [Step 5 — Isobaths Vector Layer](#step-5--isobaths-vector-layer)
9. [Step 6 — Coastline Vector Layer](#step-6--coastline-vector-layer)
10. [Step 7 — Regulated Zones Vector Layer](#step-7--regulated-zones-vector-layer)
11. [Step 8 — Zone300 Vector Layer](#step-8--zone300-vector-layer)
12. [Step 9 — Track Recording Vector Layer](#step-9--track-recording-vector-layer)
13. [Step 10 — Two-Finger Rotation (Remove)](#step-10--two-finger-rotation-remove)
14. [Step 11 — Zoom Buttons](#step-11--zoom-buttons)
15. [Step 12 — OverlayTracker (Remove)](#step-12--overlaytracker-remove)
16. [Step 13 — Decenter + Tilt Settings UI](#step-13--decenter--tilt-settings-ui)
17. [Compose Layer (Untouched)](#compose-layer-untouched)
18. [Migration Order & Rollback Strategy](#migration-order--rollback-strategy)
19. [Design Decisions (Confirmed)](#design-decisions-confirmed)
20. [Tilt Feature Specification](#tilt-feature-specification-mvp-manual-only)
21. [Final Migration Execution Order](#final-migration-execution-order)

---

## 1. Motivation

### Problems with osmdroid 6.1.18

| Issue | Impact |
|---|---|
| **No viewport offset API** — geo-centre and viewport centre are always the same pixel | Cannot decenter the boat to the lower third without making the MapView physically taller, wasting ~50% tile rendering |
| **No tilt/perspective** — strictly 2D top-down | No look-ahead 3D effect, no GPS-style perspective |
| **GroundOverlay Mercator distortion** — requires 8-band splitting hack in [`MapOverlayRenderer.addBandedOverlay()`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) | Extra code complexity, fragile workaround |
| **Imperative overlay API** — `mapView.overlays.add()` / `removeAll()` with manual dirty tracking via [`OverlayTracker`](app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt) | Fragile, easy to introduce overlay leaks |

### What MapLibre GL provides

| Feature | MapLibre API |
|---|---|
| **Viewport offset (decenter)** | `CameraOptions.padding(bottom = offsetPx)` — shifts the camera's focal point without resizing the viewport. Zero wasted tiles. |
| **Tilt/perspective** | `CameraOptions.pitch(degree)` — 0° top-down to 85° horizon |
| **Mercator-correct raster overlays** | `RasterSource` + `RasterLayer` — native Mercator projection handling, no banding hack needed |
| **Declarative layer style** | Style-based rendering (JSON or DSL) — clear separation of data and styling |
| **GPS mode rotation** | `CameraOptions.bearing()` — combined with center, zoom, padding, pitch in one atomic camera update |

---

## 2. Architecture Overview

### Current file structure (post-refactor)

| File | Role | LOC |
|---|---|---|
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Compose layout, `AndroidView` wrapper, camera, track overlays, settings UI | ~3795 |
| [`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) | **All osmdroid overlay drawing** — coastlines, depth, isobaths, zones, banded overlays | ~343 |
| [`OverlayTracker.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt) | Overlay reference lists + dirty-check fields | ~35 |
| [`RegulatedZoneComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt) | Compose zone icon stack, info panel, category toggles, boat size slider | ~505 |
| [`TrackDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt) | Compose track recording drawer panel | ~299 |
| [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) | Compose track history list overlay | ~921 |
| [`TrackStatusIcon.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt) | Compose track status indicator icon | ~117 |

### Before (osmdroid — logical layers)

```
MapScreen (Compose Box)
└── MapContent (Box, clipToBounds)
    ├── CoastlineMapView (AndroidView → osmdroid MapView)
    │   ├── Tiles (MAPNIK raster)
    │   ├── GroundOverlay ×8 (depth colour)          ← MapOverlayRenderer
    │   ├── GroundOverlay ×8 (low-depth warning)      ← MapOverlayRenderer
    │   ├── Polyline[20-50] (isobaths)                ← MapOverlayRenderer
    │   ├── Polygon[N] (regulated zones)              ← MapOverlayRenderer
    │   ├── Polygon + Polyline (zone300)              ← MapOverlayRenderer
    │   ├── Polyline[N] (coastline)                   ← MapOverlayRenderer
    │   ├── Polyline (active recording trace)         ← MapScreen LaunchedEffect
    │   └── Polyline[0-20] (history tracks)            ← MapScreen LaunchedEffect
    ├── DirectionLine (Compose Canvas)
    ├── CapArrowOverlay (Compose Canvas)
    ├── CenterMarkerOverlay (Compose Image)
    ├── Row (overlay controls: dashboard, buttons, settings)
    ├── TrackDrawerOverlay / TrackHistoryOverlay       ← Compose only
    └── RegulatedZoneComponents (icon stack)           ← Compose only
```

### After (MapLibre GL)

```
MapScreen (Compose Box)
└── MapContent (Box, clipToBounds)
    ├── MapLibreMapView (AndroidView → MapLibre MapView)
    │   ├── Tiles (style-defined source)
    │   ├── RasterLayer (depth colour)
    │   ├── RasterLayer (low-depth warning)
    │   ├── LineLayer (isobaths)
    │   ├── FillLayer + LineLayer (regulated zones)
    │   ├── FillLayer + LineLayer (zone300)
    │   ├── LineLayer (coastline)
    │   ├── LineLayer (active recording trace)
    │   └── LineLayer[N] (history tracks)
    ├── DirectionLine (Compose Canvas)                  ← UNCHANGED
    ├── CapArrowOverlay (Compose Canvas)                ← UNCHANGED
    ├── CenterMarkerOverlay (Compose Image)             ← UNCHANGED
    ├── Row (overlay controls)                          ← UNCHANGED
    ├── TrackDrawerOverlay / TrackHistoryOverlay        ← UNCHANGED
    └── RegulatedZoneComponents                         ← UNCHANGED
```

**Primary migration targets:**
- [`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) — all osmdroid overlay drawing → replaced by MapLibre style layers
- [`OverlayTracker.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt) — obsolete → deleted
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — camera, track overlays, zoom buttons → rewritten with MapLibre APIs

**Layer insertion order:** All `addLayerBelow`/`addLayerAbove` calls must execute in correct dependency order (e.g., `"coastline-layer"` must exist before `addLayerBelow(..., "coastline-layer")` is called). The current per-step pseudo-code shows ideal ordering but during incremental migration (Steps 3–9), layers may be inserted in different order. Recommendation: add all layers in `onMapReady` after style loads, then update data per step.

**Camera listener migration:** osmdroid `MapListener` (center/zoom change callbacks in `CoastlineMapView`) must be replaced with MapLibre `OnCameraChangeListener`:
```kotlin
map.addOnCameraChangeListener { cameraState ->
    onCenterChanged(cameraState.center.latitude, cameraState.center.longitude)
    onZoomChanged(cameraState.zoom)
}
```

### Data flow

```
ViewModel (StateFlow)
    │
    ▼
Compose state (collectAsState)
    │
    ▼
MapContent (Compose)
    │
    ├── MapLibreMapView (AndroidView)
    │   └── map.getMapboxMap().setCamera(...)  ← camera + overlays
    │
    └── (Compose overlays: markers, dashboard, controls, track UI) ← UNCHANGED
```

The ViewModel layer (`CoastlineViewModel`, `DepthViewModel`, `TrackViewModel`) is **unchanged**. Only `MapScreen.kt`, `MapOverlayRenderer.kt`, and `OverlayTracker.kt` need rewriting/replacing.

---

## 3. Dependency Changes

### Add to [`gradle/libs.versions.toml`](gradle/libs.versions.toml)

```toml
[versions]
maplibre = "11.5.2"  # latest stable as of writing

[libraries]
maplibre-android-sdk = { group = "org.maplibre.gl", name = "maplibre-android-sdk", version.ref = "maplibre" }
```

### Add to [`app/build.gradle.kts`](app/build.gradle.kts)

```kotlin
dependencies {
    // Replace: implementation(libs.osmdroid.android)
    implementation(libs.maplibre.android.sdk)
}
```

**Keep osmdroid during migration** — both libraries can coexist during development. The final cleanup PR removes the osmdroid dependency.

### Permissions

MapLibre does not require additional permissions beyond what the app already declares (`ACCESS_FINE_LOCATION` for GPS).

---

## 4. Step 1 — MapView Setup

### Current ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — `CoastlineMapView` composable)

```kotlin
@Composable
private fun CoastlineMapView(
    segments: List<CoastlineSegment>,
    regulatedZones: RegulatedZoneSet?,
    zone300: Zone300Data?,
    depthBitmap: Bitmap?,
    lowDepthWarningBitmap: Bitmap?,
    depthBox: BoundingBox?,
    isobaths: List<Isobath>,
    zoomLevel: Double,
    center: LatLng,
    initialZoom: Double,
    boatPosition: LatLng? = null,
    headingDeg: Double = -1.0,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onZoomChanged: (Double) -> Unit = {},
    onMapViewReady: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localMapView = remember { mutableStateOf<MapView?>(null) }
    val tracker = remember { OverlayTracker() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = ctx.packageName
                osmdroidTileCache = java.io.File(ctx.cacheDir, "tiles").also { it.mkdirs() }
            }
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                minZoomLevel = 8.0
                maxZoomLevel = 18.0
                controller.setZoom(initialZoom)
                controller.setCenter(GeoPoint(center.latitude, center.longitude))
                // ... overlays added here via MapOverlayRenderer functions ...
            }
        },
        update = { mapView ->
            // ... dirty-check overlay updates via OverlayTracker ...
        }
    )
}
```

### Target (MapLibre)

```kotlin
@Composable
private fun MapLibreMapView(
    center: LatLng,
    zoomLevel: Double,
    headingDeg: Double,
    depthBitmap: Bitmap?,
    lowDepthWarningBitmap: Bitmap?,
    depthBox: BoundingBox?,
    isobaths: List<Isobath>,
    coastlineSegments: List<CoastlineSegment>,
    regulatedZones: RegulatedZoneSet?,
    zone300: Zone300Data?,
    onCenterChanged: (Double, Double) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onMapReady: (MapLibreMap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }

    // Tile style — use OSM raster tiles as base
    val styleJson = remember {
        buildString {
            append("""
            {
              "version": 8,
              "sources": {
                "osm": {
                  "type": "raster",
                  "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                  "tileSize": 256,
                  "attribution": "© OpenStreetMap contributors"
                }
              },
              "layers": [
                { "id": "osm-bg", "type": "raster", "source": "osm" }
              ]
            }
            """.trimIndent())
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                getMapAsync { map ->
                    map.setStyle(styleJson) {
                        // Style loaded — add custom layers
                        mapRef.value = map
                        onMapReady(map)
                    }
                }
            }
        }
    )
}
```

**Key changes:**
- `MapView(ctx)` → `MapView(ctx)` (same class name, different package)
- `setTileSource` → style JSON with `"osm"` raster source
- `Configuration.getInstance()` removed (MapLibre handles tile caching internally)
- `controller.setCenter/setZoom` → `getMapAsync` + style-based camera
- `OverlayTracker` instantiation removed — not needed

---

## 5. Step 2 — Camera: GPS Auto-Follow + Decenter + Tilt

### Current ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — GPS auto-follow `LaunchedEffect`)

```kotlin
LaunchedEffect(...) {
    viewModel.cameraUpdates.collect { target ->
        val point = GeoPoint(target.position.latitude, target.position.longitude)
        if (reengage) {
            mv.controller.animateTo(point)
            reengage = false
        } else {
            mv.controller.animateTo(point, null, GPS_ANIMATION_DURATION_MS)
        }
        mv.mapOrientation = -target.bearingDeg
        mv.invalidate()
    }
}
```

### Target (MapLibre)

```kotlin
// ── Offset fraction for decenter ─────────────────────────────────────────
val decenterOffsetFraction by remember {
    derivedStateOf {
        val speed = navigationState.speedKnots ?: navigationState.demoSpeedKnots ?: 0.0
        if (speed <= 5.0) 0.0
        else ((speed - 5.0) / 10.0).coerceIn(0.0, 1.0) * maxOffsetFraction
    }
}

// ── Tilt angle for perspective ──────────────────────────────────────────
val tiltDeg by remember {
    derivedStateOf {
        val speed = navigationState.speedKnots ?: navigationState.demoSpeedKnots ?: 0.0
        if (speed <= 5.0 || !appSettings.perspectiveViewEnabled) 0.0
        else ((speed - 5.0) / 15.0).coerceIn(0.0, 1.0) * maxTiltDeg
    }
}

// ── Smooth camera animation ─────────────────────────────────────────────
LaunchedEffect(appSettings.gpsMode, appSettings.demoHeadingUp, autoFollowSuppressed, mapLibreMap) {
    val map = mapLibreMap ?: return@LaunchedEffect
    if (!appSettings.gpsMode && !appSettings.demoHeadingUp) return@LaunchedEffect
    if (autoFollowSuppressed) return@LaunchedEffect
    var reengage = true

    viewModel.cameraUpdates.collect { target ->
        val offsetPx = with(density) { (decenterOffsetFraction * screenHeightDp).toPx() }

        val camera = CameraOptions.Builder()
            .center(Point.fromLngLat(target.position.longitude, target.position.latitude))
            .bearing(-target.bearingDeg)
            .zoom(zoomLevel)
            .pitch(tiltDeg)
            .padding(0.0, 0.0, offsetPx.toDouble(), 0.0)
            .build()

        if (reengage) {
            map.setCamera(camera)  // instant
            reengage = false
        } else {
            map.setCamera(
                camera,
                MapAnimationOptions.Builder().duration(GPS_ANIMATION_DURATION_MS).build()
            )
        }
    }
}
```

**Key changes:**
- **One atomic camera call** — centre, bearing, zoom, padding, pitch all at once
- **`padding(bottom = offsetPx)`** — this is the decenter mechanism. Zero wasted tiles.
- **`pitch(tiltDeg)`** — tilt for perspective look-ahead
- **`derivedStateOf`** for offset fraction and tilt — reactive to speed changes
- No separate `mapOrientation` / `invalidate()` needed — MapLibre handles this

### Decenter + Tilt Settings (NOT YET IN CODEBASE)

Add to [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt):

```kotlin
data class AppSettings(
    // ... existing fields (track recording, auto-show, etc. already in code) ...
    val decenterEnabled: Boolean = false,        // OFF by default per design decision #5
    val decenterMaxFraction: Float = 0.25f,      // 0%–40%
    val perspectiveViewEnabled: Boolean = false, // MVP: Boolean toggle; §20 TiltMode enum deferred
    val perspectiveMaxTiltDeg: Float = 50f,      // 0°–85°
)
```

Add toggles in Settings → Display / Navigation tab.

---

## 6. Step 3 — Depth Colour Raster Overlay

### Current ([`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) — `addBandedOverlay()` + `drawDepthMap()`)

- Splits the pre-rendered depth bitmap into 8 horizontal strips
- Creates 8 `GroundOverlay` objects pinned at true latitudes
- Workaround for osmdroid's Mercator-linear stretching

### Target (MapLibre)

For a pre-rendered bitmap covering a specific bounding box, use `ImageSource`:

```kotlin
private fun addDepthLayer(map: MapLibreMap, bitmap: Bitmap, bbox: BoundingBox) {
    map.style?.addSource(
        ImageSource("depth-image", 
            CoordinateBounds(
                Point.fromLngLat(bbox.lonWest, bbox.latSouth),
                Point.fromLngLat(bbox.lonEast, bbox.latNorth)
            )
        ).also { it.setImage(bitmap) }
    )

    map.style?.addLayerBelow(
        RasterLayer("depth-layer", "depth-image"),
        "coastline-layer"  // place below coastline
    )
}
```

**The banding hack is eliminated** — MapLibre's Mercator projection handles the latitude distortion natively.

---

## 7. Step 4 — Low-Depth Warning Overlay

### Current ([`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) — `drawLowDepthWarning()`)

Same 8-band GroundOverlay pattern as depth map.

### Target

Same as Step 3, using a separate `ImageSource` + `RasterLayer`:

```kotlin
map.style?.addSource(
    ImageSource("lowdepth-image", CoordinateBounds(...))
        .also { it.setImage(warningBitmap) }
)
map.style?.addLayerBelow(
    RasterLayer("lowdepth-layer", "lowdepth-image"),
    "isobaths-layer"  // below isobaths
)
```

**Visibility toggling:** `layer.isVisible = appSettings.lowDepthWarningVisible`

---

## 8. Step 5 — Isobaths Vector Layer

### Current ([`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) — `drawIsobaths()`)

Creates `Polyline` objects for each isobath contour. Each polyline is added to `mapView.overlays`.

### Target (MapLibre)

Convert isobath polylines to GeoJSON features, add as a `GeoJSONSource` + `LineLayer`:

```kotlin
private fun updateIsobaths(map: MapLibreMap, isobaths: List<Isobath>) {
    val features = isobaths.map { iso ->
        val coords = iso.line.points.map { 
            listOf(it.longitude, it.latitude) 
        }
        Feature.fromGeometry(
            Geometry.fromJson("""{"type":"LineString","coordinates":${coords}}""")
        )
    }

    val source = map.style?.getSource("isobaths") as? GeoJSONSource
    if (source != null) {
        source.setGeoJSON(FeatureCollection.fromFeatures(features))
    } else {
        map.style?.addSource(
            GeoJSONSource("isobaths", FeatureCollection.fromFeatures(features))
        )
        map.style?.addLayerBelow(
            LineLayer("isobaths-layer", "isobaths").apply {
                setProperties(
                    PropertyFactory.lineColor(Color.parseColor("#8B4513")),
                    PropertyFactory.lineWidth(1.5f),
                    PropertyFactory.lineOpacity(0.7f)
                )
            },
            "regulated-zones-layer"
        )
    }
}
```

**Benefits over osmdroid:**
- One `GeoJSONSource` instead of N `Polyline` objects — single data update
- MapLibre handles clustering/decimation automatically
- Style properties are decoupled from data

---

## 9. Step 6 — Coastline Vector Layer

### Current ([`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) — `drawCoastline()`)

- Creates `Polyline` per coastline segment + dot/ring markers for islands/obstructions

### Target

Same pattern as isobaths — `GeoJSONSource` + `LineLayer`:

```kotlin
private fun updateCoastline(map: MapLibreMap, segments: List<CoastlineSegment>) {
    val features = segments.map { seg ->
        Feature.fromGeometry(
            MultiLineString.fromLineStrings(
                seg.points.map { pt ->
                    LineString.fromLngLats(
                        listOf(Point.fromLngLat(pt.lon, pt.lat))
                    )
                }
            )
        )
    }
    // ... same GeoJSONSource update pattern ...
}
```

**Dot/ring markers** for islands/obstructions can use `CircleLayer` (for pure dots) or `SymbolLayer` with custom icons.

---

## 10. Step 7 — Regulated Zones Vector Layer

### Current ([`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) — `drawRegulatedZones()`)

Creates `Polygon` objects for each regulated zone, with support for holes (island interiors). Uses color from `regulatedZoneColor()` function (also in `MapOverlayRenderer.kt`).

### Target

```kotlin
private fun updateRegulatedZones(map: MapLibreMap, zones: RegulatedZoneSet?) {
    val features = zones?.zones?.map { zone ->
        val outerRing = zone.outerRing.map { 
            listOf(it.longitude, it.latitude) 
        }
        val holes = zone.holes.map { hole ->
            hole.map { listOf(it.longitude, it.latitude) }
        }
        Feature.fromGeometry(
            Polygon.fromOuterInner(
                LineString.fromLngLats(outerRing.map { Point.fromLngLat(it[0], it[1]) }),
                holes.map { hole ->
                    LineString.fromLngLats(hole.map { Point.fromLngLat(it[0], it[1]) })
                }
            )
        ).apply {
            addStringProperty("zoneType", zone.zoneType.name)
        }
    } ?: emptyList()

    // ... GeoJSONSource update or add ...
}
```

**Style by zone type** using data-driven styling:

```kotlin
map.style?.addLayer(
    FillLayer("regulated-zones-fill", "regulated-zones").apply {
        setProperties(
            PropertyFactory.fillColor(
                match("zoneType") {
                    // Use zone type → colour mapping
                }
            ),
            PropertyFactory.fillOpacity(0.3f)
        )
    }
)
```

The [`RegulatedZoneComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt) file (icon stack, info panel, category toggles) is **pure Compose** — untouched by migration.

---

## 11. Step 8 — Zone300 Vector Layer

### Current ([`MapOverlayRenderer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt) — `drawZone300()`)

Creates `Polygon` for fill + `Polyline` for border.

### Target

Same pattern as regulated zones — `GeoJSONSource` with `FillLayer` + `LineLayer`:

```kotlin
map.style?.addLayer(FillLayer("zone300-fill", "zone300").apply {
    setProperties(
        PropertyFactory.fillColor(Color.parseColor("#FF4444")),
        PropertyFactory.fillOpacity(0.2f)
    )
})
map.style?.addLayer(LineLayer("zone300-border", "zone300").apply {
    setProperties(
        PropertyFactory.lineColor(Color.parseColor("#FF0000")),
        PropertyFactory.lineWidth(2f),
        PropertyFactory.lineDasharray(arrayOf(4f, 2f))
    )
})
```

---

## 12. Step 9 — Track Recording Vector Layer

### Current ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — two `LaunchedEffect` blocks)

Two separate `LaunchedEffect` blocks manage track polylines directly on `mv.overlays` (not through `OverlayTracker`):

**History tracks:** Diff-based update using `renderedTrackIds` set — removes outdated `"track_hist_{id}"` polylines, adds new ones with fade-gradient opacity (newest→oldest), color interpolation from `trackingColorPastFrom` → `trackingColorPastTo`.

**Active recording trace:** `snapshotFlow { trackRecorderState }` — updates existing `"track_recording"` polyline or creates/removes it based on `TrackRecorderState.ON`.

### Target (MapLibre)

**History tracks** — single `GeoJSONSource` updated with all visible track geometries; `LineLayer` with per-feature properties:

```kotlin
private fun updateTrackOverlays(
    map: MapLibreMap,
    visibleTracks: List<Track>,
    trackingRenderNb: Int,
    colorPastFrom: Int,
    colorPastTo: Int,
    transparencyFrom: Int,
    transparencyTo: Int
) {
    if (!map.isStyleLoaded) return
    
    val features = visibleTracks.mapIndexed { index, track ->
        val coords = track.trackPoints.map { listOf(it.lon, it.lat) }
        val t = if (visibleTracks.size <= 1) 0f 
                else index.toFloat() / (visibleTracks.size - 1).toFloat()
        val alpha = (transparencyFrom + (transparencyTo - transparencyFrom) * t) / 100f
        val color = interpolateColor(colorPastFrom, colorPastTo, t)
        
        Feature.fromGeometry(
            Geometry.fromJson("""{"type":"LineString","coordinates":[${coords.joinToString(",") { "[${it[0]},${it[1]}]" }}]}""")
        ).apply {
            addNumberProperty("strokeWidth", 6.0)
            addNumberProperty("strokeOpacity", alpha.toDouble())
            addStringProperty("strokeColor", "#%06X".format(color and 0xFFFFFF))
        }
    }

    val source = map.style?.getSource("track-history") as? GeoJSONSource
    if (source != null) {
        source.setGeoJSON(FeatureCollection.fromFeatures(features))
    } else {
        map.style?.addSource(GeoJSONSource("track-history", FeatureCollection.fromFeatures(features)))
        map.style?.addLayerBelow(
            LineLayer("track-history-layer", "track-history").apply {
                setProperties(
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineColor(Color.parseColor("#FF1565C0")),
                    PropertyFactory.lineOpacity(0.7f)
                )
            },
            "coastline-layer"
        )
    }
}
```

**Active recording trace** — separate `GeoJSONSource` updated in real-time via `snapshotFlow`:

```kotlin
LaunchedEffect(mapLibreMap, appSettings.trackingColorActive) {
    val map = mapLibreMap ?: return@LaunchedEffect
    snapshotFlow { trackRecorderState }.collect { state ->
        val source = map.style?.getSource("track-recording") as? GeoJSONSource
        if (state.state == TrackRecorderState.ON && state.recordingPoints.isNotEmpty()) {
            val coords = state.recordingPoints.map { listOf(it.lon, it.lat) }
            val geoJson = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[${coords.joinToString(",") { "[${it[0]},${it[1]}]" }}]}}"""
            
            if (source != null) {
                source.setGeoJSON(Feature.fromJson(geoJson))
            } else {
                map.style?.addSource(GeoJSONSource("track-recording", geoJson))
                map.style?.addLayerAbove(
                    LineLayer("track-recording-layer", "track-recording").apply {
                        setProperties(
                            PropertyFactory.lineColor(Color.parseColor("#%06X".format(appSettings.trackingColorActive and 0xFFFFFF))),
                            PropertyFactory.lineWidth(10f),
                            PropertyFactory.lineOpacity(0.9f)
                        )
                    },
                    "track-history-layer"
                )
            }
        } else {
            map.style?.removeLayer("track-recording-layer")
            map.style?.removeSource("track-recording")
        }
    }
}
```

**Note:** [`TrackDrawerOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt) and [`TrackHistoryOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) are pure Compose — **untouched** by the migration.

**Settings fields already in code** ([`AppSettings`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt)):
- `trackEnabled`, `tracksVisible`, `trackingRenderNb` (0–20)
- `trackingColorActive`, `trackingColorPastFrom/To`, `trackingTransparencyFrom/To`
- `trackingColorPinnedFrom/To`, `trackingColorHistory/End`, `trackingColorPinned`

No migration-specific changes needed — MapLibre consumes the same settings.

---

## 13. Step 10 — Two-Finger Rotation (Remove)

### Current ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — `setOnTouchListener` block)

```kotlin
var lastAngleDeg = 0f
mv.setOnTouchListener { _, ev ->
    when (ev.actionMasked) {
        MotionEvent.ACTION_POINTER_DOWN -> { /* track two-finger rotation */ }
        MotionEvent.ACTION_MOVE -> { /* compute angle, update mapOrientation */ }
    }
}
```

### Target

**Delete this block entirely.** MapLibre handles two-finger rotation natively via `MapView`'s built-in gesture handlers. Rotation is automatically reflected in `CameraOptions.bearing()`.

---

## 14. Step 11 — Zoom Buttons

### Current ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt))

```kotlin
mv.controller.zoomIn()
mv.controller.zoomOut()
```

### Target

```kotlin
map.zoomIn(map.getCameraState().center, 0.0)    // zoom in by 1 level
map.zoomOut(map.getCameraState().center, 0.0)   // zoom out by 1 level
```

Or animate:

```kotlin
map.setCamera(
    CameraOptions.Builder().zoom(map.getCameraState().zoom + 1).build(),
    MapAnimationOptions.Builder().duration(200).build()
)
```

---

## 15. Step 12 — OverlayTracker (Remove)

### Current ([`OverlayTracker.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt) — standalone file, 35 lines)

```kotlin
class OverlayTracker {
    val depth = mutableListOf<GroundOverlay>()
    val lowDepth = mutableListOf<GroundOverlay>()
    val isobaths = mutableListOf<Polyline>()
    val regulatedZones = mutableListOf<Polygon>()
    val zone300 = mutableListOf<Any>()
    val coastline = mutableListOf<Any>()

    var lastDepthBitmap: Bitmap? = null
    var lastLowDepthBitmap: Bitmap? = null
    var lastIsobaths: List<Isobath> = emptyList()
    var lastRegulatedZones: RegulatedZoneSet? = null
    var lastZone300: Zone300Data? = null
    var lastSegments: List<CoastlineSegment> = emptyList()
    var lastDepthBox: BoundingBox? = null
    var lastZoom: Double = -1.0
}
```

Used by `CoastlineMapView` in `MapScreen.kt` to rebuild only layers whose data changed. Track recording overlays manage `mv.overlays` directly — never used `OverlayTracker`.

### Target

**Delete the entire file.** MapLibre manages layers declaratively through the style system. Layer visibility and data updates are handled via `style?.getLayer(id)?.isVisible = ...` and `GeoJSONSource.setGeoJSON(...)` — no manual overlay tracking needed.

Also remove the `tracker` instantiation and all dirty-check logic from `CoastlineMapView` in `MapScreen.kt`.

---

## 16. Step 13 — Decenter + Tilt Settings UI

**Not yet implemented in codebase.** These settings fields do not exist in [`AppSettings`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt).

Add to Settings (Display tab) in [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt):

```kotlin
// ── Decenter toggle ─────────────────────────────────────
SettingsToggleRow(
    label = "Decenter boat position",
    checked = appSettings.decenterEnabled,
    onToggle = { onUpdateSettings(it.copy(decenterEnabled = it)) }
)

if (appSettings.decenterEnabled) {
    SettingsSliderRow(
        label = "Max offset",
        value = appSettings.decenterMaxFraction,
        onValueChange = { /* update */ },
        valueRange = 0.0f..0.4f
    )
}

// ── Perspective (tilt) toggle ───────────────────────────
SettingsToggleRow(
    label = "Perspective view (tilt)",
    checked = appSettings.perspectiveViewEnabled,
    onToggle = { onUpdateSettings(it.copy(perspectiveViewEnabled = it)) }
)

if (appSettings.perspectiveViewEnabled) {
    SettingsSliderRow(
        label = "Max tilt angle",
        value = appSettings.perspectiveMaxTiltDeg,
        onValueChange = { /* update */ },
        valueRange = 0f..85f
    )
}
```

---

## 17. Compose Layer (Untouched)

The following Compose-only layers stay **exactly as they are** — they are independent of the map rendering library:

| Layer | File | Status |
|---|---|---|
| [`DashboardPanel`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) | Separate file | ✅ Untouched |
| `DirectionLine` | Inline in `MapScreen.kt` | ✅ Untouched |
| `CapArrowOverlay` | Inline in `MapScreen.kt` | ✅ Untouched |
| `CenterMarkerOverlay` | Inline in `MapScreen.kt` | ✅ Untouched |
| [`FanLayout`](app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt) | Separate file | ✅ Untouched |
| [`MapControlButton` / `FanIconComponents`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt) | Separate file | ✅ Untouched |
| [`TrackStatusIcon`](app/src/main/java/ykws/android/maro/ui/map/TrackStatusIcon.kt) | Separate file | ✅ Untouched |
| [`TrackDrawerOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt) | Separate file | ✅ Untouched |
| [`TrackHistoryOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) | Separate file | ✅ Untouched |
| [`RegulatedZoneComponents`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt) | Separate file | ✅ Untouched |
| Settings overlay | Inline in `MapScreen.kt` | ✅ Untouched |
| Loading/error overlays | Inline in `MapScreen.kt` | ✅ Untouched |
| Exit banner | Inline in `MapScreen.kt` | ✅ Untouched |

**Total Compose LOC untouched:** ~4600 across `MapScreen.kt` Compose inline (~2800) + separate files (~1800).

---

## 18. Migration Order & Rollback Strategy

### Execution order

Each step is independently testable — build and run after each.

| Step | What changes | Files affected | Can build? | Risky? |
|---|---|---|---|---|
| 1 | Add MapLibre dependency, create `MapLibreMapView` composable alongside existing `CoastlineMapView` | `build.gradle.kts`, `libs.versions.toml`, `MapScreen.kt` | ✅ | Low |
| 2 | Wire camera (GPS auto-follow) — decenter + tilt are optional at this point | `MapScreen.kt` | ✅ | Medium |
| 3 | Depth raster overlay | New MapLibre source/layer code in `MapScreen.kt` | ✅ | Medium |
| 4 | Low-depth warning overlay | `MapScreen.kt` | ✅ | Medium |
| 5 | Isobaths vector layer | `MapScreen.kt` | ✅ | Medium |
| 6 | Coastline vector layer | `MapScreen.kt` | ✅ | Medium |
| 7 | Regulated zones vector layer | `MapScreen.kt` | ✅ | Medium |
| 8 | Zone300 vector layer | `MapScreen.kt` | ✅ | Medium |
| 9 | Track recording vector layer | `MapScreen.kt` | ✅ | Medium |
| 10 | **Cutover**: replace `CoastlineMapView` → `MapLibreMapView` in `MapContent`. Delete `MapOverlayRenderer.kt` **only after** confirming MapLibreMapView is stable — both composables coexist until verified, then osmdroid code removed. | `MapScreen.kt`, delete `MapOverlayRenderer.kt` | ✅ | High |
| 11 | Remove two-finger rotation | `MapScreen.kt` | ✅ | Low |
| 12 | Remove `OverlayTracker` class | Delete `OverlayTracker.kt` | ✅ | Low |
| 13 | Remove osmdroid dependency | `build.gradle.kts`, `libs.versions.toml` | ✅ | Low |
| 14 | Add decenter + tilt settings UI (MVP: `Boolean` toggle for perspective, slider for decenter fraction; §20 `TiltMode` enum deferred to future AUTOMATIC) | `MapScreen.kt`, `SettingsManager.kt` | ✅ | Low |

### Rollback

Because `CoastlineMapView` and `MapLibreMapView` coexist during development (Step 1–9), rollback at any point means:

```kotlin
// MapContent:
// Swap the comment:
// MapLibreMapView(...)       ← new
CoastlineMapView(...)          ← old
```

No data loss, no schema change, no ViewModel changes. Full rollback is a single line swap + `gradle` revert.

---

## 19. Design Decisions (Confirmed)

All open questions have been resolved:

| # | Question | Decision |
|---|---|---|
| 1 | **Tile source** | **OSM raster tiles** (same as current). Zero visual change. |
| 2 | **Offline MBTiles** | **Skip for now.** Online-only. Add only if users request it. |
| 3 | **Depth bitmap approach** | **Unchanged.** Existing `DepthBitmap.kt` feeds MapLibre's `ImageSource`. 8-band `addBandedOverlay` hack eliminated. |
| 4 | **Tilt default** | **OFF by default.** Opt-in via Settings → Display. Three-state: OFF / MANUAL / AUTOMATIC (future). |
| 5 | **Decenter default** | **OFF by default.** When enabled: 25% max offset, gradual ramp 5–15 kn. |
| 6 | **Demo mode** | **Both GPS and demo mode** — pan-derived speed drives decenter/tilt ramps. |
| 7 | **Compose binding** | **Raw `AndroidView(MapView)`** — stable, no community dependency risk. |
| 8 | **Track recording** | Same as current — `TrackViewModel` drives both active trace and history overlays. Separate `GeoJSONSource` per mode, color/opacity from existing `AppSettings` fields. |
| 9 | **MapOverlayRenderer** | **Replaced entirely.** All osmdroid drawing functions become MapLibre style layers. File deleted at cutover (Step 10). |
| 10 | **RegulatedZoneComponents** | **Untouched.** Pure Compose — icon stack, info panel, category toggles, boat size slider. |

---

## 20. Tilt Feature Specification (MVP: Manual Only)

### Settings UI

A new section in Settings → Display:

```
┌─────────────────────────────────────┐
│  Map Tilting                   [OFF]│
│                                     │
│  ↳ State:  ○ OFF  ○ MANUAL         │
│            ○ AUTOMATIC (future)     │
│                                     │
│  [visible when MANUAL selected]     │
│  Tilt angle:  ─────●───────  35°    │
│  [0°]                        [85°]  │
│  [ ↺ Reset to 0° ]                 │
└─────────────────────────────────────┘
```

### Implementation

**SettingsManager.kt:**
```kotlin
data class AppSettings(
    val tiltMode: TiltMode = TiltMode.OFF,
    val manualTiltDeg: Float = 30f,
)
enum class TiltMode { OFF, MANUAL, AUTOMATIC }
```

**Camera logic (MapScreen.kt):**
```kotlin
val tiltAngle = when (appSettings.tiltMode) {
    TiltMode.OFF -> 0.0
    TiltMode.MANUAL -> appSettings.manualTiltDeg.toDouble()
    TiltMode.AUTOMATIC -> 0.0  // placeholder
}
```

**Live preview:** Slider updates `manualTiltDeg` → Compose recomposes → camera updates → map tilts in real-time behind Settings.

### Future: AUTOMATIC mode

```kotlin
TiltMode.AUTOMATIC -> {
    val speed = effectiveSpeedKn
    if (speed <= minKn) 0.0
    else ((speed - minKn) / (maxKn - minKn)).coerceIn(0.0, 1.0) * maxTiltDeg
}
```

---

## 21. Final Migration Execution Order

| Step | Description | Blocking |
|---|---|---|
| 1 | Add MapLibre dep, create `MapLibreMapView` with OSM raster tiles, `AndroidView` wrapper | None |
| 2 | Camera: GPS auto-follow + decenter (OFF default) + manual tilt (OFF default, 3-state) | Step 1 |
| 3 | Depth raster overlay (ImageSource, no banding) | Step 1 |
| 4 | Low-depth warning overlay | Step 1 |
| 5 | Isobaths vector layer (GeoJSONSource + LineLayer) | Step 1 |
| 6 | Coastline vector layer | Step 1 |
| 7 | Regulated zones vector layer | Step 1 |
| 8 | Zone300 vector layer | Step 1 |
| 9 | Track recording vector layer (active trace + history, separate GeoJSONSources) | Step 1 |
| 10 | **Cutover**: replace `CoastlineMapView` → `MapLibreMapView` + delete `MapOverlayRenderer.kt` | Steps 2–9 |
| 11 | Remove two-finger rotation listener | Step 10 |
| 12 | Delete `OverlayTracker.kt` + remove all tracker references in `MapScreen.kt` | Step 10 |
| 13 | Remove osmdroid dependency | Step 10 |
| 14 | Tilt Settings UI (OFF/MANUAL selector + slider with live preview + reset button) | Step 2 |

**Rollback:** Both `CoastlineMapView` and `MapLibreMapView` coexist during Steps 1–9. Rollback = swap composable + revert dependency.
