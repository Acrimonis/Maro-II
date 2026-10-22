# Context Hydration — Route — 2026-10-22

**Last Bake:** 2026-10-22 18:05 UTC — written by `#bake` on `feature/speed-history-2` (renamed from `feature/whatEver`, never published); the session built the whole `Any`-pace plan, reviewed it once, fixed the review's gaps and the user's own list, and committed on the user's word.

**Directive trace:** no dependency was added, no binary file opened and the device never touched; the session started on the user's `#impl` order, every write was code, tests, strings, docs and the xTrack files, each claim about the code had a read behind it, and the one `ask` review hop ran under `#implement`; one question (which gaps change what the user sees) was rejected and answered in the user's own next message; none stopped.

## State

The `Any` pace is **built** on this branch from the plan's decisions D1–D15 and every item F, A, B, G, C, E is ticked: `PacePlateau` (middle-half average over a 2–10 min window, a gap held at the pace shown or — once a quarter of the window is read — at the readings' own average), the trip store (15 per kind, cruising speed, open-water minutes, demo flag), the day's pace, the pace's source, the Speed History section with its Pace-now line, window slider and Include-demo switch, and a Calculate that disables itself and toasts. `assembleDebug` is green and the unit suite is 1496 tests, 0 failures. Two known unverified edges: the recorder's sample path rests on `Track.markedFor` and the device, and an unchanged GPS speed value is now re-read once a second by `RouteHost`.

## Target Files

- `xTrack/Route/261021_FEAT_PLN_Route_observed-pace-reference.md` — the plan, every item ticked, `## Open points` holding the gap rule
- `app/src/main/java/ykws/android/maro/data/route/` — `PacePlateau.kt`, `RoutePace.kt`, `SpeedHistory.kt`, `SpeedHistorySeeder.kt`, `SpeedHistoryProfile.kt`
- `app/src/main/java/ykws/android/maro/ui/map/` — `RouteViewModel.kt`, `RouteHost.kt`, `SpeedHistoryActions.kt`, `MapScreen.kt`, `OverlayLayer.kt`, `MapScreenSettingsOverlay.kt`

## Next Step

The device pass, the user's own: a GPS drive reading `Now (x kn, Live)` once a quarter of the window is read, a stop, a second trip the same day starting on `Today`, Calculate, and the Include-demo switch.
