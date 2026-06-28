# Plan: marker-pin-tri-state — Fan Layer Tri-State Toggle

> **Feature:** Markers | **Subfeature:** marker-pin | **Branch:** feature/marker-pin
> **Depends on:** marker-pin (UserMarker.pinned + togglePin — already implemented)

## Goal

Replace the binary fan layer toggle for markers with a tri-state cycle:
**Hidden → Show All → Show Pinned → Hidden**

## Current State

| Aspect | Current |
|--------|---------|
| Setting | `userMarkersVisible: Boolean` in [`SettingsManager.kt:170`](../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:170) |
| ViewModel | `toggleVisibility()` + `showLayer()` — binary |
| Fan child | [`MapScreen.kt:1520`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1520) — `LocationOnIcon(alpha)` |
| onChildClick | [`MapScreen.kt:1537`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1537) — `onToggleUserMarkers()` |
| Marker list | `userMarkers` — unfiltered, passed to `MarkerOverlay` at [line 1018](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1018) |
| activeChildCount | counts `markerLayerVisible` boolean |

## Design

### State Model

Use an `enum class` (matches project conventions — sealed classes/enums used for `MarkerDrawerState`, `MarkerType`, `WizardStep`):

```kotlin
enum class MarkerLayerState { HIDDEN, SHOW_ALL, SHOW_PINNED }
```

Default: `SHOW_ALL` — matches existing `userMarkersVisible = true`.

### State → Behavior Mapping

| State | Fan child `isActive` | Icon | MarkerOverlay receives | Rendering |
|-------|---------------------|------|----------------------|-----------|
| HIDDEN | false | `LocationOff`, inactive alpha | nothing (not rendered) | — |
| SHOW_ALL | true | `LocationOn`, active alpha | all `userMarkers` | All geometry (zones + proximity). Pinned markers: icon replaces center dot. |
| SHOW_PINNED | true | `WhereToVote` (active) | only `userMarkers.filter { it.pinned }` | Icon only by default. Selected marker (dashboard): full geometry (icon + zones + proximity). |

### Rendering Rules Detail

**`drawGeometry`** gate (in `MarkerOverlay.kt`):
```kotlin
val drawGeometry = markerLayerState != MarkerLayerState.SHOW_PINNED
    || marker.id == selectedMarkerId
```

| Mode | Pinned? | Selected? | Dots | Zones | Proximity | Icon |
|------|---------|-----------|------|-------|-----------|------|
| SHOW_ALL | No | — | ✅ | ✅ | ✅ | ❌ |
| SHOW_ALL | Yes | — | ❌ | ✅ | ✅ | ✅ |
| SHOW_PINNED | Yes | No | ❌ | ❌ | ❌ | ✅ |
| SHOW_PINNED | Yes | Yes | ❌ | ✅ | ✅ | ✅ |

### Cycle Logic

```
onChildClick(index=5):
  state = next(state)  // HIDDEN→SHOW_ALL→SHOW_PINNED→HIDDEN
  persist
```

### Migration (Boolean → Enum)

Existing users with `userMarkersVisible = false` must become `HIDDEN`, not `SHOW_ALL`. Add migration in `SettingsManager` init block following existing `CURRENT_VERSION` pattern:

1. Bump `CURRENT_VERSION` to 3
2. If old version < 3: read old `KEY_USER_MARKERS_VISIBLE` (default true), seed `KEY_MARKER_LAYER_STATE`, remove old key

## Implementation Steps

### Step 1 — MarkerLayerState enum

**File:** `MarkersViewModel.kt` — add at top level (alongside other sealed/enum types):

```kotlin
enum class MarkerLayerState { HIDDEN, SHOW_ALL, SHOW_PINNED }
```

### Step 2 — AppSettings + SettingsManager migration

**File:** `SettingsManager.kt`

**2a.** Replace `userMarkersVisible: Boolean = true` with:
```kotlin
val markerLayerState: MarkerLayerState = MarkerLayerState.SHOW_ALL
```

**2b.** Add key constant (replace `KEY_USER_MARKERS_VISIBLE`):
```kotlin
private const val KEY_MARKER_LAYER_STATE = "marker_layer_state"
```

**2c.** Bump `CURRENT_VERSION` to 3.

**2d.** In `init` block, add migration:
```kotlin
if (oldVersion < 3) {
    val oldVisible = prefs.getBoolean("user_markers_visible", true)
    val newState = if (oldVisible) MarkerLayerState.SHOW_ALL.name else MarkerLayerState.HIDDEN.name
    editor.putString(KEY_MARKER_LAYER_STATE, newState)
    editor.remove("user_markers_visible")
}
```

**2e.** Update `load()` to read:
```kotlin
markerLayerState = try {
    MarkerLayerState.valueOf(prefs.getString(KEY_MARKER_LAYER_STATE, MarkerLayerState.SHOW_ALL.name)!!)
} catch (_: Exception) { MarkerLayerState.SHOW_ALL }
```

**2f.** Update `save()` to write:
```kotlin
putString(KEY_MARKER_LAYER_STATE, updated.markerLayerState.name)
```

### Step 3 — MarkersViewModel.kt

