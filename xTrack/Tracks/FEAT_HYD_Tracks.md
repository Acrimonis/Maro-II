# Context Hydration — Tracks — 2026-10-24

**Last Bake:** 2026-10-24 06:50 UTC — written by `#bake` on `feature/track-comment-ignore-time-spend-at-la-salis`.

**Directive trace:** one covered action class was met and none stopped — implementation ran on the user's `#impl` after `assess`/`discuss`, and `#bake` / `#commit` / `#merge` were invoked by the user; no dependency was added, no machine-shaped data file was opened, the device was not touched, and every claim about the code came from a file read.

## State

The recorded comment's home-port line reads `la Salis: departure → return` instead of a time-spent duration: [`TrackNaming.homePortCommentLine()`](../../app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt) builds the pure shape, [`recomputeDescription()`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) prepends it and drops home-port `IDLE` stop lines, the departure side is the persisted [`Track.startedAtHomePort`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt), and the arrival side is a recorder `endedAtHomePort` set at finalize. `compileDebugKotlin` and the `data.track` unit suite are green with four new `TrackNamingTest` cases; the device pass is owed.

## Target Files

- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — `recomputeDescription`, `portDisplayName`, `homePortLine`, `formatClock`, the geofence fields
- `app/src/main/java/ykws/android/maro/data/track/TrackNaming.kt` — `homePortCommentLine`
- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — `startedAtHomePort` (`@ProtoNumber(23)`)
- `app/src/test/java/ykws/android/maro/data/track/TrackNamingTest.kt` — the line-shape cases

## Next Step

Device pass owed: the user deploys and verifies a recorded trip's comment shows the port departure → return line.
