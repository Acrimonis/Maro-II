<!-- scope: feature -->
# Route — the coastal leg's clock, and a possible third engine

**Date:** 2026-10-07 · **Status:** in design, for discussion (point 4 of four) · **Order:** the user's word
of 2026-10-07 — *"the computation of the coastal leg is always way faster. Is this due to the coarseness of
the grid around the 300 m zone and the speed zones? Would it help to maybe precompute a fine grid around them
also? This would be a third algo 'finerilly' built out of/derived from 'evolutive'."*

## What is there today

- **Two grid plans, one pipeline.** [`UniformGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:58)
  answers one tile at `route.avoid.grid.cellM` with its fine cell as a **ratio** (100 m and ~33 m as shipped);
  [`EvolutiveGridPlan`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteGridPlan.kt:77) answers
  **two layers on one lattice** — 100 m and 20 m — and the fine layer is cut as **windows over the coastal
  ribbon** by the grid builder.
- **The fine pass already refines locally.** [`RouteFinePass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteFinePass.kt:66)
  re-solves a restrictive zone's crossing on its own fine grid along the settled line, so a zone inside the
  corridor is not entirely at the coarse cell's mercy.
- **The clock samples at the finest walked cell** — [`clockSampleM(cellM, fineCellM)`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RouteEta.kt:48)
  is half of `min(cell, fine)`, so `avoid` steps ~17 m and `evolutive` ~10 m, and each leg's speed is the limit
  in force at the water it crosses.
- **The walk has a ceiling.** `route.walk.maxCells` = 600 000 refuses a walk whose layers cannot fit, and the
  window mask is what keeps a long coastal corridor inside it.

## The two readings to separate first

"The coastal leg is always way faster" has two different meanings and two different fixes; the plan must not
pick one by assumption:

1. **The clock is optimistic** — the reported time for a coastal rung is shorter than the same line driven for
   real, because the slow water it crosses is under-resolved: a 300 m band or a zone entered and left between
   two samples, or a leg whose limit is read at one endpoint. The fix is about **sampling and limits**.
2. **The line is optimistic** — the drawn coastal line genuinely cuts through slow water that a finer grid
   would route around, so it is faster *because it is wrong*. The fix is about **geometry and the search**.

They are told apart by one reading: a rung's reported clock against the same polyline re-timed at the fine
cell, and the drawn line's own band/zone metres (`bandMetres`, `bandPricedMetres`, `slowMetres`, the *zone
share* the engine already traces) against a finer walk's.

## What the change would be

If the answer is (2), the user's proposal is already close to a mechanism the tree has: `EvolutiveGridPlan`
cuts its fine layer from a **mask**, today the coastal ribbon. **Widening that mask to the 300 m band's and
every priced zone's own outline** would precompute fine water exactly where the slow water is — a change to
the *plan*, not a new engine.

A third engine is the other option, and its cost should be stated plainly: `route.engine.id` is a
user-visible dropdown row, a second algorithm to keep green on every later change, and a second behaviour to
explain in the doc — while `evolutive` is *already* a plan-level variant of `avoid`. So the honest order is:
widen the mask inside `evolutive` first, and split a third engine only if the two behaviours must coexist and
the user wants to choose between them.

## Feasibility and risks

- **High feasibility for the mask widening** — the mechanism, the lattice family, the window merge and the
  cell budget all exist; the work is where the mask comes from and how it grows.
- **The cell ceiling is the real brake.** Fine water around every zone can be a large fraction of a long
  corridor; `route.walk.maxCells` refusal must be handled by design (windows merged, cell budget counted)
  rather than discovered on the water.
- **Cost per solve.** The fine layer is the expensive half; a mask over every zone multiplies the fine cells on
  a coastal route in a zone-dense area (Lérins, the Antibes arrêtés).
- **The seam already works**, so fine water beside the interior is walkable — this is not new machinery.
- **Precision is not the same as truth**: a finer grid prices the same *limit* more accurately; if the clock's
  optimism is a limit-read problem (reading 1), no amount of extra cells fixes it.

## Phases (sketch)

1. **The reading** (R97 — the user's own, and not a precondition for code): one coastal rung's reported clock
   against its own polyline re-timed at the fine cell, beside the zone/band metres the trace already prints,
   for both `avoid` and `evolutive`. This settles reading (1) versus (2).
2. **Only then** the design: the mask's source (band outline, zone rings, or both), its growth and its
   interaction with the window merge and the cell ceiling.
3. **A fixture** that pins the coastal clock against a known slow stretch — the case the user reports.
4. **The record**: the engines' reference, the plan seam's doc, and the feature's state line; the third engine
   only if step 2 concludes the behaviours must coexist.

## Open questions

- Which reading is the real one? (Step 1 answers it; everything else waits on that.)
- Should a widened mask be **always on** for `evolutive`, or opt-in as a third engine? The user asked for a
  third algorithm; the tree's cheapest route is one plan, and the difference is a dropdown row.
- If a third engine is wanted, what does `route.engine.*` say about it — a new id, a label, and which plan it
  inherits — and does it ship as the default?
- What is the acceptable **cell ceiling** for a coastal route in a zone-dense area before a walk is refused,
  and should the mask degrade gracefully (drop the least-priced zones' water) rather than refuse?
