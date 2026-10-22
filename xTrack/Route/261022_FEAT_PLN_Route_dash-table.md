# Route — the search dashboard's summary table

Branch `feature/route-dash`. Scope: the three-column table inside the route search panel only —
[`RouteSummaryTable()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:348).
The header, the footer, the rows' ranks and the crown are untouched.

## What is asked

1. Columns 1 and 2 read more compactly — less vertical space between their lines.
2. A second line in column 2 states the route's average speed, all inclusive.
3. Column 3's Speed limits readings stop hugging the cell's inner border, so they look aligned with the
   neighbouring columns whose type is larger.
4. The times of column 3 read `mm:ss min`, rounded to the second — a reading under a minute now prints
   as `0:mm` instead of being dropped.

## Decisions taken

- The average speed sits **second** in column 2: time, speed, distance.
- It prints as `14.7 kn` — one decimal, the app's existing speed reading.
- It is **all inclusive** by construction: distance over the whole allotted time, so slow zones, ramps
  and every cap are already inside it.
- A sub-minute slow entry is printed (`0:40 min`), not dropped; only a reading that rounds to zero
  seconds stays off the line.
- `StatCell`'s metrics do not move — it is shared by the track cards and the live-tracks block; the
  alignment of ask 3 is fixed at this panel's call site.

## The four changes

### 1 · Compact lines in columns 1 and 2

- The gap is the `Text`'s own line height, not padding: both cells carry 4 dp top/bottom today.
- Give those cell texts an explicit tighter `lineHeight` at their 13 sp; the cells' 4 dp inner padding
  is left as it stands — only the gap between the lines tightens.
- The crown's glyph stands at 20 sp and inflates its line box: constrain that glyph's line height to the
  name's, and check on device that the ✓ is not clipped.
- The row is `height(IntrinsicSize.Min)`: its height follows the tallest cell, so this trims what is
  inside the row and only shortens the row if column 3 stops being the tallest — which change 3 also
  works towards.

### 2 · Average speed, second line of column 2

- One formula, one home: a pure `routeAvgSpeedKn(distanceNm, durationSec)` in `RouteOverlay.kt`, read by
  [`RouteRowFigures`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:157) and by
  [`RoutePlan.plannedPaceKn`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:103), so
  the plan-level figure and the row never disagree.
- `RouteRowKind` gains `SPEED`, and [`routeRowLines()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:179)
  returns `DURATION, SPEED, DISTANCE`; a waiting row prints three `--` placeholders, so the pending and
  provisional paths need no separate branch.
- Two new keys in both locales — a `%1$.1f` value format and a `kn` unit — printed through the existing
  [`RouteValueLine`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:521).

### 3 · Column 3 aligned inside its cell

- The measured label column and its centred `:` separator stay — the layout does not change.
- The grid's rows gain the same vertical padding columns 1 and 2 carry (4 dp), so the readings stop
  hugging the cell's inner border beside the larger type.

### 4 · Column 3's times as `mm:ss min`

- [`RouteSlowLimitEntry`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:111) carries
  seconds instead of whole minutes, and
  [`routeSlowLimitEntries()`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:118) rounds
  each to the second and drops only a zero.
- The printer reuses the ETA pair — `route_summary_eta_value_fmt` `%1$d:%2$02d` and
  `route_summary_eta_unit` `min` — so column 2 and column 3 print a time the same way.
- `route_slow_minutes_fmt` becomes unused and is deleted from both locales.
- `routeSlowLimitRows()` and its wrapping rule are untouched.

## Tests

- `RouteSlowLimitTest` is rewritten around seconds: rounding, the `0:mm` reading kept, the zero dropped.
- `RouteAcquisitionTest`'s row-lines test moves to the three-line order and gains the speed line.
- A new unit test pins `routeAvgSpeedKn` (distance over time, knots; zero duration yielding zero) and
  asserts `plannedPaceKn` reads the same helper.

## Owed to the device

- The compacted cells against the crown's 20 sp glyph — no clipped ✓.
- The added speed line against the shared line with the second cell, since a reading is held to one line
  and clips rather than wraps.

## Not in scope

The header's status line, the page dots, the footer's three actions, the rank and stage words, the crown's
rule, and `StatCell` itself.

## Implemented

Shipped on `feature/route-dash` (2026-10-22): columns 1 and 2 compact on one `ROUTE_CELL_LINE_HEIGHT` with
their 4 dp cell padding kept, column 2's speed line seating second via one `routeAvgSpeedKn` read by the
row and the plan, and column 3 keeping its measured label column and centred `:`, one reading per line
with the full `1:05 min` value. Each of the three columns takes an equal `weight(1f)` share, so the
boundaries stay consistent across rows; the two-per-line pairing and its `routeSlowLimitRows` helper are
removed.

- Target files: `RouteOverlay.kt`, `RouteConfirmPanel.kt`, `RouteViewModel.kt`, both `strings.xml`,
  `RouteSlowLimitTest.kt`, `RouteAcquisitionTest.kt`, `StatCell.kt` (doc only).
- Review: the four asks verified; two doc drifts fixed, the `plannedPaceKn` test added, the inline formula
  re-read through the helper.
- Build: `:app:compileDebugKotlin`, the two `ui.map` test classes and `:app:assembleDebug` green.
- Owed to the device: the crown glyph not clipped at the tight line height, and the auto-sized columns
  reading well across portrait and landscape.
