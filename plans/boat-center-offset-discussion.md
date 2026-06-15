# Moving Map Reference to 66% Vertical

## Goal

Move the boat marker from the visual map center (50%) to 66% down from the top, giving 2× more map space ahead than behind the boat.

## Current Behavior

[`MapScreen.kt:316-327`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:316):

```kotlin
mv.controller.animateTo(point)  // centers boat at 50%
```

The map controller's `animateTo(GeoPoint)` always centers the given point at the **visual center** of the MapView (50% horizontally, 50% vertically).

## The Offset Calculation

```
┌─────────────────────────┐
│                         │
│    50% (current)        │  ← map controller centers here
│                         │
│        ⛵                │  ← boat currently at center
│                         │
│    66% (desired)        │  ← we want the boat here
│                         │
│    Dashboard             │
└─────────────────────────┘

Offset = 0.16 × screenHeight (from 50% → 66%)
```

To make the boat appear at 66%, the map center needs to shift **northward** (up on the map) by the geographic equivalent of 0.16× screen height.

## The Correct Approach

Use OSMdroid's [`Projection`](https://github.com/osmdroid/osmdroid/blob/master/osmdroid-android/src/main/java/org/osmdroid/views/Projection.java) to convert between screen pixels and geographic coordinates. This correctly handles map rotation and zoom:

```kotlin
// In the auto-follow LaunchedEffect, before calling animateTo:
val projection = mv.projection
val screenPoint = projection.toPixels(point, null)
val mapViewHeight = mv.height  // pixels
screenPoint.offset(0, -(mapViewHeight * 0.16f).toInt())  // shift up by 16% of height
val offsetCenter = projection.fromPixels(screenPoint.x.toDouble(), screenPoint.y.toDouble())
mv.controller.animateTo(offsetCenter)
```

### Why toPixels/fromPixels Works

- `toPixels(GeoPoint, Point)` converts a geographic coordinate to screen pixel coordinates, accounting for zoom and rotation
- We shift the screen point upward by 16% of the MapView's height
- `fromPixels(x, y)` converts the shifted screen point back to a geographic coordinate
- Pass this offset coordinate to `animateTo()` — the map will center on the offset point, and the boat (at its original position) will appear at 66%

### Edge Case: Map Rotation

When the map is rotated (`mv.mapOrientation = -bearingDeg`), the toPixels/fromPixels conversion **still works correctly** because the Projection accounts for rotation. The "up" shift in screen space always moves the map content downward visually, regardless of whether north is at the top.

## Implementation Points

| Aspect | Detail |
|--------|--------|
| **Where to change** | [`MapScreen.kt:316-327`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:316) — the `animateTo` calls in the GPS auto-follow `LaunchedEffect` |
| **Where to change (initial)** | [`MapScreen.kt:1169`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1169) — the initial `setCenter` when the MapView is created |
| **Offset ratio** | 0.16 = 0.66 - 0.50. Configurable if we want it adjustable later. |
| **Orientation aware** | In landscape, the dashboard is on the left (not bottom), so the vertical center offset needs to consider the dashboard width. Same ratio, just the available map height changes. |
| **Boat marker position** | This only changes the map center — the boat marker itself stays drawn at the boat's geographic position, which is now at 66% on screen. No marker position change needed. |

## Files to Change

- [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — GPS auto-follow `animateTo` calls + initial `setCenter`

