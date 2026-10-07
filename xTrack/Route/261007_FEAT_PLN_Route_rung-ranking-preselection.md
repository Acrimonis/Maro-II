<!-- scope: feature -->
# Route — the rungs ranked, and the winner preselected

**Date:** 2026-10-07 · **Status:** in design · **Order:** the user's word of 2026-10-07 in the
`RoutePassRules` discussion — *"The three rungs — go with (b): rank the rungs against each other and
preselect the winner"*.

**Origin:** the discussion opened by the user's question about `RoutePassRules` — why a keep rule with no
caller stands in the tree. `betterPass` is the ranking rule the ladder has never used: each arming declares
three rungs at fixed λ and offers all three, with the seat's target coming from the configured preference
alone.

## Why — the ladder offers three lines and ranks none of them

- **Three rungs, one pass each, no comparison.** [`routesToCompute`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:206) declares `around`, `balanced` and `through` over one shared grid; [`solveAtLambda`](../../app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt:318) computes each once and never corrects it, so nothing ever compares two of them.
- **The rule that would rank them exists and is unread.** [`betterPass`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRules.kt:18) ranks by **zone share, then in-zone metres, then clock** — the three quantities a rung's own answer is built from — and no shipped path calls it.
- **The seat is the preference alone.** [`preferredRungIndex`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:320) is stored at arming from `routeRungIndex(routeSlowWaterAversion)` ([`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:205)) and re-applied on every landing through [`routeEtaSeatedIndex`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:275). The user's own aversion decides where the selection sits, and the lines themselves get no vote.

## What changes

1. **The engine ranks its own rungs.** When a ladder's rungs have settled, the engine folds their own costs through `betterPass` and reports the winner. The rule keeps its **three** keys, which is why the ranking lives engine-side: the answer deliberately carries no instrumentation vocabulary, so in-zone metres never reach the flow.
2. **The flow seats the winner.** The seat's target becomes the reported winner once it exists, and the preference keeps its say by *producing* that winner rather than by naming the index — the one-shot shape the auto-pick already uses ([`routeAutoPickReady`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:194) and its flag in `MapScreen`), so a late landing cannot move a selection the user has since made.
3. **`RoutePassRules` survives, half its size.** `betterPass` gains a caller and becomes live; [`fineSpliceBetter`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRules.kt:40) loses its subject with the re-search retirement, so it and its test retire — the object then holds the ranking rule alone.

## The preference's order

The preference stops being a seat hint and becomes the ranking's own input: each of its three stops names the
criterion that **leads**, and the other two follow as that stop's tie chain. The user's own words, 2026-10-07.

| Preference (λ) | Leads with | Stated as |
|---|---|---|
| **Fast** (0) | the trip's **total time** | *fast: time* |
| **Balanced** (2.5) | **`PassCost.zoneShare`** — the share of the trip's own time a speed zone slows | *balanced: PassCost.zoneShare* |
| **Fun** (5) | **the least time slowed by zones** — the same measure as Balanced's | *less time in zones as possible, which is what maximum zones aversion would provide* |

The three criteria are the ones [`PassCost`](../../app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRules.kt:25) already carries, so the ranking keeps its inputs and gains only an order parameter. **What the preference no longer does** is name a rung: it names an intent, and the ranking answers which rung delivers it.

## The preference's rule

The preference stops being a seat hint and becomes the ranking's own input: each of its three stops states
**how the three criteria are weighed**, in the user's own words of 2026-10-07.

| Preference (λ) | The rule | What a user reads |
|---|---|---|
| **Fast** (0) | the trip's **total time** leads — the shortest trip wins outright. | Pick the quickest route, whatever water it crosses. |
| **Best** (2.5) | the **total time** leads **provided the zone share stays within the ratio**; a rung over it is beaten by the smaller share, so the budget is a gate and the clock decides under it. | Pick the quickest route that still keeps slow water under your budget. |
| **Fun** (5) | the **zone time** leads and the **overall time is not weighed at all** — the clock enters only as the last resort. | Pick the route with the least time in speed zones, however long it takes. |

Two consequences worth stating before this is built:

- **The budget stops being decorative.** `route.avoid.speedZone.timeBudgetPct` — handed to the engine today as
  its `slowWaterBudgetPct` provider and read nowhere — becomes Best's gate, so this rule gives the provider its
  first reader.
- **The three stops are genuinely distinct**, which the first mapping was not: the clock alone, the clock under a
  zone budget, and zone time regardless of the clock.
- **The gate gets a surface and the stop gets its own word.** The budget ratio is given a Routing-settings block
  of its own — it has a stored preference and no row today — so the number Best measures against is visible and
  settable; and the middle stop is renamed **Best**, the word its rule earns.
- **The gate's edges fall out of the rule, with no special case.** *Within* is inclusive, so a line spending
  exactly the budget is inside it; a ratio of 0 leaves only a zero-zone line qualifying and otherwise hands the
  choice to the cleanest, while a ratio of 100 qualifies every line and hands it to the clock — so the two
  extremes behave like Fun and Fast, which is what those settings should mean. The key is already clamped 0–100.
- **The seat follows the running best and converges on the final one.** It moves on **each** improvement — the
  user's word of 2026-10-07, reversing the earlier *no follow on each update* — and **only the user's own touch
  freezes it**, never afterwards. The auto-pick still takes the **final** best, so the two agree on every arming
  the user has not touched, and differ only where a frozen seat is left behind by a better last landing.

**Balanced's gate is confirmed by reading the tree:** the ratio is `route.avoid.speedZone.timeBudgetPct` —
**25** in [`maro.properties`](../../app/src/main/assets/maro.properties:385) and clamped 0–100 — and it is already
surfaced as the **slow-water budget preference** ([`SettingsManager.routeSlowWaterBudgetPct`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:167), persisted as `route_slow_water_budget_pct`), so the gate is user-tunable rather than a constant. Two things this exposes:

- The code default ([`routeAvoidSpeedZoneTimeBudgetPct`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:430) = **33**) **disagrees with the shipped file's 25** — a latent drift of the same kind the dash keys had, which no test pins.
- If Balanced gates on that preference, the user needs a surface to see and move it: no Settings row for the budget was found.

**Settled with the user, 2026-10-07.** Fun leads on the **absolute zone seconds** — minutes, not a share, so a
longer trip cannot look cleaner (derivable as the share times the duration, so no new plumbing); **one shared
tail** settles a lead tie for all three stops, the remaining criteria in `betterPass`'s own order and a total tie
falling to the rung nearest the preference; the seat **may move once** when the winner lands, in the auto-pick's
one-shot shape; and **`fineSpliceBetter` retires** with its test, leaving `RoutePassRules` holding the
preference-aware ranking alone.

## Where each half lives

- **The ranking is the engine's**: it holds the per-rung costs before anything is published, and `PassCost`'s figures — the zone share, the in-zone metres and the clock, plus the absolute zone seconds Fun leads on — are not on an answer and must not be added to one.
- **The seat is the flow's**: `preferredRungIndex` and `routeEtaSeatedIndex` stay the one home for where the selection sits, and the winner is only the value handed to them.
- **The engine already holds both inputs, unread**: `aversionKn` (the preference's λ) and `slowWaterBudgetPct` are wired from the settings providers by [`MapRouteEffects`](../../app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt:33), so the ranking needs **no new plumbing** — it begins reading two providers that are dead today, which answers the question of whether the engine needs a setting or reads one: it takes a provider, as every engine does.
- **The λ→stop mapping has to move.** The thresholds that turn a stored aversion into one of the three stops live in [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:205) as the UI's own rule, and the engine now needs the same answer; the mapping therefore relocates beside the ranking, one home for the ladder's three λ values and their thresholds, with the UI's helpers reading that home rather than owning it.
- **The winner rides the seam as one added field** — not a side channel and not a new update kind — and it is a **running best, never a verdict**: folded at **every rung's terminal** on the user's word of 2026-10-07, *"as soon as I have 2 routes I can already make a choice"*, it carries the best of the rungs that have landed **and how many it compares**, so a surface can read *so far, the winner is…* instead of waiting for the third. Only a terminal carries a finished line, so no mid-pass update can produce a winner; the flow's [`onUpdate`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:443) takes every update and files it by lookup id, and the *stage word* stays the first lookup's alone.

## Phases

1. **The rule, its mapping and the running best.** The λ→stop thresholds move out of the UI's pure rules into one home beside the ranking, with [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:205)'s helpers reading it rather than owning it — the engine needs the same answer and must not duplicate it. One entry point in `RoutePassRules` then takes the preference's stop and folds the landed rungs through its three shapes — the clock alone, the clock under the gate, the zone seconds regardless of the clock — folding at **every rung's terminal**, where the added field carries the best of those landed **and how many it compares**, so a surface presents it as *so far* rather than as a verdict. A rung that found no path is not a candidate, and a lead tie walks the one shared tail. Exit: five fixtures — one per shape, one proving the gate, one where a later landing changes the running best, and the mapping's own — and the build green.
2. **The stop's word.** The middle preference is renamed through the one string the preference family reads — `route_computation_balanced` (EN *Balanced* → *Best*, FR *Équilibré* → the French word still to choose). The panel's rung row is **not** touched: it reads its own family (`route_rung_balanced` = *Balanced*), which names where the line goes rather than what the user wants. Three KDocs naming the word go with it ([`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:222), [`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1572), [`MenuDrawerOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:609)), and `settings_freq_balanced` — the update-frequency row's own *Balanced* — is another setting and stays. Exit: both locales carry the new word, the sweep is one string pair plus three KDocs, and no test asserts the old word because none does.
3. **The budget's own setting.** A Routing-settings block of its own holds the budget ratio, reading and writing the preference the gate uses, seeded from `route.avoid.speedZone.timeBudgetPct`. Exit: the row persists the value and the gate's fixture reads that same stored value back.
4. **The seat, the so-far marking and the auto-pick, flow-side.** The seat **follows the running best on every improvement**, from the first comparison — two rungs landed — and **a touch of the selection freezes it for good**; seated on the running best's row, marking and seat agree except where the user has frozen one. The **running best's row** is marked **so far** while the ladder still computes, one new string in both locales, cleared when the last rung lands. The **auto-pick** (`Route (auto)`, R80) changes its promise: triggered, it selects the **final best** rather than index 0, so it waits for the set rather than for the main's line. Exit: the seat follows each improvement and freezes on any hand selection, the marking clears at the last rung, the auto-pick selects the final best, and every existing seat fixture stays green with the one pinned to index 0 retargeted.
5. **The record.** `RoutePassRules`' KDoc **and its name** — with `fineSpliceBetter` retired the object holds one preference-aware ranking, so a singular name matches what it is — `PassCost`'s own doc (its figures are no longer three), the ladder section of [`FEAT_DOC_Route_engines.md`](FEAT_DOC_Route_engines.md:1), the feature file's state lines **with R80's amended text**, and the budget key's code default brought onto the shipped 25; `fineSpliceBetter` and its test retire.
6. **The row's own presentation.** The winner carries a **pulsing dot** in a **leading slot of its own**, before the row's first column — the app's lists already give their selection door a dedicated leading zone, so a leading marker follows precedent rather than inventing a column — reusing the pulse component already in the tree ([`MapPulseDot.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapPulseDot.kt:1)). The figures column **reverses its two lines** — the **time first, the distance second** — keeping every line's weight exactly as it is today, so the row's emphasis is untouched and the selected row still reads as it does now (the user's word of 2026-10-07: *just revert the order*). The **pending rows** reverse with it, or a waiting row would read in the old order beside a landed one. The pair comes from the row's one figures home ([`RouteRowFigures`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:150)). Exit: the winner's row pulses and the dot travels with the running best, and the line order is pinned by a fixture **for a landed row and a pending one alike**.

