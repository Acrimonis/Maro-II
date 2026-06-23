# Extend Coastline to Menton — Implementation Plan

## Summary

1. Extend eastern bound from 7.31°E (Frejus) → 7.55°E (Menton+buffer)
2. Consolidate region ID to single source of truth (`BuildConfig.REGION_ID`)
3. Move downloaded source data to `D:\.src\.data\` for cross-project sharing

---

## A. gradle.properties

```properties
maro.region.lonEast=7.55          # was 7.31
maro.region.id=nice-menton        # NEW — single source for region ID
```

## B. app/build.gradle.kts

Add after line 26 (after REGION_LON_EAST):

```kotlin
val regionId = project.findProperty("maro.region.id") as String? ?: "nice-frejus"
buildConfigField("String", "REGION_ID", "\"$regionId\"")
```

## C. Kotlin — Replace hardcoded "nice-frejus" with BuildConfig.REGION_ID

| File | Change |
|------|--------|
| `CoastlineGenerator.kt:63` | Delete `const val REGION_ID = "nice-frejus"`; use `BuildConfig.REGION_ID` in `generate()` default |
| `DepthConstants.kt:8` | Delete `const val REGION_ID = "nice-frejus"`; consumers use `BuildConfig.REGION_ID` |
| `RegulatedZonesRepository.kt:23` | Default `regionId` → `BuildConfig.REGION_ID` |
| `RegulatedZone.kt:196` | `RegulationMetadata.regionId` default → `BuildConfig.REGION_ID` |

KDoc / comments: "Nice–Fréjus" → "Nice–Menton" where appropriate.

## D. Bake Scripts — Region ID

| Script | Change |
|--------|--------|
| `tools/bake-env.bat:7` | `set "REGION=nice-menton"` |
| `tools/bake-depth.bat:10` | `set "REGION=nice-menton"` |
| `apk-bake.bat:18-22` | Status paths: `nice-frejus` → `nice-menton` |

## E. Data Storage — Move Downloads to D:\.src\.data\

| Script | Change |
|--------|--------|
| `tools/bake-emodnet.bat:13` | `WORK=%TEMP%\emodnet_e5` → `WORK=D:\.src\.data\emodnet` |
| `tools/bake-litto3d.bat:13` | `TILES=tools\litto3d_tiles` → `TILES=D:\.src\.data\litto3d` |
| `tools/bake-litto3d.bat:17` | `-TilesDir "%~dp0litto3d_tiles"` → `-TilesDir "D:\.src\.data\litto3d"` |
| `.gitignore:44` | Remove `tools/litto3d_tiles/` (no longer in repo) |

Baked outputs (`data/app-assets/`) stay as-is — they're small and must be in-project for APK packaging.

## F. Test Files

Update hardcoded `"nice-frejus"` strings in ~8 test files. Use `BuildConfig.REGION_ID` where accessible; use the literal `"nice-menton"` where BuildConfig isn't available in test scope.

## G. Re-deploy Procedure

```
apk-bake.bat all        ← regenerates all assets with new bounds + region ID
apk-deploy.bat          ← builds APK + pushes to device
```

Or minimal (no prebaking):
```
apk-deploy.bat          ← build + push
→ On device: tap "Côte" then "Bande"
```
