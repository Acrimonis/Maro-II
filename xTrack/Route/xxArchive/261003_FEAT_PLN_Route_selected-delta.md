# FEAT_PLN Route — the acquisition's seated selection and the table's delta

**Date:** 2026-10-03 · **Feature:** Route · **Status:** in design — implemented-ness is read from the
`## Implemented` pointer in [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md), never from this line.

## 1. What changes

The acquisition panel's **third column** carries the span by which the page's route differs from the
**selected route** — how much more or less time it takes — instead of the span against the main rung, and
the selection itself is **seated on data** the moment any page has a computed line (§10), so the basis the
column measures against is a row that actually holds a route.
The middle column keeps each page's own Dist · ETA, so the third column answers one question only: *how
does this one compare with the one I have picked?* The forced-crossing note in the same column is
untouched.

## 2. Why

- The column's basis is the **main** today: [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:325)
  reads `mainDurationSec = pages.firstOrNull()?.plan?.durationSec`, and its private
  [`routeDeltaText`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:494) prints
  `page.durationSec - main` for every page but the first.
- The **selected** route is what `Select route` and `Save to track` act on, so it is the only basis a
  reader can act upon; a delta against a rung nobody chose says nothing about the choice in hand.
- The corpus already describes it that way: the comment over the comparison family at
  [`strings.xml`](../../app/src/main/res/values/strings.xml:705) calls it *the flow's arithmetic of each
  route's duration against the selected one*, so the panel has drifted from its own documented intent.

## 3. The rule and its one home

`RouteOverlay.kt` is the feature's pure-rules home, and it already carries a declaration written for
this very rule:

```kotlin
routeDeltaSec(
    pageDurationSec: Double?,
    selectedDurationSec: Double?,
    isSelected: Boolean
): Double?
```

- `null` on the selected page itself, where **either** duration has not landed, or on a tie;
- otherwise `pageDurationSec - selectedDurationSec` — **positive means slower than the selection**.

The selection is passed in as a flag rather than derived from two indices: the caller already knows which
page it is drawing, and one boolean cannot disagree with itself the way two index arguments can.

**The reshape adds nothing and removes dead weight**: [`RouteComparison`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:71)
and [`routeComparison`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:77) are referenced
nowhere in `app/src` — main or test — so the live rule takes the home the dead declaration occupies and no
second arithmetic is introduced. The panel then keeps the **formatting** alone: `routeSpanText(abs(delta))`
plus the `less` / `more` pair.

## 4. The panel

[`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:380):

- the reference becomes the **selected page's** duration, read through the table's **own clamped
  `selectedIndex` parameter** — the index its caller already clamped against the page count, never a fresh
  read that could step outside the list — instead of `pages.firstOrNull()`;
- `routeDeltaText`'s guard changes from *index 0 says nothing* to *the selected page says nothing about
  itself*;
- both KDocs follow, since both currently state the main as the basis.

## 5. The edges, pinned

- A page whose plan has not landed shows nothing, plan-less or not.
- Where the **selection** has no plan yet, the whole column falls silent — there is no reference to
  measure against, and §10's seat makes that window as short as the first landing.
- **The basis shows at once**, because arming seats the selection on the cursor's rung rather than on the
  main, so a cursor that picks balanced or through changes the column's reading from the first frame.
- The **main** rung now shows a delta whenever it is not the selection; its own cell was the one that
  used to be silent.
- Tapping a row selects it, so every tap re-bases the other deltas — the change's accepted cost.

## 6. Against the change

Re-basing on the selection costs the main rung its role as a fixed yardstick: comparing two unselected
rungs now means tapping one of them first, and since a tap also selects, a tap meant only to *look*
re-bases every other delta. The counter is that the chosen route is the only one the reader can act on,
and the middle column already lets two durations be read side by side by paging.

**The option this rejects** — keep the main as the yardstick and surface the selection's own delta in the
header, beside the stage. It loses because the reader compares the rows they may take, and one figure in
the header says how the selection stands without saying how every *other* row stands against it.

## 7. The doc changes, and where each landed

- **The epic pointer:** one line under the epic's `## Implemented` — the acquisition table's delta
  re-based on the selected route — with this plan's pointer beside it, since the change shipped rather
  than sitting owed under `## Delta` → **Owed builds**.
- **R90's clause:** [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) now reads *and a candidate's delta against
  the selected route with the forced-crossing note*, landed with the code.
- **The `Body` row of `§5.8 Route Panel`** in [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:874):
  it named **no basis** at all, so the change **added** *against the selected route* rather than
  replacing an *against the main* the row never read; §5.8 reads true now.
- The strings' own comment needed no edit: it already reads *against the selected one*, so this change
  makes the comment true rather than false — though it still sits over the dead `route_comparison_fmt`,
  which §9 leaves alone.

## 8. Verification

- Cases for the pure rule, in the acquisition's own suite (`RouteAcquisitionTest`, its subject confirmed
  when the cases are added), with `RoutePlanTest` the fallback home should that suite prove to cover the
  algorithm rather than the acquisition's own state: a slower page, a faster page, the selection itself,
  a page without a plan, and a tie.
