# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-24 11:09 UTC
**Active Subfeature:** track-list-render-indicator (implemented)

## Session Summary

Track list render indicator — each card now shows a 4dp left-edge accent bar previewing the track's polyline color+alpha on the map. Extracted shared `computeTrackPolylineAppearance()` utility, refactored MapScreen rendering loops, added `IntrinsicSize.Min` + explicit 4-component `Color()` to fix bar visibility.

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
