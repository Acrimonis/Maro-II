<!-- scope: feature -->
# Route — the search's speed: remediation plan

**Date:** 2026-10-27 · **Status:** waves A–D and P3 implemented (`## Implemented`); B1c and wave E open; wave F (§11): F0–F2 implemented, F3/F4 wait on F0's device reading ·
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

Five waves, each shippable alone, ordered by measured weight and blast radius; wave F (§11) was added after
them, on the approach pass the device found. Every wave ends green on the
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
| F — the approach pass's pricing | F17 | `RouteApproachPass`, `PullContext`, `MarkMemo`, `MultipassPull`, `ShallowEdgeIndex` | counter, then exact cuts |

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

## 11. Wave F — the approach pass's pricing (design, 2026-10-27)

**Order:** the user's word of 2026-10-27, *"design / plan pricing design"*, on the Route hydration's next step.
F0–F2 implemented on the user's `#implement` of the same day (`## Implemented`); F3 and F4 are design, waiting
on F0's device reading.

**Where the pass stands.** After the reorder (`## Implemented`, *prices first and proves only would-be
winners*) the approach pass is 6.9 / 9.1 / 12.8 s of the three rungs on the 15 km pair, about 85 % of it
pricing: 600–736 candidates a rung, each pricing up to two kilometre-long chords on geometry the memo has
never seen. The price walk pays a gate-edge read at **every mark it prices** (`readPrice` → `berthRate` →
`edgeM`) and at **every span or group it tries to prove** (`softClearanceM` → `berthClearanceM`, plus a far
read wherever the near one finds no edge): 43–85 k near and 16–31 k far reads a pass, the largest count on the
`APPROACH` line. The line counts reads; it does not say what a read costs — hence F0.

**The rule is the plan's own:** the drawn line, the clock, the slow-water seconds and the crown stay
byte-identical. Every lever below changes **which reads are paid, never a decision** — the berth's outer ramp
is priced from a span's midpoint wherever a proof passes, so a lever that moved one proof's verdict would move
a sum. Three levels: fewer evaluations (F1), fewer reads per chord (F2, F3), a cheaper read (F4).

**Every threshold is the law's, never the shipped keys'** (review W1–W3). The clamps allow an outer edge
under the margin (outer 0–500 m, margin 0–200 m), so the berth's rate is exactly zero only from
`quietM = max(outerM, marginM)`, where its clearance is `e − quietM`; every lever reads `quietM`, never
`outerM`. A comparison a lever makes at a boundary today's code does not make keeps a 1 m margin, so
rounding cannot flip it (W4).

| Phase | Level | Touches | Kind |
|---|---|---|---|
| F0 | instrument | `PullTiming`, `MarkMemo`, `readPrice`, `RouteApproachPass` | reporting only |
| F1 | evaluations | `RouteApproachPass` | exact, local |
| F2 | reads per proof | `PullContext`, the two proofs in `MultipassPull`, `MarkMemo` | exact, local |
| F3 | reads per mark | `MarkMemo`, `PullContext` | exact, structural |
| F4 | cost per read | `ShallowEdgeIndex` | exact, local |

**F0 — what a read costs.**
- Four clocks, each on the side of a read that pays it, where it is paid: the near and far edge reads inside
  `MarkMemo.edgeDistance` / `farEdgeDistance` — the memo gains the nanos beside its miss counts, since
  `PullContext.edgeM` cannot tell a hit from a miss (W5) — the field's price read on `readPrice`'s miss in
  `MultipassPull`, and the field's clearance read in the two proofs, which also gains a count.
- `edgeNone`, the near reads that found no edge, **split by caller**: the berth's (rate and proof), which F3
  can remove, and the wall proof's (`gateRefuses`), which it leaves (W6).
- **Each pass's own ms** beside its evaluations (`pass1Ms` / `pass2Ms`): pass 2's repeats take their price,
  edge and hard reads from the shared memo, so an evaluation count does not measure their time (W7).
- The `APPROACH` line prints them. Paid only where a timing is handed in; no answer changes.
- Acceptance: the clocks sum to no more than `priceMs + clearMs`, every read in `refine` sitting inside one of
  the two (confirmed by the review); the instrumented call answers as the bare one.
- Reading owed, the user's device pass: which read carries the price time, how many of the berth's near reads
  find nothing, and pass 2's own ms. It sizes F1 and chooses between F3 and F4.

