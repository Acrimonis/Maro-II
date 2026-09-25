---
feature: Markers
type: discussion
created: 2026-09-25
branch: feature/marker-tile
status: implemented
---

# Marker tile — header layout rework (discussion)

## Topic

In the marker list tile — also rendered by the marker detail drawer — the POI icon slot sits
in the middle of the action cluster. It reads as a third action button sandwiched between
pin and edit, even though it is also the marker's visual identity.

Target:

```
[icon / icon action] [geometry glyph] [coords]        [pin] [edit] [chevron]
```

Current:

```
[geometry glyph + coords]                    [pin] [icon / icon action] [edit] [chevron]
```

## Current state

- Tile body: `MarkerCardContent` — `MarkerManagementOverlay.kt:259`
- Header row: `MarkerManagementOverlay.kt:302-374`
  - coordinate `Text` = `coordinateHeader(marker)` — `MarkerManagementOverlay.kt:496` — which is a
    single string: `"<geometry emoji> [lat, lng]"` (`📍` pin / `🎯` circle / `🛤️` corridor, from
    `MarkerGeometry.iconFor` — `UserMarker.kt:174`). The geometry glyph is **not** a discrete
    element today; it is a string prefix.
  - trailing cluster: pin 36dp (`:317-327`), icon picker 36dp (`:329-343`, 20sp emoji or
    `LocationOff` fallback), edit 36dp (`:354-364`), chevron 28dp (`:366-373`, `showChevron` only).
- Render sites (exhaustive — grep for `MarkerCardContent`):
  - marker list overlay `MarkerManagementOverlay.kt:198-210` (via `ListOverlayScaffold`)
  - marker detail drawer `MarkerDrawer.kt:232` (`showChevron = false`)
- Track twin: `TrackCardContent` — `TrackHistoryOverlay.kt:413` — same shell, duplicated by copy
  (12dp radius, 4dp accent stripe, 8/2dp padding, 11/15/13sp).

## What the target requires

1. Split `coordinateHeader()` into two pieces: a geometry glyph value and a plain coordinate
   string. The glyph becomes its own small `Text`/`Box`; coordinates lose their prefix.
2. Promote the icon picker to a leading slot — same behaviour (`IconPickerDialog`), own
   `contentDescription`, 36dp touch target, start-aligned after the 4dp accent stripe + 8dp pad.
3. Trailing cluster drops 3 buttons → 2 (pin, edit), freeing ~36dp of width for the coordinate
   text, which keeps `weight(1f)` + ellipsis.
4. Row height: unchanged at 36dp (action buttons remain the tallest child), so the drawer
   `MeasureHeight` probe stack is unaffected — no drawer-height regression expected.

## Decisions (2026-09-25)

- **Geometry glyph stays inline** — `coordinateHeader()` is not split; the glyph remains a string
  prefix of the coordinate text.
- **Leading icon action retained** — the icon-picker button is relocated, not made decorative and
  not folded into Edit (option A, not D).
- **Chevron** stays trailing and last.
- **One tile, both render sites** — the change lands in `MarkerCardContent` only; the detail drawer
  inherits it. The "dash" reference is dropped from scope.

Locked layout:

```
[icon action] [glyph + coords]        [pin] [edit] [chevron]
```

## Options considered — A chosen

| # | Option | Effect |
|---|--------|--------|
| A | As sketched: leading icon action, glyph inline, cluster pin + edit | Matches the request; 3 elements crowd the left edge |
| B | Leading icon **with the geometry glyph as a corner badge** | 3 elements → 2; type and identity stay co-located |
| C | Drop the inline glyph entirely (identity via leading icon + accent colour; geometry visible in the drawer) | Widest coordinate line; loses at-a-glance type in the list |
| D | Leading icon becomes decorative; icon picking moves into Edit | Removes the action-vs-decoration ambiguity; needs the wizard/Edit route to own icon choice |
| E | Keep the icon trailing, only reorder to `[glyph + coords] … [icon] [pin] [edit]` | Minimal diff, but does not fix the "icon between actions" confusion |

## Implementation notes

1. **Relocate, do not rebuild** — the header `Row` keeps `SpaceBetween` + `CenterVertically`; the
   icon-picker `IconButton` becomes the first child; the coordinate `Text` keeps `weight(1f)` +
   ellipsis; the trailing `Row` keeps pin + edit at 36dp with `spacedBy(2.dp)`.
2. **Hoist the dialog state** — `showIconPicker` moves to the composable body next to
   `editingField`, and `IconPickerDialog` is rendered once instead of inside the trailing `Row`
   scope (`MarkerManagementOverlay.kt:328-353`).
3. **No height change** — 36dp buttons remain the tallest child. Review-hop correction: the marker
   drawer has no `MeasureHeight` probe at all — `MarkerDetailContent` has a single call site and the
   panel is sized by `DrawerScaffold(wrapContent = !isLandscape)`; `MeasureHeight` survives on the
   track drawer only. The stale KDoc claiming otherwise sits at `MarkerDrawer.kt:236-239`.
