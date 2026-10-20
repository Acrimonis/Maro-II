# Plan — the gate standoff is sampled, not measured (v5: measured edge, 10 m floor, centred passages)

**Status:** implemented 2026-10-20, device pass owed · **Feature:** Route · **Created:** 2026-10-17 · **Revised:** 2026-10-19 (v5)

## History, one line each

- **v1** (pull-stage disc plus post-hoc repair) — backed out on 2026-10-17: the repair re-searched the same blind grid.
- **v2** (corner-sampled wall plus a wider dilation) — never implemented: it still sampled, and it closed channels.
- **v3** — the 3 m edge measured as segments stored in fine tiles.
- **v4** — the edge on one world-level index, a leg pad for grid steps, and the corner pass's 50 m ring named as a bug.
- **v5** (this) — the user's answers to Q1 and Q2 applied, plus the narrow-passage compromise:
  - a 10 m hard floor;
  - a priced 10–20 m zone;
  - a grid-step check replacing the leg pad;
  - a centring pass.

  v5 also reviews the ends' carve and disc, the pull's price proof and marker walls.

## Decisions

- **Q1 — answered 2026-10-19: the gate's end waiver shrinks to 20 m.**
  - Within 20 m of either end, or on its carved approach, the 3 m rule does not bind; beyond that it does.
  - The coast's own 50 m waiver is unchanged.
- **Q2 — answered 2026-10-19: yes.** The corner pass reads the gate's 20 m, measured relative to the corner it rounds (D8).
- **Q3 — adopted here as proposed; the two values await the user's word:**
  - a hard floor of **10 m** from the 3 m edge;
  - a priced zone from 10 to 20 m (`innerSecPerM` **0.5 s/m**);
  - the existing berth from 20 to 50 m.

## What the navigator will see

- **Shallows:** no route comes closer than 10 m to water under 3 m, except within 20 m of its own start or finish.
- **Open water:** routes keep 20 m from shallows, now measured rather than spot-checked.
- **Passages:** about **46 m** and wider are always usable. Narrower ones (from about 26 m) are usable only where the grid happens to fall well.
- **Which rung takes a passage** depends on the detour it saves: *Coastal* takes passages most often, *Detour* least.
- **Centring:** through a passage the line runs down the middle — within about 1 m once S9 lands, within about 10 m before it.
- **Corners:** corners near shallows are rounded instead of left sharp.
- **Ends:** an end tucked deep in a shallow inlet may become unreachable (its target turns red). Today the route leaves such an end along the shallow edge.

## Evidence

- **2026-10-17,** [`route_acq.log`](../../route_acq.log:151): λ=1 cuts La Grenille at `minDepth=2.2m gateEdge=5.0m` at 43.57112,7.14007.
- **2026-10-17,** [`route_acq2.log`](../../route_acq2.log:1975): v1's repair ran twice and ended `clear=false`; cost rose about 2.5×.
- **2026-10-19,** five searches on `feature/route-low-depth-check`:
  - every rung of searches 3 and 5 reads `minDepth=2.2m`, `bodyEdge=5.0m` at about 43.5709,7.1402;
  - that point is about 53 m into the chord from 43.57062,7.14069 to 43.57115,7.13994, both of whose bows the corner pass refused (`BOW_DEPTH`), so **the pull's chord breached**;
  - searches 1, 2 and 4 keep a vertex at 43.57241,7.14143 (`SWEEP keep … why=LAND`), a passable fine-cell centre whose edge reading is 10 m.
