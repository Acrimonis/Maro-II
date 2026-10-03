<!-- scope: feature -->
# Route — the avoid acquisition: the shortest-exit plan

**Created:** 2026-09-30 · **Branch:** `feature/avoid-I` · **Status:** design — Phase 3, Phase 4's splice
half, **Phase 1 and Phase 1b** shipped 2026-09-30; Phase 0's trace half delivered by the same pass, D6,
D7 and D8 settled with it; the rest waits on its decision.
Values live in `maro.properties`; this file names the roles, the phases and the decisions.

## The premise this plan answers

The requirement: a high `route.avoid.speedZone.softCostAversion` must mean **spend as little time in a
speed zone as possible** — leave at the earliest opportunity, whatever detour that costs. The code today
bends the line but cannot promise that, for four reasons read off the tree:

- **P1 — λ is a price, never a constraint.** [`AvoidSearch.search`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt:112) adds `zonePriceAtLimits(...)` per
  expansion, handed in by [`runPass`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:692).
  The line therefore trades detour metres against `λ × in-zone excess`; "leave at the earliest
  opportunity" is the λ→∞ limit, a hard objective this design does not express.
- **P2 — the cursor is not held.** [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:324) seeds λ from the setting, then re-derives it
  from the measured share against the budget (`λ₁ = λ₀ × share / budget`), one correction, two passes.
  The property is a seed for pass one, and the corrected λ is unclamped.
- **P3 — the share λ is asked to fix is broader than λ prices.** [`zoneSlowShare`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:221)
  counts any leg run below the pace, ramps included, so slow water the speed-zone cursor cannot price —
  the band's, the approach ramps' — sits in the same numerator.
- **P4 — the tail can undo the bend.** [`fineReSearch`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1080) splices a re-solved line because it is faster
  **on the clock**, a λ-blind test; the fairing reads no price at all — "a bow into a priced band or
  zone is accepted" ([`RouteCurveFitter`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCurveFitter.kt:66)) — and the faired line is the one drawn and saved.
- **P5 — the band is priced by an invented number, not by its own limit.** The route engine receives the
  band as **geometry only** (`AvoidWorld.bandWidthM`) with no speed attached, so
  [`bandPriceSec`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:109) fabricates
  one — `base × (zone300.softCostAversion − 1)`, its own key and its own 1..5 clamp — while
  [`limitAtFor`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:964) reads the
  regulated zones alone, so the clock never slows in the band. Verified 2026-09-30 by arithmetic: at the
  shipped 28 kn pace a 5 kn band cell costs `5 × base` against a 5 kn ring cell's `19.4 × base`, the same
  speed priced 3.9 apart, and the invented `4 × base` matches a 5 kn ring only at λ ≈ 0.87.

## Shipped since this plan was written (2026-09-30)

