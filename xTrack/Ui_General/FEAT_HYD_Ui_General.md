# Context Hydration — Ui_General — 2026-10-06

**Last Bake:** 2026-10-06 12:38 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added (the two new icons are hand-written `ImageVector`s already in place), no machine-shaped data file was opened, every write followed an order (the branch work on `feature/list-selection`, the docs reconciliation and the bake on the user's own directive, the commit and push on the same word), the device was never touched (builds only), and every claim written about the code came from a file read this session. The gaps are named rather than hidden: nothing is device-validated, and no Ask verdict is recorded for this session's payload.

## State

One branch, `feature/list-selection`, cut from `origin/develop`, carrying no upstream yet — the first `#push` writes its own name. One thing shipped: the list-selection rework, whose plan of record is `xTrack/Ui_General/261006_FEAT_PLN_Ui_General_list-selection-type-icon.md`, its §10–§14 recording the trials.

**The per-item door is the card's leading edge.** `ListSelectionRail` draws the item's own colour as a 6 dp accent bar inside a 14 dp visual zone that folds in the content's former 8 dp leading padding; `ListSelectionTouchZone` carries the 24 dp touch band as an overlay above the content (`matchParentSize()`), its last 10 dp overlapping the card body by design, so no layout width moves. A tap enters multiselect and selects through `onSelect`, a long-press resolves as the card's own `onLongPress`; both callbacks null — the drawer and inspect call sites — emits no node.

**The picked card and the header.** The selected card wears a 2 dp `uiAccent` border (was 1 dp) and the 15 % tonal shift, and `ListSelectionCheck` draws the 24 dp `uiAccent` disc bearing the white 16 dp check at the head of the first line, selected only, so an unselected card reserves no slot. The multiselect header carries Close (X), the "N selected" count and two text chips — invert (its word `multiselect_invert`, or `multiselect_clear` once everything is picked) and select all (`multiselect_select_all`, enabled only while the selection is partial, dimmed to 0.25 alpha when full); `multiselect_deselect_all` and the dead `deselectAll()` are gone. The batch **export** action wears the card's own `Icons.Filled.Upload` glyph — the one the list item's `cd_export_gpx` button draws — so one action reads as one icon on both surfaces.

**What left.** The type-glyph path through the scaffold and both card contents, `ListTypeGlyph`, and the marker header's leading icon/pick button — which now leads the trailing cluster at 36 dp, ahead of pin and edit, the coordinate text owning the header's left. The scaffold's `cardContent` slot hands the consumer `(item, isSelected, onSelect, onLongPress)` with no `typeIcon`.

`docs/ui-lists-guidelines.md`, `docs/ui-drawer-guidelines.md` §9 and `docs/ui-component-guidelines.md` §5.9 were reconciled to the shipped shape in the same session, and the bake folded the settled `### action faces` section and merged the epic's duplicate `## Implemented` heading. `apk-build.bat` BUILD SUCCESSFUL with no new warning naming a touched file and the scoped `ui.components` suite green; the two `TrackOutlineTest` reds on the branch's base are untouched by this change.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt` — the 6 dp bar / 14 dp zone and `ListSelectionTouchWidth`'s 24 dp touch band
- `app/src/main/java/ykws/android/maro/ui/components/ListSelectionCheck.kt` — the 24 dp check disc
- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — the `cardContent` signature, the 2 dp border, the two header text chips
- `app/src/main/java/ykws/android/maro/ui/components/ListTypeGlyph.kt` — deleted
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` · `MarkerManagementOverlay.kt` — the door, the check, the marker cluster order
- `app/src/main/java/ykws/android/maro/ui/icons/SwapHoriz.kt` · `SelectAll.kt` — deleted
- `docs/ui-lists-guidelines.md` · `docs/ui-drawer-guidelines.md` §9 · `docs/ui-component-guidelines.md` §5.9
- `xTrack/Ui_General/261006_FEAT_PLN_Ui_General_list-selection-type-icon.md` — the plan and its §10–§14 shipped records

## Next Step

The device pass, owed and unstarted: the door's tap and long-press against the ancestor swipe detector, the 10 dp overlap, the check's shift of the header line, the 2 dp border's weight, and the two header chips' states.
