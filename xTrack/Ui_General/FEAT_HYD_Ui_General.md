# Hydration: Ui_General

**Session:** Zone auto-show re-reveal fix + adaptive isStopped StateFlow.

**State:**
- 6/7 subfeatures complete (all [x] except map-print-layout [ ]).

**What happened this session (in order):**
- Branch renamed from `feature/btn--colors` to `feature/map-layers`.
- 3-layer Compose overlay split: DirectionLine (bottom), CapArrowOverlay (middle), CenterMarkerOverlay (top).
- Replaced monolithic overlayKey with per-layer OverlayTracker (full rebuild cleanup).
- Added isStopped StateFlow derived from AdaptiveGpsPolicy (adaptive time+distance, not hardcoded SOG threshold).
- Removed STOPPED_SPEED_KN constant.
- Removed stoppedAndIdle from 300m band hide conditions.
- Fixed inside+non-compliant re-reveal: moved check from hide path to reveal early-return path (was unreachable after auto-hide).
- All changes BUILD SUCCESSFUL, all 35 unit tests pass.

**Next step:**
- Deploy and test on device — verify: zone auto-show on approach, inside+non-compliant re-reveal, per-layer OverlayTracker rebuild, isStopped detection.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — OverlayTracker, 3-layer split, draw function sink params
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — isStopped StateFlow, zoneAutoShowDecision fix
- `app/src/test/java/ykws/android/maro/ui/map/Zone300DecisionTest.kt` — updated tests for new behavior
- `plans/overlay-rebuild-full-cleanup.md` — full cleanup design plan

**Last Bake:** 2026-06-17 10:28 UTC
