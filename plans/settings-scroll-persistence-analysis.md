# Settings Scroll Persistence — Failure Analysis

## Symptom
Scroll position resets to top on every settings overlay dismiss/reopen, despite two different implementation attempts.

## Current Implementation (lines 1175–1198)

```kotlin
val scrollState = rememberScrollState()

// Restore
LaunchedEffect(Unit) {
    if (initialScrollOffset > 0) {
        scrollState.scrollTo(initialScrollOffset)
    }
}

// Save
LaunchedEffect(scrollState) {
    snapshotFlow { scrollState.value }
        .collect { onScrollChanged(it) }
}

Column(
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(scrollState)
)
```

## Root Cause Analysis

### 1. `scrollTo` fires before layout is complete — **most likely culprit**

`LaunchedEffect(Unit)` starts its coroutine during the **composition phase**, before the layout/draw phases. The `Column` with `.verticalScroll(scrollState)` hasn't been measured yet. `ScrollState.maxValue` defaults to 0 because the scrollable content height is unknown.

`ScrollState.scrollTo()` clamps the target to `[0, maxValue]`. So:

| Frame | What happens |
|-------|-------------|
| Compose | `scrollState = ScrollState(0)`, `LaunchedEffect` starts |
| Compose | Column content (toggles, sliders) is composed — they have intrinsic sizes |
| Layout  | Column is measured: `maxValue = somePositiveValue` |
| Draw    | Content rendered at scroll 0 |
| **After frame 1** | `scrollTo(500)` runs, but `maxValue` is already correct → works in theory |

Actually, `LaunchedEffect(Unit)` runs **after** the first composition but **before** the first layout+drawn frame? Let me reconsider.

In Compose, `LaunchedEffect` starts its coroutine in the `Applier` phase, which is AFTER composition but the coroutine body runs asynchronously on the main dispatcher. The Compose rendering pipeline processes frames sequentially: composition → layout → drawing → next frame.

When `LaunchedEffect(Unit)` is encountered:
1. Its coroutine is launched on `Dispatchers.Main`
2. The coroutine suspends at the first suspension point (the `scrollTo` call is suspend)
3. Control returns to the composition pipeline
4. Layout and drawing proceed

So the timeline is:
```
Composition ──┐
LaunchedEffect│──LaunchedEffect coroutine scheduled──scrollTo(500)──┐
starts ───────┘                                                    │
Layout ────── maxValue is set                                      │
Draw ──────── renders at scroll 0                                  │
                                                                   │
└←─── scrollTo resumes, clamps to maxValue, scrolls ───────────────┘
```

BUT — `scrollTo` is a `suspend` function that internally uses `withInfiniteAnimationFrameMillis`. It awaits the next animation frame. By the next animation frame, layout IS complete. So `maxValue` should be correct.

Hmm, so this timing theory might be wrong. Let me reconsider.

### 2. `snapshotFlow` misses the final scroll value on dismiss

When the user dismisses settings (`showSettings = false`), the `SettingsOverlay` composable leaves composition. Its `LaunchedEffect` coroutines are cancelled. If the `snapshotFlow { scrollState.value }` hasn't emitted the final value before cancellation, the last saved `onScrollChanged` call is stale.

This is a **race condition**: the scroll value changes during the dismiss recomposition frame, but the `snapshotFlow` emission is scheduled after the frame completes. By then, the coroutine is already cancelled.

**Evidence**: This is a well-known Compose gotcha. `snapshotFlow` observes snapshot state reads (like `scrollState.value`). It emits on the next frame after a change. If the composable is disposed before that next frame, the emission is lost.

### 3. `scrollTo(initialScrollOffset)` is called with `initialScrollOffset = 0` because #2 lost the save

If #2 is the issue, then `settingsScrollOffset` is stuck at 0 (or an old value). `initialScrollOffset = 0` → no-op restore.

### 4. Combined failure mode (most likely)

Assuming the user scrolls to position 500 then taps the back button:

| Step | What happens | `settingsScrollOffset` |
|------|-------------|----------------------|
| Scroll to 500 | `snapshotFlow` emits 500 → `onScrollChanged(500)` | **500** |
| Tap back | `showSettings = false` triggers recomposition | 500 |
| Recomposition | `SettingsOverlay` starts leaving composition | 500 |
| LaunchedEffect cancel | `snapshotFlow` coroutine is cancelled | 500 (OK — last emission already 500) |
| Dismiss complete | | **500** |
| Reopen | `initialScrollOffset = 500` | 500 |
| `scrollTo(500)` | Fires after frame 1 | — |

