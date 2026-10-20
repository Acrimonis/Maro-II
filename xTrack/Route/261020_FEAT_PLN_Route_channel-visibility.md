<!-- scope: feature -->
# Plan — the Bacon – La Grenille channel: EMODnet kept out of the route, ends kept clear, passages centred

**Status:** v8 (2026-10-20) · T, W and **D implemented** · C and S follow the device run · **Feature:** Route · **Created:** 2026-10-20

## The report

> The channel pass never shows up in the route results; it renders as merged with other routes. The last build seems to ignore channels. Some depth data seems missing around the low readings, but this did not prevent the run on the last committed version.

## The user's decisions (2026-10-20)

- **B — no.** Being slower is not the cause; the fix is not to offer a slower channel.
- **W — yes.** A route may not start or end too close to the shore or to low depth: its ends move to the nearest point that far off (implemented, 25 m).
- **C — yes.** The nudge centres a passage's legal water.
- **The EMODnet cutoff is 3.00**, read by the chart and the router alike (the chart slider's shipped default moves 2.0 → 3.0).
- **Missing depth reads as the cutoff depth in the route search.** An excluded EMODnet reading, or a node with no data, takes part in the router's blend at that depth: it neither drags its neighbours shallow nor walls anything itself.
- **Version numbers stay on track data only.** The fine-tile format version is removed (implemented): the store keeps one installed build's entries.

## What the data and the device runs show

- **The channel runs west–east at ~43.5704–43.5709 N**, between Pointe du Bacon's shallows and La Grenille's (43.5711–43.5724 N), 95 m from the coast at its east end.
  - Every 16:09–16:11 route goes round La Grenille, bending at 43.5726 N; the three rungs fold into one row (`FOLD dispersion=15–25 m`).
  - v5's reading of the 7.141–7.142 E north–south run as "the channel" was wrong.
- **The depths**, read through the app's own `DepthSerializer` in a throwaway JVM probe (output at the session's `scratchpad/channel_probe.txt`; no data file opened):
  - Litto3D reads 3.6–4.9 m on one row only (43.57059 N, 7.1389–7.1401 E).
  - The channel's middle rows (43.57082 and 43.57104 N) are EMODnet at 0.1–3.5 m, bar one Litto3D 7.1 m node.
- **Three readings of the same raster:**

| Variant | The channel along 43.5707 N |
|---|---|
| **A** — today: every node blended, EMODnet under 2.0 m excluded **after** the blend | closed |
| **B** — EMODnet under 2.0 m kept out of the blend | pinched under 10 m at ~7.1386 E by a 2.9E node |
| **C** — EMODnet under 3.0 m kept out of the blend | open, **≥ 20 m from the 3 m line** along 43.5707–43.5708 N, on the 20 m cells and the 10 m wall the search reads |

- **EMODnet walls the router three ways:**
  1. `DepthGrid.depthAt` blends every node and takes the heaviest one's source, so an excluded 0.4E node drags its Litto3D neighbours' samples under 3 m.
  2. The sample gate tests the blended value, so an EMODnet-heaviest sample blended into 2.0–3.0 m walls too.
  3. EMODnet between the cutoff (2.0) and the gate depth (3.0) is shallow outright.

## The regression

- **The 00:30 run (process 31342) predates the second review of the gate-measurement work.**
  - Its `EDGES` line reads `pad=2.72m split=13` against the committed `pad=2.09m split=17`.
  - Its channel lines stood 11.7–15.9 m off the 3 m line.
  - It already ran the measured gate, the 10 m wall and the ramp.
- **What closed the channel:** that review's raw-depth prefilter (*"EMODnet-gated nodes hide shallow bands"*) began tracing chunks that hold excluded EMODnet nodes (mixed quads 16070 → 16538 on the same 3610 chunks), and mechanism 1 made 3 m lines of them.
- **What did not close it:** the end anchoring and the end repair — the 16:09 run's ends repair and its routes land.
- The logcat holds no run of the committed build.

## The fix

