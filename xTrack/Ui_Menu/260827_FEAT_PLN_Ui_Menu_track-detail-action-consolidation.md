# 260827 — Track / Marker action & card normalization

- **Feature:** Ui_Menu
- **Status:** implemented — interaction refinement done, build passing
- **Branch:** feature/persist-track-tweak
- **Date:** 2026-08-27

## Decisions

- **F1** — remove Share from the track detail header.
- **F2** — export glyph = `Upload` ("Export GPX") everywhere.
- **F3** — list card and detail card are the **same card** (inline actions + inline editing).
- **F4** — add Pin inline to the marker card.
- **Delete** — swipe in the list; trash icon in the detail header.
- **Marker text** — name + description editable inline; the wizard keeps editing name/description and
  geometry (both write the same persisted fields — last write wins, no conflict).

## Normalized model

Card (identical in list and detail):

- **Track card:** `Pin · Resume · Export (Upload)` + inline-editable name/comment.
- **Marker card:** `Pin · Icon · Edit(wizard)` + inline-editable name/description.

Delete: swipe (list) / header trash (detail). Detail header: `Close · Title · Delete`.

## Detailed changes

1. **Track detail header** ([`OverlayLayer.kt:380`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:380),
   [`:460`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:460)) — remove the Share `IconButton`, keep Delete.

2. **MarkersViewModel — inline text update** ([`MarkersViewModel.kt:631`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:631)):
   add `updateMarkerText(id, name = null, description = null)` that patches the marker and persists via
   `repo.update(updated)` + reload (mirror `updateMarker`), without touching the wizard form or geometry.

3. **Extract `MarkerCard`** from the list card ([`MarkerManagementOverlay.kt:340`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:340)):
   - inline-editable **name** and **description** (tap → `TextField` → IME Done commits, Back reverts),
     mirroring [`TrackCardContent`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:416);
   - actions `Pin · Icon · Edit` (+ chevron in list only);
   - `Pin` → `viewModel.togglePin`, `Icon` → existing `onSetIcon`, `Edit` → existing `onEdit` (wizard).

4. **Use `MarkerCard` in the detail** ([`MarkerDrawer.kt:137`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:137)):
   replace the read-only info card with `MarkerCard`; keep the direction/distance line and prev/next pills.

5. **Marker detail header** ([`MarkerDrawer.kt:147`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:147)):
   remove Pin, Icon, Edit; keep only Delete.

6. **Wizard** — unchanged; it keeps `Name`/`Description` steps plus geometry, writing the same fields as the
   inline path via `repo.update`.

## Review

- **Consistency:** inline and wizard both call `repo.update`, so the two text paths can't diverge (last write wins).
- **Sync:** `updateMarkerText` reloads `_allMarkers`/`_markers` exactly like `updateMarker`, so list and drawer update together.
- **No functionality loss:** Pin/Icon/Edit stay reachable from the card; Delete stays swipe (list) + header trash (detail).
- **Track side:** only the header Share removal; the track card is already the shared, inline-editable card.
- **Out of scope / unchanged:** the wizard's `pinned = form.icon != null` coupling; marker multiselect (pin/delete);
  track multiselect.
- **Editing exclusivity:** name and description edit states are mutually exclusive (one field at a time), matching
  the track card's behaviour; BackHandler reverts the in-progress edit.

## Key files
- [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:375)
- [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:416)
- [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:631)
- [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:340)
- [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:137)
- [`WizardDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:213)

## Implemented

- [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) — removed Share from the track detail header (Delete kept); added and wired `onUpdateMarkerText`.
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — passes `onUpdateMarkerText` to `markersViewModel.updateMarkerText`.
- [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) — added `updateMarkerText(id, name, description)` (patch + `repo.update` + reload).
- [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) — `MarkerCardContent` is now `internal`, with inline name/description editing, `Pin · Icon · Edit` actions, and a `showChevron` flag; list call site wired.
- [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) — header reduced to Delete only; body renders the shared `MarkerCardContent` (direction/distance + prev/next kept).

Build: `gradlew assembleDebug` — **BUILD SUCCESSFUL**.

## Interaction refinement (final)

**Inline edit on double-click, everywhere (marker + track):**
- Name/description (marker) and name/comment (track): single tap → `onTap`; double tap → inline edit.
  Use `Modifier.combinedClickable(onClick = { onTap?.invoke() }, onDoubleClick = { enter edit })`.
- Seed the field text from the current value on edit entry (both cards — fixes stale text).
- Empty marker description shows a muted "Add description…" placeholder (double-tappable), mirroring the track card's
  "Add a comment…" so a blank description can still be created inline.

**Chevron (marker card, list only):**
- Bottom-right corner gutter (≈ 48 dp tappable Box, chevron icon centred), `onClick = onTap`; NOT full-height.
- Collapsed (hidden) in detail via `showChevron = false`.

**BackHandler precedence (Back reverts edit, doesn't close drawer):**
- `MarkerDrawer`: move close `BackHandler` before `when(drawerState)`.
- Track detail: hoist editing state — `TrackCardContent` exposes `onEditingChange(Boolean)`, forwarded through
  `OverlayLayer`/`TrackHistoryOverlay`; `MapScreen` holds `trackIsEditing` and gates the close BackHandler
  (`if (trackDrawerState.isOpen && !trackIsEditing)`).

**Cleanup:**
- Remove unused imports in `MarkerDrawer`.
- Name-clear asymmetry: no change (non-blank parity with wizard).

## Implemented (interaction refinement)

- [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) — name/description
  double-click-to-edit (single tap = `onTap`), seeded on entry, blank-description placeholder, chevron corner gutter (48 dp → `onTap`).
- [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) — name/comment double-click-to-edit
  + seeded on entry.
- [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) — close `BackHandler` moved before
  `when(drawerState)`.

Deviations:
- Track-drawer BackHandler hoisting was verified unnecessary — [`MapScreen.kt:2139`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2139)
  composes before `OverlayLayer` at [`MapScreen.kt:2233`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2233), so the card's
  revert handler already wins; skipped.
- Unused-import cleanup left as compiler warnings (cosmetic).

Build: `gradlew assembleDebug` — **BUILD SUCCESSFUL**.

## Implemented

- [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) — removed Share from the track detail header (Delete kept); added and wired `onUpdateMarkerText`.
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — passes `onUpdateMarkerText` to `markersViewModel.updateMarkerText`.
- [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) — added `updateMarkerText(id, name, description)` (patch + `repo.update` + reload).
- [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) — `MarkerCardContent` is now `internal`, with inline name/description editing, `Pin · Icon · Edit` actions, and a `showChevron` flag; list call site wired.
- [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) — header reduced to Delete only; body now renders the shared `MarkerCardContent` (with direction/distance + prev/next kept).

Build: `gradlew assembleDebug` — **BUILD SUCCESSFUL**.
