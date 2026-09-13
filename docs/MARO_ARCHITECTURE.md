<!-- scope: reference -->
# Maro-II Spatial Engine Architecture Constraints

## Target Coordinates Boundary
All spatial algorithms must be hard-bounded to the corridor defined in `gradle.properties` (`maro.region.lonWest` / `maro.region.lonEast`, currently 6.70–7.55°E) — the single source of truth; N/S follows the real coast. Reject or truncate any data ingestion outside this box.

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
processed at build time by its prebake tool (e.g. `tools\bake-*.bat` + GDAL, then a JVM
`*PrebakeTest`) and committed as a bundled `.bin`. None is fetched on-device.

**Storage conventions.** Baked datasets live in the gitignored `data/app-assets/<dataset>/` tree and
are packaged into the APK at build time through an asset srcDir — depth `.asc` sources and their
`.bin` outputs both sit there, and the `.asc` is excluded from the APK. `app/preloaded/` is
deprecated. The only build prop is the **map extent** (W/E) in `gradle.properties` → `BuildConfig`;
N/S stay constant (coast → ~6 NM).

**Status: adopted.** Repositories are pure loaders; only the app reads the baked `.bin` assets, and
a missing asset simply means an empty layer until the next bake. Bakes are driven by
`apk-bake.bat` / `tools\bake-*.bat`; `apk-build.bat` never bakes.

## Reference Docs

| Doc | Scope | When to load |
|-----|-------|-------------|
| [docs/color-scheme.md](color-scheme.md) | Color tokens, palette, alias chains | Changing any UI color |
| [docs/ui-component-guidelines.md](ui-component-guidelines.md) | Component + settings patterns: cards, rows, headers, dividers, popup styling, spacing tokens | Building or modifying any settings, menu or list surface |
| [docs/material-icons-standalone-guide.md](material-icons-standalone-guide.md) | How to add Material Symbols icons as standalone ImageVector .kt files | Adding new icons |
| [docs/ui-drawer-guidelines.md](ui-drawer-guidelines.md) | Drawer/overlay UI framework: OverlayLayer + DrawerSlot architecture, animation specs, composable contract, how to add a new drawer | Any drawer or transient UI surface work |
