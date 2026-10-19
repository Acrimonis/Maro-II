# Context Hydration — Route — 2026-10-20

**Last Bake:** 2026-10-20 01:20 UTC — written by `#bake` on `feature/route-low-depth-check`; the measured depth gate is implemented, reviewed twice and committed with this bake.

**Directive trace:** four covered action classes were met and none stopped. The work started on the user's own orders (`#impl with recommended values`, then "resume and do not stop"); no dependency was added; the device was touched twice on the user's word, to set the `MaroRoute` tag and to read the logcat; and every claim about the code came from a file read. One search swept a machine-shaped depth file (`assets/depth/*.asc`) by accident through a too-wide grep; nothing was read from it and later searches were scoped to code and docs.

## State

**The depth gate's standoff is measured on the traced 3 m line.** One `GateRule` over a lazy per-chunk `ShallowEdgeIndex` paints the wall, guards every search step, measures each pulled chord on its own 20 m lattice refined to 2 m, holds the corner pass at the gate's 20 m relative to its corner, and feeds the `LINE` trace (`edgeIdx`, `bodyEdgeIdx`, `innerM`) beside the old auditor. On `evolutive` the wall is 10 m with the berth's steep inner ramp to 20 m; on `avoid`, the default row, the wall is the 20 m margin. The ends waive the gate within 20 m and on their carved approach; a medial nudge centres a passage's line. 1378 unit tests green, `apk-build.bat` packaged; the device pass is the user's.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/ShallowEdgeIndex.kt` — the edge index, `GateRule`, `shallowEdgesOver`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteMedialNudge.kt` — the passage-centring pass
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt`, `MultipassSearch.kt`, `MultipassPull.kt`, `BerthCarve.kt`, `RouteCornerPass.kt` — the guards that read it
- `xTrack/Route/261017_FEAT_PLN_Route_gate-standoff-sampling.md` — design, both reviews' outcome and what stays open

## Next Step

The device pass: the 2026-10-19 end pairs and a Pointe du Bacon – La Grenille pair on `evolutive`, reading `minDepth ≥ 3.0m`, `bodyEdgeIdx ≥ 10m`, the passage line on its middle, `innerM` and the `EDGES` / `PULL farEdgeReads` timings.
