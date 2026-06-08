<!-- scope: feature -->

# Depth Mapping — Offline Bake Guide

How to produce the preloaded depth data the app reads at first map init. For this fixed
zone (Cannes→Menton) the data is **baked offline** and shipped as assets — no on-device
fetch or decoder (see [depthMappingSources.md](depthMappingSources.md) for why).

## What the app expects

The repository (`DepthRepository`) reads these on a cache miss; **each is optional** —
a missing file just skips that tier (the layer stays inert until baked in):

| Asset (under gitignored `data/app-assets/`, packaged into the APK) | Source | Tier | Convention |
|---|---|---|---|
| `depth/emodnet-nice-frejus.asc` | EMODnet DTM 2024 (tile E5) | deep backbone 10–60 m+ | ESRI ASCII, **elevation rel. LAT** (app negates → depth) |
| `depth/litto3d-nice-frejus.asc` | SHOM Litto3D PACA 2015 | collision 0–10 m | ESRI ASCII, **elevation rel. IGN69**, WGS84 (app negates **and** shifts IGN69→LAT) |

- **Region id:** `nice-frejus`. **Corridor:** the W/E coastline-point gradle props `maro.region.lonWest/lonEast` (→ `BuildConfig`) are the single source. The depth grid **envelope is derived** (coastline bbox + 6 NM, `DepthZoneMask.envelopeOf`); `DepthZoneMask` then clips the grid to the exact **6 NM-of-coast navigable buffer**. Needs the shipped coastline `data/app-assets/coastlines/nice-frejus.bin` (auto-baked by `bake-depth` if missing). No hardcoded depth box.
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

## Bake / build / deploy

Three separated stages — **bake** (data prep) → **build** (package) → **deploy** (install):

- **`apk-bake.bat`** — the selector. Run it (interactive menu, present/MISSING per asset) or
  non-interactively: `apk-bake.bat all | coastline | emodnet | litto3d | depth | band`. It writes the
  gitignored `data\app-assets\` tree. Each target is a directly-runnable script under `tools\`:
  `bake-coastline` (OSM + 300 m band), `bake-emodnet`, `bake-litto3d`, `bake-depth` (merge + 6 NM clip),
  `bake-zone300` (network-free band refresh). All share the corridor via **`tools\bake-env.bat`**
  (reads the `maro.region.*` props).
- **`apk-build.bat`** — only `gradlew assembleDebug`; it does NOT bake. It packages whatever is in
  `data\app-assets\` (+ app assets); unbaked tiers are simply absent.
- **`apk-deploy.bat`** — installs the debug APK and relaunches the app on the connected device.

Dependencies auto-resolve: `bake-depth` needs the coastline (the 6 NM clip + envelope derive from it)
and the EMODnet `.asc`, and runs `bake-coastline` / `bake-emodnet` first if they're missing
(`--no-auto-deps` to hard-fail instead). A fresh clone has no baked data → bake before build.

## 1. EMODnet deep backbone (scripted)

```
tools\bake-emodnet.bat
```
Downloads tile **E5** (`downloads.emodnet-bathymetry.eu/v12/E5_2024.tif.zip`, no auth, CC-BY 4.0;
the tile is **cached** in `%TEMP%` so re-clips skip the download), clips to the corridor box from
`bake-env.bat`, and writes `data/app-assets/depth/emodnet-nice-frejus.asc`. E5 is large
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
   The bake clips to the corridor box from `bake-env.bat` and the merge fills uncovered cells from
   EMODnet, so partial Litto3D coverage is fine — extend east (Nice→Menton) later by re-running with a
   wider window + re-baking.
2. Run:
   ```
   tools\bake-litto3d.bat
   ```
   Mosaics the tiles, reprojects Lambert-93→WGS84, clips, downsamples shoalest (`-r min`), and
   writes `data/app-assets/depth/litto3d-nice-frejus.asc`.

> ⚠ Litto3D's guaranteed marine extent is only to the −10 m isobath, and open-coast coverage is
> patchy beyond it — expect the collision band (0–10 m) well-covered, deeper gaps filled by EMODnet.
> Licence: Etalab Licence Ouverte ("Ne pas utiliser pour la navigation" — personal-use planning aid).
> (The diffusion.shom.fr account+cart UI still works as a manual fallback, but is no longer needed.)

## 3. Verify

- `\.gradlew testDebugUnitTest` — pipeline unit tests (merge/parse/validate) stay green.
- Build + run the app; the depth layer loads from the baked assets on first map init. The embedded
  `ValidationReport` (control points incl. the Lérins passage) gates the collision tier.

## Status

The encapsulation is in place: directly-runnable `tools\bake-*.bat` per source + the `apk-bake.bat`
selector, the region single-sourced from the `maro.region.*` props, and no baked data in git
(everything lands in the gitignored `data\app-assets\`). Add future dive sources
(`bake-shom-lots`, `bake-sentinel-sdb`) as new `tools\bake-*.bat` targets wired into `apk-bake.bat`.

## Later (data-availability-driven, see sources doc)

- **Dive detail 10–60 m:** add SHOM survey *lots* (XYZ→grid) and/or DIY Sentinel-2 SDB (10 m,
  Posidonia-masked) as further `.asc` assets feeding the same merge.
- **Two-resolution grid** (10 m nearshore) once a fine dive source lands.
