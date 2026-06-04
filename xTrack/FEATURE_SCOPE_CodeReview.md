# Feature: CodeReview

**Status:** Active
**Created:** 2026-06-04T00:00:00.000Z
**Last Modified:** 2026-06-04T00:00:00.000Z
**Active Subfeature:** frequency
**Description:**
Code-review findings for the Maro coastline app and their fixes.

**One-liner:** CodeReview feature epic.

## Subfeatures

### frequency  [x]
Distance-to-coast / water recompute currently runs on every osmdroid scroll event (30–60/s) on the UI thread → visible map jank. Throttle it and remove a redundant spatial query.

#### Todos
- [x] Throttle per-event recompute to ~6–7 Hz via `_mapCenter.sample(150ms).mapLatest{…}.flowOn(Default)` in CoastlineViewModel; move work off the UI scroll path. — DONE, build-verified.
- [x] Remove redundant double spatial query — added `isOnWater(lat, lon, distMeters)` overload; pipeline reuses one `distanceToCoast` result. — DONE, build-verified.

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt`

## Todos
- [x] #1 BBox max seed uses `Double.MIN_VALUE` (CoastlineSpatialIndex.kt:107-109) — WONTFIX: geographic scope is positive lat/lon only.
- [x] #2 `mapView` declared without `remember` (MapScreen.kt:84) — FIXED: wrapped in `remember`.
- [x] #3 Water raycast assumes sea-is-south + direction-agnostic short-circuit (CoastlineRepository.isOnWater) — WONTFIX: small scope, sea is south.
- [x] #4 Distance fallback precedes node-id prepend in stitching (CoastlineGenerator.buildSingleChain) — FIXED: both node-ID passes now run before either distance fallback; rationale documented in method KDoc.

## Rules

## Key Files
