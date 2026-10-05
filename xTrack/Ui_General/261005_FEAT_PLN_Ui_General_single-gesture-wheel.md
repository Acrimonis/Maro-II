# Plan — the dropdown wheel, one gesture, an arrowless box and a wheel over it

**Feature:** Ui_General · **Date:** 2026-10-05 · **Status:** implemented on `feature/wheel-down` — pass 1, revision 1 and revision 2 all built green; the commit is a tap on a row, an outside click cancels, and one text alignment drives the field and the wheel; the device pass is owed

> **This plan supersedes every earlier requirement for the dropdown control** except the two clauses §3 names as
> standing. The clauses it retires are enumerated in §3 and their homes are edited at implementation; the rule
> set in §2 is the control's whole requirement from here — nothing about this control survives from an earlier
> document as a separate rule.

## 1. What is being built

Three changes to the dropdown: one gesture, one box without an arrow, and a wheel that lands on the box.

- **One gesture to open and move**: a pointer down on the box followed by a drag opens the popup, and that same
  drag spins the wheel — no tap is needed to open it.
- **A tap to take**: a tap on a row writes that row and closes; an outside click closes and writes nothing, so
  the change is cancelled.
- **A box without its arrow**: the `KeyboardArrowDown` glyph and the width it reserved go, and the box's width
  answer shrinks with them.
- **A wheel that lies on the box**: the popup is centred on the box and its centre — the banded entry — is
  exactly the box's own rectangle, so the entry under the band sits **on top of** the control in z-order, same
  width and same height, with the neighbouring entries spilling above and below it.

## 2. The rule set now — the control's whole requirement

- **Opening is a drag or a tap, and the drag is taken unconditionally.** A vertical drag beginning on the box
  opens the popup and the page underneath it does not scroll under that finger; a tap on the box keeps opening
  it, so a non-drag path survives and the control is never drag-only.
- **The drag only scrolls and snaps.** The band shows what a tap would take, and the drag never commits — the
  wheel cannot choose on its own, which is the standing clause §3 names.
- **Commit is a tap on a row.** A tap on any row, the banded one included, writes that row and closes the
  popup, so the flow has one explicit act of taking and nothing is written before it.
- **An outside click cancels.** Dismissing by an outside tap or by back writes nothing and the box keeps the
  value it had; because nothing is written while the popup is open, the cancel is the absence of a write rather
  than a revert.
- **The band is a candidate, not the value.** While the popup is open the box carries the committed value and
  the band carries the candidate, so the two can differ on screen — the deliberate cost of tap-first, named in
  §6 rather than hidden.
- **The band is the box's rectangle.** The popup is centred on the box at both axes, and its centre slot is the
  same width and height as the box and covers it exactly — the band is drawn *on* the control, not above it,
  and the rows either side spill over the surrounding panel.
- **The popup's surface carries no inset, and its width is the box's own** (2026-10-05): the wheel passes
  `contentPadding = 0.dp` to `PopupSurface`, so the section card fills the popup and no `uiBackground` ring
  shows around it, and the box's measured width is exactly what the popup takes.
- **The slot is the box's own measured height**, not a fixed figure: the control already measures its box, and
  that measurement is what the wheel's slot takes, so the two cannot drift at any font scale.
- **The box carries no arrow**, and the width it asks for no longer reserves one.
- **The control takes a text alignment, centred by default, and both of its surfaces read that one value.** The
  field's value and the wheel's every row are drawn on the same axis, so the two can never disagree; the
  alignment is a caller-set behaviour, never a measurement.
- **The wheel still opens on the current value**, the box's word and the banded entry coming from one resolved
  index, and the landing that puts that entry under the band is unchanged.
- **The wheel's own geometry is otherwise unchanged** — three to five slots from the content's count, the band
  as the taken-choice face with its accent rules, and only the centred label bold.

## 3. Superseded — every requirement this plan retires

