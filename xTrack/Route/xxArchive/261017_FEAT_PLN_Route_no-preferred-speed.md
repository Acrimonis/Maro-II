<!-- scope: feature -->
# Route — no preferred speed: an `Any` stop on the free-water pace

**Date:** 2026-10-17 · **Status:** implemented, 2026-10-17 — **Order:** the user's words of 2026-10-17, after the
session's discussion of what the pace is: *"if it is 'whatever', there is no need to rank it anymore. I would
pick it myself"*, then *"add a 'Any' at the top of the prefered speed scale (in dashboard and in preferences).
update doc and plan"*.

## What the session asked, and what it settled

The session opened on the impact of setting no preferred speed, and the answer was that the pace is not a
comfort dial: it is the cost unit, the definition of slow, the forced-crossing probe's wall, the bend radius and
the crown's severity. Leaving it *unset* cannot mean *absent*, because every consumer is a non-null `Double`.

- **The unit** — [`baseCostSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:131)
  is `cellM / v(pace)`, and the A* heuristic is the same division, so the base is uniform and cancels on its own.
- **The definition of slow** —
  [`zonePriceSec()`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:158) charges
  `cellM × (1/v(limit) − 1/v(pace)) × k`, which is `k × base × (pace/limit − 1)` floored at zero: a limit at or
  above the pace costs nothing, so the pace alone decides which water exists as a price.
- **The probe's wall** —
  [`blockedCopy(paceKn)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt:363) turns a
  cell whose limit stands below the pace into land for the forced-crossing probe
  ([`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:829)).
- **The bend radius** — [`RouteCornerPass`](../../app/src/main/java/ykws/android/maro/spatial/RouteCornerPass.kt:154)
  reads `v(pace)` for `r = v²/a`, so the pace decides how every corner is drawn.
- **The crown's severity** — [`slowWaterSeverity()`](../../app/src/main/java/ykws/android/maro/spatial/RouteEta.kt:247)
  is `(pace − limit) / pace`.

Two prices do not track the pace, and the asymmetry matters to any later design: the marker price is
[`base × factor`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteMarker.kt:118) and so is
pace-invariant in relative terms, while the depth band is absolute λ-free seconds
([`depthBandPriceSecAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt:304)) and so
is relatively weaker as the pace drops.

## The decision

**An `Any` stop at the top of the free-water pace scale, offered by both doors.** It is the scale's first
position and the absence of a preference, not a speed: the user stops being asked how fast the boat goes, and
the search plans at whatever the boat is actually doing.

- **`Any` is a sentinel, not a number.** The value is `AppConfig.ROUTE_FREE_WATER_PACE_ANY = 0f`, below the
  5 kn floor so no legal value collides with it — the same shape as `path.count`'s All sentinel and the
  `Slow water` dial's `null` weight.
- **One ordered option list, two doors read it.** `ROUTE_FREE_WATER_PACE_OPTIONS_KN` is the sentinel followed by
  the six numbered stops; the Settings row and the drawer wheel both read that one list, so the row's ends and
  the wheel's rows cannot disagree.
- **A speed can still be set.** The five-to-thirty-five grid is untouched and still snap-read; `Any` is a
  position *before* it, never a value inside it.

## The semantics, pinned

- **What `Any` means in the search** — the pace is the boat's own, read through
  [`RoutePace.paceKn()`](../../app/src/main/java/ykws/android/maro/data/route/RoutePace.kt:58): the 75th
  percentile of the last ten minutes' unrestricted readings once five samples stand.
- **The fallback** — until the window has evidence, the search plans at the shipped
  [`route.freeWaterPaceKn`](../../app/src/main/assets/maro.properties:132) value, which therefore becomes both the
  shipped stop and `Any`'s own fallback. ONE HOME: the number keeps its one home in the properties file.
- **The row says Any; the report names the number.** The Settings row and the drawer wheel read the word `Any`
  at the sentinel, while the acquisition's settled status word and the followed trip line name the pace the plan
  **actually used**, because those two surfaces report a plan and never a setting.
- **Both resolutions have one home** — one pure resolver, `routePaceForSearchKn(setKn, observedKn)`, answering
  the observed pace when the setting is `Any` and evidence stands, the shipped fallback when `Any` stands alone,
  and the setting's own number otherwise. Both wiring sites call it; neither re-derives it.