4. **Emoji adjacency** — the 20sp POI icon and the 11sp geometry glyph that now directly follows it
   need a start gap (~6-8dp) between the leading button and the coordinate text, otherwise the two
   glyphs read as one crowded cluster.
5. **Empty POI icon** keeps the muted `LocationOff` fallback; rendering the geometry glyph in the
   leading slot instead would duplicate the inline glyph.
6. **Reclaimed width** — the trailing cluster loses one 36dp button plus 2dp spacing, all of which
   goes to the coordinate text. Corridor markers (`glyph [lat, lng] → [lat, lng]`) gain the most.

## Trade-offs to weigh

- **Affordance:** a tappable emoji at the start of a row reads as a leading avatar (decorative).
  Options A/B need a subtle "editable" cue; option D removes the question.
- **Empty state:** when `marker.icon == null` the leading slot shows the muted `LocationOff`
  glyph — at the far left that reads as a broken image more than it does in the trailing cluster.
  Consider the geometry glyph itself as the leading visual when no POI icon is set.
- **Consistency:** `TrackCardContent` is a near-identical twin; a header grammar change here
  widens the divergence unless the same grammar lands there too.
- **Doc drift found:** `ui-lists-guidelines.md` § Card Tap & Navigation Chevron describes the
  marker card chevron as a 48dp tappable gutter (`Box` + `clickable`); the code renders a plain
  28dp `Icon` inside the card's `combinedClickable`. Confirm which is canonical before touching
  this row.

## Open items

1. **Chevron contract** — `ui-lists-guidelines.md` § Card Tap & Navigation Chevron specifies a 48dp
   tappable gutter; the code renders a plain 28dp `Icon` inside the card's `combinedClickable`.
   Fix the doc to match the code, or implement the gutter? Decide before rewriting the row.
2. **Optional a11y** — the icon button carries a `contentDescription` only in its `LocationOff`
   branch; the emoji branch announces as an unlabelled button. Add a label while relocating?
3. **Track twin** — apply the same header grammar to `TrackCardContent`, or track separately?
4. `MarkerGeometry.iconFor` stays the single source of truth; glyph rendering stays emoji `Text`.

## Prospective files

- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` — header row + `coordinateHeader()`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — consumer (no change expected)
- `docs/ui-lists-guidelines.md` — marker card chevron/header spec
- `xTrack/Markers/FEAT_DSC_Markers.md`, `FEAT_HYD_Markers.md` — session state

## Review — Ask hop (2026-09-25)

Verdict: layout delivered on both render sites, no defect in the change itself. Findings are
recorded, never fixed here (scope lock):

| # | Finding | Origin | Status |
|---|---------|--------|--------|
| F1 | `MARKER_GEOMETRY_FONT_SIZE = 14.sp` (`MarkerManagementOverlay.kt:255`) has no reference left in the file | pre-existing on `origin/develop` | recorded |
| F2 | `MARKER_HEADER_ICON_GAP` sits at the tail of the `sp` block instead of beside the `dp` metrics (`:257`) | ours | nit |
| F3 | redundant `marker.icon!!` inside the `icon != null` branch (`:315`) | pre-existing | recorded |
| F4 | stale KDoc in `MarkerDrawer.kt:236-239` claiming a `MeasureHeight` probe that file never imports or calls | pre-existing | recorded |

Corrections this hop made to the plan above: the drawer call site is `MarkerDrawer.kt:272`, and the
marker panel is wrap-content with no probe in play. Recomposition was found neutral-to-better, the
`Box`-level dialog the sound shape, and multiselect unaffected by the relocation.

## Correction — icon flush with the title's left edge (2026-09-25)

User report: too much padding left of the icon action; it must be left-aligned with the title below it.

Cause: `IconButton` centres its glyph inside its own 36dp box, leaving a ~6dp inset *inside* the
control on top of the 4dp accent stripe and the 8dp column padding — so the icon's ink starts ~18dp
from the card's left edge while the title, description and belongs-to-track row all start at 12dp.

Fix: shift the button. **Do not re-order the row** — the icon action stays the header row's first
child and the coordinate `Text` keeps `MARKER_HEADER_ICON_GAP` as its own start padding.

```kotlin
// Half the IconButton's 36dp box minus the 24dp glyph box: cancels the control's internal
// centring inset so the icon's ink lands on the column's left edge, like the title.
private val MARKER_HEADER_ICON_INSET = 6.dp