- **The record sweep, no behaviour change** — the speed-zone switch comment's false shipped value, the
  outside-margin fraction's stale number, the budget comment's stale default, the clamp's scope in the
  property and in `AppConfig` (it bounds the configured value; the loop's correction is unclamped), the
  fine-cell parse comment that called the key unread, the depth-gate KDoc re-anchored on
  `depthGateSource`, the epic's stale known-limits clause, and the flag KDoc that stated the code
  fallback as a default.
- **Phase 3, done early** — the λ loop keeps the better pass through a pure `PassCost` comparator beside
  `shareRank` (zone share, then in-zone metres, then the clock), only the kept pass's λ reaches the fine
  pass, the re-search and the probe, and both passes are traced (`zoneMetres`, `keep`, `PASSKEEP`).
- **Phase 4's first half, done early** — the re-search's splice needs strictly faster **and** no worse
  slow share (`fineSpliceBetter`), with both shares traced, and the `LINE` trace reports the loop's kept
  share beside the faired one. `solveCrossing` was read as asked and left alone: its local A\* prices the
  zone at the kept λ, so its decision is price-driven, not clock-driven.
- **Gate:** `gradlew :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL, `RouteAvoidEngineTest`
  27 tests with no existing expectation moved, both new tests executed; nothing committed.
- **Phase 1 and Phase 1b, closed on the review's findings (2026-09-30, same day)** — the band's limit is
  now **stored on the grid** beside a ring's and priced per expansion, so its price follows the corrected
  λ exactly as a ring's; the slow time is split **zone · band · ramp** and only the zone share drives the
  loop's correction, [`betterPass`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1026)
  and `budgetUnmetZoneShare`; the pull's guard prices the band and a ring with `max`, never a sum; the
  cape acceptance is pinned on the derived exchange rate `(pace/limit − 1) × λ`; and the `BAND` tag now
  means the band's **law water** (its width), the collar carrying a price but no limit. D6 settled as one
  cursor for every slow source (the band's own key retired), D7 as the collar keeping its own fraction,
  D8 confirmed as shipped. `PASS`/`PASSKEEP`/`LINE` carry λ, the band's priced λ, the three shares and
  in-zone metres. Gate: `apk-build.bat` and `gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL — the three
  touched suites 28 · 9 · 22, none skipped, the two expectations Phase 1 re-bases updated in place;
  nothing committed.

## Decisions to settle before the phases they gate

- **D1 — the budget's authority** (gates Phase 2): does `timeBudgetPct` stay a target the loop corrects
  λ toward, or does it become a ceiling that is only **reported**, leaving `softCostAversion` to mean
  what it says? Either way the record must state which of the two owns the line.
- **D2 — the fairing's no-price rule** (gates Phase 4): the fitter's KDoc says the soft price is
  deliberately unread, "the app cueing zone proximity elsewhere". Keep that and accept that a bow may
  enter a zone, or make the fitter refuse a ring.
- **D3 — the hard mode's face** (gates Phase 5, user-visible): does "as short as possible" ship as the
  top of the existing aversion range, or as its own setting? The user's call, since it changes what a
  number does.
- **D4 — the fine band** (gates Phase 0, walk item 15): the code already re-solves at
  `route.avoid.fine.cellRatio`. Change the code, or change the record to say so.
- **D5 — marker weights** (gates Phase 7, walk item 14): merge Phase 5's marker weights with the shipped
  marker scale now, or behind the regional grid.
- **D6 — one weight for all slow water** (gates Phase 1b): with the band priced by its own limit, does
  `route.avoid.zone300.softCostAversion` retire so one λ prices every source, or does each source keep a
  weight multiplying the same limit-keyed excess? The user's stated principle — a limit means one price
  — argues for one weight; a per-source weight keeps a UI knob for the band.
- **D7 — the band's outside margin** (gates Phase 1b): the 50 m strip beyond the band keeps its shape,
  priced as a fraction of the band's new excess at the existing `…zone300.outsideMargin.costFraction`,
  or the strip retires with the invented price it was built to soften.
- **D8 — the clock and the band** (gates Phase 1b, user-visible): a point inside the band takes its 5 kn
  in the clock **whatever the price switch says**, so reported times grow for inshore legs even with
  `route.avoid.zone300.enabled` false — the switch prices water, it never suspends the law. Confirm, or
  gate the clock with the switch.

## The band's law — the fix this session settled on (2026-09-30)

**The rule the user stated:** the 300 m band carries an absolute 5 kn limit, and a limit means one price
wherever it comes from — a 5 kn band cell and a 5 kn ring cell cost the same, a 10 kn ring costs less,
20 less still, and nothing at all at the boat's own pace. `zonePriceSec` already has exactly that shape;
the band is the one source that never learned its own speed.

**The work, five steps:**

- **B1 — the band's limit has one home.** `route.avoid.zone300.limitKn=5` in `maro.properties` plus its
  `AppConfig` accessor, clamped, with a KDoc stating it is the law's rule and not a tuning knob.
- **B2 — one price law.** The band's source prices through [`zonePriceSec`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:120)
  with that limit; `bandPriceSec` and, per D6, `route.avoid.zone300.softCostAversion` retire. The band
  stays a **soft** source — passable, never a wall — and its price now moves with the loop's λ like
  every other slow source.
- **B3 — the clock reads the band.** `limitAtFor` returns the **strictest limit in force**: the band's
  5 kn inside `bandWidthM` of the coast, a ring's own limit inside its ring. The ETA, the ramps
  (`route.speed.accelMps2`) and the reported share then agree with the drawn line about the same water.
  Per D8 this holds whatever the price switch says.
- **B4 — the collars.** The band's 50 m outside strip, and the ring's 100 m collar, keep their gradient
  shape as a fraction of the new excess, per D7.
- **B5 — tests.** One case asserting the invariant the user stated — **a 5 kn band cell and a 5 kn ring
  cell price identically** — plus the band-cost suite re-based, the ETA suite carrying the band's own
  slow time, and the zone phase-4 and cost-field suites green.
- **B6 — the record.** The avoid-algorithm doc's band paragraph, the `maro.properties` band block, and
  the epic's "Speeds come from where the line is" bullet — which says today that the band's limit slows
  the clock and is made **true** by B3.

**The exchange rate it buys.** A band metre becomes worth `(pace / 5 − 1) × λ` extra detour metres
instead of `zone300.softCostAversion − 1`: at 28 kn and the shipped λ = 4 that is **18 instead of 4**, so
a 1 km band stretch justifies up to 18 km of seaward detour where it justified 4 km. The band stops
being the cheapest slow water on a cape, which is the mechanism the Cap d'Antibes observation turned on.

**What it costs.** Inshore ETAs grow — the clock finally pays 5 kn at a 28 kn pace, about 5.6 × the pace
per metre — and the band's own key and clamp either retire (D6) or lose their meaning.

## Phase 0 — Measure, and repair the record (no behaviour change)

- **Goal:** the numbers the later phases argue over, and the record items already owed.
- **Work:** have `runPass` emit λ, the share **split** three ways (zone interior · band · ramp), in-zone
  metres and the exit count, and have the answer emit the faired share beside the searched one; measure
  the `PULL` boundary's mapping cost on the device against the ≤ 500 ms budget (the item the previous
  chunk parked here); settle D4 and write it; repair the F1/F2 wording pair of the standoff register.
