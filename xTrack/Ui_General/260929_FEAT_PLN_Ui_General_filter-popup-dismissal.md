# Plan — the filter popup's dismissal

**Feature:** Ui_General · **Date:** 2026-09-29 · **Status:** in design — the discussion is captured here and
nothing has been written to code.

## 1. The requirement

> "the list filters popup … right now, they close on selection of an item. Keep it opened unless there is only
> one group, otherwise validate it on scrim click or back."

Read as, then **re-pinned by the user (2026-09-29)**: the popup **never closes on a row tap** — the generic
behaviour, with no group count anywhere in it — and is dismissed by an outside tap or by back alone, the filter
still applying live on each tap.

## 2. What the code does today

- [`FilterControl()`](../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:241) is the
  popup: a `Popup(alignment = TopEnd)`, height-bounded and scrollable, whose rows **write through**
  `onFilterChange` and then set `expanded = false`
  ([`:296`](../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:296)) — that last line
  is the whole of the complaint.
- **Half the request is already true.** `onDismissRequest = { expanded = false }`
  ([`:266`](../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:266)) is what an
  outside tap *and* the back press go through, so "dismiss on scrim click or back" needs nothing added — the
  popup's own dismissal already is that.
- A group is an **axis** ([`FilterAxisSpec`](../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:137)),
  drawn as one `PopupSectionTitle` and one `PopupSectionCard`; a gated axis (`dependsOn`) is greyed rather than
  removed, and the filter **applies on each tap** — the control holds no draft state.
- Call sites: `TrackHistoryOverlay` and the menu drawer's track list pass
  [`trackFilterAxes()`](../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:149) — **four** axes —
  and `MarkerManagementOverlay` and the drawer's marker list pass
  [`markerFilterAxes()`](../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:193) — **five**.

## 3. The change

1. **One deletion.** The `expanded = false` after `onFilterChange` goes: a row tap writes the filter and
   nothing else. No group count enters the rule — the user's own correction of 2026-09-29 — so the popup
   behaves the same whatever a caller passes it.
2. **Nothing else moves.** The dismissal stays `onDismissRequest`'s (outside tap and back), the rows keep
   writing through `onFilterChange`, and the icon's active alpha, the width, the height bound and the inner
   scroll are untouched.
3. **One rule to write.** "A row tap writes the choice and leaves the popup open; the popup itself is dismissed
   by an outside tap or by back" — into the filter part of `docs/ui-lists-guidelines.md`, where the popup's own
   rules already live; §2.10 stays as it is.

## 4. Settled by the user, 2026-09-29

- **There is no group count in the rule.** "Forget the one group. Keep the behavior generic." — the row tap
  never closes the popup, whatever a caller passes, so the axis count leaves the rule entirely and the
  four-and-five-axis call sites need no special case.
- **"Validate" is the live update.** The filter keeps applying on each tap and the list behind answers at once,
  which is what the open popup shows; there is no draft state and none is to be added. The popup itself is
  dismissed by the outside tap or by back, as it already is.

## 5. Out of scope

- The **sort** popup (`SortControl`) keeps its close-on-choice — one choice, one job.
- The filter popup's geometry, height bound and inner scroll (the 2026-09-22 pass).
- The scrim matrix: the popup adds no scrim of its own, and the lists' 0.50 scrim stands.
- `ListFilter`, the axes and their predicates.

## 6. Tests, and the honest gap

- The rule is one deletion and wants no predicate: with no group count and no draft there is nothing left to
  pin beyond the popup's own dismissal, which is Compose's `Popup` behaviour rather than ours — the earlier
  `filterClosesOnChoice(axisCount)` idea died with the group count.
- The behaviour itself — staying open across taps, closing on the outside tap and on back — is a device look,
  as every popup's dismissal is.
