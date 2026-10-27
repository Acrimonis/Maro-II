<!-- scope: feature -->
# Route — the search's speed: remediation plan

**Date:** 2026-10-27 · **Status:** waves A–D and P3 implemented (`## Implemented`); B1c and wave E open ·
**Order:** the user's word of 2026-10-27: *"write a remediation plan, review, update, report on plan"*, on the
evaluation of the same day, then `#implement`.

**Evidence and findings:** [`261027_FEAT_PLN_Route_search-perf-evaluation.md`](261027_FEAT_PLN_Route_search-perf-evaluation.md)
(the budget in its §2, the findings F1–F15 in §3, the exclusions in §4, the health items in §5). This file is
the work; that file is the reason. The two are one topic and one day; the plan is kept apart because it has
its own lifecycle (phases, acceptance, `## Implemented`) and the evaluation is its fixed record.

**The one rule every phase obeys — no regression:** the drawn line, the per-leg clock, the slow-water
seconds and the crown are **byte-identical** before and after each phase on the fixtures named in §7, and the
device pass is the user's own. Where a phase corrects a defect (A0), the only lines that move are lines that
were drawn over water the cache had wrongly painted. A phase that cannot prove that is not shipped as part of
this plan.

## 1. Shape of the work

Five waves, each shippable alone, ordered by measured weight and blast radius. Every wave ends green on the
scoped suite and `compileDebugKotlin`, carries its own fixture that pins the line, and leaves the trace
format unmoved except where a wave adds a counter (named where it does).

| Wave | Findings | Touches | Kind |
|---|---|---|---|
| A — the tile cache hits | F15, F1, F2, F3 | `RouteGridBuilder`, `MultipassWorld`, `CoastlineRepository`, `TileKey`, `MapRouteEffects` | two defects |
| B — the walk's proofs and memo | F4, F6 | `MultipassPull`, `RoutePassRunner`, `RouteFinePass`, `RouteApproachPass`, `RoutePassPrimitives` | counter, then a constant; threading |
| C — the shared crossing raster | F5 | `RouteFinePass`, `RoutePassModels`, `MultipassGrid`, `MultipassPull` | exact, one memo |
| D — the post-search tail | F7, F8, F11 | `RouteCornerPass`, `RouteEta`, `RouteAvoidEngine`, `RoutePassModels`, `ZoneGeometry`, `ShallowEdgeIndex` | exact, reads moved |
| E — the search and the raster | F9, F10 | `WalkLattice`, `MultipassSearch`, `MultipassGrid` | exact, structural |
| P3 — the instrument | F12 | `RouteAvoidEngine` | reporting only |

Not in this plan (recorded in the evaluation §4 and §3 F13/F14): any kernel or metric change, the incumbent
A\*, overlapping the two pull walks, the berth proof's reach. The code-health items of the evaluation §5 ride
the wave that touches their file and are listed per wave; the ones no wave touches are §8.

## 2. Wave A — the tile cache hits

**A0 — a tile is a function of the lattice, the world and its key alone (F15).** *First, because A1–A3 make
cross-box hits the norm and would otherwise serve wrong water.*
- `buildFineTile` harvests **for itself**: `world.segmentsIn(reach)` and `world.openCoastIn(reach)` over the
  tile's grown block inflated by the largest reach any of its sweeps uses — `max(marginM, bandReachM(width,
  outsideMargin), the mask's upper edge, zoneOutsideMarginM, the collar reach)` with the degree slack of A2 —
  and no longer takes `edges`/`openCoast` from its caller. `fineTilesOver` and `fineWindowGrid` stop passing
  them.
- Exact inside the building box, by the same argument A2 makes for rings (a segment beyond the largest reach
  writes no cell of the block; a closed ring wholly outside toggles an even count or none). **One thing to
  pin, not assume:** the open coast is clipped by `openCoastPolylinesIn` and closed by `closeOpenCoast` at the
  region's north, so a polyline cut at a smaller box's edge is closed from different cut points; the parity
  of every cell inside the block must come out the same. The fixture below reads it rather than arguing it.
- Acceptance: (1) for every tile fully inside the island fixture's corridor, the tile built from the per-tile
  harvest equals the tile built from the corridor harvest, every `FineTile` array compared; (2) an island
  standing outside corridor 1 but inside a block straddling its edge is read as land by an arm over
  corridor 2 through `ownTiles` and through the store, where today it reads as water; (3) the engine fixtures
  unmoved.
- Health riding A0: the `FineLayerSpec` of A2 carries the reach, so the harvest's box is derived in one place.

**A1 — a content stamp for the coastline (F1).**
- `LiveMultipassWorld.coastlineGenerationStamp` answers a stamp of the **data**, not of the file and not of the
  object: the cache copy is `CoastlineSerializer.serialize(data)` re-encoded with the 300 m band added, so the
  asset's bytes and the cache's differ while the data is one. Two candidates, the implementer's choice: the
  coastline metadata's own version notion (its fetch timestamp) where it is proven identical across the asset
  and the cache, else a 64-bit hash over `allSegments`' coordinates taken **once** at index build and kept on
  the repository beside `spatialIndex`, published together. `CoastlineSpatialIndex` keeps no `hashCode`
  override (it is never a key).