- **T — the trace** (implemented, see *Implemented*).
- **W — the ends kept clear** (implemented, see *Implemented*).
- **D1 — the router's read keeps excluded and missing depth out of the 3 m line.**
  - One read in `DepthGrid`, **`depthAtFilled(lat, lon, excludeBelowM, fillM)`**, shares `depthAt`'s bilinear loop. Before the blend it sets a node to `fillM` where the node is excluded EMODnet or holds no data. A sample with all four nodes missing stays no data.
    - **Excluded EMODnet:** an EMODnet node under `excludeBelowM`, the negative Cap d'Antibes artefact included.
    - **The source check:** the node's source byte is compared with `EMODNET.id` only for a node under the cutoff.
    - **The rule's one home:** the per-node exclusion is extracted as one predicate, which today lives in three copies disagreeing on negatives and on cutoff 0 (`DepthGrid.kt:49`, `:104`, `DepthIsobaths.kt:106`).
  - **`fillM` is the gate depth**, `route.avoid.depthGate.minM` (3.0). With the shipped cutoff at 3.00 it is the user's *cutoff depth* exactly. Why the gate depth:
    - A fill equal to the gate depth gives **the same verdict and the same 3 m line as leaving the node out**: the blend is `α·C + (1−α)·g`, under `g` exactly where `C` is. So the channel opens exactly as variant C.
    - It leaves the coast, the 6 NM mask's edge and the raster's border unmoved.
    - A fill at a lower cutoff (0 or 2.0) would wall every coast and raster edge.
    - A fill at a higher cutoff (3.5–5) would shrink real Litto3D shallows beside excluded nodes.
  - **The plateau reads exactly the gate.** The blend is computed as `fill + Σw·(v − fill) / Σw`, so an all-filled quad reads `g` exactly and is never shallow (the gate is `< minM`).
  - **Readers:** the live world's `depthAt` reads it (`MultipassWorld.kt:279-280`) in place of `depthAt(...).gatedForEmodnetShallow(...)`, the gate depth from `AppConfig`. `shallowEdgesOver` reads it for the contour and the prefilter alike, so `rawDepthAt` goes.
    - Every other router reader asks only *clears the gate or not*, so it follows through the world: `validWater`, `BerthCarve`, `RouteGridBuilder`, `depthMAt`, `RouteDepthReading`.
    - The post-blend gate leaves the router; re-gating a filled sample would bring mechanism 2 back.
  - **A filled sample is said:** it carries no Litto3D source, so the trace prints it as filled. `LINE minDepth` and the grid line show an assumed 3.00 as `filled`, never as a sounding.
  - **The chart keeps its own read.**
- **D2 — the cutoff's default is 3.0, in one home.**
  - `maro.properties` gains `depth.emodnetShallowCutoffM=3.0`, read through `AppConfig`, in place of the `2.0f` literal written twice in `SettingsManager.kt:149` and `:660`.
  - **A device that saved a setting keeps its own value:** saving writes every field (`SettingsManager.kt:921`). No migration — the user's device already stood at 3.0 and moves to 3.5 by the slider (2026-10-20).
  - **The device ran at 3.0 all along:** its closed channel came from mechanism 1 alone, excluded nodes dragging Litto3D samples under 3 m. Mechanism 3 was already off there, so D1 is the whole fix on that device. A probe of variant A at 3.0 would confirm it.
  - **At 3.5 the routing is unchanged.** EMODnet at 3.0–3.5 m was never shallow for the 3 m gate, and the fill stays at the gate depth (3.0), so no real Litto3D shallow shrinks. The chart then also hides EMODnet 3.0–3.5 m.
- **Tiles:** nothing to bump — the store keeps one installed build's entries, and both `TileKey` and `LiveShallowEdges`' key carry the cutoff and the gate depth, so a slider move rebuilds what it changes.
- **C — the nudge centres the legal water.**
  - A side's bank is the nearer of the 3 m edge **plus the wall** and the coast **plus its margin**. The coast is read by stepping on the fine pass's field's `hardDistanceM`, which carries the coast as its distance wall (`RoutePassPrimitives.kt:45`).
  - The target is the middle of that strip, and a passage is a strip no wider than twice the berth's outer edge (100 m), measured on the legal banks.
  - **Both sides may move:**
    - A new vertex may not stand nearer either legal bank than its sample did.
    - The stretch is accepted where its total time **without the berth's term** is no dearer and its least legal clearance does not fall.
