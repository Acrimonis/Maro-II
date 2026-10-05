<!-- scope: feature -->
# 261005_FEAT_PLN_Route_acquisition-panel-tweaks

Status: in design — the acquisition panel's report takes four changes; nothing is implemented. Branch:
`feature/route-displayS`.

## Purpose

The acquisition's summary table ([`RouteConfirmationPanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:94))
carries the route comparison. The user's own list (2026-10-05), in the panel's words:

1. **The pager sometimes rests between two pages** when the panel is created.
2. **The forced-crossing note is useless** — `Forced crossing of …` leaves the report.
3. **A useful figure replaces it** — the time the route spends slowed, summed per regulated speed limit, with the
   300 m zone as its own entry: `Speed limits: 5 min in the 300 m zone, 10 min at 5 kn, 6 min at 10 kn`.
4. **The delta becomes the third column's first line and reads bold.**

## Review — the plan challenged

- **The pager's symptom is a cancelled animation, not only a feedback loop.** The panel syncs selection and pager
  both ways, and an `animateScrollToPage` cancelled by a seat change strands the pager at a fractional offset. The
  programmatic path must jump, never animate, and the selection must follow the pager only once it has settled.
- **The line is long for a one-third column.** It wraps and can unbalance the rows' heights; the ordered shape
  stands, header first, and the device pass decides whether it must move below the table into `PlanNotes`.
- **The attribution must be written down, not implied.** A leg is credited to the enforced limit in force at its
  midpoint; a ramp run credits the limit it enters and the one it leaves, so a zone's figure counts its ramp up
  and down; a leg inside a priced zone credits the zone, not the band, so the buckets stay disjoint and sum to the
  whole slow time.
- **Empty and sub-minute cases.** Whole minutes only; an entry under a minute is dropped and the line is omitted
  when nothing slowed the route, so it never prints a zero.
- **A dead string may already sit beside it.** `route_comparison_fmt` looks unused next to the `less`/`more` pair;
  the removal pass verifies it and deletes it if dead.
- **Directive trace — the five covered classes.** None ran unasked this session: no dependency added, no
  machine-shaped data file opened, work started only on the user's word, the device untouched, and every code
  claim followed a read.

## Steps

1. **The pager settles on one page** — [`RouteTablePager`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:290):
   read `pagerState.settledPage` for pager → selection, guard selection → pager on `!isScrollInProgress`, and
   sync with `scrollToPage` (never `animateScrollToPage`); add `RouteViewModel.selectPage(viewIndex)` so a page is
   set absolutely, and route the row tap and the ‹ › pair through it.
2. **The forced-crossing note leaves** — delete the block at
   [`RouteConfirmPanel.kt:446`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:446) and the
   now-dead `route_forced_crossing` string from both locales; keep `route_trip_forced`, the dashboard card and the
   [`forcedCrossingZoneNames`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:75) field.
3. **The delta is bold** — [`RouteConfirmPanel.kt:438`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:438)
   keeps its first position and takes a forced `FontWeight.Bold`.
4. **The engine sums the slow time** — a pass over the timed legs attributes each leg's seconds to its enforced
   limit, folds each ramp into the limit it serves, and keeps the 300 m zone as its own bucket; the result is
   published on [`RouteResult.Success`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:39) as
   a list of (limit, seconds) and mirrored on [`RoutePlan`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:62).
