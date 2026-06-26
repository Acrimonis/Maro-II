# marker-pin — Pin Toggle Implementation Plan

> **Parent:** xTrack/Markers | **Plan:** [plans/marker-pin.md](../../plans/marker-pin.md)
> **Date:** 2026-06-26

## Summary
Add pinned/unpinned toggle to all markers using same IconButton pattern as track pins (Icons.Filled.LocationOn / Icons.Outlined.LocationOff).

## Implementation
- `UserMarker.pinned: Boolean` — serialization-safe (default false + ignoreUnknownKeys)
- `MarkersViewModel.togglePin(markerId)` — repo.update + reload
- Pin IconButton in MarkerCardContent header (left of Edit)
- Pin IconButton in MarkerDrawer ViewingContent header (before Edit)
- Callback chain: MapScreen → OverlayLayer → MarkerManagementOverlay → SwipeToDeleteMarkerCard → MarkerCardContent
