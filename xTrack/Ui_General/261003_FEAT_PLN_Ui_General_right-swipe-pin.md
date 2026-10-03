# Plan — the right-swipe pin

**Feature:** Ui_General · **Date:** 2026-10-03 · **Status:** shipped — implemented on `feature/list-swipe` with
both gates green. The Ask hop returned **revise** on one Medium, recorded in §8 and folded nowhere, and the device
pass is what stays open.

## 1. The requirement

> "Add a right-swipe action on the card — pin/unpin the item — beside the existing left-swipe delete, on both
> lists."

Read as: a right drag on a non-live card in either list overlay toggles that item's pinned flag, with the card
returning to rest; the left drag keeps its delete lifecycle untouched.

**Settled by the user, 2026-10-03 — option one, in their words:** "swipe right past thirty in a hundred, let go,
the pin flips, the card slides back, symbol only." So: **commit on release**, the same **30 %** threshold the
delete swipe uses, a **snap-back** to rest rather than a slide-away, and the reveal is the **pin glyph alone** —
no word, therefore **no new string**.

## 2. The surface this builds on

- The whole swipe surface is one private composable,
  [`SwipeableItemCard()`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:335),
  serving both consumers. It holds `SwipeState.CARD / SNACKBAR / DELETED`
  ([`:326`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:326)) and the constants
  `DRAG_THRESHOLD = 0.30f`, `ANIM_DURATION_MS = 200` and `SNACK_ANIM_MS = 250`
  ([`:329`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:329)).
- **The drag was one-directional and is now signed.** It used to clamp to leftward travel alone —
  `.coerceIn(-cardWidthPx, 0f)` — and its release tested one sign, `cardDragOffset < -threshold`; it now clamps
  both ways and branches on the extracted decision
  ([`:403`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:403)).
- **There was no reveal layer at all.** The card simply slid out and was replaced by the inline `SnackbarSlot`.
- The scaffold's call site passes the delete callbacks into `onAction: (ListAction) -> Unit`, and the public API
  ([`ListOverlayScaffold.kt`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt)) has
  no pin surface — it grew none, the toggle being wired internally.
- **The toggle behind it already existed twice.** [`TrackViewModel.setPinned()`](../../app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:383)
  and [`MarkersViewModel.setMarkerPinned()`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:1210)
  are both live, reached from the card's own button and from the multiselect sub-actions.
- **The item already carries the state.** `ListableItem.isPinned`
  ([`ListableItem.kt`](../../app/src/main/java/ykws/android/maro/data/model/ListableItem.kt:10)) is on the common
  interface, and both cards already draw the pair — `Filled.PushPin` when pinned, `Outlined.PushPin` when not,
  with `cd_unpin` / `cd_pin` naming the action — at
  [`TrackCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:578) and
  [`MarkerCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:338).
- The two hosts dispatch `ListAction` in `MapScreen.kt`'s `onTrackAction`
  ([`:3228`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3228)) and `onMarkerAction`
  ([`:3313`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3313)).
- A right swipe is therefore a **second door onto an existing state**, not a new capability; the plan records
  that rather than treating the redundancy as a defect.

## 3. The change

1. **One new action.** `ListAction.TogglePin(val id: String, val pinned: Boolean)`
   ([`ListAction.kt`](../../app/src/main/java/ykws/android/maro/data/model/ListAction.kt:15)), carrying the
   **target** state the card resolved (`!item.isPinned`). No undo entry, no pending set: a pin is reversible by
   the card's own button, and by the same gesture wherever an active filter has not taken the card away (§4), so
   the delete lifecycle was not copied.
2. **The drag becomes signed.** `SwipeableItemCard`'s offset clamps to `-cardWidthPx .. cardWidthPx`, and the
   release resolves one of three outcomes — nothing, delete, pin — at the same 30 % threshold on both axes. The
   delete outcome keeps its body exactly; the pin outcome fires the toggle and animates the offset back to `0f`
   with the existing `tween(ANIM_DURATION_MS)`.
3. **A reveal layer, glyph only.** Layer 0 beneath the card, anchored at its leading edge and uncovered as the
   card travels right, gated by `pinRevealAlpha`
   ([`:358`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:358)); it reuses the
   card's own pair — `Outlined.PushPin` + `cd_pin` when unpinned, `Filled.PushPin` + `cd_unpin` when pinned — on
   the `ButtonColors.icon` tint. No label, no new string, both locales untouched.
