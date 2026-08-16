# GPS Recording Regression — Diagnosis & Fix Plan

**Feature:** BoatTrace
**Status:** discussion (explain) — root cause confirmed
**Created:** 2026-08-16 07:45 UTC
**Updated:** 2026-08-16 07:52 UTC

## Symptoms

1. Current recorded track does not appear in the side drawer dash.
2. Recording GPS button stays blue (ON + idle).
3. Current track is not drawn on the map.
4. No points appear recorded (point count stays 0).

## Regression Source

The 2026-08-15 notification feature (branches `feature/notification` → `feature/feature-next`) moved the
`TrackRecorder` out of the UI/ViewModel and into [`TrackRecordingService`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:68).
Key commits:

- `feat(notification): … service-owned recorder, startup NPE fix`
- `fix(notification): exit confirmation via ModalBottomSheet recording sheet`
- `fix(notification): restore live track polyline on relaunch; skip spurious resume prompt`

Before this change, [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:895) assembled
`TrackSample`s from the UI's `NavigationViewModel` GPS pipeline and fed the recorder. After the change,
GPS mode relies on a second, service-owned [`GpsLocationSource`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:57)
plus [`AdaptiveGpsPolicy`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt:20), feeding the
recorder through `_sampleInput` ([`TrackRecordingService`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:458)).

## Root Cause (confirmed)

The recorder, `_sampleInput`, and the capture path are all healthy — demo mode records points, and the UI GPS
pipeline positions the boat. The failure is isolated to the service's own GPS producer,
[`startGpsSampling()`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:255).

- It is launched once from [`ensureRecorder()`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:205)
  in [`onCreate()`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:107) — before the service
  is foreground and, on a fresh install, before the user has granted location permission.
- [`GpsLocationSource.locationUpdates()`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:164)
  throws `SecurityException` on `requestLocationUpdates` when permission is missing; the `callbackFlow` closes with error.
- The `.catch { Log.w(...) }` in `startGpsSampling()` swallows that error, the coroutine terminates, and the GPS
  sample producer **never restarts**. The recorder stays `ON` with zero samples → blue idle icon, 0 points,
  no polyline, empty drawer stats.

The UI GPS pipeline is unaffected because `NavigationViewModel` starts its own GPS source only after
`gpsMode` is enabled (post-permission), so the boat marker keeps moving.

## Fix Direction (post-review)

Make the service GPS producer resilient instead of run-once. In
[`TrackRecordingService`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:68):

- Guarded re-arm: on `ACTION_START_RECORDING` and on `ACTION_UPDATE` with `EXTRA_IS_DEMO == false`,
  call `startGpsSampling()` only when `gpsJob == null || !gpsJob.isActive`.
- Gate on `demoMode.value == false` so the listener never spins up in demo mode.
- Call `adaptivePolicy.reset()` before re-starting so a stale anchor cannot misclassify ACTIVE/IDLE.
- Log the exception type + message; treat `SecurityException` as retryable instead of a blanket terminal catch.
- Prefer a bounded retry (or on-demand re-arm) over an unbounded `retryWhen` loop.
- Keep the eager `onCreate` start so background auto-start recording still works once permission exists.

## Verify

- Fresh install: grant permission → start GPS recording → points accumulate, icon turns green,
  polyline appears, drawer dash updates.
- Upgrade / permission already granted: GPS recording works immediately (confirms no second defect).
- Demo mode still records.
- Logcat `MaroII_TrackService` shows a `SecurityException` before the fix and none after; no terminal
  "GPS sampling failed".
