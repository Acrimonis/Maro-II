# Coastline — Session Hydration (2026-06-22 20:52)

## Active Session
- **Subfeature:** extend-to-menton
- **Branch:** feature/extend-coastline
- **Plan:** plans/extend-coastline-to-menton.md

## Completed
- Coastline eastern bound extended: 6.70°E – 7.55°E (Nice–Menton)
- Region ID consolidated: "nice-frejus" → "nice-menton", single source via BuildConfig.REGION_ID
- OOM crash fixed: DepthSerializer.deserialize() now accepts InputStream, avoids double memory
- AdaptiveGpsPolicyTest fixed: removed stale speed parameter from onFix() calls
- Settings "Regenerate" button: now closes settings sheet before raster rebuild
- Unified data architecture: all data lives in D:\.src\.data\, apk-deploy.bat syncs to data/app-assets/
- Litto3D tiles migrated: tools/litto3d_tiles/ → D:\.src\.data\litto3d/
- EMODnet cache: D:\.src\.data\emodnet\
- Bake scripts simplified: no robocopy, no delayed expansion, pure copy commands
- .gitignore: removed tools/litto3d_tiles/
- Batch file corruption fixed: LF→CRLF line endings across all 17 .bat files (root cause of 'M'/'tlocal'/'ll' errors)
- bake-litto3d.bat: %%→% everywhere, %D:\.tmp%→D:\.tmp path fix
- _bake_litto3d_temp.bat: %%→% everywhere
- apk-bake.bat: if...else dispatch, if defined FRESH guards (no literal %FRESH% passthrough)
- bake-depth.bat: set "FRESH="→set FRESH= (undefine vs define-as-empty)

## Baked Assets (D:\.src\.data\)
- coastlines/nice-menton.bin + .bbox ✅
- depth/emodnet-nice-menton.asc ✅
- depth/litto3d-nice-menton.asc.gz ✅
- depth/nice-menton.bin ✅
- regulated-zones/nice-menton.bin ✅

## Pending
- ~~User to run: apk-bake.bat all → apk-deploy.bat~~ ✅ batch errors fixed, Gradle processDebugResources file-locking is pre-existing
- Verify Litto3D shallow layer visible on device

## Key Files
- gradle.properties — maro.region.lonEast=7.55, maro.region.id=nice-menton
- app/build.gradle.kts — BuildConfig.REGION_ID
- app/src/main/java/ykws/android/maro/data/depth/DepthSerializer.kt — InputStream deserialize
- app/src/main/java/ykws/android/maro/data/depth/DepthRepository.kt — stream-based readBundled
- tools/bake-*.bat — all output to D:\.src\.data\
- apk-deploy.bat — sync D:\.src\.data\ → data\app-assets\ before build
- apk-bake.bat — if...else dispatch with if defined FRESH guards
