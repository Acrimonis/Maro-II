<!-- scope: feature -->
# Arc Layout Button — Design Specification

## Feature: UiThingies → arc-layout-button

---

## 1. Core Concept

A **parameterised fan layout** framework for map overlay buttons. Each fan instance is a parent button with N child buttons that fan out from behind it along a circular arc.

Children render **on top of** the parent (higher z-order). The parent is always visible; children emerge from behind it when the fan is open.

---

## 2. Fan Geometry — STRONG RULES

### 2.1 Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `θ` | Float (degrees) | Inter-button angle. The angular spacing between adjacent children as seen from the parent at center. |
| `currentCount` | Int (2..5) | How many child buttons this instance renders. |
| `direction` | FanDirection | Which way the fan opens (UP, DOWN, LEFT, RIGHT, UP_LEFT, etc.) |
| `buttonSizeDp` | Dp = 64 dp | Diameter of all buttons (matches current LayerButton). |

### 2.2 Derived Geometry

All buttons are 64 dp circles (current LayerButton size).

**Parent at center.** Children on a circle arc of radius `R` around it.

```
parent → child distance = R  (same for ALL children — EQUIDISTANT by relationship)
child ↔ child chord     = 2R × sin(θ/2)  (same for ALL adjacent pairs — EQUIDISTANT by relationship)
```

Different relationship types (parent-child vs child-child) have different numerical distances. This is INTENTIONAL. Each type is internally consistent — all parent-child distances are equal, all child-child chords are equal.

### 2.3 Radius Calculation

```
R = (buttonSizeDp + edgeGapDp) / (2 × sin(θ/2))
```

With buttonSizeDp = 64 dp and edgeGapDp = 8 dp:

| θ | R | parent→child | child↔child chord | Edge gap |
|---|-----|-------------|-------------------|----------|
| 30° | 139.1 dp | 139.1 dp | 72.0 dp | 8 dp |
| 36° | 116.5 dp | 116.5 dp | 72.0 dp | 8 dp |
| 45° | 94.1 dp | 94.1 dp | 72.0 dp | 8 dp |
| 60° | 72.0 dp | 72.0 dp | 72.0 dp | 8 dp |

### 2.4 Positioning (Centered in Arc)

For `currentCount = N` children, the N children are **centered** in the fan's directional arc:

```
startAngle = directionBaseAngle
totalArcSpan = (N - 1) × θ
offset = (referenceArcSpan - totalArcSpan) / 2
childPosition[i] = startAngle + offset + i × θ    for i = 0 .. N-1
```

Where `referenceArcSpan` is the total span for maxCount (default 180° semicircle).

### 2.5 Direction Mapping

| FanDirection | Base angle | Opens toward |
|-------------|-----------|-------------|
| `UP` | -90° | Upward from bottom anchor |
| `DOWN` | +90° | Downward from top anchor |
| `LEFT` | 0° | Leftward from right anchor |
| `RIGHT` | 180° | Rightward from left anchor |
| `UP_LEFT` | -45° | Toward top-left |
| `UP_RIGHT` | -135° | Toward top-right |
| `DOWN_LEFT` | +45° | Toward bottom-left |
| `DOWN_RIGHT` | +135° | Toward bottom-right |

### 2.6 Z-Ordering

Parent button is drawn at the lowest z-level. Children are drawn ABOVE the parent. When the fan opens, children animate from the parent's center position outward to their arc positions.

---

## 3. Composables

### `FanLayout`

```kotlin
@Composable
fun FanLayout(
    config: FanConfig,
    modifier: Modifier = Modifier,
    parentIcon: @Composable () -> Unit,
    children: List<@Composable (isOpen: Boolean) -> Unit>
)
```

```kotlin
data class FanConfig(
    val thetaDeg: Float,          // inter-button angle (primary parameter)
    val currentCount: Int,        // how many children (2..5)
    val direction: FanDirection,  // fan opening direction
    val buttonSizeDp: Dp = 64.dp,
    val edgeGapDp: Dp = 8.dp,
    val isOpen: Boolean = false   // fan open/closed state
)
```

### `MapControlButton`

Shared base for ALL control-stack buttons (64 dp circle, white bg, theme blue icon):

```kotlin
@Composable
fun MapControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
)
```

---

## 4. Layout Integration

### Current right-edge control stack → after refactor:

```
Column (SpaceBetween)
  SettingsButton
  Column (spacedBy 8dp)
    FanLayout(direction=LEFT, θ=?, count=?) {       // Layer toggles fan
      parentIcon = DangerLayerButton icon
      children  = [LayerButton, NewButton, ...]
    }
    FanLayout(direction=UP, θ=?, count=?) {          // New fan
      parentIcon = ...
      children  = [...]
    }
  Column (spacedBy 8dp)
    ZoomButton(+)
    ZoomButton(-)
```

Each fan's parent button sits at the same position as current standalone buttons. Children fan out from behind it.

---

## 5. Key Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — control stack, button composables
- New: `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt` — FanLayout composable
- New: `app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt` — FanConfig data class + FanDirection enum
- New: `app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt` — shared button composable
- New: `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — Canvas icon composables

---

## 6. Migration Plan — Current Layer Toggle Buttons → FanLayout

### Current state (MapDisplay toggle-danger-layer)

Two standalone buttons in a `Column(spacedBy=8.dp)` at line 632 of MapScreen.kt:
- `DangerLayerButton` — toggles low-depth warning overlay (parent candidate)
- `LayerButton` — toggles 300m zone overlay (child)

Both are 64 dp circles, white bg, Canvas icons.

### Target state

A single `FanLayout` instance replacing the inner Column:
- **Parent**: `DangerLayerButton` (warning triangle icon) — the trigger, always visible
- **Children**: `LayerButton` (circular ring icon) + future layer toggles
- **Direction**: `LEFT` (fan opens toward the map, away from the right edge)
- **θ**: configurable (e.g., 36° for 5-button reference, or 45° for 4-button)

### Migration steps

1. Create `FanConfig` with chosen θ, direction=LEFT, currentCount=1 initially (just LayerButton), expandable to 2+
2. Compute R from θ + 64 dp + 8 dp gap
3. Parent icon = DangerLayerButton Canvas drawing (warning triangle)
4. Child icons = [LayerButton Canvas drawing (circular ring)]
5. Remove standalone `DangerLayerButton()` and `LayerButton()` composable calls at line 636-643
6. Wire `onToggleLowDepthWarning` → parent onClick
7. Wire `onToggleZone300` → child[0] onClick
8. Remove the standalone composable functions DangerLayerButton and LayerButton (lines 1034-1116) — their icon drawing logic moves to FanIconComponents.kt
9. Verify: parent always visible at same position, children on top, correct z-ordering

