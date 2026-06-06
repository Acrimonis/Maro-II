<!-- scope: feature -->

# Depth Mapping (BARO) — Design

Design notes for the **DepthMapping** feature epic (codename **BARO**, from
*bathymétrie*): gathering, merging, validating, and rendering seafloor depth
(0–60 m, extendable to 80 m) within the app's scoped coastal zone (`nice-frejus`,
Cannes → Menton, incl. Îles de Lérins).

Upstream source-of-truth for data sourcing is the prior research under
[`docs/oZer/`](oZer) (the five `BARO - *.md` notes). **DepthMapping ≡ BARO.** This
document is the consolidated, API-validated design; the build order and signatures
live in [DepthMappingPlan.md](DepthMappingPlan.md).

> **Update 2026-06-06 (supersedes parts below; plan not yet accepted).** Source decisions are
> now recorded authoritatively in [depthMappingSources.md](depthMappingSources.md). Deltas:
> - **No on-device decoder.** EMODnet DTM 2024 tile **`E5`** downloads directly as ESRI `.asc`/
>   GeoTIFF (no auth, CC-BY) → existing `AsciiGridParser`. WCS is **off the data path**
>   (`EmodnetWcsClient` demoted); REST `/depth_sample` kept for runtime point cross-checks.
> - **Bake-everything offline** for this fixed zone: base bake = Litto3D (0–10 m) + EMODnet E5
>   (10–60 m+ backbone, ~115 m). **EMODnet ≠ dive detail** (no HRSM/SDB here — verified empty).
> - **Dive detail = SHOM survey lots** (free CC BY-SA multibeam, patchy) and/or **DIY Sentinel-2
>   SDB** (10 m; **Posidonia seagrass degrades it here**) — added later, data-availability-driven.
> - **Merge = per-cell arbitration:** datum-align→LAT → shoalest (collision) / finest (dive) →
>   disagreement down-weights confidence → SDB cross-calibrate + seagrass-mask; wire validator→confidence.
> - **Grid = 25 m single grid now** (~20 MB, perf-safe); two-resolution (10 m nearshore) later.

## Summary (ELI16)

**The functional case (the "why")**
- Two needs in one map layer: (1) *don't hit the bottom* in the shallows
  (**0–5 m**, collision), and (2) *see the seafloor shape* — ridges, pinnacles,
  wrecks — for **diving** (**5–60 m**).
- These pull in opposite directions: collision wants the **shallowest honest
  number** (safety); diving wants the **sharpest detail** (features).

**Drawing it (the "how")**
- Build one **depth grid** for the zone (a raster of depth-below-lowest-tide).
- From that single grid, *derive* everything: a **color depth map** (one color per
  cell) and **isobath lines** (depth contours), plus a live **depth-under-cursor**
  number. The grid is the one source of truth — the picture can never disagree
  with the number.

**Making it trustworthy (the "are we sure?")**
- We don't trust the data blindly: a **validation harness** compares the grid
  against known depths (charted Lérins passage, published dive sites) and reports
  the error. A constant offset means a **datum mismatch** — auto-corrected.

**Parked for later**
- Real-time tide correction (we use the conservative lowest-tide datum instead).
- The 10 m Sentinel-2 satellite tier (resolves dive features) — a later pass.

## Problem & core principle

Depth data is **heterogeneous** (different resolutions, vertical datums, CRS,
formats, ages) and no single free source covers 0–60 m well. The design resolves
this by **resampling every source onto one common grid** (`DepthGrid`), in one sign
convention (metres below **LAT / lowest tide**, positive-down), with `NaN` for gaps.

**Single source of truth = the grid.** Isobaths come from marching-squares on the
grid; the color map is a per-cell coloring of the grid; point depth is a bilinear
sample of the grid. (Same principle as Zone300, where the drawn line *is* the
distance metric.)

## Source strategy (validated June 2026)