- Exact: `TileKey` is unchanged in shape; only the number it carries is now the same across launches.
- Acceptance: a unit test that a repository loaded from the asset and one loaded from the **re-encoded cache**
  of the same data answer one stamp, and different data two; `FineTileCodec.entryName` equal / unequal
  accordingly. (The review hop's note: a two-repositories-same-bytes test would pass while the device still
  misses on its second launch — the cache-copy case is the one that matters.)

**A2 — the tile key names the tile's own water (F2).**
- `fineTileKey` takes `zoneRings` filtered to the rings whose bbox meets the tile's grown block inflated by
  `zoneOutsideMarginM`, and `markers` filtered to those whose body plus `proximityM` meets the same box (a
  `RouteMarkerGeometry.bbox(reachM)` helper, new) — **over-inclusive by construction**: the inflation is taken
  in degrees at the latitude that makes it widest, scaled by 1.05 and grown by two fine cells, because
  `forEachCellNear` expands its bbox at the lattice's reference latitude while the distance is measured at
  the points' own, and an edge just past a degree-exact inflation can still stand within the margin by
  distance; under-inclusion could move a collar byte, over-inclusion never can. The filtered lists are
  sorted by id (canonical order) and are what `buildFineTile` hands `rasterizeWindow`, so the key and the fill
  agree. `zoneGenerationStamp` and `excludedZoneIdSet` **leave the key**: the filtered ring list carries their
  whole content for this tile, and keeping them would still invalidate every tile on any zone edit anywhere.
- Exact, by the fill's own arithmetic: a ring whose bbox misses the raster's box contributes no crossing on any
  row inside the box, or an even count on one side of it — `fillScanlineEvenOdd` toggles an even number of
  times before column 0 and the cell state is unchanged; the collar sweep writes nothing for a segment
  further than the margin; a marker whose outer edge misses the box prices no cell of it (`collarFactor` is 0
  beyond `coreM + proximityM`); the zone fill writes limits only, which neither the gate relaxation nor the
  mask reads.
- Acceptance: a fixture with three zones (one over the tile, one meeting only its halo, one far away) and two
  markers (one reaching, one not) rasterises **byte-identical** tiles through the filtered and the unfiltered
  lists — every `FineTile` array compared — and the filtered key differs from the unfiltered only in the
  dropped entries; a ring edge placed at the margin threshold on a tile **north of the lattice's reference
  latitude** stays in the filtered list; two corridors over the same water with different far zones produce
  one tile name.
