# 261002_FEAT_PLN_Route_aversion-and-slow-water-model

Status: shipped — Phase F: the three-route ladder lives end to end — three fixed-λ
rungs over one shared grid, the Driving-preference cursor picking the initial rung,
collapsed rungs deduped with the nearest survivor selected. Final arbitration
2026-10-03 is the design of record; Phases 0/A/B/D shipped on the superseded
three-dial model; D2/D3/D4 and Phase C superseded. Build green, full unit suite
green; pointer in `## Implemented` (`FEAT_DSC_Route.md`).

## Purpose

Re-model the route's slow-water settings around a vocabulary that separates physics
from preference: the **pace** is the boat and the **aversion** is how hard the search
avoids slow zones. Today those ideas are tangled in one price formula and one hidden
constant; the Final arbitration (2026-10-03) consolidated the model to one preference
cursor over a fixed ladder of routes.

## The user's model, as stated

- Aversion means *avoid slow moving areas* — 0 is "whatever", the maximum is "spend as
  little time as possible in the zone, show me the shortest way in and out".
- Slow-water cost means *how much time must I save for the time spent in a slow zone to
  be worth it*.
- The free-water pace and the cost must be separated, and λ calibration moves to the
  slow-water budget, with `softCostAversion` the dial that carries the aversion.

## How the current implementation actually reads

- The price of one slow cell is its open-water base plus the **time excess** scaled by
  λ: `cellM/v(pace) + λ × cellM × (1/v(limit) − 1/v(pace))`
  ([`RouteCostField.kt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:89)).
- The same pace therefore feeds both the clock and the price, and it is the reference
  that decides which zones are free — so a change to the pace dial silently retunes
  how hard every zone is avoided (the coupling the user wants broken).
- λ is seeded from `route.avoid.speedZone.softCostAversion` — a properties-only key,
  clamped 0..5, code fallback 1.0, shipped 4, no Settings row
  ([`AppConfig.kt`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:306)).
- λ is then **corrected once** by the budget loop — `λ₁ = λ₀ × zone share ÷ budget` —
  two passes being the cap, so the budget can only raise λ, never lower it, and a share
  still out is reported rather than chased
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:507)).
- The budget measures **zone time only** — the 300 m band's slow time and the ramps are
  read apart and never drive it
  ([`RouteEta.kt`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:245)).
- The overrun is computed and then **dropped**: `budgetUnmetZoneShare` never reaches the
  panel because the published plan carries `forcedCrossingZoneNames` and no budget field,
  and the two strings exist but are read by nothing
  ([`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:122)).
- The "max aversion = shortest in/out" behaviour already exists as a separate path: a
  crossing is forbidden while a way around exists, and only a **forced** crossing is
  priced and taken — which is exactly "shortest chord through the zone" when no way
  around exists
  ([`RouteResult.kt`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:31)).

## The target model

**Superseded.** The three-dial target formerly described here — pace · aversion λ ·
user-facing budget — is retired by the Final arbitration at the end of this file
(2026-10-03). The design of record is one Driving-preference cursor over a fixed effort
ladder, with the budget demoted to an internal auto-pick criterion. The arbitration
section is the sole statement of the model.

## Decisions — survivors and superseded

The Final arbitration is authoritative on every conflict. Survivors:

- D1 — survives: the pace stays a user dial, the boat's cruising speed, the physics
  reference and the trip figure's fallback, relabelled "Cruising speed"; the observed
  pace already takes over for the displayed ETA, and the cost stays honest.
- D5 — survives: the properties key `route.avoid.speedZone.softCostAversion` stays the
  seed and one home, with no rename and no migration.
- D6 — survives: avoid entirely while a way around exists, shortest chord through the
  zone when a crossing is forced.
- D8 — survives: semantic decoupling — the pace stays the honest reference and may still
  move the price, so λ = 1 keeps meaning the fastest route; the settings wording must say
  the cruising speed can change the drawn route.
- D9 — survives, recast: one global preference; per-type aversion stays out of scope.

Superseded by the arbitration:

- D2 — superseded: the 0..5 log-curve slider. The ladder's rung scale is an open point.
- D3 — superseded: the budget's band floor; the demoted budget's counting rule folds into
  the auto-pick criterion.
- D4 — superseded: "reported, never refused"; the forced-crossing path already refuses
  nothing.

Resolved by the follow-up decisions (2026-10-03):

