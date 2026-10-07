# Context Hydration — Route — 2026-10-07

**Last Bake:** 2026-10-07 13:40 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-07 10:17 UTC) the work ran on the user's own words — one
`#implement` on the rung-ranking plan, then six follow-ups on the acquisition panel and the followed tile:
the winner's disc moved behind the name, its first column's padding restored and the disc pinned to the
cell's top-right, the disc then **dropped** as distracting, the followed tile re-led with the ETA over
`distance · preference @ pace`, the cell made flip-able on a tap, and its border made to pulse in the
toggle's blue — plus the feature file's own cleanup to present-tense state, the `#bake`, `#commit` and
`#push` invocations. No dependency was added, no machine-shaped data file was opened, no work was started
without an order, the device was not touched, and every claim about the code follows a file read.

## State

**The rung ranking shipped whole, and the mode was reshaped around it.** [`RoutePreference.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt)
is the ladder's one home for its three λ values, indices and words; [`RoutePassRanking.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt)
ranks the rungs that have landed — Fast the total time, Best the total time under the
`route.avoid.speedZone.timeBudgetPct` gate, Fun the absolute zone seconds with the clock last, one shared
tail on a lead tie and a total tie to the rung nearest the preference — and [`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt)
folds a **running best** at every rung's terminal under one `Mutex`, riding the update as [`RouteRunningBest`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:150).
The seat follows that winner on every improvement and **freezes on a hand touch**; the fan's `Route auto`
child takes the **final best** ([`selectBestRoute()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt)); the middle stop is renamed **Best**/**Optimal**;
and the budget gained a Routing-settings row, its code default brought onto the shipped **25**.
[`RoutePassRules`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt) became
`RoutePassRanking` with `fineSpliceBetter` and its test retired, and the register gained **R98**.

**The acquisition table was settled by three passes.** The winner's disc moved from a leading slot to
behind the rung name, then to the top-right of the first cell with the column's inset restored — and was
finally **dropped** on the user's word as distracting, so the selection alone marks the chosen line while
the *so far* mark still rides the running best's row. The figures column prints **time first, distance
second** for landed and pending rows alike.

**The followed tile is the route's own, and flips.** Its main figure is the remaining time through
[`routeEtaText`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt), over one line built
from `route_trip_line_fmt`: the distance left, the Driving preference's word and the cruising pace. A tap
flips it to the shore reading and back, a freshly armed route opening on the trip face, and the trip face
wears a **border pulsing in the route toggle's own blue** on the app's single pulse beat.

**The feature file was cleaned on the user's word.** [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) dropped its
`## Implemented` log, both closed walk levels, the `## Delta` narrative and the struck R83–R87 rows, from
355 lines to about 145, and now states the current code, its live requirements at the same numbers, its
rules, key files, docs, open todos and owed passes.

Suite **940 / 1 / 11** — the parked `route.avoid.fine.cellRatio` red alone — and `assembleDebug` green.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePreference.kt`, `RoutePassRanking.kt` — **new**, the ladder's home and the preference-aware ranking; `RoutePassRules.kt` deleted
- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt`, `RouteAvoidEngine.kt` — `RouteRunningBest` on the seam, the `runningBest` field on the update, the fold at every terminal
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the running best exposed, the seat following it and freezing, `selectBestRoute()`
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt`, `RouteConfirmPanel.kt`, `MapScreen.kt` — the row's figure order and *so far* mark, the cleaned first column, the panel's new parameters
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — the trip face's ETA-first reading, the two-face flip, the pulsing border
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties` — the budget default 25
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `route_winner_so_far`, the Best/Optimal rename, `route_trip_line_fmt`; `route_trip_ago` and `route_trip_forced` retired
- `app/src/test/java/ykws/android/maro/spatial/multipass/RoutePassRankingTest.kt` — **new**, six fixtures; `RouteAvoidEngineTest`, `RouteAcquisitionTest`, `RouteAutoPickTest`, `RoutePlanTest` retargeted
- `xTrack/Route/FEAT_DSC_Route.md`, `FEAT_DOC_Route_engines.md`, `261007_FEAT_PLN_Route_rung-ranking-preselection.md` — the record
- `xTrack/GLOBAL_CONTEXT.md` — this bake's routing row and focus entry

## Next Step

The ranking's device acceptance and the followed tile's new reading are the user's own, unmeasured. The
parked `route.avoid.fine.cellRatio` red stays by the user's word, and four findings stand open: the
`RoutePreference.ofIndex` silent default, "three rungs" living in three places, two stale lines in
[`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md), and the removed `## Implemented` section against
`AGENTS.md` §7a's bake target.
