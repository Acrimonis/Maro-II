<!-- scope: feature -->

# List header icons — sort-order flip + fold sort into the filter workflow

## Context

The list headers (Track History and Marker Management via the shared
[`ListOverlayScaffold`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt)) currently
carry three glyphs: **FilterAlt** (opens the filter-axes menu), **FilterList** (opens the sort popover),
and **Refresh** (reset). The user wants more header room by (eventually) folding sort into the filter
workflow, and wants the sort icon to visually indicate the current sort order without any behavior change.

## Goal

A phased refactor. **Phase 1 (first, isolated):** leave everything exactly as it is except flip the
`FilterList` sort icon vertically when the sort is descending. **Phase 2 (later):** collapse the separate
sort icon into the filter workflow so the header shows one action icon instead of two.

## Phase 1 — flip the sort icon on descending (do first, minimal)

- In `SortControl` ([`ListOverlayScaffold.kt:136`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:136)) the sort icon is `FilterList`.
- Apply a vertical mirror **on the Icon composable only** so ascending flips the glyph and descending
  keeps it upright:
  `modifier = Modifier.size(ButtonColors.iconSizeDp.dp).scale(1f, if (state.descending) 1f else -1f).alpha(sortAlpha)`
- Import `androidx.compose.ui.draw.scale`.
- **Convention (user-confirmed):** descending = upright; ascending = mirrored. Rationale: the FilterList
  glyph is three stacked lines with the widest line at the TOP when upright
  ([`FilterList.kt:38`](app/src/main/java/ykws/android/maro/ui/icons/FilterList.kt:38)); the default sort is
  descending, and descending should show the big base at the top, i.e. the upright icon. So the icon is
  mirrored only when ascending.
- Everything else stays identical: tap target, popover, checkmark-on-selected pattern, tapping the
  selected field toggles direction, active/inactive alpha, behavior.

### Phase 1 verification
- Build via `apk-build.bat`.
- Manual: in both list headers, choosing a sort order that is descending shows the flipped icon;
  ascending shows it upright; filter/sort/reset behavior unchanged.

## Phase 1b — direction glyph on the selected sort item (small)

- In `SortControl`'s field list ([`ListOverlayScaffold.kt:165`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:165)), on the **selected row only**, add a right-aligned
  asc/desc glyph: leading checkmark (unchanged) + row label + `Spacer(weight 1f)` + directional icon.
- Icons: Material core `ArrowUpward` for ascending, `ArrowDownward` for descending (already available,
  no standalone file needed), tinted with the accent when selected.
- Mapping: ascending = ArrowUpward, descending = ArrowDownward (standard arrow convention; independent of
  the header FilterList mirror which keeps the wide-base-at-top rule for descending).
- Row stays tap-to-select / tap-selected-to-toggle-direction; the glyph updates with `state.descending`.
- Optional variant for confirmation: replace the leading checkmark with the direction arrow only (one
  glyph per selected row) instead of showing both checkmark and arrow.

### Phase 1b verification
- Build; manual: opening the sort menu, the selected field shows a right-aligned up arrow when ascending
  and a down arrow when descending; toggling by re-tap updates the arrow; other rows show no arrow.

### Phase 1b Ask-review notes
- Apply the trailing arrow to BOTH the general sort fields and the custom-field rows, using the same
  selected test (field + custom key).
- Row is fillMaxWidth; the weight spacer + trailing arrow on the selected row only causes no label shift.
- Recommended: keep the leading checkmark and add the trailing arrow (18–20 dp, accent tint, a11y
  contentDescription). Dropping the checkmark to arrow-only is a possible later simplification.
- `ArrowUpward`/`ArrowDownward` are core icons; up = ascending, down = descending, consistent with the
  header FilterList mirror.

## Phase 2 (later) — fold sort into the filter workflow

