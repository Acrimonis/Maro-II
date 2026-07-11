# Marker Highlight — Dual-Polyline Outline (consistency with track change)

**Created:** 2026-07-11
**Branch:** `feature/track-hilite`
**Status:** planned

## Problem

Track highlight now uses dual-polyline outline (dark 16px under-stroke + gold 8px core). Marker highlight still uses flat gold (`COLOR_HIGHLIGHT = 0xFFFFD700`) with no outline — inconsistent and suffers the same visibility issues.

## Solution

Apply the same dual-layer approach to marker highlight rendering in [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt). When `marker.id == highlightedMarkerId`, render a dark under-stroke before each gold geometry element.

### Constants to add
```kotlin
private val COLOR_HIGHLIGHT_UNDER = 0xCC000000.toInt()  // dark under-stroke
private const val HIGHLIGHT_UNDER_STROKE_ADD = 6f        // extra width for under-stroke
```

### Changes by geometry type

#### 1. Pin dots
In the `Pin` branch, when highlighted, add a dark dot Marker (slightly larger radius) before the gold dot:
```kotlin
if (drawGeometry && !skipDots) {
    if (marker.id == highlightedMarkerId) {
        addPinOverlay(mv, geom, "${marker.id}_ul", COLOR_HIGHLIGHT_UNDER, 
            createDotBitmap(COLOR_HIGHLIGHT_UNDER, radiusMultiplier = 1.5f), ...)
    }
    addPinOverlay(mv, geom, marker.id, baseColor, dotBitmap, ...)
}
```

#### 2. Circle outline  
In `addCircleOverlay`, when highlighted, add dark circle polyline before gold. Modify call site in main loop to pass `isHighlighted` flag.

#### 3. Corridor centerline, parallels, caps
In `addCorridorOverlay`, when highlighted, add dark versions before gold:
- Dark centerline at `2f * strokeMultiplier + HIGHLIGHT_UNDER_STROKE_ADD`
- Dark parallels at `4f * strokeMultiplier + HIGHLIGHT_UNDER_STROKE_ADD`  
- Dark caps at `4f * strokeMultiplier + HIGHLIGHT_UNDER_STROKE_ADD`

#### 4. Corridor p1/p2 dots
Same as pin dots — dark dot before gold when highlighted.

### Implementation strategy

Add an `isHighlighted: Boolean = false` parameter to:
- `addCircleOverlay` → passes to `addCirclePolyline`
- `addCorridorOverlay` → passes to `addCorridorParallels`, `addSemiCircleCaps`, centerline `buildPolyline`, and pin dots
- `addPinOverlay` → adds dark dot before gold when true

Each function, when `isHighlighted = true`, adds dark under-stroke polylines/dots before the gold ones. The dark versions use `COLOR_HIGHLIGHT_UNDER` color and `+ HIGHLIGHT_UNDER_STROKE_ADD` stroke width.

### Files touched

| File | Change |
| [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) | Add `COLOR_HIGHLIGHT_UNDER`, `HIGHLIGHT_UNDER_STROKE_ADD`. Add `isHighlighted` param to `addPinOverlay`, `addCircleOverlay`, `addCorridorOverlay`. Wire dark under-strokes before gold in each function. |

## Implemented

**Date:** 2026-07-11
**Branch:** `feature/track-hilite`

All 7 change points applied to [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt):

# | Location | What changed |
|---|----------|--------------|
1 | L53-56 | Added `COLOR_HIGHLIGHT_UNDER` (0xCC000000) and `HIGHLIGHT_UNDER_STROKE_ADD` (6f) constants |
2 | `addPinOverlay` | Added `isHighlighted` param; dark under-dot at 1.5× radius, title `_ul`, no click listener |
3 | `addCircleOverlay` | Added `isHighlighted`; dark circle polyline at `4f * sm + 6f`; center dot via `addPinOverlay` |
4 | `addCorridorOverlay` | Added `isHighlighted`; dark centerline at `2f * sm + 6f`; passes to parallels/caps; p1/p2 via `addPinOverlay` |
5 | `addCorridorParallels` | Added `isHighlighted`; dark left+right lines at `sw + 6f` |
6 | `addSemiCircleCaps` | Added `isHighlighted`; dark p1+p2 caps at `sw + 6f` |
7 | Main loop | Computed `isHighlighted` per marker; passed to all geometry branches; proximity previews hard-coded `false` |

**Build:** `gradlew.bat assembleDebug` — BUILD SUCCESSFUL (41 tasks, 0 errors).