- Health riding A2: the fine-layer bundle derived twice (`buildLayeredGrid`, `fineTilesOver`) becomes one
  `FineLayerSpec` value derived once per arm — the margin, the zone margin, the priced rings, the band law,
  the mask, the min depth, the gate, the collar reach, the marker strength, the harvest reach of A0, **and the
  stamps and the marker list read once** (evaluation F11's per-tile re-reads). `fineWindowGrid`,
  `fineTileKey` and `buildFineTile` take the spec and the tile coordinates and nothing else.

**A3 — the pre-warm waits for a complete world (F3).**
- `MapRoutePrewarmEffect`'s `worldReady` folds in `zonesLoaded && markersLoaded`; `markersLoaded` exists
  (`MarkersViewModel.markersLoaded`), `zonesLoaded` does not — the zone source gains one, true once its first
  load has answered (empty included), the same shape as the markers' flag. **Gap named:** which class owns the
  zone list's load is the RegulatedZones feature's; the flag is specified here, its home is theirs.
- Exact: the pre-warm builds the same tiles later, not different ones.
- Acceptance: a test on the gate predicate; on the device the first `PREWARM done` of a launch reads
  `built=N` and the next cycle over the same area reads `warm=N built=0`.

**Wave A acceptance as a whole:** on a device pass, a launch after a warm launch prints `STORE load … hit=true`
for the arm's tiles and the pre-warm's first cycle prints `warm=N`; the fine layer's `GRID layer=fine … ms`
on a cold-process arming over warmed water reads tens of ms, not thousands. The drawn line on the measured
pair is unmoved (its tiles lie inside its own corridor, so A0 changes none of its bytes), pinned by A0's and
A2's fixtures.

## 3. Wave B — the walk's proofs and memo

**B1a — the hard-read counter, shipped first (F4's evidence).**
- `PullTiming.hardReads`, incremented in `readHard` on the miss side exactly as `priceReads` is in
  `readPrice`; printed on `PULL`, `FINAL`, `FINE PULL` and `FINE settled` beside `priceReads`. Reporting only.
- Acceptance: the counter equals the memo-less read count on `AvoidPriceWalkTest`'s fixture; a device
  capture of the measured pair, the user's own, reads it per rung.

**B1b — two steps, two names.**
- `PullSetup.coarseStepM` is the clearance **proof** step and nothing else. `RouteApproachPass` reads it today
  as a **geometry** step — its candidate radius gate, its scale-loop floor and `hasSlowWater`'s lattice — so
  it gains its own `stepM` parameter, handed `fineCellM` by `refineOriented`, and reads the setup's step no
  more. Exact: the approach pass's arithmetic is handed the value it reads today.
- Acceptance: `RouteApproachPassTest` builds its own setup and would not see the runner's change, so the pin
  is on the fine-pass fixture: the refined line unchanged with the proof step at 20 and at 100.

**B1c — the clearance proof's step above the sampling step (F4).**
- Three sites: `RoutePassRunner.runPass` (`coarseStepM = tailCellM` → the proof step; the comment about "both
  shipped cells" goes), `RouteFinePass.refineOriented` and `RouteFinePass.solveCrossing` (`coarseStepM =
  fineCellM` → the proof step). `priceStepM` stays `cellM` everywhere; the fine sampling step stays
  `clearanceStep(marginM)`.
- **The value is chosen from B1a's capture, not assumed.** At a proof step `s` a chord pays `dist / s` coarse
  reads plus a fine read wherever the coarse mark read under `margin + s / 2`; open water pays `25 / s` of
  today's reads, a stretch standing between 62.5 m and `50 + s / 2` off the coast pays up to a quarter more.
  The candidates are 50 m (`2 × sampleStep`, the conservative stop: proofs at 75 m, half the open-water
  reads) and 100 m (the interior cell, `avoid`'s own: a quarter of the open-water reads, proofs at 100 m).
  The measured pair's band water is ~6 % of its line, so both should win; the capture says which by how much.
  **No new key**: the step is the interior cell or a derived constant of the walk, never a `*.properties`
  value, since no user-visible behaviour moves with it.
- Exact: `legClearCause`'s verdict reads the fine marks alone — refused iff a non-waived fine mark is
  `hardBlocked` or reads under `marginM` with its covering coarse mark unproved; a coarse mark decides only
  whether that fine read is *skipped*, and skips it only where its own reading proves the fine mark clear by
  the 1-Lipschitz bound (`provedClearM = marginM + coarseStep / 2`), every fine mark standing within half a
  coarse step of its coarse mark. `gateRefuses` walks `GATE_MARK_STEP_M`, `marginWaived` and the price walk
  never read the step; the snap, the straightener, the sweep and the nudge reach it only through
  `legClearCause`; `avoid` already runs 100.
- Acceptance: a direct test — `MultipassPull.pull` and `sweep` over the island fixture's cell path at a proof
  step of 20 and of the chosen value answer **the same list**, point for point, and `hardReads` is **not
  higher** on the fixture and **lower on the capture**. `AvoidPriceWalkTest` asserts the `FINAL` line's
  `stepM=` equals the 20 m band cell (its lines 92–95) and is rewritten to the chosen step in the same
  commit; the engines doc's readings paragraph names the change. The existing engine and symmetry tests stay
  green unmoved.

**B2 — one memo per pass, one per fine pass (F6, the 2026-10-14 plan's P2).**
- `runPass`: one `MarkMemo` created beside `setup`, handed to both `pullBothWays` calls and to `snapToCorners`
  (which gains a `memo` parameter and folds it into its context). `refineOriented`: `fineGuard` is built
  **before** the zone loop and passed **into** `solveCrossing` as its `guard` (today it builds an identical
  field of its own, after the loop); one `MarkMemo` threaded through the local pull, the snap, the settled
  pull, the sweep (already shared), `RouteApproachPass.refine` and `straightenLine` (both read
  `setup.context()` — they gain the memo the same way `snapToCorners` does). `solveCrossing`'s own pulls share
  that memo; its `base` field is a different identity and is read by no pull.
- Exact: a memo hit returns the field's own double for the same point under the same field identity; the
  price table stores the raw field rate and `berthRate` is added outside it on every read; `fieldOnly()`
  stores the same raw value; the edge tables' reach is constant within a pass; `reversed()` keeps the
  identity; the field is built once per pass so `bind` never wipes mid-pass; no decision reads a counter.
- Acceptance: the pass fixtures answer the same lines; `AvoidPriceWalkTest`'s relational assertions hold and
  its "one memo per pull through the default" case stands (the defaults are kept); a new test pins that the
  final pull over the snapped line reads fewer field prices with the shared memo than without and the same
  line. The device `FINAL` line's `memoPriceHits` now includes the first pull's hits (cumulative counters) —
  noted in `FEAT_DOC_Route_engines.md`'s readings paragraph.
- Health riding B2: `PullSetup` construction and its three-site commentary become one
  `pullSetupFor(ctx, field, lambda, proofStepM)` in `RoutePassPrimitives`; the `zonePriceSec` lambda wrapping
  `slowWaterPriceAt` becomes one `zonePriceFor(pace, lambda)`.

## 4. Wave C — the shared crossing raster

**C1 — one raster per arm per zone (F5).**
- `GridContext` gains `crossingRasters: CrossingRasterMemo` (fresh per context, like `probe`): a `Mutex` and
  a map from `(zone.id, box, fineCellM)` to a **completed** `MultipassGrid`. `solveCrossing` asks it for the
  zone's box: the first rung builds under the lock, stores, and every rung takes `grid.copy()` (a new
  `MultipassGrid.copy()` — `blockedCopy` without its loop) before its own two `forceFree`s. A build cancelled
  under the lock stores nothing, so the next rung builds for itself; nothing half-built is ever shared. The
  probe's lock is taken after the fine pass returns, so no two locks nest.
- Exact: the raster's inputs are λ-free — `base` carries the coast wall, the gate and the markers and `lambda`
  reaches only the priced branch it does not take; the band and the rings ride the grid as limits; the box
  is the zone's own, inflated and clamped to the corridor, so three rungs ask for one grid; `forceFree` ×2 is
  the only write after the build and lands on the copy; the search and its step guard read only; the copy is
  the seven arrays verbatim; the search, pulls, snap and comparison are untouched.
- **Memory, named:** the crossing box has no `withinBudget` today, and the memo keeps one full-box 20 m grid
  alive beside the three per-rung copies (four at the peak instead of three). The memo sizes the box in cells
  and bypasses itself above a ceiling (the existing `routeWalkMaxCells` divided by three, a derived bound,
  not a key), and the stored grid is released once every rung of the arm has taken its copy (the holder's
  in-flight count already knows how many) — or, simpler, once three copies were taken.
- Cost moved, not added: the siblings wait on the lock for one build instead of running three on contended
  cores; the wall of the slowest rung cannot grow (its own build took at least as long).
- Acceptance: a fixture with a priced ring on the line asserts the three rungs' splice decisions and lines are
  unchanged against the recorded fixture, and a build counter (test seam) reads 1 per zone per arm.

**C2 — the pulled line carries its own cost (F5, small).**
- `MultipassPull.pull` gains a sibling returning `PulledLine(points, totalSec)`: the accepted chords'
  `totalSec` summed left to right, **plus** the chord committed on the `probe == anchor + 1` refusal, which
  today skips `priceRefusal` on LAND and so never had its `totalSec` computed — it is priced at the commit —
  and the whole prefix where `pathTotalSec` is null. `solveCrossing` reads it for `localCost`; every other
  caller keeps the list-returning `pull`.
- Exact: both the sum and `lineCostSec` read the field's own double (memo hit or fresh) per mark, left to
  right, so the two are bit-identical; `coarseCost` stays one short walk.
- Health riding C: `solveCrossing`'s 24 parameters become `(ctx, line, zone, start, aim, lambda, guard, memo,
  refusals, checkCancelled, trace)` — the rest is read off `GridContext`.

## 5. Wave D — the post-search tail

**D1 — the corner pass reads a bbox-indexed zone set (F7).**
- `GridContext` gains `worldZoneSet: ZoneSet` built **over the world's whole non-excluded zone list** once per
  arm (`zonesProvider()` filtered by the excluded ids, the same list `zoneLimitKnAt` walks today), and
  `RouteCornerPass.round` takes it; `cornerInZone`, `zoneExcused` and `Walls.zoneBlocker` read
  `worldZoneSet.strictestLimitKnAt(lat, lon) != null`.
- Why the whole list and not the corridor's: a bow reaches a full leg (`route.turn.reachFraction=1.0`), a
  pair-merge crossing lies up to a full leg beyond the line, the A\* centres on the corridor snapped outward
  and the tangent corners stand off edges that merely overlap the box — so a sampled point is **not** bounded
  by the corridor and a corridor-bounded set could answer differently. Over the whole list the read is exact
  for any point, and the per-zone bbox reject is the whole saving; the ungated list is the right one, since
  the pass asks the price law's read (`zoneLimitKnAt`), not the switch-gated `ctx.zones`.
- Exact: `ZoneSet.strictestLimitKnAt` equals `strictestLimitKnAt` over the same excluded-filtered list — a box
  miss is a containment miss — and `!= null` is containment either way; the provider and the settings are
  read once per arm instead of per sample, under the world's own between-solves stability contract.
- Acceptance: the corner fixtures' verdicts and points are unchanged; a test with a zone outside the corridor
  but inside a leg's reach asserts the same `BOW_ZONE` verdict through the set as through the world.

**D2 — one composite per attempt, no boxed pairs (F7).**
- `composite` is split into the end-state update (what `tangent` needs: the `x`, `y`, `h` updates, which never
  read the point list) and the point loop; `firstBlocker` and the bisections call the end-state function,
  the final `Attempt` builds the points once and reads `.tangent` off the same `Composite`. `x`, `y` go to two
  `DoubleArray`s; `rAndL` returns into a two-slot `DoubleArray`. `turnDegreesAt` is computed once per vertex
  into a `DoubleArray` **per line instance** (`line` and `corners` are two lines).
- **Dropped from the plan** (review): `significantLegM` as a prefix-sum difference — it sums consecutive
  haversines outward and `P[j] − P[i]` re-associates; its result feeds `reachAsk`, `room` and the bisection
  thresholds, so the last bits matter. A few legs per bend; nothing to save.
- Exact: the same floating operations in the same order per point; the end-state function is the loop's last
  state, lifted — pinned by asserting equality of `tangent` from both paths on every bend of the corner
  fixtures.

**D3 — the clock's one walk (F8).**
- `timeLineWithProfile` records per split leg `lengthM` and the **raw** `limitAt(mid)` (a `Double?`, not the
  capped `ceilingMpsAt`), `inZone(mid)`, `inBand(mid)`, and per vertex the raw `limitAt(v)` and the raw
  ceiling, carried on `TimedLine` as a defaulted `LegFacts?` field; `slowTimeByLimit`, `rampSlowSeconds`
  (its `servedLimitKn` and `cappedByTheBoat` derive from the raw values), `slowShares`, `slowMetres`,
  `zoneSlowShare` and `polylineLengthM` read the arrays when present and walk as today when absent (a
  `TimedLine` built elsewhere). `durationSec` becomes a cached `val`.
- **What stays live:** `insideSeconds` samples eight points plus a 40-step bisection per slow leg — not
  midpoint reads — so its coast queries are not cached; the gain is the five midpoint walks, not the whole tail.
- Exact: the same `midpoint`, the same `haversine`, the same `limitAt`/`inZone`/`inBand` at the same points,
  each read once and stored raw; the sums are the same doubles in the same order; no test compares
  `TimedLine` by equality.
- Acceptance: every `RouteEta` fixture unchanged; a new test asserts `slowTimeByLimit` and `rampSlowSeconds`
  with and without `LegFacts` answer equal lists.

**D4 — small, exact (F11).**
- `GridContext` carries **two** sets, because the engine reads two today: `zoneSet` over the ungated
  `speedZonesIn(box)` (the clock's and `crossedSlowZoneNames`'s) and `pricedZoneSet` over the switch-gated
  `ctx.zones` (the share split's `inZone`); replacing the per-rung `speedZonesIn(ctx.box)` and the two
  `inZone(ctx.zones)` constructions with the matching set. With `route.avoid.speedZone.enabled=false` the two
  differ and the crown reads the gated one — one set would move it.
- `lineEntersZone` rejects a leg whose own lat/lon box misses the zone's bbox before sampling it (samples are
  lat/lon-linear on the chord, so a box miss has no sample inside); the vertex check stays.
- `ShallowEdgeIndex.chunkAt`: a last-chunk cache on the **session-shared** index is one `@Volatile` immutable
  `(key, chunk)` pair or a per-query local — never two fields written apart. The `cos` hoist of the evaluation
  is already in place per call and is dropped from the plan.

## 6. Wave E — the search and the raster

**E1 — the flattened walk's values (F9).**
- `WalkWindows` fills, **per walk** from the arm's own windows (one O(n) pass; the end discs write cells per
  arm, so nothing of this rides the shape cache), primitive per-slot arrays: `passable`, `sourceCostSec`,
  `limitKn`, `collarLimitKn`, `bandCollarLimitKn`, `gateDistanceM`, `cellSizeM`. `relax` and the step
  guard's stored-distance proof read the arrays.
- **Dropped from the plan** (review): the precomputed neighbour table. The neighbourhood is not a function of
  the shape — `supersedingSlot → fineCopyOf` and the copy's way back read passability, which the end discs
  change per arm — and each slot's `forEachStep` runs once per expansion, so a per-walk table costs what it
  saves. The measurable gain is the seven per-relaxation decodes; the enumeration stays as it is.
- Exact: the same values the grid answers today, read once; the enumeration order, `skip`, `edgeM`, the
  `cellSec` arithmetic and the heap's slot tie-break are untouched.
- Acceptance: on every search fixture, `path`, `expansions` and `costSec` equal the recorded values; the
  two-layer symmetry test unmoved. Memory, named: seven arrays × ~23–185 k slots × three rungs — a few MB at
  the ceiling, released with the walk.

**E2 — one coast sweep with three visit sets, and bucketed fills (F10).**
- `rasterizeFrame` walks `forEachCellNear` once per edge at the **largest** radius in force and computes the
  distance once per (cell, edge) — but keeps **three minima**, each taken only over the edges whose **own
  radius-bbox holds the cell** (the three sweeps' own visit sets), and applies the three writes from those at
  the same points of the sequence (land first, then the field pass, then the band limit and collar, then the
  mask). `forEachCellNear`'s bbox is in degrees at the lattice's reference latitude while the distance is at
  the points' own, so north of the reference a cell can stand outside an edge's 50 m bbox yet within 50 m by
  distance — today never visited, water; under a single 350 m visit set it would be visited and painted land.
  One minimum is not enough either (a cell at 60 m inside edge A's box and at 49 m in edge B's sliver). The
  three per-radius bbox tests are pure arithmetic on the cell's row and column.
- The even-odd fills bucket edges by the rows they span (sorted by `minLat`, an active list advanced per row)
  and sort a `DoubleArray` scratch in place — the same crossings in the same ascending order (`sorted()` and
  `DoubleArray.sort` share `Double`'s total order). The field pass reads the sources through primitive
  `(lat, lon)` fun interfaces.
- **Kept as is** (review): the blocked cell's second depth read — `gate.isShallow(centre)` goes through the
  edge index's own `depthAt` while the field's source goes through `depthMAt(world)`; they are the same read
  only if the two delegate without a blend, which no test pins. It stays until one does.
- Exact where stated: the distance per cell per edge is the **same** `pointToSegmentDistance` call, so
  `min ≤ margin`, `min ≤ width`, `in (width, reach]` and `min in band` are the same comparisons the three
  sweeps made over the same visit sets; band and collar writes are `min`-commutative and land is idempotent.
  **The kernel does not change** (evaluation F10's note).
- Acceptance: every raster fixture's grid arrays byte-identical (a test that rasterises the island fixture
  through the old and the new frame and compares all seven arrays — the old frame kept in the test source
  for the comparison, then removed), **plus a constructed sliver case**: the lattice's reference latitude
  south of the cell and a north–south segment at exactly `50 m × cos(ref) / cos(φ)`, which the island fixture
  (reference at its own mid-latitude) cannot reach by chance; the engine fixtures unmoved.

## 7. P3 — the instrument (F12)

- `solveAtLambda` times the corner pass, the profile clock, the probe and `rungSlowWater` on the same
  nullable-tally path `PullTiming` uses and prints them on the `DEVICE PASS` line beside `coarseMs` and
  `fineMs` — the timers **outside** every `trace { }` closure, so the `LINE` block's own readings (550–700 ms
  per rung with the tag on) are not inside the figure they describe.
- Acceptance: the parts of a `DEVICE PASS` sum to the rung's own wall within the trace block's cost; the line
  format is otherwise unmoved; no answer changes. Shipped with wave B's counter so one capture reads both.

## 8. The fixtures that pin "no regression"

- `RouteAvoidEngineTest.aPairArmedFromEitherEndDrawsOneLineAndOneCrown` and `RouteEvolutiveEngineTest` —
  the three rungs' points, clocks and crown on the island fixture, before and after every wave.
- `AvoidPriceWalkTest`, `CanonicalChordProofsTest`, `AvoidSweepWindowTest` — the walk's relational
  invariants (marks = lattice count, memo reads < memo-less reads); `AvoidPriceWalkTest`'s `stepM=` line
  assertion rewritten in B1c's commit.
- The per-wave byte-identity tests named above (A0 tiles across harvests and across corridors, A2 tiles
  across filters and the north-of-reference edge, B1b/B1c lines, C1 splices, D1 reach, D2 tangents, D3 slow
  tables, E1 paths, E2 grids and the sliver).
- `RouteDirectionSymmetryTest` over the baked region (`-Dmaro.prebake=true`) on a machine with the bake —
  the 24-pair survey predicted at 0.0 m on every stage row; it is the one check that reads the real coast.
- The device pass, the user's own: the same pair as the evaluation's budget (Village Englouti → Feu
  Artifice, 30 kn, `evolutive`), its three `LINE distance=…m duration=…s` figures and the crown unmoved,
  `hardReads` per rung before B1c, and the `RUNG firstMs` figures beside the evaluation's §2 table.

## 9. Health items no wave touches

- `RouteEvolutiveEngine`: the registry row constructs `RouteAvoidEngine(…, EvolutiveGridPlan)` directly; the
  class and `RouteEvolutiveEngineTest`'s delegation cases go (its line cases move to the avoid test with the
  plan argument).
- Dead members (evaluation §5): `SeamNeighbours.acrossFromCoarse`, `WalkWindows.crossLayerSlots`,
  `passableCount()`, `RouteGridBuilder.fineTileBoxes` and the 5-argument `fineWindowBoxes`,
  `StoreFullness.atCeiling`, `RouteAvoidEngine.lineLengthM` (alias) — deleted with their test-only callers
  moved onto the production members; `sharesIndexWith` stays (D59).
- KDoc corrections (evaluation §5, eight items) — one pass, with the plan pointer repair: the 2026-10-14
  plan's `RouteSelectivePerfEvalTest` citation re-pointed at `AvoidPriceWalkTest`'s relations.
- `MultipassPull.M_PER_DEG = 111_320` is left as it is: changing it moves the straightener's epsilon test, a
  behaviour change.

## 10. Review — one independent read, 2026-10-27, and what it changed

The hop read the plan, the evaluation and every file the waves touch, and was briefed to break each "exact"
claim in source. Its findings and the plan's disposition, by severity:

| # | Finding | Disposition |
|---|---|---|
| R1 | **Blocks wave A.** A tile is rastered with the caller's corridor-clipped harvest but cached as a whole block; the key names neither; cross-box hits would serve wrong water. | **A0 added**, first in the wave; F15 added to the evaluation. |
| R2 | A1's "the cache copy is the same bytes" is false — the cache is re-encoded with the band added; a bytes stamp rebuilds every tile once more and the proposed test would not see it. | A1 stamps the **data**; the acceptance is the asset-versus-cache case. |
| R3 | A2's filter must over-include: the sweep's bbox is in reference-latitude degrees, the distance at the points' own. | A2 inflates with slack and pins a north-of-reference edge. |
| R4 | A2 keeps the world-wide zone stamp and excluded set in the key, so any zone edit still invalidates every tile. | Both leave the key; the filtered, id-sorted ring list carries their content. |
| R5 | **Blocks B1 as written.** `RouteApproachPass` reads `coarseStepM` as geometry (radius gate, scale floor, `hasSlowWater`), so 20 → 100 moves the line. | **B1b added**: two steps, two names; the approach pass keeps `fineCellM`. |
| R6 | B1's "tests stay green unmoved" is false (`AvoidPriceWalkTest` pins `stepM=20`); "strictly lower" is geometry-dependent — coastal water 62.5–100 m off pays up to a quarter more. | Counter first (**B1a**), value chosen from the capture, 50 m named as the conservative stop; the test rewritten in the same commit; the evaluation's F4 reworded from "inert". |
| R7 | C1 memory: no cell ceiling on the crossing box; the memo adds a fourth full grid at the peak. | Ceiling and release after the last copy, in C1. |
| R8 | C2's sum must price the LAND-committed chord and the null-prefix case, or it is not `lineCostSec`. | Written into C2. |
| R9 | **D1's reach is a full leg, not a fraction**, and the line itself leaves the corridor; a corridor-inflated set is unsound. | D1 builds the set over the **whole** non-excluded list; the saving is the bbox reject, unchanged. |
| R10 | D2's prefix-sum `significantLegM` re-associates and feeds thresholds. | Dropped. |
| R11 | D3 must store raw limits, not the capped ceiling; `insideSeconds` is not a midpoint read. | Written into D3; the gain statement narrowed. |
| R12 | D4 would merge the gated and ungated zone sets; with the price switch off the crown moves. | Two sets kept. |
| R13 | **Blocks E1 as written.** The neighbour table depends on per-arm passability; a shape-cached one is stale. | Table dropped; per-walk value arrays only. |
| R14 | **Blocks E2 as written.** One sweep at the largest radius changes the visit set (the sliver north of the reference); one minimum is not enough. | Three minima over the three visit sets; a constructed sliver fixture. |
| R15 | E2's blocked-cell re-read goes through a different depth door than the field's; not proven equal. | Kept until a test pins the two equal. |
| R16 | P3 had a row and no section; the last-chunk cache on a shared index needs one volatile pair. | §7 written; D4 amended. |

**Confirmed exact by the hop, as read in source:** A2's even-odd and marker arguments, B1c's verdict
structure and the absence of other readers of the proof step, B2's memo semantics, C1's λ-free raster and
copy, D1's set-versus-world read, D4's bbox reject, E1's enumeration order, E2's write order and crossing order.

**Strongest objection to the plan as updated:** wave A now carries a correctness fix (A0) whose "exact inside
the box" rests on the open-coast closure producing the same parity from a smaller clip, which is argued from
the design record (D52) rather than proven here; its fixture (1) is what proves it, and if it fails the tile
must instead be harvested at the corridor's own reach and keyed on the clip — more bytes per key, the same
cache. **Second:** B1c's gain is now conditional on a device reading the agent cannot take; if the capture
shows the coastal penalty eating the open-water saving at 100 m, 50 m is the fallback and the item may land
smaller than the evaluation's estimate.

## Implemented

- **2026-10-27 — waves A, B (B1a, B1b, B2), C, D and P3, on `feature/roooouuutttiiinnngggg`.** `compileDebugKotlin`,
  `assembleDebug` and the whole unit suite green (1 645 tests, 42 of them new or rewritten); the device pass
  is the user's own.
  - **A0 / A2** — a tile is built from **its own** harvest and water: `buildFineTile` reads the coast over the
    tile's grown box inflated by the spec's harvest reach with the filter's slack, and `tileWater` reads the
    zones and the costed markers from the **world** over the same reach (never a corridor's or the pre-warm
    box's list, which the fixture showed would miss a zone inside the tile but outside the box); the rings
    are keyed in id order. `FineLayerSpec` derives the fine layer once per arm for the arm and the pre-warm
    alike; `TileKey` lost the world-wide zone stamp and excluded set; `RouteMarkerGeometry.reachBox` and
    `reachWithSlack` are the filter's two homes. Pinned by `FineTileWaterTest` over a real
    `CoastlineSpatialIndex`: byte-identical tiles a harvest reach inside the corridor, the straddling block's
    island ribbon kept, one key from two corridors, the margin-plus-slack ring in the key and the far one out.
    **Found on the way:** the corridor-clipped harvest also missed coast just beyond the corridor's edge, so a
    block within the band's reach of the edge lacked that band collar even inside the box — the tile's own
    harvest now carries it (the evaluation's F15, as the fixture showed it).
  - **A1** — `CoastlineRepository.coastlineStamp`, an FNV-1a fold over the coast's points published with the
    index; `LiveMultipassWorld.coastlineGenerationStamp` answers it. `CoastlineStampTest` pins the
    asset-versus-re-encoded-cache case.
  - **A3** — `RegulatedZonesRepository.loaded` (true once the first load has answered, empty and failed
    included), `NavigationViewModel.speedZonesLoaded`, and the pre-warm's `worldReady` folds in the zone and
    marker lists.
  - **B1a** — `PullTiming.hardReads`, paid in `readHard` on the miss side alone; on the `PULL`, `FINAL`, `FINE
    PULL`, `FINE settled`, `FINE LOCAL PULL` and `PULL2`/`FINAL2` lines. `MarkMemo.hardOrNull`/`putHard` replace
    `hardDistance`. Pinned: paid plus served equals the bare walk's reads.
  - **B1b** — `RouteApproachPass.refine` takes its own `stepM` (the fine cell) and reads the setup's proof step
    no more; the runner's comment now states what the shipped 20 m band cell does to the proof.
  - **B1c — not landed, by the plan:** the proof step moves on a device reading of `hardReads`; `stepM=20`
    still prints and `AvoidPriceWalkTest` still pins it.
  - **B2** — one `MarkMemo` per pass in `runPass` (both pulls and the snap) and one per fine pass (the
    re-solves, both pulls, the sweep, the approach and the straightener), the guard field built once before
    the zone loop and handed into `solveCrossing`; `pullSetupFor` and `zonePriceFor` are the two setup
    sites' one home (the crossing's own setup still built by hand, see below). Pinned: the shared memo draws
    the same line and pays fewer price and hard reads. The memo's hit counts on a `FINAL` line are now the
    pass's, cumulative.
  - **C1** — `CrossingRasterMemo` on `GridContext`: the zone's λ-free raster built once per arm under a lock
    and copied per rung (`MultipassGrid.copy`), bypassed above a third of `route.walk.maxCells`; a `FINE
    RASTER` line says `shared=`/`built=`. `RouteAvoidEngine(shareCrossingRasters = false)` is the test seam.
    Pinned: one build per zone and grid, and the three rungs' points, clocks, tables and crown byte-equal
    with the sharing off. **Left:** the stored grid lives to the end of the arm (R7's release not done).
  - **C2** — `MultipassPull.pullWithCost` answers the pulled line's own `totalSec` (an immediate step the wall
    refused is priced at its commit); `solveCrossing` reads it instead of a third walk. A bare `pull` pays
    exactly what it paid (the cost is read only where wanted). Pinned against `lineCostSec` on a zigzag, a
    bend, a chord, and two walled paths that exercise both commit branches.
  - **D1** — `RouteCornerPass.round` takes a `zoneLimitKnAt` read; the engine hands `GridContext.worldZoneSet`
    (the world's whole non-excluded list, `MultipassWorld.allSpeedZones`, indexed) — a box reject ahead of
    every ring walk at every 2 m sample. Pinned: set and world draw the same points and verdicts.
  - **D2** — one composite per attempt: `composite(…, withPoints)` runs the end state alone for the fit
    bisections, the points ride two `DoubleArray`s, and every vertex's turn is read once (`turnsOf`) for the
    merge, the shares, the significant legs and the drawing tail. The prefix-sum leg was dropped (R10).
  - **D3** — `timeLineWithProfile` keeps `LegFacts` (lengths, midpoints, raw mid limits) on the `TimedLine`
    and `durationSec` is summed once; `rungSlowWater` reads the legs' water once (`LegWater`) for the
    per-limit table and the ramps. Pinned by `LegFactsTest` against the bare walk.
  - **D4** — `GridContext.zoneSet` (ungated) and `pricedZoneSet` (gated) replace the per-rung
    `speedZonesIn(box)` and the two `inZone(ctx.zones)` builds; `lineEntersZone` rejects a leg whose own box
    misses the ring's. **The edge index's last-chunk cache was built and withdrawn:** with it `GateGuardsTest`
    moved a clearance verdict when run alone (passing in the full suite), the cause not found in immutable
    chunks within the run; the lookup is as it was.
  - **P3** — `DEVICE PASS` carries `cornerMs`, `clockMs`, `probeMs`, `crossedMs` and `slowMs`, each around its
    own work and never a trace block; the line is emitted after the tail.
  - **Device capture (2026-10-27, first launch after install, a 15 km pair)** — no gain to see, and the
    capture says why: 22.3 s tap to last terminal, with `RouteApproachPass` taking 10.3 / 12.6 / 15.0 s of the
    17.1 / 19.7 / 21.7 s rungs (the evaluation's F17) — a stage no earlier capture contained and no wave
    touched; the store had been emptied by the install (88 loads, all misses, 88 saves), so wave A's
    cross-launch effect is unread. What the capture does show: the crossing raster built once and shared
    (317.6 ms, then 2.2 and 2.4 ms), the arm's fine layer assembled in 43 ms with no tile built, and the
    first pull's `hardReads` at 87–90 % of its marks — the reading B1c waits on.
  - **Review hop (2026-10-27)** — exact where present; fixed on its word: a bare `pull` no longer prices a
    two-point path, the dead `hardDistance` and two stale KDocs, `probeMs` split from `crossedMs`, the tile's
    water computed once per tile, the key's cap the raster's own, the rings sorted, the switches read off the
    spec, the C2 wall cases pinned, one tautological assertion dropped.
  - **Found and fixed on the way — the edge index held its raster weakly** (`shallowEdgesOver`): its chunks
    are traced lazily, so a chunk built after the collector took a raster nothing else held read every
    node as no data and the gate's wall vanished silently. `GateGuardsTest` flaked on it once the new
    allocation-heavy tile fixture ran before it in the same JVM (three different cases over four runs), and
    passed alone twice. The index now holds its raster; a replaced one is released when the next search or
    pre-warm tile replaces the index. Reachable in the app only across a depth regeneration during a search.
  - **Deferred, named:** wave E (the plan's own rule — the device budget after A–D decides); the
    `solveCrossing` parameter trim and its hand-built setup (its one direct test caller builds no context);
    `rAndL`'s boxed pair; the halo branch of `tileWater` has no fixture with a live gate; D2's tangent
    equality rests on the corner fixtures, `composite` being private.
