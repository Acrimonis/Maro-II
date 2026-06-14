<!-- scope: feature -->

# Distance Tile — Display Scenarios & Requirements

## Overview

The Distance tile (`DistanceCard`) shows the distance to the nearest relevant boundary — either the shore, a zone boundary (when inside a zone near exit), or a zone entry point (when approaching a zone ahead).

## Scenarios

### 1. No data / Loading

| Field | Value |
|-------|-------|
| Condition | `state !is CoastlineState.Ready` or `distanceToShore == null` |
| Title | `DISTANCE` |
| Value | `—` (dash) |
| Subtitle | *(none)* |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 2. On land (`!isWater`)

| Field | Value |
|-------|-------|
| Condition | `isWater == false` |
| Title | `DISTANCE` |
| Value | `X.X km` / `X m` |
| Subtitle | `from sea` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |
| **Issue** | Card uses full-opacity `cardBg` instead of subdued grey (`zoneNormal` + `dullAlpha`) like zone/depth tiles |

### 3. On water — no zones nearby (`zoneSituation == null`)

| Field | Value |
|-------|-------|
| Condition | `state is Ready`, `isWater == true`, `zoneSituation == null` |
| Title | `DISTANCE` |
| Value | `X.X km` / `X m` |
| Subtitle | `from shore` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 4. On water — inside zone, far from exit

| Field | Value |
|-------|-------|
| Condition | `currentZone != null`, `isNearExit == false` |
| Title | `DISTANCE` |
| Value | shore distance |
| Subtitle | `from shore` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 5. Inside zone, near exit — beyond is OPEN_SEA

| Field | Value |
|-------|-------|
| Condition | `isNearExit`, `beyondType == OPEN_SEA`, no next zone ahead |
| Title | `DISTANCE` |
| Value | exit distance (to zone boundary) |
| Subtitle | `open water` |
| Card | 🟢 `zoneExit` (#2E7D32) |
| Border | none |

### 6. Inside zone, near exit — beyond is ZONE

| Field | Value |
|-------|-------|
| Condition | `isNearExit`, `beyondType == ZONE`, no next zone ahead |
| Title | `DISTANCE` |
| Value | exit distance (to zone boundary) |
| Subtitle | `→ NextZoneName` |
| Card | 🟠 `zoneEntry` (#E65100) |
| Border | none |

### 7. Inside zone, near exit — beyond is LAND

| Field | Value |
|-------|-------|
| Condition | `isNearExit`, `beyondType == LAND`, no next zone ahead |
| Title | `DISTANCE` |
| Value | exit distance (to zone boundary) |
| Subtitle | `land` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 8. Inside zone, near exit — next zone ahead on heading

| Field | Value |
|-------|-------|
| Condition | `isNearExit`, `nextZoneAhead != null` (↑ arrow) |
| Title | `DISTANCE` |
| Value | next zone entry distance |
| Subtitle | `→ NextZoneName` |
| Card | 🟠 `zoneEntry` (#E65100) |
| Border | none |

### 9. Outside zone — zone ahead but beyond `autoRevealDistanceM`

| Field | Value |
|-------|-------|
| Condition | `isNearEntry == false` (zone distance > `autoRevealDistanceM`) |
| Title | `DISTANCE` |
| Value | shore distance |
| Subtitle | `from shore` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |
| **Issue** | Zone IS the nearest boundary (closer than shore) but not shown because `isNearEntry` additionally gates on `autoRevealDistanceM` (200m) |

### 10. Outside zone — zone entry ahead within threshold, ↑ direction

| Field | Value |
|-------|-------|
| Condition | `isNearEntry == true`, `directionArrow == "↑"` |
| Title | `DISTANCE` |
| Value | entry distance |
| Subtitle | `→ ZoneName` (with optional `- ETA X s`) |
| Card | 🟠 `zoneEntry` (#E65100) |
| Border | none |

### 11. Outside zone — zone entry ahead within threshold, not ↑ direction

| Field | Value |
|-------|-------|
| Condition | `isNearEntry == true`, `directionArrow != "↑"` |
| Title | `DISTANCE` |
| Value | entry distance |
| Subtitle | `→ ZoneName` (with optional `- ETA X s`) |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

## Identified Issues

### Issue A — Scenario 2 (On land): Wrong background color
**Current:** 🔵 `cardBg` (#16213E) — full blue, same as normal data state
**Expected:** `zoneNormal` (#37474F) + `dullAlpha` (0.33) text, consistent with zone/depth tiles
**Fix:** Add explicit on-land check before the distance-to-shore display, similar to `SpeedLimitCard` / `DepthCard`

### Issue B — Scenario 9 (Zone ahead, beyond threshold): Wrong fallback
**Current:** Falls to shore distance when zone is closer than shore but beyond `autoRevealDistanceM`
**Expected:** Show zone distance whenever it's the nearest boundary (zone distance < shore distance), regardless of auto-reveal threshold. The `autoRevealDistanceM` gate should only apply to the zone tile's exit preview, not the distance tile's nearest-boundary logic.
**Fix:** Remove the `autoRevealDistanceM` / `autoRevealTimeS` gate from `isNearEntry`. Only keep the `zone distance < shore distance` comparison.

## Wiring

```kotlin
DistanceCard(
    distanceToShore = distanceToShore,
    isWater = isWater,
    state = state,
    zoneSituation = zoneSituation,
    autoRevealDistanceM = appSettings.zoneAutoRevealDistanceM,
    autoRevealTimeS = appSettings.zoneAutoRevealTimeS.toFloat(),
)
```

- `distanceToShore` from `CoastlineViewModel._distanceToShore`
- `zoneSituation` from `CoastlineViewModel._zoneSituation`
- `autoRevealDistanceM` / `autoRevealTimeS` from settings (for exit-only gating)

## Colour Key

| Name | Hex | Usage |
|------|-----|-------|
| 🔵 `cardBg` | #16213E | Default, on land, land exit, non-heading approach |
| 🟢 `zoneExit` | #2E7D32 | Exit to open sea |
| 🟠 `zoneEntry` | #E65100 | Exit to another zone, entry to a zone ahead |
