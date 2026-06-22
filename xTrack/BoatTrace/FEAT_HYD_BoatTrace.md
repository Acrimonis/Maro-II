# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-22 13:20 UTC
**Active Subfeature:** pinned-tracks (design)

## Session Summary

Pinned tracks design finalised — replace eye-icon visibility toggle with pin-icon, separate transparency for pinned tracks, z-order decisions.

### pinned-tracks design (2026-06-22)
- **Pin replaces eye-icon** in TrackHistoryOverlay track card
- **Separate transparency:** `trackingTransparencyPinnedNewest→Oldest` (defaults 0%→20%), history keeps existing 20%→80%
- **`trackingRenderNb` renamed** → applies to history only; pinned always render
- **Z-order:** active > pinned > past
- **No new color keys needed** — `trackingColorPinnedFrom→To` already wired
- **New protobuf field:** `pinned: Boolean` replacing `visibleOnMap`
- Full design captured in `FEAT_PLN_BoatTrace_pinned-tracks.md`

### previous (spike-rejection-v2, demo-track-fix, drift-on-idle)
- Spike rejection v2: 4-gate algorithm in TrackRecorder.kt
- Demo track off-by-one fix + stillness gate bypass
- Dead reckoning state cleared on IDLE

## Key Files Modified (this session)
- xTrack/BoatTrace/FEAT_PLN_BoatTrace_pinned-tracks.md — created (full design plan)
- xTrack/BoatTrace/FEAT_DSC_BoatTrace.md — pinned-tracks subfeature added + finalised
- xTrack/GLOBAL_CONTEXT.md — focus BoatTrace, subfeature pinned-tracks

## Next Steps
- [ ] Implement pinned-tracks per FEAT_PLN_BoatTrace_pinned-tracks.md (8 steps)
- [ ] Deploy APK and E2E verify demo track recording (from spike-rejection-v2)
- [ ] Track list UI polish per FEAT_PLN_BoatTrace_TrackList_Design.md
