# FanLayout Extension — For Porting ArcAnchorButton

## Current Architecture (develop)

[`ArcLayoutToggle.kt`](app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt) has two composables:

1. **`ArcAnchorButton`** — parent button with 3-stripe layer icon + `activeLayerCount` badge at top-right
2. **`ArcButtonOverlay`** — full-screen overlay positioned at root level, renders:
   - A scrim (click to dismiss)
   - 4 animated arc buttons (depth, regulated zones, 300m zone, low-depth warning)
   - A "dummy anchor" on top (so children emerge from behind it)
   - Staggered expand (70ms per child), simultaneous collapse
   - R = 80dp, 4 buttons at 60° spacing over 180° sweep

## Required Extensions to FanLayout

### 1. FanConfig additions

```kotlin
data class FanConfig(
    val thetaDeg: Float,
    val currentCount: Int,
    val direction: FanDirection,
    val buttonSizeDp: Dp = 64.dp,
    val edgeGapDp: Dp = 8.dp,
    val isOpen: Boolean = false,
    // NEW:
    val toggleChildren: Boolean = false,       // children toggle on/off
    val stayOpenAfterToggle: Boolean = true,   // fan stays open after child tap
    val showActiveBadge: Boolean = false,      // show badge on parent
    val activeChildCount: Int = 0,             // for badge display
)
```

### 2. Parent Badge

Match [`ArcAnchorButton`](app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt:86-94) exactly:
- 18 dp blue circle at `Alignment.TopEnd`
- White bold text, 11 sp, `PlatformTextStyle(includeFontPadding = false)`
- Rendered OUTSIDE the CircleShape clip of the parent button

### 3. Toggle Children

Child content composable receives `isActive: Boolean` for icon alpha:
```kotlin
children: List<@Composable (isActive: Boolean) -> Unit>
```

FanLayout internally tracks which children are toggled via `activeChildIndices: Set<Int>`.

### 4. Stay-Open + Dismiss Behavior

| Action | Behavior |
|--------|----------|
| Tap child | Toggle child active state, fan stays open |
| Tap parent | Toggle fan open/close |
| Tap scrim | Close fan |
| Back press | Close fan |

The **scrim** is a full-screen `Box` with `Modifier.clickable` behind the fan children. Currently the scrim is rendered by `ArcButtonOverlay` at root level. With FanLayout, we need to either:
- A) Have FanLayout emit the scrim itself (requires parent to pass screen size)
- B) Keep a separate `FanOverlay` composable at root level (like current pattern)

For simplicity, **Option A** is cleaner: FanLayout renders the scrim internally as a full-size Box at z-index below children but above the parent. This requires the parent to provide enough space (which the existing `Box(Modifier.fillMaxSize())` in `MapContent` already does).

### 5. Animation

Staggered expand, simultaneous collapse — matches current ArcLayoutToggle:
```kotlin
// Expand: staggered
delay(idx * 70L)
anim.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
// Collapse: simultaneous
anim.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
```

## Migration Plan

### Files to modify
1. [`FanConfig.kt`](app/src/main/java/ykws/android/maro/ui/map/FanConfig.kt) — add toggle fields
2. [`FanLayout.kt`](app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt) — add badge, scrim, animation, toggle state
3. [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — replace `ArcAnchorButton` + `ArcButtonOverlay` with FanLayout
4. [`ArcLayoutToggle.kt`](app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt) — remove or deprecate (icons move to FanIconComponents.kt)

### New composable signatures

```kotlin
@Composable
fun FanLayout(
    config: FanConfig,
    modifier: Modifier = Modifier,
    parent: @Composable (isOpen: Boolean, activeChildCount: Int) -> Unit,
    onParentClick: () -> Unit,
    children: List<@Composable (isActive: Boolean) -> Unit>,
    onChildClick: ((index: Int, isActive: Boolean) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null   // scrim tap / back handler
)
```

### Integration in MapScreen

Replace:
```kotlin
// Current call sites:
ArcAnchorButton(activeLayerCount, onClick = onToggleArc, onPositionChanged = onAnchorPositionChanged)
ArcButtonOverlay(expanded, onDismiss, anchorCenter, ...)
```

With:
```kotlin
FanLayout(
    config = FanConfig(
        thetaDeg = 60f,
        currentCount = 4,
        direction = FanDirection.LEFT,
        isOpen = arcExpanded,
        toggleChildren = true,
        showActiveBadge = true,
        activeChildCount = listOf(depthVisible, regVisible, zoneVisible, dangerVisible).count { it }
    ),
    parent = { isOpen, count -> /* 3-stripe layer icon + badge */ },
    onParentClick = { /* toggle arcExpanded */ },
    children = listOf(
        { isActive -> depthIcon(isActive) },
        { isActive -> regIcon(isActive) },
        { isActive -> zoneIcon(isActive) },
        { isActive -> dangerIcon(isActive) },
    ),
    onChildClick = { index, isActive -> /* toggle corresponding setting */ },
    onDismiss = { /* set arcExpanded = false */ }
)
```

---

## Decisions (Confirmed)

| Question | Decision |
|----------|----------|
| Scrim? | ❌ No scrim. Close on back press or parent button tap. |
| Animation | ✅ 70ms stagger expand (per child), 200ms simultaneous collapse. |
| Icon migration | ✅ Move 4 icons from ArcLayoutToggle.kt to FanIconComponents.kt. |
| Toggle children | ✅ Children receive `isActive: Boolean` for alpha state. |
| Stay-open | ✅ Fan stays open after child toggle; closes on parent tap or back. |
| Active badge | ✅ 18 dp blue circle at TopEnd of parent, bold white text. |
