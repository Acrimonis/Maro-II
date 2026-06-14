# Final Zone Tile Matrix — DistanceCard & SpeedLimitCard

## DistanceCard — 6 scenarios

| # | Scenario | Condition | Title | Value | Footer | Color |
|---|----------|-----------|-------|-------|--------|-------|
| 1a | Inside zone, near exit → open sea | `isNearExit` && `beyondType=OPEN_SEA` | DISTANCE | `47 m` | `to open sea - ETA 12 s` | 🟢 `zoneExit` |
| 1b | Inside zone, near exit → next zone | `isNearExit` && `beyondType=ZONE` | DISTANCE | `47 m` | `→ NextZoneName - ETA 12 s` | 🟠 `zoneEntry` |
| 1c | Inside zone, near exit → land | `isNearExit` && `beyondType=LAND` | DISTANCE | `47 m` | `to land - ETA 12 s` | 🔵 `cardBg` |
| 2 | Outside, zone entry imminent | `isNearEntry` | DISTANCE | `120 m` | `→ ZoneName - ETA 30 s` | 🟠 `zoneEntry` (if ↑) else 🔵 |
| 3 | Inside zone, beyond thresholds | `currentZone != null` && !`isNearExit` | DISTANCE | `1.2 km` | `from shore` | 🔵 `cardBg` |
| 4 | Default (no relevant zone) | else | DISTANCE | `1.2 km` | `from shore` | 🔵 `cardBg` |

### DistanceCard changes needed

1. **Priority 1 label**: Replace `stringResource(R.string.dash_to_zone)` with dynamic label derived from `currentZone.beyondType`:
   - `OPEN_SEA` → `"to open sea"`
   - `ZONE` → `"→ ${currentZone.beyondName}"` 
   - `LAND` → `"to land"`

2. **Priority 1/2 ETA extraction**: Already correct — uses `currentZone.etaSeconds` / `nearestZone.etaSeconds`.

3. **Card colors**: Already correct — uses beyond-type-based colors for Priority 1, arrow-check for Priority 2.

---

## SpeedLimitCard — 5 branches

| # | Scenario | Condition | Title | Value | Subtitle | Color |
|---|----------|-----------|-------|-------|----------|-------|
| A | Inside zone, near exit → open sea | `current != null` && `isNearExit` && `beyondType=OPEN_SEA` | Zone name | `10 kn` | `OPEN SEA · 47 m · ETA 12 s` | based on speed vs current limit |
| B | Inside zone, near exit → next zone | `current != null` && `isNearExit` && `beyondType=ZONE` | Zone name | `10 kn` | `→ NextZone · 120 m · ETA 30 s` | based on speed vs current limit |
| C | Inside zone, near exit → land | `current != null` && `isNearExit` && `beyondType=LAND` | Zone name | `10 kn` | `—` | based on speed vs current limit |
| D | Inside zone, far from exit | `current != null` && !`isNearExit` | Zone name | `10 kn` | `—` | based on speed vs current limit |
| E | Zones ahead, approaching | `ahead != null` && `shouldReveal` | Ahead zone | `↑250 m` | `AheadZoneName · ETA 45 s` | 3-tier ramp vs ahead limit |

### SpeedLimitCard changes needed

#### Inside-zone branch (A-D):
1. **Value**: Always show speed limit (`10 kn`) — **revert** the `beyondType`-as-value change.
2. **Subtitle for A**: Build from `current.beyondType.name.replace("_", " ") + " · " + distanceText(exitDist) + " · " + formatEta(etaSeconds)`
3. **Subtitle for B**: Build from `"→ " + (current.beyondName ?: "ZONE") + " · " + distanceText(exitDist) + " · " + formatEta(etaSeconds)`
4. **Subtitle for C/D**: empty string
5. **Color**: Remove `zoneExit` override. Use uniform compliance-based colors:
   - `compliant` → `zoneCompliant` (green `#1B5E20`)
   - `!compliant` → `zoneDanger` (red `#B71C1C`)

#### Heading-ahead branch (E):
6. **Subtitle**: Change from `"${ahead.speedLimitKn} kn - $etaStr"` to `"${ahead.zoneName} · $etaStr"`
7. **Color**: Use 3-tier speed ramp vs `ahead.speedLimitKn`:
   - `speedKnots == null` → `cardBg`
   - `speedKnots ≤ aheadLimit` → `speedSafe` (green `#2E7D32`)
   - `aheadLimit < speedKnots ≤ aheadLimit × 1.4` → `speedCaution` (orange `#EF6C00`)
   - `speedKnots > aheadLimit × 1.4` → `speedDanger` (red `#C62828`)

---

## Implementation steps

1. **DashboardPanel.kt** — revert SpeedLimitCard inside-zone value to always show speed limit
2. **DashboardPanel.kt** — update SpeedLimitCard inside-zone subtitle for A/B (beyond type + distance + ETA)
3. **DashboardPanel.kt** — remove `zoneExit` color override in inside-zone; always use compliance color
4. **DashboardPanel.kt** — update SpeedLimitCard heading-ahead subtitle to `zoneName · ETA`
5. **DashboardPanel.kt** — update SpeedLimitCard heading-ahead color to 3-tier ramp vs ahead limit
6. **DashboardPanel.kt** — update DistanceCard Priority 1 footer label to use beyond type
7. **Build & verify**
