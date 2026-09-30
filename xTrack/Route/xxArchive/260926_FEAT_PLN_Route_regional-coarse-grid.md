<!-- scope: feature -->
# Route — the regional coarse grid, with the fine pass as its refinement (2026-09-26, discussion)

**Status:** in design — a discussion opened by the user's own requirement (*I need to trace routes in my area*), which the corridor's reach contradicts. Nothing built. The sibling plan [`260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md`](260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md) keeps the defect register and the fix design; this file carries the **architecture** the register's F5 and D8 point at.

## The requirement, and what stands in its way

- **The requirement.** A route in the user's own area, anywhere in the app's water — not only inside a halo drawn around the two points.
- **What stands in the way, measured, not argued.** The corridor is the two points' bounding box grown by `route.avoid.corridor.reachM` (1852 m), doubled once (3704 m) when the first search fails. The crossing ask's grown box measured `43.53737..43.60512` by `7.08383..7.17774` — about 7.5 km × 7.6 km, **22 952 cells** at 50 m — and the whole ask took **3.3 s** on the Pixel 7.
- **And the cost scales with area, so the reach is the only knob the current shape has.** The measured rasterisation rate across the last device run is **≈ 30 µs per 50 m cell** (6 006 cells in 163 ms, 12 319 in 380 ms, 22 952 in 839 ms).

