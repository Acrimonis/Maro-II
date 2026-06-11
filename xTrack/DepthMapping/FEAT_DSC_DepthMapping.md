---
name: DepthMapping
status: active
created: 2026-06-04 00:00
modified: 2026-06-10 12:13
active_subfeature: none
---

# Feature: DepthMapping

**Description:**
Map seafloor depth within the scoped zone across the 0–60 m range at the best
practical precision, for two purposes: high-precision **collision avoidance** in
the 0–5 m near-surface band, and **best-available seafloor profiling** (ridges,
pinnacles) for scuba diving in the 5–60 m band. Source data is heterogeneous
(SHOM, Litto3D, EMODnet, …); normalization for our use cases is a separate
concern, out of scope here. Output feeds depth contour curves and/or color
(hypsometric) depth maps.

> **DepthMapping ≡ BARO** (prior research codename). Source-of-truth for data
> sourcing: `docs/oZer/BARO - *.md`. Consolidated, API-validated design+plan:
> `docs/DepthMappingDesign.md` + `docs/DepthMappingPlan.md`.

## Session progress (2026-06-05)

**Built + unit-tested (pure JVM, all green via `.\gradlew testDebugUnitTest`; full debug APK builds):**
models, scalar marching squares, merge, serializer, **validator** (priority), isobaths, colour ramp.
**Built + compiles (not device-verified):** generator, repository, bitmap, view-model, EMODnet clients.

**Live-validated endpoints (June 2026):**
- EMODnet REST `/depth_sample` — open/no-auth; `{avg:−elev,…}`; Lérins passage ≈ **3.56 m**. ✅ working.
- EMODnet WCS coverage = **`emodnet__mean`** (NOT `emodnet:mean`); GetCoverage formats =
  GeoTIFF / GML / PNG / text — **no ESRI ASCII**. ⇒ full-grid fetch needs a GeoTIFF/GML decoder
  (`EmodnetWcsClient.fetchCoverage` throws until then). `AsciiGridParser` serves SHOM/Litto3D `.asc`.

**Remaining:** GeoTIFF/GML decoder (or bake EMODnet `.asc`) · MapScreen+MainActivity UI wiring ·
Litto3D offline bake → `preloadedShallow` · on-device verification.

> ⚠️ **Repo divergence — IMPORTANT.** This work lives in `D:\.src\Maro_II_b` under
> `data/depth/`. A **separate copy `D:\.src\Maro_II`** has a parallel Sentinel-2 SDB
> implementation under `data/bathymetry/` (`SdbComputer.kt`, `SentinelDownloader.kt`).
> The two trees have diverged (different package: `data/depth` vs `data/bathymetry`).
> Reconcile before further work so the depth feature is not duplicated/conflicting.
> (2026-06-05: fixed a `kotlin.math.max` import error in `Maro_II`'s `SdbComputer.kt:67`;
> a nullability warning remains at `SentinelDownloader.kt:45`.)

## Source re-evaluation & data strategy (2026-06-06)

Re-searched all bathymetry sources (5 parallel agents, live-verified). Full recoverable
synthesis → [depthMappingSources.md](../docs/depthMappingSources.md). Decisions:
- **No on-device decoder.** EMODnet's WCS-no-ASCII snag is bypassed: tile **`E5`** downloads
  directly as ESRI `.asc`/GeoTIFF (no auth, CC-BY) → bake via existing `AsciiGridParser`.
  `EmodnetWcsClient` demoted; `EmodnetRestClient` kept for runtime point cross-checks.
- **Bake-everything for this fixed zone.** Base bake = **Litto3D** (0–10 m collision) +
  **EMODnet E5** (10–60 m+ backbone, ~115 m coarse). Litto3D needs baking anyway (no value
  API), so EMODnet joins the same lane.
- **EMODnet ≠ dive detail** (~115 m; no HRSM/SDB for this zone — verified empty live). Real
  dive detail = SHOM survey **lots** (free, accurate, patchy) and/or DIY **Sentinel-2 SDB**
  (10 m; **Posidonia** degrades it here) — added later, data-availability-driven.