- **Touches:** [`RouteAvoidEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:322) traces, [`RouteEta`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:221), the avoid-algorithm doc.
- **Gate:** `gradlew :app:assembleDebug :app:testDebugUnitTest` green; one trace each for a coastal line
  and a zone-crossing line. The device run is the user's.
- **Acceptance:** both traces carry λ, the three shares and in-zone metres; the record items closed.
- **Delivered 2026-09-30 (the trace half):** the `PASS`, `PASSKEEP` and `LINE` traces carry λ, the band's
  priced λ, the three shares and in-zone metres, and the record items are closed; the `PULL` boundary's
  device measurement and D4 stay owed.

## Phase 1 — Make the share honest

- **Goal:** the loop and the reported budget key read the quantity λ can actually buy down.
- **Work:** derive a **zone share** (ring interiors), a **band share** and a **ramp share** from the same
  timed legs; drive the λ loop and `budgetUnmetZoneShare` on the zone share alone; publish the other two
  as readings.
- **Touches:** [`zoneSlowShare`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:221) and a companion, [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:324)'s loop and its
  final `finalShare`, [`RouteResult`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:74)'s KDoc, the zone phase-4 and ETA suites.
- **Gate:** build + suite green with the updated expectations.
- **Acceptance:** a line whose slowness is all band reports zone share 0, is not corrected, and is not
  reported unmet; a zone-crossing line is still corrected.
- **Why it precedes Phase 1b:** B3 widens the metric once more (the band's slow time becomes real time),
  so the split must exist before the band's limit is added to it.
- **Shipped 2026-09-30, with Phase 1b:**
  [`slowShares`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteEta.kt:232) walks the same
  timed legs once and splits them zone · band · ramp; the loop, `betterPass`'s first key and
  `budgetUnmetZoneShare` read the zone share alone, all three published on the trace.

## Phase 1b — The band's law (the section above, as a phase)

- **Goal:** one price law keyed on the limit, and the band's own 5 kn known to both the search and the
  clock.
- **Work:** B1–B6, gated by D6, D7 and D8.
- **Touches:** [`RouteCostField.bandPriceSec`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCostField.kt:109) and its consumers, [`RouteAvoidEngine.limitAtFor`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:964),
  `AvoidWorld`'s band read, `AppConfig` + `maro.properties`, the band-cost and ETA suites, the epic.
- **Gate:** build + suite green, including the 5 kn band-versus-ring invariant.
- **Acceptance:** a route past a cape takes the seaward way wherever the inshore one is shorter by less
  than the new exchange rate; the reported time pays the band's limit.
- **Objection, stated:** this makes the band a *soft* source at 5 kn, so on a long coastal leg the legal
  line becomes dear enough that the search may prefer a detour which is itself slow or deep-draft
  unfriendly; the price still cannot express "never inshore", which stays D3's question.
- **Shipped 2026-09-30** — B1–B6 plus the review's findings: the limit stored per cell in
  [`AvoidGrid`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:112) and written by
  [`writeBandLaw`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidGrid.kt:430), priced per
  expansion in [`AvoidSearch`](../../app/src/main/java/ykws/android/maro/spatial/avoid/AvoidSearch.kt:115),
  the guard's arms merged in
  [`costField()`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:809), and the
  avoid-algorithm doc and the outside-margin default repaired.

## Phase 2 — Own the cursor

- **Goal:** `softCostAversion` means one thing, stated where it is read.
- **Work:** per D1, either fix λ at the setting (the budget becomes a report) or keep the correction
  clamped to the documented range; put the chosen sentence in the property, the accessor and the
  Settings description. With Phase 1b this becomes one sentence about **all** slow water.
- **Touches:** [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:324), [`AppConfig`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:298), `maro.properties`, the Settings row.
- **Gate:** build + suite green.
- **Acceptance:** one route solved at the range's ends shows a monotone effect on the zone share, and the
  record no longer contradicts the arithmetic.

## Phase 3 — Keep the better pass — **shipped 2026-09-30**

- **Goal:** the loop stops discarding a better answer.
- **Work:** compare pass two with the incumbent — zone share first, in-zone metres next, time last — and
  keep the better, with both named on the trace. The comparator is pure and testable, like
  [`shareRank`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:972).
- **Touches:** [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:324)'s loop, one pure comparator, the avoid suites.
- **Gate:** build + suite green.
- **Acceptance:** a case where pass two is worse keeps the incumbent; a case where it is better keeps it.
- **Shipped:** [`betterPass`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:981) with
  [`PassCost`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:988), the keep decision
  in [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:324) and
  [`zoneMetres`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1431) as its interior
  reading; both unit cases live in the avoid suites.

## Phase 4 — Make the tail λ-aware — **half shipped 2026-09-30**

- **Goal:** nothing after the loop silently undoes the loop.
- **Work:** the fine re-search's splice compares the **priced** cost rather than the clock alone (it must
  at least not regress the zone share); per D2 the fairing either refuses a ring or the record states
  why it may bow in; the answer reports the searched and the faired shares so a regression is visible.
- **Touches:** [`fineReSearch`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1003), [`RouteCurveFitter`](../../app/src/main/java/ykws/android/maro/spatial/avoid/RouteCurveFitter.kt:1), the answer's fields, their tests.
- **Gate:** build + suite green.
- **Acceptance:** a faired line that re-enters a zone is refused or reported; no shipped test regresses.
- **Shipped:** [`fineSpliceBetter`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:1003),
  the two shares on the `FINE research` line and the kept share beside the faired one on `LINE`.
- **Standing:** the fairing half, and with it the whole of Phase 4's second clause, per D2. Phase 1b
  sharpens it: once the band is dear, a bow into it costs what a bow into a ring costs.

## Phase 5 — The objective: the earliest exit

- **Goal:** the requirement at the head of this file is met, not approximated.
- **Work:** give the range's top a **hard mode** — a final pass on a copy of the grid with the
  restrictive zones blocked, spliced only where an answer exists **and** the forced-crossing probe does
  not report that zone forced ([`forcedCrossingNames`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:879) already probes exactly this way). Below the
  top the price behaves as today, so D3's answer decides the face.
- **Touches:** [`searchOnce`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:322), the probe, `AvoidGrid`'s copy path, `maro.properties`' KDoc and the Settings row.
- **Gate:** build + suite green; the two device counter-examples that retired the standoff (a 10 kn zone
  off Cap d'Antibes with passages tens of metres wide) re-read on the device, the user's run.
- **Acceptance:** where a way around exists the shipped line spends no metres in the zone, whatever the
  detour; where none exists the crossing is still reported forced and is still crossed.
- **Note, added with Phase 1b:** the band's law may make this phase unnecessary wherever the seaward way
  is the cheaper one — the hard mode is then only for the case where the detour itself is dear.
- **Objection, stated:** a hard mode can send the route on a long detour through worse water, or refuse
  a crossing that is legally required — which is why the probe gates it rather than merely reporting.

## Phase 6 — The regional coarse grid (parked frame, last)

- **Goal:** the reach, the growth and the box-dependence dissolve together.
- **Work:** one coarse grid over the region, kept rather than rebuilt per ask, a per-ask A* over it, the
  fine pass promoted to the substantive half. Entered only once Phases 1–5 are measured.
- **Acceptance:** on the Lérins-to-Salis corridor a first ask near the plan's ≈ 1.05 s and later asks
  ≈ 0.5 s, against today's grown 3.3 s, with no answer regression; "no path" and "forced" become
  statements about the region's own water, which makes the standoff register's F1/F2 true by
  construction.

## Phase 7 — The parked algorithm items

- Phase 5's marker weights (avoid-only, a marker may only make the sea dearer) merged with the shipped
  marker scale into one scale, per D5.
- The review over Phase 6's folded fixes, its first point the fairing's shipped values read against the
  plan's own table; then the fairing's four keys and the GPX acceptance device pass, the user's.

## Ordering, and what gates what

- **Shipped out of order, on the user's word:** Phase 3 and Phase 4's splice half; both are done, their
  gates green, and their acceptance met.
- **What stands, in order:** Phase 0's remainder (D4 and the device measurement) → Phase 2 (D1) →
  Phase 4's fairing half (D2) → Phase 5 (D3) → Phase 6 (optional, last) → Phase 7 (D5). Phase 1 and
  Phase 1b shipped together on 2026-09-30 with D6, D7 and D8 settled; Phase 0's trace half is delivered.
- Phase 1 preceded Phase 1b because the band's clock entry widens the share metric the loop reads — both
  delivered by the same pass — and D3's hard mode is now re-judged with the band's price already paid,
  since it may already buy the seaward route.
- Each phase's acceptance is the next one's premise, and the device reads between them are the user's;
  the parked walk items 14 and 15 are discharged by Phases 7 and 0 respectively.
- Every phase's gate is the same pair — `gradlew :app:assembleDebug :app:testDebugUnitTest` — plus the
  device pass it names; nothing here is device-validated by the agent, and no phase is started without an
  explicit order for it.

## Outcome

Shipped 2026-09-30 on `feature/avoid-I`, then parked: **Phase 1** (the share split zone · band · ramp, the
loop and the budget key read on the zone share alone), **Phase 1b** (the band's law — its own 5 kn stored
on the grid beside a ring's and priced per expansion, the clock reading the strictest limit in force, one
price for every slow source), **Phase 3** (the pure `betterPass`/`PassCost` comparator that keeps the
better pass) and **Phase 4's splice half** (`fineSpliceBetter`, strictly faster and no worse a slow share);
**Phase 0's trace half** (`PASS`/`PASSKEEP`/`LINE` carry λ, the band's priced λ, the three shares and
in-zone metres) and the record sweep also shipped. **D6** settled as one cursor for every slow source,
**D7** as the collar keeping its own fraction, **D8** confirmed as shipped. Build and the touched suites
green throughout; nothing device-validated by the agent.

The rest of the plan was **parked rather than dropped** when the epic's open walk level closed by decision
on 2026-10-03. Each carried point keeps its resume condition here, so nothing depends on this archived body:

- **Item 1 · D4 — the fine band:** decide whether to change the code or change the record — the engine
  already re-solves at `route.avoid.fine.cellRatio`.
- **Item 2 · Phase 0's remainder:** measure the `PULL` boundary's mapping cost on the device against the
  ≤ 500 ms budget, and settle D4 with it.
- **Item 8 · D1 — the budget's authority:** decide whether `timeBudgetPct` stays the loop's target or
  becomes a ceiling only reported, leaving `softCostAversion` to mean what it says.
- **Item 9 · Phase 2 — own the cursor:** make `softCostAversion` mean one thing where it is read, gated by
  D1.
- **Item 11 · D2 — the fairing's no-price rule:** keep it and accept that a bow may enter a zone, or make
  the fitter refuse a ring.
- **Item 12 · Phase 4's fairing half:** make the tail λ-aware per D2 — its splice half already shipped.
- **Item 13 · D3 — the hard mode's face** (user-visible): ship "as short as possible" as the top of the
  aversion range or as its own setting.
- **Item 14 · Phase 5:** the earliest exit as the range's hard mode, gated by the forced-crossing probe.
- **Item 15 · D5 — marker weights:** merge Phase 5's marker weights with the shipped marker scale now, or
  behind the regional grid.
- **Item 16 · Phase 6:** the regional coarse grid.
- **Item 17 · Phase 7:** the parked algorithm items — the marker weights and the review over Phase 6's
  folded fixes.