- D7 — resolved: the ladder uses fixed rungs (min, split, max), so there is no corrected
  λ to clamp; the maximum rung is the configured maximum aversion.
- D10 — rung count = 3: the ladder carries three routes.
- D11 — rung scale: the minimum and maximum aversion are the two end rungs, and the
  remaining rung splits the range evenly between them.
- D12 — auto-pick: the Driving-preference setting drives the default rung; the cursor's
  position is the selected rung.
- D13 — early select and early save (R88/R89) bind to the selected rung.
- D14 — degenerate ladder: where rungs sit within the collapse tolerance of each other —
  the symmetric maximum deviation ≤ `route.avoid.ladder.collapse.toleranceM` (default
  25 m) — the duplicates are removed and the survivor is marked as the collapse's own.
- D15 — dedup selection: when a collapse removes the rung the cursor points at, the
  nearest surviving rung is selected.

The arbitration supersedes D2/D3/D4 and obsoletes Phase C; the survivors and the
follow-up decisions above are the only decisions still standing.

## Phases

- Phase 0 — single-line baseline: empty `route.avoid.candidate.passes` so one settled
  line renders while the new model is tested; this also removes the `zone300` candidate
  re-rasterise, the dearest step. **Found a no-op on device 2026-10-03:** an empty value
  is parsed to `null` by [`parseCandidatePasses`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:115)
  and the loader only assigns on non-null, so the shipped pair survives and the candidates
  still run. Fixed 2026-10-03: `parseCandidatePasses` now answers an empty list for a
  blank value, so an empty key declares no candidates; build and config tests green.
  **Survives the arbitration.**
- Phase A — documentation, **re-scoped to the one-cursor vocabulary**: rewrite the
  property comments, the Settings labels and the descriptions to state the
  Driving-preference ladder, not the three-dial model; keep λ = 4 shipped so existing
  routes do not move on day one.
- Phase B — the seam, **shipped 2026-10-02 on the three-dial model**: the aversion
  provider through [`RouteEngineChoice`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:26),
  the Settings row, λ read at solve time; the factory arity change touches both call
  sites (MapScreen, MapRouteEffects) and keeps the dummy engine's no-arg build. Stays on
  the branch until the merge, then the Driving-preference cursor replaces it.
- Phase C — **superseded by the arbitration**: the one-shot-to-convergence calibration
  has no user budget to converge on; the ladder computes each rung at its own fixed
  aversion instead.
- Phase D — **shipped 2026-10-02, then superseded**: `budgetUnmetZoneShare` and the
  settled λ carried to the panel. With the budget demoted, the overrun becomes the
  auto-pick ranking input, not a surfaced dial.
- Phase E — tests, **re-scoped**: pin the ladder semantics — the three rung aversions
  (min, split, max), the cursor's rung selection, the auto-pick by the Driving-preference
  setting, and the duplicate removal on collapse — instead of the dial semantics
  (0 / 1 / top).
- Phase F — the ladder build (engine-side): **shipped 2026-10-03** — compute the three
  rungs (min, split, max) as acquisition pages over the one shared grid, map the
  Driving-preference cursor to the initial selected page, and drop collapsed-rung
  duplicates (D10–D15); the acquisition panel already ships the page walk and the
  selected-page actions (R90).

## Performance — the honest delta

- One solve at the 3704 m corridor was measured on device as ~1.16 s rasterise and ~3.4 s
  solve, 30 843 cells at the doubled box — the figures live in the reach key's own comment
  ([`maro.properties`](../../app/src/main/assets/maro.properties:235)).
- The λ loop re-solves over the grid built **once**: a further pass costs one multiply per
  expansion plus an A\* and a pull, never a re-rasterise
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:379)).
- Two passes is the loop's cap today, so N is 1 or 2
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1610)).
- Candidates cost extra by design: `speedZone` is one search over the existing grid, while
  `zone300` is one full solve with its own re-rasterise — the dearer of the two
  ([`maro.properties`](../../app/src/main/assets/maro.properties:310)).
- A budget-unmet, forced-crossing or no-path answer re-runs the whole solve at reach × 2 —
  the expensive escalation, because it re-rasterises
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:307)).
- Repair is ≤ 50 ms per point and the A\* checks cancellation every 256 expansions, so the
  acquisition stays incremental and a flung map never waits on a dead computation
  ([`RouteAvoidEngine.kt`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:110)).