| Tier | Depth | Source | How (validated) | Datum/CRS |
|------|-------|--------|------------------|-----------|
| Deep backbone | 5–60 m+ | **EMODnet Bathymetry DTM 2024** | **Direct tile download, no auth** `downloads.emodnet-bathymetry.eu/v12/E5_2024.asc.zip` (ESRI ASCII, LAT) or `.tif` (GeoTIFF) → `AsciiGridParser`; clip with `gdalwarp`. WCS off the data path (emits no ESRI-ASCII). **Open REST, no auth** `rest.emodnet-bathymetry.eu/depth_sample?geom=POINT(lon lat)` (validated live) for point cross-checks. ~115 m N-S / ~84 m E-W. | LAT (+MSL variant), WGS84 |
| Shallow precision | 0–10 m | **SHOM Litto3D PACA** (Etalab open) | Raw `.asc` via the **public SHOM INSPIRE pre-package API, no auth** (`tools/fetch_litto3d_paca.ps1`; the diffusion.shom.fr cart is just an optional UI). SHOM **WMTS gives rendered tiles only — not values.** 1 m grid, 0.5 m vertical accuracy. Marine extent guaranteed only to −10 m isobath. | **IGN69, Lambert-93** → reproject + datum-shift |
| Dive detail | 10–60 m | **SHOM survey "lots"** (CC BY-SA) | Free XYZ multibeam soundings (data.shom.fr); dense where modern (EM710/EM2040) → grid to ≤25 m. Coverage survey-by-survey (patchy). | LAT, WGS84 |
| Mid (deferred) | 10–25 m | **Sentinel-2 SDB** (Copernicus) | OIDC (no static key) → STAC → OData → DIY Stumpf ratio, ICESat-2 calib. Free. 10 m. ⚠ **Posidonia seagrass degrades retrieval in our dive areas.** | UTM → WGS84 |
| Fallback (deferred) | any | GEBCO 2024 | WCS/NetCDF, 450 m | MSL |

`HOMONIM` (100 m) was evaluated and **dropped** in BARO (too coarse for diving);
EMODnet DTM 2024 supersedes it as the open backbone.

**Decisive consequences:**
- EMODnet is the open, no-auth backbone, **baked offline** from the direct `.asc` tile
  download — no WCS and no on-device raster decoder needed (see the 2026-06-06 update above).
- The EMODnet **REST `/depth_sample`** (JSON, no auth) powers both the validation
  harness and the live depth-at-point readout with **zero raster handling**.
- **Litto3D = preloaded baked grid**: open-licence bulk (public pre-package API) + Lambert-93 reprojection
  make it impractical on-device, so it is prepared offline and shipped via the
  same `app/preloaded/…` lane the coastline already uses. Personal-use only
  (constraint), so no redistribution concern.

## Expected precision by depth range

Two axes trade off across the range — **vertical accuracy** (drives collision) vs
**horizontal resolution / feature detection** (drives diving; a 15 m pinnacle needs
≤~25 m cells to appear at all).

| Range | Use case | Source | H-res | Vertical uncertainty (LAT) | Conf |
|-------|----------|--------|-------|----------------------------|------|
| 0–2 m | Collision (critical) | Litto3D (baked) | 1 m | ±0.5–0.6 m (GPS ±5–15 m is the real limit) | 90 |
| 2–5 m | Shallow caution | Litto3D (baked) | 1 m | ±0.5 m | 90 |
| 5–10 m | Anchoring / shallow dive | Litto3D / EMODnet | 1 m / 115 m | ±0.5–0.8 m / ±1–3 m | 90/60 |
| 10–25 m | Recreational diving | EMODnet (SDB later) | 115 m / 10 m | ±1–3 m / ±1–2 m | 60/70 |
| 25–60 m | Deeper diving | EMODnet | 115 m | ±2–5 m | 60 |
| 60–80 m | Context only | EMODnet/GEBCO | 115–450 m | ±5–10 m+ | 60/30 |

Combined vertical uncertainty ≈ √(source² + datum_residual² + grid_quantization²).
Source figures from Litto3D specs (bathy-LiDAR 0.5 m @95%), Sentinel-2 SDB (1–2 m
RMS), EMODnet composite (~1–3 m). Datum residual IGN69→LAT ~0.3–0.5 m, reduced to
~0.2 m after harness calibration.

**Takeaways:** 0–10 m precision is good (~0.5 m) **only via the baked Litto3D
tier**; 5–60 m **feature detection** is the weak axis until the Sentinel-2 SDB
tier lands. These are a-priori estimates — the **validation harness measures the
actual residuals** per tier and writes them into the cache.

## Precision tiers — merge semantics

The merge rule switches on the depth being written, not on a fixed region:
- **Shallow (0–10 m), shoalest-wins:** Litto3D is authoritative ≤ `SHALLOW_TIER_MAX_M`
  (10 m). Where two candidates exist, the **shallower** wins — conservative, never
  reports deeper than the most pessimistic source. This is the collision-safe rule.
- **Deep (5–60 m), best-resolution-wins:** the **finest-resolution** source wins, to
  keep ridges/pinnacles sharp.
- **Per-cell provenance:** every cell records its `source` and a `confidence`
  (0–100), seeded from the source's nominal accuracy and **down-weighted by the
  validation residuals** for that tier. The live readout shows it.
- **Conflict resolution (multi-source):** datum-align all sources to LAT first (most
  "disagreements" are datum offsets); apply the band rule; where trusted sources still differ
  by more than their combined uncertainty, **down-weight that cell's confidence** and resolve
  conservatively (collision → shoalest, dive → measured over inferred). Downsampling a fine
  source into a collision cell aggregates by **min (shoalest)**, not mean.