**F1 — the second pass skips what the first left unchanged.**
- `refine` runs up to two passes, and pass 2 runs whenever pass 1 moved a bend — which every rung of the
  capture did (`moved=2`, and pass 2 runs only after a move). Pass 2 then searches every window again, yet a
  window's outcome is a function of its own points `work[from..to]`, the context and the bounds alone: a
  window pass 1 searched without a move, its points unchanged, cannot move in pass 2.
- A set of the windows searched without a move, keyed on a **copy** of their point lists — `work.subList` is
  a view the next splice would mutate inside the set (W8) — compared by `LatLng` equality, bit for bit on the
  doubles; pass 2 skips a window found in it. A window touching a moved bend holds a new point and is searched
  as before. Counted on the line as `repeats=`.
- Exact: the skipped search is the one pass 1 ran on the same inputs; the memo changes which reads are paid,
  never a value, and `slow()` is keyed on the pair.
- Size — **smaller than it looks** (W7): pass 2 searches the same points, so its price, edge and hard reads
  are already the memo's. F1 removes the residue — the field's clearance reads (no memo; F2 adds one),
  `hardBlocked`, the haversines and the table lookups — read on F0's `pass2Ms`.
- Acceptance: the six-water equivalence test with the skip on and off (a `skipRepeats` seam beside
  `incumbentFirst`) draws the same points, on a fixture where pass 1 moves a bend and pass 2 skips at least
  one window — asserted present, never counted.

**F2 — the berth's side of a proof is read only where it can decide.** A span or group proof passes where
`min(field, berth)` is finite and at least `halfM`. With the berth priced its clearance is always finite, so
the proof is `field ≥ halfM && berth ≥ halfM`, and three reads go:
- **Field side first.** Where the field's clearance is under `halfM` the proof fails whatever the berth says:
  no edge read.
- **The berth's ceiling.** The berth's clearance never exceeds `max(BERTH_PROOF_REACH_M, edgeReachM)` (a near
  reading's clearance is at most its own reach, a far one's at most `BERTH_PROOF_REACH_M`; 150 m on the shipped
  keys) — up to an ulp, so the cut is taken only **1 m above** it (W4). A span whose half-length is above that
  is never proved, so it splits with no edge read — every proof on the top levels of a kilometre chord.
