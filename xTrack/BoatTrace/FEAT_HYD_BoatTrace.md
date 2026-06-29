# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-29 12:48 UTC
**Active Subfeature:** notif-fix (implemented)

## Session Summary

Notification simplification — stripped extended info (speed/elapsed/distance) from notification layout, keeping only collapsed title line. Reverted RemoteViews column experiments and adaptive icon explorations. Build: ✅

### idle-time-tracking implementation (2026-06-28)
Idle time tracking — added `idleDurationSec` accumulator in TrackRecorder using `isStopped` transition detection. Displays in track history summary cards, live track card (Nav corrected to elapsed-minus-idle), and menu drawer recording status. Fixed `navigatingDurationSec = totalElapsedSec - idleDurationSec`. Real-time UI refresh on every addPoint() call while idle.

### track-list-render-indicator implementation (2026-06-24)
- **Utility:** `TrackPolylineAppearance` data class + `computeTrackPolylineAppearance()` pure function — computes ARGB + stroke width from index/total/transparency/color settings
- **MapScreen:** Refactored history and pinned polyline loops to call the utility (replaced ~22 lines of duplicated inline computation)
- **TrackHistoryOverlay:** Accepts 10 render-settings params (`tracksVisible`, `trackingRenderNb`, transparency/color for past + pinned). Pre-computes `accentColors: Map<String, Color>` via `remember` keyed on all settings + summaries
- **TrackCardContent:** Wrapped in `Row(IntrinsicSize.Min)` with 4dp accent bar `Box(fillMaxHeight)`. Color: exact ARGB from utility for visible tracks, muted grey at 15% alpha for non-visible (beyond `trackingRenderNb` or `tracksVisible=false`)
- **Color fix:** Explicit 4-component `Color(red, green, blue, alpha)` instead of `Color(argbInt)` to avoid sign-extension issues with negative ARGB values

## Key Files Modified (this session)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — utility function + refactored LaunchedEffect + call site params
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — 10 new params, accent color precomputation, Row+accent bar in TrackCardContent, `fillMaxHeight` + `IntrinsicSize` imports
- `xTrack/BoatTrace/FEAT_DSC_BoatTrace.md` — subfeature entry + implemented section
- `xTrack/BoatTrace/FEAT_HYD_BoatTrace.md` — this file
- `xTrack/BoatTrace/FEAT_PLN_BoatTrace_track-list-render-indicator.md` — design plan

## Next Steps
- [ ] Deploy APK and E2E verify accent bars on device
- [ ] Track list UI polish per FEAT_PLN_BoatTrace_TrackList_Design.md
