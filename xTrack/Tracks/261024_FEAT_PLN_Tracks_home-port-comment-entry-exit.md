# FEAT_PLN — Tracks — home-port comment entry/exit

**Date:** 2026-10-24
**Feature:** Tracks
**Status:** design, settled 2026-10-24 — not implemented

## Context

A recorded trip's comment is built live by `TrackRecorder.recomputeDescription()` — one line per `BoatMarker`, rendered by `formatStopLine()` as `HH:mm: zones for Xmin`. An idle stop at the home port (la Salis) therefore prints a time-spent duration (`la Salis for 45min`), which the user does not need.

The home port should read its **departure and return times** instead.

## Decision

- The la Salis line shows departure → return, no duration: `la Salis: 09:12 → 17:45`.
- The times are the track's own `startTimeMs` / `endTimeMs` — the geofence-driven entry/exit, debounce-lagged by the existing 10 s / 15 s gates — so no new timestamp is captured.
- The rule applies to every trip, manual or automatic: the home port is simply treated differently, never gated on how recording started or stopped.
- The line is the comment's **first** line.
- Live: while the trip runs, only the departure time shows (`la Salis: 09:12`); the return time is appended once the trip ends.
- Any home-port idle stop line is suppressed, so a `for Xmin` duration never prints for the port.
- Detection reuses `TrackNaming.namesHomePort()` — label naming Salis, or position inside the Port Salis geofence — and the display name is the top matched marker at the port point, falling back to `la Salis`.
- The title is untouched (it already blanks the home port via `unlessHomePort()`), and existing finalized tracks keep their old comments — no migration.

## Implementation

In [`TrackRecorder.kt`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) and [`TrackNaming.kt`](../../app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt):

1. `recomputeDescription()` prepends the port line and drops home-port `IDLE` stop lines from the per-marker list.
2. The line's shape is the pure [`TrackNaming.homePortCommentLine()`](../../app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt) — `name: departure → return` from `track.startTimeMs` / `track.endTimeMs`.
3. The departure side is `Track.startedAtHomePort`, persisted on the track (a geofence exit, or a manual start inside it), so a checkpoint resume keeps it; the arrival side is a recorder `endedAtHomePort` set at finalize from the latest geofence reading.
4. `portDisplayName()` probes the geofence origin for the departure side (auto-start records its first point after the exit) and the last point for the arrival side, falling back to `la Salis`.
5. Non-port stops keep `formatStopLine()`; `MANUAL` markers are unchanged.

## Tests

- [`TrackNamingTest`](../../app/src/test/java/ykws/android/maro/data/track/TrackNamingTest.kt) pins the line shape: round trip, live departure-only, one-way return-only, and never-touching-port (null).

## Open

- Nothing blocking. A trip that never touches the port keeps today's comment unchanged.

## Implemented

Shipped 2026-10-24 on `feature/track-comment-ignore-time-spend-at-la-salis`. The departure side became a persisted `Track.startedAtHomePort` rather than a first-point geofence test (auto-start records its first point after the exit), the line shape moved to the pure, tested `TrackNaming.homePortCommentLine()`, and `portDisplayName()` probes the geofence origin on departure. Four `TrackNamingTest` cases pin the shape; `compileDebugKotlin` and the `data.track` suite are green.
