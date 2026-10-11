# Context Hydration — Route — 2026-10-11

**Last Bake:** 2026-10-11 09:00 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-10 17:13 UTC) the session ran on the user's own words: `#new markr-cost`, reversed the very next turn to carry on `feature/rte-mark-cost` with the branch it had just cut deleted; *"validate the handling of the cost … is the cache being refreshed on value / markers changed?"*; the well's shape ("radius … the current flat value, and the proximity the gravity well"); the three decisions settled one by one (reuse the marker's own proximity, never a wall on a Pin, no automatic re-run); *"asses plan report"*, *"review plan"*, *"eli each open decision"*; and finally *"work the plan as written"*. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read.

## State

**The branch.** `feature/rte-mark-cost`, tracking `origin/feature/rte-mark-cost`; `feature/markr-cost` was cut on the user's order and deleted on their reversal the same minute, so this work rides the branch that already existed.

**The marker cost is now a well.** A costed **Circle**, **Corridor** or **Pin** adds one λ-free price — `base × step × strength` times its geometry's own well factor, full inside the marker's body and falling in a straight line to nothing at the outer edge of the marker's own **proximity** — while a `ROUTING_COST_BLOCKED` marker walls its **body** alone; a **Pin** is a body of radius `0`, so its price is a cone from its centre, it is never a wall, and its cost caps at the top price.

**The stale-cache hole is closed.** [`TileKey`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt:28) now carries `markerStrength` and `markerPriceEnabled` beside the costed set, so on `selective` a strength move or a switch toggle re-tiles instead of serving a cached price, and `markerProximityRangeM` is the per-type proximity fallback's one home, read by the repository and the projection alike.

**What is owed.** The engine-level fixture — a line detouring around a priced mark and refusing through a wall — is still unwritten, and the device pass (R97) reading the collar's fade and a strength move on real water is the user's own.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt` — the body plus collar geometry, `wellFactor`'s one fall-off home, the price law, `boundaryDistanceM` at the outer edge
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassWorld.kt` — the projection: a Pin as a zero-radius body, capped at the top price and never a wall
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileKey.kt`, `RouteGridBuilder.kt` — `markerStrength` and `markerPriceEnabled` as key material, threaded through `fineTileKey`
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`, `data/markers/UserMarkerRepository.kt` — `markerProximityRangeM`, the fallback's one home, with the repository delegating
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/RoutingCostStep.kt`, `ui/map/MarkersViewModel.kt` — the cost side for every type, the Pin's track capped at the top price, `formCostForSave`
- `app/src/main/res/values{, -fr}/strings.xml` — `wizard_routing_cost_top` and the proximity comment
- `app/src/test/java/ykws/android/maro/spatial/multipass/` — `RouteMarkerCostTest` (rewritten for the well), `TileKeyTest`, `FineTileMapTest`

## Next Step

No code task is open on the feature beyond the owed engine-level fixture; the device pass (R97) is the user's own.