5. **The panel prints it** — a pure formatter in [`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:74)
   builds the one line; the panel renders it under the bold delta with the `*` footnote.
6. **Tests** — the per-limit attribution (the ramp fold and the zone-beats-band rule) and the formatter.
7. **The record** — §5.8 of [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:964) now
   carries the changed third column; the feature file and hydration follow at `#bake`.

## Target files

- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the pager, the third column, the delta
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `selectPage`, the `RoutePlan` field
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — the line formatter and the plan inverses
- `app/src/main/java/ykws/android/maro/data/model/RouteResult.kt` — the per-limit list on `Success`
- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` and `spatial/avoid/RouteEta.kt` — the attribution pass
- `app/src/main/java/ykws/android/maro/spatial/avoid/ZoneGeometry.kt` — the band/zone membership read, reused
- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml` — the new strings, both locales
- `docs/ui-component-guidelines.md` — §5.8, updated

## Strings (planned)

- `route_slow_limits` — the header, `Speed limits:` / `Limites de vitesse :`
- `route_slow_limit_entry_fmt` — `%1$s at %2$s` (`10 min at 5 kn`)
- `route_slow_band_entry_fmt` — `%1$s in the 300 m zone`
- `route_slow_limits_note` — the `*` footnote: the figure counts the ramp into and out of each limit
- removed: `route_forced_crossing`; verified-dead: `route_comparison_fmt`

## Revision — the Speed limits render as a two-column table (2026-10-05, the user's word)

The one comma-joined line becomes a **two-column table of the shared reading cell**
([`StatCell`](../../app/src/main/java/ykws/android/maro/ui/components/StatCell.kt:128)) — the very shape the
drawer's live-tracks block wears — one entry per limit, band first then the limits ascending.

- **The cell, not a bespoke row** — `StatCell(label, value)` with the table's one measured label column
  (`rememberLabelColumnWidth`, extracted from [`MenuDrawerOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:717)
  into `StatCell.kt` so both surfaces share one home), laid out in rows of two `Box(Modifier.weight(1f))`.
- **The order is the engine's own** — the 300 m zone first, then the regulated limits ascending, so the grid reads
  `300M · 5 min`, `3 kn · 10 min`, `10 kn · 3 min` row-major.
- **The labels are short** — `300M` for the band and `%1$.0f kn` for a limit (the user's word, 2026-10-05, dropping
  the word *zone*); the values keep their unit (`%1$d min`, a sub-minute entry dropped).
- **The place is the third column, below the bold delta** — per page, so each route's row carries its own figures
  (the user's word, 2026-10-05, walking back the full-width placement below the comparison table).
- **No footnote** — the muted "the time counts the slow…" line is dropped entirely (the user's word); the figures
  stand alone.
- The strings change with it: `route_slow_limits`, `route_slow_limit_kn_fmt`, `route_slow_limit_entry_fmt`,
  `route_slow_band_entry_fmt` and `route_slow_limits_note` leave; `route_slow_limit_label_fmt` (`%1$.0f kn`),
  `route_slow_band_label` (`300M`) and `route_slow_minutes_fmt` (`%1$d min`) stand, both locales.
- **Applied 2026-10-05 on `feature/route-displayS`**: the two labels were still carrying the long form
  (`300 m zone`, `%1$.0f kn zone`); both now read `300M` and `%1$.0f kn`, the comments above them follow, and
  `route_slow_minutes_fmt` keeps `%1$d min`. No deviation from this section — `apk-build.bat` succeeded and the
  two suites pass.

## Revision 2 — the merged rungs name themselves (2026-10-05, the user's word)

When two rungs fold (D14), the survivor's row carries the **names of every rung it stands for** instead of the
`route_collapsed_note` (*Every preference gives this same line*), which leaves the strings.

- **The names are the acquisition's own rung labels** — `route_rung_around` (*Around slow water*),
  `route_rung_balanced` (*Balanced*) and `route_rung_through` (*Through slow water*) — joined by one separator
  string, the survivor's name first then each folded rung in fold order.
- **The page carries them** — `RoutePage` gains `foldedDescriptionResIds: List<Int>`, empty until a fold; the
  collapse branch in `RouteViewModel.onUpdate` appends the dropped page's `descriptionResId` to the survivor's
  list, so the name is not lost when the duplicate page goes.
- **The panel reads the list** — column 1 prints the joined names for a collapsed page (falling back to its single
  `descriptionResId`), and the `route_collapsed_note` rendering and its string are deleted in both locales.
- A test pins the fold carrying both names onto the survivor.
- **Applied 2026-10-05 on `feature/route-displayS`**: `RoutePage.foldedDescriptionResIds`, the collapse
  branch carrying the dropped rung's `descriptionResId` onto the survivor, the panel's column 1 printing the
  joined names, and `route_rung_names_sep` (` · `) in both locales with `route_collapsed_note` deleted.
  No deviation from this section; two implementation notes — the labels are resolved with an explicit loop
  because `joinToString`'s transform is not a `@Composable` context, and the test engine's
  `descriptionResId` stub now numbers its rungs (1, 2, …) so the fold's pin tells the survivor's own label
  from the dropped one. `apk-build.bat` succeeded and `RouteAcquisitionTest`, `RouteSlowLimitTest` and
  `SlowTimeByLimitTest` pass.

## Revision 3 — the rungs renamed, and a settled-status line (2026-10-05, the user's word)

- **The rungs are renamed** — `route_rung_around` reads *Around*, `route_rung_balanced` *Balanced* and
  `route_rung_through` *Coastal* (fr: *Autour*, *Équilibré*, *Côtier*). The settings' own hurry↔fun labels
  (`route_computation_*`, Fast · Balanced · Fun) keep their vocabulary, R95 keeping the two apart.
- **The fold's separator is ` | `** — `route_rung_names_sep` reads ` | `, so a folded row lists `Around | Balanced`.
- **A settled-status line** — where the panel's status currently reads nothing once the search stops, it reads
  `<N> routes @ <pace> kn` (`1 route @ …` in the singular), N the pages standing and the pace the cruising speed the
  mode runs at, formatted through `settings_route_pace_value_fmt`; the panel gains a `paceKn` parameter and both
  `MapScreen` call sites pass `routePaceKn`. The committed and acquiring words are unchanged.
- Strings, both locales: `route_status_done_one` (`1 route @ %1$s`) and `route_status_done_many` (`%1$d routes @ %2$s`).
- **Applied 2026-10-05 on `feature/route-displayS`**: the three rung labels renamed in both locales
  (`Around`/`Autour`, `Balanced`/`Équilibré`, `Coastal`/`Côtier`); their comments and the settings block's
  cross-reference followed so no line still describes the old slow-water wording; `route_rung_names_sep`
  reads ` | `; `RouteConfirmationPanel` takes `paceKn: Double` and its settled branch prints the status line;
  `route_status_done_one`/`_many` stand in both locales; both `MapScreen` call sites pass
  `paceKn = routePaceKn`. No deviation — `routePaceKn` is the mode's cruising speed
  (`AppConfig.routeFreeWaterPaceKn`, *the pace the trip figure plans at*), the same the panel's trip figure
  reads. `apk-build.bat` was BUILD SUCCESSFUL and `RouteAcquisitionTest`, `RouteSlowLimitTest` and
  `SlowTimeByLimitTest` pass.

## Owed device pass

- The pager settling through arming and each landing, and on the panel's creation.
- The Speed limits table's two columns inside the third column's own width.
- A folded row's joined rung names at that same width, and the settled-status line's own fit.

## Open points

- None blocking. The line's placement moves into `PlanNotes` only if the device pass shows the wrap unreadable.

## Outcome

Shipped as planned on `feature/route-displayS` (2026-10-05):

- **The pager settles on one page** — pager → selection on `settledPage`, selection → pager guarded on
  `!isScrollInProgress` and jumping with `scrollToPage` (never `animateScrollToPage`), and a new absolute
  `RouteViewModel.selectPage` used by the pager, the row tap and the ‹ › pair's call sites — the cancelled
  animation that stranded the table between pages is gone.
- **The forced-crossing note left the panel**, and the dead `route_forced_crossing` and `route_comparison_fmt`
  strings left both locales; `route_trip_forced`, the dashboard card and the `forcedCrossingZoneNames` field
  stay, the engine and the card still reading them.
- **The delta is forced bold** as column 3's first line.
- **The engine sums the slow seconds per speed limit** — `slowTimeByLimit` in `RouteEta.kt`, each slow leg
  charged to the limit at its midpoint, ramps folded to the nearest limit (forward winning a tie), a priced
  zone beating the band — published on `RouteResult.Success` and mirrored on `RoutePlan`; `routeSlowLimitEntries`
  prints whole minutes and drops a sub-minute entry.
- **The Speed limits render as a two-column reading table** in the third column, below the bold delta, per route,
  band first then the limits ascending (the Revision section carries the two passes that settled the placement).

Deviations: none from the plan. `apk-build.bat` was BUILD SUCCESSFUL and the new suites `SlowTimeByLimitTest`
and `RouteSlowLimitTest` pass (5 tests). Owed to the user: the device pass — the pager settling through arming
and each landing, and the Speed limits table's two columns at the narrowest panel.

**Revised the same day, twice** (the user's word, 2026-10-05): the Speed limits line first became a two-column
table of reading cells, and then moved from below the comparison table into the third column beneath the delta,
per route, with the footnote dropped — the Revision section above is the record of both passes.
