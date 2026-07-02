# Auto-Marker Proximity Fix

**Created:** 2026-07-02 22:55 UTC
**Branch:** feature/markers-date-points-fix
**Feature:** Markers (cross-cutting with BoatTrace/GPS)

## Problem

`addTempAutoMarker()` creates 🕐 idle pins with `proximityOverrideM = null`.
`proximityRange()` falls back to `Double.MAX_VALUE` for null → Pin distance gate
never rejects any auto marker. Every auto marker anywhere on the map matches
`whereAmI()` regardless of actual boat distance.

No coastline ray-casting is triggered (Pins skip `segmentIntersectsLand`), but
the debug overlay shows green segments to every matching auto marker.

## Fix (3 files)

### 1. `maro.properties` — new config key

```properties
# Proximity range (metres) for auto-created 🕐 idle markers.
# Boat must be within this distance for whereAmI to match.
track.boatMarker.autoMarker.proximityM=300
```

Insert after line 45 (`track.boatMarker.autoMarker.opacity=50`).

### 2. `AppConfig.kt` — new field + loading

**Field** (after `boatMarkerIdleOpacityPct` at line 107):

```kotlin
/** Proximity range (m) for 🕐 auto-marker pins. Set via `track.boatMarker.autoMarker.proximityM` in maro.properties. */
var boatMarkerAutoMarkerProximityM: Double = 300.0
    private set
```

**Loading** (after opacity loading at line 528):

```kotlin
props.getProperty("track.boatMarker.autoMarker.proximityM")?.toDoubleOrNull()?.let {
    boatMarkerAutoMarkerProximityM = it.coerceAtLeast(0.0)
}
```

### 3. `MarkersViewModel.kt` — use the config

Change line 643 from:

```kotlin
proximityOverrideM = null,
```

To:

```kotlin
proximityOverrideM = AppConfig.boatMarkerAutoMarkerProximityM,
```

## Impact

- `whereAmI()` will only match auto markers within 300m of the boat
- No change to user-created markers (they always have explicit proximity)
- No change to `confirmAutoMarker()` or any other marker flow
- Tunable via `maro.properties` without rebuild

## Key Files

- `app/src/main/assets/maro.properties` — new key
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — field + loading
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — use config
