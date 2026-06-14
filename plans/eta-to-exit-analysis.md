# ETA to Exit — What's Missing

## The ETA formula already exists

In [`buildHeadingResult()`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:1107):

```kotlin
val etaSeconds = if (currentSpeedKnots != null && currentSpeedKnots > 0f) {
    (distanceAheadM / (currentSpeedKnots * 0.514444)).coerceAtLeast(0.0)
} else null
```

Takes `distanceAheadM` and `currentSpeedKnots`, returns seconds. This is already computed on every pipeline tick and packed into `HeadingAheadResult`.

## What's missing

Only one thing: **heading-aligned exit distance** — how far from the boat to the zone boundary **along the current heading** when inside the zone.

### Gap 1: 300m band

[`distanceTo300mAlongHeading()`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:946) has an early return at line 953:

```kotlin
if (currentDistToCoast <= CoastlineRepository.ZONE_DISTANCE_M) return 0.0
```

When inside the band, it immediately returns 0.0. The ray-march + binary search only detects **entry** into the band, not exit.

**Fix:** Add an exit mode — walk forward until `coastDist > ZONE_DISTANCE_M`, binary search the boundary, return that distance. Same algorithm, inverted condition.

### Gap 2: SHOM speed zones

`firstSpeedZoneAhead()` uses a ray cast from the boat position through the speed zone index. Need to verify whether the spatial index can find the **exit** boundary when the ray starts inside a zone polygon. If the index only returns zones the boat is **not** inside, we need a different approach (e.g., walk along heading, query containment at each step).

### Gap 3: ETA display on the tile

ETA display format (`"45 s"` / `"1m 30s"`) already exists in the heading-ahead branch of SpeedLimitCard. No new UI work.

## Summary

| Component | Status | What's needed |
|---|---|---|
| `etaSeconds` formula | ✅ Exists | Nothing |
| Speed input (`speedKnots`) | ✅ Available | Nothing |
| Heading-aligned exit distance (300m) | ❌ Returns 0.0 | Invert ray-march condition |
| Heading-aligned exit distance (SHOM) | ❓ Unknown | Verify spatial index behavior |
| Display format on tile | ✅ Exists | Nothing |
