# Movable slow-water approach

## Request

Implement the user's 2026-10-26 instruction to optimise the rock approach and adjoining open-water
stretch together, allowing the slow-zone crossing point to move while preserving clearance, with a
regression test. The supplied Le Village Englouti log shows a BAND price refusal at the offshore bend.

## Implemented

- `RouteApproachPass` searches a new intermediate point over bounded windows spanning slow-water and
  adjoining unpriced legs. It can move the crossing and remove an offshore bend together, where a
  deletion-only shortcut loses to the existing crossing. Search bounds and resolution live in that pass.
- Source-owned BAND/ZONE price arms identify eligible stretches; marker or depth prices alone do not
  trigger this refinement. Every proposal still pays all sources and the berth through `totalSec`.
- Only strict whole-window cost improvements are accepted. Both replacement legs use `legClear` with
  the existing setup, preserving coast, rastered wall, measured depth gate and real endpoint waivers.
  New points stay inside the corridor, and the route endpoints never move.
- Candidate exploration is cancellable and canonicalised for reverse-route symmetry. `APPROACH`
  diagnostics report moves, tested candidates and saved weighted cost; a bounded local search does not
  claim a globally optimal route.
- `RouteFinePass` invokes the pass after the deletion sweep and before straightening and rounding.

## Target files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteApproachPass.kt`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt`
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteApproachPassTest.kt`
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteFineReachabilityTest.kt`
- `xTrack/Route/FEAT_DSC_Route.md`
- `xTrack/Route/FEAT_DOC_Route_engines.md`

## Verification

- The focused approach suite passed: seven tests covering a pinned slow-disc crossing that the old
  deletion sweep cannot improve, additional coast and depth obstacles, determinism and reversed ends,
  corridor bounds, marker-only exclusion and cancellation.
- The fine-stage reachability test also requires the approach stage for both shipped grid plans.
- The regression uses the production shortcut allowance and still proves that deletion alone retains
  the bend, while moving the approach removes it at lower total cost.
- The multipass suite plus `RouteAvoidEngineTest` and `RouteEvolutiveEngineTest`, and `assembleDebug`,
  succeeded offline on the standalone upstream-based branch: 424 tests across 63 suites, 421 passed,
  three baked-data tests skipped, no failures.
  The skipped tests are the missed-turn listing and the named/random direction-symmetry data replays.
- `git diff --check` passed. Independent feature-coverage and code-health review passed with no blocking
  findings. Its optional coverage observation was resolved by narrowing the depth test's name to the
  measured wall it actually exercises; it does not claim to exercise an endpoint requiring a waiver.

Build and regression command (using the existing local toolchain and cache):

```sh
env JAVA_HOME=/tmp/maro-jdk ANDROID_HOME=/tmp/maro-android-sdk \
  ANDROID_SDK_ROOT=/tmp/maro-android-sdk GRADLE_USER_HOME=/tmp/maro-gradle \
  bash ./gradlew --offline :app:testDebugUnitTest \
  --tests 'ykws.android.maro.spatial.multipass.*' \
  --tests 'ykws.android.maro.spatial.RouteAvoidEngineTest' \
  --tests 'ykws.android.maro.spatial.RouteEvolutiveEngineTest' :app:assembleDebug
```

## Limits

The regression is synthetic geometry reproducing the failure mode, not a replay of the GPX against the
device's baked datasets. The exact brown/red line and the final rounded route have not been validated
against those datasets. Additional search and field sampling near slow-water approaches costs runtime;
the bounded search controls candidate count, not the cost of each clearance/price query.

The first Gradle invocation stopped before compilation on the sandbox's network-interface restriction;
the focused suite succeeded offline with approved access for local Gradle process communication.

## Upstream scope

This change is based directly on upstream `develop` at `b1f40aa2`. The earlier snapping and sweep-angle
changes in PR #392 are not included; the approach regression and build also pass without them.
