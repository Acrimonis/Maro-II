# Hydration — BakeNormalization

**Last Bake:** 2026-06-08 17:10

## State (pipeline split done + committed a3b8e89; this session = incremental fixes on `feature/litto3d-shallow`)
- Litto3D **EAST** fetched (510 tiles total) → full-corridor `litto3d-nice-frejus.asc.gz` (49 MB) →
  depth `nice-frejus.bin` re-merged (eastern shallow now folded in).
- Fixed `bake-depth --fresh`: it parsed flags with `shift`, which also shifts `%0` → every `%~dp0`
  sub-bake call broke (looked for `bake-emodnet.bat` in the repo root). Now `for %%A in (%*)`.
- Fixed APK build OOM (`compressDebugAssets`): added `*.asc.gz` to `ignoreAssetsPatterns` + purged a
  stale ~1.6 GB `.asc` lingering in `mergeDebugAssets`. APK now ~16 MB, ships only the `.bin`.
- Depth **source label**: bold + red→green-by-confidence (HSV 0→120°) in `DashboardCard`.
- Verified the dashboard depth number, the colored overlay, and the isobath lines all read the **same**
  `DepthGrid` (`.bin`, LAT datum) → consistent (dashboard bilinear; raster/isobaths raw cell).

**Subfeature:** `depth source` [ ] (1 total / 0 done).

## Next
- `apk-deploy.bat` → on device pan into the eastern nearshore (Antibes→Nice); confirm the Profondeur
  card reads a **bold green `Litto3D`** there → then tick the come-back todo + the `depth source` sub.
- Open: validate the intermittent-Overpass theory (also in GLOBAL_TODOS).
- Likely PR `feature/litto3d-shallow` → develop once verified.

## Key files
`tools/bake-depth.bat`, `app/build.gradle.kts`,
`app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`, `data/app-assets/depth/nice-frejus.bin`,
`tools/bake-litto3d.bat`, `tools/bake-emodnet.bat`, `apk-bake.bat`.