- **S — the start that finds no way.** Designed once a `PASS … NO PATH` line with `startLayer`/`startOpen` appears. Expected: `endCellAt` anchors only on a cell the walk can leave.
- **Held:** the fold blind to obstacles (no fold of two different ways in evidence).

## Order

1. **D1 and D2** (no migration; the device moves its slider).
2. **The device run at two speeds:** the channel taken or not, `bodyEdgeIdx`, `NUDGE` counts inside it. The whole passage lies inside the berth's 20–50 m price.
3. **C**, centring the channel's legal water.
4. **S**, when a `NO PATH` line appears.

## Non-regression and performance

- **D1 — the 3 m line moves only beside excluded EMODnet nodes, both ways.**
  - It opens water they dragged shallow.
  - It can also wall water open today: a sample whose heaviest node was excluded read no data, and now blends that node at the gate depth with its neighbours, so a shallow Litto3D node beside it pulls it under 3 m.
  - Missing nodes move nothing, whether land, the mask or the raster's border.
- **D1's cost:** the trace gets cheaper. Its mixed quads are a subset of today's, plateaus of excluded EMODnet are no longer traced, and the prefilter's second node pass goes.
- **D2 — the chart changes too.** At 3.0 the colour map, the isobaths and the low-depth warning layer hide EMODnet 2–3 m (`DepthBitmap.kt:59`, `DepthIsobaths.kt:45`, `LowDepthWarningBitmap.kt:48`), as they already hide EMODnet under 2 m.

## Tests

- **D1:**
  - A Litto3D 4 m node beside an EMODnet 0.4 m node reads ≥ 3 m between them.
  - An all-filled quad's interior reads the gate depth exactly and is not shallow.
  - A sample with all four nodes missing reads no data.
  - The −1.7 m EMODnet artefact stays excluded, beside `DepthSampleGateTest`.
  - A Litto3D 2 m node beside an excluded EMODnet node puts the 3 m line within one pad of the excluded node.
  - With the cutoff below the gate, or at 0, nodes still fill at the gate depth, and EMODnet between the cutoff and the gate still walls.
  - A chunk holding only excluded EMODnet traces no mixed quad, so dropping `rawDepthAt` is safe.
  - A fixture channel of Litto3D 4 m with excluded EMODnet nodes in it is open in the edge index.
- **Kept unchanged:** `ShallowEdgeIndexTest.kt:92` (a shallow node beside no data), the coastline's guard.
- **Rewritten on purpose:**
  - `ShallowEdgeIndexTest.aHiddenEmodnetNodeStillLeavesItsBandTraced` (`:115`) pins today's opposite and is rewritten to pin D1.
  - `RouteEmodnetShallowGateTest`'s `GatedWorld` (`:112-113`) and its doc (`:21-28`) mirror the live read.
- **D2:** the default reads 3.0 from the properties. A stored 2.0 is migrated once or kept, by the user's choice.
- **Engine:** a raster pair through a channel whose middle rows are excluded EMODnet lands a route through it.

## Texts to update with D

- **KDocs:**
  - `MultipassWorld.kt:100-106`, `:273-278`.
  - `ShallowEdgeIndex.kt:37`, `:104-125`, `:630-635`.
  - `RoutePassPrimitives.kt:164-168`.
  - `RouteDepthReading.kt:19-20`.
  - `SettingsManager.kt:53-55` ("Default 2.0").
- **The rule** at `FEAT_DSC_Route.md:139`.

## Strongest objection

- The channel opens through water EMODnet reports at 0.1–1.9 m (probe rows 43.57082 and 43.57059), on distrust of EMODnet alone — no Litto3D sounding covers those two rows.
- Every EMODnet-only shoal under 3 m elsewhere is crossed the same way.

## Adjacent, not acted on

- With the cutoff at 0, the router still excludes the −1.7 m EMODnet artefact while the chart's per-cell gate passes negatives through. That contradicts `MultipassWorld.kt:103-105`; D1's one predicate settles it.
- The doubled-corridor retry builds one grid per rung rather than one shared grid (`RouteAvoidEngine.kt:499`).

## Implemented

