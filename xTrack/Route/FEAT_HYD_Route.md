# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 01:50 UTC — written by `#bake`; absence means never baked

**Directive trace:** One session on `feature/avoid-adaptive-grid`, created at the user's `#new`: of the five covered classes none ran unasked — no dependency was added, no machine-shaped data file was opened, work started only on the user's explicit word, the device was never touched, and every claim about the code followed a read. One unfounded assertion was made and corrected in the same session — that the session carried no shell — where the truth is sharper: the shell exists and **Architect mode is what forbids it**, which is why the branch was created from a Code hop.

## State

**The clock's step fix shipped this session** (the speed plan's Phases 1 to 3) and no other code changed; the session also wrote three plans and created the branch. **The fix**: the constant `BOUNDARY_SAMPLE_M = 25.0` is gone for a **required** `sampleM` at half the finest cell the engine walks, `clockSampleM(cellM, fineRatio)` is the clock's own rule in the clock's file, a non-finite limit now reads as no limit in the splitter, and the three engine sites pass the step — with two tests proving a 10 m regime hides between a 25 m step's samples and not between a 5 m step's. The planned "slower of the two ends" rule and its count were **withdrawn**: the first would cap an approach leg at the next regime's limit and destroy the profile's anticipation. **The adaptive grid is re-scoped**: it is no longer a rework of `avoid` but the algorithm of a **second engine named `evolutive`**, planned in [`261004_FEAT_PLN_Route_evolutive-engine.md`](261004_FEAT_PLN_Route_evolutive-engine.md) — a third `RouteEngineChoice` row built by composition, one neutral step reading on `RouteUpdate`, one pass pipeline extracted with a grid provider and a second-pass region provider, and `avoid` proved unchanged by its suite and its drawn line. The algorithm itself is [`261004_FEAT_PLN_Route_hybrid-grid.md`](261004_FEAT_PLN_Route_hybrid-grid.md): fine 20 m beside the coast and the depth gate, coarse 100 m in open water, ratio 1 : 5 derived from a metres fine cell, the second pass a chain of 300 m boxes on one lattice, and one seam helper pricing a crossing from the two cell centres. **`avoid`'s grid, keys and ladder are untouched**; its clock's step is what shipped, and its own residue stands — one test red, `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, against the file's `0.3333` experiment value. The walk's item 15 — the fine band's Change 4 — is **superseded** by these plans, which settle the fine cell in metres, the band's width and the ratio.

## The run after the bake (2026-10-04, 01:52 to 07:23)

- **The clock's step fix**: `sampleM` required at half the finest cell the engine walks, `clockSampleM` as the clock's own rule, all four readers of a limit agreeing that a non-finite answer is none, three tests.
- **The second engine**: `RouteEvolutiveEngine` by composition and the `route_engine_evolutive` row, both locales.
- **Every step reports**: `RouteStepReading` on `RouteUpdate`, the avoid engine's three figures, the view model's one insertion, the acquisition panel's header line, and a test that pins the emission.
- **Phase 3's seam**: `RouteGridPlan` holds an algorithm's two decisions — the first walk's cell and the second pass's region — with `UniformGridPlan` as `avoid`'s default and `evolutive` carrying a plan of its own. Landing it additively rather than by moving the pipeline is a recorded deviation, with its reason.
- **The gate**: the spatial suite runs **190 tests with one red** — `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, `avoid`'s own experiment residue, red before this run.
- **D4a — the saved offsets are the plan's own**: `TrackFromCourse.build` accumulates each leg's `durationSec` into `timeOffsetMs` and stamps each vertex with its leg's made-good speed, so `routePlanOf`'s inverse recovers the leg seconds exactly bar a per-leg millisecond flooring.
- **D4b — the early line is paced by design, and the likeliest symptom**: `partialPlanOf` times every leg at the pace with no zone read, so the drawing line and the early-saved line print an optimistic ETA.
- **D4c — a followed stored route carries no speeds**: `routePlanOf` builds times alone, and they are the times the pure-leg clock wrote, so the two agree by construction.
- **The seam widened and the properties comment corrected** (07:23): `RouteGridPlan` now hands back `firstWalkGrid(...)` and `secondPassRegions(...)` — a `GridTile` list and a region list — while `UniformGridPlan` still answers one tile over the whole corridor and the same widened line box, so `avoid` is unmoved; the two-resolution comment in `maro.properties` now states the single `cellM` and names `route.avoid.grid.fineRatio` as readerless, both ratio keys staying in place.
- **Not started**: the pipeline extraction and Phase 5's adaptive grid — the widened seam is what the lattice needed first, and the extraction of the 1 500-line class waits for its own order.

## Target Files

- `xTrack/Route/261004_FEAT_PLN_Route_hybrid-grid.md` — the adaptive grid's design, amended and re-scoped this session
- `xTrack/Route/261004_FEAT_PLN_Route_evolutive-engine.md` — the second engine: the code map, the speed contract's inheritance, the readings and the pinned mechanisms, new this session
- `xTrack/Route/261004_FEAT_PLN_Route_speed-attribution.md` — the clock's step fix, implemented in Phases 1 to 3 with two landed tests, Phase 4's three checks owed
- `app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt`, `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the splitter and both clocks, and the engine's three clock sites
- `xTrack/Route/FEAT_DSC_Route.md` — the feature doc: its Docs pointers and the superseded Change 4 bullet
- (prior) `app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt`, `RouteCornerPass.kt` — the clock the speed fix edits and the corner pass

## Next Step

The seam widening landed at 07:23 — [`RouteGridPlan.kt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteGridPlan.kt:22) answers `firstWalkGrid(...)` and `secondPassRegions(...)` with `UniformGridPlan` still returning one tile and one box, so `avoid`'s answers are unmoved: the spatial suite runs 190 tests with one red and two skipped, the red being its own parked ratio residue. What remains is the pipeline extraction of [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:397) with `avoid` proved unchanged, then the adaptive grid in the grid plan's own phase order.
