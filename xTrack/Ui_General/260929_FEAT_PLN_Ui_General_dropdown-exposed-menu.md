# Plan — DropdownRow on Material 3's exposed dropdown box

**Feature:** Ui_General · **Date:** 2026-09-29 · **Status:** shipped — the literal M3 field (§6), the review's four points folded (§7), docs §2.12, §2.10 and `ui-drawer-guidelines.md` §8a in the same pass; the device pass is owed

## 1. Why

`DropdownRow` hand-rolled its own dropdown: a `Row` with `clickable { expanded = true }`, a nested `Box`
standing as the menu's anchor, and a bare `DropdownMenu`. Material 3 ships that whole control as
`ExposedDropdownMenuBox` — anchor, placement, dismiss handling and scroll state included — so the app's
dropdown is rebuilt on it and the hand-rolled mechanics are deleted.

The visible face does not move: the row keeps its label, its optional description and its value with the
down-arrow on the right, and the three call sites — the route's two ends and the route algorithm — pass
the arguments they passed before.

## 2. Decisions

- **D1 — the row is the anchor.** `menuAnchor(MenuAnchorType.PrimaryNotEditable)` is applied to the whole
  row, so §2.12's single-tap-target rule holds and no second `clickable` exists to double-hand a tap.
- **D2 — the menu wraps its own content.** `matchAnchorWidth = false`: the anchor is a full-width card
  row, and matching it would open a card-wide menu where the old `DropdownMenu` wrapped its items.
- **D3 — no `MaterialTheme.colorScheme` value reaches the screen.** Every slot M3 would paint reads an
  `AppConfig` token, so `colors.properties` stays the only source of colour.
- **D4 — the menu is a §2.10 popup.** Surface, corner, elevation, border and the height bound come from
  the canonical popup spec, which keeps those rules in one home; §2.12 points at it instead of restating.
- **D5 — the clause that a dropdown must not paint a surface retires.** §2.12 closed on *use this control,
  and do not paint a surface on it*; what a control paints is the control's business, so the clause read
  as a nonsense constraint on the control's own definition. §4's anti-pattern — hand-rolled dropdown
  rows — stands as the usage rule it is.

### Colour mapping

| M3 slot | M3 default | Mapped to |
|---|---|---|
| `ExposedDropdownMenu` container | `surfaceContainer` | `uiBackground` |
| shape | `MenuDefaults.shape`, 4dp | 12dp |
| shadow / tonal elevation | 0dp / 3dp | 8dp / 0dp |
| border | none | 1dp `uiNestedCardBorder` |
| height | unbounded | `popupMaxHeightDp()` |
| item text | `onSurface` | `uiTextPrimary`, 15sp Medium, SemiBold while current |
| item container | transparent | transparent |
| item icon slot | `onSurfaceVariant` | `uiAccent` — unused, no icon is drawn |
| item padding | 12dp horizontal | 16dp / 2dp |
| anchor label · description · value | — | `uiTextPrimary` 16sp Medium · `uiTextMuted` 13sp · `uiValueText` `uiFontValueSize` Bold |
| anchor arrow | `ExposedDropdownMenuDefaults.TrailingIcon` | `KeyboardArrowDown`, `uiAccent` |

## 3. Library

`material3` resolves to **1.4.0** through `compose-bom:2026.05.00` (`:app:dependencyInsight
--configuration debugRuntimeClasspath --dependency material3`), so the typed
`menuAnchor(MenuAnchorType.PrimaryNotEditable)` is the live form, and the control carries
`@OptIn(ExperimentalMaterial3Api::class)` — the opt-in §2.11 already uses for the settings tab strip.

## 4. Owed

- The device pass: the menu's placement under a full-width row, its width against a long option label,
  and one tap opening the list exactly once — the `menuAnchor` hand-off is the one mechanism no unit
  test covers.
- Nothing else in the popup family moved: the filter and sort pickers keep their own `Popup` and their
  anatomy in `docs/ui-lists-guidelines.md` §4.

## 5. Findings not acted on

- Both popups in `ListOverlayScaffold.kt` hardcode `0x40FFFFFF` where `AppConfig.uiNestedCardBorder`
  already holds that value, and §2.10's table prints the same value as a literal rather than naming the
  token.
- The popup row text (15sp) and the popup geometry (12dp corner, 8dp shadow, 1dp border) have no token of
  their own; this control and the popup family both state them as literals.

## 6. Revision — shipped as the literal M3 field (2026-09-29, same morning)

The first cut was rejected on look — three findings, and the first has a cause worth keeping.

- **The menu is not anchored to the control.** The menu hangs off the bounds of whichever node wears
  `menuAnchor`, and the cut moved that from the value box the hand-rolled version anchored to the **whole
  row** — so the list now opens at the row's left edge, under the label, where it used to open under the
  value. The row-as-anchor was chosen for §2.12's single-tap-target rule and cost the placement.
- **The menu should read like the app's other popups** — the filter and sort pickers — rather than like
  M3's own container.
- **The control itself should be a box field with a down arrow**, not a bare value and glyph sitting on
  the row.

### Directions that were on the table (historical — superseded by the resolution below)