- **The far read sized to the question.** Where the near read finds no edge and `halfM ≤
  BERTH_PROOF_REACH_M`, the far read reaches the lesser of `quietM + halfM + 1 m` and today's `outerM +
  BERTH_PROOF_REACH_M` — `quietM`, not `outerM`, because past the near reach the clearance is `e − marginM`
  wherever `outerM < marginM` (W1). A found value is the same double the long read finds (the box only grows
  with the reach) and is compared exactly as today; "none within" stands a metre past the boundary, so it
  passes the proof as the long read's answer would. The far table holds "clear to R" as a negative value
  (NaN stays the absent one), so a short "none" never answers a longer question.
- **The field's clearance memoised** (W15). `priceClearanceM` is a pure function of the field and the point,
  read at every proof and never held; a point-keyed table beside the price table answers the same double.
- Exact: the same boolean at every proof. A read skipped at a midpoint that a later walk prices is paid there
  instead, never twice. `berthClearanceM` itself is unchanged — `GateCoverageTest` pins its far-out 150 m
  directly (W12); the proofs call a new decision beside it.
- Acceptance: today's `softClearanceM` comparison kept in the test as the oracle and compared with the new
  decision over thousands of points and half-lengths on a berth-priced gate fixture (field-bound, berth-bound,
  both), under the shipped law **and** two the clamps allow — `outerM < marginM` with an inner ramp, and
  `outerM > 150 m` (W1–W3); the equivalence test and the island fixture's rungs unmoved; fewer edge reads on
  a long-chord fixture.

**F3 — one exact read answers its neighbourhood.**
- The edge distance is 1-Lipschitz: a point `r` metres from a read point `q` stands at least `e(q) − r` off
  the edge. Most marks need a threshold, not a value: the berth's rate is exactly `0.0` at or beyond
  `quietM` — not `outerM`: with an inner ramp and `outerM < marginM` the ramp prices the water between them
  (W2) — and a proof's berth side needs only `≥ halfM`.
- `MarkMemo` gains a lattice of exact reads at cell centres — cell about 50 m (twice the mark step), each read
  at reach `quietM + cell` — filled lazily per pass and bound to the field like the memo's tables. It is
  consulted **inside `berthRate` and the berth's side of the proof, never in `edgeM`**, whose exact value the
  wall proof (`gateRefuses`) reads (W11). A priced mark asks its cell's node first: where `L = e(q) − r −
  slack ≥ quietM`, or the node found no edge within its reach, the rate is exactly `0.0` and no read of its
  own is paid. The proof's berth side decides from the same bound only where `L ≥ quietM`, and as today never
  above `BERTH_PROOF_REACH_M`: its side holds where `min(L − quietM, BERTH_PROOF_REACH_M) ≥ halfM`, which is
  a lower bound on today's clearance whether the near read, the far read or neither finds the edge (W3).
  Otherwise the exact reads, as today.
- What still pays its own read: marks within about `quietM + cell` of the edge — the ramp's water, where the
  value matters.
- Exact: where the bound decides, the rate returned is the `0.0` the law returns and `raw + 0.0` is `raw`;
  every proof decides the same boolean. The slack covers the read's own metric (local equirectangular at the
  read point) against the haversine `r` — millimetres at these spans; 0.5 m is taken.
- Blast radius: `PullContext` is every walk's — the pulls, the sweep, the straightener — so the whole fine
  pass reads through the lattice; a `MarkMemo` seam turns it off.
- Acceptance: a property test over a traced gate fixture — thousands of points, half within a cell of the
  edge and straddling cell boundaries — in which no bound contradicts the exact read, under the shipped law
  and F2's two clamp-allowed laws; the island fixture's three rungs and `RouteEvolutiveEngineTest`
  byte-identical with the seam on and off; the equivalence test unmoved.

**F4 — the read itself, nearest first.**
- `ShallowEdgeIndex.distanceM` visits every quad of the box around the point. Visiting quads in rings outward
  and stopping once a ring's nearest possible distance exceeds the best found **by more than 1 m** returns the
  same minimum — the same `pointToSegmentM` doubles over every segment that could win. The ring's bound is
  measured in the **query latitude's** longitude metres, as the read itself measures, never
  `DepthLattice.cellLonM`, which is the raster's south edge's and its widest (W9).
- A chunk records its segments' extent, and **`distanceM` alone** skips a chunk whose segments all stand
  beyond reach — never `forEachSegmentNear`, whose other reader, `forEachSegmentIn`, hands the collar marking
  in `RouteGridBuilder` every segment of a quad meeting the box, beyond the box included (W10).
- It pays on reads that find an edge; a read that finds none still crosses its box, which F3 removes.
- Exact; it reaches every caller of the index through the same double — the raster's seeds, the corner pass,
  the trace, `RouteAvoidEngine`, `RouteMedialNudge` (`edgeM`, and `rayHitM`'s steps) and `BerthCarve` (W13).
- Acceptance: today's `distanceM` kept in the test, the two answering identical doubles over random points
  and reaches on a traced fixture, query points north of the raster's south edge among them.

**Order.** F0 and F1 ship together: F1 needs no reading to be exact, and the same capture reads what it
saved. F2 next. Then F3 where the berth's edge clocks carry the time and its `edgeNone` is most of its near
reads, F4 where reads that find an edge carry it — and neither where the field's own reads carry it, F2's
clearance memo being the one field-side lever here.

**Not in this wave.**
- Fewer directions, scales or passes, or a coarser price step — each moves a line.
- Pricing candidates in parallel — threading, excluded as in the evaluation's §4.
- A window-level travel bound (`haversine(a, e) / pace ≥ oldSec`) — exact, but it never fires on a window
  that crosses slow water, whose old legs already cost more than the straight leg's travel.
- F3's bound would also serve the gate's wall proof (`gateRefuses`) — clearance, not pricing.
- The two items the last order left (`addClearanceMarks` per mark, the tests-only `MarkMemo.price()`).
- **Found by the review, pre-existing, not in this wave (W14):** where `outerM < marginM`, the near reach
  (`edgeReachM`, anchored on `outerM` and the wall) can stop short of the ramp, so ramp water past `hardM +
  12 m` reads a rate of zero, and a far "none" claims 150 m against a margin boundary that may be nearer. The
  shipped keys (outer 50 m, margin 20 m) do not reach it; a fix moves lines and needs its own order.

**Gaps.** What a read costs, how many of the berth's near reads find nothing, and pass 2's own ms — F0's.
The depth raster's cell on the device, which sets both the box a read crosses and F3's cell — unread.

**Strongest objection.** After the review the cheap levers are smaller than they looked — F1's repeats are
already memo-served — so most of any gain rests on F3 or F4, the two with the widest reach: F3 changes the
read path every walk of the fine pass shares and stands on a Lipschitz bound with a numerical slack, F4 on a
ring bound every index caller reads through. Neither is sized until F0's capture, and one contradiction in
F3's property test makes it unsound as drawn.

### Review — one independent read, 2026-10-27, and what it changed

On the user's order to review, update and report. The hop read §11 and every file it touches, briefed to
break each "exact" claim in source. Its findings and the plan's disposition:

| # | Finding | Disposition |
|---|---|---|
| W1 | **Blocks F2 as written.** Past the near reach the clearance is `e − marginM` wherever `outerM < marginM`, which the clamps allow; "none within `outerM + halfM`" would pass a proof today's read fails. | `quietM = max(outerM, marginM)` in the reach, capped at today's long reach. |
| W2 | **Blocks F3 as written.** With an inner ramp and `outerM < marginM` the rate is positive between them; "zero at `outerM`" is false. | The threshold is `quietM`. |
| W3 | **Blocks F3 as written.** With `outerM > 150 m` a plain `L − outerM ≥ halfM` passes a proof today's capped far read fails. | The bound keeps the `BERTH_PROOF_REACH_M` cap and decides only from `quietM`. |
| W4 | F2's "none within R" and the ceiling cut can disagree with today's arithmetic by an ulp. | A 1 m margin on both. |
| W5 | F0's edge clocks cannot sit in `PullContext.edgeM` (no hit/miss split); `readPrice` is `MultipassPull`'s. | Clocks in `MarkMemo` and `readPrice`; Touches corrected. |
| W6 | `edgeNone` also counts the wall proof's reads, which F3 leaves. | Split by caller. |
| W7 | `evals=a/b` does not measure pass 2's time: its repeats are memo-served. | Each pass's own ms; F1's size restated. |
| W8 | A `subList` key mutates on the next splice. | The key is a copy. |
| W9 | F4's ring bound must use the query latitude's metres, and keep a margin. | Written into F4. |
| W10 | An extent skip in `forEachSegmentNear` changes the collar marking's segments. | The skip lives in `distanceM` alone. |
| W11 | The lattice must not sit in `edgeM`; "the near read would find nothing" is false where `hardM + 12 > outerM` (harmless to the rate). | Placed in `berthRate` and the proof's berth side; the claim dropped. |
| W12 | `GateCoverageTest` pins `berthClearanceM`'s 150 m. | `berthClearanceM` unchanged; named. |
| W13 | F4's callers were not all named. | Named. |
| W14 | Pre-existing: `edgeReachM` anchored on `outerM` misreads the ramp where `outerM < marginM`. | Recorded under *Not in this wave*. |
| W15 | A missing exact lever: `priceClearanceM` has no memo. | Added to F2. |

**Confirmed exact by the hop, as read in source:** F1's window reads only its points, the context and the
bounds, `slow()` is pair-keyed, pass 2 runs only after a move, and `LatLng` equality is bit-exact; F2's berth
clearance is always finite when priced, field-first gives the same boolean (NaN included), the ceiling holds
for every law the clamps allow up to an ulp, and a found value is the same double; F3's slack has wide
headroom (about a millimetre of drift), `λ × 0.0` is `+0.0`, and each memo serves one gate and berth per
field on one thread; F0's reads all sit inside `priceNs` or `clearNs`, `hasSlowWater` paying none of them;
F4's chunk extent is published safely through `computeIfAbsent`.

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
  - **Second device capture (2026-10-27, cache cleared, pre-warm awaited, the same pair)** — the first,
    repeated within noise (rungs 16.9 / 19.6 / 22.5 s; the approach pass 9.5 / 12.0 / 14.4 s, 56–64 % of them).
    It reads what B1c waited on: **86–90 % of the first pull's marks pay a hard read**, at 0.9–1.3 s of
    clearance a rung, so the proof step is worth about a second a rung at most (4–6 %) on this pair — B1c stays
    open but is not the lever. The cold pre-warm built and saved 302 tiles in 26 s and the arm built none. The
    store's cross-launch hit remains unread (the cache was cleared).
  - **The approach pass's own clocks (2026-10-27, on the user's "next step")** — F17's split, reporting only:
    the `APPROACH` line keeps its three figures and adds `ms`, `windows`, `evals`, `outside`, `refusedClear`,
    `clearMs` and `priceMs` (the clearance proofs and the priced chords apart), `slowMs`/`slowLegs` (the
    slow-water test), and the pull's read counts on the pass's context — `clearMarks` (fine marks tested, a
    depth step each), `hardReads`, `priceMarks`, `priceReads`, `edgeReads` (the new `MarkMemo.edgeMisses`),
    `farEdgeReads` and the memo's hits. Pinned: the line adds up (`evals = windows + candidates`) and the
    answer with the memo and the instrument is the bare call's. The device reading is the user's.
  - **Cross-launch store read (2026-10-27)** — a fresh process, the cache not cleared, loaded all 63 of the
    arm's tiles from the store with no miss, and the pre-warm built nothing: wave A's stamp holds across launches.
  - **The approach pass prices first and proves only would-be winners (2026-10-27, `#implement`)** —
    `RouteApproachPass.candidateSec`: a candidate is rejected on its two legs' travel, then on its first leg's
    cost plus the second's travel, then on its full cost, each against the window's best so far, and is proved
    clear only where it would win; the seeds keep the proof-first `replacementSec`. Exact because a candidate's
    cost is only ever compared below the best, no price is negative (every input clamped at zero or above) and
    rounding is monotone. Pinned: 40 seeded lines × 4 setups (free water, a second rock, a gate shoal, a
    marker-only price) answer the same points in both orders (`incumbentFirst = false` is the test seam).
    Device, the same start point to the centimetre: the pass 12.6 / 16.0 / 21.0 → 6.9 / 9.1 / 12.8 s, its
    clearance half −72–81 %, the rungs 20.7 / 24.3 / 28.6 → 15.9 / 18.4 / 21.4 s, tap to last route 29.9 →
    22.2 s, every line, clock and approach outcome identical. 1 647 tests and `apk-build.bat` green. The Ask
    hop's review returned after the commit (`b3ca6bf4`): **it could not break the claim** — `sec` is read only by
    `sec < bestSec`, the field price is non-negative by `priceAt`'s `cost > 0.0` filter, the travel terms are the
    same expression as `totalSec`'s, the bounds hold under rounding for finite, infinite and NaN costs, and no
    memo read changes with order. **Its findings — closed by the follow-up below, save the two it names:** the berth rate's ≥ 0 rests on its config
    clamp alone and no fixture prices a berth; the 40-case test proves neither cut path nor a refused would-be
    winner runs (sum `cutTravel`/`cutLeg` and a candidate-only refusal count, add a berth-priced setup); the box
    and proof blocks are copied between `candidateSec` and `replacementSec`; five KDocs or comments are stale
    (the class KDoc's "clamped" premise, "exact" → "sound", `priceNs`, `clearanceMarks` and the end disc, a test
    comment); the `APPROACH` line's `refusedClear` mixes seeds with candidates and `edgeReads`/`farEdgeReads`
    double-count far misses; `addClearanceMarks` runs per mark in the measured walk; and a pre-existing
    `MarkMemo.price()` (tests only) stores a per-cell price in the per-metre table.
  - **The review's follow-up (2026-10-27, `#implement`)** — the equivalence test now refines 8 seeded lines over
    6 waters (free, a rock standing on the free answer, a gate shoal, the shoal with a priced berth, a
    marker-only price, a mild slow disc where the best is mostly travel): 48 comparisons draw identical lines,
    and the travel cut, the first-leg cut, a losing full price, a refused would-be winner and a berth-priced
    search are each asserted **present**, never counted. `outside` and `proveClear` replace the copied corridor
    check and proof; `replacementSec` names its seeds, so the `APPROACH` line's `refusedClear` counts candidates
    and `seedsRefused` the seeds; `MarkMemo.farEdgeMisses` splits the far reads from `edgeMisses`, so
    `edgeReads + farEdgeReads` is the memo's paid edge reads with nothing counted twice (device: 42 885 + 16 156
    = the old 59 041 on λ 5, every line, clock and approach outcome identical to the run before). The class
    premise, "sound under rounding", `clearNs`/`priceNs`/`refusedClear`, `clearanceMarks` and `edgeHits` KDocs
    are corrected. 1 647 tests and `apk-build.bat` green. **Left, outside the order taken:**
    `addClearanceMarks` still runs per mark, and the tests-only `MarkMemo.price()` still stores a per-cell price
    in the per-metre table. **This follow-up's own Ask hop had not returned when it was committed on the user's
    word.**
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

