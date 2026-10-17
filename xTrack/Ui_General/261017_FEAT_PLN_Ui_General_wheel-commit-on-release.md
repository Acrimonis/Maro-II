# FEAT_PLN Ui_General — the dropdown wheel commits on release

**Feature:** Ui_General · **Date:** 2026-10-17 · **Branch:** `feature/sp-ui` · **Status:** implemented — build and scoped suite green; the device pass on the release timing is owed
**Owner surface:** the one dropdown control, [`DropdownRow`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt:87) with its popup body [`DropdownWheel`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt:75) and the pure arithmetic in [`WheelPolicy`](../../app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt:1).

## 1. The order, as given

The user's word (2026-10-17), then the answer to the fork it contained:

1. "I want the wheel component to change its behaviour to set on change."
2. "No different behaviour anymore between clicking on the value and scrimming it."
3. "Close the popup on thumb up and set the value on release."
4. Asked which event is the commit — a settle at rest, a pointer-up, or a live write per band change — the answer was **"Set on release"**.

## 2. What the control does today — three doors, three behaviours

| Door | Today | Code |
|---|---|---|
| A row inside the popup | writes that row and closes | [`DropdownWheel.kt:159`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt:159) |
| A vertical drag begun on the box | opens the popup, scrolls the hoisted wheel by hand, snaps to the nearest slot on release, **writes nothing** | [`DropdownRow.kt:170`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt:170) and [`snapWheelToSlot`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt:228) |
| An outside click or back | closes, **writes nothing** — the cancel | [`DropdownRow.kt:195`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt:195) |

The band is a **candidate, not the value**: the box keeps what it holds while the popup is open. That sentence lives in `docs/ui-component-guidelines.md` §2.12 and §2.15, in the epic's `### dropdown row`, and in two KDocs that reason from it — [`DropdownWheel.kt:111`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt:111) ("nothing is written while the popup is open, so `selectedIndex` cannot change under this effect") and [`DropdownRow.kt:75`](../../app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt:75).

## 3. The rule that replaces them

**One event commits: the release.** The band's row at the instant the pointer lifts is written through `onSelect`, and the popup closes with that write.

- A tap on a row is the degenerate release — no movement, the same write and close it already performs.
- A drag on the box ends at `onDragEnd`; that is its release, and it writes the band and closes instead of snapping.
- A drag or fling inside the popup's own wheel ends at its pointer-up; the wheel reads its own band and commits there.
- An outside tap closes **and commits the band** — with any exit writing, "no different behaviour" leaves the scrim nothing else to mean.
- **The cancel disappears.** There is no path that closes the popup without writing, so the control has no revert, and a mistaken gesture is undone by opening it again.

Consequences worth naming before the code moves:

- **The fling is cut.** Under `rememberSnapFlingBehavior` the list keeps moving after the finger lifts, so the value committed is the row crossing the band at that moment, not the row the wheel would have settled on. That is exactly what "set on release" means, and it makes release timing the arbiter.
- **One write per gesture**, not one per band change: `onSelect` is a real write everywhere it is passed — the settings rows go through `onUpdateSettings`, and the menu drawer's route ends go through `storeRouteEnd`, which moves the route's start. A live write per band change was rejected for that reason.
- **The landing is unchanged** — the per-open `LaunchedEffect` still scrolls the stored entry under the band on open, and still keys on the open. Its comment's premise dies and must be rewritten.

## 4. Change set

1. **One band reader** — the commit reads the band through the existing pure [`wheelCentredIndex`](../../app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt:58) off the hoisted `LazyListState`; the field's `onDragEnd` and the popup's dismissal both reach it, so no second definition of "the row under the band" appears.
2. **`DropdownWheel`** — the row tap keeps its `onChoose(index)` + `onDismiss()`; a pointer observer that does not consume adds the wheel's own release, committing `centred` and closing.
3. **`DropdownRow`** — `onDragEnd` commits the band and closes in place of `snapWheelToSlot`; `onDismissRequest` commits the band before closing.
4. **Retirement check** — if nothing else reads [`wheelSnapTargetSlots`](../../app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt:94) and `snapWheelToSlot`, both go, with the unit test that pins the first.
5. **Corpus** — §2.12 and §2.15 rewritten from "the band is a candidate" to "the band is the value, committed on release"; the epic's `### dropdown row` follows; the 2026-10-05 wheel plan is marked superseded where it states a requirement; the two KDocs above are rewritten with the code.

## 5. The back key and the cancelled drag — settled

