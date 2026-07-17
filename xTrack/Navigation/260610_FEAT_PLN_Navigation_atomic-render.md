<!-- scope: feature -->
# Cap Arrow Atomic Render — Discussion

## Problem

The user reports: *"The arrow is not jumping ahead of the boat. Is it possible to render the boat and the arrow in the same 'transaction'?"*

## Root Cause Analysis

### Atomicity Gap in State Emission

Inside [`CoastlineViewModel`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:378-380), the GPS fix handler writes to **two separate** `MutableStateFlow` instances sequentially:

```kotlin
_speedKnots.value = fix.speedMps?.let { it * MPS_TO_KNOTS }  // line 378
if (fix.hasCourse && fix.bearingDeg != null) {
    setMapBearing(fix.bearingDeg)                              // line 380 -> _mapBearing.value = deg
}
```

In the UI, [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:163-164) collects these as **two independent** `collectAsState()` calls:

```kotlin
val speedKnots by viewModel.speedKnots.collectAsState()
val mapBearing by viewModel.mapBearing.collectAsState()
```

### The Intermediate Frame

Even though both assignments happen in the same coroutine tick, Compose's snapshot system may process them in **separate frames**:

| Frame | `speedKnots` | `mapBearing` | Effect |
|-------|-------------|--------------|--------|
| 1 (stale) | old speed | old bearing | correct for old state |
| 2 (line 378 fires) | **new speed** | **old bearing** | ✗ arrow drawn at wrong angle |
| 3 (line 380 fires) | new speed | new bearing | ✓ correct |

Frame 2 is the problem: the arrow is drawn with the new speed (length) but the **previous frame's bearing** (angle), making it visually disconnected from the boat.

### Current Architecture — Already in Same Composable

The boat `Image` and the arrow `Canvas` are already inside the same [`CenterMarkerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:994-1079) composable — so they render in the same layout/draw pass **within a single frame**. The issue is that the **parameters** (`bearingDeg` / `speedKnots`) can arrive from two different frames due to the split StateFlows.

## Solution: Atomic NavigationState

Introduce a single data class that bundles all navigation-related UI state, exposed as **one** `StateFlow`. This guarantees Compose reads all values at the same snapshot version — no intermediate frames.

### Proposed Data Class

```kotlin
data class NavigationState(
    val bearingDeg: Float = 0f,
    val speedKnots: Float? = null,
    val demoSpeedKnots: Float? = null
)
```

### Changes Required

**1. ViewModel** — [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt)

- Replace `_mapBearing`, `_speedKnots`, `_demoSpeedKnots` with a single `_navigationState: MutableStateFlow<NavigationState>`
- Retain `setMapBearing()` logic (delta threshold), but fold into atomic update:

```kotlin
private fun setNavigationState(fix: GpsFix) {
    _navigationState.update { current ->
        val newSpeed = fix.speedMps?.let { it * MPS_TO_KNOTS }
        val newBearing = if (fix.hasCourse && fix.bearingDeg != null) {
            val delta = abs(((fix.bearingDeg - current.bearingDeg + 540f) % 360f) - 180f)
            if (delta >= MIN_BEARING_DELTA_DEG) fix.bearingDeg else current.bearingDeg
        } else current.bearingDeg
        current.copy(bearingDeg = newBearing, speedKnots = newSpeed)
    }
}
```

- Retain `lastGpsBearingMs` / `_needsCompass` for compass fallback — those are separate concerns.

**2. MapScreen** — [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

- Replace the three `collectAsState()` calls with one:

```kotlin
val navState by viewModel.navigationState.collectAsState()
```

- Pass `navState` as a single parameter through `MapContent` → `CenterMarkerOverlay`

**3. CenterMarkerOverlay** — [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:994)

- Replace `bearingDeg: Float`, `speedKnots: Float?`, `demoSpeedKnots: Float?` with `navigationState: NavigationState`
- Derive local values inside the composable:
  ```kotlin
  val bearingDeg = navigationState.bearingDeg
  val effectiveSpeedKn = navigationState.speedKnots ?: navigationState.demoSpeedKnots
  ```

### Benefits

- **Guaranteed atomicity** — Compose reads bearing + speed from the same snapshot
- **Eliminates one intermediate recomposition** (fewer total frames)
- **Cleaner API** — one parameter object vs three nullable scalars
- **No behavioral changes** — map rotation, compass fallback, and demo mode unaffected

## Open Questions

1. Should `demoSpeedKnots` also be folded into `NavigationState`? Currently set in a different coroutine (pan velocity). If folded, the demo speed update becomes atomic with bearing too.

2. Would an alternative approach — combining into a single `combine()` flow in the ViewModel — be simpler? E.g., `combine(_mapBearing, _speedKnots, _demoSpeedKnots) { ... }` — this avoids changing the field storage but still gives a single atomic flow for the UI to collect.

