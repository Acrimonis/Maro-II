<!-- scope: feature -->
# Route — the search's speed: evaluation of `avoid` and `evolutive`

**Date:** 2026-10-27 · **Status:** evaluation, for discussion — no code changed · **Order:** the user's word of
2026-10-27 on `feature/roooouuutttiiinnngggg`: *"evaluate the route search algorithm (avoid and especially
evolutive). Precision and feature set is OK. I am looking at performance and speed. Keep in mind code health,
performance improvement suggestions. NO regression. evaluate, report."*

**Scope:** machine cost only — where an arming spends its time, what can be cut **without moving a drawn
point, a clock or a crown**, and what the code's health costs. Precision questions are out of scope and every
lever below is marked *exact* (same doubles, same verdicts by construction) or *not exact* (excluded).

**Method:** one full read of the pipeline by the author (`RouteAvoidEngine`, `RoutePassRunner`,
`MultipassSearch`, `MultipassGrid`, `RouteGridBuilder`, `MultipassPull`, `RouteCostField`,
`RoutePassPrimitives`, `RouteFinePass`, `WalkLattice`, `ZoneGeometry`, `MultipassWorld`,
`CoastlineSpatialIndex`), two independent read-only review hops (the fine pass and tile layer; the
post-search stages), each claim re-checked in source, and the device captures already on disk read for the
budget: `logcat_Le_Village_Englouti_bad-pull.log` and `route_good.log` (2026-10-26, Village Englouti →
Feu Artifice, 30 kn, `evolutive`, λ 1 · 3 · 5, 12 km) and `route_full.log` (the same session's pre-warm).
This supersedes the budget section of
[`261014_FEAT_PLN_Route_acquisition-perf.md`](261014_FEAT_PLN_Route_acquisition-perf.md) and stands beside
[`docs/REVIEW_2026-10-26_routing-algorithm.md`](../../docs/REVIEW_2026-10-26_routing-algorithm.md) §3, whose
"next performance batch" it re-ranks.

## 1. Verdict

- **The algorithm's shape is right and its hot loops are already unboxed; what remains is paid work whose
  result is thrown away, and one cache that never hits.** The fine-tile store is write-only across app
  launches (its coastline stamp is an identity hash) and its key moves with the requesting corridor, so every
  launch rebuilds every tile and the pre-warm's whole budget is spent for nothing. That is the one defect;
  the rest is redundant reads.
- **On `evolutive` the pull's coarse-mark proof spares nothing**: the clearance step it is handed (20 m) is
  below the walk's own sampling step (25 m), so the coarse lattice is the fine lattice offset by half a step —
  in open water every coarse mark pays a live coast query (as many reads as a proofless walk), and within
  62.5 m of the coast the fine mark pays a second one. The uniform `avoid` plan gets the proof; the two-layer
  plan, the one the user cares about, does not. Restoring it is exact; its size depends on how much of the
  line stands 62.5–100 m off the coast (F4), which is why the plan reads a counter before it moves the step.
- **A latent cache defect the review hop found (F15):** a fine tile is rastered with the **building box's**
  coast harvest but cached as a whole 64-cell block, so the part of a block outside that box carries **no fine
  water** (the mask paints it land, the walk falls back to the coarse cell there) and its band collar misses
  coast standing just beyond the box — a line's resolution and price then depend on which box built the tile.
  The corridor-keyed tile key hides it today; making the cache hit (F1–F3) would make it live, so it is fixed
  first.
- **The measured rung** (λ 5, the slowest, 4.1 s) splits as: fine stage 33 %, pull 23 %, post-search tail
  18 % (of which three quarters is trace-only work), A\* 11 %, final pull 8 %, snap 5 %. The arming's wall was
  4.4 s with warm tiles and would have been ~8 s cold.

## 2. The budget, as the device recorded it

One arming, three rungs concurrent on `Dispatchers.Default`, `MaroRoute` tag **on** (so every trace line below
was paid). Times from the logcat's own timestamps and the engine's own `ms=` fields.

| Stage | λ 1 | λ 3 | λ 5 | Shared / notes |
|---|---|---|---|---|
| Repair + corridor + harvest + coarse raster + fine assembly | — | — | — | **283 ms** per arm, warm tiles (`GRID layer=fine … ms=13.3`); **3 544 ms** on the same pair with cold tiles (`route_good.log`) |
| A\* (`STAGE GRID→SEARCH … SEARCH→PULL`) | 311 | 333 | 449 | 19–21 k expansions over 23 467 slots, two layers |
| `PULL` (both ways) | 862 | 932 | 966 | clear 338–397 · price 501–552 · marks 8.6–10.1 k · farEdgeReads 2.9–3.5 k |
| `SNAP` | 189 | 243 | 213 | corner snap: 2 clearances + 4 priced legs per candidate |
| `FINAL` (both ways) | 286 | 274 | 333 | marks 3.5–5.1 k; `FINAL2` bwd reads 69–359 against fwd 757–919 — the memo at work |
| `FINE` stage (`fineMs`) | 825 | 1 055 | 1 372 | local pull 1.7–1.8 k marks; **sweep 9.4–19.4 k marks**; nudge 1.2 k samples; no zone re-solve on this pair |
| Corner pass (`DEVICE PASS → CORNERS`) | 83 | 79 | 63 | 8–10 bends |
| Clock + corner tally + probe (`CORNERS → PROBE`) | 98 | 112 | 90 | probe memoised, 10 ms once |
| `LINE` readings (`PROBE → LINE`) | ~700 | ~660 | ~550 | **trace-only** — inside the sink, not paid with the tag off |
| `rungSlowWater` + terminal (`LINE → DONE`) | 35 | 41 | 27 | |
| **Rung (`RUNG firstMs`)** | **3 454** | **3 775** | **4 122** | arming wall **4 420 ms** |

What this says: with the tag off a rung costs about 3.0–3.5 s on this pair, and nothing in it is a single
hot spot — the fine stage's sweep and the two-sided pull together are half, and both are the same walk
(`legClearCause` + `softPriceSec`) over the same reads. A second capture at 10 kn with a 5 kn zone on the line
shows the crossing re-solve at **600–1 060 ms per rung per zone** (`FINE zone=… reason=worse … ms=`), most of
it discarded.

## 3. Findings, ranked by expected device ms

Each entry: what, the evidence, why it is exact, the expected gain. "Unmeasured" means no device figure
isolates it; the budget above bounds it.

### F1 — The on-disk tile store never hits across launches (defect)

- `LiveMultipassWorld.coastlineGenerationStamp` answers `spatialIndex.hashCode()`
  ([`MultipassWorld.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt))
  and `CoastlineSpatialIndex` overrides neither `equals` nor `hashCode`, so the stamp is the object's
  **identity hash** — a new number every process. `TileKey.canonicalBytes` writes it, `FineTileCodec` names
  the entry by the SHA-256 of those bytes, so no entry saved in one launch is ever asked for by name in the
  next.
- Evidence: across the three 2026-10-26 logs, **1 940 `STORE load … hit=false`, zero hits**; every `PREWARM done`
  reads `warm=0 built=N` (178 tiles in one cycle); the first arming on cold water paid `GRID layer=fine …
  ms=3543.7` against `13.3` once the in-memory map held the tiles. The 2026-10-14 plan saw the same symptom
  and read it as "no warm arm measured yet"; the cause is the stamp.
- Fix, exact: a **content** stamp — the baked coastline asset's own identity (size + mtime, or a digest taken
  once at index build), carried on the repository beside the index. Same key semantics, no byte of any tile
  moves. Gain: the cold first arming (3.5 s → the warm 13 ms on the fine layer) and the whole pre-warm's CPU
  and battery per launch (80 tiles took 8.8 s in `route_full.log`; 904 tile builds in one session).

### F2 — The tile key carries the requesting box's zone set, so arms and the pre-warm key one tile differently

- `fineTileKey(… zoneRings = priced …)` with `priced = world.speedZonesIn(box)` — the **corridor's** zones in
  `buildLayeredGrid`, the **pre-warm box's** in `fineTilesOver`
  ([`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt)).
  Two boxes overlapping different zone sets name the same tile twice; a pre-warmed tile is found by an arm
  only when the arm's corridor meets exactly the zones the warm box met. `markers = world.routeMarkers()` is
  the whole world's list, so any marker edit anywhere invalidates every tile (by design, but the same cure
  applies).