- **Merge = per-cell arbitration:** datum-align→LAT → band rule (collision shoalest / dive
  finest) → disagreement down-weights confidence → SDB cross-calibrated & seagrass-masked.
  Wire up the validator→confidence loop.
- **Grid = 25 m single common grid now** (captures all currently-available real detail;
  ~20 MB full zone; perf-safe via one Bitmap GroundOverlay). Evolve to **two-resolution**
  (10 m nearshore / 25–50 m offshore) only when a fine dive source lands — never
  10-m-everywhere (~124 MB).
- **Pipeline wired (2026-06-06):** `DepthGenerator` is now pure (merges pre-parsed
  `SourceRaster`s, no WCS/IO); `DepthRepository` bakes from `assets/depth/*.asc`; bbox widened;
  `AsciiGridParser` gained a datum-offset; new raster shoalest-merge (land/ceiling-guarded) + tests.
  Build + unit tests green. See [DepthMappingBake.md](../docs/DepthMappingBake.md). Only remaining:
  run the bake + drop the data in.

## Next session — start here (ordered)

Prereq for any bake: `GDAL_HOME=D:\Programs nICo\_Dev_\GDal` (see README / build-environment memory).

1. **On-device verify the depth rendering** *(code wired 2026-06-06; `assembleDebug` +
   `testDebugUnitTest` green; not yet device-run).* Launch the app and confirm: colour-map
   `GroundOverlay` covers the zone with correct N/S/E/W orientation; isobaths at zoom ≥13 (2 m
   contour ≥15); z-order depth→isobaths→300 m band→coastline; dashboard `🌊 Fond` + source·confidence
   + validation badge. Tune ramp / isobath styling on the real basemap. *(Coastline `.bin` 218 KB +
   EMODnet deep `.bin` 18 MB already in `assets/`; the earlier "layers EMPTY" note was stale.)*
2. **Litto3D collision tier (0–10 m, safety)** *(now scripted — NOT blocked):* `tools\fetch_litto3d_paca.ps1`
   (public SHOM INSPIRE API, no account) → `tools\bake_litto3d.bat` → re-run `DepthPrebakeTest`
   (merges shallow+deep) → new `assets/depth/nice-frejus.bin`. The 38 prépaquets tile the full 2-D
   corridor (incl. open-sea tiles; ~2.8 GB), so **focus on a coast stretch** via a Lambert-93 km
   window — Cannes→Antibes (default view + Lérins validation pts) = 13 tiles / ~0.9 GB:
   `-Mnt5m -Xmin 1013 -Xmax 1032 -Ymin 6273 -Ymax 6292`. Bake clips to WATER_BBOX; EMODnet fills the
   rest, so partial coverage is fine (extend east later). See [DepthMappingBake.md](../docs/DepthMappingBake.md) §2.
3. **Housekeeping:** `RegionConfig` W/E prop (`buildConfig` + `gradle.properties`→`BuildConfig`;
   retire the 3 drifting bboxes) · move `.asc` + gdal sidecars out of `assets/` → `tools/`
   intermediates · trim the 18 MB `.bin` (byte-pack source/confidence) · finish rollback doc-prose
   (`DepthMappingPlan/Sources/Bake`, `FEATURE_SCOPE_Coastline/Zone300`) · reconcile
   `MARO_ARCHITECTURE` hard-bound box · (deferred) reconcile `Maro_II` vs `Maro_II_b` divergence.

## Subfeatures
### DataGathering  [ ]
Bbox-scoped, on-device fetch of bathymetry from validated open sources. Single
source of truth = a resampled `DepthGrid` (the resample step is the normalization).