| Retired requirement | Its home today | Replaced by |
|---|---|---|
| The popup is positioned **at the box's bottom left**, as wide as the box's measured width | `docs/ui-component-guidelines.md` §2.12 | §2 and §5, centred on the box with the band on top of it |
| The box carries the app's `KeyboardArrowDown` in `uiAccent`, and its width answer reserves that arrow's 24 dp and a 4 dp gap | `DropdownBox.kt` KDoc, `dropdownBoxWidth` | §2, an arrowless box and the width it really needs |
| A wheel slot is the bars' 38 dp measured from the label, with that figure as a floor | `WheelPolicy.kt` (`WHEEL_ITEM_DP`), the wheel plan §4 | §2, the slot is the box's own measured height |
| The wheel plan's geometry and placement sentences, which assume a popup hanging under the box | `xTrack/Ui_General/260929_FEAT_PLN_Ui_General_dropdown-wheel.md` §3.2 · §4 | §2 and §5 |
| The wheel's KDoc, *three things are deliberate here*, whose slot and placement assume a standalone menu | `DropdownWheel.kt` | new KDoc at implementation |
| The epic's `### dropdown row` sentence, already stale on the retired M3 menu | `xTrack/Ui_General/FEAT_DSC_Ui_General.md` | rewritten to §2 |

- **Two clauses are explicitly *not* retired**, and an earlier draft of this plan had them wrong:
  [`§2.15`](docs/ui-component-guidelines.md:581)'s **a tap on a row is what chooses** and **a drag that commits
  on its own stays out** both stand, and §2 restates them with the cancel added. The clause requiring a wheel
  in a popup to keep a tap path stands with them.
- **R70 stays retired.** Its shape was one visible row stepped by a drag, not a three-to-five row wheel whose
  commit is a tap.
- **What else is not retired**: the landing-on-the-current-value machinery, the slot *count* rule and the
  band's face. They are restated in §2 so they have one home, not two.

## 4. Mechanism — one gesture that crosses a window boundary

- **The popup is a separate window, so the pointer that opened it cannot reach it.** The finger is still owned
  by the field, so the field's own detector must drive the scroll: `DropdownRow` hoists the wheel's
  `LazyListState` and hands it to `DropdownWheel`, and the field's vertical-drag detector calls
  `listState.scrollBy(dy)` on it.
- **The opening drag owns its own snap.** The library's `rememberSnapFlingBehavior` is attached to the
  LazyColumn's own scroll and cannot be borrowed for an imperative `scrollBy`, so a new pure helper —
  `wheelSnapTargetSlots(offsetPx, slotPx)`, the nearest slot multiple — lands the row under the band on
  release. It sits in `WheelPolicy.kt` beside the existing arithmetic and is pinned by `WheelPolicyTest`.
- **A later drag takes the library's snap.** A drag begun inside the open popup reaches the wheel's own
  LazyColumn and snaps by the library's own behaviour, so one physical motion snaps by two mechanisms
  depending on where the finger started. Named here; unifying them is a later pass, not this one.
- **Nothing is written while the popup is open.** The wheel keeps no settle watcher and no last-written guard;
  the only write is the row tap, `onChoose(index)`, followed by the close. That is what makes the outside click
  a cancel for free.
- **The band stays at the wheel's centre**, which is what makes §5's placement a pure offset: with the
  surface's inset symmetric, the wheel's centre is the popup's centre, so centring the popup on the box is the
  same act as putting the band on it.
- **The landing may key on the value it reads again.** Because nothing writes while the popup is open,
  `selectedIndex` cannot change under the opening effect, so the per-open key pass 1 introduced may stay or
  return to its original form — either is correct now.
- **The focus loss only closes.** The popup keeps `focusable = true`, so `onDismissRequest` still fires on an
  outside tap and on back, and it closes and writes nothing — it *is* the cancel of §2.
- **The guard is not a proof.** The commit reads the row the finger names, and the band reads the wheel's own
  layout, so the two can still disagree if the layout convention is wrong; that root cause was never
  established, which is why the device pass remains the check.

## 5. Placement — the wheel centred on the box, its band on the box

- **Today**: `Popup(alignment = Alignment.TopStart, offset = IntOffset(0, anchorSize.height))` — the menu's
  top-left on the box's bottom-left, hanging below and flush left.
- **Now**: the popup is centred on the box, so the band's rectangle equals the box's. With the surface's inset
  removed for this member, the placement is one offset pair: `x = 0` and
  `y = −(endPadPx + POPUP_SECTION_PAD_VERTICAL_DP)`, with the popup's width the box's own measured width. The
  vertical figure is the section card's own 8 dp inset on each side plus the wheel's end padding, and that
  padding is the same expression that already sizes the wheel.
- **The slot question disappears.** Because the slot is the box's height (§2), the wheel's height is
  `slots × boxHeight` and its centre is the box's centre by construction, so the band lands on the box at every
  count and every font scale.
