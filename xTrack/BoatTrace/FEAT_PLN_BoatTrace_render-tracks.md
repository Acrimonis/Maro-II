# Render Tracks — Implementation Plan

## Overview

Add a configurable track history rendering system that displays the N most recent tracks on the map with decreasing transparency, renders the active recording track in a dedicated color, and provides a layer toggle in the fan layout.

## Requirements

1. **Settings** in `maro.properties` + `build.gradle.kts` BuildConfig + `AppSettings`:
   - `tracking.render.nb` — int 0..20, default 5
   - `tracking.color.active` — ARGB hex color for the active recording track
   - `tracking.color.history` — ARGB hex color for historical tracks
   - `tracking.color.pinned` — ARGB hex color for pinned tracks

2. **Rendering rules for history tracks:**
   - Render `tracking.render.nb` tracks from history, sorted most recent → oldest
   - Color: `tracking.color.history` with decreasing transparency from 90% → 10% in `tracking.render.nb` increments
   - The first (most recent) history track gets a thicker stroke width
   - All rendered on a "tracks layer" that can be shown/hidden via fan layout

3. **Active track rendering:**
   - Active recording track rendered in `tracking.color.active`

4. **Layer toggle:**
   - Add a track layer icon to the ArcLayout fan layout layer buttons
   - Respects `tracksVisible` setting (new AppSettings boolean)

## Implementation Steps

### Step 1: maro.properties — Add tracking render settings

**File:** [`maro.properties`](maro.properties:36)

Add after the existing `track.*` section:

```properties
# Track rendering on map
tracking.render.nb=5
tracking.color.active=#FF1565C0
tracking.color.history=#FF1565C0
tracking.color.pinned=#FF1565C0
```

---

### Step 2: build.gradle.kts — Add BuildConfig fields

**File:** [`app/build.gradle.kts`](app/build.gradle.kts:79)

Add after existing track recording defaults (around line 83). Use `propInt` with `0x` hex integers directly — no `parseHexColor` helper needed:

```kotlin
// ── Track rendering defaults from maro.properties ──────────
buildConfigField("int", "TRACKING_RENDER_NB", propInt("tracking.render.nb", 5).coerceIn(0, 20).toString())
buildConfigField("int", "TRACKING_COLOR_ACTIVE", propInt("tracking.color.active", 0xFF1565C0.toInt()).toString())
buildConfigField("int", "TRACKING_COLOR_HISTORY", propInt("tracking.color.history", 0xFF1565C0.toInt()).toString())
buildConfigField("int", "TRACKING_COLOR_PINNED", propInt("tracking.color.pinned", 0xFF1565C0.toInt()).toString())
```

> **Review note:** Using `propInt` with `0x` hex literals avoids adding a `parseHexColor` function. The maro.properties values should use decimal integers (the hex literal is only the BuildConfig default).

---

### Step 3: AppSettings — Add tracks layer visibility + render settings

**File:** [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:54)

Add to [`AppSettings`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:54) data class:

```kotlin
/** Whether the tracks overlay layer is visible on the map. */
val tracksVisible: Boolean = true,
/** Number of historical tracks to render on the map (0-20). */
val trackingRenderNb: Int = BuildConfig.TRACKING_RENDER_NB,
/** ARGB color for the active recording track. */
val trackingColorActive: Int = BuildConfig.TRACKING_COLOR_ACTIVE,
/** ARGB color for historical tracks. */
val trackingColorHistory: Int = BuildConfig.TRACKING_COLOR_HISTORY,
/** ARGB color for pinned tracks. */
val trackingColorPinned: Int = BuildConfig.TRACKING_COLOR_PINNED
```

Add the corresponding SharedPreferences keys in `companion object`:

```kotlin
private const val KEY_TRACKS_VISIBLE = "tracks_visible"
private const val KEY_TRACKING_RENDER_NB = "tracking_render_nb"
private const val KEY_TRACKING_COLOR_ACTIVE = "tracking_color_active"
private const val KEY_TRACKING_COLOR_HISTORY = "tracking_color_history"
private const val KEY_TRACKING_COLOR_PINNED = "tracking_color_pinned"
```

