<!-- scope: feature -->

# Speed Tile — Display Scenarios & Requirements

## Overview

The Speed tile (`SpeedCard`) shows current speed over ground (SOG) with colour-coded compliance relative to the active speed zone limit.

## Scenarios

### 1. No GPS fix / Demo stopped

| Field | Value |
|-------|-------|
| Condition | `speedKnots == null` |
| Title | `SPEED` |
| Value | `—` (dash) |
| Subtitle | `demo mode` |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 2. Unrealistic speed

| Field | Value |
|-------|-------|
| Condition | `speedKnots > 99.9 kn` |
| Title | `SPEED` |
| Value | `—` (dash) |
| Subtitle | *(none)* |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 3. No zone limit

| Field | Value |
|-------|-------|
| Condition | `activeSpeedLimitKn == null` |
| Title | `SPEED` |
| Value | `X.X kn` |
| Subtitle | *(none)* |
| Card | 🔵 `cardBg` (#16213E) |
| Border | none |

### 4. Inside zone — compliant (speed ≤ limit)

| Field | Value |
|-------|-------|
| Condition | `speedKnots <= limit` |
| Title | `SPEED` |
| Value | `X.X kn` |
| Subtitle | `Limit Y kn` |
| Card | 🟢 `speedSafe` (#2E7D32) |
| Border | 2dp 🟢 |

### 5. Inside zone — caution (limit < speed ≤ limit×1.4)

| Field | Value |
|-------|-------|
| Condition | `speedKnots ≤ limit × 1.4` |
| Title | `SPEED` |
| Value | `X.X kn` |
| Subtitle | `Limit Y kn` |
| Card | 🟠 `speedCaution` (#EF6C00) |
| Border | 2dp 🟠 |

### 6. Inside zone — speeding (speed > limit×1.4)

| Field | Value |
|-------|-------|
| Condition | `speedKnots > limit × 1.4` |
| Title | `SPEED` |
| Value | `X.X kn` |
| Subtitle | `Limit Y kn` |
| Card | 🔴 `speedDanger` (#C62828) |
| Border | 2dp 🔴 |

## Wiring

```kotlin
SpeedCard(
    speedKnots = speedKnots,
    activeSpeedLimitKn = zoneSituation?.currentZone?.speedLimitKn,
)
```

- `speedKnots` comes from GPS (`navigationState.speedKnots`) with demo fallback (`navigationState.demoSpeedKnots`)
- `activeSpeedLimitKn` is the speed limit of the zone the boat is currently inside (`zoneSituation.currentZone.speedLimitKn`)
- When boat is not inside any zone → `activeSpeedLimitKn` is `null` → Scenario 3 (blue, no border)

## Colour Key

| Name | Hex | Meaning |
|------|-----|---------|
| 🔵 `cardBg` | #16213E | Neutral — no zone or no violation |
| 🟢 `speedSafe` | #2E7D32 | Compliant — speed ≤ limit |
| 🟠 `speedCaution` | #EF6C00 | Warning — approaching violation |
| 🔴 `speedDanger` | #C62828 | Violation — exceeding limit by 40%+ |
