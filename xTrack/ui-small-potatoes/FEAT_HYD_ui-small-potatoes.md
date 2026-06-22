# ui-small-potatoes — Hydration Snapshot

**Baked at:** 2026-06-22 14:20 UTC
**Active Subfeature:** small potatoes

## Session Summary

Two small UI tweaks applied.

### remove-track-toggle
- Removed ON/OFF Switch from TrackDrawerOverlay "Track List..." row
- Recording now controlled only via TrackStatusIcon on map

### danger-icon-redesign
- WarningTriangleIcon: white filled triangle + punched-out transparent "!" (BlendMode.Clear)
- Changed from grey triangle with white "!" to white triangle with transparent cutout

## Key Files Modified
- TrackDrawerOverlay.kt — removed ON/OFF Switch from "Track List..." row
- FanIconComponents.kt — WarningTriangleIcon: white triangle + BlendMode.Clear "!"

## Next Steps
- [ ] Deploy and verify on device
