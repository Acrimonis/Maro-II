<!-- scope: feature -->

# Zone Tile (SpeedLimitCard) — Display Scenarios & Requirements

## Overview

The Zone tile (`SpeedLimitCard`) shows the current speed regulation zone — either the 300m band or a SHOM speed zone. It displays the zone name, speed limit, and optionally an exit preview when near the boundary.

## Scenarios

### 1. No data / Loading

| Field | Value |
|-------|-------|
| Condition | `state !is CoastlineState.Ready` |
| Title | `300M ZONE` (`dash_zone_title`) |
| Value | `—` (dash) |
| Subtitle | *(none)* |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 2. On land

| Field | Value |
|-------|-------|
| Condition | `!isWater` |
| Title | `300M ZONE` |
| Value | `On land` (`dash_not_at_sea`) |
| Subtitle | `Out of zone` (`dash_out_of_zone`) |
| Card | 🔘 `zoneNormal` (#37474F) |
| Text | all dull (33% alpha) |
| Border | none |

### 3. No zone data → LIBRE

| Field | Value |
|-------|-------|
| Condition | `zoneSituation == null` (or no zones at all) |
| Title | `LIBRE` |
| Value | `--` |
| Subtitle | `No limit` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 4. Inside zone, far from exit

| Field | Value |
|-------|-------|
| Condition | `currentZone != null`, `isNearExit == false` |
| Title | `ZoneName` (e.g. `BANDE 300M`) |
| Value | `5 kn` (speed limit) |
| Subtitle | *(none)* — limit only |
| Card | 🟢 `zoneCompliant` / 🟢 `speedSafe` / 🟠 `speedCaution` / 🔴 `speedDanger` (by speed compliance) |
| Border | 2dp same as card |

### 5. Inside zone, near exit → OPEN_SEA

| Field | Value |
|-------|-------|
| Condition | `isNearExit`, `beyondType == OPEN_SEA` |
| Title | `ZoneName` |
| Value | `5 kn` |
| Subtitle | `open water` |
| Card | compliance color |
| Border | 2dp |

### 6. Inside zone, near exit → ZONE

| Field | Value |
|-------|-------|
| Condition | `isNearExit`, `beyondType == ZONE` |
| Title | `ZoneName` |
| Value | `5 kn` |
| Subtitle | `→ NextZoneName` |
| Card | compliance color |
| Border | 2dp |

### 7. Inside zone, near exit → LAND

| Field | Value |
|-------|-------|
| Condition | `isNearExit`, `beyondType == LAND` |
| Title | `ZoneName` |
| Value | `5 kn` |
| Subtitle | *(none)* — land exit shows no subtitle |
| Card | compliance color |
| Border | 2dp |

### 8. Zones ahead on heading (approaching)

| Field | Value |
|-------|-------|
| Condition | `currentZone == null`, `zonesAround` not empty, within reveal thresholds |
| Title | `ZoneName` |
| Value | `5 kn` (limit of ahead zone) |
| Subtitle | `ZoneName - ETA X s` |
| Card | 🟢/🟠/🔴 by speed compliance vs ahead zone's limit |
| Border | none (unless compliance colored) |

### 9. Zones ahead, beyond reveal thresholds

| Field | Value |
|-------|-------|
| Condition | `currentZone == null`, zones ahead but distance > `autoRevealDistanceM` AND ETA > `autoRevealTimeS` |
| Title | `LIBRE` |
| Value | `--` |
| Subtitle | `No limit` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

## Identified Issues

### Issue A — Threshold mismatch
**Current:** `SpeedLimitCard` uses `autoRevealDistanceM` / `autoRevealTimeS` (shared with map auto-show, defaults 200m/20s) for `isNearExit`
**Should use:** `ZoneConfig.distanceOutOfZoneInfoM` / `ZoneConfig.distanceOutOfZoneTimeS` (100m/10s) — consistent with distance tile

### Issue B — Exit subtitle: distance removed
The plan says zone tile should "remove distance from subtitle — only show limit + beyond type + next zone name". Currently the subtitle only shows beyond type (no distance number), which matches. But confirm this is correct.

## Wiring

```kotlin
SpeedLimitCard(
    state = state,
    isWater = isWater,
    speedKnots = speedKnots,
    zoneSituation = zoneSituation,
    autoRevealDistanceM = appSettings.zoneAutoRevealDistanceM,
    autoRevealTimeS = appSettings.zoneAutoRevealTimeS.toFloat(),
)
```

Currently uses `autoRevealDistanceM`/`autoRevealTimeS` from settings. Should use `ZoneConfig.distanceOutOfZoneInfoM`/`ZoneConfig.distanceOutOfZoneTimeS` for `isNearExit` check.