Add loading in `load()` and saving in `update()` for each field.

---

### Step 4: FanIconComponents — Add TrackLayerIcon

**File:** [`FanIconComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:230)

Add a new composable icon for the tracks layer — a simple route/trace line icon:

```kotlin
/**
 * Icon: route trace line (tracks overlay toggle).
 * Two connected dots with a line — represents a track trace on the map.
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun TrackLayerIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width; val h = size.height
        // Two dots (start/end) with a connecting line
        val dotRadius = w * 0.08f
        val startX = w * 0.25f; val endX = w * 0.75f
        val midY = h * 0.5f
        // Connecting line
        drawLine(
            color = ButtonColors.icon,
            start = Offset(startX + dotRadius, midY),
            end = Offset(endX - dotRadius, midY),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round,
            alpha = alpha
        )
        // Start dot
        drawCircle(ButtonColors.icon, dotRadius, Offset(startX, midY), alpha)
        // End dot (slightly larger)
        drawCircle(ButtonColors.icon, dotRadius * 1.3f, Offset(endX, midY), alpha)
    }
}
```

---

### Step 5: CoastlineViewModel — Add toggleTracksVisibility

**File:** [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

Add after `toggleDepthLayerVisibility()`:

```kotlin
/**
 * Toggle the tracks layer visibility on/off.
 */
fun toggleTracksVisibility() {
    settingsManager.update { it.copy(tracksVisible = !it.tracksVisible) }
}
```

---

### Step 6: MapScreen — Update FanLayout to include tracks layer

**File:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1238)

The fan currently has `maxCount = 5` and `currentCount = 4` (depth, regulated zones, zone300, low depth warning). Adding tracks makes it 5 children.

**Changes:**
- `currentCount` changes from `4` to `5`
- `maxCount` bumps from `5` to `6` (leave headroom for future layer buttons)
- Add `appSettings.tracksVisible` to the `activeChildCount` list
- Add a 5th child composable `TrackLayerIcon` to the children list
- Add a 5th `onChildClick` case for the tracks toggle

```kotlin
// FanConfig changes:
maxCount = 6,       // was 5 — leave headroom
currentCount = 5,   // was 4

// activeChildCount
activeChildCount = listOf(
    appSettings.depthLayerVisible,
    appSettings.regulatedZonesVisible,
    appSettings.zone300Visible,
    appSettings.lowDepthWarningVisible,
    appSettings.tracksVisible
).count { it }

// children (5th entry added)
children = listOf<@Composable (Boolean) -> Unit>(
    { isActive -> DepthBarIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
    { isActive -> RegulatedZoneIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
    { isActive -> DoubleCircleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
    { isActive -> WarningTriangleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
    { isActive -> TrackLayerIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) }
)

// activeStates
activeStates = listOf(
    appSettings.depthLayerVisible,
    appSettings.regulatedZonesVisible,
    appSettings.zone300Visible,
    appSettings.lowDepthWarningVisible,
    appSettings.tracksVisible
)

// onChildClick (case 4 added)
onChildClick = { index: Int, _: Boolean ->
    when (index) {
        0 -> onToggleDepthLayer()
        1 -> onToggleRegulatedZones()
        2 -> onToggleZone300()
        3 -> onToggleLowDepthWarning()
        4 -> onToggleTracks()
    }
}
```

Add `onToggleTracks` to the composable signature and wire it:

```kotlin
// In MapScreen composable call, add:
onToggleTracks = viewModel::toggleTracksVisibility,

// In function signature, add:
onToggleTracks: () -> Unit = {},
```

Pass `appSettings.tracksVisible` as a parameter to the map content so the track overlay LaunchedEffect can gate on it.

---

### Step 7: MapScreen — Update track overlay rendering logic

**File:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:545)

#### 7a: History track overlay (lines 545-586)

Replace the existing track overlay LaunchedEffect with new logic:

```kotlin
// ── Track overlay: render N most recent history tracks with fading transparency ──
LaunchedEffect(mapView, trackSummaries, appSettings.tracksVisible, appSettings.trackingRenderNb, appSettings.trackingColorHistory) {
    val mv = mapView ?: return@LaunchedEffect
    
    // Remove ALL track history overlays first ("track_hist_*")
    val toRemove = mv.overlays.filter { overlay ->
        (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_hist_") == true
    }
    mv.overlays.removeAll(toRemove)
    
    if (!appSettings.tracksVisible) {
        mv.invalidate()
        return@LaunchedEffect
    }
    
    val nbToRender = appSettings.trackingRenderNb.coerceIn(0, 20)
    if (nbToRender <= 0 || trackSummaries.isEmpty()) {
        mv.invalidate()
        return@LaunchedEffect
    }
    
    // Sort by startTimeMs descending (most recent first)
    val sorted = trackSummaries
        .filter { it.visibleOnMap }
        .sortedByDescending { it.startTimeMs }
        .take(nbToRender)
    
    // Base color without alpha: extract RGB from trackingColorHistory
    val baseColor = appSettings.trackingColorHistory
    val baseRgb = baseColor and 0x00FFFFFF
    val total = sorted.size
    
    for ((index, summary) in sorted.withIndex()) {
        val track = trackViewModel.loadTrackDetail(summary.id) ?: continue
        if (track.trackPoints.isEmpty()) continue
        
        // Decreasing transparency: first (most recent) = 90% alpha, last = 10% alpha
        val alphaFraction = if (total == 1) 0.90f
            else 0.90f - (index.toFloat() / (total - 1).toFloat()) * 0.80f
        val alphaInt = (alphaFraction * 255).toInt().coerceIn(0, 255)
        val colorWithAlpha = (alphaInt shl 24) or baseRgb
        
        // First (most recent) track is thicker
        val strokeWidth = if (index == 0) 10f else 6f
        
        val polyline = org.osmdroid.views.overlay.Polyline().apply {
            title = "track_hist_${summary.id}"
            outlinePaint.color = colorWithAlpha
            outlinePaint.strokeWidth = strokeWidth
            setPoints(track.trackPoints.map { pt ->
                org.osmdroid.util.GeoPoint(pt.lat, pt.lon)
            })
        }
        mv.overlays.add(polyline)
    }
    mv.invalidate()
}
```

**Key change:** Instead of the old approach that rendered per-track based on `visibleOnMap`, the new approach:
- Removes ALL `track_hist_*` overlays before redrawing
- Only renders if `tracksVisible` is true
- Takes the N most recent tracks, sorted by `startTimeMs` descending
- Applies decreasing transparency (90% → 10%) across the N tracks
- Makes the first (most recent) track slightly thicker (10f vs 6f)

The per-track `visibleOnMap` field from Tack is still used as a filter step — only tracks where `visibleOnMap == true` are candidates for the top-N selection.

#### 7b: Active recording track (lines 588-620)

Update the active recording polyline color to use `tracking.color.active`:

```kotlin
// In the active recording trace LaunchedEffect (line 605):
outlinePaint.color = appSettings.trackingColorActive

// Make sure appSettings is accessible inside the collect lambda —
// it already is since we're in a composable context, but if the
// LaunchedEffect needs to react to color changes, add appSettings.trackingColorActive
// to the LaunchedEffect key.
```

Change the LaunchedEffect key from `LaunchedEffect(mapView)` to `LaunchedEffect(mapView, appSettings.trackingColorActive)`:

```kotlin
LaunchedEffect(mapView, appSettings.trackingColorActive) {
```

---

### Step 8: NavigationSettings — Add "Tracking" section

**File:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2408)

Add a "Tracking" section at the end of the `NavigationSettings` composable, before the closing spacer:

```kotlin
Spacer(modifier = Modifier.height(24.dp))

// ── Tracking section ─────────────────────────────────────────────
SectionHeader(title = "Tracking")
Spacer(modifier = Modifier.height(8.dp))

Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(ComposeColor(AppConfig.uiSettingsCardBackground))
) {
    // Tracks layer visibility toggle
    // Number of layers slider (0-20)
    // Color preview + picker for active/history/pinned
}
```

The section should include:

1. **Tracks Layer visibility** — Switch toggle (maps to `appSettings.tracksVisible`)
2. **Number of history layers** — Slider 0-20, step 1 (maps to `appSettings.trackingRenderNb`)
3. **Color selection** — Three color preview swatches with labels:
   - Active track color
   - History track color
   - Pinned track color
   
   **Use a Canvas-based custom color picker** (~100 lines, zero new deps). The project already uses Canvas extensively in [`FanIconComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt). Implement:
   - HSV color wheel using `drawCircle` + `drawLine` arc rendering
   - Brightness slider
   - `Color.HSVtoColor` / `colorToHSV` for conversion
   - Reusable composable: `ColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit)`
   - Show a small color swatch for each field; tapping it opens the picker dialog
   - Persist result via `onUpdateSettings { it.copy(trackingColorActive = newColor) }`

---

### Step 9: TrackDrawerOverlay — Use tracking colors

**File:** [`TrackDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt)

If the drawer shows a preview line/color of the active track, update it to use `tracking.color.active`.

---

## Dependency/Affected Files Summary

| File | Change |
|------|--------|
| `maro.properties` | Add 4 new `tracking.*` properties |
| `app/build.gradle.kts` | Add 4 BuildConfig fields using `propInt` with `0x` hex ints |
| `SettingsManager.kt` | Add 5 `AppSettings` fields (tracksVisible, trackingRenderNb, trackingColorActive, trackingColorHistory, trackingColorPinned) + keys + load/save |
| `TrackViewModel.kt` | Add LRU track detail cache (`loadTrackDetailCached`, `invalidateTrackCache`) |
| `FanIconComponents.kt` | Add `TrackLayerIcon` composable |
| `CoastlineViewModel.kt` | Add `toggleTracksVisibility()` |
| `MapScreen.kt` | FanLayout: `maxCount` 5→6, `currentCount` 4→5, add tracks layer; Track overlay: incremental diff rendering with fade + LRU cache, active track color; NavigationSettings: add Tracking section with color pickers |
| `TrackDrawerOverlay.kt` | Optional: use tracking colors in active track preview |

## Rendering Algorithm

```
trackSummaries (all tracks)
  → filter visibleOnMap == true
  → sort by startTimeMs descending
  → take top N (trackingRenderNb)
  
For each track at index i (0 = most recent):
  alpha = 90% - (i / max(N-1, 1)) * 80%
  color = trackingColorHistory with RGB preserved, alpha applied
  strokeWidth = 10f if i == 0 else 6f
  add Polyline to map overlay with title "track_hist_{id}"

Active recording:
  color = trackingColorActive
  strokeWidth = 10f
  title = "track_recording"
```

## Review Findings (Ask Agent)

### 1. Feature Gaps
- **Error-state handling:** The plan uses silent `?: continue` when `loadTrackDetail()` fails — should at minimum log failures so rendering issues are diagnosable
- **Empty-state UX:** When `visibleOnMap` filters all tracks out or `trackingRenderNb == 0`, the map shows nothing. With a master `tracksVisible` layer toggle, this is acceptable — the layer toggle itself is the empty-state indicator
- **`maxCount = 5` leaves no room:** The fan layout's `maxCount` is currently 5 with 4 children. Adding tracks makes it 5 children, maxCount stays 5 — zero headroom for future layer buttons. **Recommend bumping `maxCount` to 6** preemptively so there's room for one more layer without geometry recalculation

### 2. Color Picker Approach
**Recommended: Canvas-based custom color picker** (~100 lines, zero new Gradle dependencies)
- The project already uses Canvas heavily in [`FanIconComponents.kt`](app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt) — same pattern
- Matches the Design Deviation Gate rule (§4 of AGENTS.md): no new external libraries needed
- Implementation: HSV color wheel + brightness slider using Compose Canvas + `colorToHSV`/`Color.HSVtoColor`
- One composable called with `onColorSelected: (Int) -> Unit` callback, reusable for all 3 color fields

### 3. Critical Performance Issues

**Issue A: Full teardown+rebuild on every `trackSummaries` emission**
- The current plan removes ALL `track_hist_*` polylines and re-adds them on every recomposition
- `trackSummaries` emits on ANY change (track added, deleted, edited — including 30s checkpoint saves during recording)
- With 20 tracks × potentially hundreds of GeoPoints each, this causes visible map stutter

**Fix: Incremental overlay diff instead of full rebuild**
- Maintain a `Set<String>` of currently-rendered track IDs
- On each LaunchedEffect trigger: compute diff against desired set (add new, remove stale, leave unchanged)
- Only update polylines for tracks whose alpha/width changed (i.e. index in the sorted order shifted)
- This avoids re-allocating and re-adding thousands of GeoPoints on every minor `trackSummaries` change

**Issue B: Protobuf deserialization × N on every refresh**
- `loadTrackDetail(id)` deserializes a protobuf binary file from disk on `Dispatchers.IO`
- Calling this for up to 20 tracks in a `for` loop = up to 20 disk reads + protobuf parses per refresh

**Fix: Add LRU cache in `TrackViewModel`**
- Add a `Cache<String, Track>` (e.g. using `kotlinx.coroutines` + `ConcurrentHashMap` or a simple `LinkedHashMap` with `maxSize = 30`)
- Cache tracks after first load; invalidate entry on `updateMetadata()`, `deleteTrack()`
- For the overlay rendering, read from cache first, fall through to disk on miss
- Bonus: cache hit avoids coroutine hop to `Dispatchers.IO` for already-loaded tracks

### 4. Memory
- 20 full `Track` objects at ~300 KB each ≈ 6 MB + GeoPoint overhead (~72 bytes per point × ~1000 points × 20 tracks ≈ 1.4 MB) — acceptable with LRU cache
- Without cache: re-allocation on every refresh triggers GC pressure, especially on lower-end devices

### 5. Other Flags

**`parseHexColor` in build.gradle.kts is unnecessary complexity**
- Simpler: use Kotlin's `0x` hex integer literal directly in `maro.properties` instead of `#` prefixed strings
- Or keep `#` prefix but parse inline — the `0x` approach means BuildConfig fields are just `propInt("tracking.color.active", 0xFF1565C0.toInt())`
- This avoids adding a `parseHexColor` helper function entirely

**Fragmented settings UX**
- Track recording settings (enable, geofence origin, radius) are in the **General** tab
- Track render settings (number of layers, colors) are proposed for the **Navigation** tab
- This creates a split UX where tracking is in two tabs. **Recommendation:** keep everything in Navigation tab for coherence, or move recording settings to a unified Tracking section. Flag for user decision.

## Decisions

1. **Color settings UI:** Use a Canvas-based custom color picker (~100 lines, zero new deps) for `tracking.color.active`, `tracking.color.history`, and `tracking.color.pinned`.

2. **Pinned (`tracking.color.pinned`):** Set up the infrastructure (maro.properties property, BuildConfig field, AppSettings field + keys + load/save) but do NOT implement pinned track rendering behavior yet. Reserved for future use.

3. **Default `tracksVisible`:** `true` — tracks layer visible out of the box.

4. **Settings tab unification:** Move track recording settings (track enabled, geofence origin, radius) from the **General** tab into the **Navigation** tab, under a unified **"Tracking" collapsible section**. The section layout:
   - **Expander header:** "Tracking"
   - **Contents (inside expander):**
     - Recording subsection (enable toggle, geofence origin lat/lon, radius, geofence toggle)
     - Divider
     - Render subsection (tracks layer visibility toggle, number of layers slider, color pickers for active/history/pinned)
   - Remove the old recording settings from the General tab
