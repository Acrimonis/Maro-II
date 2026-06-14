# Round 1 — Multi-Source Normalization: Complete

## What was implemented

### Data Model — `RegulatedZone.kt`
- `RegulationClassification` sealed class (S101, Catrea, Restrn, InpnMpa, Seed)
- `SpeedSource` enum (STRUCTURED_FIELD, INFORM_TEXT, TXTDSC_MAP, DEFAULT_RULE, NONE)
- 3 new `@ProtoNumber` fields: `classification(11)`, `speedSource(12)`, `legalDecreeRef(13)`
- 3 new `ZoneDisplayCategory` values: `FISHING_PROHIBITED`, `ENVIRONMENTAL`, `INFORMATION`
- Extended `displayCategories()` with posidonie/herbier keyword scanning, restrictionCode==10 diving check, fishing prohibition detection, fallback ENVIRONMENTAL/INFORMATION for unactionable zones

### SHOM Client — `ShomRegulationClient.kt`
- CATREA/RESTRN parsing (`parseCatrea()`, `parseRestrnAuth()`)
- Enhanced speed extraction (`parseSpeedFromInform()` with nœuds/noeud/nds/kts)
- `TXTDSC_SPEED_MAP` lookup table
- REGNAV layer documented (auth-only on public endpoint)

### IGN Nature Client — `IgnCartoNatureClient.kt` (NEW)
- IGN API Carto Nature client — queries `natura-habitat` + `natura-oiseaux` endpoints
- Returns GeoJSON FeatureCollection with URL-encoded polygon bbox
- ✅ Working: 16 zones fetched (13 habitat + 3 birds), 12 survived dedup

### INPN Client — `InpnRegulationClient.kt` (NEW)
- INPN WFS client for `wfs_inpn:amp_polygones` + `wfs_inpn:natura2000_sic`
- Code is correct with validated params (`typeName`, `BBOX` uppercase, EPSG:4326 axis order)
- BunkerWeb WAF blocks this machine — graceful degradation to 0 zones

### Aggregator — `RegulationAggregator.kt`
- 3-way authority dedup: SHOM > IGN/INPN > SEED
- 50m Haversine centroid threshold
- Unknown-source handling

### Icon Provider — `RegulatedZoneIconProvider.kt`
- Emoji/colour/alpha/strike for all 9 display categories
- Uniform dark blue background (except SPEED_LIMIT=red, SEAPLANE=grey)

### UI Layout — `MapScreen.kt`
- Warning strip: horizontal scroll Row → vertical Column, priority-ordered
- Info text: one-line ellipsis → auto-wrapping, uses category emoji, same category dedup as icons
- Both composables share `CATEGORY_PRIORITY` ordering

### Prebake
- Wired IGN Nature client + expanded bbox (6.7,43.4,7.6,43.8)
- Result: **124 zones** (109 SHOM + 12 IGN + 3 SEED)
- 85,684 bytes `.bin` regenerated

### Build & Deploy
- APK built and deployed to device ✅
- Committed as `3b055de` — 10 files, +1043/-188 lines

---

# Round 2 — Boat Size & Category Toggle Filtering (Plan)

## What to implement

### 1. Settings Data — `SettingsManager.kt` (+10 fields)

| Field | Type | Default | Persistence Key |
|-------|------|---------|-----------------|
| `boatSizeM` | `Double` | `BuildConfig.REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M` (6.0) | `boatSizeM` |
| `showCategoryNoAnchor` | `Boolean` | `true` | `showCategoryNoAnchor` |
| `showCategoryMooring` | `Boolean` | `true` | `showCategoryMooring` |
| `showCategorySpeedLimit` | `Boolean` | `true` | `showCategorySpeedLimit` |
| `showCategoryNoDiving` | `Boolean` | `true` | `showCategoryNoDiving` |
| `showCategorySeaplane` | `Boolean` | `true` | `showCategorySeaplane` |
| `showCategoryNoAccess` | `Boolean` | `true` | `showCategoryNoAccess` |
| `showCategoryFishingProhibited` | `Boolean` | `true` | `showCategoryFishingProhibited` |
| `showCategoryEnvironmental` | `Boolean` | `true` | `showCategoryEnvironmental` |
| `showCategoryInformation` | `Boolean` | `true` | `showCategoryInformation` |

Helper: `fun AppSettings.isCategoryVisible(cat: ZoneDisplayCategory): Boolean`

### 2. Filter Pipeline — `MapScreen.kt`

New `filterRegulatedZones()` function:
```kotlin
fun filterRegulatedZones(zones, boatSizeM, isCategoryVisible): RegulatedZoneSet? {
    // 1. Boat size: zone.appliesTo(boatSizeM) → removes entire zone
    // 2. Category visibility: zone must have ≥1 visible category
    // 3. If no zones remain → return null (layer auto-hides)
}
```

Replace the simple `regulatedZonesVisible` gate with:
```kotlin
val visibleRegulatedZones = if (appSettings.regulatedZonesVisible) {
    filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { appSettings.isCategoryVisible(it) }
} else null
```

### 3. Settings UI — `SettingsScreen.kt`

New section in Display → Layers:

```
╔═════════════════════════════════════════════╗
║  🚤 Boat length             6m             ║
║     3 ─────────●────────── 25              ║
║                                           ║
║  🔴10  Speed limit                  [✓]   ║  ← red box with "10"
║  🚤    No access                    [✓]   ║
║  ⚓    No anchor                     [✓]   ║
║  🤿    No diving                     [✓]   ║
║  🐟    Fishing prohibited            [✓]   ║
║  🚤    Mooring                       [✓]   ║
║  ✈️    Seaplane                      [✓]   ║
║  🌿    Environmental                 [✓]   ║
║  ℹ️    Information                   [✓]   ║
╚═════════════════════════════════════════════╝
```

### No re-bake needed
All filtering is runtime — operates on the existing 124-zone prebaked dataset.

### Files changed
| File | Changes |
|------|---------|
| `SettingsManager.kt` | +10 fields, +1 helper, +10 persistence keys |
| `MapScreen.kt` | +filterRegulatedZones(), wire into visibility gate |
| `SettingsScreen.kt` | +boat size slider +9 category toggles in Display→Layers |