- **2026-10-27 — wave F's F0, F1 and F2 (§11), on `feature/roooouuutttiiinnngggg`, `#implement`.** The whole
  unit suite green (1 650 tests, 3 new; the line test extended, the six waters shared) and `apk-build.bat` green; the device pass is the
  user's own.
  - **F0** — `PullTiming` carries the paid side of each read: `nearEdgeNanos`, `farEdgeNanos`,
    `fieldPriceNanos`, `fieldClearReads`/`fieldClearNanos`, and `berthEdgeNone`/`wallEdgeNone` (the near reads
    that found no edge, by caller). The two edge clocks sit in one home each (`nearEdgeRead`, `farEdgeRead`),
    which `MarkMemo`'s miss and a memo-less walk both read through, so a hit is never clocked. The `APPROACH`
    line prints them beside `repeats`, `passMs=a/b` and `passEvals=a/b`. Pinned: the pass evaluations sum to
    `evals`, and the read clocks sit inside `priceMs + clearMs`.
  - **F1** — `refine` keeps a set of the windows it searched without a move, keyed on a copy of their points,
    and the second pass skips them (`skipRepeats = false` is the test seam). Pinned by a line that runs the
    original approach down a straight chain into a second slow disc, its end walled in a thin ring so every
    replacement there is refused: pass 2 skips that crossing's windows and draws the full pass's line.
  - **F2** — `PullContext.softProves` replaces `softClearanceM` in the span and group proofs: the field's side
    first, no edge read past the berth's ceiling (`berthCeilingM`, plus 1 m), and the far read sized to
    `min(quietM + halfM + 1 m, outerM + 150)` (`berthClears`). `BerthLaw.quietM = max(outerM, marginM)` is the
    one home of the berth's quiet distance. `MarkMemo`'s far table holds found distances and "clear to R" as
    a negated reach; a new table memoises the field's clearance. `berthClearanceM` keeps its value (pinned by
    `GateCoverageTest`). Pinned by `SoftProofTest`: the new proof against the old comparison over 1 200
    points × 17 half-lengths, two fields and five laws (shipped; an outer edge inside the margin with an inner
    ramp; one past 150 m; a near clearance past 150 m; no berth), on one shared memo asked in shuffled order;
    and fewer edge reads on each priced law.
  - **Deviations from §11's text** — the clocks live on `PullTiming`, handed into the memo's two reads, not on
    the memo, so they are paid only where a timing is handed in; `pass1Ms`/`pass2Ms` shipped as `passMs=a/b`;
    the six-water lines left no window for pass 2 to skip, hence the dedicated F1 fixture.
  - **Review hop (2026-10-27)** — nothing blocking; it confirmed `softProves` equal to the old comparison in
    every case and every law the clamps allow, the far table's encoding, F1's key and F0's placement. Fixed on
    its word: `farEdgeDistance`'s contract written as it behaves (a held found distance answers a shorter
    reach too, which both callers read as the long read's value); a six-water F1 test that never skipped
    removed; a law whose near clearance passes 150 m added, pinning the ceiling's `edgeReachM` term;
    `berthClears`' last line reduced; stale "two tables" KDoc; the read helpers made private; unused defaults
    dropped. **Left, named:** the F0 clocks run in every production walk (every pass hands in a timing) while
    only `APPROACH` prints them — two `nanoTime` calls per paid read; the clock-if-timed pattern is written four
    times; `berthClearanceM` is now a second home of the near-then-far logic, kept for its test (W12); the clock
    containment check runs on a gateless fixture, so the edge clocks' placement rests on reading.
  - **F0's device reading (2026-10-27, Lérins → La Salis, 30 kn, `evolutive`)** — the approach pass 5.5 / 9.9 /
    11.1 s (λ 3 / 1 / 5), pricing 79–89 % of it; inside pricing the field's own reads carry 89–91 % (price about
    63 µs a read, clearance about 89 µs) and the gate-edge reads 5–7 % (about 7 µs a near read). By §11's own
    order neither F3 nor F4 is built: the lever is the zone reads' cost, unmeasured by source (zones, the 300 m
    band, markers) and undesigned. F1 skipped 6 / 0 / 8 windows; "Closest"'s second pass found no repeat and
    still took 6.8 s, its first pass's moves having made new windows. This pair has no pre-wave capture, so its
    lines' identity rests on the suites.
