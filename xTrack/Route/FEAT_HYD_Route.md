# Context Hydration — Route — 2026-08-16

**Active Subfeature:** none

## State
Planning phase complete; no implementation code written yet. Spec fully settled: adaptive CDT navigation mesh prebaked with JTS + poly2tri (src/test, testImplementation), time-based A* cost (distance ÷ min(cruiseSpeed, zoneLimit)), long-press → preview-and-zoom → confirm interaction. Isolation design finalized: all code under ykws.android.maro.route/, three shared-file edits (MapScreen hook, menu entry, settings section).

## Target Files
- `xTrack/Route/FEAT_DSC_Route.md` — single canonical plan (spec + isolation design + 10-step order)

## Next Step
Start implementation at step 1: route models + protobuf mesh schema + RoutePointQueries/RouteGeometry interfaces.
