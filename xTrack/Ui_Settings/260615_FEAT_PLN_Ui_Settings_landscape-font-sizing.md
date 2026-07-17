<!-- scope: feature -->
# Landscape Dashboard Width & Font Sizing Validation

## 1. Landscape Dashboard Width

### Current Code

[`MapScreen.kt:537`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:537):

```kotlin
val landscapeDashboardWidth = maxHeight * 80 / 100
```

In landscape, the `BoxWithConstraints` has:
- `maxWidth` = screen height (tallest dimension)
- `maxHeight` = screen width (narrowest dimension)

So `landscapeDashboardWidth = maxHeight * 0.80` = **80% of the narrow screen dimension**.

### Example Dimensions

| Device | Screen | maxWidth (portrait height) | maxHeight (portrait width) | Dash width (80%) | Map width |
|--------|--------|---------------------------|---------------------------|-----------------|-----------|
| Phone 20:9 | 1080×2400 | 2400dp | 1080dp | **864dp** | 1536dp |
| Phone 16:9 | 1080×1920 | 1920dp | 1080dp | **864dp** | 1056dp |
| Tablet | 1600×2560 | 2560dp | 1600dp | **1280dp** | 1280dp |
| Foldable inner | 1800×2400 | 2400dp | 1800dp | **1440dp** | 960dp |

### Options to Increase Width

| Option | Code Change | Dash Width (phone 16:9) | Map Width | Trade-off |
|--------|------------|------------------------|-----------|-----------|
| **Current** | `maxHeight * 80 / 100` | 864dp | 1056dp | Balanced |
| **A: 90%** | `maxHeight * 90 / 100` | 972dp | 948dp | More dashboard, less map |
| **B: 100%** | `maxHeight` | 1080dp | 840dp | Full narrow dimension |
| **C: % of maxWidth** | `maxWidth * 35 / 100` | 672dp | 1248dp | Absolute 35% of wide dim |
| **D: % of maxWidth** | `maxWidth * 45 / 100` | 864dp | 1056dp | Same as current on 16:9 |
| **E: Fixed fraction** | `maxWidth / 3` | 640dp | 1280dp | Always 1/3 of wide dim |

**Recommendation:** Option A (90%) if you want more dashboard real estate without severely crowding the map. The map still gets ~88% of the narrow dimension, which is sufficient for OSMdroid tiles. Option E (`maxWidth / 3`) is also clean — gives the dashboard a consistent 1/3 share regardless of aspect ratio.

---

## 2. AutoSizeValue Font Sizing Validation

### Current Code

[`DashboardPanel.kt:254-284`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:254):

```kotlin
var contentSize by remember { mutableStateOf(IntSize.Zero) }
val density = LocalDensity.current
val fontSize = if (contentSize != IntSize.Zero) {
    val heightDp = with(density) { contentSize.height.toDp().value }
    val widthDp = with(density) { contentSize.width.toDp().value }
    val byHeight = heightDp * 0.70f           // ← 70% of cell height
    val byWidth = widthDp * 1.5f / text.length.coerceAtLeast(1)  // ← character fitting
    minOf(byHeight, byWidth).coerceIn(14f, 64f)
} else 32f
```

### How It Works

1. **`byHeight = cellHeight × 0.70`** — The font size is 70% of the card's measured height, reserving ~30% for vertical padding (title above, subtitle below, card margins).

2. **`byWidth = cellWidth × 1.5 / textLength`** — Approximation of max character width: assumes each character needs `cellWidth / (textLength / 1.5)` space (the 1.5 factor accounts for the average character being ~1.5× narrower than the full cell width). This prevents long strings from overflowing horizontally.

3. **`minOf(byHeight, byWidth)`** — The tighter constraint wins.

4. **`.coerceIn(14f, 64f)`** — Never smaller than 14sp, never larger than 64sp.

### Validation Points

| Check | Current Value | Assessment |
|------|--------------|------------|
| **Height factor (0.70)** | 70% of cell → font | Reasonable. In a 2×2 grid (50% cell height), with current padding → cell height ≈ (dashHeight/2 - padding). 70% gives readable values. |
| **Width factor (1.5)** | `charWidth = cellWidth / (len/1.5)` | Works for typical values ("1.2 km", "32 m", "5.0 kn"). May overflow for very short text ("5 kn") or underflow for long ("données validées RMSE 1.2m"). |
| **Min clamp (14sp)** | 14sp floor | Good — 14sp is the subtitle size. Below that would be illegible. |
| **Max clamp (64sp)** | 64sp ceiling | Generous but prevents absurdly large text in very small grids. |
| **Landscape vs portrait** | Same formula, different cell sizes | In landscape, cells are taller (dashboard is full height) but narrower (dash width / 2 per card). In portrait, cells are wider but shorter. The `minOf` handles this automatically. |
| **px→dp conversion** | Uses `LocalDensity.current` | ✅ Correct — `onSizeChanged` returns pixels, converted to dp properly. |
| **Empty state** | Uses 14sp subdued text, not AutoSizeValue | ✅ Separate path, no issues. |

### Potential Improvements

1. **Width factor is a guess**: The `1.5f` multiplier is empirical. A more principled approach would use `Paint.measureText()` to measure actual glyph widths, but that adds complexity. The current approach works well for the common case (numbers, short strings).

2. **Fallback default 32sp**: Before the first `onSizeChanged` fires, the font is 32sp. This might cause a brief "jump" on first composition. The `onSizeChanged` fires on the same frame as the first layout pass, so this is usually imperceptible.

3. **No per-char width consideration**: `"11111"` and `"MMMMM"` have very different rendered widths at the same font size. The formula treats all characters equally.

### Recommendation

The AutoSizeValue logic is sound for the dashboard use case (short numeric values with unit suffixes). No changes needed unless specific overflow/underflow cases are observed.

---

## Summary

| Topic | Suggested Change |
|-------|-----------------|
| **Landscape dash width** | Option A: `maxHeight * 90 / 100` or Option E: `maxWidth / 3` |
| **Font sizing** | No changes needed — formula is validated and correct |


