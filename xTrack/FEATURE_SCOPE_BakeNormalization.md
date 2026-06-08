---
name: BakeNormalization
status: active
created: 2026-06-08 00:00
modified: 2026-06-08 14:31
active_subfeature: none
subs_total: 0
subs_done: 0
one_liner: Rationalize the data-bake / APK-build / device-deploy scripts into a clean bake-[type].bat set plus an apk-bake / apk-build / apk-deploy split, with a single region source of truth.
---

# Feature: BakeNormalization

**Description:** The asset-baking pipeline grew organically and is tangled: prebake prompts live
inside `apk-build.bat`, `tools\bake_depth.bat` is a second interactive orchestrator, two
near-duplicate coastline bakers write to two different asset paths (`assets/coastline/` checked-in
vs the gitignored `data/app-assets/coastlines/`) under two system properties (`maro.prebake` vs
`maro.bake`), the region bounding box is hand-synced across ≥3 files, and the new 6 NM depth mask
made depth-bake depend on `coastline.bin`. Goal: split the pipeline into independently-runnable
`bake-[type].bat` scripts + a single interactive selector (`apk-bake.bat`), a build that only
packages what was baked (`apk-build.bat`), and a deploy that pushes + relaunches (`apk-deploy.bat`).

## Subfeatures