- **SDB is systematically biased, not noisy:** cross-calibrate it to the overlapping measured
  grid, and **mask it over Posidonia / dark bottom** — it is a clear-water gap-filler only.

## Datum — lowest tide (LAT)

Per requirement, depths are referenced to the **lowest tide / chart datum (LAT)** —
the conservative choice (shoalest). Litto3D arrives in **IGN69**; the bake applies a
static IGN69→LAT offset (~0.3–0.5 m here). The validation harness then detects any
residual constant bias and recommends a correction — turning validation into a
**self-calibration** step toward LAT.

## Rendering

- **Color depth map** as a single **`Bitmap` `GroundOverlay`**, *not* per-cell
  polygons: osmdroid repaints overlays every pan frame, so thousands of filled
  polygons would jank; one blitted bitmap is near-free (the raster-tile lever the
  Zone300 plan reserved for escalation, adopted up front because data volume forces
  it). Hypsometric ramp, with a distinct warning tint in the 0–5 m collision band.
- **Isobaths** as zoom-gated `Polyline`s at fixed depth steps (2 m to 10 m, then 5–10 m
  to 60 m), derived via scalar marching-squares + Douglas-Peucker simplify.
- **Live depth-at-center** folded into the existing throttled
  `mapCenter.sample(150 ms)` pipeline; dashboard shows depth + datum + source +
  confidence (e.g. "−3.2 m (LAT) · EMODnet · ±2 m"), or "indisponible" on NaN.
- **Z-order:** base tiles → depth color → isobaths → coastline → Zone300 → markers.

## Data validation process

The priority deliverable. A harness usable both **dev-time (JUnit)** and **runtime
(confidence badge)** that compares the extracted grid to independent ground truth.

- **Control points** (`ControlPoint(name, lat, lon, knownDepthM, datum, source,
  toleranceM)`): the **charted shallow passage between Île Sainte-Marguerite and Île
  Saint-Honorat** (most diagnostic collision-band point), **published Lérins
  dive-site depths** (5–60 m), open-water charted soundings, and an independent
  cross-check from the EMODnet REST `/depth_sample`.
- **Method:** datum-align the truth → grid datum, sample the grid bilinearly,
  `residual = sampled − alignedTruth`; skip NaN (report uncovered).
- **Metrics** (`ValidationReport`): mean bias, RMSE, max abs error — overall and
  **per depth-tier** (0–5 / 5–10 / 10–60). A near-constant residual (low variance,
  high |mean bias|) flags a **datum mismatch** → recommend subtracting the bias.
- **Pass/fail:** collision tier RMSE ≤ 0.5 m / max ≤ 1.0 m (**blocking**); dive tier
  RMSE ≤ 3.0 m / max ≤ 6.0 m (soft); ≥ 4 control points.
- **Consumers:** (a) a JUnit regression gate on the merge pipeline; (b) the report
  rides in the Protobuf cache → dashboard badge, and per-cell confidence is
  down-weighted for any failing-tier source.

## Constraints honored

1. **Cache = coastline scheme** (Protobuf-javalite, cache-aside,
   `filesDir/depth/{regionId}.bin`, `app/preloaded/depth/`).
2. **All gathering + preprocessing on the computer (prebake)** — the app loads the bundled cooked
   grid; nothing is fetched or processed on device (offline-first; rolled back 2026-06-06).
3. **LAT / lowest-tide** datum (conservative).
4. **Litto3D < 10 m only** (`SHALLOW_TIER_MAX_M`).
5. Normalization is the single resample-onto-grid step; **validation is the
   centerpiece**.
6. **Personal-use, not distributed** → all data prebaked offline on the computer & bundled; no runtime fetch.

## Key files

| Concern | File |
|---------|------|
| Grid model + provenance | `data/model/DepthGrid.kt`, `DepthSample.kt`, `Isobath.kt` |
| Cache (Protobuf) | `data/depth/DepthSerializer.kt`, `app/src/main/proto/depth.proto` |
| Fetch + merge + validate | `data/depth/{DepthRepository,DepthGenerator,DepthMerge}.kt` |
| Open-API clients | `data/depth/raster/{EmodnetWcsClient,EmodnetRestClient,SourceRaster}.kt` |
| Validation | `data/depth/validation/{ControlPoints,DepthValidator,ValidationReport}.kt` |
| Scalar contour (shared geometry) | `spatial/SpatialOperations.kt` (`marchingSquaresScalar`) |
| Render + readout | `ui/map/MapScreen.kt`, `ui/map/DepthViewModel.kt` |
| Prior research | [`docs/oZer/BARO - *.md`](oZer) |
