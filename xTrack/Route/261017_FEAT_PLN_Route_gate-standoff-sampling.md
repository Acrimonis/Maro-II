# Plan — the gate standoff is sampled, not measured (v2, P0–P2 backed out)

**Status:** in design · **Feature:** Route · **Created:** 2026-10-17 · **Revised:** 2026-10-17

## What happened, in one line

The first attempt (P0 trace, P1 cell-disc guard, P2 audit-and-repair) is **backed out**; the second device run proved it detects the breach but cannot repair it, bends the other rungs, and makes them jagged. This revision keeps only the diagnosis and re-plans the fix around one principle: **the wall and the standoff must be search-side constraints, not pull-stage refusals.**

## Evidence, both runs

- Pre-fix run [`route_acq.log`](../../route_acq.log:151): λ=1 cuts La Grenille at `minDepth=2.2m gateEdge=5.0m` at 43.57112,7.14007; λ=3 and λ=5 clear at 30 m and 60 m body standoff.
- Fixed run [`route_acq2.log`](../../route_acq2.log:1975): λ=1 unchanged — `minDepth=2.2m gateEdge=5.0m`, now flagged `breach=WALL@43.57115,7.13994 retries=2`; the repair ran twice in the right box and ended `clear=false`.
- Fixed run [`route_acq2.log`](../../route_acq2.log:1985): λ=3 and λ=5 gained a **new** `STANDOFF` refusal at 43.54483,7.13716, were bent around it and still flagged `clear=false`.
- Fixed run [`route_acq2.log`](../../route_acq2.log:686): cost rose ~2.5× — λ=1 `coarseMs` 530→1379, `fineMs` 668→1530.

## The diagnosis, from the two runs

- **The WALL rock is raster-blind.** The raster stores one depth per cell centre, so a sub-cell pocket whose centre reads deep is not a wall cell; the re-solve re-searches the same blind grid, moves 150→156 points, and still crosses it.
- **The disc refuses what the search never sees.** The disc guard runs in the pull and the corner bow, but the A* paints land only at `gateDistanceM ≤ marginM` (cell centre), while the disc refuses at `marginM + cellM` — so the search keeps choosing the breaching path and the repair re-searches the same looser mask, returning the same path.
- **The repair spliced after the corner pass.** [`resolveBreaches`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:636) ran on `rounded.points` and its output fed `timeLineWithProfile` with no corner pass between — the spliced spans keep their re-solve's kinks, which is the "more corners, less smooth" the user saw.
- Therefore a pull-stage refusal plus a post-hoc repair cannot work. The fix must make the search see the rock and the standoff, and any repair must run before the corner pass — or not exist at all because the search got it right the first time.

## The corrected fix

- **P1 — make the wall see the rock (was P3, now first).** Rasterise the gate wall from the **minimum of the cell's centre and four corners**, and bump [`FineTileCodec.FORMAT_VERSION`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCodec.kt:32) so warm tiles cannot serve the old wall. This turns the La Grenille pocket into a wall cell the search, the BFS and the berth all see.
- **P2 — make the standoff a search constraint, not a pull refusal.** Dilate the search's land mask to the same reach the disc enforces — paint land at `gateDistanceM ≤ marginM + cellM` — or add the disc as a hard source the A* prices, so the search never produces a chord the pull must refuse. Keep the disc check in `legClearCause` only as an assertion the constraint held, not as the enforcement.
- **P3 — keep the audit as a trace-only backstop.** [`RouteDepthReading.minDepthReading`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteDepthReading.kt:38) and the `GATE` worst-five line stay, because they named the breach and cost nothing on a plain solve. No repair loop: with P1 and P2 the search should produce no breach, and a surviving `breach=` line is a bug report, not a fix attempt.
- **No post-hoc splice.** If a repair ever returns, it runs on the pre-corner line and the corner pass runs after it; that is out of scope for this pass and recorded only so the seam is never forgotten.

## Confirmation, offline first

- A fixture whose pocket contains **no cell centre** must, under P1, become a wall cell; under today's raster it does not — one assertion each.
- A fixture whose chord grazes a neighbour cell inside `marginM + cellM` must be refused by the **search** (no path), not by the pull — the A* path must already clear the disc.
- The La Grenille case, replayed: λ=1's line must keep `minDepth ≥ 3.0m` and `gateEdge ≥ marginM`, and λ=3/λ=5 must keep the 30 m / 60 m body standoff with no `breach=` and no new corners.

## Risks

- Min-over-corners widens the effective wall by up to a half-cell diagonal; measure the Cagnes–Cannes probe for lost legal lines before calling it settled.
- The search-side dilation (`marginM + cellM`) is stricter than today's `marginM` land and may over-refuse narrow channels; the probe is the gate.
- Cost: the corner minimum adds four depth reads per cell on the frame the rasterizer already walks; no new per-mark cost beyond today.

## Key files

- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassGrid.kt` — `rasterizeFrame`, `dilateGateGrid`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCostField.kt` — the standoff rule and the hard source
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt` — `legClearCause`, `clearanceStep`
- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteDepthReading.kt` — the trace-only reading
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCodec.kt` — `FORMAT_VERSION`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — the finalise seam and the `LINE` sink