- **Anchor.** A dedicated anchor node fixes the placement: the field itself, or the value box as before.
  If the field becomes the anchor it is also the tap target, so §2.12's *the row is the target* becomes
  *the field is the target*.
- **Field skin.** Either M3's own `OutlinedTextField` as the anchor — literal, at the cost of a 56dp
  minimum height, a floating label and every colour slot overridden — or the app's own rim: a `Box`
  carrying a 1dp `uiNestedCardBorder` on a 12dp corner, the value and the arrow inside it, `menuAnchor`
  on it, M3 supplying placement and dismiss.
- **Popup consistency, and how deep.** §2.10 is the canonical popup surface — outer `uiBackground` on a
  12dp corner behind a 1dp rim, an inner `uiCardBackground` card per section, rows at 16dp/2dp, a 24dp
  check box with the `✓` in `uiAccent`, 15sp Medium and SemiBold row text. Either M3's menu container is
  styled to match, or one shared popup skin is extracted and the filter popup, the sort popup and this
  menu all render through it.
- **The other popups' placement.** The filter and sort pickers hang their `Popup` at
  `Alignment.TopEnd` of their own container, a cruder anchor than the dropdown's; full sameness would
  reach them too.
- **The check mark** on the current option — declined in the first cut — is implied by the consistency
  ask.
- **Docs in play:** §2.12 (the control's anatomy and its target), §2.10 (the popup surface), and
  `ui-drawer-guidelines.md` §8's label-less, value-only route ends if those two ends become fields.

### Resolved and shipped — the literal M3 field (2026-09-29)

D1 and D2 above (the row as anchor, `matchAnchorWidth = false`) are superseded by this resolution, which
`DropdownRow.kt` now implements; §2.12 of `docs/ui-component-guidelines.md` carries the shipped anatomy.

The user's word — *whatever is closest to what I said* — and what was said was an **exposed dropdown menu
box, Material 3**, a **box field with a down arrow**. So the anchor is M3's own field, not our own rim:

- **Anchor and target.** A read-only `OutlinedTextField` (`readOnly = true`, `singleLine = true`, the value
  in `uiValueText` Bold) carrying `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)`; the field
  is both the anchor and the tap target, so the menu hangs under it and §2.12's *the row is the target*
  becomes *the field is the target*. The row keeps its leading label/description column, which today's
  three call sites leave null.
- **Field colours**, mapped slot by slot through `OutlinedTextFieldDefaults.colors(…)` so no
  `MaterialTheme` value shows: container `uiBackground`, rim `uiNestedCardBorder`, focused rim `uiAccent`,
  text `uiValueText`, cursor `uiTextPrimary`.
- **Trailing icon.** The app's `KeyboardArrowDown` in `uiAccent` — the down arrow that was asked for —
  rather than M3's rotating default.
- **Menu.** `matchAnchorWidth` returns to true, the field now being the anchor, and the menu reads as a
  §2.10 popup: the outer surface, the 15sp rows and the 24dp `✓` box on the current option — the check
  mark the first cut left out, which §2.10 already requires. It first shipped in `uiTextPrimary`, the way
  the built `SortControl` painted it; **§7 settled it on §2.10's own `uiAccent`** for the whole family, so
  the table stands as written.
- **Superseded** by §7: the filter and sort pickers do share this control's row now, and the check mark
  settled on `uiAccent`.
- **Named risk.** M3's field brings a 56dp minimum height and its own focus behaviour, so the two route-end
  rows and the settings row grow taller than the app's compact rows and a focusable control joins the
  drawer's traversal — device checks, not arguments against the look.

## 7. The review's four points, folded (2026-09-29)

The Ask hop returned **revise** on four points; the user's word was to fold all four, *matching the popups'
tight rows and settling the check mark across the dropdown menu, `SortControl` and `FilterControl`*.

- **One row for the whole family.** [`PopupFamily.kt`](../../app/src/main/java/ykws/android/maro/ui/components/PopupFamily.kt)
  now owns `PopupRow`, `PopupSectionCard`, `PopupSectionTitle` and the family's geometry as constants, and
  all three members draw through them. M3's `DropdownMenuItem` — whose own minimum interactive size held
  each row near 48dp — is gone from the dropdown's menu, so its rows are the popups' own 16dp/2dp rows and
  the two lists finally read alike.
- **The check mark is settled in one place:** `uiAccent`, 16sp, SemiBold, on the selected row only, a
  switched-off row dimmed to 0.4. The white check `SortControl` painted and the accent one `FilterControl`
  painted are now the same mark, and §2.10's table reads true without an edit.
- **The menu scrolls.** `ExposedDropdownMenu` is handed a `ScrollState` of its own beside the
  `popupMaxHeightDp()` bound, so a long option list — the route ends' list is the fixed entries plus every
  marker flagged for that end — scrolls instead of clipping.
- **§2.12's colour claim is narrowed.** A read-only field stays selectable, so the theme's own primary at
  40 % can still paint a selection; the claim now names the slots the control maps and admits the one it
  does not, rather than saying nothing M3 would colour escapes.
- **Still owed:** the device pass — the field's 56dp height against the compact rows, the menu's placement
  under the field, one tap opening once, and what a screen reader announces for a control with no label.
