<!-- scope: reference -->
# Maro-II Spatial Engine Architecture Constraints

## Target Coordinates Boundary
All spatial algorithms must be hard-bounded to the corridor defined in `gradle.properties` (`maro.region.lonWest` / `maro.region.lonEast`, currently 6.70–7.55°E) — the single source of truth; N/S follows the real coast. Reject or truncate any data ingestion outside this box.

## Grid Memory Model
The depth grid lives on the JVM heap: `DepthRepository` opens the bundled `assets/depth/<region>.bin` as a stream and `DepthSerializer` decodes the packed cells straight into one `FloatArray` (`index = row * cols + col`) — no intermediate byte copy, no `parseFrom`. The cold path exceeds the default heap cap, which is why the manifest declares `largeHeap` (budget in `xTrack/DepthMapping/260921_FEAT_PLN_DepthMapping_cold-start-heap.md`). Only the rendered rasters go through a channel: `RasterCache` persists the colour-map and low-depth bitmaps as raw `IntArray` pixels via `ByteBuffer` → `FileChannel` under `cacheDir/raster/`. Never read a whole asset with `readAllBytes()` first; stream it.

## Asynchronous Visual Processing
Any implementation of Marching Squares (contour generation) or Bitmap color-ramp rendering must be isolated to a background Coroutine (`Dispatchers.Default`). The main UI thread must only receive completed `Bitmap` Ground Overlays or vector `PolylineOptions` ready for rendering.

## Data Gathering & Processing Lifecycle
*(Binding — applies to every spatial dataset: coastline, Zone300 band, depth maps, and future layers.)*

**Prebaking on the computer is the normal path.** Each dataset is produced by one pipeline —
**Gather → Process → Serialize** — that runs at build time on the computer, invoked by a prebake
test/tool. The result is a serialized `.bin` committed to bundled assets
(`app/src/main/assets/<dataset>/<region>.bin`), and the app deserializes and draws it. *(Adopted
2026-06-06 over the earlier on-device/on-demand model — this is an offline-first app used at sea.)*

**Prebake mechanism.** Prebake generators are JUnit entry points gated by `-Dmaro.prebake=true`
(via `Assume`), so they are **skipped in normal `testDebugUnitTest`/CI runs** and execute only when
explicitly invoked. `apk-build.bat` prompts which datasets to (re)prebake before building (default
**N** → existing bundled assets ship unchanged).

**App load path.** Read bundled `.bin` → deserialize → build the in-memory query index
(`CoastlineSpatialIndex`) and derived render geometry (depth isobaths) — a *draw* step, not data
generation. Depth is a pure loader: `DepthRepository` reads the asset or reports an error. The
coastline is not quite: `CoastlineRepository` keeps a `filesDir/coastlines/` cache beside the asset
(newer timestamp wins, the bundled copy is re-written to it), builds the 300 m band in the
background when the loaded data lacks one, and — only when **neither** cache nor asset exists —
falls through to `CoastlineGenerator`, an on-device OSM fetch over Overpass; "Régénérer" deletes the
cache to force that path. `network_security_config.xml` whitelists the Overpass hosts for it.

**Sources.** OSM coastline (Overpass), EMODnet depth (REST / WCS), SHOM Litto3D (GDAL reproject),
SHOM survey lots, Sentinel-2 SDB — every source is gathered + processed at build time by its
prebake tool (e.g. `tools\bake-*.bat` + GDAL, then a JVM `*PrebakeTest`) and committed as a bundled
`.bin`. The Overpass fallback above is the one on-device fetch left.

**Storage conventions.** Baked datasets live in the gitignored `data/app-assets/<dataset>/` tree and
are packaged into the APK at build time through an asset srcDir — depth `.asc` sources and their
`.bin` outputs both sit there, and the `.asc` is excluded from the APK. `app/preloaded/` is
deprecated. The only build prop is the **map extent** (W/E) in `gradle.properties` → `BuildConfig`;
N/S stay constant (coast → ~6 NM).

**Status: adopted, with the coastline fallback still in the code.** A missing depth asset means an
empty layer until the next bake; a missing coastline asset triggers the generator. Bakes are driven
by `apk-bake.bat` / `tools\bake-*.bat`; `apk-build.bat` never bakes.

## Reference Docs

| Doc | Scope | When to load |
|-----|-------|-------------|
| [docs/color-scheme.md](color-scheme.md) | Color tokens, palette, alias chains | Changing any UI color |
| [docs/ui-component-guidelines.md](ui-component-guidelines.md) | Component + settings patterns: cards, rows, headers, dividers, popup styling, spacing tokens | Building or modifying any settings, menu or list surface |
| [docs/material-icons-standalone-guide.md](material-icons-standalone-guide.md) | How to add Material Symbols icons as standalone ImageVector .kt files | Adding new icons |
| [docs/ui-drawer-guidelines.md](ui-drawer-guidelines.md) | Drawer/overlay UI framework: OverlayLayer + DrawerSlot architecture, animation specs, composable contract, how to add a new drawer | Any drawer or transient UI surface work |
