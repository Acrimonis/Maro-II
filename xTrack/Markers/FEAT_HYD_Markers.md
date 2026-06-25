# Markers — Hydration Snapshot (2026-06-25 18:52 UTC)

## State
- **Active subfeature:** whereami-rework
- **Status:** active — implemented + tested + hardened
- **Branch:** feature/markers-2

## Changes This Session
- WhereAmI rework: `WhereAmIMatch`/`WhereAmIResult` types, depth-first leaves-first traversal, 1km BBox search fence, `CoastlineSpatialIndex.segmentIntersectsLand()`
- Bearing reversed: marker→boat (display "SE of P1" = boat is SE of marker)
- Cardinal direction display: proximity → "NW of", zone → bare name
- BBox cap fixed: always 1km from boat (not `minOf(proximityRange, 1km)`)
- Proximity rendering fixed: drawn from boundary (radius+proximity, width/2+proximity)
- Sliders normalized: radius/width 0-1000m step 25m default 100m, proximity 0-1000m step 25m default 100m
- Touch target: CenterMarkerOverlay min 48dp clickable area
- Dead code removed: old `MatchResult`, `TieredMatchResult`, `precisionComparator`, `geometryTypeRank`, `distanceFromResult`, `segmentIntersectsPointList`
- UI polish: unified text format (§8), B/W edit icons (§10), viewing drawer redesign (§11), color picker (§9) — see `FEAT_PLN_Markers_next-session-ui-polish.md`

## Build
- assembleDebug ✅

## Next Step
- User validation on device
