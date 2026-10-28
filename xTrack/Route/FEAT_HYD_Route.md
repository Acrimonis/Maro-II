# Context Hydration — Route — 2026-10-28

**Last Bake:** 2026-10-28 12:24 UTC — written by `#bake` on `feature/roooouuutttiiinnngggg`.

**Directive trace:** no dependency was added and no binary file opened (the baked region read only through the code that parses it, the saved tracks left unopened); the device was read after each of the user's runs (`adb devices`, a logcat dump, `run-as` of the markers, `dumpsys package`) and written once, its main log buffer raised to 16 MiB with its verdict line, after a run's lines rolled out of the 256 KiB buffer; none of the covered action classes stopped.

## State

Bare-bends P0, P4a, P5a and P5b and the reviews' code leftovers are implemented, uncommitted: an S of two price turns on open water is merged at its outer legs' crossing (read on the device 10:28, a 31° corner left at sea), and that crossing now slides along its own legs, won or lost — on the device's replayed "Balanced" line it turns at La Fourmigue's band edge, 12.7 s saved against 6.9. The approach pass's searches share one pattern loop, one evaluation and one incumbent; `route.turn.lateralAccelMps2`'s code default follows the properties, four fixtures pinned at the 0.33 they were measured at. 1 665 tests, both symmetry suites and `apk-build.bat` green; the harness's own lines unmoved on both device pairs.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteApproachPass.kt` — `offerFor`, `Search.answerFor`, `searchAlongLegs`, `Incumbent`, `patternSearch`
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteApproachPassTest.kt` — the S and slide fixtures
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteBendHarnessTest.kt` — `-Dmaro.harness.start|aim|replay`
- `app/src/test/java/ykws/android/maro/spatial/multipass/TurnConfigFixture.kt`
- `xTrack/Route/261027_FEAT_PLN_Route_bare-bends.md` — §9–§13 and `## Implemented`

## Next Step

None owed on the bends' code; the plan's §5 keeps the root (the pass looks along the legs a line uses, what pins a point lies on the chord that skips it) and the open contradictions beside the turn's default (`route.turn.transitionSec`, `route.speed.accelMps2` and their comments) recorded.
