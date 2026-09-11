# Context Hydration — Mergitur — 2026-09-11

## State
The three-branch integration into `feature/mergitur` is COMPLETE, baked and ready to commit. TR, TI and GL landed as three sequential merge commits on the common base `025e1bc` (safety tag `pre-mergitur`); the TI hop required manual re-seating of TR's edits into TI's restructured map-render path, and a silent `visibleOnMap` collision that git could not see was caught by the compiler. `apk-build.bat` is green on every hop and after the KDoc correction; the scoped TI tests pass (10/0, 4/0, 3/0, one inert by design) and the three pre-existing failures are unchanged. The survival gate is proven: `MapTrackSegments.kt` is byte-identical to TI's tip. The Ask review's two findings are closed — B1 was accepted as a documented behaviour change (the resume backup is unpinned, not hidden). Baked + doctor-clean; nothing pushed.

## Target Files
- `xTrack/Mergitur/260911_FEAT_PLN_Mergitur_three-branch-integration.md` — authority plan v3 with `## Outcome`
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — resume/backup re-seated onto TI's `renderFocus`; KDoc corrected
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — TR's live-track exclusion now feeding `TrackSelectionPolicy`
- `xTrack/BoatTrace/260911_FEAT_PLN_BoatTrace_resume-confirm-backup.md` — amendment recording the accepted visibility change

## Next Step
Commit the 1 ms twin-ordering nudge (`TrackViewModel.duplicateTrack` + `MapSelectionPolicyTest`), then push `feature/mergitur` and open the PR to `develop` with the per-hop SHAs in the body, then device smoke test the confirm dialog, resume-with-backup, live polyline and map visibility/selection. Watch the consumer side of B1: the copy still consumes a display slot while it exists, but it can no longer take the tie from its original.
