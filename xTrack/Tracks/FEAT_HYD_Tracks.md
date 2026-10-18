# Context Hydration — Tracks — 2026-10-18

**Last Bake:** 2026-10-18 12:44 UTC — written by `#bake` on `feature/route-naming` (cut from `origin/develop` by the user's `#new route-naming`). The section fold was reported and skipped: an open `## Walk` stands in the feature file.

**Directive trace:** one covered action class was met and none stopped. Authorisation held — the implementation ran only on the user's `#impl`, the idle-only-body plan having been settled by `assess, plan, discuss` first, and `#bake` / `#commit` are their own invocations. No dependency was added; no machine-shaped data file was opened; the device was not touched, so no device pass was taken; and every claim about the code came from a file read.

## State

**The two kinds' ends share one rule (2026-10-18).** [`MarkerMatcher.topNamingMatch`](../../app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt:548) is the ends' one home: the smallest zone wins, an inside match beating a proximity match only on equal size, so a near zone outranks a containing one exactly when it is strictly smaller — auto markers ignored, a pin (size zero) outranking any zone. The recorder's `endAt` and the route's `routeEndName` both read it, replacing their `firstOrNull` over the display order and the route's zone-only filter.

**A track's title body carries idle stops alone (2026-10-18).** [`TrackRecorder.idleBodyMarkers`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1589) ranks only `IDLE` boatMarkers — dive-first, then longest-idle — and `composeTripName` feeds the body from it, so a marker merely passed never names the title; the traversal's `timedScores` stays only for the comment's traversed list, and `leadingStop` folded into the new helper and was removed. Origin and destination stay positional, and the home-port refusal (`namesHomePort`) is unchanged.

**Gate.** `.\gradlew.bat testDebugUnitTest assembleDebug` green. [`MarkerMatcherNamingMatchTest`](../../app/src/test/java/ykws/android/maro/spatial/MarkerMatcherNamingMatchTest.kt) pins the selector (nested zones, the near-smaller exception, the equal-size tie, auto exclusion, the pin case).

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — `topNamingMatch`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt` — `endAt`, `idleBodyMarkers`, `composeTripName`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `routeEndName`, `composeRouteName`
- `app/src/test/java/ykws/android/maro/spatial/MarkerMatcherNamingMatchTest.kt` — the selector's cases

## Next Step

Open: no unit test pins the idle-only-body rule, and an idle stop resolves inside-or-near rather than strictly inside — both await the user's word. The plan of record is `261010_FEAT_PLN_Tracks_marker-zone-naming.md`, its §12 and `## Implemented` carrying this session.