- The tie cases matter less than they look: the ladder's tolerance-based collapse already drops a rung
  that lands within the collapse distance of another, so a zero delta is rare by construction.
- `apk-build.bat` green, and the route suites stay green.
- **Owed to the user:** the panel's look on a device — no JVM test reaches the composable, so the
  third column's wording and its silence on the selected page are a device reading.

## 9. Out of scope

- [`route_comparison_fmt`](../../app/src/main/res/values/strings.xml:706), referenced nowhere in either
  locale — a deletion of its own, decided separately, since it sits in the family this plan edits but
  nothing about it is wrong.
- The middle column, the paging, the selection's taken-choice face, and the forced-crossing note.
- The sweep of any **document** that still names `routeComparison` as live — the dead-declaration check
  behind §3 ran over `app/src` alone, so an archived Outcome or the epic's prose is the implementer's to
  sweep.

## 10. The seat — added 2026-10-03

The requirement: the selection updates **as soon as a route has computed data**, and it takes **the most
relevant** one. Settled with the user on 2026-10-03: **the standing selection** — the page the selection
already holds — is what *most relevant* means, so a step onto an already-landed row stands while only a
step onto a pending row is undone; and the seat **always moves to a landed row** — a step cannot park on
an empty one.

- **Today** — [`arm`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:432) seats the
  selection on the Driving-preference cursor's rung while **every page still holds a null plan**, and
  [`onUpdate`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:445) touches the
  selection only when a landing **collapses** into a neighbour (D15): an ordinary landing leaves it where
  it was. So the selection can stand on a row with no line while another row already draws one — which
  under §1 is exactly a silent third column.
- **The rule** — one pure predicate in `RouteOverlay.kt` beside `routeRungIndex`:
  `routeSeatedIndex(hasPlan: List<Boolean>, preferredIndex: Int): Int` — the preferred page when it holds a
  plan; otherwise the page **holding a plan** that lies nearest the preferred one; otherwise the preferred
  index unchanged.
- **The preference** — the **standing selection**: the seat re-reads on every landing with the page the
  selection already holds as its preference, so a landed row keeps the seat and only a pending one steps
  it to the nearest landed neighbour. The Driving-preference cursor's rung keeps its one home
  (`routeRungIndex`) at the arming alone, where it declares the initial preference before any data lands.
- **The trigger** — arming goes through the same predicate, so D12's inline test disappears; and `onUpdate`
  re-seats after every landing, so the seat follows the first **data** rather than the first
  **declaration**. A collapse's index bookkeeping (D15) runs first, and the predicate seats on the
  survivors afterwards.
- **A step cannot park on an empty row** — a step onto a page whose plan has not landed is taken back by
  the next landing, so no state survives in which the selection holds nothing while a line stands beside
  it; the cost is named rather than hidden, since a step meant only to look at a pending rung is undone.
- **What it must not break** — the stored match's single settled page (R83) carries a plan from the start,
  so there the predicate is the identity; and the fan's `Route auto` child (R80) takes **index 0** by
  naming it (`selectMainRoute()`), so the page the seat followed onto never moves what the one-shot
  selects. `RouteState.Choosing.plan` is the **main's** (index 0), the settled answer the auto-pick waits
  on — never the seat's.
- **Verification** — the predicate's cases: the preferred row landed; the preferred row pending with a
  neighbour landed; nothing landed yet; and the single-page set. Then `apk-build.bat` and the suites.

## Outcome

**Shipped 2026-10-03.** The panel's third column now reads each page's delta against the **selected** route — `routeDeltaSec` took the one home the dead `RouteComparison` occupied — and the seat re-seats on data on every landing through `routeSeatedIndex`, so the basis the column measures against is always a row that holds a line. R90's clause and §5.8 of `docs/ui-component-guidelines.md` were rewritten to read *against the selected route*; the device look stays the user's own.
