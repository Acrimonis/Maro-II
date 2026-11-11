# Context Hydration — Tracks — 2026-11-11

**Last Bake:** 2026-11-11 07:35 UTC — written by `#bake` on `feature/rooouting`.

**Directive trace:** the code moved on the user's `#implement` for step 1; no dependency was added, no machine-shaped data file was opened, the device was touched only by the user (their pass read OK), and every claim about the code came from a file read; none of the covered action classes stopped.

## State

Step 1 of the auto-start plan is shipped and read OK on the device: the circle exit and its debounce are replaced by the forgotten-start level rule (`AutoStartDetector` — rested by the latch, off `offMin` since the last stop, moved by the idle thresholds, on water by position, no recovery pending), the last stop persisted through the recorder's `onStopped` hook, the arrival prompt armed by where the start fell, and a safety net prompting once after `offMin` idle inside a trip that never left. `apk-build.bat` and 1 764 tests green; everything is uncommitted. One Ask pass was folded; its leftovers (recorder-level `observe` and discard-clock tests, three detector boundaries, the standby interval read from `AppConfig`, `Decision.fromHome` always true, a speed-less first fix, the safety-net ordering, real-time sleeps in the recorder tests) stand open in the plan's Outcome. Flagged-marker auto-start is parked as step 2, maybe later. The 2026-09-15 walk level is still open.

## Target Files

- `xTrack/Tracks/261110_FEAT_PLN_Tracks_home-circle-forgotten-start.md` — step 1, shipped; its Outcome holds the deviations and the open review leftovers
- `xTrack/Tracks/261110_FEAT_PLN_Tracks_marker-auto-start.md` — step 2, parked
- `app/src/main/java/ykws/android/maro/data/track/AutoStartDetector.kt` — the rule and the latch
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — OFF/ON detector calls, `beginRecording`, the safety net, `onStopped`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — the persisted last stop, water and recovery wiring

## Next Step

None owed on step 1. Open choices: close the review leftovers, resume step 2 from its plan, or commit when the user decides.
