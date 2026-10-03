<!-- scope: feature -->
# Route — the preference becomes the auto-selected default

**Date:** 2026-10-03 · **Feature:** Route · **Status:** design settled, not implemented

## Decision

Keep the three-route ladder. Order the rungs by ETA, fastest first — which makes "best speed" the ordering criterion literally. The Driving preference (λ around · balanced · through) stops choosing the route; it only marks which rung starts selected.

## Functionally

- The target speed already drives the computation — it is the base cost, the A* heuristic and the zone-excess price — and λ stays a separate detour multiplier, so a rung still exists for around, balanced and through.
- The panel and the map's line pool order the rungs by landed duration ascending; ties keep the ladder's natural order.
- The preference resolves to one rung and that rung is the initial selection; the user still steps freely among the sorted rungs.
- The collapse (two rungs within the dispersion tolerance folding into one) applies before the sort, so a collapsed ladder can show fewer than three rows and the sort never has to compare a duplicate.

## Choices made

1. ETA is the ordering key, not λ — "through" is usually first and "around" usually last, unless the geometry collapses them.
2. The preference is a default marker, not a filter: every rung stays selectable.
3. The λ ladder is kept, so the around-vs-through detour control survives as a selectable row rather than a hidden price.

## What changes in the code

- `RouteViewModel` keeps the three fixed-λ lookups; the page set gains an ETA-ordered view, or the panel sorts the landed pages by duration.
- The preference's rung stays the *initial* selection, now resolved against the sorted list rather than the ladder index.
- `routeStepIndex` and the panel's table walk the sorted order.
- `routeRungIndex` / `routeRungLambda` are unchanged — λ still names each page's aversion.

## Refinement — keep the preference, relabel it, autoselect on ETA (2026-10-03)

- The preference is **not disconnected**; it stays the one hurry↔fun lever.
- **Relabel in Settings and in the ladder:** the three rungs become Fast · Balanced · Fun in both locales, and the Driving-preference slider reads the same labels through `routeRungIndex`.
- **Autoselect on landing:** the seat re-runs `routeSeatedIndex(hasPlan, preferredIndex)` on every landing — while nothing has landed it holds the preference's rung, marked computing; once any row lands it selects the most eligible one, the preference's rung when landed, else the nearest landed rung. The existing predicate, re-applied per landing, is the whole change.
- The engine's `aversionKn` is already unused (the rungs are fixed λ), so the preference's only effect remains the seat — nothing else to wire or unwire.
- The ETA display order from the earlier decision stays: Fast → Balanced → Fun, fastest first.

## Review — risks before the test

- **The main is index 0, not the fastest.** `_pages` order is computation start order, and the provisional line plus the stage narrate `MAIN_INDEX` — the around rung, normally the slowest. An ETA sort is a **view** over that list, so the main's identity and the drawing stay tied to the main while the table shows fastest-first.
- **The seat re-runs on landing, never on empty pages.** `routeSeatedIndex` already walks outward from the preferred index and picks the nearest landed row; the change is to re-apply it on every landing instead of only at arming and on collapse, so the highlight follows the most eligible row as the ETAs arrive.
- **The delta column reads against the selected row.** Reordering only changes which row is selected, so `routeDeltaSec` keeps working; ties must keep the ladder's natural around→through order so the sort is stable.
- **Collapse happens before the sort.** Two rungs within the dispersion tolerance fold first, so the sorted list can hold fewer than three rows and never compares a duplicate.
- **The engine's `aversionKn` is already dead.** If the test sticks, that parameter and the factory's slider argument are removal candidates — but only after the setting's fate is decided, not now.

## Rename — fast · balanced · fun (2026-10-03)

- The three rows keep their resource ids and every code reference; only the user-facing values change, in both locales.
  - `route_computation_through` → **Fast** / **Rapide** — the λ=0 line: soonest arrival, crawls through slow water.
  - `route_computation_around` → **Fun** / **Plaisir** — the λ=5 line: keeps the boat at speed, longest clock.
  - `route_computation_balanced` → **Balanced** / **Équilibré** — the midpoint.
- Combined with the ETA order, the panel reads **Fast · Balanced · Fun** left to right: the hurry end first, the fun end last, the price of each step in the delta column.

## Review — first implementation (2026-10-03)

Built and green; the relabel, the ETA display order and the per-landing seat landed, but the Ask hop found two defects to fix before this is done:

- **High — the seat reads the wrong preference source. Fixed 2026-10-03:** the preferred rung now resolves from `settings.value.routeSlowWaterAversion`, so the slider reaches the next arming's seat.
- **Medium — the collapse remap parks a folded preference. Fixed 2026-10-03:** the remap lands on `survivorIndex`, with `RouteAcquisitionTest.aCollapsedLandedPreferenceSeatsTheSurvivor()` pinning the 3→2 collapse case.
- **Lows:** the `_desc` removal was correct and unrequested; the left-alone `route_discard_*` and `settings_route_preference_desc` edits are unrelated but carry a latent identical-English-strings inconsistency.

## Outcome

Shipped 2026-10-03 on `feature/dash-map-layout`, nothing committed: the rungs read Fast · Balanced · Fun in both locales, the panel sorts by landed ETA, and the seat re-applies on every landing from the live preference. The two Ask-hop defects are fixed and the regression test is green; the device pass remains unrun.
