# Context Hydration — Route — 2026-10-04

**Last Bake:** 2026-10-04 01:50 UTC — written by `#bake`; absence means never baked

**Directive trace:** One session on `feature/avoid-adaptive-grid`, created at the user's `#new`: of the five covered classes none ran unasked — no dependency was added, no machine-shaped data file was opened, work started only on the user's explicit word, the device was never touched, and every claim about the code followed a read. One unfounded assertion was made and corrected in the same session — that the session carried no shell — where the truth is sharper: the shell exists and **Architect mode is what forbids it**, which is why the branch was created from a Code hop.

## State

**No code changed this session; the tree carries three plans and one new branch.** **The adaptive grid is re-scoped**: it is no longer a rework of `avoid` but the algorithm of a **second engine named `evolutive`**, planned in [`261004_FEAT_PLN_Route_evolutive-engine.md`](261004_FEAT_PLN_Route_evolutive-engine.md) — a third `RouteEngineChoice` row built by composition, one neutral step reading on `RouteUpdate`, one pass pipeline extracted with a grid provider and a second-pass region provider, and `avoid` proved unchanged by its suite and its drawn line. The algorithm itself is [`261004_FEAT_PLN_Route_hybrid-grid.md`](261004_FEAT_PLN_Route_hybrid-grid.md): fine 20 m beside the coast and the depth gate, coarse 100 m in open water, ratio 1 : 5 derived from a metres fine cell, the second pass a chain of 300 m boxes on one lattice, and one seam helper pricing a crossing from the two cell centres. **The per-point speed defect is located and planned** in [`261004_FEAT_PLN_Route_speed-attribution.md`](261004_FEAT_PLN_Route_speed-attribution.md): `BOUNDARY_SAMPLE_M = 25.0` is a constant tied to the retired 50 m cell, so a limit regime narrower than the step is invisible, and a leg's speed and its time both come from one midpoint read — the fix derives the step from the finest cell the engine walks, reads both ends, takes the slower one and counts the legs that disagreed. **`avoid` is untouched by all of it**, and its own residue stands: the suite compiles with one test red, `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell`, against the file's `0.3333` experiment value. The walk's item 15 — the fine band's Change 4 — is **superseded** by these plans, which settle the fine cell in metres, the band's width and the ratio.

## Target Files

- `xTrack/Route/261004_FEAT_PLN_Route_hybrid-grid.md` — the adaptive grid's design, amended and re-scoped this session
- `xTrack/Route/261004_FEAT_PLN_Route_evolutive-engine.md` — the second engine: the code map, the speed contract's inheritance, the readings and the pinned mechanisms, new this session
- `xTrack/Route/261004_FEAT_PLN_Route_speed-attribution.md` — the clock's speed fix, site by site, with its four tests, new this session
- `xTrack/Route/FEAT_DSC_Route.md` — the feature doc: its Docs pointers and the superseded Change 4 bullet
- (prior) `app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt`, `RouteCornerPass.kt` — the clock the speed fix edits and the corner pass

## Next Step

Run the speed fix first, because it edits `RouteEta` and three clock sites inside the class the extraction later moves; then the engine row, the reading and the extraction; the adaptive grid last.