| Whole-region grid | cells over ~1 200 km² of water | rasterise at 30 µs/cell |
|---|---|---|
| 50 m (today's cell) | ≈ 480 000 | **≈ 14 s** |
| 100 m | ≈ 120 000 | ≈ 3.6 s |
| 200 m | ≈ 30 000 | ≈ 0.9 s |
| 300 m | ≈ 13 300 | ≈ 0.4 s |

- **So the user's proposal is the one the arithmetic supports:** a **coarse whole-area grid** for coverage, and **the fine pass as the refinement** that buys the fidelity back where the line actually runs.

## The shape it takes

1. **A coarse grid over the app's water, kept rather than rebuilt per ask.** One rasterisation of the region at a cell size between 200 m and 300 m, held for the session and rebuilt only when a layer's own generation moves — the shape the removed tracer already shipped (a kept terrain behind an identity-and-generation licence), so a layer that lands or refreshes invalidates it and nothing else does.
2. **Per ask, one A\* over that grid** — tens of thousands of cells, tens of milliseconds — which is what removes the reach, the growth, and the box-dependence of every verdict: the whole area *is* searched, so "no path" and "forced crossing" become statements about the app's water rather than about a halo (the register's F1, F2 and F5 dissolve in one move).
3. **The fine pass becomes the substantive half, not an option** (the register's D8): the coarse answer is refined at the working cell size **along its own corridor** — the swath the plan dropped — with a local re-solve wherever the fine grid disagrees with the coarse one. Today's fine pass is "a pull and a snap plus a local A\* at a crossing"; this makes it the place the route is really computed.
4. **The refinement reuses what exists:** the current corridor machinery (harvest, rasterise, A\*, pull, snap) is exactly what a swath refinement needs, so the coarse pass *adds* a stage rather than replacing one.

## The principles that keep it honest

- **The coarse pass is conservative, the fine pass is exact, and the fine pass may only loosen.** A coarse cell must never be called water on evidence the fine grid would refuse: its margin grows by half a cell (`marginM + cellM / 2`), and its depth test is taken where a boat would really be, not at one centre. Then a coarse line can be *wrong in the pessimistic direction only* — it may refuse a passage a finer grid would take, never invent water that is not there — and the fine pass is free to add.
- **A refusal at the coarse size is not a refusal at all**, and must be retried before it is shown: severing a tens-of-metres passage is precisely what a 200 m cell does, so where the coarse search finds nothing the refinement (or today's corridor machinery as a second attempt) runs before any sentence.
- **The zones stay live.** The grid stores each zone's limit, so a zone change (an exclusion, a new fetch) must either re-rasterise the coarse grid or be applied lazily per cell — to decide, not to discover in the field.
- **The cost budget is stated and measured, not hoped:** the coarse build once (≈ 0.4–0.9 s), the A\* per ask (≈ tens of ms), the swath and the pull per ask (≈ 0.5–1.2 s by today's measurements), against the plan's own 500 ms wall — which this architecture **exceeds, deliberately**, and the wall itself is re-argued with these numbers rather than kept as a number nobody meets.

## The objections, named

- **A line over land is worse than a refusal**, and a coarse grid is where that risk lives: hence conservatism, and hence the fine pass being mandatory rather than optional wherever the coarse answer runs.
- **A kept grid is state**, and the removed tracer's own lesson was that state buys speed and costs reproducibility (its warm and cold lines differed). The coarse grid is kept for *connectivity*, and the fine pass recomputes the line every time, so the divergence the tracer suffered is confined to the part the user never sees.
- **This is a bigger change than the register's fixes**, and it makes D8 (the swath) a prerequisite rather than an option. Its own hop order is owed before it is built.
- **What it does not fix:** the pull's standoff keeping a staircase (the register's F4, whose tally reads `standoff=486`), the crossing policy (its F9), the offers' missing row (D7).

## Open questions for the user

1. **The coarse cell size:** 200 m (≈ 0.9 s to build, more detail) or 300 m (≈ 0.4 s, coarser still) — or a size derived from the region's own area against a stated build budget.
2. **When the coarse grid is built:** on the first ask of a session (paying it inside a response the user is already waiting on), or when the route mode arms (paying it before the ask, behind the mode's own preparation).
3. **Whether the refinement replaces the corridor or backs it:** the coarse pass first with the corridor machinery as the fallback for a coarse refusal, or the corridor first (today's shape) with the coarse grid as the wider retry.

## What a second refinement pass costs (asked 2026-09-26)

- **The price of a pass is the pull, not the raster.** Measured on the crossing ask: `STAGE PULL` → `STAGE SNAP` = **443 ms** and `SNAP` → the next boundary = **382 ms**, while a 1 000-cell swath rasterises in **≈ 30–60 ms** and its A\* in **≈ 10–20 ms**. So **each extra refinement pass costs ≈ 0.8–1.0 s**, and almost all of it is the pull and the snap over the line.
- **And the swath must only cover *one* cell of uncertainty**, which is what makes a cascade cheap in area: the medium pass's swath covers the coarse cell (± 200 m → 400 m wide), the fine pass's covers the medium cell (± 50 m → ~120 m wide). For a 10 km line:

| pass | cell | swath | cells | raster | A\* | pull + snap | ≈ total |
|---|---|---|---|---|---|---|---|
| coarse, kept once per session | 200 m | whole region | ≈ 30 000 | ≈ 0.9 s | tens of ms | — | ≈ 0.9 s |
| medium, along the coarse line | 50 m | 400 m | ≈ 2 000 | ≈ 60 ms | ≈ 10 ms | ≈ 0.8 s | ≈ 0.9 s |
| fine, along the medium line | 20 m | 120 m | ≈ 3 000 | ≈ 90 ms | ≈ 20 ms | ≈ 0.8 s | ≈ 0.9 s |
| *one jump, for comparison* | 20 m | 400 m | ≈ 10 000 | ≈ 300 ms | ≈ 30 ms | ≈ 0.8 s | ≈ 1.1 s |
| *today's single fine pass* | 20 m | the coarse line's own box | — | — | — | ≈ 0.5 s | ≈ 0.5 s |

- **What that says, plainly:** two refinement passes cost **≈ 1.8 s per ask** against **≈ 1.1 s** for one jump from coarse to fine, because the second pass pays a second pull and snap. The cascade's *rasters* are the cheaper half of the difference and its extra pull is the dearer one.
- **What the extra pass buys, and it is not speed:** each step's swath covers exactly one cell of the previous grid's uncertainty, so the error is **bounded at every step** instead of assumed across a 200 m jump; the medium line is water-tested at 50 m before the fine pass refines it, so a passage the coarse grid severed is found *before* the fine grid is spent on it; and the local re-solves (the crossing) can fire at the cheaper level first, leaving the 20 m grid for what only it can see.
- **Where the money is, if the cascade is wanted:** the pull's own cost — 0.4 s a pass, and the register's F4 is already going to rewrite it (`standoff=486` refusals a pass); a pull brought near 0.15 s makes the extra pass cost ≈ 0.5 s and the cascade ≈ 1.3 s, which is the same order as today's single grown ask's **3.3 s**.
- **Either way the wall is re-argued:** today's grown ask already runs 3.3 s against the plan's 500 ms, so the number to carry is the cascade's own 1.8 s plus the kept coarse build, stated as a budget rather than discovered on the phone.

## Precision against speed (asked 2026-09-26)

**The two currencies, and they are not the same one.** Cell size buys **placement** — where the line stands relative to the water, the fence and a zone's ring — and it buys almost **nothing for access**: a passage is passable only where a cell centre stands clear of *both* shores by the margin, so a channel of width `w` is routable only when `w ≳ 2 · marginM + cellM` ≈ **100 m + cellM**. Shrinking 50 m to 10 m therefore moves the bar from ~150 m to ~110 m, and the tens-of-metres passages this coast is made of stay closed whatever the cell — the **fence** decides, and only its waiver (F7's carve, F9's option (d)) opens them.

| cell | a water strip it can pass | placement error | region ~1 200 km² | one 400 m swath over 10 km | per km of line |
|---|---|---|---|---|---|
| 300 m | ≳ 400 m | ± 150 m | 13 300 cells ≈ **0.4 s** | 130 cells ≈ 4 ms | 0.4 ms |
| 200 m | ≳ 300 m | ± 100 m | 30 000 ≈ **0.9 s** | 300 ≈ 9 ms | 0.9 ms |
| 100 m | ≳ 200 m | ± 50 m | 120 000 ≈ **3.6 s** | 1 100 ≈ 33 ms | 3 ms |
| 50 m | ≳ 150 m | ± 25 m | 480 000 ≈ **14 s** | 4 400 ≈ 130 ms | 13 ms |
| 20 m | ≳ 120 m | ± 10 m | 3 000 000 ≈ **90 s** | 27 500 ≈ 830 ms | 83 ms |
| 10 m | ≳ 110 m | ± 5 m | 12 000 000 ≈ **6 min** | 110 000 ≈ 3.3 s | 330 ms |

- **Read down the cost columns and the shape is forced:** an area-wide grid is only payable at 200–300 m; everything finer has to be spent on a swath, and a swath's cost falls with the square of the cell — which is why the refinement is affordable only because it covers a *band*, never a box.
- **Read down the precision columns and the ceiling is visible:** going from 50 m to 10 m buys ±15 m of placement and 40 m of passability, and costs **25× the area** — so the fine end of the scale is a *dressing* of the line's position, not a key to water.
- **The pull is the other half of every pass, and it does not scale with the cell at all:** ≈ **0.4 s per pass** on the logged line, i.e. ≈ 40 ms per kilometre of line — **ten times a 50 m swath's rasterisation** on the same kilometre. A pass's cost is therefore `0.4 s + (swath area) × 30 µs`, and it is the line's own length and jaggedness that dominate.
- **What the numbers say to choose:** the kept **200 m** region grid (≈ 0.9 s once) with **one** refinement at 50 m along its line (≈ 0.05 s of raster + a 0.4 s pull) is the cheapest shape that answers the requirement; adding the 20 m step buys ±10 m of placement instead of ±25 and costs one more pull (≈ 0.8 s) — worth it for a berth approach, indifferent on open water.
- **And the honest caveat:** the per-cell rate was measured at 50 m on one phone, and a coarser cell spends less on the margin sweep but the same on the per-cell field read, so the table is an estimate with a ±30 % spread rather than a promise.

## The compromise proposed (2026-09-26)

- **The division of labour that answers the user's own point:** the **grid picks the order** — which side of an island, which passage exists — and the **pull picks the position**, because `legClear` tests the world's own geometry (`distanceToCoastM` per sample, the rings' own distances) rather than the grid. So a **conservative** coarse line is *corrected* by the pull to the exact clearance wherever the water allows, and precision of the grid matters far less than it looks. The pulling optimisation the user is planning (the register's F4) is therefore **the enabler** of this compromise, not a separate nicety.
- **What is proposed, in one shape:**
  1. **A conservative region grid at 250 m, kept for the session** — ≈ **0.55 s** to build once, its margin grown by half a cell and its depth read where a boat would really be, so it can only refuse water, never invent it.
  2. **One 50 m refinement along the coarse line** — a swath `2 × (250 + 50)` = 600 m wide, ≈ **7 ms per kilometre** of line (≈ 70 ms for 10 km) — plus a **pull and snap** (≈ 0.4 s, the pass's real price).
  3. **Escalation by evidence, never by habit:** the 20 m grid is built only at a berth approach or where the tally says a chord was refused (`PULLREF`), and the crossing's local re-solve keeps its own box. No 20 m pass along the whole line.
  4. **A coarse failure is never shown.** Severing a narrow passage is what a coarse cell does by construction, so a coarse "no path" **triggers the refinement** (or today's corridor machinery as the second attempt) before any sentence reaches the panel — which is also what makes the register's F1 and F2 honest.
- **The budget it lands on:** the first ask of a session ≈ **1.05 s** (0.55 build + 0.5 work), every ask after it ≈ **0.5 s** — against today's grown ask's **3.3 s**, six times faster *and* with the whole area covered, the reach gone and the forced verdict provable.
- **What coarseness still costs, named:** access in strips under about **300 m** (`2 · marginM + cellM` at 250 m), and the **order** near broken ground — a line that goes *around* two islands the coarse grid merged rather than between them. Both are pessimistic, both are recoverable by the refinement, and neither is a wrong line.
- **The objection to the compromise:** a coarse grid can refuse water the truth allows, so the refinement must be *mandatory* ahead of any refusal and the coarse rules must stay conservative — otherwise the user meets "no route" in water a boat can use, which is the complaint that opened this file.
- **The measurement that settles the cell size:** build the region at 200 m, 250 m and 300 m once and read `GRID`'s own timing line per build; the table's ±30 % spread is wider than the difference between 200 and 300, so the number should be read off the phone rather than chosen here.

## What this dissolves from the sibling register

- **F5 retires** — the reach, the growth and the corridor bound go with the corridor itself, and `route.avoid.corridor.reachM` leaves with them.
- **F1 and F2 become true by construction** — the whole area is searched, so "no path in the area searched" is the only refusal left and "forced crossing" is provable against the region's own water; their wording changes land with step 5 below rather than as separate fixes.
- **D8 stops being an option**: the swath *is* this file's step 4.
- **D11, F3 and F4 stay, unchanged** — and they are steps 1 and 2, because the pull is this architecture's enabler and the two small fixes keep today's path honest while the region grid is built.

## The build order

1. **The pull first (the register's F4)** — the staged standoff before the cell step, judged on `PULLREF standoff=` collapsing and `path` → `final` tightening. It is the enabler: every refinement's cost is a pull, and every refinement's quality is the pull's clearance read.
2. **D11's disc and F3 together** — the end exempted by a disc of `marginM` as well as by the carve, and the crossing's local re-solve reaching the line's ends. Both are small, both keep the shipped path honest, and both are prerequisites of step 4's escalation (F3 *is* the escalation's mechanism).
3. **The kept region grid** — a session resource over the app's water at `route.avoid.region.coarseM`, built on a background dispatcher as part of the engine's own `prepare()` (so the mode arms only when it is in), behind a licence of the three layers' generations and the region's bounds; its stored costs are the base and the band's price at one pace, **re-scaled by a single factor** when the pace moves (a multiply over some tens of thousands of cells); the zone limits stay in the cells and a zone change invalidates. The instrument gains a `REGION` line: cell, cells, metres of water, milliseconds.
4. **The 50 m swath refinement** — the coarse polyline's own bounding box inflated by `2 × coarseM`, rasterised at `route.avoid.grid.cellM` (the existing key, one home), re-searched and pulled, with the local box at 20 m escalating wherever the refined line still fails a rule: a chord the exact geometry refused (the `PULLREF` tally) or a crossing the probe cannot avoid. A `REFINE` line per pass carries the cell, the cells, the waypoints and the milliseconds.
5. **The verdicts, area-wide** — the probe runs over the region grid, so "forced" means no way round in the app's own water; and the refusal's sentence becomes the register's one, true statement.
6. **The cell size, read not chosen** — build the region once at 200 m, 250 m and 300 m and read the three `REGION` lines; the wall is then re-argued from the measured first-ask and steady-state figures rather than kept at 500 ms.

## The interfaces and keys, so the hop is a build and not a design

- **One new key:** `route.avoid.region.coarseM` (default 250, clamped 100–500). Everything else is derived: the swath's width is `2 × coarseM`, its cell is `route.avoid.grid.cellM`, the escalation's box is the failing stretch's own inflated by `2 × marginM`, and the region's bounds are the app's own water.
- **Once retired:** `route.avoid.corridor.reachM`, `route.avoid.fine.cellRatio`'s role as the *whole* fine cell (the 20 m box keeps it) and the growth's double attempt.
- **The resource:** a `RegionGrid` holding the grid, its cell size and its licence, owned by the engine beside its state, rebuilt when the licence fails — the shape the removed tracer shipped for its terrain, whose warm-and-cold divergence this design contains by recomputing the line on every ask.
- **The escalation is evidence-driven:** nothing builds a 20 m grid unless the refined line's own tally or the probe asks for it, so the common ask pays two grids (region + swath) and nothing else.
- **The tests the hop owes:** the licence rebuilds on a generation change and nothing else does; the pace re-scale leaves the fine answer unchanged; a passage the coarse grid severs is found by the refinement (a staged world); a refused chord escalates to the local 20 m box (a staged world); the probe's verdict is area-wide; and the two stages' agreement — the refined line no longer than the coarse one, never over land.
