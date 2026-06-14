# Session State — DepthMapping (litto3d-regression)

**Last Bake:** 2026-06-10 12:13 UTC

## State
- **Investigation:** Litto3D regression — `.asc.gz` data file missing from data pipeline
- **Root cause:** `litto3d-nice-frejus.asc.gz` was never produced by `bake-litto3d.bat`; only GDAL metadata sidecars exist. The full `bake-depth.bat` pipeline was never executed.
- **Pipeline status:** `apk-bake.bat` already fully integrates Litto3D baking + depth merge. No code changes needed.
- **Fix:** `apk-bake.bat litto3d depth` then `apk-build.bat` + `apk-deploy.bat`

## Key Findings
- Raw Litto3D tiles exist in `tools/litto3d_tiles/` (80+ `.asc` files) — downloaded but never processed
- EMODnet `.asc` exists — baked independently
- `bake-depth.bat` auto-detects the tiles + missing `.asc.gz` and runs `bake-litto3d.bat` as a dependency
- `DepthPrebakeTest` then parses both sources → `mergeDeep` + `mergeShallowShoalest` → produces combined `.bin`

## Files Changed
- `plans/litto3d-regression-analysis.md` — created (regression analysis doc)
- `xTrack/DepthMapping/FEAT_DSC_DepthMapping.md` — attached analysis doc to ## Docs

## Next Step
- Run `apk-bake.bat litto3d depth` to re-bake both sources and produce the combined `.bin`
- Run `apk-build.bat && apk-deploy.bat` to ship
