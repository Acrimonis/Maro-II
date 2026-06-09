---
name: BakeNormalization
status: active
created: 2026-06-08 00:00
modified: 2026-06-08 17:10
active_subfeature: none
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

### depth source  [ ]

Validate / improve how the depth widget surfaces which dataset a reading came from (EMODnet deep vs
Litto3D shallow) — also the on-device way to confirm the Litto3D east baked in.

#### Findings (current state — validated 2026-06-08)
- **Yes, the source IS shown.** The dashboard **"Profondeur"** card (`DashboardPanel.DepthCard`) puts it in the **subtitle** as `<source> · <confidence>%` — e.g. `EMODnet · 60%`, `Litto3D · 90%`. Value is `%.1f m`; card tinted by the depth-ramp colour.
- Source = `DepthSample.source` (the source at the map centre; `DepthGrid.depthAt` returns the highest-weight valid neighbour's source). Confidence = `DepthSample.confidence` (0–100).
- Labels (`DashboardPanel.depthSourceLabel`): `Litto3D`, `SHOM`, `EMODnet`, `Satellite` (SDB), `GEBCO`, `Interpolé`, `—` (NONE).
- States: not water → "Not at sea / Hors zone"; no data → value "—".
- **So:** shallow nearshore should read `Litto3D · …%`; deep water `EMODnet · …%` — flipping to `Litto3D` over the eastern nearshore is the live confirmation the east baked.

#### Todos
- [ ] (after the east bake) confirm on-device the eastern nearshore depth card reads `Litto3D`

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — `DepthCard` + `depthSourceLabel`
- `app/src/main/java/ykws/android/maro/ui/map/DepthViewModel.kt` — live depth-at-centre sample
- `app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt` — `depthAt` (source = highest-weight neighbour)

## Todos
- [ ] **Come back to it:** verify the full `apk-bake.bat --fresh litto3d depth` end-to-end — eastern tiles fetched → Litto3D rebuilt over the full envelope → depth re-merged → on-device the eastern nearshore shows `Litto3D` — then commit `feature/litto3d-shallow`.
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
- [x] Litto3D `.asc` bloat (deep = nodata, not data) — **implemented** (re-bake `litto3d depth` to confirm): no hardcoded band — clip derives from a coastline-bbox sidecar (`Zone300AssetBaker` emits `<region>.bbox`, `bake-env` reads it) + `bake-litto3d` **gzips** the `.asc` (`AsciiGridParser` reads `.gz`; −99999 padding compresses ~50–100× → ~10–30 MB). See `plans/litto3d-shallow-coverage.md`.
- [x] Cleanup dead code: deleted the dead singular `assets/coastline/` baker path (`CoastlinePrebakeTest` + the stale checked-in `.bin`); `Zone300AssetBaker` → gitignored `data/app-assets/coastlines/` is the one coastline baker the app actually ships (apk-build.bat already pointed there post-rebase)
- [x] Retarget the 6 NM depth-mask to read the SHIPPED coastline (gitignored `data/app-assets/coastlines/`, via `maro.repoDir`) instead of the dead singular checked-in asset — fixes the mask clipping against an unshipped coastline
- [x] `bake-zone300.bat` + `Zone300BandRefreshTest` — recompute the 300 m band from the existing `coastline.bin`, no network (band-only refresh)
- [x] `bake-depth` auto-resolves missing deps (runs `bake-coastline` / `bake-emodnet` if absent), `--no-auto-deps` to hard-fail. (`--no-mask` dropped — the envelope now derives from the coastline, so it's mandatory)
- [x] EMODnet `.tif` download caching — `bake-emodnet` reuses the cached `%TEMP%\emodnet_e5\*.tif`, re-clips only
- [x] Renamed `bake_*.bat` → `bake-*.bat`; updated `docs/DepthMappingBake.md` (bake/build/deploy model, derived envelope, `data/app-assets` paths)
- [ ] Validate the intermittent Overpass-outage theory — confirm the coastline OSM fetch failures are transient (succeeded 13:52, failing ~16:52 on 2026-06-08), not a persistent network / cert / IPv6 block (also tracked in `GLOBAL_TODOS.md`).
- [ ] Complete Litto3D corridor coverage: only the **WEST** is fetched (Fréjus→Antibes, Lambert-93 `X≈995–1024`; 263 tiles / 1.7 GB) — the **EAST** (Antibes→Nice, `X≈1024–1050`) is missing, so the shallow 0–10 m tier is absent there. Fetch it: `tools\fetch_litto3d_paca.ps1 -ListOnly` (preview missing tiles + size) → `-Mnt5m` (download; idempotent, resumable) → `apk-bake.bat litto3d depth`. (EMODnet/deep already covers the whole corridor.)
- [x] `apk-bake` "fresh" offer — each interactive target now asks "Re-fetch / overwrite? [y/N]" (paren-safe `:ask` helper) and passes `--fresh`. Flags: `bake-emodnet --fresh` clears the cached E5 tile; `bake-litto3d --fresh` runs `fetch_litto3d_paca.ps1` (pulls missing tiles); `bake-depth --fresh` clears the depth sources + passes it down. Non-interactive: `apk-bake.bat --fresh <targets>`. (Bats untested here.)
- [x] Fix `bake-depth --fresh` arg parsing — it used `shift`, which also shifts `%0`, breaking every `%~dp0` sub-bake call (it looked for `bake-emodnet.bat` in the repo root). Now parsed via `for %%A in (%*)` (no `shift`).
- [x] Fix APK build OOM (`compressDebugAssets` heap) — `*.asc` didn't match the gzipped `*.asc.gz`, so the 49 MB intermediate shipped; worse, a stale ~1.6 GB `.asc` from an earlier build lingered in `mergeDebugAssets`. Added `*.asc.gz` to `ignoreAssetsPatterns` + purged the stale intermediates → clean re-merge. APK now ~16 MB, ships only the `.bin`. BUILD SUCCESSFUL.
- [x] Depth **source label** made obvious — `DashboardCard` subtitle now **bold** + coloured on a **red→green scale by confidence %** (HSV hue 0→120°). Verified the dashboard number, the colored overlay, and the isobath lines all read the **same** `DepthGrid` (`.bin`, LAT datum) → consistent.

## Rules
- **Baking ≠ fetching.** `bake-litto3d` only *processes* the 1 m tiles already in `tools\litto3d_tiles\` (fetched separately, windowed by Lambert-93 km via `fetch_litto3d_paca.ps1`). Litto3D shallow coverage = whatever tiles were fetched; the deep tier (EMODnet) always spans the full corridor.
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
- `FEAT_DOC_DepthMapping_bake.md` — current bake guide (to be updated as the scripts are normalized)
- `FEAT_PLN_DepthMapping_litto3d-shallow-coverage.md` — design: kill the hardcoded litto3d band → derive the clip from a coastline-bbox sidecar + gzip the nodata-heavy `.asc` + full-tile fetch for whole-range shallow
