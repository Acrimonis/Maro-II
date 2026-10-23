# Context Hydration — Tracks — 2026-10-23

**Last Bake:** 2026-10-23 19:19 UTC — written by `#bake` on `feature/route-toggle-color-like-track` (cut from `origin/develop` by the user's `#new route-toggle-color-like-track`).

**Directive trace:** one covered action class was met and none stopped. Authorisation held — implementation ran only on the user's `#impl`, the design settled through `assess` and `discuss` first, and `#bake` / `#commit` / `#archive` are their own invocations. No dependency was added; no machine-shaped data file was opened; the device was not touched, so no device pass was taken; every claim about the code came from a file read.

## State

**The selected item paints on its own top overlay (2026-10-23).** [`MapTrackSelectionOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt) owns the `track_selected_*` titles and is keyed on the selected id, the two persisted eyes, the kind axes and the bulk `rebuildGeneration`, so a selection change swaps one line instead of rebuilding every stored line — the flicker's cause. [`OverlayZOrder`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt) slots `track_selected_` in the track tier, above history/pinned and below the route and live recording.

**The bulk pass no longer knows the selection.** [`MapTrackOverlayHistoryDiff`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt) drops `highlightedTrackId` and the two eyes from its keys and draws every line unselected, clears the render focus, passes no selected-id escape and no longer lifts the highlighted line.

**Gate.** `.\gradlew.bat testDebugUnitTest --tests TrackRouteRoleTest --tests TrackRenderFlagsPathTest` green.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — `MapTrackSelectionOverlay`, the bulk pass's selection-free keys and loops
- `app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt` — the `track_selected_` prefix
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the overlay's wiring and the bulk call

## Next Step

Device pass owed: the user deploys and verifies a selection change no longer flickers, for a recorded track and a saved route.
