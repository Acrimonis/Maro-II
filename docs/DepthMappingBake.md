<!-- scope: feature -->

# Depth Mapping — Offline Bake Guide

How to produce the preloaded depth data the app reads at first map init. For this fixed
zone (Cannes→Menton) the data is **baked offline** and shipped as assets — no on-device
fetch or decoder (see [depthMappingSources.md](depthMappingSources.md) for why).

## What the app expects

The repository (`DepthRepository`) reads these on a cache miss; **each is optional** —
a missing file just skips that tier (the layer stays inert until baked in):

| Asset (under `app/src/main/assets/`) | Source | Tier | Convention |
|---|---|---|---|
| `depth/emodnet-nice-frejus.asc` | EMODnet DTM 2024 (tile E5) | deep backbone 10–60 m+ | ESRI ASCII, **elevation rel. LAT** (app negates → depth) |
| `depth/litto3d-nice-frejus.asc` | SHOM Litto3D PACA 2015 | collision 0–10 m | ESRI ASCII, **elevation rel. IGN69**, WGS84 (app negates **and** shifts IGN69→LAT) |

- **Region id:** `nice-frejus`. **Box:** lat 43.40–43.75 N, lon 6.70–7.31 E (= `DepthConstants.WATER_BBOX`).
- **Sign/datum handled in-app:** `AsciiGridParser.parse(..., negate = true, latOffsetM = …)`. EMODnet
  uses `latOffsetM = 0.0` (already LAT); Litto3D uses `DepthConstants.IGN69_ABOVE_LAT_M` (0.40 m).
  So the bake only needs to produce **elevation** ESRI ASCII in WGS84 — no vertical math in GDAL.
- The deep grid is kept at native resolution (~115 m); the app resamples it onto the 25 m common
  grid. Litto3D is downsampled with `-r min` (shoalest) to ~5 m in the bake, then the merge
  aggregates to 25 m by min again — conservative throughout.

## Prerequisites

