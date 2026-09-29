# Plan — the dropdown's list as a snapped wheel

**Feature:** Ui_General · **Date:** 2026-09-29 · **Status:** implemented — the wheel is the popup's body, `WheelPolicy` unit-tested, the doctrine amended (§6); the device pass is owed

## 1. The requirement

Replace the dropdown popup's **body** — the scrolling list of `PopupRow`s — with a wheel: a snapped column
whose centre slot is the choice, with a clear mark on the selection and the unselected entries left plain.
Everything outside the body stays as it is: the box on the bars' base, the popup anchored at the box's
bottom-left, its width taken from the box, and `PopupSurface`'s rim, container, corner, shadow and 12dp
inset. One deliberate difference from today: the body's height is **bound** — a fixed band of rows rather
than a content-wrapped list — so the wheel is a known size wherever it opens.

## 2. Candidates evaluated

| # | Body | Snapping | Selection shown by | vs the family | Verdict |
|---|------|----------|--------------------|---------------|---------|
| A | `LazyColumn` + snapping, fixed centre band | exact, native | band + one bold label | row diverges from `PopupRow` | viable |
| B | same scroll mechanics | exact, native | the family's own `PopupRow` (✓, accent, SemiBold) | one row idiom kept | weakest wheel feel |
| C | hand-drawn wheel, type scaled and layered by distance | custom | scale and opacity | foreign to a flat, token-coloured drawer | rejected |
| D | **A's mechanics, the band wearing the bars' taken-choice face** — `ui.select.container` behind a 1dp `uiAccent` rule — with one bold centre label | exact, native | band = the app's own taken face | reuses the app's vocabulary | **chosen** |
| E | wheel where the list is long, today's rows where it is short | exact, native | both | two shapes at one control | rejected |

- **Why C is out**: the drawer is flat and token-coloured, and it has never worn scaled or rotated type;
  a perspective wheel also fights font scale, where the app is careful about it.
- **Why E is out**: the requirement is to *replace* the popup, and a length threshold would leave two
  bodies to keep in step inside one control — the exact drift the popup family was just unified to end.

## 3. The chosen design (D)

### 3.1 Mechanics

- **`LazyColumn`, snapped to the centre** — `rememberSnapFlingBehavior(lazyListState, SnapPosition.Center)`,
  or its `SnapLayoutInfoProvider(lazyListState, SnapPosition.Center)` form if the two-argument overload is
  not in this BOM's surface — `SnapPosition.Center` is the lambda `{ _, _ -> 0.5f }`, so the same thing can
  be written as a provider of our own if neither the overload nor the constant is present. The plain
  overload is **not** enough: it snaps an item's *edge* to a viewport edge (`SnapPosition.Start`), which
  would leave the band naming no row. The library's sources are not in the tree — only `foundation.aar` is
  cached — so **the first implementation step was a compiling probe of that one call**, run on 2026-09-29:
  `rememberSnapFlingBehavior(state, SnapPosition.Center)` compiled against this BOM's foundation with no
  `@OptIn` required, so the centre variant is the form to write and the lambda fallback stays unneeded. The
  probe file was deleted once it had answered, its finding being this line.
- **Why still `LazyColumn`** — not for laziness, which is irrelevant at five rows, but because it is the
  first-party carrier of both the snap and the `LazyListState` the band reads. A `Column` plus
  `verticalScroll` has no first-party snapping overload for `ScrollState`, so it would force a hand-written
  provider *and* a `ScrollState.value`-derived index — more bespoke code, not less. The objection: once the
  wheel owns its scroll anyway (§3.1, next), laziness is pure overhead, though nil at this size.
- **The wheel owns the only scroll.** `PopupSurface` scrolls its own column today, so a wheel inside it would
  nest two same-axis scrollers — two scroll semantics nodes, and the outer one able to scroll the band's row
  out from under the band. The surface therefore needs a **non-scrolling path** (a `scrollable = false`
  parameter, or a sibling entry point) that the wheel uses; the wheel is the single scroll container.
