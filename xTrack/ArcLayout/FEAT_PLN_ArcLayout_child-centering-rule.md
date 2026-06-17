<!-- scope: feature -->
# FanLayout — Child Button Centering Design Rule (v2: template-aligned)

## v1 Problem (reverted)

The first fix added a `slotOffsetDeg` to slide all 4 children by 18° (θ/2), placing them
at **216°, 252°, 288°, 324°** — between the original template slots. This breaks the
"4 children fill **4 of 5 slots**" requirement: children must land **on** template slot
positions, not between them. An offset between slots creates uneven chord spacing that
looks like maxCount=6 (non-uniform gaps).

## v2 Fix

Keep children **on the 5-slot template positions** (198°, 234°, 270°, 306°, 342°).
The empty slot is not at either end — it's slot **2 (270°)**, the center position.
Children occupy slots **0, 1, 3, 4**:

```
Bottom (180°)                       Top (0°/360°)
  │                                    │
  │   [child slot 4 @ 342°]            │
  │   [child slot 3 @ 306°]            │
  │   [empty slot  2 @ 270°]  ← gap    │
  │   [child slot 1 @ 234°]            │
  │   [child slot 0 @ 198°]            │
  │                                    │
```

- Children on **actual template positions** ✓ ("fill 4 of 5 slots")
- **No empty at far end** ✓ (slots 0 and 4 are filled)
- **Vertically centered**: avg = (198+234+306+342)/4 = **270°** ✓
- **Clean**: 1 empty slot centered, not at edge ✓

---

# FanLayout — Child Button Centering Design Rule


## Problem

With `maxCount=5, currentCount=4, direction=LEFT`:

The 5-slot template spans **144°** (4 × 36°), centered in a **180°** semicircle with **18°** margins on each side. Four children occupy slots 0–3 via [`children.take(count)`](app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:132), leaving slot 4 (the top-most position at **342°**) empty.

Result: the 4 buttons cluster **toward the bottom** of the vertical arc — not vertically centered.

```
Top of screen               Bottom of screen
    |                            |
    |  [empty slot 4 @ 342°]     |
    |  [child @ 306°]            |
    |  [child @ 270°]  ← center  |
    |  [child @ 234°]            |
    |  [child @ 198°]            |
    |                            |
```

**Average occupied angle:** (198°+234°+270°+306°)/4 = **252°**, which is **18° below** the true center (270°).

---

## Design Rule

> **When `currentCount < maxCount`, offset the child group by `(maxCount - currentCount) × θ / 2` to center the occupied buttons within the template arc.**

This distributes the empty slot(s) evenly between both ends of the arc — "½ empty at the top, ½ empty at the bottom."

### Formula (single change to existing algorithm)

```kotlin
// Existing: center the maxCount template in 180° semicircle
val maxArcSpan = (config.maxCount - 1) * config.thetaDeg
val offsetDeg = (180f - maxArcSpan) / 2f
val startAngleDeg = config.baseAngleDeg - 90f + offsetDeg

// NEW: offset children to center them when currentCount < maxCount
val slotOffsetDeg = if (config.currentCount < config.maxCount) {
    (config.maxCount - config.currentCount) * config.thetaDeg / 2f
} else 0f

// Adjusted base angle
val adjustedStartDeg = startAngleDeg + slotOffsetDeg
val baseAngleRad = Math.toRadians(adjustedStartDeg.toDouble())
```

### Application to `maxCount=5, currentCount=4, θ=36°`

| Property | Before (current) | After (fixed) |
|---|---|---|
| Offset | `0°` | `(5-4) × 36°/2 = 18°` |
| Start angle | `198°` | `216°` |
| Child positions | `198°, 234°, 270°, 306°` | `216°, 252°, 288°, 324°` |
| Average angle | `252°` (18° low) | `270°` ✅ (dead center) |
| Empty above | `342°-306° = 36°` (1 full slot) | `342°-324° = 18°` (½ slot) |
| Empty below | `198°-198° = 0°` (none) | `216°-198° = 18°` (½ slot) |

### Visual result

```
Top of screen               Bottom of screen
    |                            |
    |  ½ empty (18°)             |
    |  [child @ 324°]            |
    |  [child @ 288°]            |
    |  [child @ 252°]            |
    |  [child @ 216°]            |
    |  ½ empty (18°)             |
    |                            |
```

- **5-slot template** preserved: θ=36°, 144° span, 18° margins ✓
- **4 children, 1 empty** ✓
- **Empty slot distributed** as ½ at each end, NOT at the far end ✓
- **Vertically centered**: equal 18° (θ/2) empty above and below ✓
- **Clean**: no extra space, no gap in the middle ✓

---

## General Case: All `currentCount` Values for `maxCount=5`

| currentCount | Child positions | Above empty | Below empty | Centered? |
|---|---|---|---|---|
| 5 | 198°, 234°, 270°, 306°, 342° | 0° | 0° | ✅ |
| 4 | **216°, 252°, 288°, 324°** | **18°** | **18°** | ✅ |
| 3 | **234°, 270°, 306°** | **36°** | **36°** | ✅ |
| 2 | **252°, 288°** | **54°** | **54°** | ✅ |
| 1 | **270°** | **72°** | **72°** | ✅ |

All cases keep the buttons centered in the template arc with equal empty space at both ends.

---

## Code Change Scope

Single-file change in [`FanLayout.kt`](app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt:119-124):

**Lines 119-124** — replace the current `startAngleDeg` derivation to include the centering offset:

```kotlin
// Current (line 119-123):
val maxArcSpan = (config.maxCount - 1) * config.thetaDeg
val offsetDeg = (180f - maxArcSpan) / 2f
val startAngleDeg = config.baseAngleDeg - 90f + offsetDeg
val baseAngleRad = Math.toRadians(startAngleDeg.toDouble())

// Fixed:
val maxArcSpan = (config.maxCount - 1) * config.thetaDeg
val offsetDeg = (180f - maxArcSpan) / 2f
val startAngleDeg = config.baseAngleDeg - 90f + offsetDeg
val slotOffsetDeg = if (config.currentCount < config.maxCount) {
    (config.maxCount - config.currentCount) * config.thetaDeg / 2f
} else 0f
val baseAngleRad = Math.toRadians((startAngleDeg + slotOffsetDeg).toDouble())
```

**No other files need changes.** FanConfig, FanIconComponents, MapScreen — all remain untouched. The fix is purely in the child-positioning math.