- Evidence: in-process, the pre-warm rebuilt **69 of the same 80 tiles one minute apart** with `warm=0`
  (`route_full.log` 18:25:10 and 18:26:19) — a key field moved between the cycles while the lattice did not;
  the zone or marker lists landing after the first cycle is the likeliest mover, and F2 is what makes the key
  sensitive to it.
- Fix, exact: key (and fill) each tile on the zones whose bbox meets the **tile's own grown box** inflated by
  the outside margin, and on the markers whose reach meets it. The even-odd fill of a ring wholly outside a
  box toggles an even count before column 0 or never, and the collar sweep is clamped to the grid, so the
  rasterised bytes are identical — pin it byte-for-byte on a fixture before shipping. Gain: the pre-warm
  finally serves arms; a second arming over the same water hits whatever corridor it came from.

### F3 — The pre-warm is gated on the coast and depth layers alone

- `MapRouteEffects` starts the walk once `coastlineReady && depthReady`; the zone and marker lists are not
  waited for, so the first cycle keys its tiles on a world that is still loading and F2 throws them away.
  Fix: gate on the zone list and the marker list having landed (the `markersLoaded` flag the end effects
  already read, plus a zones-loaded read). Exact. Gain: the first cycle's work (80 tiles, ~9 s) stops being
  wasted.

