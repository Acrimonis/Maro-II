<!-- scope: feature -->
# Menu alignment — left-aligned route ends, a content-proportional quick-access pair

Owner: **Ui_Menu** (the drawer surface). Content owner: **Route** (the pair's two settings).
Branch: `feature/menu-align`.
Status: **implemented 2026-10-05** — both changes are in the tree, `apk-build.bat` green; the device pass is owed.

## 1. What was asked — the user's word, 2026-10-05

- **The route ends read left**: the origin and destination boxes, and the wheel each opens, move from centred to
  left-aligned.
- **The quick-access pair shares the row by content**: the cruising-speed and driving-preference boxes take a
  share of the row **matching their own longest word**, replacing the fixed-pace / elastic-preference split that
  `261004_FEAT_PLN_Ui_Menu_route-quick-access.md` settled.

## 2. What follows from those decisions

- **The ends' alignment is the field's one `textAlign`** (`DropdownRow`, default `TextAlign.Center`): the same
  value drives the box's value and the wheel's rows (the 2026-10-05 single-alignment rule), so one
  `TextAlign.Start` per end moves both surfaces together and nothing is decoupled.
- **The pair gains a third width behaviour**: `DropdownPairWidth.Proportional`, beside `Content` and
  `Remainder`. Each side's weight is its own measured content width (`dropdownBoxWidth`, the box's own answer),
  and the `Row` normalises the two, so the split is in the ratio of the longest words the boxes must hold.
- **The measurement stays the box's**: `dropdownPairShare` calls `dropdownBoxWidth` and floors it at one, so a
  weighted child always carries a positive weight; neither a caller nor a side handles a dp.
- **The consequence, named rather than left to be discovered**: with proportional shares the pace box no longer
  holds a width fixed to `35 kn`, so at an extreme width or font scale its word can trim where the fixed split
  protected it — both words now degrade together rather than one being shielded.
- **The defaults do not move**: `DropdownPairRow` still defaults to `Content` left / `Remainder` right, and its
  other callers are untouched; only the drawer's quick access asks for `Proportional`.

## 3. What entered

- `ui/components/DropdownPairRow.kt` — `DropdownPairWidth.Proportional`, its branch in `DropdownPairSide`, and
  the `dropdownPairShare` helper.
- `ui/map/MenuDrawerOverlay.kt` — `textAlign = TextAlign.Start` on both `RouteEndsSection` rows, and
  `leftWidth` / `rightWidth = DropdownPairWidth.Proportional` on `RouteQuickAccessSection`'s pair.
- `docs/ui-component-guidelines.md` §2.16 and `docs/ui-drawer-guidelines.md` §8 — the pair's bullet in each,
  restated for the proportional behaviour.
- No new string (alignment is not text), no token, no colour, no dependency.

## 4. Superseded

`261004_FEAT_PLN_Ui_Menu_route-quick-access.md` §2's last bullet and §9's fixed-pace / elastic-preference split.
Its component boundary (§10), its chrome trim (§11) and the pair's default widths stand.

## 5. Verification

- **In reach:** `apk-build.bat` green (`BUILD SUCCESSFUL`, 2026-10-05 21:29 UTC); the ends' one alignment covers
  box and wheel by construction; the pair's arithmetic reads from `dropdownBoxWidth` alone, so no second
  measurement was introduced. No pure helper was extracted for a unit test — the weight **is** the box's own
  measured width.
- **Not in reach:** whether the left-aligned ends read well against the card, and whether the proportional
  pair's two shares balance on the device. The user runs this.
