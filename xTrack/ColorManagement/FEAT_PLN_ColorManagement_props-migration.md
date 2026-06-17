<!-- scope: feature -->
# Migration Plan: All Colors → `colors.properties`

> Consolidate every colour token currently scattered across Kotlin source files
> into a single `colors.properties` file loaded by `ZoneConfig.init()`.
> Hardcoded defaults remain as fallbacks in `ZoneConfig`.

---

## Phase 0 — Create the properties file

**File:** [`app/src/main/assets/colors.properties`](../app/src/main/assets/colors.properties) (new)

Contains all ~49 properties following the taxonomy:

```properties
# ── Dashboard tiles ────────────────────────────────────
ui.dashboard.background       = #1A1A2E
ui.dashboard.card.background  = #16213E
ui.dashboard.text.primary     = #E0E0E0
ui.dashboard.text.muted       = #90A4AE
ui.dashboard.status.green     = #4CAF50
ui.dashboard.status.yellow    = #FFEB3B
ui.dashboard.status.red       = #F44336
ui.dashboard.zone.safe        = #2E7D32
ui.dashboard.zone.caution     = #EF6C00
ui.dashboard.zone.danger      = #C62828
ui.dashboard.zone.compliant   = #1B5E20
ui.dashboard.zone.normal      = #37474F
ui.dashboard.zone.dangerDark  = #B71C1C
ui.dashboard.distance.entry   = #E65100
ui.dashboard.distance.exit    = #2E7D32
ui.dashboard.dullAlpha        = 0.33

# ── Action buttons ─────────────────────────────────────
ui.button.background          = #CCFFFFFF
ui.button.icon                = #1565C0
ui.button.iconActiveAlpha     = 1.0
ui.button.iconInactiveAlpha   = 0.25

# ── Settings overlay ───────────────────────────────────
ui.settings.background        = #1A1A2E
ui.settings.toast.background  = #16213E
ui.settings.toast.text        = #FFFFFF

# ── Coastline ──────────────────────────────────────────
map.coastline.mainland.color  = #1545C0
map.coastline.mainland.width  = 10
map.coastline.island.color    = #08805C
map.coastline.island.width    = 10
map.coastline.hazard.style    = disc+ring+cross

# ── Navigation aids ────────────────────────────────────
map.navigation.arrow.color    = #1565C0
map.navigation.line.color     = #4D1565C0

# ── Isobath lines ──────────────────────────────────────
map.isobath.litto3d.color     = #1B5E20
map.isobath.litto3d.width     = 1
map.isobath.emodnet.color     = #00008B
map.isobath.emodnet.width     = -1
map.isobath.default.color     = #37474F

# ── Depth colour map ───────────────────────────────────
map.depth.nodata.color        = #CCCCCC

# ── Low-depth warning overlay ──────────────────────────
overlay.lowDepth.color        = #FF00E5
overlay.lowDepth.minOpacity   = 25

# ── GPS status icon states ─────────────────────────────
status.gps.demo               = #FFFFFF
status.gps.acquiring          = #FFA726
status.gps.healthy            = #2E7D32
status.gps.idle               = #1565C0
status.gps.stale              = #F44336
status.gps.alpha.active       = 0.75
status.gps.alpha.dimmed       = 0.50

# ── Earth/Water icon ───────────────────────────────────
status.earthWater.water       = #1565C0
status.earthWater.land        = #2E7D32
status.earthWater.inactive    = #EEFFFFFF
```

---

## Phase 1 — Load `colors.properties` in `ZoneConfig.init()`

**File:** [`app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt`](../app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt)

### 1a. Add load block (after existing maro.properties loading)

Insert a new section in `ZoneConfig.init()` that loads `colors.properties`:

```kotlin
// Load colors.properties (optional — defaults apply if absent).
try {
    context.assets.open("colors.properties").use { stream ->
        props.load(stream)
    }
} catch (_: Exception) {
    // colors.properties is optional; defaults apply if absent.
}
```

### 1b. Add all ~49 fields to `ZoneConfig`

Each with its current hardcoded default as fallback. Pattern:

```kotlin
/** Dashboard background. Default #1A1A2E. Set via `ui.dashboard.background` in colors.properties. */
var uiDashboardBackground: Int = 0xFF1A1A2E.toInt()
    private set
```

Fields grouped by taxonomy section:

| Section | Fields | Type | Count |
|---|---|---|---|
| `ui.dashboard.*` | background, cardBackground, textPrimary, textMuted, statusGreen, statusYellow, statusRed, zoneSafe, zoneCaution, zoneDanger, zoneCompliant, zoneNormal, zoneDangerDark, distanceEntry, distanceExit | `Int` (ARGB) | 15 |
| `ui.dashboard.*` | dullAlpha | `Float` | 1 |
| `ui.button.*` | background, icon | `Int` (ARGB) | 2 |
| `ui.button.*` | iconActiveAlpha, iconInactiveAlpha | `Float` | 2 |
| `ui.settings.*` | background, toastBackground, toastText | `Int` (ARGB) | 3 |
| `map.coastline.*` | mainlandColor(=Int), mainlandWidth(=Int), islandColor(=Int), islandWidth(=Int) | mixed | 4 |
| `map.navigation.*` | arrowColor(=Int), lineColor(=Int) | `Int` (ARGB) | 2 |
| `map.isobath.*` | litto3dColor(=Int), litto3dWidth(=Float), emodnetColor(=Int), emodnetWidth(=Float), defaultColor(=Int) | mixed | 5 |
| `map.depth.*` | nodataColor(=Int) | `Int` (ARGB) | 1 |
| `overlay.lowDepth.*` | color(=Int), minOpacity(=Int) | mixed | 2 |
| `status.gps.*` | demo(=Int), acquiring(=Int), healthy(=Int), idle(=Int), stale(=Int) | `Int` (ARGB) | 5 |
| `status.gps.*` | alphaActive(=Float), alphaDimmed(=Float) | `Float` | 2 |
| `status.earthWater.*` | water(=Int), land(=Int), inactive(=Int) | `Int` (ARGB) | 3 |
| **Total** | | | **~49** |

### 1c. Add load lines in `init()`

One `props.getProperty(...)` call per field, using `parseColorOrNull` for colours, `.toFloatOrNull()` for floats, `.toIntOrNull()` for ints:

```kotlin
props.getProperty("ui.dashboard.background")?.let { parseColorOrNull(it) }?.let {
    uiDashboardBackground = it
}
props.getProperty("ui.dashboard.dullAlpha")?.toFloatOrNull()?.let {
    uiDashboardDullAlpha = it.coerceIn(0f, 1f)
}
// ... repeat for all 49
```

---

## Phase 2 — Migrate callers (one source file at a time)

### 2a. [`DashboardPanel.kt`](../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt)

Replace the hardcoded `DashboardColors` object. The simplest approach: make `DashboardColors` a runtime bridge.

**Option A — direct:** Replace `DashboardColors` references inline with `ComposeColor(ZoneConfig.uiDashboardCardBackground)` etc.

**Option B — bridge object:** Keep `DashboardColors` but make it a runtime getter:

```kotlin
private object DashboardColors {
    val background get() = Color(ZoneConfig.uiDashboardBackground)
    val cardBg get() = Color(ZoneConfig.uiDashboardCardBackground)
    val textPrimary get() = Color(ZoneConfig.uiDashboardTextPrimary)
    // ... etc
}
```

**Recommendation: Option B.** Least diff, no call-site changes, single point of truth.

### 2b. [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

Replace hardcoded colour literals in:

| Location | Current | New reference |
|---|---|---|
| `EarthWaterIcon` activeColor water | `ComposeColor(0xFF1565C0)` | `ComposeColor(ZoneConfig.statusEarthWaterWater)` |
| `EarthWaterIcon` activeColor land | `ComposeColor(0xFF2E7D32)` | `ComposeColor(ZoneConfig.statusEarthWaterLand)` |
| `GpsStatusIcon` DEMO | `ComposeColor.White` | `ComposeColor(ZoneConfig.statusGpsDemo)` |
| `GpsStatusIcon` ACQUIRING | `ComposeColor(0xFFFFA726)` | `ComposeColor(ZoneConfig.statusGpsAcquiring)` |
| `GpsStatusIcon` HEALTHY | `ComposeColor(0xFF2E7D32)` | `ComposeColor(ZoneConfig.statusGpsHealthy)` |
| `GpsStatusIcon` IDLE | `ComposeColor(0xFF1565C0)` | `ComposeColor(ZoneConfig.statusGpsIdle)` |
| `GpsStatusIcon` STALE | `ComposeColor(0xFFF44336)` | `ComposeColor(ZoneConfig.statusGpsStale)` |
| GPS alpha active | `ZoneConfig.gpsIconBgAlpha / 255f` | `ZoneConfig.statusGpsAlphaActive` |
| GPS alpha dimmed | `ZoneConfig.gpsIconDimBgAlpha / 255f` | `ZoneConfig.statusGpsAlphaDimmed` |
| EarthWater inactive | `ComposeColor(0xEEFFFFFF)` | `ComposeColor(ZoneConfig.statusEarthWaterInactive)` |
| Settings bg | `ComposeColor(0xFF1A1A2E)` | `ComposeColor(ZoneConfig.uiSettingsBackground)` |
| Exit-toast Surface | `ComposeColor(0xFF16213E)` | `ComposeColor(ZoneConfig.uiSettingsToastBackground)` |
| Exit-toast text | `ComposeColor.White` | `ComposeColor(ZoneConfig.uiSettingsToastText)` |
| Coastline mainland | hardcoded in drawCoastline() | `ZoneConfig.mapCoastlineMainlandColor` |
| Coastline islands | hardcoded in drawCoastline() | `ZoneConfig.mapCoastlineIslandColor` |

