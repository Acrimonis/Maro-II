---
name: RegulatedZones
status: in design
created: 2026-10-14 10:31
modified: 2026-10-14 10:31
---

# Plan — `speedRoute` steers the search, the displayed limit keeps the clock

Branch: `feature/zones-dsply`. Feature: RegulatedZones (the `regulatedZone.speedRoute.<key>` key's home);
the implementing code lives in Route's `spatial/multipass` layer. Cross-listed todo: Route.

## Intent

A zone may carry two keys: `regulatedZone.speedOverride.<key>` (the displayed limit) and
`regulatedZone.speedRoute.<key>` (a routing-only speed, today the value the route engine **prices** the
zone at). The user wants `speedRoute` to give the search an **advantage** for travelling the zone —
typically `speedRoute` above the override, so the zone's price excess falls to zero — while **every
reported figure keeps reading the override**: the leg speed, the total route time, the route line's
speed colouring and the acquisition summary's `Speed limits` line.

## The boundary, as the sources stand

- **Price (search-only, `routeLimitKn`)** — the grid's stored limits ([`RouteGridBuilder`] `ZoneRing(z.outerRing, z.holes, z.routeLimitKn)`) and the guard's live point reads ([`strictestLimitKnAt`], [`speedZoneCollarLimitKnAt`]), plus the fine pass's own crossing price. Untouched.
- **Clock (reported, `speedLimitKn`)** — one lambda today: `GridContext.limitAt` = [`limitAtFor()`], read by the provisional pair ([`RoutePassRunner`]), the settled clock and the profile ([`RouteAvoidEngine`]), the attribution ([`slowTimeByLimit`]) and the corner tally. Nothing on the price side reads it, so re-pointing that one lambda moves every reported figure together.
- The line colour derives from `legTimesSec` ([`toRenderPoints`]), the saved track's per-point speed from `durationSec` ([`TrackFromCourse`]), so both follow the clock with no change of their own.

## The change

1. **A display-law read beside the price read** — `strictestDisplayLimitKnAt(zones, excludedIds, lat, lon)` in [`ZoneGeometry.kt`], the same strictest-wins shape over `SpeedZone.speedLimitKn`. `strictestLimitKnAt` and `speedZoneCollarLimitKnAt` keep reading `routeLimitKn` and are not touched.
2. **The world's own door** — `MultipassWorld.zoneDisplayLimitKnAt(lat, lon)`, default `null`, overridden in `LiveMultipassWorld` by the new read. The default is `null` on purpose: a fake that does not override it **fails loudly** instead of quietly timing the price law. `zoneLimitKnAt` stays (the corner pass's containment test) with its KDoc corrected — it is no longer the ETA's read.
3. **`limitAtFor()`'s zone arm** reads the display accessor; the band arm is unchanged, so the 300 m band keeps one law (it has no override key).
4. **The four statements rewritten**, not trimmed: `RegulatedZone.speedRouteKn`, `SpeedZone.routeLimitKn`, `SpeedZoneBuilder`'s construction comment and the `zones.properties` header block.
5. **Tests** — one suite pinning that an override zone reports the **override** leg speed and total time while the grid still prices the zone at `routeLimitKn`; the six `MultipassWorld` fakes gain the new override (mechanical, behaviour-neutral where `routeLimitKn == speedLimitKn`).

## Decisions taken (and by whom)

- *Technical, the agent's*: a new display read beside the price read, exposed as a second world accessor with a `null` default; the price law is never re-pointed.
- *Technical, the agent's*: `routeLimitKn` keeps its name — it is still the route's price — and only the KDoc gains the second law. The plan's word "route" now means the whole pipeline, so the KDoc must say *priced*, never *timed*.
- *The user's, left as they stand*: the forced-crossing list ([`forcedCrossingNames()`], gated on `zonePriceSec(...) > 0`) stays price-side, so an override zone priced to zero is not reported as a forced crossing, and the doubled-corridor retry keyed on that list never fires for it. Nothing was changed here.
- *The user's, left as it stands*: the ranking reads the clock, so moving the clock moves Best's gate and Fun's objective with it ([`RoutePassRanking`]). R98 states the criteria but not which limit they read, so no rule is contradicted; the consequence — an override zone is judged by the displayed limit the routing never obeyed — is recorded here, not resolved.

## Out of scope

- The forced-crossing filter and the corridor retry.
- The ranking's criteria (no rule change; the coupling is recorded above).
- `RouteResult.legSpeedsMps`, written but read by nothing (the colour recomputes from `legTimesSec`); the new test asserts on `legTimesSec`, not on that field.

## Evidence

- Price readers: [`ZoneGeometry.kt:73`], [`ZoneGeometry.kt:98`], [`RouteGridBuilder.kt:121`], [`RouteFinePass.kt:79`].
- Clock readers through the one lambda: [`RoutePassRunner.kt:141`], [`RoutePassRunner.kt:166`], [`RouteAvoidEngine.kt:617`], [`RouteAvoidEngine.kt:623`], [`RouteAvoidEngine.kt:616`].
- Display surfaces already on `speedLimitKn`: [`SpeedZoneIndex.kt:49`], [`DashboardPanel.kt:261`], [`MapScreen.kt:4863`], [`NavigationViewModel.kt:1594`].

<!-- link targets are relative to this file -->
[`RouteGridBuilder`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt
[`strictestLimitKnAt`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt
[`speedZoneCollarLimitKnAt`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt
[`limitAtFor()`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt
[`RoutePassRunner`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt
[`RouteAvoidEngine`]: ../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt
[`slowTimeByLimit`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt
[`toRenderPoints`]: ../../app/src/main/java/ykws/android/maro/ui/map/LineRenderSeam.kt
[`TrackFromCourse`]: ../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt
[`ZoneGeometry.kt`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt
[`forcedCrossingNames()`]: ../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt
[`RoutePassRanking`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRanking.kt
[`ZoneGeometry.kt:73`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt
[`ZoneGeometry.kt:98`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/ZoneGeometry.kt
[`RouteGridBuilder.kt:121`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridBuilder.kt
[`RouteFinePass.kt:79`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt
[`RoutePassRunner.kt:141`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt
[`RoutePassRunner.kt:166`]: ../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt
[`RouteAvoidEngine.kt:617`]: ../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt
[`RouteAvoidEngine.kt:623`]: ../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt
[`RouteAvoidEngine.kt:616`]: ../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt
[`SpeedZoneIndex.kt:49`]: ../../app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt
[`DashboardPanel.kt:261`]: ../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt
[`MapScreen.kt:4863`]: ../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt
[`NavigationViewModel.kt:1594`]: ../../app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt
