<!-- scope: feature -->
# List-selection type glyph — the type icon that doubles as the multiselect door

Owner: **Ui_General** (the list-item card shell, [`ListOverlayScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt)).
Surfaces: [`TrackHistoryOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) and
[`MarkerManagementOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt).
Branch: `feature/list-selection`. Status: **implemented 2026-10-06**; no device pass.

## 1. What was asked — the user's word, 2026-10-06

- On the two list surfaces, every item card gains a **type icon** that is also the **multiselect
  selection affordance** and **morphs into the check mark**.

## 2. Confirmed decisions (do not re-litigate)

1. **The glyph leads the TITLE LINE**, immediately left of the item name — not a leading rail, not the
   header line. The 4 dp accent bar stays.
2. **Glyphs reuse the fan's own layer icons**: track = [`Conversion_path`](../../app/src/main/java/ykws/android/maro/ui/icons/ConversionPath.kt),
   route = [`route`](../../app/src/main/java/ykws/android/maro/ui/icons/Route.kt),
   marker = [`LocationOn`](../../app/src/main/java/ykws/android/maro/ui/icons/LocationOn.kt).
3. **The old check mark at `Alignment.BottomEnd` in `ListOverlayScaffold` is deleted**; the check mark now
   renders on the glyph.
4. **Selection keeps its existing face**: the 1 dp `uiAccent` border on the card and the 15 % white tonal shift.
5. **Marker card only**: the icon/pick `IconButton` moves out of the header's leading edge into the trailing
   action cluster, as its first child immediately left of the pin button; it normalises to 36 dp to match the
   cluster; the leading icon slot and the 4 dp `MARKER_HEADER_ICON_GAP` start padding on the coordinate text go.

## 3. The two cards, after the change

The glyph sits on the title row, at the name's own left inset. `◉` marks the 24 dp glyph slot.

```
┌─ TrackCardContent ─────────────────────────────────────────────────────┐
│ ▌ │ 2026-10-05  09:12→09:41           1281 pts      [pin][▶][⬆] ›      │  header: 11sp muted + 36dp icons
│ ▌ ├──────────────────────────────────────────────────────────────────────│
│ ▌ │ ◉  Morning run                                                    │  title: 24dp glyph + 15sp SemiBold
│ ▌ │    Add a comment…                                                 │  13sp muted
│ ▌ ├──────────────────────────────────────────────────────────────────────│
│ ▌ │  1h 02m 0s   │   48m 12s   │   5.1 kn                             │  StatCell grid
│ ▌ │  4.20 nm     │   3m 4s     │   9.8 kn                             │
└─────────────────────────────────────────────────────────────────────────┘
   4dp accent bar
```

```
┌─ MarkerCardContent ────────────────────────────────────────────────────┐
│ ▌ │ [48.1234, 2.2345] 📍                    [🎨][pin][edit] ›           │  header: coordinate text, pick moved into the cluster
│ ▌ ├──────────────────────────────────────────────────────────────────────│
│ ▌ │ ◉  Port entrance                                                  │  title: 24dp glyph + 15sp SemiBold
│ ▌ │    Add description…                                               │
└─────────────────────────────────────────────────────────────────────────┘
```

**Selected face** (unchanged): the 1 dp `uiAccent` border on the card, the 15 % white tonal shift, and the
glyph itself becoming the 24 dp `uiAccent` circle bearing the white 16 dp `Icons.Filled.Check`.

## 4. The slot / API change

- **`ListOverlayScaffold` gains `typeIcon: ((T) -> ImageVector)? = null`** — default null, so a caller whose
  list has no type is unaffected and renders no glyph.
- **The `cardContent` slot is extended** from `(T, onLongPress: (() -> Unit)?)` to hand the consumer the
  resolved icon and the selection state:

  ```kotlin
  cardContent: @Composable (
      item: T,
      typeIcon: ImageVector?,
      isSelected: Boolean,
      onSelect: (() -> Unit)?,
      onLongPress: (() -> Unit)?
  ) -> Unit
  ```

- **The scaffold resolves and owns `onSelect`**: in normal mode (and only when `multiActions` is non-empty) it
  is `{ enterMultiselect(item.id) }`, which enters multiselect and selects that item; in multiselect mode it is
  `null`, because the existing full-card tap interceptor already owns the tap there.
- **New shared component `ListTypeGlyph`** ([`ListTypeGlyph.kt`](../../app/src/main/java/ykws/android/maro/ui/components/ListTypeGlyph.kt)) —
  one 24 dp slot: the type icon in normal mode, the accent circle + white check when selected:

  ```kotlin
  @Composable
  fun ListTypeGlyph(
      icon: ImageVector,
      isSelected: Boolean,
      onSelect: (() -> Unit)?,
      modifier: Modifier = Modifier
  )
  ```

  Its selected state reads the existing `cd_selected`; its normal state, clickable only when `onSelect` is
  non-null, reads the new `cd_select` — both in `values/` and `values-fr/`.

## 5. Behaviour

- **Normal mode**: the glyph's `onSelect` enters multiselect and selects the item; the card's long-press still
  does the same. The glyph is the quick door, the long-press the full-size one.
- **Multiselect mode**: the glyph carries no handler (the card's tap interceptor toggles the item); it only
  reports state — type icon when unselected, accent check when selected.
- **Live card**: the glyph never renders — a live card travels the `liveCardContent` slot, which does not go
  through `cardContent` — so it is inert there by construction.
- **The 24 dp glyph sits under the 48 dp touch minimum** on purpose: the card's long-press remains the
  full-size selection door and the glyph is the quick one. The row is **not** enlarged to 48 dp; the note is
  stated here rather than paid for in card height.
- **Other `TrackCardContent` / `MarkerCardContent` callers** (`OverlayLayer.kt`, `MarkerDrawer.kt`) pass no
  type icon, take the defaults and render no glyph — they are outside this feature's two list surfaces.

## 6. Files

- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — the `typeIcon` parameter, the
  extended `cardContent` slot, the check-mark Box deleted (the tonal shift and the border stay).
- `app/src/main/java/ykws/android/maro/ui/components/ListTypeGlyph.kt` — new.
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — supply `typeIcon`, render the glyph
  leading the name in `TrackCardContent`.
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — supply `LocationOn`, render the
  glyph leading `marker.name`, move the pick button into the trailing cluster at 36 dp.
- `app/src/main/res/values/strings.xml` · `app/src/main/res/values-fr/strings.xml` — `cd_select` added.
- `docs/ui-lists-guidelines.md` · `docs/ui-drawer-guidelines.md` §9 · `docs/ui-component-guidelines.md` §5.9.

## 7. Verification

- **In reach:** `apk-build.bat` green with no new warning naming a touched file; the scoped
  `:app:testDebugUnitTest` run for `ui.components` and the touched map classes.
- **Not in reach:** anything visual — the glyph's tint and weight against the name, the title row's settled
  height at 24 dp, the marker cluster's new order, and the selected morph. All of it needs a device.

## 8. Open device checks

- The glyph reads as a type cue and not as a stray control on both cards, route and track glyphs included.
- The 24 dp glyph, the title and the header sit on one axis with no clipped or doubled row height.
- A glyph tap enters multiselect and selects exactly its own item; a subsequent card tap toggles.
- The check morph lands on the glyph, top-left of the title, with the border and tonal shift alone on the card.
- The marker card's pick button, now first in the trailing cluster, opens the picker and keeps the cluster even.

## 9. Header-line trial (2026-10-06) — the glyph moved to the header row

**A trial, not a settled decision** — it suspends §2's decision 1 on the device rather than here. By the
user's word of 2026-10-06 the 24 dp type glyph leaves the title line and becomes the **first child of the
header row** on both card contents: ahead of the track card's date/time text and of the marker card's
coordinate text, a 6 dp gap between glyph and text. The track card's date/time `Text` takes `weight(1f)` so
the header keeps its one line; the marker card's coordinate `Text` was already weighted. Everything else
stands — the 4 dp accent bar, the glyph's check morph, the 1 dp `uiAccent` border and the 15 % tonal shift,
the tap behaviour (`onSelect` in normal mode, the scaffold's interceptor in multiselect), the inert glyph on
a live card, the marker cluster's order and sizes, and [`ListTypeGlyph`](../../app/src/main/java/ykws/android/maro/ui/components/ListTypeGlyph.kt)
itself. Change sites: [`TrackCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt)
and [`MarkerCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt). The
guidelines (`docs/ui-lists-guidelines.md`, `docs/ui-drawer-guidelines.md` §9, `docs/ui-component-guidelines.md`
§5.9) **still describe the title-line placement** and are held until the user decides in situ.

Target shape, track card:

```
┌─┬───────────────────────────────────────────────┐
│▌│ ◈ 2026-10-05  08:12→08:41    812 pts   ⏵ ⤴  ›  │
│▌│                                               │
│▌│ La Salis morning                             │
│▌│  Add a comment…                               │
│▌│  ──────────────────────────────────────       │
│▌│  Total 1h 12m │ Nav 58m │ Avg 5.4 kn          │
└─┴───────────────────────────────────────────────┘
```

## 10. Variant B, reshaped — the leading door (2026-10-06) — built, no device pass

The user rejected variant B's fixed **28 dp rail** as too wide and gave the exact shape on
2026-10-06: a **6 dp bar**, and the card's **whole left edge** — the bar plus the content's own
leading padding — as the selection door, with no type glyph. This replaces the 28 dp rail; it is not
variant A.

- **Bar: 6 dp wide**, full height, at the card's leading edge, its fill the item's own colour
  (`accentColor` for a track, `MarkerColors.of` for a marker) — unchanged in kind from the 4 dp it
  replaces.
- **The door: 14 dp.** One full-height leading zone, the bar's 6 dp plus the content's own leading
  padding — the track column's `start = 8.dp` and the marker's `MARKER_CONTENT_PAD_H = 8.dp` — folded
  into the zone. Both cards set their content column's leading padding to 0, so the text keeps the
  place the 8 dp gave it (the track card's inner rows keep their own 8 dp) and the zone is the card's
  whole left edge.
- **The zone is the door.** It carries `combinedClickable`: a tap enters multiselect and selects the
  item through the scaffold's `onSelect`, a long-press carries the card's own `onLongPress`. It
  carries the `cd_select` name when a door exists, and draws **no pointer handling at all** when both
  callbacks are null — which is what the drawer and inspect call sites pass.
- **Constant width, both modes.** The zone never resizes, so no card reflows as selection comes and
  goes.
- **Selection: the check sits at the zone's top, spanning its full width** — a disc of the item's own
  colour, so the 6 dp bar reads as if it widens at its top to hold it — inset by the content's own
  top padding (2 dp on both cards) to line up with the header line. The disc is 14 dp, fitted to the
  zone, and carries the white `Icons.Filled.Check`. The variant B rim is dropped.
- **One home.** [`ListSelectionRail`](../../app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt)
  is reshaped into this zone and stays the single home; no markup is duplicated in the cards. The
  variant B rail shape (`ListSelectionRailWidth`'s 28 dp and its 24 dp check sizing) is gone,
  replaced by `ListSelectionZoneWidth` = 14 dp and a 6 dp bar.
- **Unchanged:** the 1 dp `uiAccent` border and the 15 % tonal shift, the multiselect tap
  interceptor, the live card's inertness, the marker cluster's order and sizes, and `ListTypeGlyph` /
  the scaffold's `typeIcon` slot (variant A's). The glyph stays off both card faces.

Target shape, track card (selected door shown):

```
┌──┬──────────────────────────────────────────────┐
│✓ │ 2026-10-05  08:12→08:41    812 pts  ⏵ ⤴  ›   │  ✓ = 14 dp disc at the door's top, header line
│▌ │                                              │
│▌ │ La Salis morning                             │
│▌ │  Add a comment…                              │
│▌ │  ──────────────────────────────────────      │
│▌ │  Total 1h 12m │ Nav 58m │ Avg 5.4 kn         │
└──┴──────────────────────────────────────────────┘
 14 dp door = 6 dp bar (bare accent when unselected) + 8 dp folded content padding
```

**The leading width each card moved from variant B.** The door is 14 dp against the 28 dp rail, so
every line regains 14 dp; against the pre-rail 4 dp bar plus 8 dp padding (12 dp) the text moves out
only ~2 dp. The drawer and inspect cards (`OverlayLayer.kt`, `MarkerDrawer.kt`) share these contents,
so variant B's 24 dp bleed there is down to that same ~2 dp.

**Risks named.**

- **The disc's contrast.** The disc is the item's own colour, so a marker whose rail is white carries
  a white check, and a past-cap track's faint `uiTextMuted` rail carries a low-contrast one. Variant
  B's white rim answered this; this shape drops it by the user's word — whether it reads in situ is a
  device call.
- **The check's size.** A 14 dp disc holds a 10 dp check; whether it reads as the knob at that size is
  a device call.
- **The door's gesture ownership.** The zone carries its own `combinedClickable`, a child of the
  card's own, itself inside the swipe detector's ancestor. Analysis: the ancestor's horizontal drag
  wins once the finger passes touch slop, cancelling the zone's tap, so the swipe is not stolen; the
  zone's long-press fires first and routes to the same `onLongPress`. Not provable off-device.

**Change sites:** [`ListSelectionRail.kt`](../../app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt)
(the zone, the 6 dp bar, the 14 dp disc);
[`TrackCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) and
[`MarkerCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) —
the folded leading padding (content column `start` → 0).

**Verification:** `apk-build.bat` green (`:app:compileDebugKotlin` executed, no warning naming a
touched file); scoped `:app:testDebugUnitTest --tests "ykws.android.maro.ui.components.*"` green. The
guidelines (`docs/ui-lists-guidelines.md`, `docs/ui-drawer-guidelines.md` §9,
`docs/ui-component-guidelines.md` §5.9) stay held until the shape is settled.

## 11. The check leaves the door for the header's leading slot (2026-10-06) — built, no device pass

By the user's word of 2026-10-06 the selection check leaves the leading door and takes the **leading
slot of the card's first line**, and the selection border widens a step.

- **The door is the bar and its tap zone alone.** [`ListSelectionRail`](../../app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt)
  keeps the 6 dp accent bar, the 14 dp width, `combinedClickable`, the `cd_select` name and the
  null-callback inertness; it draws no disc, and its `topInset` parameter is gone with the disc it
  inset.
- **The check leads the first line at its original size**: a 24 dp `uiAccent` disc bearing the white
  16 dp `Icons.Filled.Check`, `cd_selected` — the face the scaffold's old corner check carried. It is
  the **first child of the header `Row`** in
  [`TrackCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) and
  [`MarkerCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt),
  ahead of the date/time and the coordinates, with a 6 dp gap before the text.
- **Selected only.** The check is drawn only while the card is selected, so an unselected card reserves
  no slot and spends no width; the consequence is that the header's text **shifts right by the disc
  plus its 6 dp gap when selection arrives**.
- **One home.** `ListTypeGlyph` is deleted — unreferenced since variant A's glyph left both card faces
  — and the disc gets its own home,
  [`ListSelectionCheck`](../../app/src/main/java/ykws/android/maro/ui/components/ListSelectionCheck.kt),
  read by both header rows. No face is drawn twice.
- **The selection border widens to 2 dp** ([`ListOverlayScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt),
  was 1 dp) so the picked card's `uiAccent` outline reads more strongly. The 15 % tonal shift is
  untouched.
- **Unchanged:** the tap interceptor, the live card's inertness, the marker cluster's order and sizes,
  and the header rows' content otherwise.

**Change sites:** `ListSelectionRail.kt` (disc and `topInset` out), `ListSelectionCheck.kt` (new),
`ListTypeGlyph.kt` (deleted), `TrackHistoryOverlay.kt` / `MarkerManagementOverlay.kt` (check into the
header row), `ListOverlayScaffold.kt` (border 2 dp). The guidelines stay held.

**Verification:** `apk-build.bat` green with no new warning naming a touched file; scoped
`:app:testDebugUnitTest --tests "ykws.android.maro.ui.components.*"` green.

## 12. The invert chip joins the multiselect header (2026-10-06)

By the user's word of 2026-10-06 the multiselect header gains an **invert selection** control,
sharing the header row with the existing all/none chip.

- **`invertSelection()` joins `selectAll()` / `deselectAll()`** in
  [`ListOverlayScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt).
  It inverts over the **same non-live set** the other two use —
  `items.filter { !it.isLive }.map { it.id }` — so the new set is that list minus the current
  `selectedIds`.
- **The empty-invert rule.** If the inverted set is **empty** (every non-live item was already
  selected), the selection is cleared and multiselect is exited, exactly as deselecting the last
  item and `deselectAll()` already do — the mode never stands with nothing picked. Otherwise the
  selection is replaced by the inverted set.
- **Placement and label.** A second `TextButton` sits **immediately left of the all/none chip**, in
  the same idiom — `uiAccent` text at 14 sp SemiBold — and carries its own label:
  `multiselect_invert` = **"Invert"** (`values/`) / **"Inverser"** (`values-fr/`). No icon asset is
  added; the word is the whole control, read through the string id, no literal in Kotlin.
- **Same gate.** The chip is drawn under the same `nonLiveCount > 0` gate as the all/none chip. The
  "N selected" count and the all/none label both recompute on their own after an invert; nothing
  caches them.
- **No height change, no wrap.** The "N selected" `Text` gains `maxLines = 1`, keeping its
  `weight(1f)` — a narrower slot ellipsises the count instead of wrapping it onto a second line or
  growing the header.
- **Unchanged:** the door, the check, the 2 dp border and the card contents.

**Change sites:** `ListOverlayScaffold.kt` (the `invertSelection()` function, the header chip, the
count's `maxLines`); `values/strings.xml` · `values-fr/strings.xml` (`multiselect_invert`). The
guidelines stay held.

## 13. The door's touch zone widens to 24 dp (2026-10-06) — built, no device pass

By the user's word of 2026-10-06 the door's **pointer area** grows to 24 dp while its **visuals stay at
14 dp**: the user rejected widening the drawn rail, so the 6 dp accent bar and the 14 dp visual zone are
untouched, the content keeps starting 14 dp from the card's left, and only the pointer band becomes 24 dp.

- **The zone: 24 dp wide, full card height, anchored at the card's leading edge.** It carries the door's
  `combinedClickable` — a tap enters multiselect through `onSelect`, a long-press fires the card's own
  `onLongPress` — and the `cd_select` name, exactly as
  [`ListSelectionRail`](../../app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt) did
  before. The band's trailing **10 dp overlaps the card body**, the accepted cost of the choice: a tap in
  those 10 dp enters multiselect instead of opening the item.
- **One home.** The pointer handling left the rail: `ListSelectionRail` now draws the 6 dp bar alone (no
  `isSelected`, `onSelect` or `onLongPress`), and `ListSelectionTouchZone` owns the tap, the long-press and
  the name. `ListSelectionZoneWidth` stays 14 dp; the new `ListSelectionTouchWidth` is 24 dp.
- **An overlay in the outer `Box`, drawn after the row** — a `BoxScope` extension using `matchParentSize()`
  so it consumes no layout width (a Row child would have, defeating the point). It is the marker card's
  outer `Box` (which wraps its picker dialog) and the track card's (which wraps nothing else).
- **Inert without a door.** Both callbacks null — the drawer
  ([`MarkerDrawer`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt)) and inspect
  ([`OverlayLayer`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)) call sites — emits
  **no node at all**, so those cards keep their present behaviour exactly.
- **Two things left intact.** The scaffold's multiselect tap interceptor, drawn above the card content,
  still owns the tap while selection is active (and `onSelect` is null then); the ancestor horizontal drag
  detector still wins once a drag passes touch slop, so a drag started inside the 24 dp band still deletes.
- **Unchanged:** the 2 dp `uiAccent` border, the first-line check
  ([`ListSelectionCheck`](../../app/src/main/java/ykws/android/maro/ui/components/ListSelectionCheck.kt)),
  the header and its invert chip, the 15 % tonal shift, and the marker cluster's order and sizes.

**Change sites:** `ListSelectionRail.kt` (the rail reduced to the bar; `ListSelectionTouchWidth` and
`ListSelectionTouchZone` added);
[`TrackCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) and
[`MarkerCardContent`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) — the
rail call loses its door arguments and the touch zone joins the outer `Box`. The guidelines stay held.

**Verification:** `apk-build.bat` green with no new warning naming a touched file; scoped
`:app:testDebugUnitTest --tests "ykws.android.maro.ui.components.*"` green.

## 14. The two text chips carry the smart states, and invert carries the clear role (2026-10-06) — built, no device pass

By the user's word of 2026-10-06 the multiselect header keeps its two `TextButton` chips, right of the
"N selected" count in the same order — invert first, then select all — each with a smart state, and the
clear role folds onto invert rather than becoming a third control.

- **Invert** (`multiselect_invert` = **"Invert"** (`values/`) / **"Inverser"** (`values-fr/`)) sits
  immediately left of select-all. Its label is `multiselect_clear` once every non-live item is selected
  — **the same `invertSelection()` action, only the word changes**, so nothing new is wired. It stays
  enabled throughout; inverting a full selection clears it and exits multiselect, exactly as deselecting
  the last item and the retired `deselectAll()` did.
- **Select all** (`multiselect_select_all`) sits **right of the count**, after invert. **Smart state:**
  enabled only while the selection is **partial** (`enabled = !allSelected`); once every non-live item is
  selected its label dims to **0.25 alpha** — the list's own dim idiom — because its action would then
  be a no-op.
- **The idiom.** Both are `TextButton` chips in the accent text idiom these controls already wore —
  `uiAccent` text at 14 sp SemiBold — read through their string ids, no literal in Kotlin.
- **No third control, no flip on select-all.** The old "Deselect all" state retires: `deselectAll()`
  is deleted, and `multiselect_deselect_all` is removed from both locales so no dead key survives.
  `invertSelection()` is unchanged — it already exits multiselect when the inverted set is empty, which
  is exactly the all-selected case.
- **New string:** `multiselect_clear` = **"Clear"** (`values/`) / **"Effacer"** (`values-fr/`, the
  app's own word, from `filter_clear`).
- **No wrap, no growth.** The "N selected" `Text` keeps its `weight(1f)` and `maxLines = 1`.
- **The stale comment is corrected** — it now names the invert and select-all text chips rather than
  the single select-all chip.
- **Unchanged:** the door, the touch zone, the first-line check, the 2 dp border and the card contents.

**Change sites:** `ListOverlayScaffold.kt` (the two `TextButton` chips, the `deselectAll()` deletion, the
comment); `values/strings.xml` · `values-fr/strings.xml` (`multiselect_deselect_all` out,
`multiselect_clear` in). The guidelines stay held.

**Verification:** `apk-build.bat` green with no new warning naming a touched file; scoped
`:app:testDebugUnitTest --tests "ykws.android.maro.ui.components.*"` green.
