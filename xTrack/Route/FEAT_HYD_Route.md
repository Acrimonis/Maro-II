# Context Hydration — Route — 2026-10-13

**Last Bake:** 2026-10-13 18:24 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-11 11:20 UTC) the session ran on the user's own words: `#focus route`, `#new route-perf`, then a scoped device pass the user ran and whose logcat the agent fetched only on their word; then the reading and a long correction round (`#brief`, the engine comparison, the zone fine grid, the cache's persistence); then "let's cache / save all we can save" and "we will redo the refactoring in this pass"; then the plan file, its acceptance, and `#bake` with `#commit`. No dependency was added, no machine-shaped data file was opened (the depth `.bin` was never read), no work was started without an order, the device was never touched by the agent, and every claim about the code follows a file read.

## State

**The branch.** `feature/route-perf`, cut from `origin/develop` by the user's own `#new` and unpublished; everything below sits uncommitted.

**The acquisition's cost is measured, and the fine layer is now saved.** A scoped device pass on 2026-10-13 (two pairs, each armed twice on each engine, one capture) read the warm arms like-for-like: `selective` costs **1.39×** `evolutive` on the short pair (20.3 s against 14.6 s of arming) and **2.12×** on the long one, with Σexpansions 1.85× and 2.24×. The **cold** arm's clock is the fine-layer build alone — 4225.6 ms against 10.6 ms warm on `selective`, 1427.8 against 9.9 on `evolutive` — and the **warm** gap is the price reads, not the build: `selective`'s coarse pull reads 0.82–0.94 price reads a mark against 0.32–0.43, and its fine-pass local pull reads 15511 for 15719 marks against 5777 for the same marks.

**The fine layer survives a restart.** A tile is written to disk beside the depth raster's own cache and read back lazily, one file a tile. Trust rests on a canonical, identity-free encoding of the whole [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt) — nested zone rings, coast bands and both marker-geometry variants included, with the one order-undefined member sorted — stored inside each entry and compared on load, so a re-baked chart, a moved collar or a settings change can only ever be a **miss**. A corrupt, truncated or foreign entry is deleted rather than half-read.

**Then the pace left the tile.** A fine tile no longer carries the boat's speed: it stores a pace-free per-member factor and the cost is re-applied once, from the live pace, in one multiply — so one cache serves every cruising speed. `paceKn` left `TileKey`; the codec's `FORMAT_VERSION` went 1 → 2 with no migration, so every entry the earlier store wrote is refused. The disk ceiling is 16 MiB read from `route.fineTile.maxBytes`; a reclaim warns once in both locales; a store-served lookup counts as a reuse and prints a `STORE` line; a load refreshes the entry's age; and a save no longer re-sums the whole directory.

**The factoring forced one honest move.** The fine layer was pricing its markers with the **coarse** cell's base while its own cells opened at the fine base — a latent inconsistency. Fixing it, as the factoring requires, can move the drawn line **on marker-priced water** and moves nothing on marker-free water. The user accepted that move on 2026-10-13. The fixtures cannot show it: they are marker-free, and both plans still read `2015.8 m / 783.7 s` with the harness's counts exact (`priceReads=3135`, `marks=3141`, `expansions=2820`).

**This tree carries the OLD corner pass, and that is a branch fact, not a regression.** `RouteCornerPass.kt` here still reaches a fraction of the shorter half-segment and still leaves a corner sharp when no radius clears; the six symbols today's milestone added — `significantLegM`, `sharedLegReach`, `mergeCrossing`, `MIN_CORNER_KN`, `coastFloorM`, `RoundedCorner.tangentM` — return no hits in the source. That milestone sits **published but unmerged** on `feature/routing-angles`, one commit ahead of `develop`, and this branch was cut from develop. The device dump's own `CORNER idx=3 … ask=1398.8m room=33.9m speed=8.94kn` is the artefact the corner session fixed.

**What is owed.** The merge of `feature/routing-angles` into this branch (before any measurement, so a pass reads the store and not a stale corner rule); the device pass on the store's own reading; the marker-water line move, which only the device can show; then the engine question re-opened on the warm arms, and the pre-warm of the Cagnes-to-Cannes box as its own task.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/` — `FineTile.kt`, `FineTileMap.kt`, `FineTileCache.kt`, `FineTileCodec.kt`, `TileStore.kt`, `TileKey.kt`, `RouteMarker.kt`, `RouteCostField.kt`, `MultipassGrid.kt`, `RouteGridBuilder.kt`, `RoutePassPrimitives.kt`
- `app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt` — the one-file-a-tile store, atomic writes, age touch, incremental trim
- `app/src/main/java/ykws/android/maro/` — `MainActivity.kt` (install, reclaim toast, trace sink), `config/AppConfig.kt` (the ceiling accessor), `assets/maro.properties` (`route.fineTile.maxBytes`)
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `startup_fine_tile_reclaimed`
- `xTrack/Route/261013_FEAT_PLN_Route_pace-free-tiles.md` — the plan of record, implemented
- `xTrack/Route/FEAT_DSC_Route.md`, `FEAT_DOC_Route_engines.md` — the landed facts folded

## Next Step

No code task is open on the feature: the merge of `feature/routing-angles` and the device pass are the user's own.

## Retirement candidates reported, not moved

- `261007_FEAT_PLN_Route_selective-engine.md` and `261007_FEAT_PLN_Route_rung-ranking-preselection.md` — both attached and both marked landed in the feature's own `## Docs`.
