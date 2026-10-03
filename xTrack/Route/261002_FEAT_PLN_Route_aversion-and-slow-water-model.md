# 261002_FEAT_PLN_Route_aversion-and-slow-water-model

Status: in design — Phases 0/A/B/D shipped 2026-10-02, build green, Ask review ACCEPT; Phase C deferred to the device-measurement gate; no pointer in `## Implemented` yet.

## Purpose

Re-model the route's two slow-water settings around a vocabulary that separates physics
from preference: the **pace** is the boat, the **aversion** is how hard the search avoids
slow zones, and the **slow-water budget** calibrates the aversion to an outcome. Today
those three ideas are tangled in one price formula and one hidden constant.

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

- One physics dial: **pace** — the boat's speed; feeds the clock and the ETA, and it is
  the honest reference for the time-excess (a faster boat genuinely loses more time in a
  zone). Its cost effect is documented as intended, not accidental.
- One preference dial: **aversion λ** — a single, global cursor for every slow source,
  the band and a speed zone priced by the same λ, not one dial per zone type. It is the
  exchange rate "seconds of detour I accept per second of slow water": 0 = zones are free
  (pure distance/time), 1 = minimise real time (the physics point), above 1 = avoid, and
  the top of the scale is "stay out, shortest chord when a crossing is forced".
- One outcome dial: **slow-water budget** — the calibration of λ, run to convergence
  rather than one shot, so the line lands inside the band when the corridor allows, and
  the final share is **surfaced** instead of discarded.
- `softCostAversion` becomes the persisted value behind the aversion dial; the properties
  comment and the Settings description are rewritten to state the model.

## Decisions — resolved

**Resolved — technical, in the spirit of the functional model:**

- D1 — keep the pace as a user dial, the boat's cruising speed: it stays the physics
  reference and the trip figure's fallback, relabelled "Cruising speed" in Phase A; the
  observed pace already takes over for the displayed ETA, and the cost stays honest.
- D2 — a 0..5 slider of six integer positions (None at 0, Maximum at 5) whose positions map
  to λ on a **log curve** so the effect feels linear: λ = 0 at position 0, otherwise
  λ = 5^((p−1)/4) — positions 1..5 read 1.0 · 1.50 · 2.24 · 3.34 · 5.0 — and the shipped
  4 seeds position 4 (λ ≈ 3.34). The mapping lives in one pure, tested function, and the
  stored value is the position, not λ.
- D4 — keep *reported, never refused*: the budget is a preference, and the
  forced-crossing path already refuses nothing, so a hard gate would contradict the
  max-aversion wording.
- D5 — keep the properties key `route.avoid.speedZone.softCostAversion` as the seed and
  one home; a new persisted user value seeds from it, with no rename and no migration.
- D6 — confirmed by the user's own words: avoid entirely while a way around exists,
  shortest chord through the zone when a crossing is forced.
- D7 — clamp the corrected λ at 50 so a tiny budget cannot price a cell like kilometres
  of open water; the dial's own 0..5 clamp still binds the configured value.
- D9 — one global aversion dial; per-type aversion is out of scope for this plan.
- D8 — semantic decoupling, confirmed by the user: the pace stays the honest reference and
  may still move the price, so λ = 1 keeps meaning the fastest route; the settings wording
  must say the cruising speed can change the drawn route.
- D3 — the budget counts the slow water you **choose**: zones and the 300 m band, minus the
  unavoidable floor — forced crossings and the band's port exit/entry. The floor is
  subtracted on the **report**, after the probe, because a forced crossing is only known
  then; the λ loop keeps the raw zone share.

All decisions D1–D9 are resolved; the plan is ready to implement Phases A–E.

## Phases

- Phase 0 — single-line baseline: empty `route.avoid.candidate.passes` so one settled
  line renders while the new model is tested; this also removes the `zone300` candidate
  re-rasterise, the dearest step. **Found a no-op on device 2026-10-03:** an empty value
  is parsed to `null` by [`parseCandidatePasses`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:115)
  and the loader only assigns on non-null, so the shipped pair survives and the candidates
  still run. Fixed 2026-10-03: `parseCandidatePasses` now answers an empty list for a
  blank value, so an empty key declares no candidates; build and config tests green.
- Phase A — documentation: rewrite the property comments, the Settings labels and the
  descriptions to state the three-dial model; keep λ = 4 shipped so existing routes do
  not move on day one.
- Phase B — the seam: hand an aversion provider to the engine through
  [`RouteEngineChoice`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt:26),
  expose the Settings row, and read λ from it instead of `AppConfig` at solve time; the
  factory arity change touches both call sites (MapScreen, MapRouteEffects) and keeps the
  dummy engine's no-arg build.
- Phase C — the calibration: turn the one-shot correction into a capped iteration
  (suggest 4 passes) over the raw zone share, keeping the better-pass selection; only when
  the cap is still unmet does the corridor grow once and re-loop — the D3 floor is never a
  loop input, it is subtracted on the report after the probe.
- Phase D — the surface: carry `budgetUnmetZoneShare` and the settled λ through the
  ViewModel and overlay to the panel and dashboard, wire the existing strings, and
  delete the dead-string contradiction.
- Phase E — tests: pin the dial semantics (0 / 1 / top), the iterative loop, and the
  surfaced overrun; keep the suite green with the scoped run.

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

| Change | Marginal cost | Notes |
|---|---|---|
| Phase 0 — single-line baseline | negative | drops one cheap search and one full re-rasterise solve per acquisition |
| Phase B — aversion dial | 0 | the λ seed just reads a new value |
| Phase C — iterate to the budget, cap 4 | ≤ +2 A\* passes, worst case | each pass is A\* + pull + timing on the shared grid, no rasterise; an acquisition that already meets the budget pays nothing extra |
| Phase C — iterate *before* growing | potentially negative | a λ pass is far cheaper than the growth's re-rasterise, so more passes can postpone or avoid the growth escalation |
| Phase D — surface the overrun | 0 | a field carried, not computed |
| D3 — band in the budget | 0 | `slowShares` already reads band and ramp |
| D2, D7 — scale and clamp | 0 | dial arithmetic only |

### What must be measured before implementation

- The isolated cost of one A\* pass — the composite 3.4 s is not the pass cost — on device,
  at the shipped corridor.
- How often the budget is unmet today, so the worst case (+2 passes) gets a real frequency
  rather than a guess.
- What the candidates and the fine pass cost today, to know exactly what the single-line
  baseline turns off and what a later re-enable would buy back.

### The gate

No implementation until the iteration cap and the iterate-before-grow ordering are measured
on the device and the cap is confirmed; the current 2-pass figure is the floor, and any cap
above it is a latency decision, never a correctness one.

## Risks

- Each extra λ pass reuses the built grid and still costs an A\* plus a pull; the growth and
  the `zone300` candidate are the full re-rasterise solves — so the iteration cap is a
  latency decision, never a correctness one.
- Exposing λ changes a value users already have (shipped 4); the default must stay 4 or
  the change must be stated.
- The budget and the band are the user's real slow water, so D3 decides whether the dial
  keeps ignoring the 300 m band.

## Final arbitration — 2026-10-03

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