Wait, if `settingsScrollOffset` IS 500, and `scrollTo(500)` fires after frame 1 with correct `maxValue`, then it SHOULD work.

Unless `scrollTo(500)` silently fails because `maxValue` is 0 at that point.

### 5. The real timing: `scrollTo` runs in the first composition-layout frame, not after

Let me reconsider. `LaunchedEffect(Unit)` — key is `Unit` (constant). This means the effect fires **only on the first composition** of the composable. The coroutine is launched during the composition phase.

But `scrollTo` is a suspend function. Let me look at what `scrollTo` actually does in Compose:

```kotlin
// ScrollState
suspend fun scrollTo(value: Int) {
    if (value < 0) throw IllegalArgumentException(...)
    val target = value.coerceIn(0, maxValue)
    if (target == this.value) return
    animateScrollTo(target, animSpec = snap())
}

private suspend fun animateScrollTo(target: Int, animSpec: AnimationSpec<Int>) {
    var previous = this.value
    animate(previous, target, animSpec) { current, _ ->
        scroll { dispatchRawDelta(current - previous) }
        previous = current
    }
}
```

Wait, `scrollTo` calls `animateScrollTo` with `snap()` — a snap animation. So it's NOT a no-op on the value — it actually calls `scroll { dispatchRawDelta(delta) }`.

Now, `dispatchRawDelta` in `ScrollState`:

```kotlin
override fun dispatchRawDelta(delta: Float): Float {
    val oldValue = value
    value = (value + delta.roundToInt()).coerceIn(0, maxValue)
    return value - oldValue
}
```

So `scrollTo(500)` with `value = 0`:
1. `snap()` animation from 0 to 500
2. Calls `dispatchRawDelta(500)` 
3. `value = (0 + 500).coerceIn(0, maxValue)`
4. If `maxValue = 0` → `value = 0`
5. If `maxValue >= 500` → `value = 500`

The key question: **what is `maxValue` when `scrollTo` runs?**

`maxValue` is set by `ScrollState` itself, based on the scrollable content's height. It's computed during layout. If layout hasn't happened yet, `maxValue = 0`.

Since `LaunchedEffect` fires during composition (before layout), `maxValue` is indeed 0. So `scrollTo(500) → value = 0.clamp(0, 0) → value = 0`.

**This IS the bug!**

After `scrollTo` completes (with no actual scroll, since it was clamped), the content is laid out. `maxValue` becomes, say, 1000. But `scrollState.value` is already 0 (clamped from the failed `scrollTo`). The content stays at scroll position 0.

## The Fix

The restore must wait until AFTER layout is complete, when `maxValue` is known. Two approaches:

### Approach A: Wait for non-zero maxValue (recommended)

```kotlin
LaunchedEffect(Unit) {
    // Wait until the scrollable content has been measured
    snapshotFlow { scrollState.maxValue }
        .first { it > 0 }
    // Now scroll to the saved offset
    if (initialScrollOffset > 0) {
        scrollState.scrollTo(initialScrollOffset.coerceAtMost(scrollState.maxValue))
    }
}
```

This uses `snapshotFlow { scrollState.maxValue }.first { it > 0 }` to suspend until the layout is complete and `maxValue` is known. Only then does it call `scrollTo`.

### Approach B: Defer to next frame

```kotlin
LaunchedEffect(Unit) {
    // Let the first layout frame complete
    withFrameNanos {  }
    // Now maxValue is set
    if (initialScrollOffset > 0) {
        scrollState.scrollTo(initialScrollOffset)
    }
}
```

`withFrameNanos { }` suspends until the next frame callback. By then, layout is complete and `maxValue` is known.

### Approach C: Use `Modifier.onSizeChanged` on the scrollable Column

```kotlin
var scrollableHeight by remember { mutableStateOf(0) }

Column(
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .onSizeChanged { scrollableHeight = it.height }
        .verticalScroll(scrollState)
)

LaunchedEffect(scrollableHeight) {
    if (scrollableHeight > 0 && initialScrollOffset > 0) {
        scrollState.scrollTo(initialScrollOffset)
    }
}
```

## Recommendation

**Approach A** is the cleanest — no extra state, no magic frame delays, and it's self-documenting. Uses `snapshotFlow` + `first()` (from `kotlinx.coroutines.flow`) to wait for the layout to establish `maxValue`.

## Files to modify

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — replace the `LaunchedEffect(Unit) { scrollState.scrollTo(...) }` block with one of the approaches above
