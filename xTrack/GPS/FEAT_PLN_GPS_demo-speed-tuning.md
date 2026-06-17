<!-- scope: feature -->
# Demo Mode Speed Tuning

> Discussion for demo speed computation in [`CoastlineViewModel.kt:514`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:514).

## Problem

Current formula produces unrealistic speeds even with `/10` divisor:

```kotlin
val speedMps = dist / (elapsed / 1_000.0) / 10.0
val knots = speedMps * MPS_TO_KNOTS  // 1.943844
```

At zoom 14-16, a finger drag of 1cm ≈ 500m ground distance. Even a slow drag over 500ms:
```
knots = 500 / 0.5 / 10 × 1.94 = 194 kn
```

The `/10` divisor is a rough linear hack that doesn't work across the full drag-speed range.

## Solution: sqrt compression

Replace the linear `/10` with `sqrt(rawMps × scale)` — a natural compression curve that keeps low speeds responsive and high speeds bounded.

### Formula

```kotlin
val rawMps = dist / (elapsed / 1_000.0)
val knots = sqrt(rawMps * DEMO_SPEED_SCALE).coerceIn(0.0, MAX_DEMO_KNOTS)
```

### Calibration with `DEMO_SPEED_SCALE = 2.0`, `MAX_DEMO_KNOTS = 50.0`

| Drag | Ground dist | Time | rawMps | knots | Feels like |
|------|-------------|------|--------|-------|-----------|
| Very gentle tap | 5m | 1s | 5 | 3.2 kn | Drifting |
| Slow finger drag | 20m | 1s | 20 | 6.3 kn | Slow cruise |
| Moderate drag | 100m | 0.5s | 200 | 20 kn | Planing |
| Brisk swipe | 300m | 0.3s | 1000 | 45 kn | Fast |
| Fast flick | 500m | 0.2s | 2500 | 50 kn (capped) | Max |
| Zoomed far out (z11) | Same flick → 50m | 0.2s | 250 | 22 kn | Lower by distance |

The sqrt curve naturally handles different zoom levels because:
- At high zoom (z16): same finger movement = less ground distance = lower rawMps = lower knots
- At low zoom (z12): same finger movement = more ground distance = higher rawMps = higher knots
- But the sqrt dampens extremes, so it never goes above 50 kn

### Comparison

| Drag speed | Current (/10) | Proposed (sqrt) |
|-----------|--------------|-----------------|
| Very gentle | 19 kn | 3.2 kn |
| Moderate | 194 kn | 20 kn |
| Fast flick | 970 kn | 50 kn (capped) |

## Code change

File: [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:514)

```kotlin
// Replace lines 523-525:
// val speedMps = dist / (elapsed / 1_000.0) / 10.0
// _demoSpeedKnots.value = (speedMps * MPS_TO_KNOTS).toFloat()
val rawMps = dist / (elapsed / 1_000.0)
val knots = sqrt(rawMps * DEMO_SPEED_SCALE).coerceIn(0.0, MAX_DEMO_KNOTS)
_demoSpeedKnots.value = knots.toFloat()
```

Add constants to companion object:
```kotlin
private const val DEMO_SPEED_SCALE = 2.0
private const val MAX_DEMO_KNOTS = 50.0
```

Also need `import kotlin.math.sqrt` at the top of the file.

