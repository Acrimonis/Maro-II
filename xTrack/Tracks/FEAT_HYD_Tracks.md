# Context Hydration — Tracks — 2026-11-28

**Last Bake:** 2026-11-28 09:50 UTC — written by `#bake` on `feature/salis-out-track`.

**Directive trace:** the code moved on the user's `#impl`; no dependency was added, no machine-shaped data file was opened, the device was not touched, and every claim about the code came from a file read; none of the covered action classes stopped.

## State

Automatic tracking is built and green (`assembleDebug`, 1 917 tests): Settings › Navigation opens on three rows — start on leaving La Salis, save on returning, start on water — each a GPS / Demo toggle pair (defaults on/on, on/on, on/off). `ArrivalDetector` saves a trip back from open sea after 30 s in the 50 m circle and the map offers *Continue*; `WaterStartDetector` starts on a 50 m move on water away from the port, disarmed by a stop there until a rest; an automatic start saves pending recoveries first. The Ask pass's fixes are folded; its open items stand in the plan's Outcome, the one decision left being whether *leave off, water on* should still start a departure.

## Target Files

- `xTrack/Tracks/261127_FEAT_PLN_Tracks_auto-start-stop-settings.md` — the plan, decisions D1–D8 and the Outcome
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — the start gates, the arrival hold, `saveRecoveries`, `transitionTo`
- `app/src/main/java/ykws/android/maro/data/track/ArrivalDetector.kt`, `WaterStartDetector.kt`, `AutoStartDetector.kt`, `FixGuards.kt` — the three rules and their shared tests
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`, `MapScreen.kt`, `MapSnackbarHost.kt` — the section and the Continue row

## Next Step

The device pass is the user's: leave La Salis, come back within 50 m and stay 30 s, then try Continue.
