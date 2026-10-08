# Context Hydration — Route — 2026-10-08

**Last Bake:** 2026-10-08 20:59 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-08 20:37 UTC) the session ran on the user's own words: `#impl carve and write-up`, whose Code→Ask→Architect pipeline shipped **P4.5** (the carve, closed as its note plus a copy-not-alias pin) and **P5** (the record), verified green at each hop. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: the prior commit `3d0c1f6b`; the P4.5/P5 batch is uncommitted (`#commit` and `#push` invoked).

## State

**The plan's phases are all closed.** P4.5's carve is closed as its own note — the boundary already held (every carve site writes only to an arm-owned grid, and [`writeInto`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTile.kt:78) copies a tile's members rather than aliasing them), and [`writeIntoCopiesMembersWithoutTouchingTheTile`](../../app/src/test/java/ykws/android/maro/spatial/multipass/FineTileExtractTest.kt:80) holds it across all seven of the tile's arrays. P5's record is folded into [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md) (a fine-tiles bullet, the carve's line on the Ends step, the `TILE built` reading) and the feature state, and both plans' statuses and Docs markers read (implemented). `apk-build.bat` and the full `testDebugUnitTest` are green, the harness record unmoved (`priceReads 3135`, `marks 3141`, `expansions 2820`, `tileBuilds 0`, line `2015.8 m / 783.7 s`).

**The day's arc, for the reader arriving cold.** The device pass of 2026-10-08 fixed the cost on the fine-layer build; the JVM harness reproduced the ranking and gated the work; P0–P4.1 landed (one build per arm, the bounded depth scan, the flat cell, the anchored lattice); then P4.2 (the tile map), three debt batches (D1–D39, D40–D52, D53–D62), and the closing P4.5 + P5 — each reviewed independently, with the findings logged.

**What is owed.** The tile-layer device pass alone (R97). The reviews' health items ride later: **D63** (the eviction gate is half-pinned), **D64** (`TILE built` fires before the build), **F8** (the hit-rate has no production reader), **F9** (the carve rationale stands in many homes) and **F11** (the key's completeness is asserted, not proven).

**No open walk.** The feature file holds no `## Walk` section, so nothing bars a fold.

## Target Files

- `xTrack/Route/261008_FEAT_PLN_Route_selective-perf-eval.md` — the assessment, the measured device pass, the plan, the landed notes, §10's debt ledger (D1–D71, F1–F12) and the `## Implemented` entries
- `xTrack/Route/261008_FEAT_PLN_Route_anchored-tiles.md` — the tile design's one home (D17), built 2026-10-08 with the carve closed as §4's note
- `xTrack/Route/FEAT_DOC_Route_engines.md` — the acquisition engines' reference, now carrying the fine layer's tiles
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTile.kt` — the sparse hybrid tile, its extractor and the copy-not-alias `writeInto`
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt` — the tile key over every value the rasterisation reads
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileMap.kt` — the lazy single-flight map, the byte ceiling and its in-flight guard
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the tile assembly, the halo and its reason, and the carve's own sites
- `app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt` — the harness, the count-based reuse signal and the split record
- `app/src/test/java/ykws/android/maro/spatial/multipass/FineTileExtractTest.kt` — the extract boundary at both raster offsets and the copy-not-alias pin
- `xTrack/Route/FEAT_DSC_Route.md` — the feature state doc, its acquisition-cost todo refreshed

## Next Step

The tile-layer device pass — the user's own (R97), the last open item on the plan.