**Back closes without a write, because it can only arrive before the wheel has moved.** The popup is opened `focusable = true`, so the system back reaches `onDismissRequest` while it stands; but every gesture — the field's drag, a drag inside the popup, a row tap — commits and closes on its lift, so when back can fire the band still holds the stored entry the open landed under it. Committing there would write the value the control already has, so the plain close is the same value on screen with no redundant persistence write.

**A drag the gesture system cancels writes nothing.** `onDragCancel` has no finger lifting, so it closes without committing — otherwise a stolen gesture would write a row nobody released.

## 6. Non-goals

- The popup's geometry, its surface, its centring on the box and the slot-as-box-height rule — untouched.
- The wheel's arithmetic — untouched, apart from the retirement in §4.4 if it falls silent.
- The dropdown's three call surfaces (the route ends, the pace/dial pair, the settings algorithm row) — untouched; they inherit the commit.
- No dependency, no commit, no push, no device action.

## 7. Verification

`.\gradlew.bat :app:compileDebugKotlin` plus the scoped `ui.components` suite; the gesture itself is the user's device pass.

## 8. Review findings — the Ask hop, recorded and folded nowhere

The pipeline forbids ping-pong, so these stand as owed work, not as edits made after the review.

- **S-1 — the discriminator is consumption-blind (should-fix).** The wheel's observer calls a gesture a drag by Euclidean distance from the down, while a row's `clickable` cancels only on a consumed change or on leaving its bounds; a horizontal or diagonal move past the slop can therefore write the tapped row at Main and the band at Final in one gesture. The fix is to set `dragged` on `change.positionChangeConsumed()`, or to gate the commit on the list having scrolled.
- **S-2 — the snap is dead weight (should-fix).** The up that would start a fling also closes the popup, so `rememberSnapFlingBehavior` can never decide a value. It should go with its `flingBehavior` argument, both imports and the spring comment — and the four "snapped" claims with it: `docs/ui-component-guidelines.md:413`, `:510`, `:624` and `DropdownWheel.kt:53`.
- **S-3 — the field drag can read a band one increment stale (should-fix).** `onDragEnd` reads the band while the drag's last `scrollBy` is still being dispatched, so a release across a slot boundary can write the row the finger has just left.
- **S-4 — the index-to-value write is spelled twice (should-fix).** `DropdownRow` writes the chosen option at `:119` and again at `:240`; one `commit(index)` would own both.
- **N-1 — "write then close" is spelled twice** in the wheel (`:156`, `:191`), and `expanded = false` appears five times; one `commit`/`close` pair on the row would own both.
- **N-2 — an unreadable band closes without a write**, in the observer as much as in `commitBand(null)`, against the KDoc's "No exit is a cancel"; neither is reachable in practice.
- **N-3 — a drag lifted outside the popup window** takes the cancel path or the scrim's write depending on the platform; device evidence owes the answer, and this plan names neither.
- **N-4 — `WheelPolicy`'s header still names the 2026-10-05 plan** as the design of record, and `wheelSlotCount`'s KDoc justifies the even count by a snap that no longer runs.
- **Resting on an idiom rather than on code:** whether the back key reaches a popup's dispatcher at all is library behaviour; what the tree shows is that `dismissOnBackPress = false` leaves the composed `BackHandler` the only receiver, and that its registration dies with the popup.
- **Health verdict:** structure yes — one band reader, one arithmetic home, a duplicated snap mechanism and its test deleted; honesty not yet, two sentences of this plan having described behaviour the code does not have, both corrected in `## Implemented`.

## Implemented

- **wheel-commit-on-release (2026-10-17, `feature/sp-ui`)** — the release is now the only commit and the band
  is the value. `DropdownWheel` gained a Final-pass pointer observer that consumes nothing, so a drag begun in
  the popup writes its band at pointer-up and closes; the rows' `clickable` still owns taps, so a tap writes
  once, and the snap fling behaviour the close can never let settle stays in place, named for retirement in §8.
  `DropdownRow`'s field `onDragEnd` commits through
  the one band reader (`wheelCentredRow`, wrapping `wheelCentredIndex`) and closes in place of the retired
  snap, an outside tap commits the band through `onDismissRequest`, back is taken by a popup `BackHandler`
  with `dismissOnBackPress = false` so it closes without a write, and `onDragCancel` closes with nothing
  written. `snapWheelToSlot` and `wheelSnapTargetSlots` went with their unit test, nothing else reading them.
  §2.10, §2.12, §2.15, the epic's `### dropdown row` and both KDocs were rewritten, and the 2026-10-05 plan
  is marked superseded where it states a requirement. `:app:compileDebugKotlin` and the scoped
  `ui.components` suite green; no device action, no commit, no push.
