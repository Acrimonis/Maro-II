# Map Offset Phase 2 — Properties, Toggles, Settings UI

**Branch:** `feature/map-offset`  
**Status:** Planning — pending Ask review

## Goal

Replace hardcoded/internal property names with user-facing `maro.properties` entries,
add GPS/demo mode toggles to AppSettings with settings UI, and make the offset
percentage a user-facing slider (boat from bottom %).

## maro.properties entries

```properties
# ── Automatic map offset (dynamic speed-based center shift) ──

# Speed (knots) at which the look-ahead offset reaches its maximum.
# Default 20 kn. Range: 1–50.
map.offset.lookahead.maxspeedKn=20

# Boat position from screen bottom at full offset, as percentage of screen height.
# 50 = centered / disabled. 33 = ~1/3 from bottom (default). Lower = more forward view.
# Range: 5–50.
map.offset.lookahead.boatFromBottomPct=33

# Enable automatic map offset in GPS navigation mode. Default: true.
map.offset.gps=true
# Enable automatic map offset in demo/manual mode. Default: false.
map.offset.demo=false
```

## Files changed

### 1. `AppConfig.kt`

**Rename existing:**
- `mapLookAheadSpeedKn` → `mapOffsetLookaheadMaxSpeedKn`  
  Key: `map.offset.lookahead.maxspeedKn`, default `20.0`, range `1.0..50.0`
- `mapLookAheadMaxFraction` → removed (replaced by `mapOffsetLookaheadBoatFromBottomPct`)

**New:**
- `mapOffsetLookaheadBoatFromBottomPct: Int = 33`  
  Key: `map.offset.lookahead.boatFromBottomPct`, range `5..50`
- `mapOffsetGps: Boolean = true`  
  Key: `map.offset.gps`
- `mapOffsetDemo: Boolean = false`  
  Key: `map.offset.demo`

### 2. `SettingsManager.kt` / `AppSettings`

Add to `AppSettings` data class:
```kotlin
val mapOffsetGps: Boolean = true,
val mapOffsetDemo: Boolean = false,
val mapOffsetBoatFromBottomPct: Int = 33,
```

Persist to SharedPreferences with keys:
- `KEY_MAP_OFFSET_GPS`
- `KEY_MAP_OFFSET_DEMO`  
- `KEY_MAP_OFFSET_BOAT_FROM_BOTTOM_PCT`

Defaults seeded from `AppConfig` on first launch.

### 3. `MapScreen.kt`

**Offset computation change:**
```kotlin
// Convert boatFromBottomPct (5-50) to internal mapShift fraction (0.0-0.45)
val boatFromBottomPct = appSettings.mapOffsetBoatFromBottomPct
val maxMapShift = ((50 - boatFromBottomPct) / 100.0).coerceIn(0.0, 0.45)
val fullOffsetSpeedKn = AppConfig.mapOffsetLookaheadMaxSpeedKn

// Gate by mode toggle
val effectiveSpeedKn = when {
    appSettings.gpsMode && appSettings.mapOffsetGps -> navigationState.speedKnots
    !appSettings.gpsMode && appSettings.mapOffsetDemo -> navigationState.demoSpeedKnots
    else -> null  // offset disabled
}

val targetFraction = if (effectiveSpeedKn != null)
    ((effectiveSpeedKn / fullOffsetSpeedKn.toFloat()).coerceIn(0f, 1f))
else 0f

val mapCenterOffsetDp = (animatedFraction * maxHeight.value * maxMapShift.toFloat()).dp
```

**AppConfig references updated:** `mapLookAheadSpeedKn` → `mapOffsetLookaheadMaxSpeedKn`.

### 4. Settings UI (Navigation tab)

New section: **"Automatic map offset"** — placed after existing Navigation sections.

| Control | Type | AppSettings key | Default |
|---|---|---|---|
| GPS mode | Toggle | `mapOffsetGps` | ON |
| Demo mode | Toggle | `mapOffsetDemo` | OFF |
| Boat position from bottom | Slider (5–50%) | `mapOffsetBoatFromBottomPct` | 33% |

Slider description text: "Where the boat sits at speed. 50% = disabled (centered). Lower = more ahead."

### 5. `maro.properties` (assets)

Add the four entries as specified above, in a new section near the existing speed zone config.

## Conversion formula

```
internal mapShift = (50 - boatFromBottomPct) / 100.0
offsetPx = mapShift × speedFraction × screenHeight

where speedFraction = speedKn / maxspeedKn, clamped [0, 1]
```

At defaults (33%, 20 kn):
- 0 kn → 0 px offset → boat centered (50% from bottom)
- 20 kn → (50-33)/100 × 1.0 × H = 0.17H offset → boat at 33% from bottom

## Settings UI layout

```
┌─ Navigation ──────────────────────────┐
│ ...existing sections...                │
│                                        │
│ AUTOMATIC MAP OFFSET                   │
│ ┌──────────────────────────────────┐   │
│ │ GPS mode                    [ON] │   │
│ │ Demo mode                  [OFF] │   │
│ │ Boat position from bottom       │   │
│ │ [======●──────────] 33%         │   │
│ │ 50% = disabled. Lower = more    │   │
│ │ ahead.                           │   │
│ └──────────────────────────────────┘   │
└────────────────────────────────────────┘
```

## Verification

- [ ] Build passes
- [ ] `maro.properties` entries load correctly into AppConfig
- [ ] AppSettings toggles persist across app restart
- [ ] GPS mode toggle: OFF → no offset in GPS mode
- [ ] Demo mode toggle: ON → offset active in demo mode (with scroll suppression)
- [ ] Slider: 50% → boat centered at full speed (no offset)
- [ ] Slider: 33% → boat at ~1/3 from bottom at full speed
- [ ] Slider: 5% → boat at extreme forward position
- [ ] Renamed AppConfig fields work (no stale references to old names)
