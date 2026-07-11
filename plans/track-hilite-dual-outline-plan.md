# Track Highlight — Dual-Polyline Outline Plan

**Created:** 2026-07-11
**Branch:** `feature/track-hilite`
**Status:** planned

## Problem

Track highlight is a single gold polyline (`#FFD700`, 10px). Gold washes out on blue sea in bright sunlight and is nearly invisible on green/brown land.

## Solution

Dual-polyline outline: a dark under-stroke (16px, black 80%) + gold core (8px) rendered on top. The dark outline creates a universal contrast border visible on any background.

## Implementation

### Single file: [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

Two identical changes — one in the history track loop (line ~942), one in the pinned track loop (line ~1041).

**Current pattern (both loops):**
```kotlin
val appearance = if (summary.id == highlightedTrackId) {
    TrackPolylineAppearance(0xFFFFD700.toInt() or (0xFF shl 24), 10f)
} else {
    computeTrackPolylineAppearance(...)
}

// ... gap-splitting loop creating polylines with appearance.argb / appearance.strokeWidth
```

**New pattern:**
```kotlin
val appearances = if (summary.id == highlightedTrackId) {
    listOf(
        TrackPolylineAppearance(0xCC000000.toInt(), 16f),              // dark under-stroke
        TrackPolylineAppearance(0xFFFFD700.toInt() or (0xFF shl 24), 8f)  // gold core
    )
} else {
    listOf(computeTrackPolylineAppearance(...))
}

// For each appearance → gap-splitting loop
for (appearance in appearances) {
    // ... existing gap-splitting logic, unchanged
}
```

### Constants
- Under-stroke: `0xCC000000` = black at ~80% opacity, 16px stroke
- Gold core: `0xFFFFD700` full opacity, 8px stroke

### Why this works
osmdroid renders overlays in insertion order — first added = bottom. The dark 16px line is added first, then the gold 8px line on top. The dark border extends 4px beyond each side of the gold line, creating a visible outline on any background.

## Files Touched

| File | Change |
|------|--------|
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | History track loop: `appearance` → `appearances` list + inner loop. Pinned track loop: same change. |
