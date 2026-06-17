<!-- scope: feature -->
# Zone Tile — Exit Preview Threshold

## UX requirements

**Outside zone:** show what's around and what's coming up (already handled).

**Inside zone:** behavior depends on exit distance:

| Exit distance | Beyond | Tile display |
|---|---|---|
| > distanceOutOfZoneInfoM | anything | Speed limit only. No exit countdown. |
| ≤ distanceOutOfZoneInfoM | LAND | Limit + "Xm · LAND" (hazard warning) |
| ≤ distanceOutOfZoneInfoM | OPEN WATER | Limit + "Xm · ETAs" (exit countdown) |
| ≤ distanceOutOfZoneInfoM | ZONE | Limit + "Xm · ETAs · NextZone limitKn" (current + next) |

## New config property

```properties
speedZone.distanceOutOfZoneInfoM=200
```

## Tile pseudocode

```kotlin
if (currentZone == null) {
    renderHeadingAhead(headingAhead)
} else if (currentZone.distanceM > distanceOutOfZoneInfoM) {
    renderLimitOnly(currentZone.speedLimitKn, currentZone.isCompliant)
} else {
    renderExitPreview(currentZone)
}
```

