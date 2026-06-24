<!-- scope: feature -->
# Track List — Render Preview Indicator

> **Feature:** BoatTrace | **Scope:** TrackHistoryOverlay card left-edge accent bar
> **Status:** Design — approved, awaiting implementation

---

## 1. Motivation

The track list shows metadata (name, stats, date) but gives no indication of how each track will render on the map. With settings like `trackingRenderNb`, transparency gradients, and separate pinned color schemes, the user cannot tell from the list alone:

- Which history tracks will actually appear on the map (capped by `trackingRenderNb`)
- What color + alpha each track's polyline will have
- Whether `tracksVisible` is off (hiding everything)

## 2. Design

### 2.1 Indicator: Left-Edge Accent Bar

A **4dp-wide vertical strip** on the left edge of each track card, colored with the track's computed polyline ARGB value.

```
┌──────────────────────────────────────┐
║ 2026-06-22  14:30→16:45    42 pts 📌↗│  ← 4dp accent bar along left edge
║ ──────────────────────────────────── │
║ Track Name                           │
║ Comment text...                      │
║ Duration   Nav Time   Avg Speed      │
║ Distance   Idle       Max Speed      │
└──────────────────────────────────────┘
```

- **Visible track**: bar colored with the exact ARGB computed from index interpolation (color + alpha)
- **Non-visible track** (beyond `trackingRenderNb` for history, or `tracksVisible=false`): bar uses `uiSettingsTextMuted` at 15% alpha — a barely-visible grey
- **Pinned track**: uses pinned color/transparency settings, always visible regardless of `trackingRenderNb`

### 2.2 Color Computation

Pure utility function, reused by both `MapScreen` rendering and the indicator:

```kotlin
data class TrackPolylineAppearance(
    val argb: Int,        // 0xAARRGGBB — exactly what Polyline.outlinePaint.color uses
    val strokeWidth: Float // 6f or 8f
)

fun computeTrackPolylineAppearance(
    index: Int,                    // 0-based position among same-type tracks (newest first)
    total: Int,                    // total tracks of this type being rendered
    transparencyNewest: Int,       // 0..100 (0=opaque, 100=invisible)
    transparencyOldest: Int,
    colorFrom: Int,                // 0xRRGGBB (no alpha)
    colorTo: Int,
    strokeWidth: Float = 6f
): TrackPolylineAppearance {
    val alphaNewest = (100 - transparencyNewest) / 100f
    val alphaOldest = (100 - transparencyOldest) / 100f
    val t = if (total <= 1) 0f else index.toFloat() / (total - 1).toFloat()
    val alphaFraction = alphaNewest - t * (alphaNewest - alphaOldest)
    val alphaInt = (alphaFraction * 255).toInt().coerceIn(0, 255)

    val r = ((colorFrom shr 16 and 0xFF) * (1f - t) + (colorTo shr 16 and 0xFF) * t).toInt().coerceIn(0, 255)
    val g = ((colorFrom shr 8 and 0xFF) * (1f - t) + (colorTo shr 8 and 0xFF) * t).toInt().coerceIn(0, 255)
    val b = ((colorFrom and 0xFF) * (1f - t) + (colorTo and 0xFF) * t).toInt().coerceIn(0, 255)

    val argb = (alphaInt shl 24) or (r shl 16) or (g shl 8) or b
    return TrackPolylineAppearance(argb, strokeWidth)
}
```

### 2.3 Visibility Determination

For each track card, determine its rendering status:

| Condition | Bar appearance |
|---|---|
| `!tracksVisible` | Grey (muted, 15% alpha) |
| Pinned | Computed from pinned settings (always visible) |
| History, index < `trackingRenderNb` | Computed from past settings |
| History, index >= `trackingRenderNb` | Grey (muted, 15% alpha) — not rendered |

The index for history tracks is computed by filtering to unpinned, sorting by `startTimeMs` desc, and taking the 0-based position. Pinned tracks are sorted by `startTimeMs` desc and always rendered.

### 2.4 Implementation in TrackCardContent

The accent bar is added via a `Row` wrapper around the existing card content:

```kotlin
Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
    // 4dp accent bar
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(IntrinsicSize.Min)  // matches card content height
            .background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
    )
    // Existing card content
    Column(modifier = Modifier.weight(1f).background(cardBg)) {
        // ... unchanged ...
    }
}
```

Where `accentColor` is:
- `Color(computedArgb)` for visible tracks
- `Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.15f)` for non-visible

## 3. File Changes

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Extract `computeTrackPolylineAppearance()` utility; use it in existing LaunchedEffect; pass render settings to TrackHistoryOverlay |
| `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` | Accept new render-settings parameters; add left-edge accent bar to `TrackCardContent`; compute per-card visibility + color |
| (No other files) | Self-contained to these two |

## 4. Parameters to Thread Through

New `TrackHistoryOverlay` parameters:

| Parameter | Type | Source |
|---|---|---|
| `tracksVisible` | `Boolean` | `appSettings.tracksVisible` |
| `trackingRenderNb` | `Int` | `appSettings.trackingRenderNb` |
| `trackingTransparencyNewest` | `Int` | `appSettings.trackingTransparencyNewest` |
| `trackingTransparencyOldest` | `Int` | `appSettings.trackingTransparencyOldest` |
| `trackingColorPastFrom` | `Int` | `appSettings.trackingColorPastFrom` |
| `trackingColorPastTo` | `Int` | `appSettings.trackingColorPastTo` |
| `trackingTransparencyPinnedNewest` | `Int` | `appSettings.trackingTransparencyPinnedNewest` |
| `trackingTransparencyPinnedOldest` | `Int` | `appSettings.trackingTransparencyPinnedOldest` |
| `trackingColorPinnedFrom` | `Int` | `appSettings.trackingColorPinnedFrom` |
| `trackingColorPinnedTo` | `Int` | `appSettings.trackingColorPinnedTo` |

## 5. Implementation Order

| # | Step | File(s) |
|---|---|---|
| 1 | Extract `computeTrackPolylineAppearance()` as a pure function, place in `MapScreen.kt` (top-level private) or a new `TrackRenderUtil.kt` | `MapScreen.kt` or new file |
| 2 | Refactor existing `LaunchedEffect` rendering to use the utility (no behavior change) | `MapScreen.kt` |
| 3 | Add render-settings parameters to `TrackHistoryOverlay` signature | `TrackHistoryOverlay.kt` |
| 4 | In `TrackCardContent`, compute per-card visibility index + accent color, wrap content in `Row` with 4dp accent bar | `TrackHistoryOverlay.kt` |
| 5 | Update call site in `MapScreen.kt` to pass new parameters | `MapScreen.kt` |
| 6 | Build validation: `gradlew assembleDebug` | — |

## 6. Edge Cases

- **Single track of a type**: `t = 0f`, uses `Newest` color/alpha only — no gradient
- **All tracks pinned, none in history**: history indices compute to empty list; no "beyond renderNb" condition triggered
- **`trackingRenderNb = 0`**: all history tracks show grey bar
- **`tracksVisible = false`**: all tracks show grey bar regardless of pinned status
- **Card reorder after pin/unpin**: indicator updates because index is recomputed from the current `trackSummaries` list