- **What the eye reads**: the entry under the band covers the control, the entries above and below it spill over
  the panel behind, and the box is not visible while the popup is open. Since the band is only a candidate, what
  the eye reads there is *not yet* the control's value — the cost named in §6.
- **The height bound is now a capping question only.** `popupMaxHeightDp(screenHeightDp)` in
  `ListOverlayScaffold.kt` still caps the slot count; the centring takes precedence over fitting, so a box near
  a screen edge lets the popup run past it — named in §6 rather than corrected, the centring being the
  requirement.

## 6. Risks, and what only the device answers

- **The band is a candidate, so the control can show a value that is not the value.** While the popup is open
  the band covers the box with an entry that may never be taken, and an outside tap discards it. That is the
  deliberate cost of tap-first — the same cost the 2026-09-29 plan recorded — and it is the first thing the
  device pass must judge.
- **Two gestures to one choice.** A drag to move and a tap to take, where the wheel could have taken on rest;
  the trade is that the commit is explicit and the cancel exists.
- **The popup covers the control, by decision.** The band is drawn over the box and the rows spill over the
  panel, so whatever sits behind the wheel during the gesture is hidden.
- **An edge box loses part of its wheel.** A box near the top or the bottom of the screen has the popup run
  past the edge, so an entry above or below the band may be cut. A device look, and the case for a bound or a
  clamp if it reads badly.
- **The enclosing scroller loses that drag, by decision (settled 2026-10-05).** The menu drawer, the Routing
  card and the settings tabs all scroll vertically, and a vertical drag starting on a box now opens the wheel
  instead of scrolling the page. The lost scroll is the accepted cost, not a defect to fix.
- **A commit writes once, on the tap.** The call sites' own resolution runs on that write; the route algorithm
  already resolves through `RouteEngineChoice.resolve`, and the others should be checked for the same
  idempotence.
- **Two snap mechanisms** for one motion, per §4.
- **The shorter box changes the pair's look.** Removing the 24 dp arrow removes whatever it contributed to the
  box's height, so the drawer's pair of fields gets shorter; whether that reads well beside the bar cells is a
  device look.
- **The gesture itself is unproven until the device pass** — as the wheel's own device pass still is.

## 7. Files

- `app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt` — the popup's centring offsets and its
  width, the hoisted list state, the field's drag detector, the dismissal that writes nothing.
- `app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt` — the slot taken as an input, the state
  it no longer owns or the callback it now reports, the row tap that commits, and the KDoc of §3.
- `app/src/main/java/ykws/android/maro/ui/components/DropdownBox.kt` — the arrow and its gap deleted, the
  imports with them, `dropdownBoxWidth` no longer reserving either, and the value's alignment taken as a
  parameter applied to the `Text` that already fills the box.
- `app/src/main/java/ykws/android/maro/ui/components/PopupFamily.kt` — `PopupSurface` gained
  `contentPadding`, defaulted to the family's 12 dp, and its KDoc names the wheel as the one opt-out.
- `app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt` — `WHEEL_ITEM_DP` retired to the box's
  measured height, and `wheelSnapTargetSlots` added.
- `app/src/test/java/ykws/android/maro/ui/components/WheelPolicyTest.kt` — its cases.
- No call site moves: the route's two ends, the settings Route algorithm and the drawer's pair keep their
  signatures, and none of them passes an alignment — each takes the centred default, which is the visible
  change this revision carries.

## 8. Docs in play

- `docs/ui-component-guidelines.md` §2.15 — the rule of entry restated to §2: a tap chooses, the drag only
  scrolls, **and an outside click cancels**, its drag-only warning kept because it is where R70's risk is
  recorded.
- `docs/ui-component-guidelines.md` §2.12 — the bottom-left placement and the arrow replaced by §2 and §5; the
  tap-chooses sentence stays, and the value's one alignment, shared with the wheel's rows, joins it.
- `docs/ui-component-guidelines.md` §2.10 — the wheel paragraph follows, its centring, its slot and its tap.
- `xTrack/Ui_General/FEAT_DSC_Ui_General.md` — the epic's `### dropdown row` restated to §2.
- `xTrack/Ui_General/260929_FEAT_PLN_Ui_General_dropdown-wheel.md` — marked superseded by this plan where it
  states a requirement about geometry and placement, kept for its mechanism history and its tap-first rule.