- **The slot height is the bars' 38dp, measured as a floor.** `WHEEL_ITEM_DP = 38` is the user's figure at
  font scale 1.0 — what a bar cell stands at, `18dp` of glyph plus the cells' 10dp padding twice — and the
  wheel draws no glyph of its own, so the slot is `max(38.dp, the label's measured line height + 2 ×
  BAR_CELL_PAD_VERTICAL_DP)`, the label measured through `rememberTextMeasurer()` on the style the rows
  draw. That keeps the chosen figure exactly at scale 1.0 while letting a large font scale push the slot up,
  as the bars grow. The pure policy takes the measured figure as an input, keeping it free of Compose.
- **Vertical content padding** `top = bottom = slot × (slots − 1) / 2`, a whole multiple of the slot, so the
  first and last entries can reach the centre. With centre snapping this needs no other adjustment: the
  viewport centre is `(visible/2) × slot`, so a settled scroll of `k × slot` puts item `k`'s centre there.
- **The slot count is the content's own, held between three and five** (the user's word, 2026-09-29): five
  slots at five entries or more, **four at four**, three at three or fewer, with the height bound only ever
  cutting it down, floored at one. The odd-count rule this plan first imposed was unnecessary — with a centre
  snap an entry always sits under the band whatever the count, and the band is one slot tall, so it covers
  exactly that entry. An even count does demand **half-slot end padding**, `slot × (slots − 1) / 2` in
  floats, where the first implementation divided integers and would have left a four-slot wheel's ends
  unable to reach the centre.
- **The band's row is read from the layout.** `wheelCentredIndex` as first drafted took
  `(firstVisibleItemIndex, firstVisibleItemScrollOffset)`, which under content padding both omits the
  leading padding — the centred row is not `first + 0` — and is not injective over the first padded
  positions: one pair can describe two different band rows. The helper therefore takes the visible items'
  own bounds — `data class WheelItemBounds(val index: Int, val offsetPx: Int, val sizePx: Int)`, mapped from
  `layoutInfo.visibleItemsInfo` — and returns the index whose centre is nearest `viewportStartOffset +
  viewportSize/2`. Pure and unit-tested; the convention itself is settled by a test against a real
  `LazyListState`, and this is the join the roller's failure lived in.
- **It opens on the current selection** — one `LaunchedEffect` scrolls to the selected index on first
  composition, so the band starts on the taken entry instead of on the first one. Without it the band names
  an entry the box does not show.

### 3.2 The band, and what marks the choice

- The band is a slot-height background layer pinned at the wheel's centre, wearing **the bars'
  taken-choice face**: `ui.select.container` as its fill and a 1dp `uiAccent` rule above and below — the
  same pair `SegmentedRow`'s selected cell and `MultiSelectRow`'s on half draw, so the centre reads as *the
  taken choice* in the app's own vocabulary.
- **Paint order is stated, not implied**: the band is the *bottom* layer of the wheel's `Box`, the scrolling
  column above it, so the centred row's label draws over the band rather than under it.
- **`ui.select.container` keeps its own context**: the wheel sits on the family's `PopupSectionCard`, the
  `uiCardBackground` group card every `PopupRow` in the family stands on — which is exactly where the bars
  wear that fill. The card's 12dp inset is symmetric, so the centre holds, and its rounded corners sit at
  the card's top and bottom edges, well clear of a band in the middle: only a **one-slot** wheel would bring
  the band's rules near them.
