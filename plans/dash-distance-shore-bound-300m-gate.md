# Shore-bound 300m Gate — Distance Tile Fix

## Bug

When the boat is inside the 300m band heading **towards shore**, `findBandExitAlongHeading()` ray-marches forward but `coastDist` decreases (closer to shore) — never exceeds `ZONE_DISTANCE_M`. Ray-march returns `null` → `infoToZoneExitAlongHeading()` returns `null` → `currentZone` stays `null`.

Then `boundary` falls through to `zoneSituation?.zonesAround?.firstOrNull()` — which picks up any SHOM zone entry ahead on heading. The distance tile incorrectly shows AMBER for the SHOM entry, ignoring that the boat is heading towards shore inside the 300m band.

## Root cause

[`findBandExitAlongHeading()`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1460) only detects exits where `coastDist` exceeds `ZONE_DISTANCE_M` (seaward exit). Shore-bound headings never satisfy this — the function correctly returns `null`, but DistanceCard doesn't distinguish "no exit because heading shoreward" from "no zone at all."

## Fix

**File:** [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt), after line 353 (`val currentZone = zoneSituation?.currentZone`), before line 355 (`val boundary = currentZone`).

**Insert:**

```kotlin
    // Inside 300m band heading towards shore: exit ray-march fails (heading
    // goes landward, coastDist never exceeds ZONE_DISTANCE_M). Treat as
    // LAND exclusion — show coastline instead of letting zonesAround
    // hijack the boundary with an unrelated SHOM zone entry ahead.
    if (currentZone == null && isWater && distanceToShore != null && distanceToShore <= 300.0) {
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = distanceText(distanceToShore),
            subtitle = stringResource(R.string.dash_distance_from_shore),
            cardColor = DashboardColors.cardBg,
            modifier = modifier
        )
        return
    }
```

## Why 300.0

`CoastlineRepository.ZONE_DISTANCE_M` = 300. The constant is not directly accessible from the UI layer (`DashboardPanel.kt`). Hardcoding `300.0` matches the band definition and is consistent with the guard in [`NavigationViewModel.kt:460`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:460).

## Impact

| Scenario | Before | After |
|----------|--------|-------|
| Inside 300m, heading seaward, exit found | Works correctly | Unchanged |
| Inside 300m, heading shoreward, SHOM ahead | ❌ AMBER (SHOM entry hijacks) | ✅ Coastline |
| Inside 300m, heading shoreward, no SHOM ahead | Coastline (boundary=null) | Unchanged |
| Inside SHOM zone, heading shoreward | Already handled by LAND exclusion at line 386 | Unchanged |
| Outside all zones | Unchanged | Unchanged |
