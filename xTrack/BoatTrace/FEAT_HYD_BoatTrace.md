# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-22 14:54 UTC
**Active Subfeature:** pinned-tracks (implemented)

## Session Summary

Pinned tracks fully implemented — pin icon replaces eye-icon in track list, pinned tracks always render with own color/transparency, z-order split, fan button invalidate fix.

### pinned-tracks implementation (2026-06-22)
- **Data model:** `pinned: Boolean` in Track (ProtoNumber 14) and TrackSummary (ProtoNumber 12)
- **Repository:** `TrackRepository.setPinned(id, pinned)` — reads protobuf, flips bit, saves
- **ViewModel:** `TrackViewModel.setPinned(id, pinned)` — toggles via repository, refreshes summaries
- **UI:** Pin icon (Canvas-drawn, filled blue/opaque when pinned, gray/faded when not) in TrackHistoryOverlay
- **Rendering split:** Pinned tracks always render (no count limit), history capped by `trackingRenderNb`. Separate transparency per group (pinned 0%→20%, history 20%→80%). Z-order: active > pinned > history
- **Settings:** "Number of history tracks" (was "Number of tracks"), "History transparency" (was "Transparency"), new "Pinned transparency" RangeSlider
- **Colors:** Pinned defaults amber→orange (0xFFFF6F00→0xFFFF8F00), distinct from history blue
- **Fan button fix:** `mv.invalidate()` before early return in `!tracksVisible` path — toggle-off now hides tracks immediately

### previous (adaptive-isstill review, settings guidelines)
- Reviewed adaptive-isstill plan against guidelines + implementation
- Normalized `docs/settings-page-guidelines.md` to actual codebase patterns
- Fixed stop detection grouped card (SettingsToggleRow → inline Row)
- Committed + merged + pushed

## Key Files Modified (this session)
- `app/src/main/java/ykws/android/maro/data/track/Track.kt` — pinned field
- `app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt` — setPinned()
- `app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt` — setPinned(), updateTrack simplified
- `app/build.gradle.kts` — TRACKING_TRANSPARENCY_PINNED_FROM/TO, pinned color defaults
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — pinned transparency fields/prefs/keys
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — eye→pin icon
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — rendering split, pinned slider, relabels, invalidate fix
- `docs/settings-page-guidelines.md` — normalized to actual patterns
- `xTrack/BoatTrace/FEAT_DSC_BoatTrace.md` — pinned-tracks [x], implemented section
- `xTrack/BoatTrace/FEAT_HYD_BoatTrace.md` — this file
- `xTrack/GLOBAL_CONTEXT.md` — updated pointers

## Next Steps
- [ ] Deploy APK and E2E verify pinned tracks on device
- [ ] Track list UI polish per FEAT_PLN_BoatTrace_TrackList_Design.md
