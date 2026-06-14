# Zone-Ahead Dashed Line — Implementation Plan

## Goal
Draw a dashed line on the osmdroid map from the boat icon to the speed zone boundary intersection point, showing the `distanceAheadM` path visually.

## Isolation
All new code is in a single `drawZoneAheadLine()` function in `MapScreen.kt` alongside the other draw helpers. To remove: delete the function + its call sites + the `intersectionLatLng` field from `HeadingAheadResult`.

## Changes

### 1. HeadingAheadResult — add intersectionLatLng
**File:** `CoastlineViewModel.kt`
- Add `val intersectionLatLng: LatLng? = null` field
- **Source:** computed from `SpatialOperations.pointAlongBearing(origin, headingDeg, distanceAheadM)` in `querySpeedZoneAhead()`

### 2. querySpeedZoneAhead — compute intersection point
**File:** `CoastlineViewModel.kt`
- After picking the closest zone, compute intersection point:  
  `val intersectionPt = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, distance)`
- Pass to `buildHeadingResult()` and store in result

### 3. drawZoneAheadLine — new isolated overlay
**File:** `MapScreen.kt` (new private function)
```kotlin
private fun drawZoneAheadLine(
    mapView: MapView,
    boatLatLng: LatLng?,
    hitLatLng: LatLng?,
    zoomLevel: Double
) {
    // Remove existing old line
    mapView.overlays.removeAll { it is Polyline && it.title == TAG_ZONE_AHEAD_LINE }
    if (boatLatLng == null || hitLatLng == null) return
    if (zoomLevel < ZONE_MIN_ZOOM) return
    
    val line = Polyline().apply {
        title = TAG_ZONE_AHEAD_LINE
        setPoints(listOf(
            GeoPoint(boatLatLng.latitude, boatLatLng.longitude),
            GeoPoint(hitLatLng.latitude, hitLatLng.longitude)
        ))
        outlinePaint.apply {
            color = android.graphics.Color.WHITE
            strokeWidth = 5f  // outer outline for contrast
            style = android.graphics.Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
    }
    // Inner line (darker) for contrast over white backgrounds
    val innerLine = Polyline().apply {
        title = TAG_ZONE_AHEAD_LINE_INNER
        setPoints(listOf(
            GeoPoint(boatLatLng.latitude, boatLatLng.longitude),
            GeoPoint(hitLatLng.latitude, hitLatLng.longitude)
        ))
        outlinePaint.apply {
            color = 0xDD000000.toInt()  // dark, high alpha
            strokeWidth = 3f
            style = android.graphics.Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
    }
    mapView.overlays.add(innerLine)
    mapView.overlays.add(line)  // white on top
}
```

### 4. Wire through MapContent → CoastlineMapView
**File:** `MapScreen.kt`
- Add `headingAheadResult: HeadingAheadResult?` param to `MapContent` and `CoastlineMapView`
- Extract `intersectionLatLng` and pass to `drawZoneAheadLine()`
- Call in both `factory` and `update` blocks
- Include `headingAheadResult` in `overlayKey` for rebuild detection

### 5. MapScreen — pass data
- Collect `headingAheadDistance` state from ViewModel
- Pass to `MapContent`
