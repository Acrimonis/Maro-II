# Context Hydration — Route — 2026-10-28

**Last Bake:** 2026-10-28 18:15 UTC — written by `#bake` on `feature/roooouuutttiiinnngggg`.

**Directive trace:** no dependency was added and no binary file opened; the device was read only after the user's word that a build was deployed and run (logcat dumps at 18:16 and 18:36), never written; the one new key (`route.approach.lone.minSavedSec`) lives in `maro.properties` with its code default following; none of the covered action classes stopped.

## State

Committed up to `41db360d` (P0 and the leftovers). Uncommitted at this bake: lone-turns P1 — `RouteApproachPass` offers a two-span window whose legs are clean, whose point turns ≥ `MIN_TURN_DEG` and whose skipping chord crosses slow water (not land) P5b's slide along its own legs (`SLID_LONE`), kept where it saves `route.approach.lone.minSavedSec` (1 s), no search where the chord's travel cannot; a `slideLoneTurns` fixture switch pins the S tests. Replayed on the device's "Around" line the 43° turn moves 1.5 km to La Fourmigue's band edge (10.9 s); the pass +23–34 ms a rung. 1 666 tests, the symmetry survey (24 pairs) green.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteApproachPass.kt` — `isLone`, `offerFor`, `Search.answerFor`, `searchAlongLegs(kind)`, the P0 count and slack probe
- `app/src/main/assets/maro.properties`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `route.approach.lone.minSavedSec`
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteApproachPassTest.kt` — `aLoneTurnSlidesAlongItsLegsOrStays`, the equivalence test's lone lines, the S line as shipped
- `xTrack/Route/261028_FEAT_PLN_Route_lone-turns.md` — §4 as built, §12–§13, `## Implemented`

## Next Step

P1's device run (the user's), then the slack probe dropped (plan §13).
