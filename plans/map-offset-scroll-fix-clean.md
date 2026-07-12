# Map Offset — Scroll Feedback Fix (Clean)

**Branch:** `feature/map-offset`  
**Status:** Planning — pending Ask review

## Problem

`setMapCenterOffset` triggers OSMdroid scroll events. The current scroll suppression
(Option B hack) blocks ALL scroll events during offset animation, including legitimate
GPS `animateTo` scrolls. This breaks `_mapCenter` updates → `isWater` shows wrong value.

## Root cause

`updateMapCenter()` processes every scroll event identically — whether from user finger,
GPS `animateTo`, or `setMapCenterOffset`. The demo speed pipeline's `lastScrollMs` gets
refreshed by spurious offset-triggered scrolls, preventing `demoSpeedKnots` decay.

## Design

Gate `updateMapCenter` at the entry point: skip the entire function body if the
position hasn't changed. `LatLng` uses structural equality — identical lat/lon → `==` true.

### GPS mode

- `animateTo` scrolls change `mapCenter` → guard passes → pipeline runs
- `computeDemoSpeed` skipped (`!gpsMode` guard already exists)
- Shore, depth, zones all update correctly

### Demo mode

- User drags change `mapCenter` → guard passes → `computeDemoSpeed` runs
- `setMapCenterOffset` scrolls have SAME `mapCenter` → guard returns early
- `lastScrollMs` not refreshed → `demoSpeedKnots` decays naturally when user stops

## Changes

### 1. `NavigationViewModel.kt` — 1 line added

```kotlin
fun updateMapCenter(latitude: Double, longitude: Double) {
    val newCenter = LatLng(latitude, longitude)
    if (_mapCenter.value == newCenter) return  // no change — skip pipeline
    _mapCenter.value = newCenter
    // ... rest unchanged (persist, computeDemoSpeed, feedDemoPosition)
}
```

### 2. `MapScreen.kt` — remove scroll suppression (~10 lines)

In `CoastlineMapView`:

**Remove** state variable:
```kotlin
val suppressScrollCallbacks = remember { mutableStateOf(false) }  // DELETE
```

**Remove** from factory `setMapCenterOffset` call:
```kotlin
// BEFORE:
if (centerOffsetYPx != 0) {
    suppressScrollCallbacks.value = true
    setMapCenterOffset(0, centerOffsetYPx)
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        suppressScrollCallbacks.value = false
    }
}
// AFTER:
if (centerOffsetYPx != 0) {
    setMapCenterOffset(0, centerOffsetYPx)
}
```

**Remove** from MapListener:
```kotlin
// BEFORE:
override fun onScroll(event: ScrollEvent): Boolean {
    if (suppressScrollCallbacks.value) return false
    val geo = this@apply.mapCenter
    onCenterChanged(geo.latitude, geo.longitude)
    return false
}
// AFTER:
override fun onScroll(event: ScrollEvent): Boolean {
    val geo = this@apply.mapCenter
    onCenterChanged(geo.latitude, geo.longitude)
    return false
}
```

**Remove** from LaunchedEffect:
```kotlin
// BEFORE:
LaunchedEffect(centerOffsetYPx, localMapView.value) {
    val mv = localMapView.value ?: return@LaunchedEffect
    suppressScrollCallbacks.value = true
    mv.setMapCenterOffset(0, centerOffsetYPx)
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        suppressScrollCallbacks.value = false
    }
}
// AFTER:
LaunchedEffect(centerOffsetYPx, localMapView.value) {
    localMapView.value?.setMapCenterOffset(0, centerOffsetYPx)
}
```

### 3. `MapScreen.kt` — same, zoom listener

```kotlin
// BEFORE:
override fun onZoom(event: ZoomEvent): Boolean {
    if (suppressScrollCallbacks.value) return false
    ...
}
// AFTER:
override fun onZoom(event: ZoomEvent): Boolean {
    ...
}
```

## Net delta

| File | + | − |
|---|---|---|
| `NavigationViewModel.kt` | 1 line | 0 |
| `MapScreen.kt` | 0 | ~10 lines |

## Verification

- [ ] Build passes
- [ ] GPS mode: offset active, boat shows correct isWater
- [ ] GPS mode: depth, shore distance, zone lookups all correct at GPS position
- [ ] Demo mode: offset grows with pan speed, decays to 0 when panning stops
- [ ] Demo mode: no feedback loop — offset doesn't self-sustain
- [ ] Scroll events flow normally (no suppression side effects)