- **2026-10-28 — wave G: the field reads measured by source, the coast's own read cut, autonomous on the user's
  word** (*"carry this work as far as you can go without big regression on the use case nor more than 15 % of
  performance loss"*), on `feature/roooouuutttiiinnngggg`, the device off `adb`.
  - **The instrument** — `RouteBendHarnessTest` routes the Lérins → La Salis pair over the shipped region
    (`BakedRegionWorld`), the shipped `.properties` (`AppConfig.applyProperties`, split out of `init` so a JVM can
    apply the very files the app ships) and the device's own markers (`routeMarkersOf`, the live world's projection
    made shared; a copy of `user_markers.json` at `app/build/route-harness/`), at 30 kn on `evolutive`; gated on
    `-Dmaro.bendHarness=true`, with an opt-in Flight Recorder hook (`-Dmaro.jfr=<file>`) on the test JVM. Without
    the markers every rung goes round Saint-Honorat (18.7–18.9 km); with them through the islands, 15.6–15.8 km —
    the device's 15.0–15.1 km lines are not reproduced (the marker copy predates the runs).
  - **The reading** — the profile put a third of a rung's samples on `CoastlineSpatialIndex.nearestRef` (the
    band's price and clearance, the rasters), half of it in hashing a boxed cell key; the zone rings are 27–65
    vertices and their reads a few per cent. So the lever is the coast's read, not the zones'.
  - **What shipped, every line identical on the harness and the suites** — the index's grid is two flat arrays
    in the old fill order (no key object, no hash); `nearestRef` skips a segment whose box, at the shortest
    degree of longitude any of its points can take, already stands a micrometre past the best
    (`SpatialOperations.boxCannotBeat`); the three zone ring-distance loops are one `ringDistanceM` (no skip
    there — measured no cheaper on rings this short); the approach pass's slide search is its own `searchSlide`.
    A new exactness test pins `query`'s distance and segment against brute force on ~11 000 points.
  - **Measured (harness, best of five, ms a rung — Around / Balanced / Closest):** the split as committed
    514 / 414 / 440 → 401–410 / 332 / 345 with this wave; against the pre-split code (c4b1c689) 370 / 363 / 412
    that is +8–11 % / −9 % / −16 %. The harness's `rungMs` includes a grid build per rung, which the device pays
    once an arm from its tile store, so the device's shares differ; its reading is owed.
  - **Tried and dropped** — a merge never deleting both points of one split: it never fired on the pair, the
    split's second point being slid in the same pass, and tracking pairs through slides was not worth ~6 % on one
    rung.
  - **Review hop (2026-10-28)** — nothing blocking. Fixed on its word: the box bound's slack made absolute (a
    relative one fell under the distance read's own rounding within ~2 m of the coast); the exactness test asserts
    the segment taken where it is unique and probes each vertex's longitude; the harness applies the values before
    it builds its world, shares the coast index across `withMarkers`, reads the ladder off `RoutePreference`, has
    its own gate and names `--rerun`; `forEachInCell` refuses a cell outside the grid; the memory note. **Left,
    named:** the index's box seeds its max with `Double.MIN_VALUE` (wrong for non-positive coordinates) and
    `testOptions.isReturnDefaultValues` is declared twice in `app/build.gradle.kts` — both pre-existing.
- **2026-10-28 — wave G's device reading (08:39, the same pair, P3 in the same build):** the rungs' `firstMs`
  5.8 / 5.9 / 6.9 s (Around / Closest / Balanced) against 9.4 / 8.6 / 12.1 s at 23:08 the night before, the
  approach pass 2.2 / 2.4 / 3.3 s against 4.3 / 3.1 / 6.6 s with more evaluations on "Balanced" (716 candidates
  against 603); 64 of 64 tiles from the store, the phone's thermal state not controlled.
