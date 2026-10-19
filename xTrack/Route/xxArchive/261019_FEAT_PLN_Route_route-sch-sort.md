<!-- scope: feature -->
# Route search panel — per-row stage, header summary, provisional figures, order

Status: in design (2026-10-19), revised the same day after the user's answers. Nothing implemented.

## Decisions taken (user, 2026-10-19)

1. **Stage in the rank's slot.** While a row's own line is not final, the slot under its name (where `#rank` reads) prints that row's own stage — **bold italic** (`Grid`, `Search`, `Pull`, `Snap`, `Refine`). The row's rank takes the slot as soon as that row has landed, read over the rungs landed so far and live as others land.
2. **The header carries the settings, not the stage:** `Landed x/n · cruising speed · pref`. The speed is the setting, or — under `Any` — the pace evaluated for the search; the pref is the Slow-water dial's word.
3. **Provisional time and distance on every row** that has them, replaced by the settled pair at landing.
4. **"So far" is dropped.** The stage word makes it redundant; the running-best mark goes with it.
5. **Ranks are live; the check mark waits for the end.** A landed row wears its rank at once (no "so far" mark — the stage words on the other rows and the `Landed x/n` counter say the set is incomplete); the crown's check appears only when every rung has landed. This reverses the panel's recorded rule that no rank stands mid-run, for the rank only.
6. **The order is gated, 1 s.** Rows sort by rank, refreshed at most once per `maro.properties` interval (default 1000 ms); the fields (stage, figures, rank text) update at once, and the final landing refreshes the order immediately.

## Today (read from the tree)