## Verification

- **One shape per preference**: Fast picks the shortest clock; Best picks the fastest rung that stays under the budget, and the cleanest when none does; Fun picks the least zone time whatever the clock — each pinned on the engine's own evaluation.
- **No line and no clock moves**: the three rungs are computed exactly as they are today; only which one the seat starts on changes.
- **The seat follows, then freezes for good.** It moves to the running best on every improvement, and the first touch of the selection ends that permanently — the user's word of 2026-10-07 — so the only residual is a seat a better last landing would have changed had the user not taken over.
- **The budget is visible, settable and read**: the new row persists the ratio, the gate reads the same stored value, and the code default matches the shipped 25.
- **Suite and build green**, the parked `route.avoid.fine.cellRatio` red the only one.

## Assessment, 2026-10-07

Recorded from this plan's own review, so a build knows what is settled and what is not.

- **How the winner reaches the flow — closed by reading.** The plan said *one new engine-reported value on the
  ladder's settlement* without pinning its form: a field on the settlement update, a new update kind, or a value
  answered before any lookup — the third impossible, since no winner can exist before the rungs settle.
  **Decided: a field on the last rung's terminal update carrying the winning computation's id.** The objection
  first raised — that the last rung to land is not the narrator, so the screen would have to read it from the
  collector — was checked and is **overstated**: [`onUpdate`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:443) already takes every update and files it by lookup id, so a field on any rung's terminal update is readable, and only the *stage word* is deliberately the first lookup's own.
