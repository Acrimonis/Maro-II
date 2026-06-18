# Plan: Drawer Tweaks — Padding, Hamburger, Track List Toggle

## 1. Track list sort by date start desc

In [`TrackViewModel.refreshSummaries()`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:114):

```kotlin
fun refreshSummaries() {
    viewModelScope.launch {
        _summaries.value = repository.listTracks()
            .sortedByDescending { it.startTimeMs }  // ← add this
    }
}
```

## 2. Padding between hamburger and settings button

At [`MapScreen.kt:1178-1185`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1178), the right-edge button column currently has no spacing between the hamburger and settings buttons.

**Change:** Add `verticalArrangement = Arrangement.spacedBy(8.dp)` to the Column, or insert a `Spacer(Modifier.height(4.dp))` between them.

## 3. Bigger hamburger icon

[`HamburgerIcon()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1843) draws on a 28dp Canvas. Increase to 36dp:

```kotlin
Canvas(modifier = Modifier.size(36.dp))
```

The stroke width and gaps are proportional (`size.width * 0.15f`, etc.) so they scale automatically.

## 4. Menu reorganization — Track List row with toggle

### Current drawer content (after section header + divider):

```
Text("Track List")                                    ← clickable
────────────────────────────────────
Row("Start Tracking"           [Switch])              ← REMOVE
── Stats card (if active) ──                          ← KEEP
```

### New layout:

```
Row("Track List"               [ColoredSwitch])       ← Track List + ON/OFF toggle
── Stats card (if active) ──                          ← same
```

### Visual on/off switch with status icon colors

The [`Switch`](app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt:179-191) already uses `SwitchDefaults.colors()`. Replace with colors matching the tracking status icon:

| State | Thumb | Track |
|---|---|---|
| **ON** (recording) | `status.tracking.healthy` (green `#CC4CAF50`) | Green at 40% alpha |
| **ON + idle** (not recording) | `status.tracking.idle` (blue `#FF1565C0`) | Blue at 40% alpha |
| **OFF** | `status.tracking.off` dimmed | As today (muted) |

The switch colors change dynamically based on `recorderState.isMoving`:

```kotlin
val thumbColor = when {
    !isActive -> Color(AppConfig.uiSettingsTextMuted)
    recorderState.isMoving -> Color(AppConfig.statusTrackingHealthy)
    else -> Color(AppConfig.statusTrackingIdle)
}
val trackColor = thumbColor.copy(alpha = 0.4f)
```

### Callback wiring

The toggle uses the existing `onStartRecording`/`onStopRecording` callbacks — no new wiring needed.

## Files to modify

| File | Changes |
|---|---|
| `TrackViewModel.kt:114` | Add `.sortedByDescending { it.startTimeMs }` to `refreshSummaries()` |
| `MapScreen.kt:1178-1185` | Add spacing between hamburger and settings buttons |
| `MapScreen.kt:1843` | Change `HamburgerIcon()` Canvas size from 28dp → 36dp |
| `TrackDrawerOverlay.kt:147-212` | Merge Track List row and toggle; remove separate Start/Stop row; color switch dynamically |

## Migration

No settings migration. Visual change only.
