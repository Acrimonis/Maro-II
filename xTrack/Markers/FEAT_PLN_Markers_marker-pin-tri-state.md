# marker-pin-tri-state — Fan Layer Tri-State Toggle

> **Parent:** xTrack/Markers/marker-pin | **Plan:** [plans/marker-pin-tri-state.md](../../plans/marker-pin-tri-state.md)
> **Date:** 2026-06-26

## Summary
Replace binary marker layer toggle with tri-state cycle: HIDDEN → SHOW_ALL → SHOW_PINNED → HIDDEN.

## Implementation
- `MarkerLayerState` enum (HIDDEN, SHOW_ALL, SHOW_PINNED) replacing `userMarkersVisible: Boolean`
- SettingsManager migration v2→v3: Bool → String enum
- `cycleMarkerLayerState()` in MarkersViewModel
- Filter `userMarkers.filter { it.pinned }` when SHOW_PINNED
- `where_to_vote` Material Symbol icon for SHOW_PINNED state
- Pin IconButtons in list card + drawer (marker-pin prerequisite)
