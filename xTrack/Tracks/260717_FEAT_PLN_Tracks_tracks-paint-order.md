# Plan: Tracks Paint Order — Flip Z-Order + Highlight-to-Top

**Created:** 2026-07-17 14:28 UTC
**Branch:** feature/tracks-paint-order
**Scope:** Single file — `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Current State

```
z-order (bottom → top): newest history → ... → oldest history → newest pinned → ... → oldest pinned → active
```

Transparency gradient (newest=opaque, oldest=transparent) partially masks the inverted z-order, but the layering is wrong: oldest tracks render on top of newer ones. Highlighted track gets gold glow but stays in its original z-order slot.

## Target State

```
z-order (bottom → top): oldest history → ... → newest history → oldest pinned → ... → newest pinned → active → highlighted
```

- History + pinned: newest-on-top (flipped)
- Highlighted track: moved above active track (absolute top)
- Transparency index calculation preserved (index 0 = newest = most opaque)

## Changes (MapScreen.kt only)

### 1. History tracks — flip addition order (lines ~958–1060)

**Current:** loop iterates `sortedByDescending` (newest first), adds each overlay directly to `mv.overlays`. Newest added first → renders at bottom.

**Change:** accumulate all overlays into a `MutableList<Overlay>`, reverse, then `mv.overlays.addAll()`.

```kotlin
// BEFORE (conceptual)
for ((index, summary) in sortedDesired.withIndex()) {
    // ... build polylines + auto-markers ...
    mv.overlays.add(solidPolyline)
    mv.overlays.add(iconMarker)
}

// AFTER
val historyOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
for ((index, summary) in sortedDesired.withIndex()) {
    // ... build polylines + auto-markers (unchanged) ...
    historyOverlays.add(solidPolyline)  // was mv.overlays.add(...)
    historyOverlays.add(iconMarker)     // was mv.overlays.add(...)
}
historyOverlays.reverse()
mv.overlays.addAll(historyOverlays)
```

- `computeTrackPolylineAppearance` index parameter unchanged — still 0=newest.
- Highlighted track appearance (gold+shadow) unchanged — still rendered in its normal group slot, then moved to top via step 3.
- Auto-marker 🕐 pins for history tracks move with their parent track.

### 2. Pinned tracks — flip addition order (lines ~1068–1164)

Same pattern as history:

```kotlin
// BEFORE
for ((index, summary) in pinnedSummaries.withIndex()) {
    // ... build polylines + auto-markers ...
    mv.overlays.add(solidPolyline)
    mv.overlays.add(iconMarker)
}

// AFTER
val pinnedOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
for ((index, summary) in pinnedSummaries.withIndex()) {
    // ... build polylines + auto-markers (unchanged) ...
    pinnedOverlays.add(solidPolyline)
    pinnedOverlays.add(iconMarker)
}
pinnedOverlays.reverse()
mv.overlays.addAll(pinnedOverlays)
```

### 3. Highlight-to-top (after active track move, ~line 1173)

After the existing active-track move-to-end block (lines 1166–1173), insert:

```kotlin
// Move highlighted track above active (z-order: ... → active → highlighted)
if (highlightedTrackId != null) {
    val highlightedOverlays = mv.overlays.filter { overlay ->
        val polyTitle = (overlay as? org.osmdroid.views.overlay.Polyline)?.title
        if (polyTitle != null) {
            polyTitle == "track_hist_$highlightedTrackId" ||
            polyTitle == "track_pin_$highlightedTrackId"
        } else {
            val markerTitle = (overlay as? org.osmdroid.views.overlay.Marker)?.title
            markerTitle?.startsWith("track_auto_hist_${highlightedTrackId}_") == true ||
            markerTitle?.startsWith("track_auto_pin_${highlightedTrackId}_") == true
        }
    }
    mv.overlays.removeAll(highlightedOverlays)
    mv.overlays.addAll(highlightedOverlays)
}
```

Title matching strategy:
- **Polyline titles** (`track_hist_<id>`, `track_pin_<id>`): exact `==` match — no suffix on polyline titles
- **Marker titles** (`track_auto_hist_<id>_<seq>`, `track_auto_pin_<id>_<seq>`): `startsWith` with trailing `_` delimiter to avoid prefix collisions between IDs
- Active track (`track_recording`) is NOT a candidate — highlightedTrackId always refers to a finalized track

### 4. No changes to

- `computeTrackPolylineAppearance` — index semantics preserved
- Active track rendering (lines 1179–1250) — LaunchedEffect keyed on recorder state, separate from history/pinned LaunchedEffect
- `highlightedTrackId` set/clear logic — already correct in track list action handlers
- MarkerOverlay — user markers are separate LaunchedEffect, not in scope

## Z-Order Verification Table

| Layer | Bottom/Top | Group internal order |
|---|---|---|
| History tracks | Bottom | oldest → newest |
| Pinned tracks | Above history | oldest → newest |
| Active track | Above pinned | single polyline |
| Highlighted track | Absolute top | gold glow + shadow (2 layers) |

## Implemented

**Date:** 2026-07-17 16:15 UTC
**Build:** ✅ `assembleDebug` passes

Three changes in `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`:

1. **History tracks z-order flip** — Accumulated all history overlays (solid segments, gap lines, auto-marker pins) into `historyOverlays: MutableList<Overlay>`, reversed, then `mv.overlays.addAll()`. Oldest now at bottom, newest at top. `computeTrackPolylineAppearance` index unchanged.

2. **Pinned tracks z-order flip** — Same `pinnedOverlays` accumulation + reverse pattern.

3. **Highlight-to-top** — After active track move-to-end, if `highlightedTrackId != null`, filters overlays by `Polyline.title ==` (exact) and `Marker.title.startsWith` (with trailing `_` delimiter), removes + re-adds at end.

Final z-order: `oldest history → newest history → oldest pinned → newest pinned → active → highlighted`
