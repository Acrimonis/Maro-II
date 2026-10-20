<!-- scope: feature -->
# Plan — smooth driving: no corner left standing where a curve fits

**Status:** in design (v2, after review 1 — one decision is the user's, see *Decision*) · **Feature:** Route · **Created:** 2026-10-20

## The order

> Evaluate and plan a way to smoothen the route so there are no sharp angles (I want smooth driving with as little angles as possible).

## Evidence — where the angles come from

The 2026-10-19 device session (`evolutive`, 5 kn, before both the measured gate and the medial nudge) drew 25 lines over a few pairs. Their `CORNER` verdicts name **27 distinct bends**:

| Verdict | Bends | Turns |
|---|---|---|
| `BOW_DEPTH` — left standing, the bow would near the shallows | 12 | 12°–59°, most 25°–48° |
| `BOW_COAST` — left standing, the bow would near the coast | 2 | 18°, 43° |
| `ROUNDED` | 5 | 12°–60° |
| `SHALLOW` — under 10°, kept as a point | 8 | 0°, and three at 5°–7° |

**Half the bends are drawn as angles, and they are the sharp ones.** The sample is small and predates the two changes most likely to move it, so the first step re-measures it (S1).

**Cause 1 — the floor is measured at the corner.**
- A bow may not come nearer a wall than the corner it replaces: the coast at `min(margin, the corner's own)`, the gate at `min(preferred, the corner's own)`.
- A taut line bends around the wall, so the wall is on the turn's inside, the side a bow cuts toward, and every inside cut moves nearer it.
- At the 5 kn pace the shrink stops at the turn floor (5 kn, radius ~10 m, apex ~1 m deep), so each such corner is asked one bow, and the floor refuses it.
- Yet the legs beside the corner usually come **nearer** that wall than the corner does: a wall point on the corner's inside bisector stands `d·cos(δ/2)` off the legs against `d` off the corner. The rule is stricter than its own stated intent — *a curve can never make the line worse than it already is* — which reads off the stretch the curve replaces, not the one point.

**Cause 2 — a staircase pinned by the wall.**
- Most standing bends have legs at the 20 m fine grid's own lengths (20, 28.3, 44.7, 56.6, 84.8, 113.1 m) and its own turns (45°, 26.6°, 18.4°).
- The sweep keeps them because the straight chord would cross the wall: the session logs 186 `SWEEP keep why=LAND`.
- These are grid steps along a curved wall, not bends the water asks for.

## Options

- **A — the stretch floor (recommended; the user's call).** A bow may come as near a wall as **the stretch of line it replaces** already does — the two legs within its own tangent — never nearer, and never under the gate's wall or the coast's floor where the gate does not waive itself.
  - It costs two segment reads per bow size tried.
  - Bows stay tangent to their legs, so it adds no join, no new angle and no new verdict.
  - The line's closest approach to every wall can never drop.
  - *Objection:* it rewrites the corner pass's own wording — *not nearer than the corner it replaces* — which the user's Q2 confirmed for the gate. Corners that are shrunk today may draw larger bows, as near as their legs. At 5 kn a corner whose legs sit on the wall itself still cannot round.
- **B — the apex-held bow (v1's choice; rejected by review).**
  - The idea is to shift the refused bow outward until its apex sits on the corner.
  - With its apex on the corner, the bow's tangent there is perpendicular to the bisector, so it moves toward any wall off the bisector from its first step.
  - An on-bisector wall point is approached too whenever the bow's radius is under the corner's distance — ~10 m against 10–20 m here.
  - Measured at the corner's floors, almost every held bow would be refused. And each held bow adds up to four unrounded joins, two steering against the turn.
- **C — a global curvature smoother** (an elastic band, or a spline under the walls). It needs its own collision proofs and an iterative solve on the device, and the 2026-10-17 Chaikin pass already showed that a smoother adding points makes the corner pass see more bends (`FEAT_DOC_Route_decisions.md` §8). Rejected.

## Design

- **S1 — the angles' own reading (first, independent of the decision).**
  - The `CORNERS` line gains `sharpest=`, the largest turn left standing in degrees.
  - The `LINE` line gains `maxTurn=`, the largest heading change between two consecutive drawn straights.
  - The order's own measure, on every route. The device pass after the measured gate then re-baselines the counts above.
- **F1 — the stretch floor (on the user's word).**
  - In `attemptCorner`, the gate's and the coast's floors read off the **replaced stretch** of the bow drawn at each size `v`:
    - the gate: `min(preferred, segmentEdgeM(tangent-in → corner), segmentEdgeM(corner → tangent-out))`;
    - the coast: the same over sampled points;
    - in both cases at least the wall where the gate does not waive itself, as `Walls.gateFloorAt` already does.
  - `wallsAround` gains the stretch in place of the one corner; the joining chords keep their fixed-end reading.
- **F2 — a run along a curved wall is one turn (after S1 shows runs).**
  - `compoundLine` merges a close **pair** of same-way bends into the crossing of its outer legs. A staircase along a curved wall is a **run** of such bends, often interleaved with flat kinks.
  - F2 merges a run whose span is under the pace's tangent for its summed turn, the crossing's extensions measured as item 2 measures the pair's.
  - The merged bow then reads its floor off the run's whole stretch (F1).
  - In a centred passage the crossing nears the other bank and is refused, so F2 does not round a passage's staircase. That stays a grid limit, recorded with the measured gate's open points.
- **Not planned:** the 5°–7° kinks (not sharp; rounding them would make the room count them as bends and shrink their neighbours).

## Non-regression and performance

- **F1 relaxes a floor and nothing else.**
  - A corner rounded today can only draw an equal or larger bow (the floor never rises).
  - A refused corner may now round.
  - Every drawn point still keeps the walls the line it replaces kept.
  - The clock reads the bow's ceiling where the turn floor stood. At the 5 kn pace both are 5 kn; at a higher pace a corner rounded at the floor speed or above can only shorten the ETA.
  - Cost: two segment reads per bisection step on bows near a wall.
- **F2 changes only lines with same-way runs under the pace tangent;** its cost is that of today's pair merge, per run.
- **Tests to re-read under F1:** every case asserting `BOW_DEPTH` or `BOW_COAST`, in `RouteCornerPassTest`, `GateGuardsTest` (the near V at `:84-95`, whose legs stand nearer than its corner) and `GateCornerTest`. Each is re-read, never re-pinned blindly.

## Tests

- **S1:** a line with one standing 40° corner prints `sharpest=40.0`, and `maxTurn=` reads the drawn polyline.
- **F1:**
  - A corner whose legs pass a rock nearer than the corner itself rounds, and no drawn point comes nearer the rock than the legs did.
  - A corner whose legs touch the wall is still refused.
  - A corner rounded today draws the same or a larger bow.
- **F2:** a run of three 20° bends around a convex shelf, under the pace tangent, draws one bow at the run's stretch floor; the same run in a centred passage is left as today.

## Decision — the user's

- **Should a bow be allowed to come as near a wall as the stretch of line it replaces already does, rather than no nearer than its corner?** A *yes* authorises F1. A *no* leaves Cause 1 standing, and only S1 and F2 remain.

## Docs to update on implementation

- The pass's header and the `CornerOutcome` KDoc in `RouteCornerPass.kt`.
- `FEAT_DOC_Route_engines.md` step 10.
- `FEAT_DSC_Route.md`'s corner-pass bullet: *the coast test is relative to the corner it replaces*.

## Key files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteCornerPass.kt` — `attemptCorner`, `wallsAround`, `compoundLine`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — `traceCornerVerdicts`, the `LINE` line
