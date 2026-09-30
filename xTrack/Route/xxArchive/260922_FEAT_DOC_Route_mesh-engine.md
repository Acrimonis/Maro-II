# Route — the mesh engine, as it was

**Date:** 2026-09-22 · **Status:** retired and removed — the code behind it is deleted by [`260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md`](260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md)
**Provenance:** everything below is read off the engine's own sources before their deletion, and off the two plans that recorded its measurements — [`260919_FEAT_PLN_Route_registration-and-drift.md`](260919_FEAT_PLN_Route_registration-and-drift.md) and [`260920_FEAT_PLN_Route_trajectory-quality.md`](260920_FEAT_PLN_Route_trajectory-quality.md) — both archived on 2026-09-22.

## 1. What it was

- A **prebaked navigation mesh**: a triangulated fabric of the app's own water, shipped as a protobuf `.bin`, with an on-device A\* over its flat arrays. It was the first engine and the one the feature shipped end to end with.
- The mesh carried **geometry and connectivity only** — never a limit and never a time. The effective limit was resolved live while a route was asked for, so a regulation change or a cruise-speed slider never forced a rebake.

## 2. Flow

1. **Bake (off-device, test classpath only, `-Dmaro.prebake=true`).** `RouteMeshBuilder` read the coastline, regulated-zone and depth `.bin` inputs, built a planar overlay with JTS, triangulated per face with a constrained Delaunay (poly2tri), and refined **bounded and graded**: ~60 m near a constraint, ~500 m offshore, grading over about 1.2 km. Water shallower than **2.5 m** was never meshed, the connectivity pass held a **30 m minimum channel width**, and each triangle was labelled with the stretch of connected water it belonged to. A final pass marked each edge that lay inside the 300 m coastal band. The result was written through `RouteMeshSerializer` as javalite packed primitive arrays with CSR offsets.
2. **Load (on-device, once).** `RouteMeshRepository` stream-deserialized the asset on `Dispatchers.Default` behind a single-flight `Mutex`, holding the flat arrays the search reads — one representation serving file, memory and search.
3. **Search (per route).** `RouteSearch` ran an A\* on the flat arrays. The edge cost was **pure time**, `lengthM ÷ min(cruiseSpeedKn, limitKn)`, with the limit recomputed per search from the live zone layer and the band's value; the heuristic was `distance ÷ cruise`, admissible by construction. The traversal rule was **two runs**: the first forbade any priced zone's interior, the second priced them and ran only when the first found nothing, and that second run reported the crossing by name.
4. **Post-passes on the chain.** A collinear merge; `RouteShortcut`, a greedy straightening that admitted a candidate only when the bake's kept triangles proved it lay on the boat's own stretch **and** it was no dearer than the legs it replaced; then `RouteFillet`, which replaced a corner with a chorded arc of radius `R = v² ÷ a` at the lateral-acceleration cap whenever the water allowed and the arc was not dearer than the corner.
5. **The drawn clock.** `RoutePlanTiming.drawnLegSeconds()` read the plan's seconds off the **drawn polyline**, each sub-leg at the limit inherited from the chain edge it was cut from. An invariant in `RouteSearch.buildSuccess()` asserted that the drawn line's own cost never exceeds the seconds the search accumulated; on breach it returned the raw node chain with a warning rather than failing the route.
6. **Output.** `RouteResult.Success` plus the engine's own dossier, `RouteMeshDetails`.

## 3. Limitations and issues

- **The mesh gated the whole feature.** The toggle opened only on `RouteMeshState.Ready`, so a missing or stale `.bin` did not degrade routing — it removed the mode. A rebake was owed for every regulation or depth change, and a mesh carried nothing that could detect a stale input.
- **Nothing about the line was straight by construction.** Because the cost was time alone on a fabric that is dense inshore, the optimum hugs whichever constraint is cheapest: a bend every ~60 m along the coast, single corners of 100°+, and a zone rounded about a metre off its edge. Straightness had to be bought back afterwards, by a turn penalty, a merge and a shortcut pass — three passes that each had to re-price what they changed.
- **The gate closed most of the box's water.** Of 128 247 water cells in the box (5 130 km²), **95 754 had no depth reading at all (74.7 %)**, and the gate closed every one of them by design: 3 794 km² that could never be routed. This was the largest single cause of "missing water", an order of magnitude above the three candidate causes the study set out to test, and it is what the reported bay turned out to be.
- **The fillet shipped green and had never drawn a curve.** Its arc was built on `R ÷ tan(θ/2)` and `R ÷ sin(θ/2)` where `R · tan(θ/2)` and `R ÷ cos(θ/2)` belong, so the sweep it produced was never the turn and it refused every corner not within a ten-thousandth of a right angle — and **every test case sat at 90°**, where the two forms agree. The geometry was gathered into one `RouteTurnGeometry` and cases at 30°, 60° and 120° were added.
- **The clock over-reckoned the drawn line.** The trip figure described the path the search priced rather than the line on the screen, so after the straightening passes the plan's time overestimated the drawn route by 3 % to 15 % on the measured pairs — the wound that `RoutePlanTiming.drawnLegSeconds()` was written to close.
- **A berth had to be a price, not a wall.** Rounded zones with 0 m clearance were a consequence of the time-only cost; the hard 25 m buffer that replaced it held the clearance but cost **13 % of the sounded water** and was re-done as a clearance price in the seconds the search already spends.
- **Unknown soundings were bridged, not trusted.** Keeping them was far too broad — 46 621 nodes and 3.84 MB became 111 497 and 9.55 MB — so the policy became "keep an unsounded triangle only where its own water reconnects two stretches", which took `nice-menton` from 21 stretches to 13 for +19 nodes and +1 249 bytes, admitting 0.0 km² of unsounded water against 1 228.5 km² that route.

## 4. Performance

- **The bake shipped 46 621 nodes, 136 853 edges and 3.1 MB** for `nice-menton`, and its own benchmark put the worst-case search at **127.9 ms**, inside the ≤ 500 ms target it was built against. Later the bake was made to ship its 90 242 triangles too, so a post-pass could test containment exactly.
- **The load** was a few MB decoded once on `Dispatchers.Default`, which is why the repository held the result rather than re-reading the asset per search.
- **The trajectory probe** read the long pair at **413 vertices · 7 366° of total turn · 110° worst corner · 0 m minimum zone clearance** in its first round, and after the merge, shortcut and corrected fillet at **255 vertices · 6 931° · 100°**, with length and ETA moving inside a quarter of a percent.
- **The inshore pairs**, once P2's traversal rule was adopted (8 of 8 metric pairs routed, 0 lost, none forced), read e.g. **18 vertices · 130° · 20° worst · 20 m clearance · 4.18 km · 5.6 min**, with the drawn figure against the priced one at −15.23 % — the reading that produced the drawn clock.
- **No on-device reading of this engine was ever taken**: the numbers above are the JVM prebake benchmark, the probe and the harness, and that gap is part of why it was replaced rather than tuned.

## 5. Why it went

- The line on the water was the user's own verdict — the study was opened on "a track that draws jagged, corners taken sharply at regulated-zone edges" — and the fixes were never sufficient: each pass that straightened the line created a new seam (the shortcut ignoring a zone the chain had rounded; the fillet's re-pricing), and the invariant that policed them was itself the thing that had to be extended.
- The mesh's **maintenance cost** is structural rather than incidental: a fabric of the water is stale every time the soundings or the regulation move, and the app cannot tell that it is.
