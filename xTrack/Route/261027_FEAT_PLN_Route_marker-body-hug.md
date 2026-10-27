# Marker body hug — a costed marker gets an edge, its collar stays a lean

## Request

2026-10-27. After reading the last device search (Le Village Englouti → Golfe Juan, 12:10:54–12:11:09): the
Balanced rung keeps a bend in open water at 43.51450, 7.10271 and every rung crosses the grade‑2 pin's
collar at 43.51236, 7.07650 (a 300 m proximity override on that marker; the shipped pin default is
`marker.proximity.pinM`). The user's requirement: "the marker cost handled better and the pull doing its
job — no bend out in the middle of the bay; get around the marker cost, then pull it."
**Decision (user, 2026-10-27): hug the body, the collar left as a priced lean.**

Reviewed 2026-10-27 (independent `ask` pass, 15 findings); the design below carries them. The one piece
the review cut is recorded under *Open decision* and is not part of this plan.

## What the trace established

- The two bends east of the island are kept by `SWEEP keep … why=PRICE`; the arms along the refused chords
  are band 1852 s / zone 29 s / marker 4.5 s and zone 719 s / band 534 s / marker 5.9 s. The marker is
  under 0.3 % of either refusal; the band at the island's east tip and the 10 kn zone off the far shore do
  the refusing.
- The mid‑bay bend is where the greedy pull's chord broke, 2 km from either obstacle. The snap's reach
  (two cells, the band offset, the zone margin plus a cell) cannot carry it to a corner, and no pass moves
  a kept bend to the edge that kept it. The same defect stands for a zone, the band, or a wall marker.
