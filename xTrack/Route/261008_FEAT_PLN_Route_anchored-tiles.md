<!-- scope: feature -->
# Route — the anchored, tile-keyed fine layer (P4.2 design + the zone stamp)

**Date:** 2026-10-08 · **Status:** implemented 2026-10-08 — the anchor (P4.1) and this doc's §2–§4 (the zone stamp, the hybrid sparse tile, the tile key, the `FineTileMap`, the routed fine layer and its byte-ceiling/LRU/pin budget) are built; the carve (P4.5) stays deferred; **Order:** split off the sibling plan's P4 block — [`261008_FEAT_PLN_Route_selective-perf-eval.md`](261008_FEAT_PLN_Route_selective-perf-eval.md) §9 — with the tile map as the delivery; **Branch:** `feature/route-algo-selective-eval` cut from `d41b4569`.

This file carries P4.2 forward, and is the tile design's **one home** (D17). The sibling plan keeps the measured evaluation; the anchor's own P4.1 text and every phase that must not move with the tile map stay there. §2–§4 are the shape that shipped on 2026-10-08; only the carve (§5) and the device acceptance (todo 8) remain open.

## 1. The anchor, carried over from P4.1

The anchor is the whole precondition, so it is stated here once even though it landed against the sibling plan.

- **The type.** A new public [`LatticeAnchor`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt) carries `latSouth`, `lonWest` and `referenceLat`; it is public only because the public [`MultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt) names it.
- **The family.** [`LatticeFamily.of(anchor, coarseCellM, fineCellM)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt) draws **both** layers on the one anchor origin and the one reference latitude, the coarse pair still `ratio ×` the fine. Sharing the origin is what the seam's `1 : ratio` nesting rests on, so one anchor must serve both.
- **The source.** [`MultipassWorld.latticeAnchor`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt) answers the depth raster's own south-west origin and centre latitude ([`LiveMultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt)); `null` falls back to [`LatticeAnchor.wholeDegree`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt), still corridor-free. [`buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt) reads it there.
- **The snap.** [`WalkLattice.rowOf`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt) and `colOf` add a tiny `LATTICE_SNAP_EPS`, because a fixed anchor reaches its cell lines by adding an integer multiple of the cell size to the anchor and that round trip leaves the ratio a hair below its integer. The old corridor anchor made the round trip exact (offset zero); the fixed one does not.
- **The bug the anchor exposed.** A window's **local** cell index and its **lattice-global** coordinate coincide only at offset zero. Two sites assumed it: the search seeds at [`RoutePassRunner.runPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt) (the interior grid's local end cells handed to [`WalkWindows.slotOf`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt)) and the berth carve at [`carveEndOf`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt). Both now translate through the new [`WalkWindows.latticeCell`](../../app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt), a no-op for the single grid.
- **The re-baseline landed — 2026-10-08.** The harness fixture in [`RouteSelectivePerfEvalTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt) once pinned `priceReads=8217`, `marks=8226`, `expansions=2640`; the anchored family re-samples the shoreline and the shallow belt, so the counts moved and were re-pinned **deliberately**, never forced back. The anchored fixture now reads **`priceReads 3135` · `marks 3141` · `expansions 2820`** — the corridor-anchor reproduction beside it reads `7916 / 7925 / 2650`, the 301-read gap between the two records staying **unattributed** — and the pin is a **±1 % band** (D23) rather than an exact value, so a cross-environment run cannot redden while a real regression still fails. The line's own distance/duration (`2015.8 m / 783.7 s`) is unmoved, and the fixture cannot bend it, so the line comparison stays device-only (D3).

## 2. What P4.2 builds

**One tile, on the anchored fine lattice, holding only the collar members.**

- **The tile grid.** A tile names a `tileCells × tileCells` block of the fine lattice, so a tile's bounds are pure arithmetic on the anchor: `tileCells × fineCellSizeDegLat` north–south and the degenerate pair east–west. The tile edge in metres is the **knob** that sets how finely the map grows, and it is fixed on the anchored lattice, never per corridor.
- **The membership.** A tile keeps a cell only where the plan's fine mask keeps water — the coast bands, the zone rim and the depth collar of [`FineWater`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt). The collar members are the tile; the box's land and open water outside the collars are not stored at all.
- **The tile type (the hybrid, solved here).** A tile is immutable and holds parallel primitive arrays over its **member** list, never a boxed cell and never a full dense box:
  - a `ByteArray` of state (the `MultipassCellState` ordinal),
  - a `DoubleArray` each of `sourceCostSec`, `zoneLimitKn`, `collarLimitKn`, `bandLimitKn`, `bandCollarLimitKn` and `depthPriceCoef`,
  - an `IntArray` of each member's **local** cell index, so a dense box is never materialised and a lookup is a binary search or a small open-addressed map over the local index.
  This is the shape [`MultipassGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt) already carries after P3, minus the box's absent cells — the same seven primitives, held sparsely.
