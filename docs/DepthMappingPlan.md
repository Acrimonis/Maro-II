<!-- scope: feature -->

# Depth Mapping (BARO) — Plan

Build order, signatures, file anchors, and tuning constants for **DepthMapping**.
Rationale is in [DepthMappingDesign.md](DepthMappingDesign.md). Mirrors the coastline
machinery and the [300MLinePlan.md](300MLinePlan.md) style.

## 0. Where things are today (anchors to reuse)

| Concern | File | Reuse |
|---------|------|-------|
| Protobuf cache pattern | `data/coastline/CoastlineSerializer.kt`, `proto/coastline.proto` | manual javalite builder, packed floats |
| Cache-aside repo | `data/coastline/CoastlineRepository.kt` | `setCacheDir`, read→miss→generate→write, StateFlow |
| Fetch + progress | `data/coastline/CoastlineGenerator.kt` | OkHttp, multi-endpoint, `onProgress(phase,pct)` |
| Pure geometry | `spatial/SpatialOperations.kt` | `douglasPeucker`, `chaikin`, **binary** `marchingSquares` (add scalar variant) |
| Grid/scalar field | `spatial/CoastlineSpatialIndex.kt` | grid keying template |
| Throttled VM | `ui/map/CoastlineViewModel.kt` | `_mapCenter.sample(150ms).mapLatest{}.flowOn(Default)` |
| Overlays | `ui/map/MapScreen.kt` | `drawCoastline`, overlay add/removeAll + invalidate |
| Models | `data/model/{LatLng,BoundingBox,CoastlineState,GenerationProgress}.kt` | reuse `LatLng`, `BoundingBox`, `GenerationProgress` |
| Build wiring | `app/build.gradle.kts` | protobuf-javalite auto-compiles `proto/*.proto` |

## 1. Data model (`data/model/`)

```kotlin
enum class DepthDatum { LAT, MSL, IGN69, UNKNOWN }   // LAT = lowest tide = chart datum
enum class DepthSource(val id: Int, val nominalResM: Double, val seedConfidence: Int) {
    NONE(0, Double.MAX_VALUE, 0), LITTO3D(1, 1.0, 90), SHOM(2, 50.0, 80),
    EMODNET(3, 115.0, 60), SDB(4, 10.0, 70), GEBCO(5, 450.0, 30), INTERPOLATED(6, 0.0, 20);
    companion object { fun fromId(i: Int): DepthSource }
}

data class DepthGrid(
    val regionId: String, val boundingBox: BoundingBox,
    val rows: Int, val cols: Int,
    val cellSizeDegLat: Double, val cellSizeDegLon: Double,
    val datum: DepthDatum,
    val depths: FloatArray,      // rows*cols, metres below datum (positive-down), NaN = NoData
    val source: ByteArray,       // rows*cols, DepthSource.id
    val confidence: ByteArray,   // rows*cols, 0..100
    val metadata: DepthMetadata
) {
    fun idx(r: Int, c: Int) = r * cols + c           // row 0 = south, col 0 = west
    fun hasData(r: Int, c: Int): Boolean
    fun cellCenter(r: Int, c: Int): LatLng
    fun depthAt(lat: Double, lon: Double): DepthSample   // bilinear, NaN-aware
}

data class DepthSample(val depthM: Float, val source: DepthSource, val confidence: Int, val hasData: Boolean)
data class DepthMetadata(val source: String, val fetchTimestampMs: Long, val gridResM: Double,
    val cellCount: Int, val noDataCount: Int, val minDepthM: Float, val maxDepthM: Float,
    val validation: ValidationReport?)
sealed interface DepthState { Idle; Loading; data class Ready(grid); data class Error(message) }
data class Isobath(val depthM: Float, val lines: List<List<LatLng>>)
data class DepthRenderModel(val isobaths: List<Isobath>, val bitmapReady: Boolean)
```

## 2. Scalar contour (add to `spatial/SpatialOperations.kt`)

