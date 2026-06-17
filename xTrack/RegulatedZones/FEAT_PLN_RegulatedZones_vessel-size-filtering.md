<!-- scope: feature -->
# Boat Size & Per-Category Toggle Filtering

## Requirements

1. **Boat size** slider (3–25m, default 6m) in Display → Layers settings
2. **9 per-category toggles** — each `ZoneDisplayCategory` gets a toggle. Toggle off = icon hidden from strip, zone polygon hidden from map.
3. **Auto-hide zone** — if all categories of a zone are toggled off AND the zone doesn't match boat size, it's hidden from both map overlay and strip.
4. **Auto-hide layer** — if no zones remain visible, the regulated zones layer hides itself.

## Screenshot of Category Emoji (from RegulatedZoneIconProvider)

| Category | Emoji | Label |
|----------|-------|-------|
| SPEED_LIMIT | `#` | Speed limit |
| NO_ACCESS | 🚤 | No access |
| NO_ANCHOR | ⚓ | No anchor |
| NO_DIVING | 🤿 | No diving |
| FISHING_PROHIBITED | 🐟 | Fishing prohibited |
| MOORING | 🚤 | Mooring |
| SEAPLANE | ✈️ | Seaplane |
| ENVIRONMENTAL | 🌿 | Environmental |
| INFORMATION | ℹ️ | Information |

Note: SPEED_LIMIT renders the knot number (e.g. "5", "10") on screen — settings toggle label uses "#" as placeholder.

## Data Model Changes

### AppSettings (SettingsManager.kt) — 10 new fields

```kotlin
data class AppSettings(
    // ... existing fields ...
    val boatSizeM: Double = BuildConfig.REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M, // 6.0
    val showCategoryNoAnchor: Boolean = true,
    val showCategoryMooring: Boolean = true,
    val showCategorySpeedLimit: Boolean = true,
    val showCategoryNoDiving: Boolean = true,
    val showCategorySeaplane: Boolean = true,
    val showCategoryNoAccess: Boolean = true,
    val showCategoryFishingProhibited: Boolean = true,
    val showCategoryEnvironmental: Boolean = true,
    val showCategoryInformation: Boolean = true,
)
```

### Helper function

```kotlin
fun AppSettings.isCategoryVisible(cat: ZoneDisplayCategory): Boolean = when (cat) {
    ZoneDisplayCategory.NO_ANCHOR -> showCategoryNoAnchor
    ZoneDisplayCategory.MOORING -> showCategoryMooring
    ZoneDisplayCategory.SPEED_LIMIT -> showCategorySpeedLimit
    ZoneDisplayCategory.NO_DIVING -> showCategoryNoDiving
    ZoneDisplayCategory.SEAPLANE -> showCategorySeaplane
    ZoneDisplayCategory.NO_ACCESS -> showCategoryNoAccess
    ZoneDisplayCategory.FISHING_PROHIBITED -> showCategoryFishingProhibited
    ZoneDisplayCategory.ENVIRONMENTAL -> showCategoryEnvironmental
    ZoneDisplayCategory.INFORMATION -> showCategoryInformation
}
```

## Filter Pipeline (MapScreen.kt)

New `filterRegulatedZones()` helper, called once before both overlay and strip rendering:

```kotlin
fun filterRegulatedZones(
    zones: RegulatedZoneSet?,
    boatSizeM: Double,
    isCategoryVisible: (ZoneDisplayCategory) -> Boolean
): RegulatedZoneSet? {
    if (zones == null) return null
    val filtered = zones.zones.filter { zone ->
        if (!zone.appliesTo(boatSizeM)) return@filter false
        val cats = zone.displayCategories()
        if (cats.isEmpty()) return@filter false
        cats.any { isCategoryVisible(it) }
    }
    if (filtered.isEmpty()) return null
    return zones.copy(zones = filtered)
}
```

```kotlin
// MapScreen call site — replaces simple visibility gate
val visibleRegulatedZones = if (appSettings.regulatedZonesVisible) {
    filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { cat ->
        appSettings.isCategoryVisible(cat)
    }
} else null
```

If `visibleRegulatedZones` is null → layer is hidden from both map and strip.

## Settings UI (SettingsScreen.kt)

New section in Display → Layers, below the existing regulated zones toggle:

```
┌─────────────────────────────────────────────┐
│  🚤 Regulated zones                  [✓]    │  ← existing
│                                              │
│  ── Boat & category filters ──               │
│  🚤 Boat length        6m                    │
│     [3]────────●──────────[25]               │
│                                              │
│  #  Speed limit                       [✓]    │
│  🚤 No access                         [✓]    │
│  ⚓ No anchor                          [✓]    │
│  🤿 No diving                          [✓]    │
│  🐟 Fishing prohibited                 [✓]    │
│  🚤 Mooring                            [✓]    │
│  ✈️ Seaplane                           [✓]    │
│  🌿 Environmental                      [✓]    │
│  ℹ️ Information                        [✓]    │
└─────────────────────────────────────────────┘
```

## Files Changed

| File | Change |
|------|--------|
| `SettingsManager.kt` | +10 AppSettings fields, +1 helper, +10 persistence keys |
| `MapScreen.kt` | `filterRegulatedZones()` helper; replace simple gate with filtered gate |
| `SettingsScreen.kt` | Boat size slider + 9 category toggles in Display → Layers |

