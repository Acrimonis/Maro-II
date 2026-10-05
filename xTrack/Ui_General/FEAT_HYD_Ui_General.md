# Context Hydration — Ui_General — 2026-10-05

**Last Bake:** 2026-10-05 09:53 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed an order (the branch on `#new`, pass 1 on `#impl`, revisions 1 and 2 on the user's own directives, the bake on `#bake`), the device was never touched (builds only), and every claim written about the code here came from a file read in the session. The gaps are named rather than hidden: nothing is device-validated, and no Ask verdict is recorded for revision 1's payload.

## State

One branch, `feature/wheel-down`, cut from `origin/develop` (`e6c2a63`) on `#new`, carrying no upstream yet — the first `#push` writes its own name. Three passes shipped: the plan of record is `xTrack/Ui_General/261005_FEAT_PLN_Ui_General_single-gesture-wheel.md`, whose §12, §14 and §16 record them.

**The dropdown is one gesture now.** A vertical drag on the box opens the popup and, through the hoisted `LazyListState`, drives the wheel's scroll — the pointer cannot cross into the popup's window — with the opening drag's snap taken by the new pure `wheelSnapTargetSlots` and a later drag keeping the library's centre snap, two mechanisms for one motion. The drag is taken unconditionally, so the menu drawer, the Routing card and the settings tabs no longer scroll under a finger that lands on a box.

**The commit is a tap on a row**, and an outside click cancels. Revision 1 removed the settle watcher pass 1 had shipped — the `snapshotFlow` collector over `listState.isScrollInProgress` and its last-written guard — so nothing is written while the popup is open and the band is a candidate rather than the value; a tap on any row writes that row and closes, and an outside tap or back closes and writes nothing, which makes the cancel the absence of a write rather than a revert.

**The box lost its arrow**, and `dropdownBoxWidth` the 28 dp its gap and glyph reserved. **The slot is the box's own measured height**, in place of the retired `WHEEL_ITEM_DP`. **The popup is centred on the box**: its surface carries no inset (`contentPadding = 0.dp`, the family's 12 dp default kept for the other members), its width is the box's own measured width, and the offset is `x = 0`, `y = −(endPadPx + POPUP_SECTION_PAD_VERTICAL_DP)`, so the banded entry is exactly the box's rectangle and the neighbours spill over the panel. **One text alignment**, `TextAlign.Center` by default, drives both the field's value and the wheel's rows, so the two cannot be drawn on different axes.

**Rules moved once.** §2.15, §2.12 and §2.10 of `docs/ui-component-guidelines.md` and this feature's `### dropdown row` carry the new rule set; the 2026-09-29 wheel plan is marked superseded where it states a requirement; the epic's `## Implemented` holds one entry per pass.

`apk-build.bat` BUILD SUCCESSFUL on every pass with no new warning naming a touched file, and the scoped `ui.components` suite green; no new string, no dependency and no git write before this bake. Two non-blocking findings stand unfixed — the `TextAlign.Center` default spelled in three signatures, and `DropdownRow`'s KDoc not naming `textAlign` — and the duplicate `## Implemented` heading in the epic, predating this session, was seen and left.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt` — the drag detector, the hoisted list state, the centred popup and the text alignment
- `app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt` — the slot and the state as inputs, the row tap that commits, the alignment
- `app/src/main/java/ykws/android/maro/ui/components/DropdownBox.kt` — no arrow, the shrunk width, the alignment
- `app/src/main/java/ykws/android/maro/ui/components/PopupFamily.kt` — `PopupSurface`'s new `contentPadding`
- `app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt` · `app/src/test/java/ykws/android/maro/ui/components/WheelPolicyTest.kt` — `WHEEL_ITEM_DP` retired, `wheelSnapTargetSlots` added
- `docs/ui-component-guidelines.md` — §2.10 · §2.12 · §2.15
- `xTrack/Ui_General/261005_FEAT_PLN_Ui_General_single-gesture-wheel.md` — the plan, its decisions and its three shipped records

## Next Step

The device pass, owed and unstarted: the centred field, the candidate-versus-value read while the popup is open, the outside-click cancel, the flush card, the drawer's own scroll lost to the drag, the two snap mechanisms, and the shorter box in the pair.
