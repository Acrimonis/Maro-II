# Context Hydration — Route — 2026-10-15

**Last Bake:** 2026-10-15 12:57 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-14 14:17 UTC) the session opened with the user's `#new routacach`, then ran the fine-tile cache review — three questions, what it caches, when it does it and when it stops — which turned into a requirements list, a plan, the user's own `#impl` (Code hop, build, Ask review, Architect report, then one fix hop on the review's eight defects), a review of the plan itself, and two behaviour fixes on the routing square's white loading dot. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched by the agent, and every claim about the code follows a file read.

## State

**The warm-up starts with the app and grows outward.** The pass no longer waits for the boat to be idle: it starts once both baked layers can answer, and [`FineTileWarm.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileWarm.kt) seeds the walk on `route.prewarm.box` and widens it by whole tiles of the plan's own lattice, one ring at a time, while the store has room. Its three stops are the store's own ceiling, the baked region's edges and the pass's tile budget; **there is no radius dial**, and growth is not linear in area, since a ring yields only the tiles the active plan keeps fine water on.

**Its workplace is its own.** The walk runs on a dispatcher built at the app edge from `route.prewarm.threads`, whose workers carry a background CPU priority, so nothing rasterises on the screen's thread. It **yields** to an armed route and to a depth-raster regeneration, re-asked per tile and per ring, and it **stands down** on battery-saver, which is read at every ask; one process-level slot holds one walk, so an Activity recreation waits rather than starting a second.

**The store answers how full it is, live.** [`TileStore.fullness()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileStore.kt) returns the bytes held, the ceiling and whether one more entry of the size just written would fit, recomputed per read so it cannot latch — a save trims back to the ceiling, which is why a bare `held >= ceiling` test would never fire; the storeless default answers **unknown**, so a fixture with no store behaves as before the seam existed.

**The white dot means the warm-up and nothing else.** The map's per-lookup `onBusy` feed is gone, so no arm's store read can light it; the pass brackets each tile it builds with `FineTileCache.beginWarmTile()` / `endWarmTile()`, merged by the existing grace, so the mark is lit while a tile is really being built, dark while the walk waits at its gate, and dark when everything is already warm. Its beat is the app's **shared** one — the halved `uiMapPulseDotLoadingMs` is deleted and `routeFace` reads `MAP_PULSE_DEFAULT_MS` like every other pulse. Two defaults the `AppConfig` docs carried wrongly (the pulse floor and its period) were corrected to the properties file.

**Two behaviours named and left, both outside the asks:** the signal is still called `tileStoreBusy` on the square and `fineTilesBusy` in the map though it now means the warm-up, and a tile served from memory or the store can still light the dot briefly.

**Planned, not built.** [`261015_FEAT_PLN_Route_warm-area-coverage-and-lifecycle.md`](261015_FEAT_PLN_Route_warm-area-coverage-and-lifecycle.md:1) carries §10, the ceiling moving 48 → 72 MiB across its three mirrors, and §11, the settled **80 % growth target** (`route.prewarm.targetPct`, on named bounds) that leaves the rest of the store as a reserve for water outside the seed area. Both wait on the user's word; §9 records that growth itself evicts nothing, and that what protects the seed is use rather than position.

**Gates.** `:app:testDebugUnitTest :app:assembleDebug` green at 1271 tests with 12 pre-existing skips, and `app-debug.apk` repackaged; `apk-build.bat` calls `gradlew` bare, so it needs the working directory on `PATH`. No device pass has been run on any of it.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTilePrewarm.kt` — the pass, its per-tile window bracket and the gate without `idle`
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileWarm.kt` — `WarmArea` and `warmOutward`, the seed and the outward rings
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileStore.kt` — `fullness()` and `StoreFullness`, the no-op store answering unknown
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCache.kt`, `FineTileActivity.kt` — the one walk slot and the busy window, now the walk's own
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileMap.kt` — the `onBusy` sink removed
- `app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt` — the live fullness verdict
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the plan's own fine lattice and tile side handed to the growth
- `app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt`, `MapScreen.kt` — the host: world readiness, the competing job, the worker, the signals passed in
- `app/src/main/java/ykws/android/maro/ui/map/MapToggleFace.kt`, `RouteOverlay.kt` — the loading face on the shared beat
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties`, `assets/colors.properties` — the growth step and threads keys, and the corrected pulse docs
- `xTrack/Route/261015_FEAT_PLN_Route_warm-area-coverage-and-lifecycle.md` — the plan of record, in design
- `xTrack/Route/FEAT_DSC_Route.md` — the feature file, its fine-layer and warm-up bullets rewritten

## Next Step

No code task is open on the feature. Two items are the user's own and gate nothing: the warm-up's device pass (R97) — a cold start on warm and on cold water, a route armed mid-walk reading the yield and the resume, and battery-saver switched on mid-walk — and D1, whether the tiles stay in the app's cache folder or move to the private files folder. The two planned pieces stand on the user's word: the ceiling rise to 72 MiB and the 80 % growth target. The feature's older open items are unchanged — the corner pass's 50 m coast-margin question, and the fine-tail and `selective` device passes.

## Retirement candidates reported, not moved

- `261007_FEAT_PLN_Route_selective-engine.md` and `261007_FEAT_PLN_Route_rung-ranking-preselection.md` — both attached and both marked landed in the feature's own `## Docs`, as the previous bake reported; nothing new is a candidate this bake.
