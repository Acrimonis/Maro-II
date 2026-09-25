<!-- scope: feature -->
# Route — avoid engine: a zone tangent and coarse-to-fine precision

**Folded 2026-09-25** into [`260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md`](260924_FEAT_PLN_Route_avoid-soft-sources-and-curves.md) — this plan's four changes are that plan's `## Amendments` section; Changes 1 & 2 (the switches) shipped 2026-09-25 and are folded below, while 3 & 4 stay in design.

**Created:** 2026-09-25 · **Branch:** `feature/route-avoid` · **Status:** in design — Changes 1 & 2 shipped 2026-09-25, 3 & 4 remain

## What this is for

Two changes remain, asked at the user's word and corrected by two independent reviews. **Performance is the first requirement**, so Change 4 keeps the fast route-derived path and the exact aim-independent graph is dropped.

1. An **identical tangent look-ahead for the 300 m zone**.
2. **Coarse-to-fine precision** — a fine grid only around the coarse path — instead of the corner graph.

## Implemented (2026-09-25)

- **Change 1 — depth avoidance on/off.** `route.avoid.depthGate.enabled=true` (default) gates the depth source; with it off, [`AvoidWorld.load()`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidWorld.kt:127) loads the coastline only and [`RouteAvoidEngine.prepare()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:92) arms on `coastlineReady` alone.
- **Change 2 — 300 m zone on/off.** `route.avoid.zone300.enabled=true` (default) gates the band's one soft source; the rasterize sweep's `bandM`/`bandPriceM` arguments are removed so the field writes the price once and the pull's chord guard stays alive.

## Change 3 — an identical tangent look-ahead for the 300 m zone

- The band's tangent corners are the same coastline convex corners offset by `bandReachM(bandWidthM, zone300MarginM)` rather than by `obstacleMarginM`; `TangentCorners.corners(...)` gains an `offsetM`. The offset and the clearance margin read the same `zone300MarginM`.
- **Three corrections from the review:** clamp the offset — `offsetM / sinHalf` explodes for near-collinear corners, so cap the offset distance and drop the corner when it exceeds the cap; use **per-set selection radii** — a land bend snaps within ~50 m and a band bend within its own reach, never one radius for both; and the band margin reads `route.avoid.zone300MarginM` (today parsed but unused), not `obstacleMarginM`.
- **Price honesty** now holds because Change 2 keeps the pull's chord guard alive: an edge is accepted only when it clears the land and is no dearer than the cell path over the span it replaces.

## Change 4 — coarse-to-fine precision, not a corner graph

**Why.** The shipped result bends at leftover kinks of a 50 m grid path — the grid is too coarse at the coast, so the pull turns grid kinks into false corners and identical routes bend differently. The exact corner graph was measured at 7.3 s and is dropped; the cheap fix is a **finer grid where it matters**.

**The mechanism.**

1. **Coarse pass, unchanged.** The 50 m grid A\* fixes the homotopy (which side of each obstacle, the corridor).
2. **A fine band that pins the homotopy.** A finer grid (25 m, tunable) subdivides **only the coarse path's cells** (plus one ring), and each fine cell inherits its coarse cell's passability; the second A\* walks that subdivided corridor. Because a fine cell is passable only where its coarse parent was, the fine pass cannot route around the other side of any obstacle — the coarse pass fixes the side by construction.
3. **Pull, then a light snap.** The fine path is pulled taut; the existing corner snap becomes a small cleanup, no longer the thing that invents the corners.
4. **Widen on no-path, not on edge-touch.** The fine band widens once only when the fine search finds **no path** (unreachable start or aim inside the band) — the corridor's own exhaustion policy — because a path cut off by a narrow band never touches an edge, and a path that does touch may be optimal and unwidenable.
5. **Determinism is per-route and per-process**, not per-corner: the same start and aim give the same line in one process; the cross-aim claim is dropped, since every fast variant derives its order from the route — and `TangentCorners.corners()` inherits ring order from the index, so this is scoped to a single build, not across launches.

**Performance, priced.**

- Coarse A\*: ~33k cells, ~5–15 ms (unchanged).
- Fine band: ~8 km path × ~1 km band ≈ 8 km²; at 25 m cells ≈ 13 000 cells, at 12.5 m ≈ 51 000 — a fine A\* and its near-coast raster of **~15–80 ms extra**. The figure assumes the fine grid covers **only the band** (outside-band cells are masked land, never a full-box rasterize, which would ~4× the cells). The widen and the corridor retry each add another fine pass, so the gate also carries a **longest-route** wall-time reading, not just the acceptance pair.
- Pull + snap: unchanged, a few ms.
- **A wall-time reading on the acceptance pair** is the gate — a measurement, not an assertion — and the band width and fine cell size are its two levers.

## Tests

- The concave-zone regression: a bay-shaped band chording its mouth with the zone on, diving in with it off.
- The fine-band regressions: the fine path stays within the band and reuses the coarse side; the pull emits no grid-kink corner — every emitted corner has a deflection ≥ a stated threshold, a collinearity merge dropping the rest; and the same start+aim reproduces the same line.
- A band-dearer chord is refused, and a wall-time guard covers the fine pass.

## Review findings (2026-09-25) — folded in

Two independent reviews returned **revise**. The first: the world's single both-layer entry, the sweep-side band price and its double count, the 100 m snap radius and the price-blind snap, and the naive corner graph's 7.3 s. The second: the window can cut a needed long chord; the cross-aim determinism claim is unsatisfiable; the band price's home was unchosen; the ~80 ms was an early-rejection bound; `prepare()` must be gate-aware too; `addTangent` explodes at `offsetM/sinHalf`; and `zone300MarginM` is parsed but unused. All are folded into the changes above. The stale "the gate is always on" sentence in `maro.properties` and the `AppConfig` KDoc were corrected with Change 1.

## Open decision

The fine band's width and its cell size are starting values, set by the harness measurement on the acceptance pair. The user's call remains whether Change 4 ships as the fine band or the snap is corrected in place instead.
