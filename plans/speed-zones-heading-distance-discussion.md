# SpeedZones — Heading-Aware Distance & Auto-Show Gaps

## Requirement 1: Heading-aware distance to next speed zone ahead

The top-right dashboard tile, when OUTSIDE any speed zone, must display the distance to the **next speed zone in front of the boat according to the current heading** — not the closest point in any direction.

### What's implemented (closest-point only)

The current [`SpeedLimitCard`](../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) uses `distanceToSpeedZone` from [`SpeedZoneIndex.query()`](../app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt), which computes the **minimum Euclidean distance to any polygon edge** via `SpatialOperations.pointToSegmentDistance`. This is a closest-point query, not a heading-ahead query.

The card currently shows:
```
┌──────────────────────┐
│     300m zone        │  ← generic zone title
│      180 m           │  ← closest distance, any direction
│   to the 300m zone   │
└──────────────────────┘
```

### What's needed (heading-ahead)

Per the [speed-zones-design.md](../plans/speed-zones-design.md) §3, heading is available from:
- **GPS mode**: GPS COG (course over ground) or compass azimuth fallback
- **Demo mode**: pan velocity direction (bearing between successive `computeDemoSpeed()` calls)

When heading IS available → heading-aware distance → show:
```
┌──────────────────────┐
│    CAP D'ANTIBES     │  ← zone name first encountered along heading
│       10 kn          │  ← that zone's speed limit
│     → 180 m          │  ← distance AHEAD along heading
│  Vit 4.5 · ETA 1m18s │  ← current speed + estimated time to zone
└──────────────────────┘
```

When heading is NOT available (GPS lost, demo stopped, first launch):
```
┌──────────────────────┐
│       LIBRE          │
│        --            │
│   Aucune limite      │
└──────────────────────┘
```

### Missing algorithms (3 functions from §3 of the plan)

| Algorithm | File | Status |
|-----------|------|--------|
| `distanceTo300mAlongHeading()` — ray-march along heading in 10m steps, check `distanceToCoast`, binary search when ≤300 | CoastlineViewModel or SpatialOperations | ❌ Not implemented |
| `firstSpeedZoneAhead()` — ray-polygon intersection: cast ray from boat along heading, find first polygon edge crossing | SpeedZoneIndex | ❌ Not implemented |
| `querySpeedZoneAhead()` — combined pipeline: closest of band + SHOM, return zone name + limit + distance + ETA | CoastlineViewModel | ❌ Not implemented |

### Effort estimate
- `distanceTo300mAlongHeading()`: ~20 lines, pure geometry, no dependencies
- `firstSpeedZoneAhead()`: ~60 lines, standard segment intersection test, needs `SpatialOperations` or inline
- `querySpeedZoneAhead()` + StateFlow: ~15 lines
- SpeedLimitCard update to heading-ahead format: ~40 lines (zone name, limit, →Xm, ETA)

---

## Requirement 2: Auto-show according to settings (distance or time at current speed)

The speed zone overlay must auto-show when the boat approaches a speed zone, using the same distance/time hybrid logic as the 300m zone's [`zone300Decision()`](../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt).

### What's implemented

| Component | Status |
|-----------|--------|
| `speedZonesVisible` toggle in AppSettings | ✅ Done |
| `speedZoneAutoShowGps` / `speedZoneAutoShowDemo` toggles | ✅ Done |
| `speedZoneManuallyHidden` / `speedZoneAutoRevealed` / `lastDistToSpeedZone` state vars | ✅ Done |
| `toggleSpeedZonesVisibility()` method | ✅ Done |

### What's missing

❌ The auto-show decision logic is **not wired** into the shore pipeline's `onEach` block. The 300m zone's `zone300Decision()` is called there, but no parallel call exists for speed zones.

The skeleton is ready — the shore pipeline just needs an additional block like:

```kotlin
// In onEach { shore -> ... }, after the 300m auto-show block:
val szAutoShowEnabled = if (cfg.gpsMode) cfg.speedZoneAutoShowGps else cfg.speedZoneAutoShowDemo
if (szAutoShowEnabled && shore.speedZoneQuery.nearestZone != null) {
    val szDecision = speedZoneDecision(...)
    when (szDecision.action) {
        SpeedZoneAction.REVEAL -> settingsManager.update { it.copy(speedZonesVisible = true) }
        SpeedZoneAction.HIDE -> settingsManager.update { it.copy(speedZonesVisible = false) }
        SpeedZoneAction.NONE -> {}
    }
    speedZoneAutoRevealed = szDecision.autoRevealed
}
```

A `speedZoneDecision()` function would mirror `zone300Decision()` but operate on `SpeedZoneQuery` data (distance to nearest speed zone, inside/outside, approaching direction, speed).

---

## Summary

| Gap | Blocks Feature? | Effort |
|-----|----------------|--------|
| Heading-ahead distance (ray-march + ray-polygon) | Yes — the card shows wrong distance semantics | ~95 lines of geometry code |
| Heading-ahead card format (zone name, limit, →Xm, ETA) | Yes — card shows generic info | ~40 lines of Compose |
| Auto-show wired into shore pipeline | Partially — state machine ready, no decision call | ~30 lines of pipeline wiring |