#### Todos
- [x] `EmodnetRestClient` — `/depth_sample` JSON (avg=−elev) **validated live, working** (runtime point cross-check / validation input)
- [x] `resampleOnto` common grid — `SourceRaster.sampleAt` bilinear + `DepthMerge` (NoData-safe)
- [x] `AsciiGridParser` — ESRI `.asc` → `SourceRaster` (SHOM/Litto3D/EMODnet/baked grids; tested)
- [~] **Deep backbone = EMODnet DTM 2024 tile `E5`** — pipeline **wired** (repo loads `assets/depth/emodnet-nice-frejus.asc` → `AsciiGridParser` negate → `mergeDeep`); bake script `tools/bake_emodnet.bat` done; **awaiting data download**. Decoder DROPPED; `EmodnetWcsClient` demoted.
- [~] **Collision tier = Litto3D** — pipeline **wired** (repo loads `assets/depth/litto3d-nice-frejus.asc`, negate + IGN69→LAT via `AsciiGridParser.latOffsetM`; new `mergeShallowShoalest` raster overload, land/ceiling-guarded); bake script `tools/bake_litto3d.bat` done. **Download un-blocked (2026-06-06):** Litto3D PACA is open data on the **public SHOM INSPIRE pre-package API** (no account) → scripted via `tools/fetch_litto3d_paca.ps1` (curl+tar, MD5, idempotent; km-window focusable — full zone 38 tiles ≈ 2.8 GB, Cannes→Antibes focus 13 ≈ 0.9 GB). **Cannes→Antibes 5 m fetch + bake in progress.** Guaranteed only to −10 m isobath.
- [x] **Widen `WATER_BBOX`** to full Cannes→Menton — lat 43.40–43.75, lon 6.70–7.31 (`DepthConstants`).
- [ ] (Dive detail, data-driven) SHOM survey **lots** (free CC BY-SA XYZ multibeam) over Lérins/Antibes/Cap Ferrat → grid to ≤25 m where dense
- [ ] (Dive detail, data-driven) DIY Sentinel-2 **SDB** 10 m (Stumpf + ICESat-2 calib, ACOLITE) — **Posidonia degrades it here**; cross-calibrate + seagrass-mask
- [x] Capture per-source heterogeneity metadata — `SourceRaster(resM, source)`, `DepthSource(nominalResM)`

#### Rules
- All gathering + preprocessing runs on the computer at build time (prebake); the app never fetches or processes — it loads the bundled `.bin`.
- EMODnet WCS has **no ASCII** output → grid fetch needs a GeoTIFF/GML decoder; REST `/depth_sample` is the validated live point source.

#### Key Files
- `app/src/main/java/.../data/depth/raster/EmodnetRestClient.kt` — validated point sampler.
- `app/src/main/java/.../data/depth/raster/EmodnetWcsClient.kt` — WCS URL builder (decoder pending).
- `app/src/main/java/.../data/depth/raster/AsciiGridParser.kt` + `SourceRaster.kt`.
- `docs/DepthMappingPlan.md` — endpoints, signatures, build order.

### PrecisionTiers  [x]
Source-priority strategy by depth band: 0–10 m **shoalest-wins** (Litto3D, collision-safe),
5–60 m **best-resolution** (EMODnet/finest) for seafloor profile. Logic complete + unit-tested.

#### Todos
- [x] `mergeShallowShoalest` — Litto3D wins ≤10 m, shoalest of candidates
- [x] `mergeDeep` — finest-resolution source wins 5–60 m
- [x] Per-cell `source`/`confidence` tracking (down-weighting by residuals: at runtime, pending UI)

#### Rules
- Depths referenced to LAT / lowest tide (conservative).
- Litto3D authoritative only ≤ `SHALLOW_TIER_MAX_M` (10 m).

#### Key Files
- `app/src/main/java/.../data/depth/DepthMerge.kt` (+ `DepthMergeTest`).
- `app/src/main/java/.../data/depth/DepthConstants.kt` — tunables.

### Rendering  [~]
Color (hypsometric) depth map + isobath contours + live depth-at-center readout.

#### Todos
- [x] `DepthColorRamp` hypsometric ramp (0–5 m warning tint) + `DepthBitmap` (ramp unit-tested; now `IntArray`→`createBitmap` one-shot off-thread, not per-pixel `setPixel`)
- [x] Isobath geometry: `marchingSquaresScalar` per level + DP simplify (`DepthIsobaths`, tested)
- [x] `DepthViewModel` — throttled depth-at-center pipeline (compiles)
- [x] **Draw** GroundOverlay + isobath `Polyline`s + zoom-gating + z-order in `MapScreen.kt` — built; `assembleDebug` + `testDebugUnitTest` green; **on-device verify pending**. z-order depth→isobaths→zone300→coastline; gates 11/13/15; `GroundOverlay.setPosition(NW,SE)`; `removeAll` now also drops `GroundOverlay`; centre drives both VMs.
- [x] Depth-at-center dashboard readout (`🌊 Fond`, source·confidence, band tint) + validation confidence badge (RMSE pass/fail) — built; **on-device verify pending**. `MainActivity` creates `DepthViewModel` + `initCache`.

#### Rules
- Color map as a single Bitmap GroundOverlay, NOT per-cell polygons (osmdroid per-frame repaint cost).

#### Key Files
- `app/src/main/java/.../ui/map/DepthColorRamp.kt` (+ test), `DepthBitmap.kt`, `DepthViewModel.kt`.
- `app/src/main/java/.../data/depth/DepthIsobaths.kt` (+ test).

### DataValidation  [x]  ← priority deliverable
Compare extracted grid vs independent ground truth; dev-time JUnit gate done; runtime
confidence badge pending (UI, tracked under Rendering).