- **Reading caveat:** the `LINE` reading ([`edgeDistanceAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteDepthReading.kt:111)) scans 8 bearings at 5 m steps, so its 5 m and 10 m are upper bounds on the true distance.

## Diagnosis — every guard samples, none measures

- **The grid:** the gate is one depth read per 20 m cell centre ([`rasterizeFrame`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:560)), with a centre-to-centre distance ([`dilateGateGrid`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:626)).
- **The pull and the corner pass:** both read the point plus 8 points on a ring ([`gateStandoffClears`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:258)).
- **What slips through:** the depth raster is bilinear between nodes ([`DepthGrid.depthAt`](../../app/src/main/java/ykws/android/maro/data/model/DepthGrid.kt:117)), so a single shallow node dips under 3 m only within metres of itself, between the samples.
- **Why the line lands there:** the straightener keeps only wall-pinned vertices, so the drawn line runs from wall to wall.

## Review findings (code read 2026-10-19)

- **F1 — the pull keeps a refused grid step.** An immediate step the guard refuses is accepted once ([`MultipassPull.kt:383`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:383)), so a raw leg between two legal centres can dip inside the margin.
- **F2 — the corner pass reads the coast margin.** [`RouteAvoidEngine.kt:609`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:609) hands `ctx.marginM` (`route.avoid.obstacle.marginM=50`) to [`blockerAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt:520), so the depth ring sits at 50 m and bows near shallows are refused.
- **F3 — the end waiver is 50 m for the gate.** [`marginWaived`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:814) reads `ctx.marginM`, so no gate test binds within 50 m of an end. The `LINE` body reading excludes the same 50 m.
- **F4 — tiles cannot carry the edge for the pull:** points off the fine members and on carved approaches have no tile.
- **F5 — the world reads interpolated depth only** (EMODnet-gated and renormalised over NoData), so the edge must be traced on `depthAt` itself.
- **F6 — the tile key does not move**, so cached tiles would serve the old wall.
- **F7 — the ends reopen gate water.**
  - [`openEndDisc`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt:119) reopens any coast-margin cell within 50 m of an end whose centre clears 3 m, gate-dilated cells included.
  - [`carveBerth`](../../app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt:200) reads depth only at its own cells.
  - Under Q1 both would undo the 20 m waiver, so they must follow the gate rule (D9).
- **F8 — the pull's price proof assumes a constant berth.** It prices a group of marks from one reading wherever no breakpoint lies inside ([`MultipassPull.kt:1050`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:1050)), but the berth is a ramp, not a step. That is tolerable on today's gentle 0.1 s/m slope; it is not tolerable on a steep 10–20 m ramp (D7).
- **F9 — Blocked marker bodies are seeded into the gate's distance field.** Step 4 marks every rastered block with `markGateBlocked` ([`MultipassGrid.kt:565`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:565)), and marker walls are rastered ([`RouteMarker.kt:205`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt:205)).
  - So a Blocked body gets the 20 m dilation and the berth, though the Route summary calls it a body-only wall, and the comment "the only rastered wall is the gate" is stale.
  - This plan **preserves** that behaviour (D5) and does not decide it.
- **F10 — the Route summary's berth rate is stale.** It reads `secPerM 0.75`, while `route.evolutive.berth.secPerM=0.1` has stood since `08d30f46`, the code default matching. `maro.properties` is the source of truth.

## The design

### D1 — one shallow-edge index on the world, built lazily per chunk

- **New file** `spatial/multipass/ShallowEdgeIndex.kt`: `ShallowEdgeIndex(depthAt, lattice, minDepthM)`.
  - `depthAt` is the gate's own read, NaN for unsurveyed.
  - `lattice` is the depth raster's node geometry.
- **Chunks** are 32 × 32 raster quads, built on first touch in a `ConcurrentHashMap` (`computeIfAbsent`), because tile builds run on several workers.
- **A chunk's build:**
  1. Read its 33 × 33 nodes.
  2. All clear or all shallow: no segment.
  3. Each **mixed quad** is split k × k, with `k = ceil(rasterCellM / 2.0)`.
  4. Sub-nodes are read through `depthAt`, NaN as `+∞` (NoData never walls).
  5. [`marchingSquaresScalar`](../../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:743) runs at `minDepthM`, and each quad's polyline is simplified by Douglas–Peucker at 0.25 m.
- **Storage:** segments packed in a `DoubleArray`, with one bucket per raster quad.
- **Reads:**
  - `distanceM(lat, lon, capM)` scans the buckets of every chunk within `capM`;
  - `segmentDistanceM(a, b, capM)` serves the step guard;
  - `segmentsIn(box)` serves the raster.
- **Ownership:** a process-lived holder keyed on `(depthGenerationStamp, emodnetCutoffM, minDepthM)`, behind `MultipassWorld.shallowEdges(minDepthM)`.
  - [`LiveMultipassWorld`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt:177) — built per search in [`MapScreen.kt:760`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:760) — answers from the holder, with a depth function that captures the cutoff once rather than per read.
  - The interface default builds over `depthAt` on a nominal 25 m lattice, for test worlds.

### D2 — the bound

- Crossings on quad edges are exact (bilinear is linear along an edge), and the true curve stays inside its 2 m sub-quad.
- **`EDGE_PAD_M = 2.0·√2 + 0.25 ≈ 3.1 m`** therefore bounds segment-to-edge error, the Douglas–Peucker tolerance included. Every guard reads `edgeM(p) = distanceM(p) − EDGE_PAD_M`.

### D3 — one gate rule, threaded everywhere

- `GateRule(index, hardM, preferredM, waiverM)` is built once per pass and read by the raster, the search, the pull, the corner pass, the ends and the trace.

| Engine | `hardM` | `preferredM` | `waiverM` | priced 10–20 m zone |
|---|---|---|---|---|
| `evolutive` | `route.evolutive.berth.floorM` (10) | `route.avoid.depthGate.marginM` (20) | 20 (Q1) | yes |
| `avoid` | 20 | 20 | 20 (Q1) | no — its berth is null, so its hard rule stays 20 m |

### D4 — the law: one rate function

- [`BerthLaw`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:182) gains `floorM` and `innerSecPerM`, plus `rateSecPerM(d)`, which replaces `berthShare` and `berthPriceSecAt`. With `M` = `marginM`, `F` = `floorM` and `outer` = `outerM`:
  - from F to M: `secPerM + (innerSecPerM − secPerM) × (M − d)/(M − F)`
  - from M to outer: today's `secPerM × (outer − d)/(outer − M)`
  - beyond outer: 0
- **Shape:** continuous at M and strictly decreasing in d, so in a symmetric passage the cheapest water is its middle.
- **Who reads it:**
  - the search: its `relax` takes `berth: BerthLaw?`, replacing four doubles, times `cellM` times λ;
  - the pull's `berthRate`;
  - the arm report (`PriceArm.DEPTH`).
- **New keys** in `maro.properties`, each with a `# tldr;` line, plus AppConfig accessors and clamps:
  - `route.evolutive.berth.floorM=10` — clamped 0 to `marginM`;
  - `route.evolutive.berth.innerSecPerM=0.5` — clamped from `secPerM` to 10.
- **What 0.5 means:** at the floor, a metre costs about 2× its travel at 5 kn for *Coastal* (λ=1) and about 7× for *Detour* (λ=5). It is a starting value, tuned on the device with `innerM` (D10).

### D5 — the raster paints from the measured edge

In `rasterizeFrame`, step 4b becomes:

- **Exact seeds.** For each segment of `segmentsIn(grown box)`, `forEachCellNear` (generalised from `MultipassEdge` to a plain pair) writes `max(0, d − EDGE_PAD_M)` onto each passable cell within `R_seed = max(berthOuterM, hardM + EDGE_PAD_M + cellM·√2)` — 50 m on the fine layer — keeping the minimum.
- **Dijkstra over every seed.** The gate-interior and marker zeros, plus the exact seeds, relax to `capM` as today, and land never relaxes. Within `R_seed` the stored distance stays exact (the triangle inequality forbids relaxing below it); beyond, it reads at or above the truth.
- **Hard paint:** a passable cell whose stored distance from a **depth** seed is under `hardM` is land.
- **Marker bodies keep today's paint (F9):** their own dilation (`≤ gateMarginM`, centre-based) runs from their zeros alone, only when marker walls exist.
- **Retired:** the old depth dilation paint.
- **Collar membership** (`gateDistanceM ≤ depthCollarM`) now reads the measured edge, so a rock no centre saw gets its fine water and its berth.

### D6 — the search checks steps near the edge (replaces v4's leg pad)

- `relax` ([`MultipassSearch.kt:223`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt:223)) takes a `StepGuard?` and refuses a step whose segment comes within `hardM` of the edge.
- **Trigger, no read:** with stored distances `dFrom`, `dTo` and step length `edgeM`, the step needs a check only when `min(dFrom, dTo) < (hardM + edgeM/2) × 1.0825`. The factor covers the octile overestimate beyond `R_seed`, and every other step is proved clear by the 1-Lipschitz bound.
- **Read:** one `segmentDistanceM` on the two centres.
- **Exemption:** a step with either centre within `waiverM` of a raw end or on a carved approach (the pull's own `marginWaived`) is not checked. The residual is one step (≤ 28 m) past the waiver.
- **Effect:** every raw leg the pull may keep under F1 already clears `hardM`, so no leg pad is needed. Passages from `2 × (hardM + EDGE_PAD_M) + cellM` ≈ **46 m** always hold a legal row, at any angle.

### D7 — the pull

- **Two waivers** in [`legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:865):
  - `coastWaived` keeps today's disc (`marginM`, 50 m) plus the approaches;
  - `gateWaived` uses `waiverM` (20 m) plus the approaches.
- **The tests, in order:**
  1. `!coastWaived && field.hardBlocked(p)` → LAND (as today: the gate interior and marker bodies).
  2. `!gateWaived && coastWaived && depth(p) < minDepthM` → LAND (the gate interior between 20 and 50 m, Q1).
  3. `!gateWaived && edgeM(p) < hardM` → LAND, spared by the coarse-mark proof: a coarse mark at `edgeM ≥ hardM + coarseStep/2` proves its fine marks.
  4. The coast distance, as today.
- **Lines 894–904** (the cell pre-filter and the 8-point ring) are deleted.
- **`PullSetup`/`PullContext`** carry `gate: GateRule?` and `berth: BerthLaw?`, replacing `gateDistanceMAt`, `gateMarginM`, `berthOuterM`, `berthSecPerM`, `gateDepthAt` and `gateMinDepthM`. The three wiring sites are [`RoutePassRunner.kt:90`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt:90) and [`RouteFinePass.kt:90`/`:219`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:90).
- **`berthRate(p)`** reads `edgeM(p)` (measured) instead of the cell's stored value, so the steep ramp is priced where the point actually is.
- **The price proof (F8):** `berthClearanceM` answers 0 inside the inner ramp, so groups there read per mark, and keeps today's breakpoint clearance on the gentle outer ramp.

### D8 — the corner pass (Q2)

- `round(…)` takes `gate: GateRule?` beside the coast `marginM`, and [`RouteAvoidEngine.kt:609`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:609) passes it.
- In `blockerAt`, the gate test mirrors the coast test's own rule: `gateFloorHere = min(preferredM, edgeM(at))`, so a bow may not come closer to the edge than the corner it replaces, nor closer than 20 m where the corner itself is clear of 20 m.
- The bow point's own depth step (under 3 m → `BOW_DEPTH`) stays.
- [`gateStandoffClears`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:258) loses its last caller and is deleted.

### D9 — the ends follow the gate (Q1, F7)

- **`openEndDisc`:**
  - reopens a coast-margin cell only where `edgeM(centre) ≥ hardM` or the centre is within `waiverM` of the end;
  - also opens gate-painted cells within `waiverM` whose centre clears 3 m.
- **`carveBerth`:**
  - legal water requires `edgeM ≥ hardM`;
  - intermediate cells are *margin water* — inside the coast margin **or** the gate's `hardM`, never shallow itself;
  - the reach is `carveReachCells(max(marginM, hardM + EDGE_PAD_M), cellM)`.
- **The aiming ring** reads the same carve, so a green target and a reachable end cannot disagree.
- **The carved approach stays exempt** from both margins. It is the one way out of a shallow-side berth, and its length is bounded by the reach.

### D10 — the trace

- **`EDGES chunks= mixed= segments= ms=`** — once per pass, from the index holder.
- **`LINE` keeps its independent 8-bearing auditor** (`gateEdge`, `bodyEdge`) and gains:
  - `edgeIdx=` and `bodyEdgeIdx=` — the index's reading;
  - `innerM=` — metres of line closer than `preferredM`, past the waivers.
- **The body exclusion** becomes the gate waiver (20 m discs plus carved approaches), read through `marginWaived`.
- A `bodyEdgeIdx < hardM`, or a `gateEdge` well under `edgeIdx`, is a bug report, never a repair.

### D11 — the tile key and format

- [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt) gains `berthFloorM` (the paint reads it) and `berthInnerSecPerM`, keyed beside `berthSecPerM`. Its canonical bytes and `TileKeyCanonicalTest` flip list follow.
- [`FineTileCodec.FORMAT_VERSION`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCodec.kt:32) goes from 5 to 6, with no field change, because the stored wall's meaning moved. Every tile rebuilds once, and the prewarm absorbs the home area.

### D12 — the medial nudge (centring)

- **Where it runs:** on `evolutive` lines, after `STRAIGHTEN` and before the corner pass.
- **Detecting a passage:** sample the line at 10 m. A sample is *in a passage* when `edgeM < preferredM` and rays along ± its normal hit the edge on both sides within 60 m. The rays are found by sphere-tracing on `distanceM`, a few reads per ray.
- **Moving the line:** consecutive passage samples form a stretch. Its targets are the hits' midpoints, smoothed by a 3-point average, with vertices placed every 40 m plus the stretch's own ends.
- **Acceptance:** a target is kept only if
  - its `edgeM` is at least the sample's own;
  - both adjacent chords pass `legClearCause`;
  - the stretch's travel plus price does not rise (the pull's own guard).
- **No re-straightening:** the straightener is not rerun over nudged stretches.
- **Independence:** safety does not depend on this pass. S1–S8 ship without it, with the line then within about 10 m of the middle (the cheapest grid row).

## Performance

| Path | Today | After (expected, not measured) |
|---|---|---|
| Index chunk | — | 1,089 node reads; mixed quads × k² sub-reads (k ≈ 10–13); a few ms per touched chunk, once per session, mostly inside the prewarm |
| Tile cold build | `GRID layer=fine ms=2007` (12 windows) | + one 50 m sweep per simplified segment, in the coast sweep's class |
| Tile warm load | `ms=18.6` | unchanged — no stored field added |
| Search | `expansions` 180–700 | + one segment read per step near the edge only |
| Pull, per fine mark | 1 cell read + 9 bilinear reads | 1 index read, spared by the coarse-mark proof; per-mark price reads only inside the 10–20 m ramp |
| Corner bow, per point | 9 bilinear reads | 1 depth read + 1 index read |
| Medial nudge | — | passage stretches only |
| One-time | — | every tile rebuilt once (D11) |

The device confirms these through `EDGES`, `GRID`, `PULL`, `FINE` and `DEVICE PASS` on the 2026-10-19 end pairs.

## Steps — implementation order

- **S1 — index and rule:** `ShallowEdgeIndex.kt`, `GateRule`, the world's `shallowEdges` and holder, the `EDGES` line.
- **S2 — the law:** `BerthLaw` with floor and ramp; the two keys, accessors and `# tldr;` lines; the search, the pull and the arm report on `rateSecPerM`.
- **S3 — the raster and the tiles:** exact seeds, the Dijkstra over seeds, the depth paint, marker paint preserved, `TileKey`, `FORMAT_VERSION = 6`.
- **S4 — the search:** `StepGuard` in `relax`, the trigger and the end exemption.
- **S5 — the pull:** the waiver split (Q1), the measured gate read with the coarse-mark proof, `berthRate` measured, the price proof's ramp clearance, the setup fields swapped.
- **S6 — the ends:** `openEndDisc`, `carveBerth` and the aiming ring on the gate rule.
- **S7 — the corner pass:** `GateRule`, the relative floor (Q2), `gateStandoffClears` deleted.
- **S8 — the trace:** the `LINE` additions and the body exclusion.
- **S9 — the medial nudge.**
- **S10 — the docs:**
  - [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md) — the wall, the law, the guard, the nudge;
  - `docs/maro-code.md` — the new file;
  - the `route.avoid.depthGate.marginM` comment — preferred, no longer hard, on `evolutive`.

## Tests

- **`ShallowEdgeIndexTest`:**
  - one 2.2 m node among 5 m nodes yields a closed ring;
  - for 1,000 random points, `edgeM` is never above the brute-force distance to points with `depthAt < 3` sampled at 0.25 m — the D2 bound pinned;
  - all-deep and all-shallow chunks build nothing;
  - a NoData quad beside a 2 m node still closes the ring.
- **`BerthLawTest`:** continuous at M, strictly decreasing over F..outer, zero beyond outer; a symmetric passage's cheapest lateral offset is its middle.
- **`MultipassGridGateTest`:**
  - the La Grenille shape (one shallow node between four deep fine centres) paints every cell within `hardM` of its contour;
  - exact seeds hold inside `R_seed`;
  - marker-wall cells keep today's paint and berth.
- **`MultipassSearchStepGuardTest`:**
  - a diagonal step grazing a rock is refused, and a step proved by the trigger reads nothing;
  - a straight 46.5 m passage is open at 0°, 26.6° and 45°, at four lattice offsets each;
  - a step inside the end waiver is not checked.
- **`MultipassPullTest`:**
  - a chord 5 m off the edge is refused 30 m from an end and accepted 15 m from it (Q1);
  - a marker body 30 m from an end still binds as today;
  - a group inside the ramp reads per mark.
- **`RouteCornerPassTest`:**
  - a bow at 25 m from the edge, refused today by the 50 m ring, is accepted (Q2);
  - a bow from a corner at 14 m may not go under 14 m.
- **`BerthCarveTest`:**
  - the disc opens no gate-painted cell beyond 20 m;
  - a shallow-side end still reaches legal water through margin water;
  - an end enclosed by shallows answers `CARVE_NO_LEGAL_WATER`.
- **`RouteMedialNudgeTest`:**
  - a symmetric 50 m channel's line ends within 1 m of the midline;
  - an asymmetric one ends midway between the hits;
  - a one-sided bank is left untouched;
  - a bent channel produces no new turn above the corner pass's minimum.
- **`TileKeyCanonicalTest`** flips the two new fields. **`FineTileCodecTest`:** a v5 entry decodes to `null`.
- **Device:**
  - the 2026-10-19 end pairs, plus one pair through Pointe du Bacon – La Grenille;
  - every `LINE` shows `minDepth ≥ 3.0m`, `bodyEdgeIdx ≥ 10m` and the auditor in agreement;
  - `innerM` is read to tune `innerSecPerM`;
  - the timings sit beside today's.

## Risks

- **Chart accuracy:** in passages the line runs 10–20 m from shallows by design, which trusts the chart to within a few metres there.
- **Tuning `innerSecPerM`:** too cheap and open water trades its 20 m for a shortcut; too dear and *Coastal* refuses passages worth taking.
- **Grid-row bias:** the search prices a passage at its nearest grid row, so a 46–60 m passage may look dearer to the search than the nudged line turns out to be.
- **Nudge jaggedness on ragged banks:** smoothing plus the acceptance guard bound it; a stretch the guard refuses stays as the pull drew it.
- **Ends in shallow inlets** may become unreachable (Q1 with D9).
- **`avoid` passages narrow:** the measured 20 m plus the step guard narrows `avoid`'s 100 m-cell passages. The Cagnes–Cannes probe is rerun for lost lines.
- **Unmeasured:** the depth raster's cell size (`k` and the pad derive from the loaded grid) and the index's memory (the `EDGES` line reports both).

## Out of scope, recorded

- **F9's question:** whether a Blocked marker body should keep the 20 m dilation and berth — behaviour preserved here.
- **F10:** the Route summary's stale `secPerM 0.75`.

## Key files

- `app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt` — new: the index, `GateRule`, `StepGuard`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt` — `shallowEdges`, the holder, the live answer
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt` — `BerthLaw`, `berthLawOf`, `gateStandoffM`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt` — `berthShare` / `berthPriceSecAt` / `gateStandoffClears` retired
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt` — `rasterizeFrame` 4b, `dilateGateGrid`, `forEachCellNear`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — `buildFineTile`, `fineTileKey`, the window builds
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt`, `FineTileCodec.kt` — the key fields, `FORMAT_VERSION`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt` — `relax`, `StepGuard`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `PullSetup`, `PullContext`, `legClearCause`, `berthRate`, `berthClearanceM`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt`, `RouteFinePass.kt` — the wiring sites
- `app/src/main/java/ykws/android/maro/spatial/multipass/BerthCarve.kt` — `openEndDisc`, `carveBerth`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt` — `round`, `blockerAt`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the corner call, the `LINE` sink, the nudge's seat
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteDepthReading.kt` — the auditor plus the index reading
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties` — the two keys

## Implemented

**2026-10-20, on `feature/route-low-depth-check`.** The plan shipped with Q1–Q3 as decided (the floor at 10 m and `innerSecPerM` at 0.5 confirmed by the user's "#impl with recommended values"). Two independent review rounds ran after the first build; every finding was either fixed or is listed under *Still open* below. The full unit suite passes (1378 tests, 0 failures, 12 skipped as before) and `apk-build.bat` packages; the device pass is the user's.

### What shipped

- **The edge index** — [`ShallowEdgeIndex.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt): the gate depth's contour traced per chunk on first touch, every reading minus the pad; `GateRule` (wall, preferred standoff, ends' waiver, the berth's outer edge and the seed reach) and `shallowEdgesOver`, the one way a raster's index is built. The live world keeps one index per loaded raster for the session.
- **The law** — `BerthLaw.rateSecPerM` with the inner ramp; `route.evolutive.berth.floorM` and `route.evolutive.berth.innerSecPerM` in `maro.properties`.
- **The raster** — the wall painted from the traced line, exact distances written within the seed reach, the field relaxed from them on a primitive heap, Blocked-marker bodies keeping their old centre-based standoff painted after the relaxation.
- **The search** — `StepGuard` on every improving step near the edge; each end anchors on the finest passable cell holding it (`WalkWindows.endCellAt`); a local crossing re-solve waives no end cell.
- **The pull** — the gate measured on its own 20 m lattice refined to 2 m; the coast's 50 m and the gate's 20 m waivers split (Q1); the berth priced at the point off the measured edge; the price proof reads nothing inside the inner ramp and, past the near read, one memoised wider read bounded at 150 m beyond the outer edge (`farEdgeReads=` on the `PULL`/`FINAL` lines).
- **The ends** — the end disc reopens the gate's wall only within 20 m and writes each reopened cell's measured distance; the carve crosses the gate's wall water to legal water, writes its cells' distances, and its approach is stored through its own anchor and cells.
- **The corner pass** — the gate's preferred standoff relative to the corner (Q2).
- **The medial nudge** — [`RouteMedialNudge.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMedialNudge.kt).
- **The trace** — `EDGES` per solve; `LINE` gains `edgeIdx`, `bodyEdgeIdx` (exact per leg, sampled every metre only near the edge or the ends) and `innerM`.
- **Tiles** — `TileKey.berthFloorM` added, `berthSecPerM` removed (tiles store distances, never prices); `FineTileCodec.FORMAT_VERSION` 7.
- **Tests** — `ShallowEdgeIndexTest`, `GateRasterTest`, `GateGuardsTest`, `GateCoverageTest`, `RouteMedialNudgeTest`, `GateTestFixtures`, `BerthCostTest` rewritten, and the tile, codec, corner and depth-reading suites updated.

### Where the code differs from the design text above

- **Sub-cell 1.5 m, not 2 m** — the pad is 2.1 m, so a reading sits at most 4.2 m under the truth and **a 50 m passage always keeps a legal row**, at any angle (the design's "about 46 m" ignored that a reading can sit two pads under the truth).
- **No Douglas–Peucker on the traced line** — the raster sweeps each short segment on one arithmetic kernel instead, with no allocation per cell.
- **The prefilter reads the raw depth** — a quad may hold blocked water where a node's raw depth is under the gate, even if the chart's EMODnet cutoff hides that node: its depth still blends into the neighbours' read (second review's blocker).
- **The gate's own mark lattice in the pull** (D7) — the pull's clearance marks are 25 m apart, too sparse for a 10 m floor, so the gate keeps its own 20 m lattice with 2 m refinement.
- **The nudge's passage** (D12) — a sample inside the berth's priced span whose two banks stand no further apart than twice that span, rather than inside the preferred standoff with 60 m rays; a failing target drops its own vertex only; smoothing never mixes two legs.
- **The end residual** (D6) — a step out of an end's own cell is waived; with each end anchored on its own fine cell that cell stands within about 14 m of the end on `evolutive`, and within about 70 m on `avoid`'s 100 m single grid.
- **The aiming ring** (D9) — no aiming ring reads the carve in this code (only the grid build does), so an end the shallows enclose fails as *no path*, not as a red target.
- **`GridContext.gate` is required**, and `rasterize` takes the berth's outer edge from the rule rather than as a separate argument.

### Still open

- **The device pass** — the 2026-10-19 end pairs and a Pointe du Bacon – La Grenille pair: every `LINE` at `minDepth ≥ 3.0m` and `bodyEdgeIdx ≥ 10m` with the auditor agreeing, `innerM` read to tune `innerSecPerM`, the passage line centred, and `EDGES` / `PULL farEdgeReads` / `GRID` timings beside the old ones.
- **`avoid` behaves differently, and it is the default row** (`route.engine.id=avoid`; the device runs `evolutive` by its own setting) — its 100 m cells now keep the measured 20 m with checked steps, so a passage is always open only from about 148 m (narrower ones where the grid falls well), and ends beside shallows depend on the carve. It is the honest wall where the old one grazed; whether `avoid` should also take the berth's floor and ramp, or stay the strict engine, is the user's call.
- **Health nits left** — chunk lookups box their `Long` keys; the EMODnet cutoff can still hide a sliver narrower than one sub-cell inside a traced quad (stated in the index's KDoc).
