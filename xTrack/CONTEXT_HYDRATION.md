# Context Hydration — 2026-06-06

**Active Feature:** DepthMapping
**Active Subfeature:** (none)
**Working Folder:** D:\.src\Maro_II_b

## State
Prebake-rollback done + **depth deep tier baked & validated end-to-end**. App = pure consumer
(loads bundled `.bin`; no on-device gather/process). This session:
- **GDAL via one var `GDAL_HOME`** (`D:\Programs nICo\_Dev_\GDal`, GDAL 3.12.1) → `tools\gdal_env.bat`
  derives PATH/GDAL_DATA/PROJ_LIB/PROJ_DATA; bake scripts call it.
- **EMODnet deep tier baked**: `bake_emodnet.bat` → `assets/depth/emodnet-nice-frejus.asc`
  (square cells via `-tr -tap`, numeric NoData) → `DepthPrebakeTest -Dmaro.prebake=true` →
  `assets/depth/nice-frejus.bin` (**18 MB, deep-only**).
- **Bugs fixed during validation**: non-square Golden-Surfer `.asc` → forced square + `FORCE_CELLSIZE`;
  GDAL lowercase `nan` NoData → `AsciiGridParser` now tolerant (`toDoubleOrNull`).
- **Green**: full `testDebugUnitTest` + `assembleDebug` (APK bundles the `.bin`).
- Docs: README + DepthMappingBake (GDAL_HOME primary) + memory (machine GDAL path).

## Next step  (full ordered plan → FEATURE_SCOPE_DepthMapping.md § "Next session — start here")
1. **Restore coastline** — regression: `CoastlineRepository` is load-only but no bundled `.bin` →
   coastline + Zone300 layers are EMPTY. Run `CoastlinePrebakeTest` (OSM) → `assets/coastline/nice-frejus.bin`.
2. **Litto3D collision tier** — manual SHOM download → `tools\litto3d_tiles\` → `bake_litto3d.bat` →
   re-run `DepthPrebakeTest` (merges shallow+deep). Depth is deep-only until then.
3. **MapScreen depth draw** (Rendering). 4. **Housekeeping** — `RegionConfig` W/E prop, asset cleanup, doc-prose.
Prereq for any bake: `GDAL_HOME=D:\Programs nICo\_Dev_\GDal`.

## Still open / cleanup
- `.asc` + gdal sidecars ship in `assets/` (~2 MB) → move bake output to `tools/` intermediates.
- `.bin` is 18 MB → byte-pack source/confidence to shrink.
- Finish rollback doc-prose: `DepthMappingPlan/Sources/Bake`, `FEATURE_SCOPE_Coastline/Zone300`.
- Nothing committed; branch `feature/300M-Claude`. New untracked: `app/src/main/assets/depth/*`, `tools/gdal_env.bat`, prebake tests.