### F15 — A fine tile is rastered with its building box's harvest and cached as a whole block (latent defect)

- `buildFineTile` rasterises a tile's grown block with the `edges` and `openCoast` its caller harvested over
  the **corridor** (`buildLayeredGrid`) or the **pre-warm box** (`fineTilesOver`) — `segmentsIn(box)` keeps the
  segments whose bbox meets that box, `openCoastPolylinesIn(box)` clips the mainland to it — while
  `FineTile.extract` keeps the whole 64 × 64 block and `writeInto` clips only to the arm's window. A block
  straddling the building box's edge therefore carries water where a coast outside that box stands, and the
  key (`TileKey`) names neither the box nor its clip, against its own contract that it names everything the
  raster reads.
- Today the corridor-dependent zone set in the key (F2) makes a cross-box hit rare, so the defect is latent;
  in-process, two arms meeting the same zone set over different corridors can already share such a tile.
  F1–F3 would make cross-box hits the norm and the pre-warm's edge tiles serve every arm.
- What the fixture showed (2026-10-27, `FineTileWaterTest`): the part of a block outside the building box is
  **masked land**, not wrong water — the fine mask keeps only cells near a harvested coast, so an island the
  box's harvest never saw leaves no fine ribbon and no margin; and a tile within the band's reach (350 m) of the
  box's edge, even inside the box, lacks the band collar of coast beyond the edge. The index's 500 m harvest
  cells soften the first symptom; nothing softens the second.
