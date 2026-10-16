<!-- scope: feature -->
# Route — retiring the `selective` engine

**Date:** 2026-10-16 · **Status:** implemented — the code half shipped (`selective` is out of the tree, gates
green); P5's corpus record and `#archive` moves wait on the user's word · **Order:** the user's word of 2026-10-16 —
*"as the selective algo is showing poor performance, plan to remove it completely from the app/code/ui"*.

**Scope:** the complete retirement of `selective` — its row, its plan, its depth price, its depth-dilation
collar, its keys, its strings, its tests and its corpus — leaving the **shared** depth gate, its margin and
the fine-cell-over-coarse-copy rule exactly as they are for `avoid` and `evolutive`.

**Why it goes:** the engine's own measurements put it behind, not beside, the adaptive one — the fine-layer
build ≈8× dearer per build on **fewer** fine cells and built three times an arming before the single-flight fix
(`xxArchive/261008_FEAT_PLN_Route_selective-perf-eval.md` §3a), a warm arm still 1.39× and 2.12× `evolutive`'s on
the short and long pairs, and its depth source's declared **zero clearance** degrading every price walk it
touches to per-interval reads (`priceReads / marks ≈ 1` against `evolutive`'s 0.56). Its whole reason to exist was
the depth preference, and that preference buys about a second of charge per crossing.

## Where depth lives today — the split this retirement must respect

- **The gate's wall is the shared level's** — `depthGateSource(route.avoid.depthGate.minM)` is a **Hard** source
  added inside [`costField(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:50)
  and again inside [`tileCostField(...)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:206),
  which the fine tiles are built from. `avoid`, `evolutive` and `selective` all stand on the one pipeline and the
  one field, so **the adaptive engine's depth handling is this wall**, not an algorithm of its own.
- **The margin is the shared level's too** — `route.avoid.depthGate.marginM` is read by the corner pass
  (`depthClearsGate`) and by the berth carve, for every engine alike.
- **The depth *price* is `selective`'s alone** — the door `withDepthBand` defaults false in
  [`costField`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:41) and only
  [`SelectiveGridPlan.pricesDepthBand`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:159)
  answers true, so the ramp, its 25 m extra band and the `PriceArm.DEPTH` arm all ride the shared pipeline without
  being shared behaviour.
- **The depth-dilation collar is the plan's own** — `route.selective.depth.collarM` feeds `FineWater.depthCollarM`
  and the fine mask's radial scan, one of the plan's four collars; `evolutive`'s fine water is the coastal ribbon
  and knows no depth collar.

**Consequence:** retiring `selective` takes a **preference** away and no **rule** — the wall, the margin, the
marker price and the fine-over-coarse reading are untouched.

**What this reverses, and what it does not.** The price went to a **new engine by the order's own conservatism** —
the user's word of 2026-10-07 was *be conservative and derive yet another algorithm `selective`, keeping in common
all we can, and make this evolution on that one* — with `avoid` kept the shipped default and `evolutive` the
reference the new engine is judged against. Nothing in the code forbade the flag elsewhere: `withDepthBand` is a
door every engine's `costField` takes, so the scope was a choice, and the closing of it is a choice too. The
overruling argument is the measurement: the price landed on the engine the user does not drive, where the charge
is invisible, the declared-zero clearance is paid by that engine's own walks, and the arm cost 1.39×–2.12× the
adaptive one warm. This retirement therefore reverses the **scoping** decision, not merely the engine's row — and
the wall, the margin and every other engine's arithmetic are outside it.

## What leaves — the inventory

| Touchpoint | Where |
|---|---|
| The engine row | [`RouteEngineChoice.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:70) — id `selective`, `route_engine_selective`, `SelectiveGridPlan`, its factory; the class [`RouteSelectiveEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteSelectiveEngine.kt:29) whole |
| The labels | `res/values/strings.xml:780`, `res/values-fr/strings.xml:782` |
| The plan | [`RouteGridPlan.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:147) — `SelectiveGridPlan`, `fineWater`, `union`; the seam member `pricesDepthBand` and every reader of it |
| The depth price | [`RouteFineWater.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt:91) — `depthBandOf`, `DepthBand`, [`DepthBandLaw`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt:120); the `withDepthBand` door and the depth arm source in `RoutePassPrimitives.kt:157-178` |
| Its plumbing | `RoutePassRunner.kt:81`, `RouteFinePass.kt:90` and `:182`, `RoutePassModels.kt:79-81` — the `depthBandActive` reads |
| The collar and the mask | `MultipassGrid.kt:576-578` (the collar width, the band width and the radial scan's own door), [`SelectiveMaskCache`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFineWater.kt:193), the selective-only `FineWater` fields (`zoneRimM`, `depthCollarM`), the builder's selective-only marking passes and the `bandExtraM` frame value (`RouteGridBuilder.kt:245-247`, `:445`, `:675`) |
| The keys | `maro.properties:315-352` — `route.selective.grid.cellM`, `.grid.fineCellM`, `.shore.collarM`, `.band.collarM`, `.zone.rimM`, `.depth.collarM`, `.depth.bandExtraM`, `.depth.priceSecPerM`; the seven accessors at `AppConfig.kt:380-437` and their reads at `:2387-2414` |
| The tests | [`RouteSelectivePerfEvalTest`](../../app/src/test/java/ykws/android/maro/spatial/RouteSelectivePerfEvalTest.kt:82) whole (the JVM harness, the P2.1 split variants, the pinned record) and [`RouteSelectivePlanTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteSelectivePlanTest.kt:26) whole — **but `theWindowMaskReproducesEvolutivesCoastRibbon` (SF7) is `evolutive`'s own byte-identity check and must land somewhere live** |
| The tests that merely name it | `RouteEngineChoiceTest.kt:45-48`, `FinePriorityWalkTest.kt:25-27`, `LatticeTestAnchors.kt:8`, `MultipassGridShapeTest.kt:8` — re-pointed prose, rules kept |
| The corpus | `FEAT_DSC_Route.md` (the four-engine bullet, the known limits, the two deliberate limits, the key files, the todos, the owed passes), `FEAT_DOC_Route_engines.md` (the shipped-engines table, the plan seam, the depth preference, the collars, the values), the decisions doc, the Route card in `GLOBAL_CONTEXT.md`, and `docs/maro-code.md` **to be read** before claiming it is clean |
| The plans that die with it | [`261007_FEAT_PLN_Route_depth-gate-soft-band.md`](261007_FEAT_PLN_Route_depth-gate-soft-band.md) (its scope was `selective`), the point-4 clock reading in [`261007_FEAT_PLN_Route_coastal-fine-grid-engine.md`](261007_FEAT_PLN_Route_coastal-fine-grid-engine.md), and the depth arm's standing (P3/P4) in [`261015_FEAT_PLN_Route_price-meaning.md`](261015_FEAT_PLN_Route_price-meaning.md) |
| The owed device passes | the `selective` engine's coastal pass and the depth band's keys among the passes listed in `FEAT_DSC_Route.md` |

## What must not move

- **The gate wall** at both fields, the **margin** at the corner pass and the berth carve, the **marker price**
  and its `.MARKER` arm, and the **fine cell over the coarse copy** reading rule.
- **A stored `selective` id** must resolve through the shipped fallback rather than fail — `route.engine.id`
  names the shipped engine and an unclaimed id falls back to it, so the retirement needs a witness that a stored
  `selective` lands on the default, not a migration.
- **No line, clock or ranking of `avoid` or `evolutive`** may move, the deletion of the collar being the one place
  this is in question (see the halo below).

## Phases

1. **P0 — the record.** This file; the decisions doc's retirement entry naming the reversal of the 2026-10-07
   decision that created the engine (points 1 and 4 of the algorithm review); the feature file's items closed.
2. **P1 — the row.** The engine class, the choice entry and the two labels, with the fallback witness.
3. **P2 — the plan and the seam.** `SelectiveGridPlan` and `pricesDepthBand` with every reader, so the seam keeps
   one shape.
4. **P3 — the price and the collar.** `DepthBandLaw`, `depthBandOf`, the `withDepthBand` door, the depth arm,
   the collar's radial scan, `SelectiveMaskCache`, the seven keys and their accessors.
5. **P4 — the tests.** The harness and the plan's fixtures retired, the ribbon witness re-homed, the prose
   re-pointed.
6. **P5 — the corpus and the backlog.** The feature file, the engines doc, the card, the maro-code map, and the
   plans that go with it moved through `#archive` on the user's word.
7. **P6 — the gate.** `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug` green, with
   `evolutive`'s line re-pinned beside the pre-retirement record.

## Risks and the reads owed before P3

- **The halo may shrink.** `fineTileHaloCells()` takes `max(depthCollarM, bandM)` today, so deleting the collar
  can narrow the halo `evolutive` also walks — which moves its tiles, its fine cells and possibly its **line**.
  The read: build `evolutive` on one fixed pair before and after, and compare the line, the tile count and the
  key material. This is the retirement's one behavioural risk, and it is not a `selective` question.
- **The tile key's material.** Whether [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt)
  or its codec carries a selective-only value (a collar width, the depth band's extra) — a read owed; if it does,
  its retirement is a key change, so stale entries become misses rather than wrong tiles.
- **The harness's pins retire with the engine**, and its record is the repo's own `test-*.log` files: the
  re-pin of `evolutive`'s line is the one number this retirement must carry forward.
- **`docs/maro-code.md`** may name the engine or its files — read before asserting the map is clean.

## Open questions

- **Where does a depth preference live once `selective` is gone?** This file decides the negative half only — the
  selective home goes — and hands the positive one to
  [`261007_FEAT_PLN_Route_depth-gate-soft-band.md`](261007_FEAT_PLN_Route_depth-gate-soft-band.md), whose own
  update of 2026-10-16 records that its ask now has no home in the tree: the choice is a soft preference on
  `evolutive` — the engine the user actually drives — or depth stopping at the shared wall and its margin.
- **A stored `selective`** — silently falling back, or a write that clears the id once.
- **Does the fine layer keep a depth price at all?** The marker price stays on the fine tiles whatever happens,
  so the fine-over-coarse rule survives either way; the question is only whether depth joins it.

## Implemented

2026-10-16 — **`selective` is retired root and branch.** The row, the plan, the depth price, the collar, the
keys, the labels and its two suites are gone; `avoid` and `evolutive` answer what they answered before.
`.\gradlew.bat testDebugUnitTest` (1281 tests, none failed) and `.\gradlew.bat assembleDebug` are green.

- **The read the plan owed first came back nil, so nothing of `evolutive` moved.** `EvolutiveGridPlan.fineWater`
  answers `0.0` for both widths the tile halo took `max` of and never prices the band, so its halo was already
  zero and no tile, fine cell or line of `evolutive` could move — no re-pin was needed, and the harness that held
  the old warm-arm record retired with the engine.
- **The tile key did carry the collar, so the key change is a miss and never a wrong tile.** `TileKey` keyed the
  collar widths and the band's extra, and the codec's field list lost the depth coefficient with
  `FORMAT_VERSION` 2 → 3, so an entry built before this change is orphaned rather than served.
- **Both witnesses live on.** `theWindowMaskReproducesEvolutivesCoastRibbon` is re-homed on `evolutive` in
  [`RouteFineWindowShapeTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineWindowShapeTest.kt),
  the one plan that keeps a fine layer, and the fine-over-coarse witness in
  [`FinePriorityWalkTest`](../../app/src/test/java/ykws/android/maro/spatial/multipass/FinePriorityWalkTest.kt)
  now rides the marker price. `RouteEngineChoiceTest` gains the witness that a stored `selective` id lands on the
  shipped default instead of failing.
- **The delivery report carries two open items.** Two prose lines inside the code this retirement touched still
  name the deleted collar — `rasterizeWindow`'s mask note and `TileKey.gateMarginM`'s own line — and the
  price's plumbing deeper than this plan's inventory (the grid's coefficient, the tile's stored field, the
  codec's byte and the search's `depthK`) went with the law, `DepthBandLaw` being unable to leave with a live
  reader.
- **Owed, on the user's word (P5).** The corpus record — `FEAT_DSC_Route.md`, `FEAT_DOC_Route_engines.md`, the
  decisions doc, the Route card in `GLOBAL_CONTEXT.md`, `docs/maro-code.md` — and the `#archive` retirement of
  the plans that die with the engine.