IconButton(
    onClick = { showIconPicker = true },
    modifier = Modifier.size(36.dp).offset(x = -MARKER_HEADER_ICON_INSET)
) {
    // emoji branch / LocationOff branch unchanged
}
```

Arithmetic, card-relative: stripe 0-4dp; column content starts at 12dp; button box 6-42dp after the
offset, i.e. 2dp clear of the stripe so its ripple can never overlap it; glyph ink 12-36dp — flush
with the title. The coordinate `Text` keeps its 6dp start padding, so its ink starts at 48dp and the
icon-to-glyph ink gap stays the 12dp the user already saw.

Rejected alternative, recorded: re-ordering the row so the coordinates lead and the icon follows. It
does align the text, but it puts the icon action back among the action cluster — the exact confusion
this rework removed — and it breaks the locked layout above. That reorder was briefly written into
the working tree by an intermediate step and reverted in the following one; nothing was committed.

**Applied 2026-09-25** — `MARKER_HEADER_ICON_INSET` sits beside `MARKER_HEADER_ICON_GAP`, the leading
`IconButton` carries `Modifier.size(36.dp).offset(x = -MARKER_HEADER_ICON_INSET)`, the coordinate
`Text` owns the gap again, and `apk-build.bat` exits 0. Residual doubt from the hop, unacted on: the
20sp emoji's advance is close to, not equal to, the 24dp glyph box, so the emoji branch may sit about
a dp off the title's edge where the `LocationOff` glyph lands exactly.

## Round 2 — icon-to-text gap and the glyph's place (2026-09-25, discussion — NOT implemented)

User report: too much space between the icon action and the glyph plus coordinates, and the glyph
should read *after* the coordinates rather than before them. Target:

```
[icon action] [lat, lng] [glyph]              [pin] [edit] [chevron]
```

### Where the gap comes from

Card-relative, with the shipped fix in place: stripe 0-4dp; the button box 6-42dp after its inset
offset; a 24dp glyph centred in that box inks 12-36dp; the coordinate `Text` starts at 42 +
`MARKER_HEADER_ICON_GAP` (6dp) = 48dp. The visible gap is therefore about 12dp — the control's own
6dp right-side inset plus the explicit 6dp padding, and only the second half is a number anyone chose.

### Two ways to shrink it

| Option | Resulting ink gap | Touch target | Cost |
|--------|-------------------|--------------|------|
| A — reduce `MARKER_HEADER_ICON_GAP` 6 → 0-2dp | 6-8dp | kept at 36x36dp | the button's own inset becomes the only separation |
| B — narrow the leading button to 24dp wide and drop the inset offset | 6dp | drops to 24x36dp | trades the touch target away, and the offset must go with it |

Recommended: **A** with `MARKER_HEADER_ICON_GAP = 2.dp` — an ~8dp ink gap against a 24dp glyph, one
constant, no touch regression. Strongest objection against it: the residual separation then depends on
the glyph's own width, so a narrow emoji leaves the row tighter than the arithmetic suggests; 2dp is
the hedge against that, and the device pass is what confirms it.

### Glyph after the coordinates

`coordinateHeader()` is private with a single reader — the header `Text` — so the move is one edit per
geometry branch: `"$icon [${fmt(position)}]"` → `"[${fmt(position)}] $icon"`, likewise for circle and
corridor. No model, no other call site, no string resource.

Consequences to accept before ordering it:
- The glyph inherits the 11sp muted coordinate styling; it cannot be dimmed or sized separately while
  it stays inside the string.
- It lands immediately after the numbers, not pushed against the action cluster — the empty stretch
  `weight(1f)` leaves between the glyph and the pin/edit pair stays where it is.
- A corridor then reads `[p1] → [p2] 🛤️`, and the string grows by a couple of glyph advances, which
  matters only for the longest coordinates on the narrowest layout.

Nothing in this section is applied by the section itself.

## Round 3 — the gap closed by sizing the box to the glyph (2026-09-25, APPLIED)

User: still too much space, then "make a recommendation so the space between the icon and the coords
is consistent with the other spaces". Measured cause, card-relative: the leading control's slot was
12-48dp, its box drawn 6-42dp by the offset, a 24dp glyph inked 12-36dp, and the coordinate `Text`
inked from 50dp. The gap was 14dp — 6dp the glyph's own centring inset, 6dp the slot the offset
vacated, 2dp the constant — and the vacated slot is a direct consequence of round 1's inset offset,
which is why shrinking the constant alone bought almost nothing.

Chosen shape: the box becomes the glyph's width — `Modifier.size(width = 24.dp, height = 36.dp)` — the
offset and `MARKER_HEADER_ICON_INSET` are deleted, and the emoji branch carries `maxLines = 1` so a
wide glyph cannot wrap and double the row height. Slot and ink then coincide, so the gap is
`MARKER_HEADER_ICON_GAP` alone at 2dp — the card's own rhythm, matching the 2dp between pin and edit
and the 2dp between title and description. Accepted cost: that one control has a 24x36dp touch target
and a 24dp ripple; the escape, if the device pass finds it too small, is making the emoji a plain
glyph and moving icon picking into Edit.

Applied and built: `apk-build.bat` SUCCESSFUL. Open doubt: a 20sp emoji's advance can reach ~24-26dp,
so `maxLines = 1` plus the default clip may shave a pixel off a wide glyph — only the device pass
settles it.
