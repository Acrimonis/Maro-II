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
