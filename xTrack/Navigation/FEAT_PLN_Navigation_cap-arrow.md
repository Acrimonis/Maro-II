# Navigation — Cap Arrow Design

> Discussion for feature [`Navigation`](xTrack/Navigation/FEAT_DSC_Navigation.md), subfeature `cap`.

## Concept

A heading indicator arrow drawn at the tip of the boat marker:

- **Direction** = GPS bearing (same heading that drives `mapOrientation`)
- **Length** = predicted position in 5 seconds at current speed

Boat icon: `▲` with an arrow `→` extending forward.

```
      ────►     (arrow length = distance in 5s at current speed)
      ▲
     ╱ ╲
    ╱   ╲        (boat marker icon, centered on map)
```

## Math

Arrow length in screen pixels = `speed_mps × 5s × pixels_per_meter_at_zoom`

At zoom Z on osmdroid:
```
ground_resolution = 156543.03 × cos(lat) / 2^Z  (meters/pixel)
pixels = meters / ground_resolution
```

**Length at various speeds** (zoom 15, ~43°N = ~4.8 m/pixel):

| Speed | m/s | 5s distance | Arrow pixels |
|-------|-----|-------------|-------------|
| 1 knot | 0.5 | 2.5 m | ~0.5 px → minimum 8 dp |
| 5 knots | 2.6 | 13 m | ~2.7 px → minimum 8 dp |
| 10 knots | 5.1 | 26 m | ~5.4 px → minimum 8 dp |
| 20 knots | 10.3 | 51 m | ~10.6 px |
| 30 knots | 15.4 | 77 m | ~16 px |

### Minimum and maximum lengths

- **Min**: 8 dp (so the arrow is visible even at low speed)
- **Max**: 80 dp (so it doesn't span across the whole screen at high speed on low zoom)
- At zoom < 13 the arrow becomes tiny regardless → scale exponentially with zoom

## Drawing

On the existing `CenterMarkerOverlay` Canvas (or a new overlay composable):

```kotlin
// Position: top-center of the boat marker icon
val arrowStartX = centerX
val arrowStartY = centerY - boatIconHeight / 2  // tip of the boat marker
val arrowEndX = arrowStartX + sin(bearingRad) * arrowLengthPx
val arrowEndY = arrowStartY - cos(bearingRad) * arrowLengthPx

drawLine(
    color = Color.Blue,
    start = Offset(arrowStartX, arrowStartY),
    end = Offset(arrowEndX, arrowEndY),
    strokeWidth = 3.dp.toPx(),
    cap = StrokeCap.Round
)
// Small arrowhead triangle at the end
```

## States

| Situation | Behaviour |
|-----------|-----------|
| GPS active, moving (speed > 0.5 m/s) | Arrow visible, length proportional to speed |
| GPS active, stationary | No arrow (or a tiny dot at minimum length) |
| Demo mode, moving | Arrow visible using demo speed + bearing |
| Demo mode, stationary | No arrow |
| No GPS fix / no bearing | No arrow |

## Interaction with existing code

The `cameraUpdates` flow in `CoastlineViewModel.kt:238` already emits `CameraTarget(position, bearingDeg)` at up to 25 fps. The `speedKnots` state is also available (line 146 of MapScreen.kt). Both update at the same rate — the arrow direction and length will be smooth.