4. **One gate, unchanged.** The right drag shares the existing `!isMultiSelectMode` / `!cardDismissed` /
   `SwipeState.CARD` condition ([`:390`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:390));
   the live card keeps its no-swipe path, and the snackbar slot keeps its delete-only drag.
5. **The public API does not move.** The scaffold wires the toggle internally at its call site —
   `onAction(ListAction.TogglePin(it.id, !it.isPinned))` — so no parameter was added for a choice neither
   consumer needs; both hosts want it and both have the toggle behind it.
6. **Two host wirings.** `onTrackAction` routes `TogglePin` to `trackViewModel.setPinned(action.id, action.pinned)`
   and `onMarkerAction` to `markersViewModel.setMarkerPinned(action.id, action.pinned)`, one line each beside
   their `PermanentDelete` branches.
7. **One extraction for the tests.** The release decision left the composable as the pure, Compose-free
   [`SwipePolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/components/SwipePolicy.kt:26) —
   `SwipeOutcome` (`None` / `Delete` / `TogglePin`) from `(offsetPx, widthPx, threshold)`, plus the matching
   clamp, in the `WheelPolicy` mould.

## 4. Consequences to write down, not to fix

- **The pinned filter can take the card away.** With an active `Pinned` or `Unpinned` filter a toggle makes the
  item stop matching, and the list re-filters exactly as any filter change does. No card-advance rule was added:
  the delete path's `advanceMarkerCardFrom` ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3284))
  answers a dashboard question, and no dashboard is open on a swipe. Recorded so the disappearance is not read
  later as a bug.
- **Pinned is not a sort key.** The lists sort by `ListSortField` and the custom fields
  ([`ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md:68)); pinning changes no order, so the card returns
  to the slot it left — unless the filter case above applies.
- **Two doors, one state.** The card's header button and the new swipe both write the same flag through the same
  ViewModel call; neither is authoritative.

## 5. Out of scope

- Any hold-and-confirm, any word label, any threshold other than the delete swipe's 30 %.
- The live recording card, which has no swipe.
- Swipe on the menu drawer's own track/marker rows — this pass is the two overlays' lists.
- The delete lifecycle: its threshold, its snackbar, its undo and its pending-commit rules.
- `isLive`, multiselect and the sort/filter machinery.

## 6. Tests, and the honest gap

- **Unit:** [`SwipePolicyTest.kt`](../../app/src/test/java/ykws/android/maro/ui/components/SwipePolicyTest.kt) pins
  the decision — nothing below the threshold on either side, `None` exactly at it on both axes, `Delete` left past
  it, `TogglePin` right past it, an unmeasured card resolving nothing, and both clamps.
- **Device, owed:** the right drag's feel on both lists, the reveal's appearance, the card's return, and the
  filtered-list disappearance of §4.

## 7. Target files

- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — the signed drag, the reveal layer,
  the internal `onTogglePin` wiring
- `app/src/main/java/ykws/android/maro/ui/components/SwipePolicy.kt` — new: `SwipeOutcome` + the decision and the
  clamp
- `app/src/main/java/ykws/android/maro/data/model/ListAction.kt` — `TogglePin`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the two `onAction` branches
- Test: `app/src/test/java/ykws/android/maro/ui/components/SwipePolicyTest.kt`
- Docs: [`docs/ui-lists-guidelines.md`](../../docs/ui-lists-guidelines.md) — the swipe section, the architecture
  diagram, the `ListAction` block

## 8. Shipped, and the Ask hop (2026-10-03)

**Shipped:** branch `feature/list-swipe` cut from `origin/develop` (`20d2b66`) with `--no-track`; the right drag
past 30 % commits the pin on release and the card snaps back; `ListAction.TogglePin(id, pinned)` carries the target
state; `SwipePolicy.kt` holds the decision and `SwipePolicyTest` pins it; the reveal is the bare pin glyph under a
rightward-only alpha gate; the two hosts route the action to `setPinned` / `setMarkerPinned`. `apk-build.bat`
BUILD SUCCESSFUL and `gradlew :app:testDebugUnitTest` green, no new warning and no new dependency.

