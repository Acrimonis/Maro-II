<!-- scope: feature -->
# Route acquisition panel — portable change brief

Shipped on `feature/route-displayS` (commit `6400d83`, 2026-10-05). Re-apply by hand on a branch where the
route/tree code has since been refactored and the diff will not merge. Every item names a **behaviour, a symbol or
a string key**, never a path or a line, so it can be re-implemented wherever the code now lives.

**The surface** — the route acquisition panel (the dashboard slot's content while a route is being acquired), the
route view model that drives it, the route engine's clock/ETA module and the result it publishes, the app's one
shared reading cell, the drawer live-card's label-width measurer, and string resources in both locales.

## 1. The summary table's pager settles, never resting between pages

- The comparison table is a horizontal pager, one page per route page, whose selection and current page are kept in
  step **two ways**; that two-way sync is where the bug lives.
- **Pager to selection**: observe the pager's **settled** page, not its current page, and only when it differs from
  the selected view index invoke the absolute page selector (item 2).
- **Selection to pager**: skip while the pager is scrolling, and move with an **instant jump** (`scrollToPage`),
  never an animated scroll — a cancelled animation is what strands the table between two pages.
- Retain no off-screen pages (`beyondViewportPageCount = 0`).
- Row taps and the pager's page callback use the absolute selector; the header's ‹ › pair may keep the existing
  relative step.
- Symptom fixed: the table resting between two pages when the panel is created, or after the seat re-seats on a
  landing.

## 2. The route view model gains an absolute page selector

- A function that names a page by its position in the **ETA-ordered view**: map that position through the existing
  ETA-order helper to the underlying page index, clamp it, store it as the selected index and re-sync the choosing
  state — the same tail the relative step already runs.
- Used by the pager, the row tap and (if the arrows are rewired) the arrows. The relative, looping step may remain
  for callers that still need it.

## 3. The third column's delta is bold and first

- The per-route delta text stays the third column's first element and its font weight is forced **bold** for every
  row, selected or not. Nothing below it moves.

## 4. The forced-crossing note leaves the panel

- Remove the third column's per-route note naming the priced zones a forced crossing entered.
- Delete its now-unused string key in **both** locales.
- Keep the separate following-route card's own forced string, and keep the result field that carries the zone names —
  the engine's forced-crossing probe and that card both still read it.

## 5. The engine publishes the time spent per speed limit

- **New immutable type**: a slow-limit entry holding a speed limit in knots, a number of seconds, and a flag marking
  the 300 m zone.
- **New field** on the route engine's success result: a list of those entries, defaulted empty. Mirror it on the
  route plan type, defaulted empty.
- **New pure function** in the route clock/ETA module:
  - Walk the timed legs. A leg is slow when its time exceeds its own distance at the cruising pace.
  - Charge each slow leg's **own seconds** to the water at its midpoint: inside a priced zone, the strictest limit in
    force there (the engine's limit provider); inside the 300 m zone, the configured band limit as its own bucket;
    outside both, a **ramp**.
  - A slow leg on **open water is charged to no entry at all**: the figure counts the time **spent** on a limit's own
    water and nothing else, so a bend's floor and an acceleration are the line's own ramp seconds rather than a
    limit's. (The fold that once joined a ramp to the nearest keyed limit was struck on 2026-10-05 — a device reading
    showed a whole coastal line folding into the band, since every leg of it was slower than the cruise pace.)
  - A zone wins over the band where both hold, so no bucket double-counts.
  - Accumulate by a key of (limit rounded to the nearest half knot, band flag); return the entries **band first, then
    ascending limit**.
- **Engine wiring**: in the fixed-λ solve's tail, after the smoothed-profile timing, call the new function with the
  engine's own limit provider, its zone and band membership predicates, and the configured band limit; hand the
  result to the success builder, which gains the parameter and sets the field.
- **Absent where it cannot be known**: a saved route read back as a plan, and a partial (early-save) plan, leave the
  list empty.

## 6. The panel renders a two-column Speed limits table

- **A pure formatter** over the plan's entries: whole minutes only, a sub-minute entry dropped, the band flag kept —
  one printable entry carrying the label kind (band or limit), the limit in knots and the whole minutes.
- **A small grid composable**, in the third column **below the bold delta**, per page:
  - rows of two weighted cells of the app's shared reading cell;
  - **one label column measured once** across the grid — reuse the drawer live-card's label-width measurer; move it
    into the shared reading cell's own file as internal so both surfaces share one home, and point the drawer at it;
  - the label is the band's own string when the entry is the band, otherwise the limit label format; the value is the
    minutes format;
  - an odd entry count leaves the last cell in its own column (a weighted spacer tail) rather than stretching it.
- No footnote, no header line.

## 7. String resources (both locales)

- **Add**: a limit label format (`%1$.0f kn` / `zone %1$.0f kn`), a band label (`300M`), a minutes format (`%1$d min`),
  a fold separator (` | `), a settled-status one-route line (`1 route @ %1$s`) and a settled-status many-route line
  (`%1$d routes @ %2$s`).
- **Remove**: the forced-crossing note key, the collapsed-note key, and any earlier slow-limits line-format keys this
  supersedes.
- The settled-status lines format the pace through the existing cruising-speed format key, so no second speed format
  is added.

## 8. A folded row names its rungs

- **The route page type** gains a list of the rung description resource ids the page stands for, empty until a fold.
- **In the collapse branch** of the view model's update handler, where a rung within the collapse tolerance folds into
  an already-landed survivor: the survivor's list is its own description id (or the names it has already gathered) with
  the dropped page's description id appended; the duplicate page is still removed exactly as before.
- **In the panel's first column**: a folded row prints the joined names, the survivor's first, using the fold
  separator; a rule prints its single description as before. Delete the old collapsed-note rendering.

## 9. The rung names

- The acquisition's three rung labels become **Coastal**, **Balanced**, **Around** (fr: Côtier, Équilibré, Autour).
- The settings' own hurry↔fun labels stay untouched — the two vocabularies are deliberately apart.

## 10. The settled status line

- The panel gains the mode's **cruising pace** as a parameter; both of its call sites pass the pace the mode runs at.
- The header status: the acquiring word while the search runs, the committed word once a route is taken, and
  otherwise — the search settled — the settled-status line: `1 route @ <pace>` when one page stands, else
  `N routes @ <pace>`, N the pages standing.

## Tests to add or extend

- The per-limit attribution: the ramp folds into the limit it serves; a zone beats the band where both hold; an
  ordinary route reports nothing.
- The formatter: whole minutes, a sub-minute entry dropped, the band flag kept.
- The fold: the survivor carries both rungs' description ids.

## How to verify

- Build the debug APK.
- Run the unit suites covering the attribution, the formatter and the acquisition fold.

## Decisions already made — do not re-open

- The slow time is the route's **own seconds**, not its excess over the pace, and each limit's figure **counts its
  ramps**.
- The bucket key is the **limit value**, not the zone's name; the 300 m zone is its own bucket.
- The table lives in the **third column below the delta**, per page — not full width below the comparison table.
- There is **no footnote**.
- The acquisition's rung names and the settings' hurry↔fun labels stay **apart**.

## Record

- The shipped plan of record, its revision sections, and the UI component guidelines' route-panel section carry the
  same decisions on the branch this was built on.

## Outcome

**Shipped 2026-10-05** on `feature/route-displayS` (`6400d83`) as a portable re-application brief: the pager settles, the absolute page selector, the bold delta, the panel's forced-crossing note gone, the per-limit attribution and its two-column Speed limits table below the delta, and the fold naming its rungs. Its §9 rung names (Coastal/Balanced/Around) and §10 settled status line are **superseded** by the shipped Fast · Best · Fun ladder (R95) and the 2026-10-08 stage-and-number status (`<stage> #<n>`), so neither is the current vocabulary. The per-limit attribution's own contract was re-settled afterwards by the band-time plan.