- This plan's `## Implemented` pointer already stands in the epic's `## Implemented`; revision 1's record is
  written when its hop reports.

## 9. Gates

- `WheelPolicyTest` green beside the new helper and the retired constant.
- `apk-build.bat` SUCCESS with no new warning naming a touched file.
- The scoped `ui.component` unit run green.
- The device pass is the user's own and is owed: the candidate-versus-value read while the popup is open, the
  drag-versus-scroll conflict, the wheel landing on the box at three, four and five entries, the edge cases,
  the two snap mechanisms, the shorter box in the pair, and the outside-click cancel.

## 10. Decisions

- **D0 — the enclosing scroll — settled 2026-10-05**: the drag is taken unconditionally and the page does not
  scroll under a finger that lands on a box.
- **D1 — the popup's width — settled by §2**: the popup spans the box's width plus the surface's two insets, so
  the band is exactly the box's width.
- **D2 — the height bound — settled as the agent's default**: `popupMaxHeightDp` still caps the slot count, the
  centring takes precedence over fitting, and the edge look is named in §6 rather than corrected.
- **D3 — the commit — settled, then revised the same day (§13)**: it was commit-on-settle with no cancel; it is
  now **a tap on any row commits that row, and an outside click cancels**.
- **Nothing else is open.** Every requirement above is the user's word; what remains is revision 1's
  implementation and the device pass.

## 11. Out of scope

- The slot *count* rule, the band's face and the label weight.
- The pair's layout, the drawer's Routing card and the settings tab's own arrangement.
- The anchor's measurement, beyond the box no longer measuring an arrow.
- Any change to `PopupSurface`'s geometry for the family's other members: `contentPadding` is a new parameter
  whose default keeps their 12 dp, and only the dropdown opts out.

## 12. Shipped — pass 1 (2026-10-05)

- **Pass 1 landed the changes** in `DropdownBox.kt`, `WheelPolicy.kt` — `WHEEL_ITEM_DP` retired and
  `wheelSnapTargetSlots` added — `WheelPolicyTest.kt` with two new cases, `DropdownWheel.kt` and
  `DropdownRow.kt`, with §2.15, §2.12 and §2.10 of `docs/ui-component-guidelines.md` and the epic's
  `### dropdown row` rewritten to that pass's rule set.
- **Gates**: `apk-build.bat` BUILD SUCCESSFUL with no new warning naming a touched file and the scoped
  `ui.components` suite green; no new string, no dependency and no git write entered.
- **The Ask hop returned ship**, no blocker, with four records: the settle watch firing between the opening
  drag's events; an edge box letting the platform clamp the popup; `WHEEL_SLOT_FALLBACK_DP = 38f` restating the
  retired figure; and the drag state passing through one `DisposableEffect`-written holder.

## 13. Revision 1 — the commit returns to a tap (2026-10-05)

**The user's change**: *a tap on any row commits that row and closes, and an outside click cancels.* This
supersedes pass 1's commit rule in §2 and the D3 entry in §10.

- **What the revision subtracts**: the `snapshotFlow` settle watcher in `DropdownWheel` and the last-written
  guard that deduped it. Those were the whole of pass 1's commit mechanism, so this pass is mostly a deletion.
- **What it reinstates**: the row tap that writes `onChoose(index)` and closes — the wheel's rows are already
  clickable — and the tap-first doctrine, so §2.15's *a tap on a row is what chooses* and *a drag that commits
  on its own stays out* stop being retirements and stand again, with **the cancel** as this pass's one addition
  to them.
- **What it closes**: pass 1's Medium record. With no settle watcher there is no commit between drag events, so
  a value can no longer change under a finger that is still down.
- **What it keeps**: everything else from pass 1 — the drag that opens the popup and drives its scroll, the
  hoisted `LazyListState`, `wheelSnapTargetSlots`, the arrowless box and the `dropdownBoxWidth` shrink, the slot
  as the box's measured height, and the centring arithmetic of §5.
- **A consequence the record must carry**: the band is a candidate again, so while the popup is open the control
  shows an entry the box does not hold and an outside tap discards it. That is the cost of tap-first, and it is
  what the device pass must judge first.
- **The docs move back**: §2.15 · §2.12 · §2.10 and the epic's `### dropdown row` are rewritten to §2 with the
  cancel clause stated, and this revision is recorded in the epic's `## Implemented` beside pass 1's entry.

## 14. Revision 1 shipped (2026-10-05)