Verdict: **revise**, on one Medium — a screen-reader regression the unit tests cannot see.

- **Medium — a phantom accessibility node.** The reveal's `Icon` keeps `cd_pin` / `cd_unpin`
  ([`:380`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:380)) while its parent
  Box sits at `alpha(0f)` ([`:375`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:375)),
  and alpha never clears semantics, so every non-live card now announces a second, non-actionable Pin/Unpin
  element. `contentDescription = null` — the card's own button already names the action — or
  `clearAndSetSemantics {}` is the one-line fix; folded nowhere, the pipeline forbidding ping-pong.
- **Low, pre-existing but newly visible — a cancelled gesture leaves the card off rest.** The detector has no
  `onDragCancel`, so a drag interrupted by a second pointer never resets the offset; the leftward case always did
  this, and only now does a stuck reveal make it visible.
- **Low — a third verbatim copy of the pin glyph pair.** The glyph, tint and description triple now lives in the
  card, `TrackCardContent` and `MarkerCardContent`; a shared `PinIcon(pinned)` would give it one home.
- **Low — two homes for the 30 % arithmetic.** The snackbar still computes `snackWidthPx * DRAG_THRESHOLD` and
  clamps inline, so the extraction covers the card alone; the snackbar is out of scope, so this is an observation.
- **Low — the extraction's own shape.** `SwipePolicy.kt`'s file-level KDoc is unattached above the enum's own doc,
  and the delete and pin branches are one-line giants
  ([`:396`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:396),
  [`:400`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:400)).
- **Low — the doc's `ListAction` listing is a partial restatement** that already omits `NavigateToItem`,
  `BatchExportGpx`, `ImportTracks` and `MergeTracks`, so it is stale beyond the `TogglePin` line added.
- **Low, unreachable — an unmeasured card.** The old code computed a zero threshold, so any negative offset
  deleted; [`swipeOutcome`](../../app/src/main/java/ykws/android/maro/ui/components/SwipePolicy.kt:27) returns
  `None` for a card with no measured width. Unreachable — a zero-width card takes no gesture — and the doc states
  it, so it is a guard rather than a regression.
- **Confirmed rather than assumed:** the delete path is behaviourally unchanged, its strict `<` boundary included
  (exactly at 30 % resolves `None` on both axes, and the tests assert that boundary); the pin fires once per
  gesture; every exit returns the offset and the reveal to rest; the reveal cannot surface during the delete or its
  snackbar; multiselect and the live card gate both directions; and no string or dependency entered.
- **Not covered by any file:** the on-device feel, the reveal's appearance and the filtered-list disappearance —
  the device work of §6.

## 9. The inertia pass (2026-10-03, second request — in design, nothing implemented)

> "Right swipe should have more inertia: leave a left gap about the same size of the height of the card; leave it
> opened for one sec for the pin/unpin icon flash to show."

Read as: a pin release no longer returns the card straight to rest. The card settles into a **hold** — pushed right
by its own **height**, so a gap that tall opens at its leading edge — **stays there for one second** so the pin or
unpin glyph can be read, then returns to rest. **"Inertia" is read here as the card carrying on into a held
position rather than snapping home** — no fling physics, no velocity tracking; that reading is this plan's own and
is the one point to confirm.