```kotlin
// Scalar marching squares: contour where field crosses `level`, with linear edge
// interpolation; cells touching NaN emit no edges (gaps suppress contours).
// Keyed by grid-edge identity (not quantized position) so interpolated crossings
// on shared edges connect across cells. Returns rings/lines in continuous (col,row).
fun marchingSquaresScalar(field: FloatArray, cols: Int, rows: Int, level: Double): List<List<GridPt>>

// Convert a (col,row) contour to LatLng using grid geometry.
fun gridLineToLatLng(line: List<GridPt>, bbox: BoundingBox, rows: Int, cols: Int,
    cellLat: Double, cellLon: Double): List<LatLng>
```
Isobath build: for each level in `ISOBATH_LEVELS` → `marchingSquaresScalar` →
`gridLineToLatLng` → `douglasPeucker(line, ISOBATH_EPSILON_M)`.

## 3. Merge (`data/depth/DepthMerge.kt`, pure)

```kotlin
fun resampleOnto(target: MutableDepthGrid, src: SourceRaster, tag: DepthSource)  // bilinear up / area-avg down; NaN never overwrites
fun mergeDeep(target: MutableDepthGrid, src: SourceRaster)                        // finest nominalResM wins
fun mergeShallowShoalest(target: MutableDepthGrid, shallow: DepthGrid)            // ≤ SHALLOW_TIER_MAX_M, shallower wins
fun fillGaps(target: MutableDepthGrid, src: SourceRaster)                         // only where NaN
```

## 4. Serialization (`data/depth/DepthSerializer.kt` + `proto/depth.proto`)

`depth.proto` message `DepthCache`: region_id, bbox doubles, rows/cols, cell sizes,
datum, `repeated float depths [packed]`, `repeated int32 source_ids/confidence
[packed]`, metadata scalars, embedded `ValidationReport`. `DepthSerializer.serialize/
deserialize` mirror `CoastlineSerializer` (manual builder; NaN survives float32).

## 5. Open-API clients (`data/depth/raster/`)

```kotlin
class EmodnetWcsClient(http: OkHttpClient) {
    // GET ows.emodnet-bathymetry.eu/wcs?service=WCS&version=2.0.1&request=GetCoverage
    //     &coverageId=emodnet:mean&subset=Long(..)&subset=Lat(..)&format=text/csv
    suspend fun fetchCoverage(bbox: BoundingBox, onProgress): SourceRaster   // parse CSV/ESRI-ASCII
}
class EmodnetRestClient(http: OkHttpClient) {
    // GET rest.emodnet-bathymetry.eu/depth_sample?geom=POINT(lon lat) -> JSON {avg,min,max,...}
    suspend fun depthSample(lat: Double, lon: Double): Double?
}
data class SourceRaster(val bbox: BoundingBox, val rows: Int, val cols: Int,
    val cellLat: Double, val cellLon: Double, val values: FloatArray, val resM: Double)
```
No auth on either endpoint (validated). EMODnet depth is negative-down (elevation);
normalize to positive-down-below-LAT on parse.

## 6. Repository + generator (`data/depth/`)

```kotlin
class DepthRepository {
    val state: StateFlow<DepthState>; val progress: StateFlow<GenerationProgress>
    fun setCacheDir(context)                       // filesDir/depth/, seed from app/preloaded/depth/
    suspend fun loadDepth(regionId = REGION_ID)    // cache-aside
    suspend fun refreshDepth(regionId)
    fun depthAt(lat, lon): DepthSample
    fun getRenderModel(): DepthRenderModel?
}
class DepthGenerator(gridResM = GRID_RES_M) {
    suspend fun generate(regionId, preloadedShallow: DepthGrid?, onProgress): DepthGrid
    // empty grid → EMODnet WCS (mergeDeep) → preloaded Litto3D (mergeShallowShoalest)
    // → optional GEBCO fillGaps → DepthValidator.validate → immutable grid
}
```

## 7. Validation (`data/depth/validation/`)

`ControlPoints.NICE_FREJUS` fixtures + `app/src/test/resources/depth/control_points_nice_frejus.csv`.
`DepthValidator.validate(grid, points): ValidationReport` (datum-align → residual →
per-tier mean bias/RMSE/max → datum-mismatch flag → pass/fail).

## 8. ViewModel + rendering

`DepthViewModel` mirrors `CoastlineViewModel`: `depthState`, `progress`,
`depthAtCenter: StateFlow<DepthSample?>` via its own `sample(150ms)` pipeline;
`initCache(context)` triggers the one-time lazy load.
`MapScreen`: `buildDepthBitmap(grid): Bitmap` (hypsometric ramp, NaN→transparent) →
`GroundOverlay`; `drawIsobaths(mapView, renderModel, zoom)`; zoom-gating; z-order.

