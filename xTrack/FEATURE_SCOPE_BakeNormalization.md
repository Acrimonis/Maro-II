---
name: BakeNormalization
status: active
created: 2026-06-08 00:00
modified: 2026-06-08 00:00
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
- [ ] Decompose bakes into directly-runnable `tools\bake-[type].bat`: `bake-coastline` (OSM + geometry + 300 m band), `bake-emodnet`, `bake-litto3d`, `bake-depth` (merge + 6 NM clip)
- [ ] `apk-bake.bat` — the only interactive script: present/MISSING menu of functional choices that calls the granular bats; also accept args (`all`, `coastline depth`) for non-interactive CI/agent runs
- [ ] `apk-build.bat` — strip all prebake prompts; just `gradlew assembleDebug` (ships whatever assets exist)
- [ ] `apk-deploy.bat` — `adb install -r` the debug APK + `am force-stop` + `am start` to relaunch
- [x] Add gradle props `maro.region.lonWest` / `lonEast` as the single region source (→ `BuildConfig.REGION_LON_WEST/EAST`); `CoastlineGenerator` reads them (`LON_WEST/LON_EAST` + fetch-lon now derived)
- [x] Derive the depth envelope from coastline bbox + 6 NM at bake time (`DepthZoneMask.envelopeOf`, `DepthPrebakeTest`); **`DepthConstants.WATER_BBOX` removed**, `DepthGenerator.generate(bbox=…)` now required
- [ ] `tools\bake-env.bat` — derive the GDAL clip box (W/S/E/N) from the W/E props + 6 NM margin; sourced by every bat (kill the bbox drift)
- [x] Move depth `.asc` + `.bin` to gitignored `data/app-assets/depth/` (`git rm --cached` the committed `.bin`, `/data/` already ignored, packaged via asset srcDir); `bake_emodnet/litto3d/depth.bat` OUT + `DepthPrebakeTest` read/write there via `maro.repoDir`
- [ ] APK-size follow-up: exclude depth `.asc`/`.aux.xml`/`.prj` intermediates from packaging (they ride along in `data/app-assets/depth/`; app only reads the `.bin`) — split ingredients to a non-asset `data/bake-cache/` or add an asset-ignore pattern
- [x] Cleanup dead code: deleted the dead singular `assets/coastline/` baker path (`CoastlinePrebakeTest` + the stale checked-in `.bin`); `Zone300AssetBaker` → gitignored `data/app-assets/coastlines/` is the one coastline baker the app actually ships (apk-build.bat already pointed there post-rebase)
- [x] Retarget the 6 NM depth-mask to read the SHIPPED coastline (gitignored `data/app-assets/coastlines/`, via `maro.repoDir`) instead of the dead singular checked-in asset — fixes the mask clipping against an unshipped coastline
- [ ] `bake-zone300.bat` — recompute the 300 m band from an existing `coastline.bin` with no network (band-only refresh), in addition to the band built inside `bake-coastline`
- [ ] `bake-depth` auto-resolves a missing `coastline.bin` by running `bake-coastline` first (with a network notice); `--no-mask` / `--no-auto-deps` to opt out
- [ ] EMODnet `.tif` download caching — skip the hundreds-of-MB re-download when only the clip box changes
- [ ] Rename `bake_*.bat` → `bake-*.bat` for naming consistency; update `docs/DepthMappingBake.md`

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
- `apk-build.bat`, `apk-bake.bat` (to add), `apk-deploy.bat` (to add)
- `tools\bake_depth.bat`, `tools\bake_emodnet.bat`, `tools\bake_litto3d.bat`, `tools\fetch_litto3d_paca.ps1`, `tools\gdal_env.bat`
- `app/src/test/java/ykws/android/maro/data/prebake/CoastlinePrebakeTest.kt`, `app/src/test/java/ykws/android/maro/data/prebake/DepthPrebakeTest.kt`
- `app/src/test/java/ykws/android/maro/data/coastline/Zone300AssetBaker.kt`
- `app/src/main/java/ykws/android/maro/data/depth/DepthConstants.kt`

## Docs
- `docs/DepthMappingBake.md` — current bake guide (to be updated as the scripts are normalized)
