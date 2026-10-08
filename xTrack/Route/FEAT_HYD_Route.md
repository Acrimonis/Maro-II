# Context Hydration — Route — 2026-10-08

**Last Bake:** 2026-10-08 20:37 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-08 19:05 UTC) the session ran on the user's own words: `#impl` on the active plan shipped **P4.2** (the anchored, tile-keyed fine layer) through the Code→Ask→Architect pipeline and settled D17, then the debt sweep **D40–D52**, then the ten-fix batch **D53–D62** as a direct Code session — each followed by an independent Ask review, with Architect writing the records. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: the prior commit `aee15320`; the P4.2 batch, the debt sweeps and this record are uncommitted on `feature/route-algo-selective-eval` (`#commit` invoked).

## State

**P4.2 landed and the ledger is clear down to the carve.** The fine layer is now a lazy, single-flight tile map on the fixed anchor — sparse hybrid tiles, a key carrying every value the rasterisation reads (the zone stamp among them), a byte ceiling with LRU and an in-flight guard — so a second arming marks only the tiles a farther route newly needs, and the drawn water is byte-identical (`priceReads 3135`, `marks 3141`, `expansions 2820`, one fine build per arm, line `2015.8 m / 783.7 s`). D17 settled — [`261008_FEAT_PLN_Route_anchored-tiles.md`](261008_FEAT_PLN_Route_anchored-tiles.md:1) is the design's one home; **D40–D52** and **D53–D62** cleared; D59 and D62's kept-by-documentation choices recorded. Suite green, `apk-build.bat` green.

**The day's arc, for the reader arriving cold.** The device pass of 2026-10-08 fixed the cost on the fine-layer build; the JVM harness then reproduced the ranking and gated the work; P0–P4.1 landed (one build per arm, the bounded depth scan, the flat cell, the anchored lattice); then P4.2 (the tile map) and three debt batches (D1–D39, D40–D52, D53–D62), with the reviews' D63–D71 logged.

**What is owed.** P4.3–P4.6's carve (deferred whole), P5's record (fold the outcome into [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md), the feature state and the parked perf todo), and the tile-layer device pass (R97). The review's **D63–D71** stand as health — D63 (the eviction gate is half-pinned) and D64 (`TILE built` prints before the build) the two worth taking next.

**No open walk.** The feature file holds no `## Walk` section, so nothing bars a fold.

## Target Files

- `xTrack/Route/261008_FEAT_PLN_Route_selective-perf-eval.md` — the assessment, the measured device pass, the plan, the landed notes, §10's debt ledger and the `## Implemented` entries
- `xTrack/Route/261008_FEAT_PLN_Route_anchored-tiles.md` — the tile design's one home (D17), built 2026-10-08
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTile.kt` — the sparse hybrid tile and its extractor
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt` — the tile key over every value the rasterisation reads
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileMap.kt` — the lazy single-flight map, the byte ceiling and its in-flight guard
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the tile assembly, the halo and its reason
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt` — the zone stamp and the excluded-zone set
- `app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt` — the harness, the count-based reuse signal and the split record
- `app/src/test/java/ykws/android/maro/spatial/multipass/FineTileExtractTest.kt` — the extract boundary at both raster offsets
- `xTrack/Route/FEAT_DSC_Route.md` — the feature state doc, its acquisition-cost todo refreshed

## Next Step

P4.3–P4.6's carve, or P5's record — both the user's to order; the tile-layer device pass is theirs too.
