<!-- scope: feature -->
# Port-zone naming exclusion and route-comment rung removal

**Date:** 2026-10-24
**Feature:** Tracks
**Status:** shipped 2026-10-24

## Context

Two follow-ups to the home-port normalization. A marker whose point sits inside the home-port circle can still reach a recorded trip's name body and its comment fallback, and a saved route's comment head carries a preference rung the user no longer wants.

## Decision

1. Exclude from route and track naming every marker whose point is inside the home-port geofence (`43.5696, 7.1312`, radius 50 m). The two ends of both kinds are already positional-only; the two remaining leaks are the track's name body and its traversed-marker comment fallback.
2. Remove the saved route comment's preference rung — the `Closest | Balanced | Detour` segment the code doc calls "type" — and its separator dot, leaving `#rank • pace • pref`.

## Implementation

- [x] In [`TrackRecorder.idleBodyMarkers()`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), drop any idle stop whose position stands inside the home-port circle, so it never reaches the name body.
- [x] In [`TrackRecorder.traversedMarkers()`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), drop a contact whose point stands inside the circle, so it never lands in the comment fallback.
- [x] In [`TrackRecorder.computeFinalTitle()` and `pollTitle()`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), drop a stop whose position stands inside the circle, so the legacy title fallback never names the port either — folded in from the Ask hop.
- [x] In [`MapScreen.routeCommentFor()`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), drop the `rung` element from the comment head and correct the KDoc's "type" wording; the `•` goes with the element through the join, the head now built by [`routeCommentHead()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt).
- [x] Retire the now-unread rung state: `RouteCommentFacts.rungResIds` and [`RouteViewModel.followedRungResIds`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt) (with its four writes), leaving `commentFacts()` rank-only.

## Tests

- [x] Recorder test: a port-zone idle marker stays out of the body and the traversed fallback.
- [x] Comment-shape test: the route comment head reads `#rank • pace • pref` with no rung.

## Open

- None.
