# Feature: DepthMapping

**Status:** Active
**Created:** 2026-06-04T00:00:00.000Z
**Last Modified:** 2026-06-05T00:00:00.000Z
**Active Subfeature:** none
**Description:**
Map seafloor depth within the scoped zone across the 0–60 m range at the best
practical precision, for two purposes: high-precision **collision avoidance** in
the 0–5 m near-surface band, and **best-available seafloor profiling** (ridges,
pinnacles) for scuba diving in the 5–60 m band. Source data is heterogeneous
(SHOM, Litto3D, EMODnet, …); normalization for our use cases is a separate
concern, out of scope here. Output feeds depth contour curves and/or color
(hypsometric) depth maps.

**One-liner:** Map 0–60 m seafloor depth in the scoped zone for collision avoidance and dive profiling.

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

## Subfeatures
### DataGathering  [ ]
Bbox-scoped, on-device fetch of bathymetry from validated open sources. Single
source of truth = a resampled `DepthGrid` (the resample step is the normalization).

#### Todos
- [~] `EmodnetWcsClient` — coverage **`emodnet__mean`**; WCS emits GeoTIFF/GML (no ASCII) → **needs a GeoTIFF/GML decoder** before the grid fetch works (client built + URL correct; throws until decoder)
- [x] `EmodnetRestClient` — `/depth_sample` JSON (avg=−elev) **validated live, working**
- [x] `resampleOnto` common grid — `SourceRaster.sampleAt` bilinear + `DepthMerge` (NoData-safe)
- [x] `AsciiGridParser` — ESRI `.asc` → `SourceRaster` (for SHOM/Litto3D/baked grids; tested)
- [ ] Litto3D offline bake (portal `.asc` → reproject Lambert93→WGS84 → IGN69→LAT → `app/preloaded/depth/nice-frejus.bin`); document Cannes→Menton tile list
- [ ] (Deferred) Sentinel-2 SDB path (Copernicus OIDC+STAC+OData+Stumpf) — note parallel impl in `Maro_II/data/bathymetry`
- [x] Capture per-source heterogeneity metadata — `SourceRaster(resM, source)`, `DepthSource(nominalResM)`

#### Rules
- Fetch on demand only — one-time lazy fetch on first map init; never fetch while at sea.
- Run all pre-gathering and preprocessing logic on-device.
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

### Rendering  [ ]
Color (hypsometric) depth map + isobath contours + live depth-at-center readout.

#### Todos
- [x] `DepthColorRamp` hypsometric ramp (0–5 m warning tint) + `DepthBitmap` (ramp unit-tested)
- [x] Isobath geometry: `marchingSquaresScalar` per level + DP simplify (`DepthIsobaths`, tested)
- [x] `DepthViewModel` — throttled depth-at-center pipeline (compiles)
- [ ] **Draw** GroundOverlay + isobath `Polyline`s + zoom-gating + z-order in `MapScreen.kt` (on-device)
- [ ] Depth-at-center dashboard readout + validation confidence badge (UI)

#### Rules
- Color map as a single Bitmap GroundOverlay, NOT per-cell polygons (osmdroid per-frame repaint cost).

#### Key Files
- `app/src/main/java/.../ui/map/DepthColorRamp.kt` (+ test), `DepthBitmap.kt`, `DepthViewModel.kt`.
- `app/src/main/java/.../data/depth/DepthIsobaths.kt` (+ test).

### DataValidation  [x]  ← priority deliverable
Compare extracted grid vs independent ground truth; dev-time JUnit gate done; runtime
confidence badge pending (UI, tracked under Rendering).

#### Todos
- [x] `ControlPoints.NICE_FREJUS` fixtures — Lérins passage (REST-confirmed ≈3.56 m), dive sites, soundings
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

## Todos
- [ ] Implement EMODnet GeoTIFF/GML decoder (or bake `.asc`) to unblock the live deep-tier grid.
- [ ] Reconcile `Maro_II` (`data/bathymetry` SDB) vs `Maro_II_b` (`data/depth`) divergence.

## Rules
- Personal-use app, not for distribution — runtime fetch + offline baking both allowed.

## Key Files
- `app/src/main/proto/depth.proto` + `data/depth/DepthSerializer.kt` — Protobuf cache.
- `data/depth/{DepthGenerator,DepthRepository}.kt` — fetch + cache-aside (mirrors coastline).
- `data/model/DepthGrid.kt` — grid model (`MutableDepthGrid`, bilinear `depthAt`).
- `spatial/SpatialOperations.kt` — `marchingSquaresScalar` + `gridLineToLatLng` (added).
- `docs/DepthMappingDesign.md` / `docs/DepthMappingPlan.md` — design + plan.
- `docs/oZer/BARO - *.md` (×5) — prior research (data sources, Litto3D vs HOMONIM, SDB, concepts).
- `C:\Users\Nicolas\.claude\plans\2-well-lido-is-typed-bonbon.md` — approved implementation plan.
