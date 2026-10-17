# Context Hydration — Route — 2026-10-17

**Last Bake:** 2026-10-17 16:45 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session ran on `feature/route-ld` (cut from `origin/develop`) on the user's `#focus route` / `#new route-ld`, then a long device-driven loop: the **20 m standoff** enforced, the ring replaced by a **per-grid gate-distance berth** (`#impl`), the **depth collar + tile halo** (`#impl`), the line's **Chaikin smoothing** replaced by **Douglas–Peucker** on the user's *use a better line straightening method*, and the pull's guard moved to **total time** on *plan fix* + `#impl`. Between hops the user ran the device and asked for the logcat after each build, and tuned `route.evolutive.berth.secPerM` 0.1 → 0.5 → 0.75. The device was touched only for the logcats the user asked for, dump-only; **no dependency was added, no machine-shaped data file opened, no work started without an order, and every code claim stands behind a file read.**

## State

**The 20 m wall, then the berth.** The gate wall (`route.avoid.depthGate.minM` 3.0) is rastered; the 20 m standoff (`route.avoid.depthGate.marginM`) is an **inclusive** dilation (`gateDistanceM ≤ margin` paints land) and an eight-bearing ring in the pull's [`legClearCause`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:783). The ring is then **replaced by a per-grid `gateDistanceM` field** (`MultipassGrid`, a multi-source Dijkstra BFS), and the **20→50 m berth** is priced from it — `berthShare`/`berthPriceSecAt` in [`RouteCostField.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:314), the law `secPerM × λ × share × cellM`, read by the search and the pull; the depth band is retired and `PriceArm.DEPTH` carries the berth. The berth law lives in [`berthLawOf`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt:185).

**The collar, the halo and the straightener.** The fine 20 m layer follows shallow water to `depthCollarM = depthGate.marginM + (margin + coarseCell)` = 170 m, so the weight bites offshore; each fine tile is built with a 9-cell halo (`ceil(depthCollarM / fineCellM)`) and `TileKey` carries `depthCollarM`. After the pull and before the corner pass, [`straightenLine`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:409) runs a **Douglas–Peucker** pass — it only drops points, and each chord is wall-checked (`legClearCause`) and price-checked. A first **Chaikin** attempt was reverted because it *added* points (device: `CORNERS` 12→19, `gateEdge` 20→5 m).

**The total-time guard.** [`totalSec`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:1006) = `haversine / paceMps + softPriceSec`, with `paceMps` on `PullSetup`/`PullContext`; `priceRefusal`, the sweep's triple and window proofs, and `chordLegal` all compare totals now, so a legal chord that trades a little standoff for a shorter line wins — the fix for the longer inward bend the sweep kept on soft price alone (`chordSec=1.333 pathSec=0.781`). Default `paceMps=0.0` keeps a fixture soft-only.

**The device readings, and the strength sweep.** At `secPerM=0.1` the pass read `gateEdge=20.0 m` / `bodyEdge=20–30 m`; at `0.5` the **body reached 30 m**; at `0.75` **35–40 m** (`bodyEdge=40.0 m` / `35.0 m`), each step ~5–10 m of standoff for ~85 m of distance (`1659.2 m` at 0.75 against `1573.8 m` at 0.5). The whole-line minimum stays the **departure** (`gateEdge=5.0 m` at 43.5483,7.1385, `minDepth=3.3 m`) — the boat starts at the shallow edge, where the end disc waives the margin. The berth's rule is `secPerM × λ × share`; the crossover with travel (`secPerM = travel/λ`) is 0.13 at 5 kn λ3 and 0.026 at 25 kn λ3, which is why a flat raise over-detours cruise lines.

**Gates.** `:app:testDebugUnitTest` green at **1318 tests, 1 pre-existing failure** (`PullAllowancePropertiesTest`, test 0.02 vs properties 0.1), 12 skipped; `assembleDebug` green. `RouteStraightenTest` (5 tests) and the `totalSec` test ride the suite.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `legClearCause`'s ring, `berthRate`, `straightenLine`/`markStraight`/`chordLegal`/`perpendicularM`, `totalSec`/`totalPrefix`, `priceRefusal`, the sweep's total proofs
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt` — `gateDistanceM`, `dilateGateGrid`, the inclusive dilation, the collar term in `applyFineMask`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt` — `berthShare`, `berthPriceSecAt`, `gateStandoffClears`, `depthGateSource`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt` — `gateStandoffM`, `berthLawOf`, `depthMAt`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt` — `depthCollarReachM`, `fineWater` answering `depthCollarM`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt` — the collar windows, the 9-cell tile halo, the key
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt`, `RoutePassRunner.kt` — the straightening hook and `paceMps` on both setups
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt`, `FineTile.kt`, `FineTileCodec.kt` — `depthCollarM` (v5)
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the `LINE` trace's `minDepth`/`gateEdge`/`bodyEdge`
- `app/src/main/assets/maro.properties` — `route.evolutive.berth.secPerM` (0.75), `outerM` 50, the depth-gate keys
- `app/src/test/java/ykws/android/maro/spatial/multipass/BerthCostTest.kt`, `BerthPropertiesTest.kt`, `RouteStraightenTest.kt` (in `RouteSmoothPassTest.kt`), `TileKeyTest.kt`/`TileTestFixtures.kt`
- `xTrack/Route/261017_FEAT_PLN_Route_gate-berth.md`, `261017_FEAT_PLN_Route_low-depth-fine-collar.md`, `261017_FEAT_PLN_Route_line-smoothing.md`, `261017_FEAT_PLN_Route_pull-total-time-guard.md` — the four plans and their `## Implemented`
- `xTrack/Route/261017_FEAT_PLN_Route_berth-water-edge-and-refine.md` — still **in design**, its half-cell shift not implemented

## Next Step

**The 20 m wall holds; the standoff strength is the open dial.** The body stands off 35–40 m at `secPerM=0.75`, capped by the corridor (shallow against coast/band) rather than the price; whether 0.75 is the settled strength, or a pace-scaled/capped law replaces the flat one, is the next decision. Still unlanded: the **berth-water-edge shift** ([`261017_FEAT_PLN_Route_berth-water-edge-and-refine.md`](261017_FEAT_PLN_Route_berth-water-edge-and-refine.md), in design) measuring the band's ends to the water rather than the soundings; the straightener's 10 m epsilon on device; the `paceMps` default (`0.0` soft-only) as a silent-regression risk for a future production site; the test file still named `RouteSmoothPassTest.kt` while its class is `RouteStraightenTest`; and the engine reference (`FEAT_DOC_Route_engines.md`) not yet carrying the berth, the collar or the total-time guard.

## Retirement candidates reported, not moved

- [`261017_FEAT_PLN_Route_gate-berth.md`](261017_FEAT_PLN_Route_gate-berth.md) — its work is in `## Implemented`, but the offshore collar it names now lives in its sibling plan; a candidate once the collar's device pass closes.
- [`261016_FEAT_PLN_Route_taunt-window-and-allowance.md`](261016_FEAT_PLN_Route_taunt-window-and-allowance.md) — its `## Implemented` stands; the total-time guard supersedes its soft-price allowance for the binding half, so its remaining "floor" item is moot.
