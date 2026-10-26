# Routing algorithm — deep review and fixes (2026-10-26)

**Scope.** The route acquisition pipeline as it stands on `feature/clock-and-copy`: the engine
(`spatial/RouteAvoidEngine.kt`, the `evolutive` composition over it), the shared `multipass` layer (the
grid builder and rasterizer, the lattice family, the A\*, the two-sided pull and its proofs, the sweep
and straightener, the fine pass, the medial nudge, the corner pass, the clock) and the ranking. The
view model and the drawing seam were read for how they drive the engine and are not changed here.

**Method.** One full read of the ~16 000 lines by the author of this document, then two independent
reviews by fresh readers (one over the search core, one over the post-search stages), each briefed on
the symmetry requirement and told to verify by reading, not to speculate. Every finding below was
confirmed in source before it was acted on; the one numeric claim that could be checked offline — the
corner pass's fitted speed — was reproduced in a port of the curve before the code moved.

**Constraint.** No JDK on the reviewing machine: the suite runs on the repository's CI workflow, and the
direction-symmetry instrument (`RouteDirectionSymmetryTest` over the baked region) cannot run at all
without the gitignored bake. Where this document says *measured*, it cites the design record's own
numbers from 2026-10-20/21; where it says *by construction*, it states why the result cannot depend
on the direction.

## 1. Verdict

- **The algorithm is sound; its symmetry was partly convention and partly luck.** The search already
  ran from a canonical end and the pull kept the cheaper of both walks, so a pair's cell chain and taut
  line were one line from either end. Two things still made the drawn answer, or the crown, depend on
  the arming direction or on thread timing: the ranking folded the rungs in the order their threads
  landed, and five drawing stages after the pull read the line from the arming end.
- **Two defects were real and reachable on the shipped path.** One tally object was shared by the three
  concurrent rungs' pulls, and the fine pass's sweep was gated on it, so whether a rung's line was swept
  depended on what its siblings had refused by that instant. And the corner bow's exit was drawn as a
  second entry spiral, so every rounded corner reached further along the leg it entered by than the leg
  it left by.
- **The hot path paid for readings nobody read.** Trace-only figures were computed with the trace off, a
  full clock was timed per rung and never read, the forced-crossing probe ran one exhaustive A\* per rung
  although it is identical across the three, and the A\* heap boxed every comparison.

## 2. Findings and what was done

Severity is the reviewer's; *status* says what this branch does about it.

