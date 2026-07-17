# Coastline — Session Hydration (2026-06-23 07:08)

## Active Session
- **Subfeature:** extend-to-menton
- **Branch:** feature/extend-coastline
- **Plan:** xTrack/Coastline/260623_FEAT_PLN_Coastline_extend-coastline-to-menton.md

## Completed
- Eastern bound: 7.31°E → 7.55°E (Nice–Menton) in gradle.properties
- Region ID: "nice-frejus" → "nice-menton", single source via BuildConfig.REGION_ID
- All Kotlin data repos consume BuildConfig.REGION_ID (CoastlineGenerator, DepthRepository, RegulatedZonesRepository, RegulatedZone)
- Data storage: Litto3D tiles + EMODnet cache moved to D:\.src\.data\
- apk-deploy.bat syncs D:\.src\.data\ → data/app-assets\ before build
- OOM fix: DepthSerializer uses InputStream (no double memory)
- Batch files: LF→CRLF fix, %% escaping, if/else dispatch in apk-bake.bat
- Test files: ~8 updated to "nice-menton" region ID
- AdaptiveGpsPolicyTest: removed stale speed parameter
- UI: "Côte" / "Bande" button labels on map
- .gitignore: removed tools/litto3d_tiles/ exclusion
- Commit: 635ca5a — 29 files, +148/−64

## Baked Assets (D:\.src\.data\)
- coastlines/nice-menton.bin + .bbox ✅
- depth/emodnet-nice-menton.asc ✅
- depth/litto3d-nice-menton.asc.gz ✅
- depth/nice-menton.bin ✅
- regulated-zones/nice-menton.bin ✅

## Pending
- Hazard donut rendering validation on device (Zone300 winding check)
- Shom Aton WFS GetCapabilities (live hazard fetch blocked on endpoint confirmation)

## Key Files
- gradle.properties — maro.region.lonEast=7.55, maro.region.id=nice-menton
- app/build.gradle.kts — BuildConfig.REGION_ID
- app/src/main/java/ykws/android/maro/data/depth/DepthSerializer.kt — InputStream deserialize
- app/src/main/java/ykws/android/maro/data/depth/DepthRepository.kt — BuildConfig.REGION_ID default
- app/src/main/java/ykws/android/maro/data/coastline/CoastlineGenerator.kt — BuildConfig.REGION_ID default
- app/src/main/java/ykws/android/maro/data/regulation/RegulatedZonesRepository.kt — BuildConfig.REGION_ID default
- tools/bake-*.bat — D:\.src\.data\ output
- apk-deploy.bat — sync D:\.src\.data\ → data\app-assets\
- apk-bake.bat — if/else dispatch with if defined FRESH guards