- One stage word for the whole search: only the main narrates (`publish(narrates)`, `RouteAvoidEngine.kt`), through one engine-wide `lastStage` and `mainLookupId`; the view model keeps one `_stage`; the header prints it with the selected row's number (`Pull #2`).
- The provisional pair (`RouteProvisional`: distance, duration) already exists, rides the SNAP boundary update for every rung, lands on `RoutePage.provisional`, and the row already prints it (`RouteOverlay.kt` row figures) until the terminal replaces it. Before SNAP a row has no figure (the SEARCH→PULL boundary, `RoutePassRunner.kt:131`, holds the cell path but sends no pair).
- The ETA order reads `plan?.durationSec` only, so a row holding just a provisional pair sorts as if it had nothing.
- The verdict is already live in the view model (every rung's terminal carries a fresh fold over the landed rungs); only the panel hides rank and crown while `acquiring.searching`, and puts a *so far* mark on the running best's row (`soFarLookupId`, `runningBestLookupId`).
- Header today: stage word, the dial's word, then the page dots; once settled the word reads the page count and the pace (`route_status_done_*`).

## Design

- **Per-rung stage.** The engine tracks `lastStage` per lookup, not once, and every rung narrates its boundaries (today a non-main rung publishes only at SNAP). The rungs run concurrently (`computeScope.async`), so the per-lookup state is a concurrent map keyed by lookup id (or a small per-rung object), cleared at that rung's terminal — the single `lastStage` / `mainLookupId` fields rely on the main alone writing them and cannot simply be reused. The view model adds a stage on `RoutePage`, written in the same three branches that write `_stage` for the main today (landing, boundary/refusal, fold) and cleared at its terminal. **`_stage` stays**: the menu drawer's route summary reads it (`MenuDrawerOverlay.kt`, `route_status_stage_number`). The main still alone drives the map's provisional line.
- **Row slot.** Stage word (bold italic) while the row computes → its `#rank` from the row's landing on. No *Landed* word is needed, the counter in the header carrying that. Stage words reuse `route_stage_*`. The panel drops its `searching` gate for ranks and keeps it for the crown's check.
- **Provisional figures.** Rows already print the pair; the work is (a) the sort key reads the displayed time (settled, else provisional, else none) and (b) an earlier pair at the SEARCH→PULL boundary, the cell path's length and clock, which the engine does not publish today (see *Points settled*).
- **Header (trailing edge, decided).** One line `Landed x/n · speed · pref`, joined by a middle dot, replacing the status word and the separate dial word (so the edge holds no more items than today: line, then the page dots). `x` and `n` are both derived from the page set — `x` the pages holding a plan or a refusal, `n` the page count — so a fold moves both together and the ratio stays honest. Speed reads the setting, with the resolved pace beside it under `Any` (the panel needs the setting as well as the resolved `paceKn`); pref is `dialLabel`. The line is one `maxLines = 1` text with an ellipsis, so the **tail is what portrait cuts**: segment order puts the least dispensable first, and the counter is worded short. The committed early-select state keeps its own word (`route_status_selected`) in place of the line. `route_status_stage_number` stays for the menu; `route_status_acquiring` and `route_status_done_*` go if nothing else reads them (the menu reads `route_status_acquiring_bare`).
- **Order.** One pure function in `RouteOverlay.kt`: ranked (landed) rows by rank, then rows holding a provisional pair by that time, then the rest in ladder order; with no verdict it falls back to the time order. It is read by `selectPage`, `routeEtaSeatedIndex` and both `MapScreen` call sites through the one snapshot below, so a tap never lands on a different row than the one shown.
- **Throttle (decided).** The order is a snapshot held by the view model, refreshed at most once per interval (leading edge: the first change reorders at once, later ones wait for the interval's end), the final landing bypassing the gate. **The snapshot holds lookup ids, never page indices**: a fold removes a page and re-maps the rest, so a stored index list would point at the wrong row or past the end; a fold also forces an immediate refresh. The interval is a new `maro.properties` key (default 1000 ms, `# tldr;` line, `AppConfig` accessor), its name to confirm against the route panel's family.
- **Dropped code.** `soFarLookupId`, `runningBestLookupId` and the *so far* wording; the seat keeps reading the running best.

## Points settled (2026-10-19)

- **Throttle, not settle.** First change reorders at once, later ones wait for the interval's end; settle may never fire while rungs keep landing.
- **Header on the trailing edge, with the overflow rule stated.** One ellipsised line; the cut falls on the pref, the last segment. No subtitle row; if the device shows the pref cut too often, the segments are shortened before a row is added.
- **Merged entries count as one in the ranking (user, 2026-10-19).** A folded twin is not a rung of its own: on a fold the view model records *dropped lookup → survivor*, each surviving page takes the best (lowest) engine rank among itself and the lookups folded into it, the crown moves with it (the check never vanishes with a swallowed twin), and the displayed ranks are counted over the pages present, `1..k`, never `1, 3`. The engine's fold is untouched: the collapse test (`routeDispersionM`) lives in the UI layer, and counting over the surviving pages gives the same rank sequence the engine would give had it never seen the twin, except where a twin's presence moved a tolerance group's boundary, which identical lines cannot do. The pure mapping is tested alone; the DSC todo "the ranked page the fold swallows" is closed by it.
- **The earlier provisional figure is in (user, 2026-10-19: jumping figures are accepted).** At the SEARCH→PULL boundary the cell path (`full` in `RoutePassRunner.kt`) is already in hand: its length and its enforced-limit clock (the same `timeLineWithLimits` the SNAP pair uses, one more walk) travel as a `RouteProvisional` on that boundary update, for every rung. The pair then jumps at SNAP (pulled line) and at the landing (settled line); the figure is an overestimate by nature (the jagged cell path), and the header's stage word and the row's bold-italic slot say it is not final. The non-narrating rungs' early return in `publish` (which lets only the SNAP pair through) widens to let the PULL pair through; the view model already stores any non-null provisional on the page.
- **`Any` stays as shipped.** The header shows the evaluated pace beside `Any`, so the reader sees the number the times rest on; no change to the pace rule.

## Tests

- Order function: ranked first, then provisional by time, then ladder order; no-verdict fallback; no ties.
- Throttle: the gate is a small pure class taking the time as an argument, tested on its own — first change immediate, burst coalesced, terminal and fold bypass. There is no `RouteViewModel` test to extend, so none is assumed.
- Fold mapping: a survivor takes the best rank of its folded twins, the crown follows it, the ranks count over the pages present (`1..k`), and a verdict naming only folded-away lookups still marks the survivor.
- Early provisional pair: the PULL-boundary update carries a pair for the narrating rung and for a candidate alike, and the pair the SNAP boundary then sends replaces it.
- Per-lookup stage tracking: two rungs narrating interleaved keep their own `stageDone`.
- Header text: the three segments, `Any` with and without evidence, `x/n` on a fold and on a failed rung (a refusal counts as finished).
- A dummy-engine search publishes no stage: the slot must read empty, not stale.
- The panel's marks stay untested today (open todo); not widened here.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — per-lookup stage, every rung narrating, the candidate gate in `publish` widened
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt` — the pair at the SEARCH→PULL boundary
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `RoutePage` stage, the order function, the displayed-time key
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — stage per page, the order snapshot
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the slot, the header, the *so far* removal
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the sorted pages and the header's inputs
- `strings.xml` (both locales); `maro.properties` and `AppConfig.kt` only if the throttle stays
- `app/src/test/java/ykws/android/maro/ui/map/RouteAcquisitionTest.kt`, `RouteAvoidEngineTest.kt`

## Implemented

2026-10-19, on `feature/route-gogo`. `:app:testDebugUnitTest` (full suite) and `:app:assembleDebug` green; the device pass is the user's.

- **Per-rung stage.** `RouteAvoidEngine` keeps the chain per lookup in a `ConcurrentHashMap` (`lastStages`) and every rung publishes it, opening on a new `GRID` word for the wait on the shared grid (the engine never published `CORRIDOR`/`GRID` before; only `GRID` is added). `RoutePage.stage` carries it, written in the three branches that write `_stage`, which stays for the menu drawer.
- **Early provisional pair.** `RoutePassRunner` sends the cell path's length and limit-read clock at the PULL boundary; the SNAP pair and the landing replace it.
- **Rank, crown, order.** `routeRankView` counts a folded twin with its survivor (best rank, crown follows, ranks `1..k`); the panel prints a rank as soon as the row has landed, the bold-italic stage word until then, and the crown's check only after the last rung. `routeTargetOrder` puts ranked rows first, then provisional rows by time, then the ladder; the view model holds the order as lookup ids behind `RouteOrderGate` (`route.panel.orderRefreshMs`, 1000 ms), forced when the set settles or the standing ids change.
- **Header.** `Landed x/n · speed · pref` on the trailing edge (`Any (12 kn)` under `Any`); the committed word is kept. *So far* is gone, with `route_winner_so_far`, `route_status_acquiring` and `route_status_done_*`.
- **Tests.** Order, snapshot, rank-view and gate tests; an engine test that all three rungs keep their own chain and send the PULL pair.

Deviations and corrections to this plan: a `RouteViewModel` test home does exist (`RouteAcquisitionTest`), so the earlier "none to extend" line was wrong; the throttle gate is still a standalone class. The review's open low-severity points: the verdict is stored a moment before the fold table is, so one rank recomputation may count the twin as its own entry (the next page change corrects it); `selectPage` reads the live order, so a tap in the instant before a recomposition can land one row off; the hand-tap-then-fold re-seat gap was not confirmed as new.

## Outcome

Shipped, then partly withdrawn the same day: the per-rung stage words, the `Landed x/n · speed · preference` header, the early provisional pair and the live ranks counting a merged route once stand; the one-second order gate (`route.panel.orderRefreshMs`) and the provisional-time order group were withdrawn by `261019_FEAT_PLN_Route_route-sch-sort-landing-order.md`, which stands where the two disagree. The state is in `FEAT_DSC_Route.md`; its two transient edges are a todo there.