| # | Finding | Severity | Status |
|---|---|---|---|
| F1 | `GridContext.refusals` was one `PullRefusals` per arm, incremented by all three rungs' pulls on three threads (lost updates), and `RouteFinePass` gated its sweep on `land + price == 0` — a rung's line could be swept or not by timing. | High | **Fixed.** Each pass and each refinement allocates its own tally; it rides `PassReading.refusals`; the context no longer carries one. |
| F2 | The crown was folded in landing order with a tolerance that keeps an incumbent, so two near-equal rungs crowned whichever thread finished first — run to run, and A→B against B→A. | High | **Fixed.** `RoutePassRanking.settle` folds in ladder order (around, best, fast); the engine reads it. A test shows the plain fold crowning two rungs from two orders and the settled fold one. |
| F3 | `RouteCornerPass.composite` appended `appendSpiral` twice: the exit was an entry clothoid (curvature 0 → 1/r), so the bow ended at full lateral G and its outbound reach was shorter than the inbound. Reproduced offline: the pinned fit of 1.6245 m/s is the asymmetric bow's; the mirror bow fits 1.6642 m/s with 10.000 m on both legs. | High (geometry) | **Fixed.** The exit is the entry spiral run backwards; the pinned test moves to the symmetric figure and two new tests pin equal reaches and reversed-line equality. |
| F4 | The corner snap, the fine pass (its local re-solves, two pulls, sweep, straightener, nudge) and the corner pass read the line from the arming end; the design record measured the residue at 48 m (snap), 21.8 m (fine pass) and 2.3 m (corner pass) on named pairs. | Medium (symmetry) | **Fixed by construction.** Each runs in the pair's canonical direction and is turned back (`inCanonicalDirection`, `RouteFinePass.finePass`, `RouteCornerPass.round`); an engine test asserts A→B equals B→A reversed, point for point, on all three rungs. |
| F5 | The pull's lattices are anchored at a chord's first point, so `softPriceSec(a, b)` and `(b, a)` differed in the last bits and the backward walk's prefix never hit the forward memo. | Medium | **Fixed.** `legClearCause` and `softPriceSec` read the pair in canonical direction: same marks, same double bit for bit, and the reversed read is answered from the memo (test). |
| F6 | `solveCrossing` compared the local line (from the vertex before the box to the one after) against the coarse stretch inside the box — two legs shorter — on a soft-only cost, while every other proof uses travel plus price. | Medium | **Fixed.** Both sides span the same vertices and compare `totalSec`. |
| F7 | A nudged vertex was accepted on its unsigned edge distance alone; a vertex inside the bank, or inside the coast margin, could pass, and a refused stretch closed its leg to a later passage. | Medium | **Fixed.** A moved vertex must be not shallow, outside every rastered wall and clear of the margin (outside the ends' discs); a consumed-leg marker advances on acceptance only. |
| F8 | The haversine heuristic was charged against a nominal cell sized at one reference latitude: towards the equator from that latitude the true east–west spacing exceeds the nominal by `cos φ / cos φ_ref − 1` (≈ 0.4 % at a quarter degree, 43.5°N), so `h` could exceed the true cost and the A\* could close on a chain dearer than the cheapest. | Medium | **Fixed.** `WalkWindows.heuristicScale` scales `h` by `cos φ_ref / cos φ_nearest`, never above one; every fixture and every corridor's own south half read 1.0. |
| F9 | A force-freed end cell kept the `MAX_VALUE` an unseeded cell opens with when the margin had painted it land before the edge was seeded, and the step guard trusted that as *clear*, skipping the measured read for the step out of it. | Low | **Fixed.** The builder measures the end cell exactly as a disc cell. |
| F10 | A sibling started after the arm's last disposal awaited a cancelled shared build and ended with no terminal; `landedRungs` was engine-wide, so a late rung of a superseded arm could land in a fresh arm's set. | Low | **Fixed.** A cancelled deferred is not reused; the landed set lives on the arm's holder. |
| F11 | `timeLineWithLimits` divided by a 0 kn limit or pace; the profile divided by a zero acceleration. | Low | **Fixed.** The leg speed is floored where the profile already floored it; the acceleration is required positive. |
| F12 | `RoutePassRunner` published the SNAP boundary whatever `publishStage` said. | Low | **Fixed.** |

## 3. Performance

Every change below keeps the drawn line and the clock exactly as they were: the work removed was work
whose result was never read, or a read answered from a proof that cannot move a verdict.

| Change | Where | What it saves |
|---|---|---|
| A primitive open heap ordered `(f, g, slot)` replaces `PriorityQueue<Node>` with a `compareBy` comparator. | `MultipassSearch` | One `Node` per push and two boxed doubles per comparison on every sift — the whole A\* inner loop. |
| The zone price is a `fun interface` (`ZonePrice`) instead of a `(Double, Double, Double, Double) -> Double`. | `MultipassSearch`, `RoutePassRunner`, `RouteFinePass` | Four boxed arguments and a boxed result on most relaxations of a coastal corridor. |
| The eight steps are primitive arrays; the cell's centre is read only by a crossing; the seam fills a scratch array; the layer facts and the passable count are settled once per walk. | `MultipassSearch`, `WalkLattice` | An iterator, a `LatLng` and up to five objects per expansion; an `O(n)` recount per walk. |
| The slot index is a primitive `Long → Int` map (`LongIntMap`). | `WalkLattice` | A boxed `Long` and `Integer` per neighbour lookup, 8–24 per expansion on the two-layer walk. |
| The mark memo's four tables are a primitive point → double map (`PointDoubleMap`). | `MultipassPull` | A boxed `Double` per mark stored and read; the same bit-exact keying. |
| Chords read in canonical direction. | `MultipassPull` | The backward walk's prefix reads the forward walk's marks instead of missing every one. |
| The end-disc test is read once per chord off the ends' distance to it, not twice per sample. | `MultipassPull` | Two haversines per fine mark on every chord far from both ends — most chords. |
| The forced-crossing probe is memoised per grid and crossing set. | `RouteAvoidEngine`, `GridContext.probe` | Two of three grid copies and two of three exhaustive A\* probes per arm. |
| The grown corridor is single-flighted per arm like the first build. | `RouteAvoidEngine` | Two of three 4×-area rasters when a growth fires. |
| The clock's zone read scans the corridor's zones through a boxed set, not the region's whole list through the provider. | `RoutePassPrimitives.limitAtFor(world, zones)`, `ZoneSet` | Every ring of the region per clock sample; a ring walk per zone whose box the point misses. |
| The price field's three zone reads reject by bounding box before any ring walk, with a true lower bound. | `ZoneSet`, `costField` | `O(total zone vertices)` per span proof. |
| The timed line and the share split the runner computed per pass, and the published clocks when nothing publishes, are gone. | `RoutePassRunner`, `PassReading` | Three or four clock walks per rung, each a coast query and a zone read per sample. |
| Band metres, priced metres, slow metres, shares, the corner tally and the sweep's arm report are built inside the sink. | `RouteAvoidEngine`, `MultipassPull.sweep` | A coast query per leg and a decomposed price per mark, paid with the trace off. |
| The fine-tile window opens land with one fill; the fine mask's band test is unboxed. | `RouteGridBuilder`, `MultipassGrid` | Per-cell calls and a boxed range read per cell of every fine window. |

What was **not** done, on purpose: the corner pass's three 24-step bisections, the clock's 24- and
40-step boundary bisections, the rasterizer's scanline that walks every edge per row, and the geodesic
helper the coast sweep calls per cell. Each would change a verdict in the last bits or move a cell at a
margin boundary, and this branch promises an unchanged line. They are the next performance batch, to be
taken with the instrument in hand.

## 4. What symmetry now rests on

- **The search** leaves the end that sorts first by latitude, then longitude (unchanged).
- **The pull** keeps the cheaper of both walks, priced in canonical direction (unchanged), and every
  chord proof reads the pair in canonical direction (new).
- **The corner snap, the fine pass and the corner pass** run in canonical direction and are turned back
  (new). The corner bow is its own mirror (new).
- **The ranking** folds the ladder in declaration order (new).
- **The clock** alone runs along the real travel direction, so an ETA may differ by what anticipation
  and acceleration make of the same line in two directions — the one directional reading that is physics.

The engine test `aPairArmedFromEitherEndDrawsOneLineAndOneCrown` pins the whole of it on the island
fixture: three rungs, point-for-point reversal, one crown.

## 5. Open items

- **The instrument.** `RouteDirectionSymmetryTest` and its 24-pair survey should be re-run on a machine
  with the bake; this branch predicts 0.0 m on every stage row and no crown flips, which is a stronger
  claim than the 2026-10-21 run's 0.4 m and worth reading back.
- **Device timing.** `DEVICE PASS`, `PULL`/`FINAL` and `PROBE` lines on a device pair are the real
  measure of §3; the JVM cannot stand in for the phone.
- **Not changed, recorded:** the fine pass's local re-solves and the sweep are still forward greedies
  inside the canonical frame (symmetric by construction now, not canonical in shape); the search's
  destination-rate edge price is directional and the canonical end makes it one convention per pair;
  `KDoc` in `FEAT_DOC_Route_engines.md` said "a metres-equivalent g-cost" where `g` is seconds.
- **Code health left for a later pass:** six local metric frames that could be one; the even-odd crossing
  loop written three times; `RouteSmoothPassTest.kt` holding `RouteStraightenTest`; the pull's setup and
  context re-listing one value set four times.

## 6. Strongest objection

Making the drawing stages canonical buys exact symmetry by convention, not by removing the directional
reads inside them: the fine pass's sweep still commits the first removable vertex it meets and the
corner pass still caps the earlier bend of a close pair. A line drawn from the canonical end is therefore
the line both armings get, but it is not provably the better of the two lines the old code could draw. The
design record measured those stages at 0.0 m and 0.4 m isolated, so the line the convention picks is one
of two lines that could not be told apart on this coast; the objection stands as a limit on the claim,
not a reason to keep the asymmetry.
