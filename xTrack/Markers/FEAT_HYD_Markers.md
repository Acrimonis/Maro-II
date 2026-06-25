# Markers — Hydration Snapshot (2026-06-25 17:04 UTC)

## State
- **Active subfeature:** whereami-rework
- **Status:** active — plan finalized, all review findings addressed
- **Last action:** Plan review complete. 4 findings resolved: immutable children (copy pattern), sizeOf() kept (mixed metrics correct for "most specific first"), extend existing CoastlineSpatialIndex (expose + add segmentIntersectsLand), 1km = BBox search fence (not proximity cap), UI stub for compile.
- **Branch:** feature/markers-2
- **Build:** assembleDebug ✅ (from prior merge)

## Target Files
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt` — add segmentIntersectsLand(a, b): Boolean
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt` — expose spatialIndex (drop private)
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt` — WhereAmIMatch/WhereAmIResult types, revised resolveAllMarkers(), depth-first traversal, 1km BBox fence, remove dead code
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — matchResult type, coastlineIndex injection
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — inject spatialIndex
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — temp UI stub

## Next Step
- Implement per todo list: expose index → add segmentIntersectsLand → new types → revise resolveAllMarkers → cleanup → wire → stub → build