## The touchpoints

1. **`AppConfig`** — the sentinel constant `ROUTE_FREE_WATER_PACE_ANY` beside the min/max/step, the one option
   list `ROUTE_FREE_WATER_PACE_OPTIONS_KN`, and the pure `routePaceForSearchKn` resolver.
2. **`SettingsManager`** — the stored `Float` keeps its key (`route_free_water_pace_kn`); its **read** must pass
   the sentinel through and never snap it to 5 kn, and its write path is unchanged.
3. **The Settings row** — [`MapScreenSettingsOverlay.kt:1638`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1638):
   the slider's range opens at the sentinel with one extra interval, and its label reads the `Any` string there —
   the exact shape the render-count slider already has at its own sentinel.
4. **The drawer wheel** — [`MenuDrawerOverlay.kt:624`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:624)
   builds its rows from the option list rather than the numbered stops, so `Any` is its first row.
5. **The two wiring lambdas** — [`MapScreen.kt:776`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:776)
   and [`MapRouteEffects.kt:55`](../../app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt:55) call the
   resolver instead of reading the setting raw, so the price law, the corner radius and the clock all read the
   same resolved number.
6. **The observed pace has to reach both sites** — [`RouteViewModel.paceKn`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:380)
   already publishes it; the effect-side site takes it from the same source rather than a second pipeline.
7. **Both locales** — one new key, `settings_route_pace_any`, in
   [`values/strings.xml`](../../app/src/main/res/values/strings.xml:759) and
   [`values-fr/strings.xml`](../../app/src/main/res/values-fr/strings.xml:761), read through its `@StringRes` id
   and never a literal. The French word is the user's to pick; `Indifférent` is the proposal.
8. **The properties comment** — `route.freeWaterPaceKn`'s block states that it is also `Any`'s fallback while the
   boat has no evidence, in the same write as the constant, never in a second place.
9. **Tests** — a properties/sentinel suite pinning the round-trip through `SettingsManager`, the option list's
   first position, the resolver's three cases, and the label at the sentinel.

## The traps, named

- **The snap would swallow the sentinel** —
  [`snapFreeWaterPaceKn()`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:93) coerces into 5–35,
  so a raw `0f` read through it becomes 5 kn and `Any` silently becomes the slowest boat in the app.
- **`RoutePace`'s own clamp would turn `Any` into 5 kn too** —
  [`RoutePace.clamp()`](../../app/src/main/java/ykws/android/maro/data/route/RoutePace.kt:78) coerces its fallback,
  and [`RouteViewModel.observePace`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:894) is
  handed the raw setting from [`RouteHost`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:156); the
  fallback passed there must be the resolved number, never the sentinel.
- **Two pace numbers already disagree on one screen** — the engine and the followed card read the **set** pace
  ([`MapScreen.kt:3290`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3290)) while the
  acquisition panel is handed the **observed** one
  ([`MapScreen.kt:3308`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3308)), so the settled status
  word can name a speed the line was not timed at. `Any` makes that divergence the normal case, so the resolver
  above is what keeps the two statements one.
- **The stated default is already wrong in prose** —
  [`AppConfig.kt:40`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:40) says "default 28" where the
  variable is 25, and [`maro.properties:144`](../../app/src/main/assets/maro.properties:144) calls the lateral
  acceleration "0.33 shipped" and computes its radii "at the 28 kn pace" where the shipped value is 0.666667.

## What must not move

- **The numbered grid and its snap** — 5–35 kn by 5, the slider's six intervals and the wheel's rows, so a user
  who never touches `Any` sees exactly today's behaviour.
- **The ladder and the crown** — the three λ keys, the three rungs, the `Slow water` dial, the ranks and the
  30 s tolerance are untouched by this stop.
- **The fine tile's key** — it is already pace-free
  ([`RouteGridBuilder:573`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt:573)),
  so `Any` never invalidates a cached tile.

## Documentation owed on landing

The state docs are present-tense, so none of these is written until the code ships. Each is one sentence at one
home:

- [`FEAT_DSC_Route.md:25`](FEAT_DSC_Route.md:25) — "the pace is the free-water pace setting" gains the `Any` case:
  the setting's absence, and the boat's own pace as the answer.
- [`FEAT_DSC_Route.md:45`](FEAT_DSC_Route.md:45) — the rows list names the pace row's `Any` position beside it.
- [`FEAT_DOC_Route_engines.md:38`](FEAT_DOC_Route_engines.md:38) — the registry's factory paragraph states that the
  pace it receives is the resolved one, setting or boat.
- [`FEAT_DOC_Route_avoid-algorithm.md:42`](FEAT_DOC_Route_avoid-algorithm.md:42) — the unit paragraph's `v(pace)`
  gains the same clause: the pace is the resolved one, never a stored preference alone.
- [`maro.properties:132`](../../app/src/main/assets/maro.properties:132) — the value's block names `Any`'s fallback.
- Both locales' `settings_route_pace_desc` state that the scale can be left at `Any`, so the row explains itself
  where the user meets it.

## Open, recorded and not ordered

- **Unranked proposals** — the user's *"there is no need to rank it anymore, I would pick it myself"*. With a
  resolved pace the crown still compares rungs by trip time, so this remains a separate decision: three rows
  stating distance, added distance and priced water crossed, no rank and no crown.
- **The cost unit** — a pace-free search needs a unit that is not seconds: effective metres with a per-limit
  penalty table, or seconds at the limit in force. Both are re-units of every price law and are out of this
  order's scope.
- **The calibration table** — under a pace-free unit, how dear a 5 kn ring is against a marker stops being derived
  from speed data and becomes a shipped table; the numbers are the user's.
- **The corner radius's own replacement** — drawing from the limit in force at the bend, with one fixed maximum in
  open water, belongs to the pace-free design and not to this stop.

## Implemented

**2026-10-17 — the `Any` stop is on the scale, in both doors, and the build is green.**

- **The sentinel and its one home** — `AppConfig.ROUTE_FREE_WATER_PACE_ANY` (0 kn), the ordered `ROUTE_FREE_WATER_PACE_OPTIONS_KN` (the sentinel then the six numbered stops), `isFreeWaterPaceAny()` and the resolver `routePaceForSearchKn(setKn, observedKn)`; the numbered grid and its snap are untouched.
- **The read keeps the sentinel** — `SettingsManager`'s pace read passes `Any` through unsnapped, because the snap's 5 kn floor would have turned *no preference* into the slowest boat; `RouteViewModel.observePace` resolves the fallback before `RoutePace`'s own clamp, so a cold start plans at 25 kn rather than 5.
- **Both doors read one list** — the Settings slider opens on `Any` with one extra interval and reads `settings_route_pace_any` at the sentinel; the drawer wheel's first row is `Any`; the word exists in both locales and the description names the Any behaviour.
- **One resolved pace for the search** — `MapScreen` hoists `routeSearchPaceKn`, written only through the resolver, and both the engine factory and `MapRouteEngineEffect`'s new `paceProvider` read it, so the price law, the corner radii and the clock all take the same number.
- **The trip card names the pace in force** — under `Any` it prints the resolved pace rather than the sentinel, and under a numbered stop it prints the setting exactly as before.
- **Tests and the gate** — `FreeWaterPaceAnyTest` pins the list's first position, the predicate, the resolver's three cases and the snap guard; `:app:testDebugUnitTest` green at 1313 tests (12 skipped) and `:app:assembleDebug` packaged.
- **The order's own drift settled** — the user's word keeps `route.avoid.pull.allowanceShare` at **0.1**, so the test, the `AppConfig` KDoc, the var default and the properties comment were brought to 0.1 in the same pass.
- **Left open, recorded above** — the `SettingsManager` round-trip test and the label-at-sentinel check have no harness; `Any` freezes at the 25 kn fallback in demo mode until the boat moves; the numbered-stop divergence between the panel's pace word and the engine's pace is unchanged; and the option list is still built by a getter.
- **The wheel's own word** — the drawer's quick-access wheel reads `--Any--` (`--Indifférent--`) on the sentinel row through its own key `settings_route_pace_any_wheel`, while the Settings row keeps the plain `Any`; the list, the stored value and the resolver are untouched.
