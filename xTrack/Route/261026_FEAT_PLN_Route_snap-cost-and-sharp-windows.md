# Corner snap cost and sharp sweep windows

## Request

Implement the user's 2026-10-26 instruction: make snapping compare total travel time plus penalties,
and remove the sweep's 45-degree gate while retaining its four-segment cap and clearance checks.

## Implemented

- `snapToCorners` uses `MultipassPull.totalSec` for the two replacement legs and the two existing legs.
  It accepts a move only when clearance passes and summed cost does not increase, using the already
  snapped predecessor when earlier vertices have moved. An unsafe candidate exits before pricing.
- `MultipassPull.sweep` tries windows up to `SWEEP_WINDOW_MAX_SPAN` regardless of turn angles,
  longest first, then falls back to the triple. The angle constant and span helper are removed;
  clearance, total-cost comparison, field-price allowance, fixed endpoints and termination stay intact.
- Current engine and feature documentation describe the implemented behavior; historical plans remain
  historical records.

## Target files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassPrimitives.kt`
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt`
- `app/src/test/java/ykws/android/maro/spatial/multipass/CornerSnapCostTest.kt`
- `app/src/test/java/ykws/android/maro/spatial/multipass/AvoidSweepWindowTest.kt`
- `xTrack/Route/FEAT_DOC_Route_engines.md`
- `xTrack/Route/FEAT_DSC_Route.md`

## Verification

The Linux equivalent of `apk-build.bat` and the affected routing tests succeeded using the existing
offline JDK, Android SDK and Gradle cache:

```sh
env JAVA_HOME=/tmp/maro-jdk ANDROID_HOME=/tmp/maro-android-sdk \
  ANDROID_SDK_ROOT=/tmp/maro-android-sdk GRADLE_USER_HOME=/tmp/maro-gradle \
  bash ./gradlew --offline :app:testDebugUnitTest \
  --tests 'ykws.android.maro.spatial.multipass.*' \
  --tests 'ykws.android.maro.spatial.RouteAvoidEngineTest' \
  --tests 'ykws.android.maro.spatial.RouteEvolutiveEngineTest' :app:assembleDebug
```

- Build successful; 424 tests discovered across 63 suites, 421 passed, 3 skipped, no failures or errors.
- Five snap tests cover an unpriced detour, a faster snap with a higher penalty, an insufficient penalty
  saving, a worthwhile penalty-saving detour, and a cheaper shortcut blocked by clearance.
- Nine sweep tests cover the existing allowance, flat windows, two sharp bends removable together but
  not individually, an overpriced sharp-window shortcut, clearance before pricing, a clear five-segment
  shortcut excluded by the cap while shorter shortcuts are blocked, endpoints and determinism.
- Skipped baked-data tests: `RouteMissedTurnListingTest.listEveryBendThePassLeftSharp` and
  `RouteDirectionSymmetryTest.randomPairs` / `namedPairs`.
- Independent review completed one feature-coverage pass and one code-health pass with no actionable
  findings. `git diff --check` passed.

The first Gradle launch inside the sandbox stopped before compilation on its network-interface lookup;
the same offline command succeeded with approved access for local Gradle process communication.

## Limits

The supplied Le Village Englouti route was not replayed: the baked coastline, depth and regulated-zone
data used by that run are unavailable locally. Synthetic tests establish the requested decisions, not
the exact geometry of a regenerated route.

Removing the angle gate can add clearance and price sampling at sharp bends. The retained span cap
bounds candidate count per anchor, not chord length, repeated sweep work or total runtime; runtime
impact was not measured. Snapping remains a nearest-corner local choice and cleanup remains bounded,
so neither promises a globally optimal route or the removal of every visible bend.