#### Todos
- [ ] **(Optional polish) Tier-aware confidence badge:** the `MapScreen` badge shows the *overall* RMSE; now that all control points are in-range it reads a truthful ~1.4 m, so this is no longer urgent — surfacing the **collision-tier** verdict (the safety signal) would still be a touch cleaner than an aggregate.
- [x] `ControlPoints.NICE_FREJUS` fixtures — **rebuilt + survey-cross-checked via EMODnet REST (2026-06-06):** 4 covered, in-range, SeaDataNet-survey-backed points — collision (Lérins passe 3 m), shallow (S of Ste-Marguerite 5.5 m), dive (off Juan-les-Pins 13.8 m + E of Cap d'Antibes 46.5 m). **Removed** the wrong 45 m placeholder and the on-land Cap d'Antibes "sec" (now a documented coverage gap in `ControlPoints` KDoc, no fixture). Bake **`passed=true`**, overall **RMSE 1.45 m** (collision 0.092 m · shallow 1.3 m · dive 1.8 m).
- [x] `DepthValidator` — datum-aligned residuals, per-tier mean bias / RMSE / max
- [x] Datum-mismatch flag (near-constant residual ⇒ vertical-datum offset → auto-calibrate)
- [x] Pass/fail thresholds (collision RMSE ≤0.5 m blocking; dive ≤3 m soft)
- [x] Embed `ValidationReport` in cache (serializer round-trips it; generator attaches it)
- [ ] Surface as dashboard confidence badge (UI)

#### Rules
- Collision-tier (0–5 m) validation failure is blocking (safety-critical).

#### Key Files
- `app/src/main/java/.../data/depth/validation/{ControlPoints,DepthValidator}.kt` (+ `DepthValidatorTest`).
- `app/src/main/java/.../data/model/ValidationReport.kt`.
- `docs/DepthMappingDesign.md` — validation methodology (§ Data validation process).

### prebakeData  [ ]
Unify data prebaking across datasets (coastline, Zone300, depth): default datasets are generated by
tagged JVM **prebake tests** run on the computer (selected via `apk-build.bat`) and shipped in
`assets/`; the app always supports on-device on-demand regen that **overrides** them. The only prop
is the **W/E map extent**. *(Cross-cutting — also touches Coastline; tracked here per direction.)*

#### Todos
- [ ] **Prop = W/E extent:** enable `buildConfig = true` in `app/build.gradle.kts`; read `maro.coast.lonWest`/`maro.coast.lonEast` from `gradle.properties` (committed default) + `local.properties` override → `buildConfigField`.
- [ ] **`RegionConfig`** (new) — single bbox source of truth from `BuildConfig` (W/E from prop, N/S constants); migrate `CoastlineGenerator.LON_WEST/…` and `DepthConstants.WATER_BBOX` to derive from it (kills the 3 drifting boxes).
- [x] **Prebake generators gated** — `CoastlinePrebakeTest` + `DepthPrebakeTest` are JUnit entry points gated by `-Dmaro.prebake=true` (`Assume` + gradle `systemProperty`); **skipped in normal `testDebugUnitTest`/CI**, run only when invoked.
- [x] **`CoastlinePrebakeTest`** — gather OSM → process → build Zone300 band → serialize → `app/src/main/assets/coastline/nice-frejus.bin` (needs network; run via `apk-build.bat`).
- [x] **`DepthPrebakeTest`** — parse bundled `.asc` → `mergeDeep` + shoalest + validate → serialize → `app/src/main/assets/depth/nice-frejus.bin`. **Depth ships the cooked `.bin`** (GDAL gather stays `tools/bake_*.bat`).
- [x] **Repositories are load-only** — `CoastlineRepository`/`DepthRepository` deserialize the bundled `.bin` (incl. Zone300 band); `refresh`/`regenerateBand` = reload; no on-device generation. `assembleDebug` + `testDebugUnitTest` green.
- [x] **Encapsulated depth preprocessing** — `tools\bake_depth.bat` orchestrates all depth source bakes (per-source confirm + asset present/MISSING); future slots (SHOM lots, Sentinel SDB) stubbed "not yet implemented".
- [x] **`apk-build.bat` prompt** — per-dataset prebake prompts (coastline + depth, default **N** → ship existing assets); coastline runs `CoastlinePrebakeTest`, depth runs `bake_depth.bat` + `DepthPrebakeTest`. TODO: a non-interactive flag for plain builds.
- [ ] **Future depth-source bakes** under `bake_depth.bat`: `bake_shom_lots.bat` (dive 25–60 m), `bake_sentinel_sdb.bat` (dive 10–25 m) — add when those sources are built.
- [ ] **Finish rollback doc-prose** — adapt `DepthMappingPlan.md` / `depthMappingSources.md` / `DepthMappingBake.md` + `FEATURE_SCOPE_Coastline.md` / `Zone300.md` (drop on-device / runtime-fetch mentions). Binding rules + Design + Architecture + DepthMapping rules already done.
- [~] **Produce bundled `.bin` assets** — **depth deep tier DONE**: `GDAL_HOME` set (GDAL 3.12.1) → EMODnet baked → `assets/depth/emodnet-nice-frejus.asc` → `DepthPrebakeTest` → `nice-frejus.bin` (18 MB, **deep-only**). Pending: **Litto3D collision tier** (`tools\fetch_litto3d_paca.ps1` → `tools\litto3d_tiles\` → `bake_litto3d.bat` → re-run `DepthPrebakeTest`). Coastline `.bin` already present (218 KB).
- [x] **GDAL via one var** — `tools\gdal_env.bat` derives PATH/GDAL_DATA/PROJ_LIB/PROJ_DATA from `GDAL_HOME`; bake scripts call it; documented (README/Bake/memory). Validated (GDAL 3.12.1, `projinfo EPSG:2154`).
- [x] **Bake/parser hardening** — bake forces square cells (`-tr -tap`) + numeric NoData (`-dstnodata`/`FORCE_CELLSIZE`); `AsciiGridParser` tolerant of `nan`/non-numeric tokens. Unit tests + `assembleDebug` green.
- [ ] **Cleanup** — `.asc` + gdal sidecars (`.aux.xml`,`.prj`) land in `assets/depth/` (shipped ~2 MB); move bake output to `tools/` intermediates so only `.bin` ships; consider trimming the ~18 MB `.bin` (byte-pack source/confidence).
- [x] **Doc fix** — rewrote `docs/MARO_ARCHITECTURE.md` § Data Gathering & Processing Lifecycle + `.clinerules` §8 (dropped the `maro.dataProcessing` flag; prebake = gated tests via `apk-build.bat`; prop = W/E extent).
- [ ] **Reconcile** `MARO_ARCHITECTURE.md` hard-bound box (W 6.73 vs code 6.70) → derive from / cap the prop.

#### Rules
- Prebake generators are side-effecting (network + write `assets/`) → tag-isolated, opt-in only; never in the normal test/CI run.
- App is a **pure loader** (no on-device generation, no regen button); repositories deserialize the bundled `.bin`, `refresh` = reload. The only prop is the W/E map extent.
- W/E configurable via prop; **N/S fixed** (corridor = coast → ~6 NM).
- Prebake writes straight into `app/src/main/assets/<dataset>/`; `app/preloaded/` is deprecated (staging only, not bundled).
- Depth ships the cooked **`.bin`** (GDAL gather + `DepthPrebakeTest` merge/validate, all on the computer); the app loads it (no on-device cook).

#### Key Files
- `app/build.gradle.kts`, `gradle.properties`, `local.properties` — `buildConfig` + W/E prop.
- `…/RegionConfig.kt` (new) — single bbox source of truth.
- `data/coastline/CoastlineGenerator.kt`, `data/depth/DepthConstants.kt` — migrate bounds to `RegionConfig`.
- `data/coastline/CoastlineRepository.kt`, `data/depth/DepthRepository.kt` — load precedence + asset-default lane.
- `app/src/test/.../prebake/{CoastlinePrebakeTest,DepthPrebakeTest}.kt` (new).
- `apk-build.bat` — prebake prompt.
- `docs/MARO_ARCHITECTURE.md`, `.clinerules` — doc rewrite.

### emodnet-gate  [x]
Apply the EMODnet shallow-water gate to the colour map, low-depth warning overlay, and isobath contours. Add a configurable NoData colour to the colour map via `zone.properties`. **All done + tests pass.**

#### Todos
- [x] **Add `depthGated()` method** to `DepthGrid` — returns `NaN` for EMODNET cells shallower than cutoff
- [x] **Update `DepthBitmap`** — accept `emodnetCutoffM` param, use `depthGated()` + water-aware NoData colour via grid.source byte check
- [x] **Update `LowDepthWarningBitmap`** — accept `emodnetCutoffM` param, use `depthGated()` instead of `depthRaw()`
- [x] **Update `DepthIsobaths`** — accept `emodnetCutoffM` param; add separate masking pass for EMODnet shallow cells
- [x] **Update `DepthViewModel.generateRasterLayers()`** — pass `settings.emodnetShallowCutoffM` to all three builders
- [x] **Update `RasterCache.Key`** — use real `emodnetShallowCutoffM` instead of hardcoded `0f`
- [x] **Add `nodata.color` property** to `zone.properties` — default `#FFCCCCCC` (light grey)
- [x] **Load in `ZoneConfig`** — add `nodataColor: Int` field, parse from properties
- [x] **Update `DepthColorRamp.argb()`** — return `0` for NaN (pure function); water-aware NoData colour moved to `DepthBitmap`
- [x] **Update `DepthBitmap`** — accept `nodataColor` param, water-aware via grid.source byte check (no spatial index)
- [x] **Wire in `MapScreen.kt`** — pass `ZoneConfig.nodataColor` to depth bitmap pipeline
- [x] **Unit test** `depthGated()` — covered by `DepthSampleGateTest` (same logic)
- [x] **Unit test** `DepthColorRamp.argb()` — tests pass for transparent default

#### Rules
- EMODnet raster gate uses the same `emodnetShallowCutoffM` setting as the dashboard readout
- Isobaths: EMODnet shallow masking is a **separate pass** from resolution-based `maskCoarseSources()`
- NoData colour also covers above-datum cells (`depthM < 0f`)

#### Key Files
- `app/src/main/java/.../data/model/DepthGrid.kt` — `depthGated()` method
- `app/src/main/java/.../ui/map/{DepthBitmap,DepthColorRamp,LowDepthWarningBitmap}.kt` — builders
- `app/src/main/java/.../data/depth/{DepthIsobaths,RasterCache}.kt` — masking + cache
- `app/src/main/java/.../ui/map/{DepthViewModel,ZoneConfig,MapScreen}.kt` — wiring
- `app/src/main/assets/zone.properties` — `nodata.color` entry
- `xTrack/DepthMapping/FEAT_PLN_DepthMapping_emodnet-gate-nodata-color.md` — design plan

## Todos
- [ ] **▶ NEXT — On-device verify the depth rendering** *(do this first).* Confirm: colour-map GroundOverlay covers the zone with correct N/S/E/W orientation; isobaths appear at zoom ≥13 (2 m ≥15); z-order depth→isobaths→300 m band→coastline; dashboard shows `🌊 Fond` + source·confidence + validation badge (~1.4 m). Baked in `nice-frejus.bin`: deep EMODnet (full zone) + Litto3D collision tier (Cannes→Antibes); coastline `.bin` present.
- [x] ~~EMODnet GeoTIFF/GML decoder~~ — **superseded**: deep tier baked from EMODnet E5 `.asc` (no decoder); validated end-to-end 2026-06-06 (`nice-frejus.bin`).
- [ ] Reconcile `Maro_II` (`data/bathymetry` SDB) vs `Maro_II_b` (`data/depth`) divergence.

## Rules
- Personal-use app, not for distribution — all data prebaked offline on the computer & bundled; no runtime fetch.
- Maintain `docs/depthMappingSources.md` as the recoverable synthesis of bathymetry source research — update it whenever source findings or the data strategy change.
- Keep `docs/DepthMappingDesign.md` and `docs/DepthMappingPlan.md` in sync with decisions until the implementation plan is accepted.

## Key Files
- `app/src/main/proto/depth.proto` + `data/depth/DepthSerializer.kt` — Protobuf cache.
- `data/depth/{DepthGenerator,DepthRepository}.kt` — fetch + cache-aside (mirrors coastline).
- `data/model/DepthGrid.kt` — grid model (`MutableDepthGrid`, bilinear `depthAt`).
- `spatial/SpatialOperations.kt` — `marchingSquaresScalar` + `gridLineToLatLng` (added).
- `docs/DepthMappingDesign.md` / `docs/DepthMappingPlan.md` — design + plan.
- `docs/depthMappingSources.md` — **recoverable source synthesis** (live-verified 2026-06-06): candidates, access, licences, merge design, grid decision.
- `docs/DepthMappingBake.md` + `tools/bake_{emodnet,litto3d}.bat` — offline bake guide + scripts (produce `assets/depth/*.asc`).
- `docs/oZer/BARO - *.md` (×5) — prior research (data sources, Litto3D vs HOMONIM, SDB, concepts).
- `C:\Users\Nicolas\.claude\plans\2-well-lido-is-typed-bonbon.md` — approved implementation plan.

## Docs
- `xTrack/DepthMapping/FEAT_DOC_DepthMapping_design.md` — design document
- `xTrack/DepthMapping/FEAT_DOC_DepthMapping_plan.md` — implementation plan
- `xTrack/DepthMapping/FEAT_DOC_DepthMapping_bake.md` — offline bake guide
- `xTrack/DepthMapping/FEAT_DOC_DepthMapping_sources.md` — recoverable source synthesis
- `docs/oZer/BARO - alternative sources finer than HOMONIM.md` — prior research
- `docs/oZer/BARO - Fetch Sentinel.md` — prior research
- `docs/oZer/BARO - general concepts.md` — prior research
- `docs/oZer/BARO - Sentinel-2 SDB guide.md` — prior research
- `docs/oZer/BARO - Step 01 - Data sources discussion.md` — prior research
- `xTrack/DepthMapping/FEAT_PLN_DepthMapping_emodnet-gate-nodata-color.md` — EMODnet shallow gate + NoData colour plan
- `xTrack/DepthMapping/FEAT_PLN_DepthMapping_litto3d-regression-analysis.md` — Litto3D missing-from-bake regression analysis + fix path
- `xTrack/DepthMapping/FEAT_PLN_DepthMapping_oom-mmap-fix.md` — Depth OOM memory-mapped I/O fix practical steps
- `xTrack/DepthMapping/FEAT_PLN_DepthMapping_intra-raster-progress.md` — Intra-raster progress fix plan for raster builders
