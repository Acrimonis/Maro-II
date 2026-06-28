# FEAT_PLN: Render Pinned Track Polylines on Map

**Feature:** BoatTrace
**Subfeature:** track-settings
**Date:** 2026-06-28
**Branch:** feature/track-idling
**Status:** plan

## Problem

Pinned track colors/transparency exist in settings and can be configured, but pinned track polylines never appear on the map. Only history tracks (`track_hist_`) and the active recording track (`track_recording`) have map overlay rendering at [`MapScreen.kt:630-691`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:630).

## Root Cause

The pinned tracks feature spec says "Z-order: active > pinned > past", but the map rendering [`LaunchedEffect` at line 630](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:630) only handles history (non-pinned) tracks. No code draws `track_pin_` polylines.

Additionally, the history rendering does NOT exclude pinned tracks — if a pinned track has `visibleOnMap = true`, it would be rendered as a history track too (wrong colors, double-drawn if we add pinned rendering).

## Fix Plan

### Single LaunchedEffect approach

Add pinned track rendering into the **existing** history LaunchedEffect at line 630. This avoids z-order race conditions between separate LaunchedEffects.

### Step 1 — Expand LaunchedEffect keys

Add pinned color/transparency to the key set at line 630:
```kotlin
LaunchedEffect(mapView, showSettings, appSettings.tracksVisible, appSettings.trackingRenderNb,
    appSettings.trackingColorPastFrom, appSettings.trackingColorPastTo,
    appSettings.trackingTransparencyNewest, appSettings.trackingTransparencyOldest,
    appSettings.trackingColorPinnedFrom, appSettings.trackingColorPinnedTo,
    appSettings.trackingTransparencyPinnedNewest, appSettings.trackingTransparencyPinnedOldest,
    trackSummaries)
```

### Step 2 — Exclude pinned tracks from history rendering

Change the history filter from:
```kotlin
trackSummaries.filter { it.visibleOnMap }
```
To:
```kotlin
trackSummaries.filter { it.visibleOnMap && !it.pinned }
```

This prevents pinned tracks from being double-rendered (as history + as pinned).

### Step 3 — Add pinned track rendering block

After the history track rendering loop (after line 688, before `renderedTrackIds.value = desiredIds`), add:

```kotlin
// ── Pinned tracks: always render, separate colors/transparency ──
val toRemovePinned = mv.overlays.filter { overlay ->
    (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_pin_") == true
}
mv.overlays.removeAll(toRemovePinned)

val pinnedSummaries = if (appSettings.tracksVisible) {
    trackSummaries.filter { it.pinned }.sortedByDescending { it.startTimeMs }
} else emptyList()

val pinnedTotal = pinnedSummaries.size
for ((index, summary) in pinnedSummaries.withIndex()) {
    val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
    if (track.trackPoints.isEmpty()) continue

    val appearance = computeTrackPolylineAppearance(
        index = index,
        total = pinnedTotal,
        transparencyNewest = appSettings.trackingTransparencyPinnedNewest,
        transparencyOldest = appSettings.trackingTransparencyPinnedOldest,
        colorFrom = appSettings.trackingColorPinnedFrom,
        colorTo = appSettings.trackingColorPinnedTo,
        strokeWidth = 6f
    )

    val polyline = org.osmdroid.views.overlay.Polyline().apply {
        title = "track_pin_${summary.id}"
        outlinePaint.color = appearance.argb
        outlinePaint.strokeWidth = appearance.strokeWidth
        setPoints(track.trackPoints.map { pt ->
            org.osmdroid.util.GeoPoint(pt.lat, pt.lon)
        })
    }
    mv.overlays.add(polyline)
}
```

### Z-order

Since pinned polylines are added AFTER history polylines in the same LaunchedEffect, they sit at higher overlay indices → render on top of history tracks. However, the active recording track (`track_recording`) is managed by a separate LaunchedEffect (line 694) — after the pinned block adds new overlays at the end, `track_recording` could be buried below pinned tracks.

To fix, after the pinned rendering loop, re-position the active track to the top:

```kotlin
// Ensure active track stays on top of pinned
val activePolyline = mv.overlays.firstOrNull {
    (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
}
if (activePolyline != null) {
    mv.overlays.remove(activePolyline)
    mv.overlays.add(activePolyline)
}
```

Final z-order: **history (bottom) → pinned (middle) → active (top)**.

### Step 4 — Build

Run `apk-build.bat`.

## Files Touched
| File | Change |
|------|--------|
| `app/.../ui/map/MapScreen.kt` | Add pinned color/transparency to LaunchedEffect keys, filter pinned from history, add pinned rendering block |
