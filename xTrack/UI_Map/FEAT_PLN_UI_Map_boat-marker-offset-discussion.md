<!-- scope: feature -->
# Boat Marker Offset — Top-Center at Map Center

## Current Layout

The map overlay stack in [`MapContent`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:724) is a single `Box(modifier.clipToBounds())` containing:

```
┌─────────────────────────────────────┐
│  CoastlineMapView (fillMaxSize)     │  ← the map itself, drawn by osmdroid
│                                     │
│  DirectionLine (fillMaxSize)        │  ← dashed line from center upward
│                                     │
│  CenterMarkerOverlay                │  ← Box aligned at Alignment.Center
│   ┌──────────┐                      │     size = finalSizeDp (e.g. 64dp)
│   │ 🚤       │                      │     Image fills the Box
│   │  ↑ arrow │                      │     Canvas arrow from BOAT_TIP_OFFSET (5% from top) upward
│   └──────────┘                      │
│                                     │     → image center = map center
│                                     │     → arrow emerges from boat's bow (near image top)
│                                     │     → direction line from screen center upward
└─────────────────────────────────────┘
```

**Key constants** ([lines 1205-1244](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1205)):
- `BOAT_BASE_DP = 32.0` at `REF_ZOOM = 12.0`
- `ZOOM_EXPONENT = 0.45` → marker grows ~37% per zoom level
- `BOAT_TIP_OFFSET = 0.05` → arrow starts 5% from image top edge

## Desired Change

> Move the boat marker down by 1/2 its size so the map center aligns with the top-center of the marker image. The cap arrow and direction line remain at the map center (unchanged).

```
Before:                    After:
┌──────────┐               ┌──────────┐
│ 🚤       │  ← center     │          │  ← map center (was image center)
│  ↑ arrow │               │ ↑ arrow  │     arrow still here
└──────────┘               ├──────────┤
                           │ 🚤       │  ← image shifted down by half height
                           │          │     image top-center = map center
                           └──────────┘
```

## Architectural Constraint

The [`CenterMarkerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1263) composable bundles **both** the boat marker image and the cap arrow into one `Box`:

```kotlin
Box(modifier = modifier.size(finalSizeDp)) {    // ← aligned at Center in parent
    Image(painter = ..., modifier = Modifier.fillMaxSize())
    if (showArrow) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // draw arrow from BOAT_TIP_OFFSET upward
        }
    }
}
```

Because the arrow and image share the same coordinate space, simply offsetting the entire Box would shift the arrow too. They must be **decoupled**.

## Proposed Approach

### 1. Decouple arrow from marker in `CenterMarkerOverlay`

Split into two independently positioned children inside the outer Box:

```kotlin
// Outer Box stays at Alignment.Center in parent (map center)
Box(modifier = modifier.align(Alignment.Center)) {

    // ── Boat marker: shifted down by half its height ──
    Image(
        painter = painterResource(id = drawableId),
        contentDescription = description,
        modifier = Modifier
            .size(finalSizeDp)
            .offset(y = finalSizeDp / 2),   // ← shift down: image top-center = map center
        contentScale = ContentScale.Fit
    )

    // ── Cap arrow: stays centered at map center ──
    if (showArrow) {
        val arrowColor = ComposeColor(ZoneConfig.capArrowColor)
        Canvas(modifier = Modifier.size(finalSizeDp)) {
            val cX = size.width / 2
            val startY = 0f                         // ← top of canvas = map center
            val arrowLenPx = arrowDp.toPx()
            val endY = startY - arrowLenPx          // ← extends upward (negative Y in canvas space)
            // Draw line + arrowhead from (cX, startY) to (cX, endY)
        }
    }
}
```

**Key detail:** The arrow canvas is sized `finalSizeDp` and centered, so its top edge is at `mapCenter.y - finalSizeDp/2`. The arrow starts at `startY = 0` (canvas top) and goes **upward** (negative Y = outside the canvas). 

To fix this, the arrow canvas needs different sizing — it should only be tall enough to contain the arrow, not the full marker size:

```kotlin
Canvas(modifier = Modifier
    .align(Alignment.Center)
    .width(finalSizeDp)
    .height(arrowDp + 4.dp)    // just tall enough for the arrow + a bit of padding
) {
    // Arrow draws from bottom of canvas (map center) upward
    val cX = size.width / 2
    val startY = size.height    // canvas bottom = map center (since canvas is centered)
    val endY = startY - arrowLenPx
    // draw line + arrowhead
}
```

### 2. DirectionLine — no changes needed

[`DirectionLine`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1352) draws from `(cX, cY)` = `(size.width/2, size.height/2)` (screen center) upward. Since `map center = screen center`, this is already correct and needs no modification.

### 3. `BOAT_TIP_OFFSET` — remove or set to 0

The constant [`BOAT_TIP_OFFSET = 0.05`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1242) defined the arrow's starting point near the image top (the boat's bow). After the change, the arrow starts from the map center directly, so this constant is no longer needed.

## Summary of Changes

| File | Change |
|---|---|
| [`MapScreen.kt:1263-1343`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1263) | Decouple Image and Canvas in `CenterMarkerOverlay` — Image offset by `finalSizeDp/2` downward, Canvas (arrow) stays centered |
| [`MapScreen.kt:1242`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1242) | Remove `BOAT_TIP_OFFSET` constant (no longer used) |
| [`MapScreen.kt:1352`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1352) | No change — `DirectionLine` already draws from center |

## Visual Result

```
        ┌──────┐
        │  ↑   │  ← cap arrow from map center upward
        │      │
        ├──────┤  ← map center (GPS position) = top-center of boat
        │      │
        │  🚤  │  ← boat marker shifted down by half
        │      │
        └──────┘
        ════════  ← direction line (dashed, from center upward, unchanged)
```

The map now shows more water **ahead** of the boat (below the marker on screen) and less behind (above the marker), which matches the standard nautical chart convention where the vessel icon is positioned with its bow at the GPS antenna position.

