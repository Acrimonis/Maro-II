<!-- scope: reference -->
# Maro-II Spatial Engine Architecture Constraints

## Target Coordinates Boundary
All spatial algorithms must be hard-bounded to the Nice-to-Fréjus marine corridor: West 6.73°E, East 7.31°E, South 43.35°N, North 43.73°N. Reject or truncate any data ingestion outside this box.

## Memory-Mapped Architecture Enforcement
When writing Kotlin backend services for data tracking, you are FORBIDDEN from using standard Java `FileInputStream.readAllBytes()` or loading large floating-point arrays into JVM heap memory. You must strictly use native Java `FileChannel` and memory-mapped `ByteBuffers` to perform direct, index-calculated byte offsets (`ByteOffset = (Row * total_cols + Col) * 4L`).

## Asynchronous Visual Processing
Any implementation of Marching Squares (contour generation) or Bitmap color-ramp rendering must be isolated to a background Coroutine (`Dispatchers.Default`). The main UI thread must only receive completed `Bitmap` Ground Overlays or vector `PolylineOptions` ready for rendering.

## Data Gathering & Processing Lifecycle
*(Binding — applies to every spatial dataset: coastline, Zone300 band, depth maps, and future layers.)*

**All prebaking on the computer; the app is a pure consumer.** Each dataset is produced by one
pipeline — **Gather → Process → Serialize** — that runs **only at build time on the computer**,
invoked by a **prebake test/tool**. The result is a serialized `.bin` committed to bundled assets
(`app/src/main/assets/<dataset>/<region>.bin`). The app **only deserializes and draws it** — no
on-device gathering, fetching, merging, or building. *(Rolled back 2026-06-06 from the earlier
on-device/on-demand model — this is an offline-first app used at sea, where runtime fetch is moot.)*

**Prebake mechanism.** Prebake generators are JUnit entry points gated by `-Dmaro.prebake=true`
(via `Assume`), so they are **skipped in normal `testDebugUnitTest`/CI runs** and execute only when
explicitly invoked. `apk-build.bat` prompts which datasets to (re)prebake before building (default
**N** → existing bundled assets ship unchanged).

**App load path.** Read bundled `.bin` → deserialize → build the in-memory query index
(`CoastlineSpatialIndex`) and derived render geometry (depth isobaths) — a *draw* step, not data
generation. No `filesDir` generation cache, no processing-mode flag. A missing asset just means an
empty layer until prebaked.

**Sources (all prebaked on the computer).** OSM coastline (Overpass), EMODnet depth (REST / WCS),
SHOM Litto3D (GDAL reproject), SHOM survey lots, Sentinel-2 SDB — every source is gathered +
processed at build time by its prebake tool (e.g. `tools/bake_*.bat` + GDAL, then a JVM
`*PrebakeTest`) and committed as a bundled `.bin`. None is fetched on-device.

**Storage conventions.** Bundled cooked dataset → `app/src/main/assets/<dataset>/<region>.bin`
(ships in the APK). Build intermediates (e.g. depth `.asc` from GDAL) live under `tools/` and are
**not** bundled. `app/preloaded/` is deprecated. The only build prop is the **map extent** (W/E)
in `gradle.properties` → `BuildConfig`; N/S stay constant (coast → ~6 NM).

**Status (2026-06-06): adopted.** Repositories are pure loaders; `CoastlinePrebakeTest` +
`DepthPrebakeTest` and the `apk-build.bat` prompts are in place; build green. Remaining: the W/E
`RegionConfig` prop, and producing the actual bundled `.bin` assets by running the prebakes.