- **The gate's edges are unstated.** What a ratio of 0 or 100 means, and whether *within* is inclusive; the plan
  answers only the case where every rung exceeds it — the cleanest wins.
- **A collapsed rung must not win.** Excluded as firmly as a no-path rung, or the winner can be a page that does
  not exist; the seat's existing nearest-survivor rule then still applies to the winner.
- **The rename is measured, and narrower than feared.** One string pair (`route_computation_balanced`, EN and FR)
  plus three KDocs; the panel's row reads a different family (`route_rung_balanced` = *Balanced*, naming where the
  line goes) and stays; `settings_freq_balanced` is another setting entirely; and **no test asserts the old word**.
- **Two dead inputs gain their first readers in one change**: the ranking is `betterPass`'s, and Best's gate is
  the budget provider's — worth saying aloud, because the engine's own doc names both as read nowhere.
- **Weight:** phase 1 (the ranking and its four fixtures) and phase 3 (a new Settings block with its own
  persistence) carry the change; phases 2, 4 and 5 are small and mostly mechanical.

## Risks

- **The preference's job changes shape.** Today it names a rung; after this it states an intent the ranking serves, so a user who moved the slider to reach a particular line reaches it only if that line wins.
- **A winner announced late is a jump.** The one-shot bound is what keeps it from moving twice; without it the selection would follow every landing.
- **Ranking on the answer would be weaker.** A flow-side ranking could read only share and clock, silently dropping `betterPass`'s middle key — the reason the ranking is engine-side.
- **The engine gains a small piece of state, not merely a fold.** It already mints and holds the declarations, but today it emits each rung's settled result and keeps nothing; a running best means retaining each settled rung's cost until the set completes. Small, per-arm and dropped with the arm — but new state, and worth its own fixture rather than being described away as a fold over what the engine already owns.
- **A frozen seat can be overtaken.** Once the user touches the selection the seat never moves again, so a better last landing leaves the highlight on a row the final best calls worse. That is the user's own doing by construction, and the *so-far* marking is what says the ranking is still moving.
- **The seat moves while the ladder computes** — the flicker the earlier *no follow* reading was taken against. The marking and the pulse are what make it legible rather than jumpy; if a device pass finds it noisy, the fallback is a one-shot seat, not a redesign.
- **The order change is presentation only.** Every weight stays as it is, so nothing the row emphasises moves: the *bold time versus Fun* clash and the selection-weight cue a bold time would have cost both fall away with the bold itself, and the row's own selection cue survives untouched.
- **The list's own order stays by time.** [`routeEtaOrder`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:261) sorts the pages fastest-first, so the winner can sit anywhere in the list — under Fun, deliberately so. The dot is what makes it findable; no reorder is proposed.
- **R80's promise is reversed by this plan.** Its text says the auto-pick *"promises the main, and no candidate that landed first may move it"*; the user's word of 2026-10-07 makes it take the **final best** instead, so R80, the auto-pick's KDoc and the `selectMainRoute()` call site all move with this change.

## What the preference does not do

- **It does not price anything.** The engine already ignores the `aversionKn` provider it is handed; the three rungs carry their own fixed λ, so the preference never touches the search — only the choice of which settled line to seat.
- **It does not add a rung.** All three are computed on every arming whatever the preference says, so moving the slider changes which line is preselected, never what is computed.
- **Its near-circularity is the honest limit.** λ 5 already tends to avoid zones and λ 0 already tends to the shortest clock, so the ranking will usually confirm the preference's own rung; its value is in the arming where a rung's word and its result disagree — a λ 0 line pulled through slow water, or a λ 5 line that entered more zones than a cheaper neighbour.

## Open questions

**None.** Everything the plan was written around is settled on the user's word of 2026-10-07 — Fun leads on the
absolute zone seconds, one shared tail settles a lead tie, `fineSpliceBetter` retires, and the seat takes the first
comparison and never follows, while the auto-pick takes the final best. The winner's shape is decided in the
Assessment above; the gate's edges fall out of the rule itself; and the rename's sweep is measured, at one string
pair and three KDocs. **The plan is buildable as it stands.**

## Parked

- The two-pass λ correction inside a rung (option (a) of the discussion) — the keep rule would then rank a corrected pass against its incumbent, as it was written to; not taken now.