- Fix, exact a harvest reach inside the building box and correct beyond it: harvest **per tile** inside
  `buildFineTile`, over the grown block inflated by the largest reach any sweep uses (with the filter's slack),
  and take the tile's zones from the world over the same reach, so the tile's bytes are a function of the
  lattice, the world and the key alone. Pinned byte for byte against the corridor-harvest raster for every
  block a harvest reach inside the corridor, and on a straddling block for the island's ribbon it now keeps.

### F17 — The approach pass is 60–70 % of a long route's rung, and it was never in the budget (measured)

- The 2026-10-27 device capture (first launch after the remediation's install, a **15 km** pair from a 15 kn
  zone across a 5 kn zone, 29 k expansions) puts **10.3 s, 12.6 s and 15.0 s** of the fine stage in
  `RouteApproachPass.refine` — the span from the sweep's end to its `APPROACH` line — on λ 5, 3 and 1: **86–91 %
  of `fineMs`** (12.0, 14.4, 16.5 s) and **60–69 % of the rung** (17.1, 19.7, 21.7 s; 22.3 s from tap to the last
  terminal). It tried 600, 736 and 648 candidates and moved 2 bends each: **17–20 ms a candidate**, each one
  two chord clearances and two priced chords on geometry the memo has never seen.
- It is in no earlier capture (`APPROACH` appears in none of the 2026-10-08…10-26 logs) because it shipped
  after them (`175021d2`), so the budget in §2 and every lever ranked on it describe a pipeline without it.
- The levers are the same family as F4/F6 (the candidate's chords are new, so the memo helps little; the
  clearance proof step governs its hard reads) plus its own: a candidate's two chords share their pivot, its
  8 directions × 6 scales are independent, and a window whose slow arm cannot be reduced is searched at all.
  Not designed here; it needs its own plan and a reading of `hardReads` on it.
- Beside it, on the same capture: the first two-sided pull is 1.9–2.5 s a rung with **87–90 % of its marks
  paying a hard read** (13.3–18.8 k of 15.3–20.8 k) — F4's proof is spending almost nothing, as measured —; the
  crossing re-solve's shared raster built once (317.6 ms) and served the other two rungs in 2.2–2.4 ms; the arm's
  fine layer assembled from the pre-warm in 43 ms with no tile built; the final pull is 0.36–0.76 s.
- **Wave A could not show on this capture:** the process is the first after an install, the store holds one
  build's entries only, so all 88 loads missed and the pre-warm built and saved 88 tiles in 7.5 s. A second launch
  of the same build is the reading that tests the stamp.

### F16 — The edge index held its raster weakly (latent defect, found by the fixtures)

- `shallowEdgesOver` wrapped the depth grid in a `WeakReference` so a replaced raster would not be pinned by
  the session's index; but the index traces its chunks lazily, and a chunk built after the collector took the
  raster read every node as no data — the traced 3 m line vanished and the gate's wall with it, silently.
- Evidence: `GateGuardsTest` failed three different cases over four runs once the new tile fixture allocated
  heavily just before it, and passed alone twice; on the live world it is reachable only across a depth
  regeneration while a search holds the old gate rule.
- Fix (2026-10-27): the index holds its raster; the one replaced raster it may pin is released when the next
  search or pre-warm tile replaces the index, which follows a regeneration at once.

### F4 — `evolutive`'s clearance walk gets nothing from its coarse-mark proof

- The pull's fine sampling step is `clearanceStep(marginM) = 25 m`. `RoutePassRunner` hands the two-layer
  walk `coarseStepM = tailCellM = fineCellM = 20 m`, and `legClearCause` floors it at the sampling step, so
  the coarse lattice is the fine lattice offset by half a step: every coarse mark pays `distanceToCoastM`
  and proves one 25 m interval — in open water that is exactly the read count of a proofless walk — and every
  fine mark whose coarse mark read under 62.5 m pays a second read. The runner's comment — *"a step under the
  fine one would spare nothing at all, and the caller never hands one: both shipped cells stand well above
  it"* — is false for the shipped values (20 < 25). `RouteFinePass` hands the same 20 m to both its pulls and
  to `solveCrossing`.
- Evidence: the device line prints `stepM=20.0` on every `PULL`/`FINAL`/`FINE PULL`; `PULL` carries 8.6–10.1 k
  marks with only 0.5–0.9 k memo hard hits; `clearMs` is 338–397 ms of each `PULL` and 78–129 of each `FINAL`.
- Fix, exact: hand the walk a coarse step above the sampling step — 100 m, what `UniformGridPlan` already
  uses, or 50 m as the conservative stop. The verdict is a function of the fine marks alone — a coarse mark
  only decides whether a fine read is *skipped*, and it skips only where its reading proves the fine mark
  clear by the 1-Lipschitz bound the code already trusts on `avoid`. The marks, the memo keys and the price
  walk are untouched. **The gain is geometry-dependent** (review hop, 2026-10-27): at a coarse step `s` a
  chord pays `dist / s` coarse reads plus a fine read wherever the coarse mark read under `margin + s / 2`, so
  open water pays `25 / s` of today's reads while a stretch standing between 62.5 m and `50 + s / 2` off the
  coast pays up to a quarter **more**. On the measured pair the band water is ~6 % of the line
  (`bandMetres=712–749m` of 11.9 km), so the expected net is most of `clearMs` — but it is read off a
  hard-read counter on the device before the step moves, never assumed.
- Beside it: `RouteApproachPass` reads the same `coarseStepM` as a **geometry** step (its candidate radius gate,
  its scale-loop floor and `hasSlowWater`'s lattice), so the constant is overloaded; the clearance proof's step
  and the approach pass's step must be two names before either moves.

### F5 — The crossing re-solve rebuilds a λ-free raster per rung per zone

- `solveCrossing` rasterises the zone's inflated box at the fine cell with a `base` field that carries no
  λ (coast wall, depth gate, markers), then searches, pulls twice, snaps and compares; the three rungs do it
  concurrently for the same zone and the same box, and most results are refused after the fact
  (`spliced=no reason=worse`).
- Evidence: 600–1 060 ms per rung per zone in the 10 kn captures; the raster's share is unmeasured but it is
  the whole box at 20 m with a gate seed, a Dijkstra relax, the zone fills and the band sweep.
- Fix, exact: a per-arm memo on `GridContext` keyed on (zone id, clamped box, fine cell) holding a
  `Deferred<MultipassGrid>` — the `ProbeMemo` idiom — each rung taking an array copy before its own
  `forceFree`. The search, pulls and comparison are untouched. The 2026-10-14 plan's "what cannot move" bullet
  mis-files this: the *search* is line-keyed, the *raster* is not.
- Dropped with it: the plan's **P1 incumbent-bounded local A\*** — confirmed unsound as written. The comparison
  is on the **pulled** line's `totalSec` (haversine at the pace plus the sampled price), the search's `g` is
  a cell-centre staircase in a different metric, and no bound relates them; `SearchOutcome.costSec` is never
  read. Leave it.
- Also exact and small: `pull` could return its output line's accumulated `totalSec` (the chords it accepted,
  summed left to right) so `lineCostSec(local)` is not a third full price walk.

### F6 — The mark memo is minted per pull and per pass (the 2026-10-14 plan's P2, still open)

- `RoutePassRunner` calls `pullBothWays` twice with the default fresh `MarkMemo()`, `snapToCorners` reads with
  none, and `RouteFinePass` builds its guard field twice (same arguments, different identity — which wipes a
  memo) and mints a memo per pull. The `settledMemo` shared by the final pull and the sweep is the only thread.
- Evidence: `FINAL2`'s backward walk, which does meet the forward walk's marks, paid 69–359 price reads against
  the forward's 757–919 — the saving one shared memo gives.
- Fix, exact: one `PullSetup` + one `MarkMemo` per pass threaded pull → snap → final, and one per fine pass
  threaded through its local pull, snap, settled pull, sweep, approach and straightener. The memo is keyed on
  the exact point and bound to the field's identity; a hit hands back the field's own double. Gain: a large
  share of `FINAL` (275–335 ms) and of the fine pass's pulls; unmeasured.

### F7 — The corner pass reads the zone limit through the live world's whole-region walk, per 2 m sample

- `RouteCornerPass` calls `world.zoneLimitKnAt` at every bow point and every 2 m chord sample of every
  `firstBlocker`, 26 times per bend across the fit bisections; on the live world that is
  `strictestLimitKnAt(zonesProvider(), excludedZoneIds(), …)` — a `StateFlow` read, a settings read, two
  `filter` allocations and every ring of every zone in the region, with no bounding box — while the clock's
  own read already goes through the arm's bbox-indexed `ZoneSet`.
- Evidence: the corner pass measured 63–83 ms per rung here (8–10 bends, a region of four zones); the cost
  scales with the region's zone count, so a denser chart pays more. Unmeasured in isolation.
- Fix, exact: hand the pass the arm's `ZoneSet` (or give the world a set-backed read) — a box miss is a
  containment miss, so the minimum is the same. Beside it, also exact: `firstBlocker` builds the same
  composite three times per attempt and 26 more times for its tangent alone (split the end-state update from
  the point loop); `composite` boxes 48 `Pair<Double, Double>` per call; `turnDegreesAt` is recomputed for
  the same vertex in six places (one `DoubleArray` per line); `segmentBlocker` reads the gate edge twice on
  the same chord in the unwaived case.

### F8 — The timed line is walked six times (fourteen with the tag on)

- `timeLineWithProfile`, then `slowTimeByLimit`, `rampSlowSeconds`, `lineLengthM`, and the two
  `zonesEnteredBy` samplings; each re-asks `limitAt` (a `ZoneSet` ring walk plus a coast-index query),
  `inZone` and `inBand` at the same midpoints the split already read.
- Fix, exact: one per-leg record (length, mid limit, mid in-zone, mid in-band) and one per-vertex limit array
  computed inside `timeLineWithProfile` and carried on `TimedLine` as a defaulted field; the five slow-leg
  loops read the arrays — same functions, same inputs, same doubles. Gain: most of the ~90 ms clock tail per
  rung; unmeasured.

### F9 — The two-layer A\* decodes a slot seven times per relaxation

- `relax` calls `passable`, `cellSizeM`, `limitKn`, `collarLimitKn`, `bandCollarLimitKn`, `sourceCostSec`
  and `gateDistanceM`, each doing `windowOf(slot)` + `rowOf(slot)` + `colOf(slot)` (an `IntArray` read, a
  `LongArray` read and a key unpack) before the grid read; `forEachStep` probes the `LongIntMap` up to a dozen
  times per expansion (`rawSlotOf`, `supersedingSlot → fineCopyOf`, `crossLayerSlotsInto`,
  `coarseNeighbourOfCopy`). ~20 µs per expansion on the device, 19–21 k expansions per rung.
- Fix, exact: one build-time pass per arm flattening the walk into primitive per-slot arrays (passable, cost,
  three limits, gate distance, cell size) and a per-slot neighbour table (8 same-layer + seam slots, `-1`
  absent) — the slot index is already built once per shape and cached. Same doubles, same tie order. Gain:
  a third to a half of the 310–450 ms A\* per rung; unmeasured.

### F10 — The rasterizer sweeps the coast three times and scans every ring vertex per row

- Per raster (coarse corridor, every fine tile, every crossing box): the margin sweep, the band law and the
  fine mask each walk `forEachCellNear` over every harvested edge with a `LatLng` per cell per segment; the
  three even-odd fills build an `ArrayList<Double>` per row and scan every ring's vertices per row
  (O(rows × vertices), boxed); the field pass allocates a `LatLng`, a `RouteCostAtPoint` and a boxed `Double`
  per cell and reads the depth twice on every blocked cell. A tile build costs ~110 ms on the device
  (`route_full.log`, 80 tiles in 8.8 s).
- Fix, exact: one coast sweep at the largest radius recording the per-cell minimum distance and a
  "some edge in the collar strip" flag, applied at the same steps (land ⇔ min ≤ margin; band ⇔ min ≤ width;
  collar ⇔ flag; mask ⇔ min in band); edges bucketed by the rows they span with a `DoubleArray` scratch
  sorted in place (same crossings, same order); primitive `(lat, lon)` fun interfaces on the sources and a
  `depthMAt(lat, lon)` door on the world. **Not** the kernel: `pointToSegmentDistance` projects at the three
  points' own mid-latitude while `seedShallowEdge` uses the frame's fixed scale — swapping one for the other
  can flip a cell on the margin line, and is excluded. Gain: the 283 ms arm build, the ~110 ms per tile (the
  pre-warm and every cold arm) and the crossing raster of F5.

### F11 — Small, exact, post-search

- `ctx.world.speedZonesIn(ctx.box)` is rescanned per rung for `crossedSlowZoneNames` and `inZone(ctx.zones)`
  builds a fresh `ZoneSet` at two call sites — carry the arm's `ZoneSet` on `GridContext`.
- `lineEntersZone` samples every leg at 25 m against `SpeedZone.contains` with no bounding-box reject — reject a
  leg whose own box misses the zone's first.
- `ShallowEdgeIndex.chunkAt` boxes a `Long` per `ConcurrentHashMap` get and recomputes `cos(lat)` per
  distance call — a last-chunk cache and a hoisted scale.
- `FineTileCodec` decodes through `DataInputStream(ByteArrayInputStream)` — one synchronized byte read per
  byte; `ByteBuffer` bulk gets are the same bytes. Only matters once F1 makes the store hit.
- `FineTileMap.get` re-hashes `TileKey` over every ring vertex per lookup and `fineTileKey` re-reads
  `zoneGenerationStamp` (hashing every zone), `routeMarkers()` and the excluded set per tile — read once per
  arm, cache the key's hash.

### F12 — The instrument is part of the cost it measures

- With the tag on, the `LINE` block (band metres, priced metres, slow metres, shares, min depth, the gate
  edge reading with its 8-bearing auditor) costs 550–700 ms per rung and the sweep's `SWEEP mark` report adds
  1 100+ lines per rung, all after `instrumentCoarseWalk`. None of it is paid with the tag off, but every
  device budget so far was taken with it on, so every recorded wall is inflated by about a fifth. P3 of the
  2026-10-14 plan (time the corner pass, the clock, the probe) must print those timings **outside** the trace
  closures or it measures itself.

### F13 — Known and parked: overlapping the two-sided pull

- The pull-cost plan's F1 (run the forward and backward walks concurrently) is parallelism, not work removed;
  with three rungs already concurrent the phone's cores are contended (the three A\* phases finished 311, 333
  and 449 ms apart on the same grid). Worth measuring only after F4 and F6 have cut the walk itself.