## 9. Constants (companion in `DepthGenerator`)

| Const | Value | Role |
|-------|-------|------|
| `REGION_ID` | "nice-frejus" | cache key |
| `WATER_BBOX` | lat 43.40–43.57, lon 6.90–7.20 (tune) | fetch extent |
| `GRID_RES_M` | 25.0 | common grid resolution |
| `SHALLOW_TIER_MAX_M` | 10.0 | Litto3D ceiling (constraint #4) |
| `COLLISION_MAX_DEPTH_M` | 5.0 | shoalest-critical band |
| `DEPTH_NODATA` | `Float.NaN` | gap sentinel |
| `ISOBATH_LEVELS` | 2,4,6,8,10,15,20,25,30,40,50,60 | contour depths |
| `ISOBATH_EPSILON_M` | 8.0 | DP simplify |
| `DEPTH_MAP_MIN_DRAW_ZOOM` | 11.0 | color gate |
| `ISOBATH_MIN_DRAW_ZOOM` | 13.0 | isobath gate |
| `SHALLOW_ISOBATH_MIN_ZOOM` | 15.0 | 2 m contour gate |
| `COLLISION_RMSE_PASS_M` / `_MAX_ERR_M` | 0.5 / 1.0 | 0–5 m gate (blocking) |
| `DIVE_RMSE_PASS_M` / `_MAX_ERR_M` | 3.0 / 6.0 | 5–60 m gate (soft) |
| `DATUM_BIAS_FLAG_M` | 0.5 | datum-mismatch trigger |
| `MIN_CONTROL_POINTS` | 4 | min for valid report |

## 10. Testing (pure JVM, `app/src/test/`)

- `spatial/` — `marchingSquaresScalar` (single basin, NaN hole, merging basins, level interp).
- `data/depth/DepthMergeTest` — shoalest ≤10 m, finest-res deep, NaN never overwrites, Litto3D ignored >10 m.
- `data/depth/DepthSerializerTest` — round-trip incl. NaN + provenance.
- `data/depth/validation/DepthValidatorTest` — per-tier metrics, injected constant offset ⇒ datum-mismatch flag, thresholds.
- Fixtures: `control_points_nice_frejus.csv`, small baked grid `.bin`.

## 11. Build order (pure-first; thin slice = deep-tier color map)

1. Models (`DepthGrid`/`DepthSample`/enums/`DepthState`/`Isobath`) + idx tests.
2. `marchingSquaresScalar` + tests. *(binary `marchingSquares` already exists.)*
3. `DepthMerge` pure fns + tests.
4. `DepthSerializer` + `depth.proto` round-trip test.
5. `DepthValidator` + `ControlPoints` + `ValidationReport` + tests. **(validation = priority)**
6. `EmodnetWcsClient` (CSV/ASCII → SourceRaster) + `EmodnetRestClient` + smoke test.
7. `DepthRepository` + `DepthGenerator` (EMODnet-only). **← thin vertical slice.**
8. `buildDepthBitmap` + ramp test.
9. `MapScreen.drawDepthMap` GroundOverlay + zoom gate. **First visible: color map.**
10. Isobaths render.
11. `DepthViewModel` + depth-at-center readout.
12. Preloaded cache lane + one-time lazy init wiring.
13. **Litto3D shallow tier** (offline bake → `preloadedShallow` → re-validate). *Hard tier, last.*
14. (Deferred) GEBCO gap-fill; Sentinel-2 SDB fetch button.
15. Validation surfacing + on-device tuning.

**Ship gates:** step 9 = color bathymetry; step 11 = live readout; step 13 = collision-grade shallow.

## 12. Deferred / parked
- Sentinel-2 SDB on-device fetch (Copernicus OIDC+STAC+OData+Stumpf) — see
  [`docs/oZer/BARO - Fetch Sentinel.md`](oZer).
- Real-time tide correction (LAT datum is the conservative stand-in).
- Two-resolution grid (fine shallow / coarse deep) — only if 25 m single grid (~14 MB) too heavy.
- Confirm exact IGN69↔LAT↔MSL offsets during the Litto3D bake.