### Per-acquisition cost model

`T ≈ repair (≤ 100 ms) + rasterise + N × A*-pass + [fine local A*] + probe + candidates + [growth]` —
the growth and the `zone300` candidate are the only steps that re-rasterise.

### Delta of the proposed changes

The pre-arbitration rows (Phase C, D3, D2/D7) are superseded and kept only as the record
of the earlier proposal.

| Change | Marginal cost | Notes |
|---|---|---|
| Phase 0 — single-line baseline | negative | drops one cheap search and one full re-rasterise solve per acquisition |
| Phase B — aversion dial | 0 | the λ seed just reads a new value |
| Phase C — iterate to the budget, cap 4 | ≤ +2 A\* passes, worst case | superseded — each pass is A\* + pull + timing on the shared grid, no rasterise |
| Phase C — iterate *before* growing | potentially negative | superseded — a λ pass is far cheaper than the growth's re-rasterise |
| Phase D — surface the overrun | 0 | superseded — a field carried, not computed |
| D3 — band in the budget | 0 | superseded — `slowShares` already reads band and ramp |
| D2, D7 — scale and clamp | 0 | superseded — dial arithmetic only |
| Ladder — 3 rungs | 3 × A\*-pass, one shared rasterise | to measure — the arbitration's cost driver |

### What must be measured before implementation

- The cost of one ladder rung — one A\* plus a pull on the shared grid — on device, at the
  shipped corridor, for three rungs (D10).
- The isolated cost of one A\* pass — the composite 3.4 s is not the pass cost.

### The gate

The rung count is decided at three (D10). No implementation of the ladder until the
per-rung cost is measured on the device; the rasterise is shared, so the rung count is a
latency decision, never a correctness one.

## Risks

- The ladder multiplies full solves by the rung count; the rasterise is shared, so the
  rung count is a latency decision, never a correctness one.
- Exposing λ changes a value users already have (shipped 4); the default must stay 4 or
  the change must be stated.
- The cursor never changes a route, only which rung is selected — a continuous drag moves
  nothing between rungs, so the control must be a discrete snap.

## Final arbitration — 2026-10-03 (design of record)

The three dials consolidate into one. The shipped dials stay as they are on the branch
until the merge; the model the next implementation builds is:

- **Cruising speed** — the open-water ceiling and the reference for what counts as slow.
- **Driving preference** — one cursor, ends "Through slow water" ↔ "Around slow water",
  whose only job is to auto-pick a rung of a fixed effort ladder.
- **The ladder** — as many routes as the user asks, each computed at its own aversion from
  none to maximum; the preference never changes a route, only which rung is selected, the
  others staying as offers.
- **The budget is demoted** — its surviving role is the internal auto-pick criterion,
  ranked by slow-time share, not a user dial. D2, D3 and D4 are superseded by this.

Follow-up decisions (2026-10-03): three routes (D10); the rung scale is min and max with
the remaining rung splitting the range (D11); the default rung is driven by the
Driving-preference setting (D12); early select and early save bind to the selected rung
(D13); collapsed rungs drop their duplicates (D14); a collapse that removes the cursor's
rung selects the nearest surviving rung (D15).

The acquisition UI is already merged (commit `aeeaf118`, R90): the panel pages over a
list of `RoutePage` with a `selectedIndex` and next/prev, and Select/Save act on the
selected page — the ladder's three offers reuse this, so no new UI surface is owed. The
remaining work is engine-side: compute the three rungs as pages over the one shared grid,
and map the Driving-preference cursor to the initial `selectedIndex`. Still open: the
ladder's cost model, measured on device before implementation.

**The candidate-pass apparatus retired (2026-10-03).** Dead since the ladder declares its
three rungs directly, and removed: the `route.avoid.candidate.passes` and
`route.avoid.candidate.skipAbsent` keys, `AppConfig.routeAvoidCandidatePasses` and
`routeAvoidCandidateSkipAbsent`, `parseCandidatePasses`, the whole `RouteOffer.kt` model
(`RouteOfferSource` + `RouteCandidatePass`), and the engine's stale λ-loop and candidate
KDoc, rewritten into a ladder paragraph. Still owed from the same demotion: the engine's
ignored `aversionKn`/`slowWaterBudgetPct` params, the test-only `betterPass`/`PassCost`/
`fineSpliceBetter`, and the dead slow-water budget chain.