### F14 — A tunable, exact in sum: the berth proof's reach

- `BERTH_PROOF_REACH_M = 150` caps an open-water span proof at 300 m, so a 2 km chord pays ~8 far-edge reads
  (`farEdgeReads` 2.9–3.5 k per `PULL`). A proved span adds the same double the same number of times whatever
  proved it, so raising the reach changes no sum bit; each far read then scans a wider edge reach, so the
  trade is a device measurement, not a certainty.

## 4. What must not be touched (precision is out of scope, so these are exclusions)

- Swapping haversine for any of the six local metric frames inside the corner pass, the clock or the pull:
  the metres differ at the 1e-5 level and feed thresholds and bisections.
- The three 24-step corner bisections and the clock's boundary bisections as algorithms (their *reads* may be
  memoised, F7/F8).
- The coast kernel in the rasterizer (F10's note).
- An incumbent on the crossing re-solve's A\* (F5's note).
- `lineEntersZone`'s constant 25 m step is a contradiction in the repo — `RouteEta` states that a sampler's
  step is the caller's — and it can miss a ring narrower than 25 m; behaviour, not speed, so it is named here
  and not proposed.

## 5. Code health

- **Size.** `MultipassPull` 1 709 lines, `RouteAvoidEngine` 1 459, `MultipassGrid` 1 088, `RouteGridBuilder`
  986, `RouteCornerPass` 911, `WalkLattice` 874; functions over 150 lines: `attemptCorner` 174 (17 parameters
  plus a lambda and five nested functions), `buildLayeredGrid` 172, `solveCrossing` 154, `warmOutward` 144,
  `buildGrid` 129, `rasterizeFrame` 124. Roughly half of every file is KDoc that narrates history.
