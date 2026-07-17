<!-- scope: feature -->
# Final Formatting Fixes

The logic is correct per the matrix. Only the display text strings need updating.

## Changes

### 1. DistanceCard Priority 1 footer labels

In [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt) `isNearExit` branch:

| BeyondType | Current | Change to |
|-----------|---------|-----------|
| `OPEN_SEA` | `"to open sea"` | `"open water"` |
| `LAND` | `"to land"` | `"land"` |
| `ZONE` | `"\u2192 ${beyondName}"` | no change — arrow + name is correct |

### 2. SpeedLimitCard inside-zone subtitle separators

In `currentZone != null` `isNearExit` branch, change separator from ` · ` to ` - `:

| Current | Change to |
|---------|-----------|
| `OPEN SEA · 105 m · ETA 12 s` | `open water - 105 m - ETA 12 s` |
| `\u2192 NextZone · 120 m · ETA 30 s` | `\u2192 NextZone - 120 m - ETA 30 s` |

Also lowercase the beyond type name: `"OPEN SEA"` → `"open water"`.

### 3. Heading-ahead (E) subtitle separator

Same change: `"BANDE 300M · ETA 45 s"` → `"BANDE 300M - ETA 45 s"`

### Summary of display string mapping

| Enum | Display text |
|------|-------------|
| `OPEN_SEA` | `open water` |
| `LAND` | `land` |
| `ZONE` | use `beyondName` directly |

