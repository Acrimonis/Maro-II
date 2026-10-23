# Context Hydration — Tracks — 2026-10-23

**Last Bake:** 2026-10-23 19:03 UTC — written by `#bake` on `feature/route-toggle-color-like-track` (cut from `origin/develop` by the user's `#new route-toggle-color-like-track`).

**Directive trace:** one covered action class was met and none stopped. Authorisation held — implementation ran only on the user's `#impl`, the design settled through `assess`, `eli` and `discuss` first, and `#bake` / `#commit` are their own invocations. No dependency was added; no machine-shaped data file was opened; the device was not touched, so no device pass was taken; every claim about the code came from a file read.

## State

**The selected-item speed toggle is two disk-persisted per-kind booleans, off by default (2026-10-23).** [`SelectionEyeStore`](../../app/src/main/java/ykws/android/maro/data/settings/SelectionEyeStore.kt) holds `track_eye` and `route_eye` in their own prefs file, outside `AppSettings` and `maro.properties`. On bands the open item's fill, off paints its gold highlight, and the kind's stored axes (`trackColours` / `routeSpeedColor`) are never touched by the toggle.

**`MapScreen` owns the values and the flip.** `trackEyeOverride` / `routeEyeOverride` seed from the store and persist on tap; the header's `bandedOn` reads the open kind's boolean, and the history/route/pinned loops plus the legend helpers read the open kind's own eye. `lineRenderPlan` keeps the one banded/gold decision, and `selectionColoursAxis` / `selectionBandedAfterTap` are gone, the tap being a plain flip.

**Gate.** `.\gradlew.bat testDebugUnitTest --tests TrackRouteRoleTest --tests TrackRenderFlagsPathTest` green. `TrackRouteRoleTest.aSelectedRouteTakesTheTrackGoldFace` pins the route's gold face; `TrackRenderFlagsPathTest.theLegendSeesARouteOnItsOwnAxisAndEyeAlone` pins that the open route's fill is its own eye.

## Target Files

- `app/src/main/java/ykws/android/maro/data/settings/SelectionEyeStore.kt` — the two persisted booleans
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the two states, the tap, the header readout
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — two-eye signature, unified `lineRenderPlan`, the legend helpers
- `app/src/test/java/ykws/android/maro/ui/map/TrackRouteRoleTest.kt` — the route gold face
- `app/src/test/java/ykws/android/maro/ui/map/TrackRenderFlagsPathTest.kt` — the per-kind legend gates

## Next Step

Device pass owed: the user deploys and verifies the toggle on a selected track and a selected route across an app restart.