- **Parameter threading.** `solveCrossing` 24 parameters, every one copied out of `GridContext`;
  `rasterizeFrame` 22; `fineWindowGrid` 18; `rasterizeWindow` 17; `fineTileKey` 14; `buildFineTile` 13;
  `GridContext` itself 27 fields. The fine-layer bundle (margin, zone margin, priced rings, band law, mask, min
  depth, gate, collar reach, marker strength) is derived in `buildLayeredGrid` and again line for line in
  `fineTilesOver` — one `FineLayerSpec` value derived once removes the duplication, ~40 parameters, and is
  where F11's once-per-arm stamps belong. A corner-pass context (pace constants, world, gate, margin, ends,
  the precomputed turn and leg arrays) does the same for `attemptCorner`/`wallsAround`.
- **Duplication.** `costField`'s `priceSec` and `armAt` lambdas compute the band and zone prices twice, line for
  line; `PullSetup` is constructed with the same clearance/price-step commentary three times (`runPass`,
  `refineOriented`, `solveCrossing`); the `zonePriceSec` lambda wrapping `slowWaterPriceAt` twice; the
  search → pull → snap → pull chain hand-written three times with two symmetry devices; five "excess = legTime
  − dist/pace" loops; `midpoint` inline four times; `chordPointAt` re-spelled in `RouteCorridorChain`,
  `lineEntersZone` and `RouteApproachPass`; `bandMetres`/`bandPricedMetres` one loop with two thresholds; the
  even-odd crossing loop three times (the review's item); six private equirectangular frames and
  `MultipassPull`'s own `M_PER_DEG = 111_320` against `EARTH_RADIUS_M·π/180 ≈ 111 195` everywhere else;
  `strictestLimitKnAt`/`strictestDisplayLimitKnAt` as unindexed free functions **and** as `ZoneSet` methods,
  the free pair being what the live world's hot path still calls (F7).
- **Dead or test-only in production code** (grep: definition file only): `SeamNeighbours.acrossFromCoarse`,
  `WalkWindows.crossLayerSlots`, `WalkWindows.passableCount()`, `WalkWindows.sharesIndexWith` (admitted D59),
  `speedZonePriceClearanceM`, `speedZoneCollarLimitKnAt`, `RouteDepthReading.gateEdgeM`,
  `RouteGridBuilder.fineTileBoxes` and the 5-argument `fineWindowBoxes`, `StoreFullness.atCeiling`,
  `SearchOutcome.costSec` (unread), `RouteAvoidEngine.lineLengthM` (a bare alias of `polylineLengthM` followed
  by six blank lines). `RouteEvolutiveEngine` is pure delegation — the registry already passes the plan, so
  the row could construct `RouteAvoidEngine(plan = EvolutiveGridPlan)` and the class and its test go.
- **KDoc the code no longer honours.** `RoutePassRunner`: "both shipped cells stand well above it" (F4);
  `RouteFinePass`: "run once and after the λ loop" (no loop; once per rung); `FineTile`: the halo is "a margin
  no shipped build asks for" (`evolutive` with the gate armed asks one); `FineTileMap.hitRate`: "a completed
  cache entry" while store-served requests count as hits; `ZoneSet`: "a bounding-box reject ahead of every
  ring walk" while four callers walk unindexed; `TimedLine.legSpeedsMps`: "the enforced limit at the leg's
  midpoint" while the profile stores `d / t`; `slowTimeByLimit` narrates a removed fallback beside an
  `// unreachable`; the 2026-10-14 plan pins its evidence on `RouteSelectivePerfEvalTest`, which does not exist
  in the tree (only the relational assertions in `AvoidPriceWalkTest` stand).

## 6. Recommended order

By measured weight, exactness and blast radius; every step keeps the drawn line byte-identical and is pinned
on the existing symmetry and walk fixtures before a device pass.

1. **F15 + F1 + F2 + F3** — the tile cache: the per-tile harvest first (or the cache serves wrong water), a
   content stamp over the coastline **data** (the cache copy is re-encoded, so the asset's bytes are not it),
   a per-tile zone and marker key, the pre-warm gated on a complete world. Pays on every launch and every cold
   arming; the two defects of this evaluation.
2. **F4** — a hard-read counter on the device first, then the clearance step above the sampling step, with the
   approach pass's own step split off. The largest exact per-rung cut where the capture confirms it.
3. **F6** — one memo per pass and per fine pass (the open P2), with the guard field built once.
4. **F5** — the shared crossing raster per arm; P1 dropped.
5. **F9** — the flattened walk for the A\*.
6. **F7 + F8** — the corner pass's set-backed zone read and single composite; the clock's one walk.
7. **F10 + F11** — the rasterizer's one sweep and bucketed fills; the small reads.
8. **P3** — the instrument extended to the corner pass, the clock and the probe, printed outside the trace
   closures (F12), so the next budget is the solve's own.

## 7. Strongest objection

- The ranking of F4 over F6 and F5 rests on `clearMs` being the coast query's own cost; if the device's
  `distanceToCoastM` is cheap and the `PullTiming` clock is mostly the memo's open-addressing probes and the
  `LatLng` churn, F4 saves less than F6 and the order flips. The 2026-10-26 lines cannot separate the two; a
  `PULL` line printing the hard reads *paid* (as `priceReads` does for the price half) would, and costs one
  counter. F1–F3 are unaffected by that uncertainty: a cache with zero hits saves nothing whatever the reads
  cost.

## Implemented

—
