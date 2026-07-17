# FEAT_PLN: Notification — Dynamic Multi-line Format

**Feature:** BoatTrace
**Date:** 2026-06-29
**Branch:** feature/notif-fix
**Status:** implemented + fix pending

## Title Line (collapsed)

```
Maro II • [GPS|Demo] • [Navigating|Idle|Moving] • [Recording|Ready] • [On Water|On Land]
```

## Rendering: `label: value` format (font-safe)

No fixed-width columns — proportional `Roboto` font won't align. Use simple `label: value`:

```kotlin
private fun statRow(label: String, value: String, indent: Boolean = false): String {
    val prefix = if (indent) "  " else ""
    return "$prefix$label: $value"
}
```

## Expanded Layouts

### Recording + Navigating/Moving
```
Maro II — GPS • Navigating • Recording • On Water
──────────────────────────────────────────────────
Speed: 12.3 kn
Distance: 1.2 nm
Elapsed: 00:05:53
  Navigating: 05:23
  Stationary: 00:30
Avg Speed: 8.5 kn
Max Speed: 22.1 kn
Points: 342
```

### Recording + Idle
```
Maro II — GPS • Idle • Recording • On Water
──────────────────────────────────────────────────
Elapsed: 00:07:15
Distance: 1.2 nm
  Navigating: 05:23
  Stationary: 01:52
Points: 342
```

### Ready
```
Maro II — GPS • Navigating • Ready • On Water
──────────────────────────────────────────────────
Speed: 12.3 kn
```

### Fix: Duplicate "Idle" label
Indented stat row renamed from `Idle` → `Stationary`. Title segment 3 already shows `Idle` — the stat row shows the *duration*, not the state.

## Implementation

### TrackRecordingService.kt — statRow helper
```kotlin
private fun statRow(label: String, value: String, indent: Boolean = false): String {
    val prefix = if (indent) "  " else ""
    return "$prefix$label: $value"
}
```

### Build lines
```kotlin
val lines = mutableListOf<String>()
if (recording) {
    if (isMoving) lines.add(statRow("Speed", "$speed kn"))
    lines.add(statRow("Distance", "$dist nm"))
    lines.add(statRow("Elapsed", formatElapsed(elapsedSec)))
    val navTimeLabel = if (navLabel == "Idle") "Navigating" else navLabel
    lines.add(statRow(navTimeLabel, formatElapsed(elapsedSec - idleSec), indent = true))
    lines.add(statRow("Stationary", formatElapsed(idleSec), indent = true))
    if (isMoving) {
        lines.add(statRow("Avg Speed", "$avgSpeed kn"))
        lines.add(statRow("Max Speed", "$maxSpeed kn"))
    }
    lines.add(statRow("Points", "$pointCount"))
} else {
    lines.add(statRow("Speed", if (isMoving) "$speed kn" else "— kn"))
}
```

## Files Touched
| File | Change |
|------|--------|
| `app/.../ui/map/MapScreen.kt` | Extras (done) |
| `app/.../data/track/TrackRecordingService.kt` | Fix: `label: value` format, rename Idle→Stationary |
