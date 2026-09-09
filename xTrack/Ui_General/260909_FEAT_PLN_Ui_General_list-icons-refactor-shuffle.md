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
- Apply a 180° rotation **on the Icon composable only** when the sort is descending:
  `modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(sortAlpha).rotate(if (state.descending) 180f else 0f)`
- Import `androidx.compose.ui.draw.rotate`.
- **Convention:** descending = flipped (rotated 180°); ascending = upright (0°).
- Everything else stays identical: tap target, popover, checkmark-on-selected pattern, tapping the
  selected field toggles direction, active/inactive alpha, behavior.
- Alternative accepted by the user: a true vertical mirror `Modifier.scale(1f, -1f)` instead of rotate —
  confirm which before coding (default: rotate 180f).

### Phase 1 verification
- Build via `apk-build.bat`.
- Manual: in both list headers, choosing a sort order that is descending shows the flipped icon;
  ascending shows it upright; filter/sort/reset behavior unchanged.

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

## Files Affected (Phase 1)

- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — `SortControl` icon rotation
- No icon files change (FilterList reused); no VM/model change.

## Out of scope

- The menu drawer's Link/LinkOff reposition (separate, already implemented and uncommitted).
- Changing sort semantics, fields, or the sort popover layout.
- Phase 2 fold design decisions (to be confirmed before that phase is implemented).
