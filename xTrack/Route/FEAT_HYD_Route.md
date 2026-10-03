# Context Hydration — Route — 2026-10-03

**Last Bake:** 2026-10-03 21:35 UTC — written by `#bake`; absence means never baked

**Directive trace:** One session on `feature/route-rendering`: the saved-route casing plan was pivoted to a dashed display on the user's word and the casing pass rolled back; no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim about the code followed a read.

## State

**A saved route draws dashed, plain and speed-coloured alike** — `TrackRenderPlan` carries a `route` role, the segment builders (`buildSegmentOverlays` / `buildBandSegmentOverlays`) thread a `dashed` flag into `segmentOverlays`, and a route's whole stroke takes a `DashPathEffect` at the rhythm read from `map.track.width.route.dashOn` (6.6666667) / `dashOff` (3.3333333); recorded tracks and the gold selection stay solid, and the direction arrows stay solid.

**The rhythm is file-driven** — `map.track.width.route.dashOn` / `dashOff` read through `AppConfig.trackRouteDashOnDp` / `trackRouteDashOffDp` and join the effect's rebuild keys, and the route core follows `map.track.width.route=3.0`. The R95 ladder (Fast · Balanced · Fun ordered by ETA) landed from develop during the rebase; the R93 selected-route edge stands.

Build green (`apk-build.bat`) and the scoped `ui.map` + `config` + `data.track` suites green; the route-flag test pins that a banded route and a banded track differ only by the flag, and the width guard covers the two dash keys.

## Target Files

- `MapTrackOverlayEffects.kt` — the `route` role on `TrackRenderPlan` and the `dashed` threading through `plainPath` / `bandedPath`
- `MapTrackSegments.kt` — the `dashed` flag through the segment builders and the route-dash `DashPathEffect`
- `AppConfig.kt` + `maro.properties` — `trackRouteDashOnDp` / `trackRouteDashOffDp` and `map.track.width.route=3.0`
- `TrackRouteRoleTest.kt` — the banded-route-vs-banded-track flag test
- `TrackOutlineTest.kt` — the width guard and the dash-key test

## Next Step

The device pass: a saved route dashed beside a recording, a speed-coloured dashed route, and the arrows still solid.
