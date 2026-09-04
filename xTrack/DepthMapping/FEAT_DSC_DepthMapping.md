---
name: DepthMapping
status: active
created: 2026-06-04 00:00
modified: 2026-06-10 12:13
---

# Feature: DepthMapping

**Description:**
Map seafloor depth within the scoped zone across the 0–60 m range at the best practical precision — high-precision collision avoidance in 0–5 m, and best-available seafloor profiling in 5–60 m. Output feeds depth contour curves and/or colour depth maps.

> **DepthMapping ≡ BARO** (prior research codename). Source-of-truth for data sourcing: `docs/oZer/BARO - *.md`.

## Sections

### DataGathering

Bbox-scoped fetch of bathymetry from validated open sources; single source of truth = a resampled `DepthGrid`.

#### Todos
- [~] Deep backbone = EMODnet DTM tile E5 — pipeline wired, awaiting data download
- [~] Collision tier = Litto3D — pipeline wired; Cannes→Antibes 5 m fetch + bake in progress
- [ ] (Dive detail) SHOM survey lots over Lérins/Antibes/Cap Ferrat
- [ ] (Dive detail) Sentinel-2 SDB 10 m (Posidonia degrades it — cross-calibrate + seagrass-mask)

#### Rules
- All gathering + preprocessing runs on the computer at build time (prebake); the app never fetches or processes — it loads the bundled `.bin`.
- EMODnet WCS has no ASCII output; REST `/depth_sample` is the validated live point source.

#### Key Files
- `app/src/main/java/.../data/depth/raster/{EmodnetRestClient,EmodnetWcsClient,AsciiGridParser}.kt`

### DataValidation

Compare extracted grid vs independent ground truth; JUnit gate done, runtime confidence badge pending (UI).

#### Todos
- [ ] (Optional polish) tier-aware confidence badge — surface the collision-tier verdict
- [ ] Surface as dashboard confidence badge (UI)

#### Rules
- Collision-tier (0–5 m) validation failure is blocking (safety-critical).

#### Key Files
- `app/src/main/java/.../data/depth/validation/{ControlPoints,DepthValidator}.kt`
- `app/src/main/java/.../data/model/ValidationReport.kt`

### prebakeData

Unify data prebaking across datasets (coastline, Zone300, depth): tagged JVM prebake tests selected via `apk-build.bat`; app is a pure loader.

#### Todos
- [ ] Prop = W/E extent: `buildConfig` + `maro.coast.lonWest/lonEast` gradle props
- [ ] `RegionConfig` — single bbox source of truth (kill 3 drifting boxes)
- [ ] Future depth-source bakes: `bake_shom_lots.bat`, `bake_sentinel_sdb.bat`
- [ ] Finish rollback doc-prose (drop on-device/runtime-fetch mentions)
- [~] Produce bundled `.bin` assets — deep tier done; Litto3D collision tier pending
- [ ] Cleanup — move `.asc` + gdal sidecars to `tools/` intermediates; trim 18 MB `.bin`
- [ ] Reconcile `MARO_ARCHITECTURE.md` hard-bound box

#### Rules
- Prebake generators are side-effecting → tag-isolated, opt-in only; never in normal test/CI run.
- App is a pure loader; the only prop is the W/E map extent (N/S fixed).

#### Key Files
- `app/build.gradle.kts`, `gradle.properties`, `local.properties`
- `data/coastline/CoastlineGenerator.kt`, `data/depth/DepthConstants.kt`
- `app/src/test/.../prebake/{CoastlinePrebakeTest,DepthPrebakeTest}.kt`

## Implemented

- **PrecisionTiers** — shoalest-wins ≤10 m (Litto3D) + finest-resolution 5–60 m; per-cell source/confidence
- **Rendering** — hypsometric `DepthColorRamp` + `DepthBitmap`; isobaths via marching squares + DP simplify; depth-at-center dashboard readout
- **emodnet-gate** — EMODnet shallow gate applied to colour map / warning overlay / isobaths; configurable `nodata.color` → `xTrack/DepthMapping/260609_FEAT_PLN_DepthMapping_emodnet-gate-nodata-color.md`

## Todos
- [ ] **NEXT — On-device verify depth rendering** (orientation, isobaths at z≥13, z-order, dashboard readout)
- [ ] Reconcile `Maro_II` (`data/bathymetry` SDB) vs `Maro_II_b` (`data/depth`) divergence

## Rules
- Personal-use app — all data prebaked offline and bundled; no runtime fetch.
- Maintain `docs/depthMappingSources.md` as the recoverable source synthesis.

## Key Files
- `app/src/main/proto/depth.proto` + `data/depth/DepthSerializer.kt` — Protobuf cache
- `data/depth/{DepthGenerator,DepthRepository}.kt`, `data/model/DepthGrid.kt`
- `spatial/SpatialOperations.kt` — `marchingSquaresScalar` + `gridLineToLatLng`

## Docs
- `xTrack/DepthMapping/FEAT_DOC_DepthMapping_design.md`, `_plan.md`, `_bake.md`, `_sources.md`
- `docs/oZer/BARO - *.md` (×5) — prior research
- `xTrack/DepthMapping/260609_FEAT_PLN_DepthMapping_emodnet-gate-nodata-color.md`
- `xTrack/DepthMapping/260610_FEAT_PLN_DepthMapping_litto3d-regression-analysis.md`
- `xTrack/DepthMapping/260609_FEAT_PLN_DepthMapping_oom-mmap-fix.md`
- `xTrack/DepthMapping/260609_FEAT_PLN_DepthMapping_intra-raster-progress.md`