## Todos
- [x] Decomposed bakes into directly-runnable `tools\bake-*.bat`: `bake-coastline` (OSM + band), `bake-emodnet`, `bake-litto3d`, `bake-depth` (merge + 6 NM clip), `bake-zone300`, `bake-env`
- [x] `apk-bake.bat` — selector: present/MISSING menu calling the granular bats; also non-interactive args (`all`, `coastline depth`, …)
- [x] `apk-build.bat` — stripped to just `gradlew assembleDebug` (no baking; ships whatever's in `data/app-assets`)
- [x] `apk-deploy.bat` — already a robust deploy (adb check, device select, install, aapt-launch); left as-is from develop
- [x] Add gradle props `maro.region.lonWest` / `lonEast` as the single region source (→ `BuildConfig.REGION_LON_WEST/EAST`); `CoastlineGenerator` reads them (`LON_WEST/LON_EAST` + fetch-lon now derived)
- [x] Derive the depth envelope from coastline bbox + 6 NM at bake time (`DepthZoneMask.envelopeOf`, `DepthPrebakeTest`); **`DepthConstants.WATER_BBOX` removed**, `DepthGenerator.generate(bbox=…)` now required
- [x] `tools\bake-env.bat` — reads the W/E props for the GDAL clip E/W; S/N = generous seaward window (no float math — the 6 NM that matters is seaward; coverage itself derives at merge). Sourced by bake-emodnet/litto3d
- [x] Move depth `.asc` + `.bin` to gitignored `data/app-assets/depth/` (`git rm --cached` the committed `.bin`, `/data/` already ignored, packaged via asset srcDir); `bake_emodnet/litto3d/depth.bat` OUT + `DepthPrebakeTest` read/write there via `maro.repoDir`
- [x] Exclude depth `.asc`/`.aux.xml`/`.prj` from the APK via `androidResources.ignoreAssetsPatterns` (a corridor-wide Litto3D `.asc` is ~1 GB; app reads only the `.bin`) — verified with `mergeDebugAssets` (no `.asc` packaged, `.bin` kept)
- [x] Stream-parse `.asc` (`AsciiGridParser.parse(File)`) — the ~1 GB Litto3D `.asc` OOM'd `readText()` + the token `ArrayList`; now peak memory ∝ the grid `FloatArray` (~508 MB), fits the 4 g test heap
- [ ] Litto3D `.asc` bloat: the generous seaward box at 5 m = ~972 MB of mostly-nodata padding (→ a 127 M-cell merge). Tighten the litto3d clip (focused tiles, or drop GDAL `-te` padding) so the shallow tier stays nearshore-sized
- [x] Cleanup dead code: deleted the dead singular `assets/coastline/` baker path (`CoastlinePrebakeTest` + the stale checked-in `.bin`); `Zone300AssetBaker` → gitignored `data/app-assets/coastlines/` is the one coastline baker the app actually ships (apk-build.bat already pointed there post-rebase)
- [x] Retarget the 6 NM depth-mask to read the SHIPPED coastline (gitignored `data/app-assets/coastlines/`, via `maro.repoDir`) instead of the dead singular checked-in asset — fixes the mask clipping against an unshipped coastline
- [x] `bake-zone300.bat` + `Zone300BandRefreshTest` — recompute the 300 m band from the existing `coastline.bin`, no network (band-only refresh)
- [x] `bake-depth` auto-resolves missing deps (runs `bake-coastline` / `bake-emodnet` if absent), `--no-auto-deps` to hard-fail. (`--no-mask` dropped — the envelope now derives from the coastline, so it's mandatory)
- [x] EMODnet `.tif` download caching — `bake-emodnet` reuses the cached `%TEMP%\emodnet_e5\*.tif`, re-clips only
- [x] Renamed `bake_*.bat` → `bake-*.bat`; updated `docs/DepthMappingBake.md` (bake/build/deploy model, derived envelope, `data/app-assets` paths)

## Rules
- Bake / build / deploy are separate stages: bake = data prep (writes `assets/`), build = package only (`assembleDebug`), deploy = install + relaunch. `apk-build.bat` must NOT bake.
- Dependency edge: **depth-bake requires `coastline.bin`** (the 6 NM `DepthZoneMask` reads it) — bake coastline before depth; `bake-depth` guards on a missing `coastline.bin`.
- Granular `bake-[type].bat` are non-interactive (flag/env driven); only `apk-bake.bat` prompts — so CI/agents can drive them headless.
- One region bounding box source of truth — never hand-sync the box across `.bat` + Kotlin again.
- One coastline asset path + one bake trigger — no duplicate bakers writing divergent assets.

### Settled decisions (2026-06-08)
- **Coastline asset:** keep the gitignored `data/app-assets/coastlines/` (plural) that the app actually loads, baked by `Zone300AssetBaker`. Delete the dead singular `assets/coastline/` + `CoastlinePrebakeTest`. The depth mask reads the shipped coastline.
- **Region single source = W/E props.** Author only the western & eastern coastline-point longitudes as gradle props (`maro.region.lonWest` / `lonEast`). The coastline clips E/W to them; N/S follows the real coast (OSM fetch uses a generous N/S window — a fetch window, not a coverage cap). The depth **envelope is DERIVED = coastline bbox + 6 NM** (no hardcoded box). REMOVE: `DepthConstants.WATER_BBOX` literals, the `.bat` hardcoded W/S/E/N (→ derived from props + 6 NM via `bake-env.bat`), and `CoastlineGenerator.LON_WEST/LON_EAST` + fetch-lon constants (→ read props). The N/S coverage cap disappears (coverage = coast + 6 NM). `DepthZoneMask` stays; the earlier hardcoded `WATER_BBOX` widening is superseded by derivation.
- **No baked data in git.** ALL bake outputs (coastline `.bin`, depth `.asc` + `.bin`) live in the gitignored `data/app-assets/**` tree, packaged via the build's asset srcDir. `git rm --cached` the committed `app/src/main/assets/depth/*` and gitignore them. Fresh clone → no baked data → bake before build (auto-deps covers it).
- **300 m band:** fold into `bake-coastline` AND expose a network-free `bake-zone300` band-only refresh.
- **Missing dependencies:** bakes **auto-resolve** them (e.g. `bake-depth` runs `bake-coastline` when `coastline.bin` is absent), with `--no-auto-deps` / `--no-mask` opt-outs.

## Key Files
- `apk-bake.bat` (selector), `apk-build.bat` (build-only), `apk-deploy.bat`
- `tools\bake-env.bat`, `bake-coastline.bat`, `bake-emodnet.bat`, `bake-litto3d.bat`, `bake-depth.bat`, `bake-zone300.bat`, `fetch_litto3d_paca.ps1`, `gdal_env.bat`
- `app/src/test/java/ykws/android/maro/data/prebake/DepthPrebakeTest.kt`
- `app/src/test/java/ykws/android/maro/data/coastline/Zone300AssetBaker.kt`, `Zone300BandRefreshTest.kt`
- `gradle.properties` (region props) · `app/build.gradle.kts` (BuildConfig) · `…/data/coastline/CoastlineGenerator.kt` · `…/data/depth/DepthZoneMask.kt`

## Docs
- `docs/DepthMappingBake.md` — current bake guide (to be updated as the scripts are normalized)