**Settled by the user, 2026-10-03 — the trigger, corrected.** "Action on swipe action, not on resolve. Update
display(s) at that time." So the toggle fires **mid-gesture, the moment the drag crosses the 30 % threshold** — not
on release — and the displays (the reveal's glyph and the card's own pin button, both reading the item's flag)
update **at that moment**. The release no longer decides the pin; its job is the hold that follows. This withdraws
the freeze the plan first proposed: the flash shows the state the swipe produced, because the swipe has already
produced it. The description still follows the carried fix of point 7.

- **One toggle per gesture.** The drag callback evaluates the same pure `swipeOutcome` and fires `onTogglePin` on
  the first frame the offset crosses the threshold, guarded by a per-gesture flag so a drag held past the line
  toggles once and not once a frame.
- **The release owns the hold alone.** A release whose gesture fired the pin animates the card out to the gap,
  dwells `PIN_HOLD_MS`, and returns to rest, wherever the offset happens to be when the finger lifts.
- **Delete keeps precedence.** A gesture that fired the pin and then resolved `Delete` runs the existing delete
  lifecycle and the hold is suppressed — the left swipe's behaviour does not change because the right one fired.
  This is the plan's own call and is the one edge worth a second look.
- **The filter case sharpens.** With an active Pinned or Unpinned filter the item leaves the filtered list the
  moment the toggle fires — mid-drag — so the hold may have nothing left to hold. Recorded, not fixed (§4).

1. **The gap is the card's height.** The existing `onSizeChanged` already reads the width
   ([`:368`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:368)); it gains the
   height, and the pin outcome animates the offset to the hold offset instead of `0f`. A card not measured yet
   holds nothing and returns to rest, as `swipeOutcome` already guards for the commit itself.
2. **One second, then home.** In the shape the delete branch already uses — a `scope.launch` in the release branch
   ([`:396`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:396)): animate to the
   gap on the card's own `tween(ANIM_DURATION_MS)`, `delay(PIN_HOLD_MS)`, then animate back to `0f`. The dwell is
   measured from the moment the card **reaches** the gap, so the flash is a full second of legible glyph rather
   than a second minus its travel; the two legs plus the dwell are the whole gesture.
3. **Two new private constants** beside `DRAG_THRESHOLD` and `SNACK_ANIM_MS`
   ([`:329`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:329)) — the file's
   existing home for the swipe's geometry and timing — `PIN_HOLD_MS = 1000` and nothing else; the travel reuses
   `ANIM_DURATION_MS`.
4. **A hold offset that can never exceed the card.** The gap is `min(cardHeightPx, cardWidthPx)`, so a degenerate
   tall-and-narrow card cannot push its own reveal past the travel its slot allows; the pure helper
   `pinHoldOffset(cardHeightPx, cardWidthPx)` joins [`SwipePolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/components/SwipePolicy.kt)
   and takes its own test cases beside the existing ones.
5. **The glyph itself is unchanged** — 24 dp on `ButtonColors.icon`, 16 dp in from the leading edge. The clarity
   asked for is delivered by the one-second exposure; a larger or a brighter glyph (32 dp, or `uiTextPrimary` /
   `uiAccent`) is a separate one-line change and is **not** in this pass unless it is asked for.
6. **The reveal's alpha stays binary** on the sign of the offset: it stands through the hold and through the
   return, clearing only at rest. No crossfade is added.
7. **The carried fix rides along.** The reveal now stands on screen for a full second, which sharpens the Medium
   the last review left in §8: the icon keeps `cd_pin` / `cd_unpin` while its parent Box is transparent, so every
   card announces a phantom Pin element. `contentDescription = null` on that icon — the card's own button already
   names the action — is the one-line fix, taken with this pass because it is the same layer.
8. **The hold is feedback and nothing else (user, 2026-10-03).** It carries no state, no semantics and no
   interaction: nothing is remembered per item, nothing waits on it, it confirms nothing and it adds no hit target —
   it is a purely visual dwell on the card's offset whose only job is to be seen. Two rules follow. **It yields:** a
   new drag on the card, or the list scrolling it out, interrupts it and the card simply returns to rest — the card
   is never locked for the second. **It owes nothing when it cannot be seen:** if the item leaves the list mid-hold
   (the filter case above) there is nothing left to draw and no code path to answer for it.

**Untouched:** the left clamp and the whole delete lifecycle, the 30 % threshold, multiselect and the live-card
gate, the two host wirings, and every string in both locales.

**Open, named rather than assumed:**

- **Whether "farther" also means the pull itself.** The drag clamp stays `±cardWidthPx`, which already admits this
  hold (a list card is wider than it is tall; the `min` of point 4 covers the degenerate case). If the earlier
  "allow the right swipe to go farther" was about the drag's own reach rather than the held gap, that is a separate
  one-line clamp change and is not in this pass.
- **Whether the glyph itself should also grow or brighten** (point 5).

**Tests and the honest gap.** The hold-offset helper takes unit cases; the flash itself — the gap's look, the
one-second dwell, the return — is a device look, as the rest of the swipe already is.