- **Only the centred label is bold**; the rest are not (the user's word), all in `uiTextPrimary` at the
  family's 15sp, centred on the wheel's axis where the family's list rows are left aligned with a check box.
- The family's `✓` is **not** drawn in the wheel: the band is the mark. That is a third row idiom in a
  family unified a day earlier, and it is the one consistency cost this design accepts knowingly — to be
  written into §2.10 as a named divergence.

### 3.3 Interaction, and why tap-first

- **A row tap chooses it**, exactly as today's row tap does, and the popup closes — so the three call sites
  keep their arguments and their meaning, and nothing about the flow changes.
- The **drag scrolls and snaps**; the band shows what a tap would choose. The drag never commits by itself.
- That is the deliberate answer to the recorded fault: the roller's marked entry and its committed entry
  disagreed because the drag was what committed, and the trace that would have said which of its two
  mechanisms was at fault was cut short. A wheel whose **tap** commits cannot reproduce that — though the
  root cause was never established, so the honest claim is that the commit is *guarded*, not that the shape
  is proven unrelated.
- The cost of the choice, named: a user who drags to an entry and lifts the finger sees it in the band
  without it being chosen, and must tap. The review's strongest objection is not soundness but value — a
  wheel whose band commits nothing behaves like a scrolling list. The user's word was tap-first; the
  alternative is commit-on-snap, one `snapshotFlow` on `isScrollInProgress` away, and it re-enters the
  roller's risk knowingly.

### 3.4 Accessibility

- Every row stays tappable, so the control keeps a non-drag path — the gap §2.15 records against a
  drag-only shape.
- Rows carry their taken state and a role explicitly: `PopupRow` sets only `clickable`, so the wheel's own
  row states `selected` and `Role.RadioButton` itself rather than relying on the family's row.
- **One scroll container** (the nested-scroll fix above) is what keeps the band's position and the
  announced row from disagreeing; with a second scroller above them, a reader could latch on the outer one.
- The band is decoration and carries no semantics; the box above keeps the call site's `accessibleName` and
  `Role.DropdownList`.

### 3.5 Where it lives

- `ui/components/WheelPolicy.kt` — the pure arithmetic: the slot count, the quantised height, the snap
  padding and the centred-row helper, taking measured figures as inputs and holding no Compose.
- `ui/components/DropdownWheel.kt` — the body: the `LazyColumn`, the band layer, the rows.
- `DropdownRow.kt` — the body swap inside the popup it already opens; nothing about the box, the anchor or
  the surface moves.
- `PopupFamily.kt` — the non-scrolling path the wheel needs from `PopupSurface`.
- Tests: `WheelPolicyTest` beside its subject — which is the existing convention, not a new one:
  `ListPopupHeightTest` already sits in `app/src/test/java/ykws/android/maro/ui/components/`.

### 3.6 Docs in play

- **§2.12** — the list is a wheel and a tap is what chooses; its content sentence ("the content is the
  family's own") must be **amended**, not appended to, since the wheel's rows are not `PopupRow`.
- **§2.10** — the wheel's row and band as the family's *list* variant beside `PopupRow`, the named
  divergence in the mark, and the three-level diagram amended for the wheel's levels.
- **§2.15** — the retirement note gains this re-entry and **is where the drag-only warning lives**; the
  first draft cited a §5.10 that does not exist. It must also say the roller's root cause was never
  established, so the wheel re-enters the gesture and guards the commit rather than being proven unrelated.
- **R70** stays retired and superseded: its shape was one visible row stepped on a drag, not a three-to-five
  row wheel whose commit is a tap, so re-opening it would be wrong.

### 3.7 Risks, and what only a device pass can answer

- **The snap's API shape** at this BOM — the centre variant and any opt-in are compile-time checks, since no
  library source is in the tree.
- **The commit**, as §3.3 sets it out: tap-first removes the recorded fault from the mechanism; whether the
  band reads as *pending* rather than *chosen* is a look only the device settles.
- **Font scale**: the measured slot is the fix, and whether it keeps the band's centre exact at a large
  scale is a device check.
- **The bound** is now quantised to odd counts, so the band always has a row to name; how a three-slot wheel
  reads in a short landscape panel is a device look.
- **The divergence**: a wheel's rows are not `PopupRow` — a third idiom to keep honest in §2.10.
- **The one-entry case**: answered mechanically by the three-slot rule; whether a wheel showing a single row
  reads better as a plain row stays a device judgement.

## 4. Settled with the user (2026-09-29)

- **Commit rule**: **tap-first** — a tap chooses and closes; the drag only scrolls and snaps.
- **Row height**: the bars' own cell height, **38dp at scale 1.0**, measured from the label so it grows as
  the bars do.
- **Slot count**: the content's own, held between three and five — five at five entries or more, **four at
  four**, three at three or fewer — with the height bound only cutting it down.
- **The band's face**: the bars' taken choice — `ui.select.container` behind 1dp `uiAccent` rules.
- **End treatment**: nothing at the wheel's ends.

## 5. The independent review, and what it changed (2026-09-29)

Verdict **revise**, with the premise surviving and four mechanisms failing as written. Folded above, each
against its finding:

- **Blocking — the snap does not centre.** Plain `rememberSnapFlingBehavior` aligns item edges
  (`SnapPosition.Start`), so the band would name no row; the centre variant is now §3.1's first line.
- **Blocking — the wheel never opened on the selection.** One `LaunchedEffect` scroll now does, §3.1.
- **High — two nested scrollers.** The wheel now owns the only scroll, which needs a non-scrolling path from
  `PopupSurface`, §3.1 and §3.5.
- **High — the bound broke the centre invariant.** The height is now quantised to an odd row count, §3.1.
- **High — the index function could name two rows for one pair.** It now reads the visible items' own
  bounds, §3.1.
- **Medium — the band's context and the unnamed card.** The group card stays, and the clipping it causes is
  named, §3.2.
- **Medium — n = 3 contradicted itself.** Pinned: three entries open five slots, §4.
- **Medium — no §5.10 exists.** The drag-only warning lives in §2.15, §3.6.
- **Medium — a fixed 38dp row against font scale.** The slot is measured, §3.1.
- **Low — three record items**: the test beside its subject is the existing convention (`ListPopupHeightTest`
  is already there); the band's paint order is now stated; §2.12 and §2.10 are to be amended rather than
  appended.
- **Left as unverifiable from this tree**: the padding convention of
  `firstVisibleItemIndex`/`firstVisibleItemScrollOffset` — moot now, the band reading the rows' own bounds
  instead — whether a measured slot keeps the centre exact at a large font scale, and whether
  `ui.select.container` reads differently on `uiBackground` than on `uiCardBackground`. The snap API's shape
  *was* settled, by compiling it.

## 6. Shipped (2026-09-29)

- **`WheelPolicy.kt` + `WheelPolicyTest.kt`** — the slot count, the quantised height, the end padding and the
  row the band names, in ten tests that run green.
- **`DropdownWheel.kt`** — the snapped column on `rememberSnapFlingBehavior(state, SnapPosition.Center)`
  (probe-confirmed, no opt-in), the band as the bottom layer so the centred label draws over it, the slot
  measured from the label with the bars' 38dp as its floor, one positioning on open so the band starts on
  the entry the box shows, a tap choosing, and each row stating its taken state and its role.
- **`PopupFamily.kt`** — `PopupSurface(scrollable = false)`, the one scroll being the wheel's.
- **`DropdownRow.kt`** — the popup's body is the wheel; the box, its anchor and its width are untouched, so
  the wheel drops in exactly where the list stood.
- **Doctrine** — §2.12's list bullet narrowed to the wheel, §2.10's paragraph naming it as the family's one
  row exception, §2.15's rule of entry with R70 left retired.
- **Build** — `apk-build.bat` BUILD SUCCESSFUL with no warnings; `:app:testDebugUnitTest` green.
- **The slot rule and the feel, after the code was seen running** (2026-09-29): the count became the
  content's own, three to five with **four allowed**, and the end padding's integer division was fixed to a
  float for that case — both pinned by tests, the even case included. The spring and the fling's friction
  **could not be tuned**: `rememberSnapFlingBehavior` exposes only a state and a position, and the
  `SnapFlingBehavior` class that takes an animation spec is internal to the library — two builds said so,
  one per attempt, and the second consecutive failure halted the experiment per §4. The wheel therefore
  keeps the library's own feel by the user's word, and a `TargetedFlingBehavior` of our own is the one way
  to change it.
