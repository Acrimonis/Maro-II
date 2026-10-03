<!-- scope: feature -->
# Follow / use a saved route from the track card

## Goal and settled decisions

- Replace the card's Resume action with a **follow-route** action for saved routes only — a saved route is an ordinary track carrying the `route` flag.
- The button uses the route icon, [`ui.icons.route`](../../app/src/main/java/ykws/android/maro/ui/icons/Route.kt:13) — the same vector [`RouteFanArmIcon`](../../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:217) draws.
- Pressing it activates the route mode and displays the route in **Following** state — the blue toggle, the one exit dialog, the trip figure — using the stored line as-is, no engine search.
- The button appears on both the map-opened card and the list, like Resume.
- Following a saved route stays gated by `!isRecording` — the gate is kept.
- The exit dialog's third door reads **Stop following** for a followed saved route — the normalised behaviour, 2026-09-30.

## What exists today

- [`TrackCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:471) renders the action row (pin · resume · export). The Resume icon sits at [line 587](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:587), gated by [`resumeAllowed`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:177) = `!route && endTimeMs != null`, so a route card already shows no Resume — the slot is empty.
- The route mode is `Idle → Choosing → Following`, and `Following` is entered only through [`selectRoute()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:378) from `Choosing`; there is no `Idle → Following` edge.
- [`TrackFromCourse`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:59) writes every vertex with its planned leg speed and cumulative time offset ([lines 112–125](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:112)), so a `RoutePlan` can be rebuilt from the stored track.
- The one exit dialog greys **Save Route to Track** through [`isRouteSaved`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3507), which reads the mode's session ([`RouteViewModel.isRouteSaved`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:427)).
- The trip figure reads the current boat position as its `from` ([`MapScreen` line 877](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:877), [2571–2575](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2571)) and measures what remains through [`RoutePlan.remainingFrom`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:73), so a followed saved route reports the remainder from today's position with no change.

## Prerequisite — how the followed line reaches the map (verify first)

- [`RouteHost`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:169) paints only from `pages` ([line 188–210](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:188)), and [`selectRoute()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:382) empties `_pages` while `Following` keeps its plan in `state.plan`, not in `pages`.
- Either the followed line is painted by a path not yet located, or `Following` currently draws no line — consistent with "nothing is device-validated".
- This plan owns the repair: `RouteHost` must paint `state.plan` when `state is Following` — slot 0 at full strength, the pin on `state.plan.destination` — independent of `pages`. One change serves both the existing `Select route` flow and the new follow-saved-route flow. Confirm on device before counting the feature done.

## Design

- **Card button.** Add `onFollowRoute: ((String) -> Unit)? = null` to [`TrackCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:471); render an `IconButton` with `imageVector = route` and content description `cd_follow_route`, gated by `summary.route && !isRecording && onFollowRoute != null`. Thread the lambda through the two card sites in [`OverlayLayer`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:516) and the list in `TrackHistoryOverlay`, up to `MapScreen`, exactly as `onResumeTrack` is threaded.
- **Plan reconstruction.** Add `TrackFromCourse.planOf(track)` as the inverse of `build`: points from `trackPoints`, `legTimesSec` from consecutive `timeOffsetMs` deltas, `start`/`destination` from the first and last points, `distanceM` and `durationSec` from the stored figures, `destinationMoved = false`, `forcedCrossingZoneNames = emptyList()`, `computedAtMs = track.startTimeMs`. Leg durations survive only to the stored millisecond, so the round-trip test tolerates drift and total duration reads from `navigatingDurationSec`.
- **View-model edge.** `Following` gains `followedTrackId: String? = null`; add `RouteViewModel.followSavedRoute(plan, trackId)`: from `Idle` only, write `RouteState.Following(plan, trackId)`. No session seeding — the discriminator is the id on the state, so the session keeps its one-home meaning.
- **Screen handler.** Add `followSavedTrack(trackId)` in `MapScreen`: no-op when the mode is already armed, then close selected-item dashboards and disarm inspect, set `routeArmed = true`, hide the drawer, load the full track through the repository accessor the resume path uses, rebuild the plan, call `followSavedRoute`, then recentre (GPS) or centre on `plan.start` (demo). A load that yields fewer than two points leaves the mode idle and arms nothing.
- **Exit dialog.** The save door greys when the followed plan carries a `followedTrackId` or `isRouteSaved`; **Continue route** stays, and the third door reads **Stop following** (`route_exit_stop_following`) when `followedTrackId != null`, else **Discard Route**. Ending following leaves the stored track intact.
- **Strings.** `cd_follow_route` and `route_exit_stop_following` in both `res/values/strings.xml` and `res/values-fr/strings.xml`.

## Build order

1. `RouteHost` paints `state.plan` in `Following` (prerequisite; device check).
2. `TrackFromCourse.planOf(track)` plus a round-trip unit test.
3. `RouteViewModel.followSavedRoute(plan, trackId)` and `Following.followedTrackId` plus a state test (Idle → Following, `end()` → Idle).
4. Card button and plumbing (`TrackCardContent` → `OverlayLayer` → `MapScreen`).
5. `followSavedTrack(trackId)` screen handler and camera behaviour.
6. `cd_follow_route` and `route_exit_stop_following` strings in both locales.
7. `gradlew :app:assembleDebug :app:testDebugUnitTest`, then the device pass.

## Settled decisions

- Exit-dialog label for a followed saved route reads **Stop following** — the normalised behaviour, 2026-09-30.
- Following a saved route stays gated by `!isRecording` — the gate is kept.
- The button appears on both the map-opened card and the list, like Resume.

## Follow-up fix — close on trigger and the camera

- The follow press must close its surface the moment it fires, and that close is the refocus — no second camera move.
- Thread `fromList` through the plumbing like `onResumeRequest`: `OverlayLayer.onFollowRequest` becomes `(String, Boolean)`, the detail-card sites pass `false`, the list passes `true`.
- `followSavedTrack(trackId, fromList)` closes synchronously — `showTrackHistory = false` from the list, `closeTrackDrawer()` from the card (its `preNavigationState` restore is the refocus) — disarms inspect synchronously, then loads, rebuilds and follows inside the coroutine.
- Drop the extra `recenterNow()` / `setCenter(plan.start)` and the stray `showTrackDrawer = false` and `closeSelectedItemDashboards()`; this supersedes the screen-handler camera clause in the Design section.

## Outcome

Shipped 2026-09-30 on `feature/route-card`: the route icon follows a saved route in the card's resume
slot, `routePlanOf(track)` rebuilds the plan, `followSavedRoute` enters `Following` carrying
`followedTrackId`, the exit dialog reads **Stop following** and greys its save door, and `RouteHost`
paints `state.plan` in `Following`. Build and the whole unit suite green, nothing device-validated.
Deviation: `routePlanOf` lives in `ui/map` rather than `data/track`, so no data type imports a ui type.
Follow-up 2026-09-30: the follow press closes its surface synchronously — `showTrackHistory = false` from
the list, `closeTrackDrawer()` from the card, whose `preNavigationState` restore is the refocus — inspect
is disarmed synchronously, and the extra camera moves are gone: one refocus, no jump. Build green.