- **The rasterizer, reused — with one landed correction.** A tile's contents come from the existing [`rasterizeWindow`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt) with the plan's `fineMask` applied — the marking is not re-implemented, and the tile is exactly the water the per-arm fine window used to carry. The build rasterises a box **grown by a collar-wide halo** and stores only the inner block, because the depth bound reads the gate's blocked cells from its own grid — without the halo a tile dropped a row and `expansions` fell to 2134 (the halo and its reason are stated at [`buildFineTile()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:418)).
- **What it replaces.** Only the fine layer. The per-arm fine loop in [`buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt) becomes a set of tile lookups over the boxes the plan's [`fineWindowBoxes`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt) already produces. The **coarse interior stays the per-arm corridor raster**, exactly as the sibling plan's P4 states.

## 3. The key — and the zone stamp

A tile may be reused across arms, so its key carries everything `rasterizeWindow` reads; a key that misses one leaves a stale limit cached and the line silently wrong.

- **The lattice key.** The anchor and the fine cell size — the tile grid is fixed on them, so a different anchor or cell is a different tile.
- **The stamps.** [`depthGenerationStamp`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt), [`coastlineGenerationStamp`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt) and [`emodnetCutoffM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt): a re-baked or re-cut asset, or a moved cutoff, must not leave coefficients cached.
- **The geometry and the law.** The obstacle margin, the gate's min depth and margin, the band's width, limit, outside margin and extra, the zone outside margin, the plan's fine-mask widths (the coast bands and the four collars, shore, band, zone-rim and depth), and the **pace** — the base cost is `baseCostSec(cellM, paceKn)`, so a pace move is a different tile.
- **The switches.** The three `enabled` states (the 300 m band, the speed zones, the depth gate) and the **excluded-zone set**, because the rasterizer reads all four.
- **The zone set and its stamp.** Beside the switches, the key carries the **zone set** the rasterizer even-odd-fills and a **zone stamp** that moves when that set moves.

**The zone stamp — the narrowest world member that does not exist today.** The zones are a runtime [`SpeedZone`](../../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt) list read through a provider ([`NavigationViewModel.speedZones`](../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) into [`LiveMultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt)), and no generation marker stands on them.

- Add `val zoneGenerationStamp: Long get() = 0L` to [`MultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt), defaulted like the two existing stamps.
- The live adapter answers it as a **content stamp** over the same list it hands `speedZonesIn` — the list's own `hashCode` folded to a `Long`, which covers every zone's id, limit, rings and holes because [`SpeedZone`](../../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt) is a data class. It is read once per arm beside the other two stamps, so a rebuilt zone list invalidates the tiles and a moved slider does not.
- The stamp is a **content** stamp, not a monotone counter: the cache is process-lived, and a content hash is the same idiom [`coastlineGenerationStamp`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt) already uses (the index's own `hashCode`).

## 4. Build, cache and budget

- **Lazy and single-flight.** A tile is built on its first request, and concurrent requests for the same key share one build — the [`LadderGridHolder`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt) idiom (its own `Mutex` plus the `Deferred`) lifted to a `FineTileMap`: one `TileKey → Deferred<FineTile>` map, the first caller computing inside the lock and publishing, the rest awaiting the same `Deferred`.
- **Budget.** A byte ceiling with **LRU** eviction sized on the tile's primitive bytes (`tileCells²`-bounded, sparse in practice), and a **pin** so an eviction never drops a tile an in-flight lookup holds. A hit-rate reading rides beside it, so the ceiling and the tile edge are tuned on a number rather than a guess.
- **The carve is out of the tile.** A shared tile is immutable; the per-arm berth carve and the end discs are per-acquisition writes that a tile must not carry. That boundary is stated here only as a constraint on the tile — the carve's own re-shaping is P4.5 and is a separate run.
- **The two caches must not fight.** [`SelectiveMaskCache`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt) sits under this one, holding the plan's pre-snap [`FineWater`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt) law; the tile map holds the post-snap raster. The tile build reads the mask cache's answer rather than re-deriving the collars, so the same water is not built twice.

## 5. What this does not touch

- **The carve** — P4.5's own note, deferred whole.
- **The coarse layer** — it stays the per-arm corridor raster; only the fine layer is tiled.
- **The evaluation** — the sibling plan's §3a measurement and P1–P3's landed levers are untouched; P4.1's harness re-baseline is the first todo below.

## 6. Pinned constraints

- **No new dependencies.** The tile map is Kotlin collections, `Mutex` and `Deferred` already in use.
- **No git writes.**
- **Kotlin idioms, coroutines and `Flow` only** — no raw threads or executors, per `AGENTS.md` §1.
- **KDocs updated** on every touched declaration, including the new world member and the tile type.
- **No hardcoded user-facing strings** — none arise here, but the rule stands.
- **This run's scope** was §2–§4 (the zone stamp, the hybrid tile, the key, the `FineTileMap` and the routed fine layer with its budget) — **all built on 2026-10-08**; §5's carve stays deferred whole, and the device acceptance (todo 8) is the user's.

## 7. Todos

**Landing note, 2026-10-08:** todos 1–7 shipped in one run — the P4.1 re-baseline was already landed (`3135 / 3141 / 2820`), and this run added the zone stamp, the hybrid tile, the key, the `FineTileMap`, the routed fine layer and the budget. Todo 8 (device acceptance) remains the user's.

1. Re-baseline the P4.1 harness deliberately — confirm the anchored line on the fixture, record the new distance/duration and the new `priceReads`/`marks`/`expansions`, and update the pins in [`RouteSelectivePerfEvalTest.kt`](../../app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt) with the reason recorded; keep `apk-build.bat` and the full `testDebugUnitTest` green.
2. Add the **zone stamp** — `MultipassWorld.zoneGenerationStamp` (defaulted) and the live adapter's content stamp — with a unit test that a rebuilt zone list moves the stamp and a re-read does not.
3. Add the **hybrid tile type** — the sparse parallel arrays and the local-index list of §2 — with a structural test that the tile **stores no per-cell box**: its declared instance fields are primitives and primitive arrays (a transient boxed allocator inside a method is out of a declared-field scan's reach, and the test's own KDoc says so), on the same shape as [`MultipassGridShapeTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/MultipassGridShapeTest.kt).
4. Add the **tile key** of §3 and assert its completeness — a test that flipping each keyed value changes the key, so a missing field cannot slip through.
5. Add the **`FineTileMap`** — the `TileKey → Deferred<FineTile>` map, lazy and single-flight on the `LadderGridHolder` idiom — and a test that concurrent requests for one tile build once.
6. Route the fine layer through the tile map in [`buildLayeredGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt): tiles in place of the per-arm fine windows, the coarse interior unchanged, and the harness's own readings unmoved from todo 1's re-baseline (the tile is the same water, so the line must not move a second time).
7. Add the **byte ceiling, LRU and pin** of §4 with the hit-rate reading, and a test that an eviction never drops a pinned tile.
8. **Device acceptance** — the same pair yields the same line across arms; a second arming costs the carve and the search alone, with only the tiles a farther route newly needs built; and the device pass confirms the arming's wall clock and the line against the pre-tile build.

## 8. Acceptance

- The anchor's own test — the same water maps to the same lattice indices across two corridors — stays green.
- Two consecutive arms on one pair draw the **same line**, the fixed anchor having made it deterministic.
- A second arming builds only the tiles a farther route newly needs; the tile build count is the increment, never the whole layer.
- The byte ceiling holds under LRU with no pinned tile dropped, and the hit rate is reported.
- `apk-build.bat` and the full `testDebugUnitTest` are green.

## Risks and open points

- **The anchor's reach.** The depth raster's origin may sit up to a degree from a corridor, so a fine cell's depth sample becomes an array read only where the fine lattice's lines meet the raster's own; where they do not, the radial scan of [`DepthBandLaw`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt) still stands. Aligning the fine cell to the raster's cell is a later question, not this one.
- **The key's long tail.** The rasterizer reads more values than the sibling plan's list names (the pace among them); the key is only as sound as the audit of [`rasterizeFrame`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt), so todo 4 pins it field by field.
- **The two caches.** If the tile map's coverage and the mask cache's union ever disagree about a collar, a tile is built over water the walk does not need, or missed where it does — the mask cache must stay the single home of the collar law.
