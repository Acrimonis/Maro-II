# Hydration — BakeNormalization

**Last Bake:** 2026-06-08 14:31

## State (Phases 1–3 done, verified on-device)
Rationalized the asset pipeline. **Region single source** = gradle props `maro.region.lonWest/lonEast`
→ `BuildConfig.REGION_LON_WEST/EAST`; `CoastlineGenerator` reads them. **Depth envelope is derived**
(`DepthZoneMask.envelopeOf` = coastline bbox + 6 NM); `DepthConstants.WATER_BBOX` removed,
`DepthGenerator.generate(bbox=…)` required; `DepthZoneMask` clips to the 6 NM-of-coast buffer.
**No baked data in git** — everything in gitignored `data/app-assets/**`; `.asc`/`.prj` excluded from
the APK via `androidResources.ignoreAssetsPatterns`. **Pipeline split**: `tools/bake-env|coastline|
emodnet|litto3d|depth|zone300.bat` + `apk-bake.bat` (selector) + `apk-build.bat` (build-only) +
`apk-deploy.bat`. Old `bake_*.bat` + `CoastlinePrebakeTest` deleted. **Fixes**: streaming
`AsciiGridParser.parse(File)` (1 GB litto3d `.asc` OOM'd `readText()`); `.gitattributes` forces CRLF
on `.bat` (Write tool emits LF → cmd desync). On-device: southern band filled once the **stale
EMODnet `.asc`** (pre-bake-env, old box) was re-clipped.

## Next
- Open: tighten litto3d clip (972 MB nodata bloat → 127 M-cell merge).
- MapDisplay/`layer-zone` front-matter still describes the superseded hardcoded box — revisit.
- Likely PR `feature/depth-sone` → develop.

## Key files
`tools/bake-*.bat`, `apk-bake|build|deploy.bat`, `gradle.properties`, `app/build.gradle.kts`,
`CoastlineGenerator.kt`, `DepthZoneMask.kt`, `raster/AsciiGridParser.kt`, `DepthPrebakeTest.kt`,
`Zone300BandRefreshTest.kt`.