- **The subtraction landed**: `DropdownWheel`'s `snapshotFlow` settle watcher and its last-written guard are
  gone — with `rememberUpdatedState`, `snapshotFlow`, `distinctUntilChanged`, `drop` and the `choose` val —
  so nothing is written while the popup is open and the row tap is the only commit. The landing stayed keyed
  once per open, its KDoc now saying why either key is correct.
- **One change arrived that this plan did not set**: a directive given to the Code hop removed the popup's
  12 dp inner inset, so `PopupSurface` gained `contentPadding: Dp = POPUP_PAD_DP.dp` with the family's default
  kept, and the dropdown passes `0.dp` so its section card fills the popup and no `uiBackground` ring shows.
  `menuWidth` became the box's own measured width and the offset `x = 0`, `y = −(endPadPx +
  POPUP_SECTION_PAD_VERTICAL_DP)` — which is §5 as now written, the band still landing exactly on the box.
- **The row tap is confirmed as the only commit** in `DropdownRow`: `onDismissRequest` closes and writes
  nothing, so the outside click is the cancel, and the row tap writes through `onSelect` and closes.
- **Gates**: `apk-build.bat` BUILD SUCCESSFUL with no new warning naming a touched file and
  `:app:testDebugUnitTest --tests "ykws.android.maro.ui.components.*"` green; no new string, no dependency and
  no git write; removing the watcher left no dead state or import.
- **The returned payload carries no Ask verdict for this pass**, so none is recorded. The one finding it did
  return — §5 and §13 still carrying the pre-inset width and offset — is what this section corrects.
- **Still owed**: the device pass, whose first question is the candidate-versus-value read while the popup is
  open, beside the outside-click cancel and the flush card's new look.

## 15. Revision 2 — the text alignment (2026-10-05)

**The user's requirement**: the control can set the horizontal alignment of its text, **centred by default**,
and *that alignment must match on the field and the popup*.

- **One value, two readers.** `DropdownRow` gains the alignment and hands the same value to `DropdownBox` and
  to `DropdownWheel`, so the field's value and the wheel's rows cannot be drawn on different axes — the match
  is structural rather than a convention a caller has to keep.
- **The type is Compose's own `TextAlign`**, nothing app-specific being expressed here; the control's other
  behaviour parameter, `DropdownSizing`, stays an owned enum because it names an app concept.
- **The default is the visible change.** The box's value is left-aligned today, so the centred default moves
  the drawer's two route ends and the settings Route algorithm — the user's word, and no caller opts out.
- **`DropdownWheel`'s hardcoded `TextAlign.Center` becomes the parameter**, so its current look is what a
  caller gets by default and nothing else in its body moves.
- **`dropdownBoxWidth` is untouched**: it measures the text, not its placement, so the width answer cannot
  change with the alignment.
- **The gates** are the two that hold for every pass here — `apk-build.bat` with no new warning naming a
  touched file, and the scoped `ui.components` unit run — and the device pass already owed gains nothing but
  the centred field to look at.

## 16. Revision 2 shipped (2026-10-05)

- **One value, two readers, verified in the code**: `textAlign: TextAlign = TextAlign.Center` sits on
  `DropdownRow`, `DropdownBox` and `DropdownWheel`; `DropdownRow` hands the one value to both callees, the box
  applies it to its value `Text` and the wheel to every row label in place of the hardcoded centre.
- **Nothing else moved**: `dropdownBoxWidth` is untouched, and no popup offset, drag detector or width changed.
  No call site passes the parameter, so all of them take the centred default — the drawer's two route ends and
  the settings Route algorithm included, which is the user's word.
- **Docs**: §2.10 now says the rows' alignment is the control's and not the wheel's own, and §2.12 names the
  value's one alignment shared with the rows.
- **Gates**: `apk-build.bat` BUILD SUCCESSFUL with no new warning naming a touched file and the scoped
  `ui.components` run green; no new string, no dependency, no git write.
- **Two non-blocking findings stand, unfixed** (the pipeline forbids ping-pong): the `TextAlign.Center` default
  is spelled in three signatures, a single-home candidate; and `DropdownRow`'s KDoc names `sizing` as its
  behaviour parameter but not the new `textAlign`, while both callees' KDocs name theirs.
- **Still owed**: the device pass — the centred field, the candidate-versus-value read while the popup is open,
  the outside-click cancel and the flush card.