Design (to be confirmed separately before implementation):
- Replace the two header icons (FilterAlt + FilterList) with **one view-options icon** whose single popover
  holds two sections: **Sort** (field list; tapping a field selects it, tapping the selected field toggles
  asc/desc — already today's behavior) then **Filter** (the current filter axes).
- One active indicator on the single icon when either a non-default filter or a non-default sort is set;
  Reset clears both (keep the `Refresh` action).
- Chosen sort field keeps a marker (today a checkmark; consider an arrow that also encodes direction since
  Phase 1 already rotates the header icon).
- Reuse `FilterControl` and `SortControl` internals; applies to both Track History and Marker Management
  (shared scaffold) and keeps the menu drawer's filter row independent.

### Phase 2 options (recorded, not yet chosen)
- Option A: one view-options control, Sort section then Filter section, single active indicator.
- Option B: keep only the filter funnel and put the sort sections inside that same dropdown.
- Option C: keep two icons but relocate the sort control into the filter panel footer.

## Ask-review notes (Phase 1)

1. Prefer the true vertical mirror `Modifier.scale(1f, -1f)` over `rotate(180f)`: rotate 180 also flips
   left-right, which changes how the asymmetric FilterList glyph reads; scaleY keeps it a clean vertical
   flip. Apply on the Icon only in [`SortControl`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:136); import the needed draw modifier.
2. RESOLVED: mapping is descending = upright (big base at top), ascending = mirrored. The FilterList
   glyph's widest line sits at the top when upright, and the default sort is descending, so the default
   view keeps the icon upright.
3. Phase 1 keeps everything else byte-identical: tap target, popover, checkmark-on-selected, tap-selected-
   toggles-direction, active/inactive alpha.
4. Phase 2 (fold sort into filter) must be confirmed separately, including where Reset lives and the
   single active indicator semantics.

## Phase 2 — fold sort into the filter workflow (user confirmed: do the fold)

Goal: remove the separate FilterList (sort) icon from the list toolbar; keep ONE action icon
(FilterAlt) that opens a single panel containing both a Sort section and the Filter axes.

### Behavior
- Header: one icon (funnel) + Reset. Sort icon removed.
- Tapping the icon opens one popover, ordered: **Sort section first**, then **Filter section**.
- Sort section reuses today's field list + per-field default direction + re-tap to toggle + right-aligned
  direction arrow on the selected row (Phase 1b).
- Filter section reuses the current filter axes control.
- Active indicator: the single icon is bright when a non-default filter OR a non-default sort is active.
- Reset clears both sort and filter.
- Popover width/height grows to fit both sections (stacked vertically; side-by-side is an alternative).

### Consequences for earlier phases
- Phase 1 (header FilterList mirror) is obsolete once the sort icon is removed — it is dropped with the
  removal. Phase 1b (row arrow) stays.
- SortControl/FilterControl internals are reused as panel sections; remove the separate FilterList icon
  button and its own Popup in the header.

### Design (locked after discussion)
- The single funnel icon opens the popover on the **Filter view** by default.
- A small sort glyph is pinned **top-right inside the popover**; tapping it drills into the **Sort view**
  (the popover body swaps to the sort field list).
- A back affordance returns to the Filter view (top-left back arrow, or the sort glyph toggling back).
- Header keeps one icon (funnel) + Reset; the FilterList sort icon and its header popover are removed.
- Phase 1 header mirror is dropped with the removal; Phase 1b row arrows stay.

### Open decisions
1. Back affordance: top-left back arrow vs the top-right sort glyph toggling between the two views.
2. Reset stays in the header (recommended) vs inside the panel.
3. Panel dismisses on selection (current behavior) — keep.
4. Ask finding: the header also has a redundant quick Direction-toggle IconButton
   ([`ListOverlayScaffold.kt:655`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:655)) next to sort — decide whether the fold also removes it (recommended, since sort view covers direction via the selected-row arrow and re-tap) or keeps it as a header quick toggle.

## Phase 3 — Link/LinkOff toggle in the list headers (pure flip)

Re-link policy (confirmed): re-linking only flips the link flag ON; it does NOT copy either filter to the
other. The two filters keep their values until the next edit while linked, and that edit is written to both.

- Add Link/LinkOff to the shared list header ([`ListOverlayScaffold.kt:649`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:649)) beside the filter controls, showing that type's
  link state; tapping flips it.
- Pass `filterLinked: Boolean` + `onToggleLink: () -> Unit` through ListOverlayScaffold and the list
  overlays (Track History, Marker Management), OverlayLayer, and MapScreen.
- Change BOTH the menu and list toggle handlers in MapScreen to a pure flip
  (`trackFilterLinked = !…`, `markerFilterLinked = !…`) — remove the current copy of list←map on re-link
  from the menu handlers.
- Applies to each type independently (Tracks, Markers).

## Files Affected (Phase 1)

- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — `SortControl` icon rotation
- No icon files change (FilterList reused); no VM/model change.

## Out of scope

- The menu drawer's Link/LinkOff reposition (separate, already implemented and uncommitted).
- Changing sort semantics, fields, or the sort popover layout.
- Phase 2 fold design decisions (to be confirmed before that phase is implemented).
