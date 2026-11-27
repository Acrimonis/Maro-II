# Context Hydration — Tracks — 2026-11-27

**Last Bake:** 2026-11-27 18:06 UTC — written by `#bake` on `feature/salis-out-track`.

**Directive trace:** the code moved on the user's "remove it" and `impl`; no dependency was added, no machine-shaped data file was opened, the device was not touched, and every claim about the code came from a file read; none of the covered action classes stopped.

## State

The home-port exit rule is built and green (`assembleDebug`, 1 883 tests): `AutoStartDetector` arms on any fix inside the home circle and starts a recording on a fix farther outside than its accuracy while off, past a GPS-only spike guard (Doppler ≥ 0.5 m/s, implied speed from the last fix inside ≤ `tracking.boatMaxSpeedKn`), on water and with no recovery pending. The two 5-minute waits, the persisted last stop and the never-left safety net are gone; the radius is 100 m, which the 2026-10-25 widening to 150 m (a route end 106–113 m out) contradicts for trip naming. Planned next and not started: a Navigation settings section for the auto-start (GPS / Demo / both) and the sister auto-stop on return.

## Target Files

- `app/src/main/java/ykws/android/maro/data/track/AutoStartDetector.kt` — the exit rule and its spike guard
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — `processSampleLocked`, `beginRecording`, the arrival prompt in `startIdleTimer`
- `app/src/main/assets/maro.properties` — `tracking.homePort.*`

## Next Step

The device pass is the user's: leave La Salis with tracking off and see a recording start just past the 100 m circle.
