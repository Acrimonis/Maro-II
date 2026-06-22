# Markers — Hydration Snapshot

**Baked at:** 2026-06-22 12:17 UTC

## Session Summary

Implementation plan evaluated — 7 gaps identified and resolved. Discussion notes at `xTrack/Markers/FEAT_PLN_Markers_discussion-notes.md`.

### Resolved gaps
1. Plan location → move from ZoneTile/ to Markers/ (deferred)
2. `segmentsIntersect` → promote to `SpatialOperations` as public function
3. Performance ~15× off → proximity ranges + bbox pre-filter make brute-force acceptable (15-50ms)
4. CoastlinePoint (Float) vs LatLng (Double) → convert on the fly
5. Missing ViewModel → separate `MarkersViewModel` with StateFlow, own `.kt` files under `markers/` package
6. Threading → normalize on async: all I/O + computation via `viewModelScope.launch` + `Dispatchers.Default`
7. Drawer integration → sibling of Dashboard/FanLayout in MapContent Box, `AnimatedVisibility`

### Design decisions (unchanged from prior bake)
- Three marker types: Pin, Circle, Corridor (rounded caps)
- Zone match = purely geometric; proximity = closest unblocked point ≤ derived range
- Proximity: pin=200m, circle=radius×3, corridor=width×3 (maro.properties, per-marker override)
- Land-blocking: 10° sampling on geometry boundary, segment-land intersection, 10m vertex grazing
- On-demand matching via boat marker tap
- Animated drawer (portrait=bottom, landscape=left)
- Neutral marker rendering (below boat/arrow)
- Management page: swipe-to-delete (track list paradigm)
- FanLayout pin icon toggle for layer visibility
- No auto-show, no ahead-cone, no export, no live coloring

### New decisions
- Bbox pre-filter on markers to gate `closestUnblockedPoint()` (4 float comparisons per marker)
- File segregation: all marker classes in own `.kt` files under `markers/` package
- Separate `MarkersViewModel` (not extending `CoastlineViewModel`)

## Next Steps
- [ ] Move plan file from `xTrack/ZoneTile/` to `xTrack/Markers/`
- [ ] Add `marker.proximity.pin_m=200` and `marker.proximity.zone_multiplier=3.0` to maro.properties
- [ ] Switch to Code mode — implement Phase A (data model + persistence)
