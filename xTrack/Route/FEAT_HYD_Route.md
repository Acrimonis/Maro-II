# Context Hydration — Route — 2026-10-13

**Last Bake:** 2026-10-13 20:13 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-11 11:20 UTC) the session ran on the user's own words: `#focus route`, `#new route-perf`, then a scoped device pass the user ran and whose logcat the agent fetched only on their word; the reading and the long correction round (`#brief`, the engine comparison, the zone fine grid, the cache's persistence); "let's cache / save all we can save"; "we will redo the refactoring in this pass"; `#bake` and `#commit`; `#merge` to take `develop`'s corner pass in; a second `#bake` and `#commit`; then the dot — "plan to move this cache loading to a background thread", "still do not see the pulse" three times, "I need the thread to do a pass on startup to see if the zone needs to be built"; and finally "implement the pre warm", its box then widened twice by the user's own figures. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched by the agent, and every claim about the code follows a file read.

## State

**The branch.** `feature/route-perf`, cut from `origin/develop` by the user's own `#new`; `6582dee7` (the store), `574a09c8` (`develop` merged in) and `9ae3653e` (the first bake) committed, everything since uncommitted and unpushed.

**The acquisition's cost is measured, and the fine layer is saved.** A scoped device pass on 2026-10-13 read the warm arms like-for-like: `selective` costs **1.39×** `evolutive` on the short pair (20.3 s against 14.6 s of arming) and **2.12×** on the long one, with Σexpansions 1.85× and 2.24×. The **cold** arm's clock is the fine-layer build alone — 4225.6 ms against 10.6 ms warm on `selective`, 1427.8 against 9.9 on `evolutive` — and the **warm** gap is the price reads: `selective`'s coarse pull reads 0.82–0.94 a mark against 0.32–0.43, and its fine-pass local pull 15511 for 15719 marks against 5777 for the same marks.

**The fine layer survives a restart.** A tile is written to disk beside the depth raster's own cache and read back lazily, one file a tile. Trust rests on a canonical, identity-free encoding of the whole [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt) — nested zone rings, coast bands and both marker-geometry variants included, with the one order-undefined member sorted — stored inside each entry and compared on load, so a re-baked chart, a moved collar or a settings change can only ever be a **miss**. Confirmed on device: `STORE load … hit=false` → `TILE built … halo=9` → `STORE save … bytes=N` on every tile.

**Then the pace left the tile.** A fine tile no longer carries the boat's speed: it stores a pace-free per-member factor and the cost is re-applied once, from the live pace, in one multiply — so one cache serves every cruising speed. `paceKn` left `TileKey`; the codec's `FORMAT_VERSION` went 1 → 2 with no migration. The disk ceiling is 16 MiB read from `route.fineTile.maxBytes`; a reclaim warns once in both locales; a store-served lookup counts as a reuse; a load refreshes the entry's age; and a save no longer re-sums the whole directory.

**The factoring forced one honest move.** The fine layer had been pricing its markers with the **coarse** cell's base while its own cells opened at the fine base. Fixing it, as the factoring requires, can move the drawn line **on marker-priced water** and moves nothing on marker-free water; the user accepted that on 2026-10-13. The fixtures cannot show it — they are marker-free, and both plans still read `2015.8 m / 783.7 s` with the harness's counts exact.

**The corner pass arrived by merge.** `origin/develop` took PR #327 from `feature/routing-angles` before this branch re-synced, so `574a09c8` brought the rebuilt pass, its test and `traceCornerVerdicts` in beside the store work. Three documentation files conflicted and were resolved keep-both; the source and both properties files merged clean; the build cleared the merged tree. That session's own record is [`261013_FEAT_PLN_Route_corner-pass-fixes.md`](261013_FEAT_PLN_Route_corner-pass-fixes.md:1), which arrived with the merge and supersedes its hydration entry.

**The white dot learned what it was reporting.** Three device looks saw nothing. The first two reported the zone asset's load — an APK read, milliseconds long, drawn white on the off square's pale base — and the third reported a store that nothing had asked to work, because nothing runs at startup. The dot now reports the store's own window ([`FineTileCache.busy`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCache.kt:59), [`FineTileActivity`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileActivity.kt:29), the [`FineTileMap.onBusy`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileMap.kt:43) sink, read by `routeFace`), **held for a pass** rather than merged by the 400 ms grace, with `busy on` / `busy off` traced so a device look reads the window instead of guessing at it.

**The pre-warm now gives it something to report.** [`FineTilePrewarm`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTilePrewarm.kt:56) walks a fixed box through the builder's **own** key and build ([`RouteGridBuilder.fineTilesOver`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:458)) — store-first, own-ship outward from the last known position, capped, and **paused rather than stopped** while a route is armed — owned at the edge by `MapRoutePrewarmEffect`, idle only and off battery-saver. The box is `route.prewarm.box`, the Cagnes-sur-Mer → Cannes coast carrying **2 km of margin west, east and south**, with `route.prewarm.maxTiles` (400) and `route.prewarm.maxMs` (120000) beside it.

**What is owed.** The device pass — a cleared cache, the pass at startup, the white dot for its duration, then an arm over that water reading its `STORE load`s as hits; on this merged build it would also show the marker-water move and the two corner bends (`43.53305,7.11067`, `43.54471,7.15744`). Then the engine question re-opened on the warm arms, the pre-warm's own limit below, and the corner family's open item: why the walk places a corner inside the 50 m coast margin at all.

## The pass's limit, stated

- The tile key carries the corridor's **priced-zone set**, so a pre-warm box whose zones differ from an arm's corridor keys a different tile. That costs a rebuild and never a wrong tile, and it is what the device pass's hit rate will measure.
- `fineTilesOver` restates the fine slice of the build's context; the key and the build themselves are shared with the arm, that slice is not, and its KDoc says so.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/` — `FineTile.kt`, `FineTileMap.kt`, `FineTileCache.kt`, `FineTileActivity.kt`, `FineTileCodec.kt`, `TileStore.kt`, `TileKey.kt`, `FineTilePrewarm.kt`, `RouteMarker.kt`, `RouteCostField.kt`, `MultipassGrid.kt`, `RouteGridBuilder.kt`, `RoutePassPrimitives.kt`
- `app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt` — the one-file-a-tile store, atomic writes, age touch, incremental trim
- `app/src/main/java/ykws/android/maro/` — `MainActivity.kt` (install, reclaim toast, trace sinks), `config/AppConfig.kt` (the ceiling, the box, the caps), `spatial/RouteEngineChoice.kt` (the pre-warm's plan), `assets/maro.properties` and `assets/colors.properties`
- `app/src/main/java/ykws/android/maro/ui/map/` — `MapScreen.kt`, `MapRouteEffects.kt`, `MapToggleFace.kt`, `MapPulseDot.kt`, `RouteOverlay.kt`
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `startup_fine_tile_reclaimed`
- `xTrack/Route/261013_FEAT_PLN_Route_pace-free-tiles.md` — the plan of record, implemented
- `xTrack/Route/FEAT_DSC_Route.md`, `FEAT_DOC_Route_engines.md` — the landed facts folded

## Next Step

No code task is open on the feature; the device pass is the user's own.

## Retirement candidates reported, not moved

- `261007_FEAT_PLN_Route_selective-engine.md` and `261007_FEAT_PLN_Route_rung-ranking-preselection.md` — both attached and both marked landed in the feature's own `## Docs`.
