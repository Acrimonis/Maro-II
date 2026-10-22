# Context Hydration — Route — 2026-10-22

**Last Bake:** 2026-10-22 16:26 UTC — written by `#bake` on `feature/speed-history-2` (renamed from `feature/whatEver`, never published); the session closed the `Any` pace's functional review and built nothing.

**Directive trace:** no dependency was added, no binary file opened and the device never touched; every write was the plan, the Route feature file, the code map, the routing file and this file, plus the branch rename, on the user's word — D7, D8 and D12 recorded on the user's answer alone, following the session's "plan this" pattern — and each claim about the code had a read behind it, one (saved routes excluded from the history) flagged unverified rather than asserted; none stopped.

## State

The `Any` pace shipped on this branch (`ed18e5cd..2fffc8e5`, its unit tests green; the branch is `feature/speed-history-2`) and is reopened. **Every functional decision is taken** (D1–D15 in the plan's `## Review fixes`, 2026-10-22) and **nothing is built**. The plan is the build order: F (the live window average, the 4 kn stop, the day's pace, the source label), A (bugs), B (parts of the plan the code lacks), G (the demo flag and its switch), C (text), E (tests). `## Settings surface` holds the Speed History section's row order and both locales' strings, and `## Files` the whole file list. `## Review fixes` wins over the plan's upper sections, which still describe the shipped model under "superseded" notes (C7). Two code checks come first where named: how a never-classified track gets its water share (B5), and how the recorder knows it took a demo sample (G1).

## Target Files

- `xTrack/Route/261021_FEAT_PLN_Route_observed-pace-reference.md` — `## Review fixes` (F, A, B, G, C, D, E, order of work), `## Settings surface`, `## Files`
- `app/src/main/java/ykws/android/maro/data/route/` — `RoutePace.kt`, `SpeedHistory.kt`, `SpeedHistorySeeder.kt`, and the new `PacePlateau.kt`
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt`, `RouteHost.kt`, `RouteConfirmPanel.kt` — the pace feed, the day's pace, the source
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `OverlayLayer.kt`, `MapScreenSettingsOverlay.kt` — the seed, the fold-in, the Speed History section
- `app/src/main/java/ykws/android/maro/data/track/Track.kt`, `TrackRepository.kt`, `TrackRecorder.kt`, `TrackRecordingService.kt` — the demo flag
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties`, `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`, `app/src/main/res/values*/strings.xml` — keys, persistence, strings

## Next Step

Step 1 of the plan's order of work: `PacePlateau` with its worked-cases table as tests (F1, E2), `observePace` feeding it (F2), and the window and stop-speed keys with the window slider (F5).
