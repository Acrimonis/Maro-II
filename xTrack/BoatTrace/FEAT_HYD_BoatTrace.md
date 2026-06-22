# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-22 10:48 UTC
**Active Subfeature:** track now demo (done)

## Session Summary

Spike rejection v2 — four-gate algorithm replacing 50kn hard cap.

### spike-rejection-v2 (2026-06-22)
- Replaced single 50kn hard cap with 4-gate algorithm: GPS recovery, context cap (32/120kn), direction multiplier (sea only), acceleration gate (10/30kn/s)
- Land/sea auto-detection from GPS speed history
- Demo mode bypasses all gates
- 8 new constants, 7 new fields, 4 helper methods — self-contained in TrackRecorder.kt

### demo-track-fix (2026-06-21)
- Fixed `recordingPoints` off-by-one in `TrackRecorder.addPoint()`
- Added `gpsMode` constructor parameter, bypassed stillness gate in demo mode

### drift-on-idle (2026-06-22)
- Clear `deadReckoningState = null` on IDLE transition

## Key Files Modified
- TrackRecorder.kt — spike-rejection-v2 (main), gpsMode param, stillness gate, off-by-one fix
- TrackViewModel.kt — pass settings.gpsMode
- CoastlineViewModel.kt — clear deadReckoningState on IDLE

## Next Steps
- [ ] Deploy APK and E2E verify demo track recording
- [ ] E2E verify GPS real-world track recording
- [ ] Track list UI polish per FEAT_PLN_BoatTrace_TrackList_Design.md
