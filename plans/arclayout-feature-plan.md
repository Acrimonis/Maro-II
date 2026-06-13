# ArcLayout — Layer Toggle Arc Menu

## Overview

Replace the current two isolated layer toggle buttons on the map's right-edge control stack with a single **anchor button** that fans out into an **arc menu to the left**, revealing all available layer toggles with state indicators and representative icons.

**Reference:** [ogaclejapan/ArcLayout](https://github.com/ogaclejapan/ArcLayout) — Android library arranging child views along an arc.

**Implementation approach:** Pure Compose custom layout (trigonometric positioning via `Modifier.offset`, animated with `Animatable`).

---

## Current Architecture

### Existing Right-Edge Control Stack ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:658))

```
┌────────────────────┐
│   ⚙️ Settings       │  ← SettingsButton (top)
├────────────────────┤
│                    │
│   △ DangerLayer    │  ← Low-depth warning toggle (middle cluster)
│   ○ LayerButton    │  ← 300m zone toggle
│                    │
├────────────────────┤
│   ＋               │  ← Zoom buttons (bottom cluster)
│   －               │
└────────────────────┘
```

### Current Layer Toggles

| Button | Setting | ViewModel Callback | Icon |
|---|---|---|---|
| [`DangerLayerButton`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1207) | `lowDepthWarningVisible` | `toggleLowDepthWarningVisibility()` | Warning triangle |
| [`LayerButton`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1172) | `zone300Visible` | `toggleZone300Visibility()` | Circle outline |

### Wiring

Toggles flow through [`CoastlineViewModel`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) → [`SettingsManager`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) → `AppSettings` data class → persisted via `SharedPreferences`. Visibility is read in [`MapContent`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:553) to gate overlay rendering.

### Current Overlay Visibility Gates (in `MapContent`)

```kotlin
val visibleZone300 = if (appSettings.zone300Visible) zone300 else null
val visibleLowDepthWarning = if (appSettings.lowDepthWarningVisible) lowDepthWarningBitmap else null
```

---

## Required Changes

### 1. New AppSettings Properties

Add these to [`AppSettings`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:52):

| Property | Type | Default | Description |
|---|---|---|---|
| `depthLayerVisible` | `Boolean` | `true` | Toggle depth colour map + isobath contours as one unit |
| `regulatedZonesVisible` | `Boolean` | `true` | Toggle regulated zone overlays (placeholder — no backing data yet) |

These need:
- SharedPreferences keys + load/write in `SettingsManager`
- Default values in `AppSettings` data class
- ViewModel toggle methods in `CoastlineViewModel`
- Visibility gates in `MapContent`

### 2. Four Layer Toggles for the Arc

| # | Layer | Icon Concept | State Representation | Existing Setting | New? |
|---|---|---|---|---|---|
| 1 | **Low Depth Warning** | Warning triangle (△) | Active: full opacity, Inactive: dimmed | `lowDepthWarningVisible` | No |
| 2 | **300m Zone** | Concentric circle outline | Active: full opacity, Inactive: dimmed | `zone300Visible` | No |
| 3 | **Depth Layer** | Gradient/color swatch | Active: full opacity, Inactive: dimmed | New: `depthLayerVisible` | **Yes** |
| 4 | **Regulated Zones** | Speed limit / regulatory sign | Active: full opacity, Inactive: dimmed | New: `regulatedZonesVisible` | **Yes** |

### 3. ArcLayout Implementation Approach

**Decision: Pure Compose Custom Layout** — no library dependency.

The [`ogaclejapan/ArcLayout`](https://github.com/ogaclejapan/ArcLayout) library is a traditional Android `ViewGroup`. Using it in Compose would require View → Compose interop nesting (each button wrapped in `ComposeView` inside `ArcLayout` inside `AndroidView`), which adds complexity without benefit.

**Arc geometry** — position 4 buttons along a ~140° arc opening leftward from the anchor:

```kotlin
// Angles use standard math convention: 0° = right, 90° = down, 180° = left
val startAngle = 200f   // upper-left quadrant
val endAngle   = 340f   // lower-left quadrant
val sweepAngle = endAngle - startAngle  // 140°

for (i in 0 until buttonCount) {
    val angle = Math.toRadians(startAngle + i * (sweepAngle / (buttonCount - 1)))
    val offsetX = (arcRadius * cos(angle)).toInt().dp
    val offsetY = (arcRadius * sin(angle)).toInt().dp
    // Apply via Modifier.offset(x = offsetX, y = offsetY)
}
```

- **Arc radius**: ~72-80dp from anchor center (= ~1 button width)
- **Anchor position**: Right edge of screen, vertically centered between Settings and Zoom
- **Buttons fan leftward** into the map area, staying clear of the zoom controls below

### 4. Animation Design

- **Expand**: Single `Animatable<Float>` driving 0→1 transition, `lerp` interpolates each button's offset from `(0,0)` (collapsed at anchor) to its arc position
- **Staggered entry**: `delay(i * 50L)` in a `LaunchedEffect` before each button starts animating
- **Collapse**: All buttons animate back to anchor simultaneously
- **Duration**: ~300ms expand, ~200ms collapse
- **Easing**: `FastOutSlowInEasing`
- **Why single `Animatable` instead of per-button**: Simpler, cheaper, avoids 4 separate `remember` blocks. Staggering via `delay` in the launch.

### 5. Interaction Design

1. Tap **anchor button** → toggle arc open/closed
2. Tap a **layer button** → toggle that layer's visibility, keep arc open (multi-toggle friendly)
3. Dismiss arc by:
   - Tapping anchor again
   - **Tapping outside the arc area** → requires a transparent scrim overlay behind the arc that intercepts taps. The scrim sits between the map content layer and the arc buttons layer in the `Box` Z-order.
   - **Back press** → requires `BackHandler(enabled = arcExpanded)` in `MapScreen`
4. Each button: Canvas-drawn icon + active/inactive visual state (opacity shift)

### 6. Component Structure

```
ArcLayoutToggle (new composable, replaces middle Column in right-edge stack)
├── AnchorButton
│   └── Layer stack icon + badge showing active layer count (e.g. "2/4")
├── Scrim (full-screen transparent overlay, visible when expanded, clickable to dismiss)
└── ArcLayout (visible when expanded)
    ├── LayerToggleButton[0]  ← Low Depth Warning
    │   ├── Canvas: warning triangle icon
    │   └── Active: full color / Inactive: dimmed
    ├── LayerToggleButton[1]  ← 300m Zone
    │   ├── Canvas: concentric circle outline
    │   └── Active: full color / Inactive: dimmed
    ├── LayerToggleButton[2]  ← Depth Layer
    │   ├── Canvas: gradient swatch
    │   └── Active: full color / Inactive: dimmed
    └── LayerToggleButton[3]  ← Regulated Zones
        ├── Canvas: regulatory / speed-limit icon
        └── Active: full color / Inactive: dimmed
```

### 7. New ViewModel Wiring

Add to [`CoastlineViewModel`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt):

```kotlin
fun toggleDepthLayerVisibility() {
    settingsManager.update { it.copy(depthLayerVisible = !it.depthLayerVisible) }
}

fun toggleRegulatedZonesVisibility() {
    settingsManager.update { it.copy(regulatedZonesVisible = !it.regulatedZonesVisible) }
}
```

### 8. MapContent Visibility Gates

Update overlay rendering in [`MapContent`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:553):

```kotlin
// Layer visibility gates — derived from AppSettings before passing to CoastlineMapView
val visibleZone300 = if (appSettings.zone300Visible) zone300 else null
val visibleLowDepthWarning = if (appSettings.lowDepthWarningVisible) lowDepthWarningBitmap else null
val visibleDepthLayer = if (appSettings.depthLayerVisible) depthBitmap else null
val visibleRegulatedZones = if (appSettings.regulatedZonesVisible) isobaths else null
//                                                           ↑ placeholder — no real data model yet
```

**Important:** `visibleRegulatedZones` is a UI-only placeholder. The setting exists and its toggle works, but since there's no regulated zone data model or rendering pipeline yet, no overlay appears when toggled on. This is a forward-looking slot.

### 9. File Changes Summary

| File | Change Type | Description |
|---|---|---|
| [`app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | Modify | Add `depthLayerVisible`, `regulatedZonesVisible` to `AppSettings`, `load()`, `update()` |
| [`app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) | Modify | Add `toggleDepthLayerVisibility()`, `toggleRegulatedZonesVisibility()` |
| [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Modify | - Add `onToggleDepthLayer`, `onToggleRegulatedZones` params to `MapContent`<br>- Replace middle `Column(layer_buttons)` with `ArcLayoutToggle`<br>- Add visibility gates for new layers<br>- Add `BackHandler(enabled = arcExpanded)` for back-press dismiss |
| [`app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt`](app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt) | **New** | `ArcLayoutToggle` composable: anchor button + scrim + arc positioning + `LayerToggleButton` composable + expand/collapse animation |

---

## Implementation Order (Recommended)

1. **Settings & ViewModel** — Add `depthLayerVisible`, `regulatedZonesVisible` to `AppSettings`, `load()`, `update()`, and ViewModel toggle methods
2. **`ArcLayoutToggle.kt`** — New file with:
   - `ArcLayoutToggle` composable (anchor + scrim + arc container + animation)
   - `LayerToggleButton` (reusable composable for all 4 buttons)
   - Arc positioning math
3. **`MapScreen.kt` wiring** — Replace middle Column with `ArcLayoutToggle`, thread new callbacks + visibility gates through `MapContent` and `CoastlineMapView`, add `BackHandler`
4. **Icon design** — Refine Canvas-drawn icons for depth layer (gradient swatch) and regulated zones

---

## Interaction Details (from Peer Review)

### Back-press Dismiss
Requires explicit `BackHandler` in `MapScreen`:
```kotlin
var arcExpanded by remember { mutableStateOf(false) }
BackHandler(enabled = arcExpanded) { arcExpanded = false }
```
Without this, pressing back while the arc is open would exit the screen instead of collapsing the menu.

### Outside-tap Dismiss
Requires a transparent `Box` scrim behind the arc buttons that fills the screen and intercepts taps:
```kotlin
if (arcExpanded) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { arcExpanded = false }
    )
    // Arc buttons rendered above this scrim
}
```
The scrim sits between the map layer and the arc layer in Z-order.

---

## Future Considerations (Not in Scope)

- **Settings page toggles**: The Display tab could also control `depthLayerVisible` and `regulatedZonesVisible`
- **Regulated zones data model**: Currently a UI-only placeholder. When a data model exists (speed limits, no-go areas, anchoring restrictions from OSM or other sources), `regulatedZonesVisible` will gate a real rendering pipeline
- **Auto-reveal for depth layer**: Similar to 300m zone auto-reveal on approach