- **GDAL ≥ 3 + PROJ.** Get the GISInternals **x64 / MSVC 2022 / latest stable** "Compiled binaries"
  zip (<https://gisinternals.com/release.php>) and extract it. Then **one of**:
  - **Set `GDAL_HOME`** = the extracted root → `tools\gdal_env.bat` (called by the bake) derives
    `PATH` + `GDAL_DATA` + `PROJ_LIB`/`PROJ_DATA` automatically. *(Simplest.)*
  - Put it on `PATH` yourself: PATH += `<GDAL>\bin` and `<GDAL>\bin\gdal\apps`,
    `GDAL_DATA` = `<GDAL>\bin\gdal-data`, `PROJ_DATA` = `<GDAL>\bin\proj9\SHARE`.
  - Or just run the bake from the GISInternals `SDKShell.bat`.
  Verify: `gdalwarp --version`, `projinfo EPSG:2154` (confirms PROJ found `proj.db`).
- `curl` and `tar` (built into Windows 10+).

## Encapsulated bake & build integration

All depth preprocessing is wrapped in one orchestrator — **`tools\bake_depth.bat`** — which
confirms each source bake individually (showing whether each asset is `present`/`MISSING`) and runs
only the confirmed ones. The per-source scripts (§1–§2) are its building blocks.

`apk-build.bat` integrates it with a single top-level prompt **"Preprocess depth data before build?
[y/N]"** (default **N**):
- **N (default):** no regeneration — the existing bundled `assets/depth/` files are used as-is,
  bundled into the APK, and pushed to the device on deploy (`apk-deploy.bat`).
- **Y:** runs `tools\bake_depth.bat`, which asks per source — `EMODnet deep backbone`,
  `Litto3D collision tier`, and (future) `SHOM survey lots` / `Sentinel-2 SDB`.

Run `tools\bake_depth.bat` standalone (from the repo root) to preprocess without building.

## 1. EMODnet deep backbone (scripted)

```
tools\bake_emodnet.bat
```
Downloads tile **E5** (`downloads.emodnet-bathymetry.eu/v12/E5_2024.tif.zip`, no auth, CC-BY 4.0),
clips to the box, and writes `app/src/main/assets/depth/emodnet-nice-frejus.asc`. E5 is large
(covers the whole NW Mediterranean → North Sea); the clip is small (~a few hundred KB).

## 2. SHOM Litto3D collision tier (scripted fetch + bake)

Litto3D PACA 2015 is **open data** and downloads from the **SHOM INSPIRE pre-package service** — a
**public, no-account API** (no diffusion.shom.fr login or cart needed). Endpoints + verification:
[depthMappingSources.md → "SHOM Litto3D — public download API"](depthMappingSources.md).

1. Fetch the zone tiles (built-in `curl` + `tar` only — bsdtar reads `.7z`, no 7-Zip):
   ```
   tools\fetch_litto3d_paca.ps1            REM ~38 tiles, ~2.8 GB -> tools\litto3d_tiles\*.asc
   tools\fetch_litto3d_paca.ps1 -ListOnly  REM preview tiles + total size, download nothing
   tools\fetch_litto3d_paca.ps1 -Mnt5m     REM extract 5 m DEM (~25x smaller on disk; same ~2.8 GB DL)
   ```
   Lists the prépaquets covering the box, downloads each `.7z` (MD5-verified, resumable), and
   extracts the 1 m MNT `.asc` (Lambert-93 / IGN69, nodata −99999) flat into `tools\litto3d_tiles\`.
   Idempotent — re-runs skip what's already there. The `.7z` archives are kept under
   `tools\litto3d_tiles\_archives\` (delete to reclaim space once baked).

   **Focus on a coast stretch (recommended).** The 38 prépaquets tile the *whole 2-D corridor* —
   including open-sea tiles well south of the coast that hold no 0–10 m data — so the full set is
   ~2.8 GB. Pass a Lambert-93 km window to fetch only the stretch you need. Cannes→Antibes (the app's
   default view + the Lérins validation points) is **13 tiles / ~0.9 GB**:
   ```
   tools\fetch_litto3d_paca.ps1 -Mnt5m -Xmin 1013 -Xmax 1032 -Ymin 6273 -Ymax 6292
   ```
   The bake clips to `WATER_BBOX` and the merge fills uncovered cells from EMODnet, so partial
   Litto3D coverage is fine — extend east (Nice→Menton) later by re-running with a wider window + re-baking.
2. Run:
   ```
   tools\bake_litto3d.bat
   ```
   Mosaics the tiles, reprojects Lambert-93→WGS84, clips, downsamples shoalest (`-r min`), and
   writes `app/src/main/assets/depth/litto3d-nice-frejus.asc`.

> ⚠ Litto3D's guaranteed marine extent is only to the −10 m isobath, and open-coast coverage is
> patchy beyond it — expect the collision band (0–10 m) well-covered, deeper gaps filled by EMODnet.
> Licence: Etalab Licence Ouverte ("Ne pas utiliser pour la navigation" — personal-use planning aid).
> (The diffusion.shom.fr account+cart UI still works as a manual fallback, but is no longer needed.)

## 3. Verify

- `\.gradlew testDebugUnitTest` — pipeline unit tests (merge/parse/validate) stay green.
- Build + run the app; the depth layer loads from the baked assets on first map init. The embedded
  `ValidationReport` (control points incl. the Lérins passage) gates the collision tier.

## Migration plan (toward fully-encapsulated prebake)

Goal: every depth source is produced through the one `bake_depth.bat` orchestrator, selected at
build time, with the app shipping whatever assets are present.

1. **Done** — `tools\bake_{emodnet,litto3d}.bat` + `tools\bake_depth.bat` orchestrator +
   `apk-build.bat` prompt (default N) + asset present/MISSING detection.
2. **Add future source bakes** under `bake_depth.bat` as those sources land: `bake_shom_lots.bat`
   (dive 25–60 m), `bake_sentinel_sdb.bat` (dive 10–25 m) — each follows the same
   present/MISSING + confirm pattern.
3. **Optional `.bin` prebake** — a JVM `DepthPrebakeTest` that merges the `.asc` ingredients into a
   serialized grid for instant first-load (deferred; the app already cooks on first run).
4. **Project-wide parity** — extend the same encapsulation to coastline (JVM prebake) and the
   shared W/E `RegionConfig` prop. Tracked under DepthMapping → `prebakeData` (cross-cutting).

Until a source's bake exists, its slot in `bake_depth.bat` shows a "not yet implemented" notice.

## Later (data-availability-driven, see sources doc)

- **Dive detail 10–60 m:** add SHOM survey *lots* (XYZ→grid) and/or DIY Sentinel-2 SDB (10 m,
  Posidonia-masked) as further `.asc` assets feeding the same merge.
- **Two-resolution grid** (10 m nearshore) once a fine dive source lands.