**2026-10-20, T and W, on `feature/route-low-depth-check`, uncommitted.** The plan's own order holds: S and C are designed from T's device reading, not before. One review ran after the build; its *should* findings are fixed or listed below. The full suite passes (1401 tests, 0 failures, 12 skipped as before) and `apk-build.bat` packages; the device run is the user's.

- **T — the trace.**
  - `REPAIR` names each end raw and repaired, the rule it settled on, the metres it moved and its edge and coast clearance.
  - `PASS … NO PATH` adds `startLayer`, `startOpen` (the start slot's steps into water) and `rewalked`.
  - `LINE` adds `minDepthAt` and `minDepthWaiver` (`disc`, `carve` or `none`).
  - `NUDGE` counts each sample's first failed test (`waived`, `open`, `noLeft`, `noRight`, `wide`).
  - The view model prints `FOLD`.
  - One shared gate, `RouteTrace`, serves the engine and the view model.
  - Deviation: the `GRID` line does not print fine membership; `startLayer` and `startOpen` stand for it.
- **W — the ends kept clear.** An end stands at `route.repair.clearanceM` (25) from the coast and the measured 3 m line.
  - **The rules, in turn:**
    1. the nearest clear point within twice the clearance, so a berth moves into its basin, not across its harbour;
    2. the nearest point clear of the gate's wall within `route.repair.maxRadiusM`;
    3. the old first-water rule, so no end routed before is refused.
  - **A move is clean:** it crosses the coast's own edges only to leave land it started on, and never re-enters a shoal.
  - **It runs off the screen's thread:** an exit or a newer arm during it drops its answer.
  - **No note is shown** (2026-10-20, the user's word): the `originMoved` flag and the confirm panel's two notes — the start's, which was never asked for, and the older destination's — are removed; `destinationMoved` stays on the engine's answer with no reader on screen.
  - **The worst case:** both new rules sweep their reach and the old rule decides, in 7 ms on a cold 20 m world.
- **Tests.**
  - `RouteEndClearanceTest` (7): the nearest clear point, an end left where tapped, the wall rule, the old rule never refusing, a mole never crossed, an end on land, the worst-case budget.
  - `GateEnforcementEngineTest`: the trace fields, and a run with the clearance off that exercises the ends' waiver and carve at engine level.
  - `GateEndStepTest`: the waiver's kinds, and the re-walk's own reading.

**Still open:**
- **A berth in a basin narrower than 50 m** takes the wall rule, which has no coast standoff, so it may stay where it was tapped.
- **The angular refinement is a quarter of a 22.5° step**, so the clear point is the nearest only to that bearing step.
- **The bisection assumes a clear point lies further out along each bearing.** Every returned point passes its tests, but it may not be the nearest of all.
- **The shoal check samples the move every 2 m**, while the coast check is exact against its edges.

**D — 2026-10-20, uncommitted.** D1 and D2 shipped as v8 describes. One review ran after the build; its two *should* findings are fixed and its nits applied. The suite passes (1408 tests, 0 failures, 12 skipped as before) and `apk-build.bat` packages; the device run at two speeds is the user's.

- **The router's read:** `DepthGrid.depthAtFilled` fills excluded EMODnet and missing nodes at the gate depth before the blend; a sample whose four nodes are all missing stays no data. It shares one node walker with `depthAt`, so the two reads can never weigh a point differently.
- **The readers:** the live world's `depthAt` and `shallowEdgesOver` read it; the edge index's raw-depth prefilter is gone.
- **The rule's one home:** `DepthSource.isExcludedEmodnet`. The chart's per-cell colour gate alone keeps negatives and cutoff 0 open; nothing on the chart changed behaviour.
- **The trace:** `LINE minDepth` skips assumed samples, and the grid line prints `filled`.
- **D2:** `depth.emodnetShallowCutoffM=3.0` in `maro.properties`, read through `AppConfig`, replacing the two `2.0f` literals; a device keeps its stored value.
- **Tests:**
  - `DepthAtFilledTest` (5);
  - `ShallowEdgeIndexTest` — the reversed test rewritten, plus 3 new;
  - `RouteEmodnetShallowGateTest` mirrors the live read;
  - `GateEnforcementEngineTest.aChannelOfExcludedEmodnetCarriesTheRoute`, at the device probe's own values. It first shows the old read walled a strip there, then that every rung runs the channel with the 10 m wall.