### 2c. [`FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt)

`ButtonColors` already delegates to `ZoneConfig`. If ZoneConfig fields are renamed to match the new taxonomy, update the references:

```kotlin
object ButtonColors {
    val bg get() = ComposeColor(ZoneConfig.uiButtonBackground)
    val icon get() = ComposeColor(ZoneConfig.uiButtonIcon)
    val activeAlpha get() = ZoneConfig.uiButtonIconActiveAlpha
    val inactiveAlpha get() = ZoneConfig.uiButtonIconInactiveAlpha
}
```

### 2d. [`ZoneConfig.kt`](../app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt) — self-cleanup

Existing fields like `capArrowColor` and `directionLineColor` duplicate the new `map.navigation.*` fields in Phase 1b. Remove the old fields and update internal references:

| Old field | New field |
|---|---|
| `capArrowColor` | → `mapNavigationArrowColor` |
| `directionLineColor` | → `mapNavigationLineColor` |
| `lowDepthWarningColor` | → `overlayLowDepthColor` |
| `lowDepthWarningMinOpacityPct` | → `overlayLowDepthMinOpacity` |
| `nodataColor` | → `mapDepthNodataColor` |
| `iconBackActiveAlpha` | → `statusGpsAlphaActive` (as 0-1 float) |
| `iconBackInactiveAlpha` | → `statusGpsAlphaDimmed` (as 0-1 float) |
| `waterIconBgAlpha` (delegated) | → remove, callers use `statusGpsAlphaActive` directly |
| `gpsIconBgAlpha` (delegated) | → remove, callers use `statusGpsAlphaActive` directly |
| `gpsIconDimBgAlpha` (delegated) | → remove, callers use `statusGpsAlphaDimmed` directly |
| `buttonActionBgColor` | → `uiButtonBackground` |
| `buttonActionIconColor` | → `uiButtonIcon` |
| `buttonActionIconActiveAlpha` | → `uiButtonIconActiveAlpha` |
| `buttonActionIconInactiveAlpha` | → `uiButtonIconInactiveAlpha` |

Remove the old `maro.properties` load lines for these once migrated.

### 2e. [`FanLayout.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt)

The badge already uses `ButtonColors.icon` — no change needed.

### 2f. [`LowDepthWarningBitmap.kt`](../app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt)

Update references from `ZoneConfig.lowDepthWarningColor` / `ZoneConfig.lowDepthWarningMinOpacityPct` to the new field names.

### 2g. [`DepthViewModel.kt`](../app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt)

Update `ZoneConfig.nodataColor` → `ZoneConfig.mapDepthNodataColor`.

---

## Phase 3 — Cleanup and deduplication

### 3a. Remove old `maro.properties` entries

The following properties in `maro.properties` are superseded by `colors.properties`:

```properties
icon.back.active.transparency=75        # → status.gps.alpha.active
icon.back.inactive.transparency=50      # → status.gps.alpha.dimmed
button.action.background.color=...      # → ui.button.background
button.action.icon.color=...            # → ui.button.icon
button.action.icon.active.alpha=...     # → ui.button.iconActiveAlpha
button.action.icon.inactive.alpha=...   # → ui.button.iconInactiveAlpha
```

### 3b. Deprecation note

Leave `maro.properties` as-is for existing `cap.arrow.color` and `direction.line.color` until Phase 2d removes `ZoneConfig`'s old fields. After that, move them to `colors.properties` and remove from `maro.properties`.

---

## Phase 4 — Build and verify

1. Build: `gradlew assembleDebug`
2. Fix any compilation errors from renamed fields
3. Verify at runtime that all colours render correctly

---

## Summary of files changed

| File | Change type | Complexity |
|---|---|---|
| `app/src/main/assets/colors.properties` | **Create** | ~50 lines |
| `ZoneConfig.kt` | +49 fields, +49 load lines, rename ~12 existing fields | **Large** |
| `DashboardPanel.kt` | Change `DashboardColors` to runtime getters | Small |
| `MapScreen.kt` | Replace ~14 hardcoded literals with ZoneConfig refs | Medium |
| `FanIconComponents.kt` | Update `ButtonColors` field names if changed | Tiny |
| `FanLayout.kt` | No change (already uses ButtonColors) | None |
| `LowDepthWarningBitmap.kt` | Update field references | Tiny |
| `DepthViewModel.kt` | Update field reference | Tiny |
| `maro.properties` | Remove superseded entries | Small |

**Total:** ~8 files, ~250 lines added/changed.
