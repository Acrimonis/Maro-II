# Inside-Zone Functionality Discussion

## 1. Distance to end of zone at current heading

### 300m band (`distanceTo300mAlongHeading`)
Returns **0.0 immediately** when inside the band (line 953):
```kotlin
if (currentDistToCoast <= CoastlineRepository.ZONE_DISTANCE_M) return 0.0
```
This function only detects **entries** into the band from outside. It does NOT compute exit distance from inside.

**To fix:** Add an "exit band" mode — when inside the band, walk forward along heading until `coastDist > ZONE_DISTANCE_M` (exited the band), then binary search. This is the same algorithm but inverted.

### SHOM speed zones (`firstSpeedZoneAhead`)
Queries `SpeedZoneIndex` via ray projection. Needs checking whether the spatial index returns the **exit** boundary when the ray origin is inside the zone polygon — it may only find zones the boat is **not** in.

## 2. ETA to end of zone at current heading/speed

Already computed in `buildHeadingResult()` (line 1107):
```kotlin
val etaSeconds = distanceAheadM / (speedKnots * 0.514444)
```
Would work immediately once we have the heading-aligned exit distance.

## 3. What's beyond the zone at current heading?

Requires a multi-step query from the exit point:

```
boat pos → [inside zone] → exit boundary → [beyond]
                                           ├─ Another zone?   → query speedZoneIndex
                                           ├─ Land?           → query coastline isOnWater
                                           └─ Open sea?       → neither of the above
```

For **another zone**: call `firstSpeedZoneAhead(exitLat, exitLon, headingDeg)` from the exit point.

For **land**: call `isOnWater(exitLat, exitLon)` at the exit point and a small offset beyond it.

For **open sea**: fallthrough when neither land nor another zone is detected.

## Summary

| Question | Feasible now? | What's needed |
|---|---|---|
| Exit distance at heading | ❌ No | New exit-detection mode in `distanceTo300mAlongHeading` + verify `firstSpeedZoneAhead` works from inside |
| ETA to exit | ✅ Yes | Uses existing `etaSeconds` formula — just needs the exit distance |
| What's beyond | ⚠️ Partial | Can query exit point for land/zone, but needs new multi-step query pipeline |