- A marker has no corner set at all ([`RouteGridBuilder.kt:161-171`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt#L161-L171)
  builds coast, band and zone‑ring sets), so the snap is blind to it; its price is λ‑free and small
  (grade 2 × `route.marker.costPerStep` 0.2 × a 300 m collar ≈ 4 s at 30 kn; grade 9 ≈ 35 s).
- The marker's price law needs no change for the hug: under today's law a 1 km grade‑9 body costs
  1.8 × 65 s ≈ 117 s at 30 kn against a detour of about 37 s, and a grade‑5 body 65 s, so the snap's
  total‑cost proof already keeps a central crossing out of a body once a corner exists to move to.
- `261026_FEAT_PLN_Route_snap-cost-and-sharp-windows.md` changed two comparisons; it gave the marker no
  edge and no corner, so it could not deliver this.

## Design

Two pieces, each a pass's own concern; no new properties key, no change to the price law, no tile change.

### A. The body corner set — `TangentCorners.bodyCorners(markers, offsetM, cellM)`

- A fourth `CornerSet`, built beside the zone‑ring set in **both** `RouteGridBuilder` seats — the single‑grid
  seat (lines 161–171, which `avoid` shares, so its coarse snap gains the set too) and the walk seat
  (357–367). The markers are `world.routeMarkers()` kept where the **bounding box of body plus collar**
  intersects the corridor box — never a centre test, so a body straddling the box edge keeps its corners.
- `RouteMarkerGeometry` has two variants only; a Pin reaches the engine as `Circle(position, 0.0, proximityM)`
  ([`MultipassWorld.kt:326`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt#L326)).
  **Circle with `radiusM > 0`** → points on the circle of radius `radiusM + offsetM`. **Circle with
  `radiusM <= 0`** (a pin) → no corner: a point has no body, it leans only — the decision's own consequence.
  **Corridor** → the stadium outline: the two sides at `widthM / 2 + offsetM` and the two cap half‑circles,
  sampled as one closed outline. **Wall (grade 10)** → the same body corners: the wall is where a hug is
  most needed and today it has none, since a wall is a rastered step and not a coast edge.
- Sampling: a step of `cellM` along the outline, floored at 8 points and capped at 64; where the cap bites
  (a 1 km circle at 20 m would ask 320) the spacing grows to the perimeter over 64, about 100 m there, which
  stays inside every candidate reach below. Deterministic: the first sample sits due north of a circle's
  centre, at `p1`'s cap apex for a corridor.
- `offsetM = marginM`, the pull's own margin — a standoff like the coast set's (`corners(edges, openCoast,
  marginM)`), not the cell; a wall's hard source is a step (`0` inside, `MAX_VALUE` outside), so `marginM`
  never binds on a wall body and the standoff is what keeps a corner‑to‑corner leg off its marks. The offset
  is one value per marker, so the walk seat's two cell sizes cannot give one circle two offsets.
- The set's radius is **per corner**: the owning marker's `proximityM + cellM`, so a bend inside a marker's
  collar is offered that marker's body corner and a bend beyond the collar is not. `CornerSet.radiusM` is
  already `(LatLng) -> Double`; the builder keeps a corner → marker map behind it.
- The snap's existing proofs decide: both legs clear, total travel plus penalties not dearer. A corner that
  does not pay is refused and the bend stays — the collar is a lean, never a wall.

### B. The relocation — a bend kept by the sweep slides onto the edge that kept it

- **The record.** `MultipassPull.sweep` gains one optional parameter, `keeps: MutableList<KeptBend>? = null`;
  its return type and every existing call and test are unchanged. A `KeptBend(at, worstAt, arm)` is recorded
  once per kept vertex — on the **last** pass only, memoised on the triple `(a, b, c)` so a vertex re‑tested
  by the fixpoint loop is walked once — for **both** keeps: a `PRICE` keep's `worstAt` is the chord's dearest
  mark and `arm` the arm that charged most there; a `LAND` keep's `worstAt` is the chord's **first refused
  mark** (`legClearCause` already stands on it) and `arm` is `null`. A wall marker refuses by `LAND`, so this
  is what gives a wall its hug.
- `traceArms`'s walk is factored into `worstChargeOn(a, c, ctx): ChordCharge?` (arm, point, per‑arm sums),
  read by the trace line and the record alike, so the report and the decision cannot disagree. It runs only
  where a recorder or a live trace asks; the sweep's second run below is handed no recorder and keeps
  `traceLive`'s gate, so the cost is one lattice walk per kept vertex per fine pass.
- **The pass.** New `relocateKeptBends(setup, sets, line, keeps, trace, checkCancelled)` in
  `RoutePassPrimitives.kt` beside `snapToCorners`, sharing its proof body. Left to right, once. For a kept
  vertex `b` with worst point `w`, the candidate is the corner, across all sets, **nearest to `w` and nearer
  to `w` than `b` is** — the reach is the bend's own distance to its obstacle, not the set's snap radius,
  because a mid‑bay bend stands kilometres from the corner it should hold. The candidate is proved against
  the **current** neighbours (the already‑relocated predecessor, the not‑yet‑relocated successor): both legs
  clear, total not dearer than the two legs it replaces; a corner equal to either neighbour is refused so no
  zero‑length leg is written. Refused → the bend stays. Cancellation is read every `CANCEL_CADENCE` vertices,
  as the sweep reads it.
- **Direction.** The pass is predecessor‑settled, exactly the shape `inCanonicalDirection` exists for; the
  runner's snap is wrapped and the fine pass's snap and sweep are not. `relocate` runs wrapped —
  `inCanonicalDirection(swept, setup) { line, water -> relocateKeptBends(water, sets, line, keeps, …) }` —
  with the keeps' `at` matched to the line's vertices by position, so a pair armed from either end relocates
  one line.
- **Order** in `RouteFinePass.refine`: `sweep(keeps) → relocate → sweep` (one round, bounded), then
  `straightenLine` as today. The second sweep removes the neighbour the relocation freed and is run with the
  same `settledMemo`, so a retraced mark is answered rather than paid again.
- **Report.** One `RELOCATE idx=… from=lat,lon to=lat,lon arm=BAND|ZONE|MARKER|DEPTH|LAND` line per moved
  bend and one `RELOCATE keep idx=… why=LAND|PRICE|NONE` per refused or candidate‑less one, then
  `RELOCATE in=… moved=… refusedClear=… refusedPrice=… none=…`. Refusals are the pass's own counts; the
  pull's `PullRefusals` tally is not touched.
- The runner's coarse pass gains the body corner set (A) and nothing else: only the fine pass is swept and
  relocated, and the fine line is the one the corner pass draws.

### Fixed beside the design

- `maro.properties` lines 224–233: the comment over `route.marker.costPerStep` says "Shipped at 1.0" above
  a value of `.2`; the comment is rewritten to describe the shipped value (ONE HOME PER FACT). The value is
  not moved.

## Open decision — not in this plan

- The body's **price** stays today's law (`step × strength`, 1.8 × travel time at grade 9). A dearer body
  law was proposed and cut in review: it changes which line the A\* draws for grades 6–9 everywhere, makes
  `route.marker.costPerStep` inert for those bodies, and needs `boundaryDistanceM` to declare the body edge
  as well as the collar's for the price walk's group proof to stay sound. That is a change the user sees,
  so it is the user's decision; if taken it is its own plan.

## Target files

- `app/src/main/java/ykws/android/maro/spatial/multipass/TangentCorners.kt` — `bodyCorners`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the fourth set, both seats, the bbox filter
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassModels.kt` — `KeptBend`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `worstChargeOn`, the sweep's `keeps` recorder (both keeps)
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt` — `relocateKeptBends`, the proof body shared with `snapToCorners`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt` — `sweep(keeps) → relocate (canonical) → sweep`
- `app/src/main/assets/maro.properties` — the `route.marker.costPerStep` comment
- `app/src/test/java/ykws/android/maro/spatial/multipass/TangentCornersTest.kt` — circle, corridor stadium, pin (none), wall body; the cap's spacing; determinism
- `app/src/test/java/ykws/android/maro/spatial/multipass/CornerSnapCostTest.kt` — a bend inside a collar is offered its body corner and taken only when it pays; a bend beyond the collar is not offered it; a wall body's corner‑to‑corner leg clears at the mark step
- `app/src/test/java/ykws/android/maro/spatial/multipass/SweepRelocationTest.kt` (new) — a `PRICE`‑kept bend in open water moves to the obstacle's corner and the second sweep drops its neighbour; a `LAND`‑kept bend moves to the wall body's corner; a relocation crossing land is refused; a dearer relocation is refused; a corner equal to a neighbour is refused; the record is taken once per triple; the same line from either end; determinism
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidSweepWindowTest.kt` — unchanged calls still compile (the recorder defaults to none)
- `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt` — expected to move where the coarse snap now sees body corners
- `xTrack/Route/FEAT_DOC_Route_engines.md` — the geometry, pull and field bullets
- `xTrack/Route/FEAT_DSC_Route.md` — a current‑state bullet pointing here

## Implemented (2026-10-27, `#implement`)

- **The body corner set** — [`TangentCorners.bodyCorners`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TangentCorners.kt)
  samples a circle's ring or a corridor's stadium at `marginM` off the body, one point per cell, floored at 8
  and capped at 64, each a `BodyCorner` carrying its marker's collar; a pin (radius `0`) yields nothing and
  a wall is a body like any other. [`RouteMarker.reachBox`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt)
  is the body-plus-collar box, and `bodyCornerSet` in [`RouteGridBuilder.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt)
  builds the fourth `CornerSet` in both seats from the markers whose box meets the corridor (priced ones
  only while `route.marker.enabled`, walls always), reach `proximityM + local cell` per corner.
- **The keeps record** — `MultipassPull.sweep(…, keeps)` records each kept vertex of its last pass as a
  `KeptBend`: the chord's dearest mark through `chordCharge` (the arm report's own walk, now one home for
  the `SWEEP mark` / `SWEEP arms` lines and the record) for a price keep, the first refused mark through
  `legRefusedAt` (the clearance walk answered with its point; `legClearCause` and `legClear` read it) for a
  land keep.
- **The relocation** — [`relocateKeptBends`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt)
  slides each kept bend onto the corner nearest its mark and nearer it than the bend is, on `moveVerdict`
  (the proof body the snap now shares) against the current neighbours, a neighbour-equal corner refused,
  `RELOCATE` lines and counts of its own. `RouteFinePass` runs `sweep(keeps) → relocate` (in
  `inCanonicalDirection`) `→ sweep`, the second sweep only where a bend moved.
- The `route.marker.costPerStep` comment describes the shipped value; the price law is untouched.
- Deviation from the plan: the body-corner tests live in a new `BodyCornersTest.kt` and the snap tests in a
  new `MarkerBodySnapTest.kt` rather than inside the two existing suites, so the existing fixtures stay as
  they were.

## Verification

- `gradlew :app:testDebugUnitTest --tests 'ykws.android.maro.spatial.multipass.*' --tests 'ykws.android.maro.spatial.RouteAvoidEngineTest' --tests 'ykws.android.maro.spatial.RouteEvolutiveEngineTest' :app:assembleDebug`
  on 2026-10-27: 439 tests, 3 skipped, 0 failures; the debug APK assembled. The three new suites —
  `BodyCornersTest` (6), `MarkerBodySnapTest` (3), `SweepRelocationTest` (6) — pin: a pin yields no
  corner, a circle's ring and a corridor's stadium with their first sample, the floor and the cap, a wall's
  set equal to a priced body's, the reach box; a bend inside a collar takes the body corner where it pays,
  a bend beyond it is not offered one, a wall body's corner-to-corner leg clears; a price-kept bend moves
  to the edge and the next sweep drops its freed neighbour, a land-kept bend moves to a wall body's corner,
  a land-crossing move and a dearer move are refused, a neighbour-equal corner is never taken, one record
  per kept vertex, and the same line from either end.
- `RouteAvoidEngineTest` did not move: the suite's fixtures carry no costed marker.
- The pipeline's `ask` review hop (feature coverage, then code health) found **no verdict-changing
  defect** and confirmed the `legRefusedAt` refactor returns the point at all four sites with every caller
  still reading the cause. Its findings stand open, none acted on under the pipeline's no-ping-pong rule:
  1. A kept vertex is walked twice — `legClearCause` then `legRefusedAt` for a land keep, `chordCharge`
     inside `traceArms` then again after the loop for a price keep when the trace is live — against the
     plan's one-walk claim. Fix: call `legRefusedAt` in the loop and carry the point in `KeepSite`; keep the
     `ChordCharge` the trace computed.
  2. `theSameLineRelocatesTheSameFromEitherEnd` is tautological (`a` sorts first, so both calls relocate
     the same oriented line), and the `inCanonicalDirection` wrap in `RouteFinePass` is inert because
     `refineOriented` has already turned the line; the test should call the reversed setup directly or the
     wrap and test go, with the KDoc naming the fine pass's orientation as what carries the symmetry.
  3. The snap tests carry no refused-by-price body case; the relocation's refusal tests assert the line
     but not the `why=` cause; "one record per kept vertex" is proved on a one-pass sweep only.
  4. `stadiumCorners` relies on side-effecting conditions to advance `s`; four cumulative breakpoints
     would read plainly.
  5. `bodyCornerSet`'s `reach[p] ?: 0.0` fails silent on a missed corner, and two markers sampling one
     point overwrite each other's reach — `getValue` and a `max` on insert.
  6. The sweep's KDoc still says the fine pass calls it once; the second sweep's summary line is
     indistinguishable from the first except by position — tag it.
  7. `RELOCATE_CANCEL_CADENCE` and `pointText` duplicate `CANCEL_CADENCE` and `atText` in
     `MultipassPull` (ONE HOME PER FACT) — make those `internal` and read them.
  8. The rewritten properties comment restates "0.2" above the key that carries it — say "the shipped
     value"; `AppConfigStrength` in the snap test is a literal named as a config read; the corridor
     fixture sits exactly at the cap rather than past it.
  9. Limits to state: with the 64 cap a 1 km body samples every ~100 m, so a marker with a collar narrower
     than that can be offered no corner; the walk seat samples at the coarse cell even in fine water; the
     relocation tries one candidate only.
- A device replay of the same search, read from the `MaroRoute` trace. Pre‑check first: the band set must
  hold a corner at the island's east tip — `addTangent` drops a corner whose `offsetM / sinHalf` exceeds its
  cap, so a tip sharper than roughly 20° has none, and then the relocation has nowhere to move the mid‑bay
  bend and the finding is the tip, not the pass. With a corner present: the Balanced rung's bend at
  43.51450, 7.10271 is gone or stands on that corner, the `RELOCATE` line names `BAND`, and the pin still
  leans (grade 2, no body). Deployment and the run are the user's.
- No tile change: the price law is untouched, so the fine tiles' pace‑free factor and their keys are as they
  were ([`DirectoryFineTileStore.kt:20-23`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt#L20-L23)
  folders tiles per installed build in any case).

## Limits

- A pin never hugs: by the decision it has no body. A user who wants a point avoided draws a circle.
- The relocation is one round and local: it moves a kept bend to the nearest paying corner of the obstacle
  that kept it and never searches for a globally better line. A kept bend whose obstacle has no corner
  (a dropped tip, a ring too smooth to carry one) stays where the chord broke.
- The hug's strength is the body's price, which this plan does not move; a grade‑1 or grade‑2 body is cheap
  enough that the proof may still take the crossing. That is the lean the user chose, and the open decision
  above is where a dearer body would be decided.