**3a.** Replace `userMarkersVisible` StateFlow (line 119-129) with derivations from `markerLayerState`:
```kotlin
val markerLayerState: StateFlow<MarkerLayerState> =
    settingsManager.settings.let { flow ->
        MutableStateFlow(flow.value.markerLayerState).also { sf ->
            viewModelScope.launch { flow.collect { sf.value = it.markerLayerState } }
        }.asStateFlow()
    }

/** Derived: true when layer is not hidden (backward-compat for existing consumers). */
val userMarkersVisible: StateFlow<Boolean> =
    markerLayerState.let { flow ->
        MutableStateFlow(flow.value != MarkerLayerState.HIDDEN).also { sf ->
            viewModelScope.launch { flow.collect { sf.value = it != MarkerLayerState.HIDDEN } }
        }.asStateFlow()
    }
```

**3b.** Replace `toggleVisibility()` + `showLayer()`:
```kotlin
fun cycleMarkerLayerState() {
    val next = when (markerLayerState.value) {
        MarkerLayerState.HIDDEN -> MarkerLayerState.SHOW_ALL
        MarkerLayerState.SHOW_ALL -> MarkerLayerState.SHOW_PINNED
        MarkerLayerState.SHOW_PINNED -> MarkerLayerState.HIDDEN
    }
    settingsManager.update { it.copy(markerLayerState = next) }
}

fun showLayer() {
    if (markerLayerState.value == MarkerLayerState.HIDDEN)
        settingsManager.update { it.copy(markerLayerState = MarkerLayerState.SHOW_ALL) }
}
```

### Step 4 — MapScreen.kt

**4a. Read new state (line ~494-495):**
```kotlin
val markerLayerState by markersViewModel.markerLayerState.collectAsState()
val markerLayerVisible = markerLayerState != MarkerLayerState.HIDDEN
```

**4b. Filter markers for MarkerOverlay (line ~1018):**
```kotlin
markers = when (markerLayerState) {
    MarkerLayerState.SHOW_PINNED -> userMarkers.filter { it.pinned }
    else -> userMarkers
}
```

**4c. Fan child icon (line ~1520):**
```kotlin
{ isActive -> LocationOnIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) }
```
No visual distinction between SHOW_ALL and SHOW_PINNED — deferred to follow-up.

**4d. onChildClick (line ~1537):** replace `5 -> onToggleUserMarkers()` with `5 -> onCycleMarkerLayer()`.

**4e. activeChildCount (line ~1509):** replace `markerLayerVisible` Boolean with `markerLayerState != MarkerLayerState.HIDDEN`.

**4f. activeStates (line ~1528):** replace `markerLayerVisible` with `markerLayerState != MarkerLayerState.HIDDEN`.

**4g. Function signature (line ~1208):** replace `onToggleUserMarkers` param with `onCycleMarkerLayer`, replace `markerLayerVisible: Boolean` with `markerLayerState: MarkerLayerState`.

**4h. Call site (line ~912-913):** update wiring:
```kotlin
markerLayerState = markerLayerState,
onCycleMarkerLayer = { markersViewModel.cycleMarkerLayerState() },
```

### Step 5 — MarkerOverlay.kt (rendering differentiation)

**File:** `MarkerOverlay.kt`

**5a.** Add `drawGeometry` master gate (SHOW_PINNED suppresses geometry for non-selected):
```kotlin
val drawGeometry = markerLayerState != MarkerLayerState.SHOW_PINNED
    || marker.id == selectedMarkerId
```

**5b.** Tighten `drawZones` to also respect `drawGeometry`:
```kotlin
val drawZones = drawGeometry && (!confirmed || markerZonesVisible)
```

**5c.** Add `skipDots` flag — pinned markers use icon instead of center dot:
```kotlin
val skipDots = marker.pinned
```

**5d.** Pin branch: gate `addPinOverlay` on `drawGeometry && !skipDots`.

**5e.** Circle branch: pass `skipDots` to `addCircleOverlay`; fallback center-dot also gated on `drawGeometry && !skipDots`.

**5f.** Corridor branch: pass `skipDots` to `addCorridorOverlay`; fallback p1/p2 dots also gated on `drawGeometry && !skipDots`.

**5g.** Add `skipDots: Boolean = false` parameter to `addCircleOverlay` and `addCorridorOverlay`. When true, skip the center/p1/p2 Marker overlays (icon overlays from the icon loop handle tap detection via the geographic `MapEventsOverlay`).

### Step 6 — FanIconComponents.kt

SHOW_PINNED now uses `WhereToVoteIcon` (already implemented — see MapScreen.kt line 1552).

## Files Summary

| # | File | Change |
|---|------|--------|
| 1 | `MarkersViewModel.kt` | `MarkerLayerState` enum |
| 2 | `SettingsManager.kt` | `markerLayerState: MarkerLayerState`, migration, persistence |
| 3 | `MarkersViewModel.kt` | `markerLayerState` + `userMarkersVisible` StateFlows, `cycleMarkerLayerState()`, `showLayer()` |
| 4 | `MapScreen.kt` | Tri-state wiring (icon, filter, cycle, signature) |
| 5 | `MarkerOverlay.kt` | `drawGeometry` gate, `skipDots`, per-mode rendering differentiation |
| 6 | `FanIconComponents.kt` | No change (SHOW_PINNED icon already `WhereToVote`) |
