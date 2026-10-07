# Context Hydration — Route — 2026-10-07

**Last Bake:** 2026-10-07 17:55 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-07 13:40 UTC) the work ran on the user's own words — a plan
review that folded four corrections into [`261007_FEAT_PLN_Route_avoid-settings-taxonomy.md`](261007_FEAT_PLN_Route_avoid-settings-taxonomy.md),
then the steer **fold · 1 delete · 2 normalize · update docs to match current**, which landed the avoid/evolutive
key taxonomy. No dependency was added, no machine-shaped data file was opened, no work was started without an
order, the device was not touched, and every claim about the code follows a file read.

## State

**The avoid/evolutive key taxonomy landed.** The dead key `route.avoid.grid.fineRatio` (no accessor, no parse)
was deleted with its comment; the fine layer was normalized to one idiom, **metres** — `route.avoid.fine.cellRatio`
became [`route.avoid.grid.fineCellM=33.3333`](../../app/src/main/assets/maro.properties:304),
[`UniformGridPlan.fineCellM`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:59)
reads it, and `routeAvoidFineCellRatio` with its ratio bounds became `routeAvoidGridFineCellM` with metres bounds
in [`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:200). The four asset-vs-code drifts
were aligned onto the shipped asset — `grid.cellM` 50→100, the fine cell 0.40→33.3333, `lateralAccelMps2` 1.0→0.33,
`speedAccelMps2` 0.5→1.0, `obstacle.marginM` 25→50 — and the shipped line is preserved byte-for-byte.

**The docs match.** The two acceleration comment blocks in
[`maro.properties`](../../app/src/main/assets/maro.properties:131) now state their shipped values, the
[`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:113) KDoc names the new
key, and [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md),
[`FEAT_DOC_Route_avoid-algorithm.md`](FEAT_DOC_Route_avoid-algorithm.md) and
[`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) are current.

**The parked red is resolved.** `theFineCellRatioShipsAtFortyPercentOfTheCoarseCell` was rewritten to
`theAvoidFineCellShipsInMetres`, and the evolutive plan's ratio test to `theUniformPlanReadsItsOwnMetresFineCell`.

Suite green — no unit test red on purpose-known grounds — and `assembleDebug` green.

## Target Files

- `app/src/main/assets/maro.properties` — the dead key deleted, the ratio key renamed to `route.avoid.grid.fineCellM`, the two acceleration comments corrected
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `routeAvoidGridFineCellM` and its metres bounds replacing the ratio pair; `grid.cellM` 100, `obstacle.marginM` 50, `lateralAccelMps2` 0.33, `speedAccelMps2` 1.0
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt` — `UniformGridPlan.fineCellM` reads the metres key; the interface KDoc
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the KDoc's key name
- `app/src/test/java/ykws/android/maro/spatial/RouteAvoidEngineTest.kt`, `RouteEvolutiveEngineTest.kt` — the parked red rewritten, the margin reset and the evolutive ratio test retargeted
- `xTrack/Route/FEAT_DOC_Route_engines.md`, `FEAT_DOC_Route_avoid-algorithm.md`, `FEAT_DSC_Route.md`, `261007_FEAT_PLN_Route_avoid-settings-taxonomy.md` — the record
- `xTrack/GLOBAL_CONTEXT.md` — this bake's summary row and focus entry

## Next Step

The algorithm review's points 1, 3 and 4 stay in design, each carrying a plan to be discussed in turn. Two
points park under the taxonomy work: whether `avoid`'s fine cell stays 33.3333 m or moves to a clean 20/25 m,
and whether `route.avoid.*` should be renamed to a family name that says *shared*. The rung ranking's device
acceptance and the followed tile's reading remain the user's own, unmeasured, and the removed `## Implemented`
section still stands against `AGENTS.md` §7a's bake target.
