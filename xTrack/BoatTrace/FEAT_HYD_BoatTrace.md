# BoatTrace — Hydration Snapshot

**Baked at:** 2026-09-11 16:15 UTC
**Active Subfeature:** resume-confirm-backup
**Branch:** feature/tracks-recording (created from origin/develop 2026-09-11)

## Session Summary

Two fixes on this branch:

1. **live-track-paint-regression** (committed `69d92a0`) — the live polyline was never created after the C3/C4
   seam extraction: the creation effect read a frozen `TrackRecorderUiState` parameter through `snapshotFlow`,
   so it emitted the launch-time `OFF` value once and never saw `OFF → ON`; append dropped every point and the
   trailing segment bailed. Proof: the pre-#225 monolith has no `snapshotFlow` at all and reads the state
   directly. Fix: effect keyed on `trackRecorderState.state`, self-healing append/trailing, `isLive` excluded
   from the map-resolve path (latent, resume-only).
2. **resume-confirm-backup** — resuming a stored track now opens a confirmation sheet (ConfirmSheet geometry)
   with a default-checked backup box; confirm writes a hidden, unpinned copy (fresh UUID, suffixed name,
   marker links stay on the original) then resumes the original; Cancel/scrim dismiss only. Wired on the list
   card — which no longer auto-dismisses — and on both dashboard cards, gated by `isRecording`. EN+FR strings.
   Ask review SOUND; build SUCCESSFUL.

## Next Step

Device E2E for both: fresh recording paints point-by-point and survives filters; resume sheet appears on all
three surfaces with the box checked, writes a backup only when ticked, and continues on the original.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (ResumeConfirmSheet, host, PendingTrackResume, closeTrackDrawer)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` (onResumeRequest + three card sites)
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` (duplicateTrack, resumeTrack)
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` (live polyline effects)
- `xTrack/BoatTrace/260911_FEAT_PLN_BoatTrace_live-track-paint-regression.md`
- `xTrack/BoatTrace/260911_FEAT_PLN_BoatTrace_resume-confirm-backup.md`
