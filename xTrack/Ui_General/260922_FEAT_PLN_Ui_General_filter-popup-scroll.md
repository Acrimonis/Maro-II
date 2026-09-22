<!-- scope: feature -->
# Filter popup — the track filter overflows the screen in landscape

**Date:** 2026-09-22 · **Branch:** `feature/filter-scroll`, cut from `origin/develop` (`4a7fefc`) · **Status:** shipped 2026-09-22 — see `## Outcome` · **Feature:** Ui_General owns `ListOverlayScaffold` and the list filter system; UI_Map owns the map side that mirrors the same filter state

## 1. Report

The popup opened by the list header's funnel button does not fit in landscape on the Tracks list. It is a fixed-width `Column` with no height bound and no scroll, so the rows past the screen edge are clipped and unreachable — the bottom axis is not merely hard to read but impossible to select.

The ask: the popup's content scrolls when its inner size exceeds its outer size. Nothing in the app is edited by this plan.

## 2. What the code carries today

| Anchor | What it holds |
|--------|---------------|
| `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:247-328` | `FilterControl` — the funnel button and its `Popup(alignment = Alignment.TopEnd, properties = PopupProperties(focusable = true))` |
| `…ListOverlayScaffold.kt:275-280` | the popup's `Surface`: `Modifier.width(240.dp)`, no height constraint of any kind |
| `…ListOverlayScaffold.kt:281` | the body — a plain `Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp))`, no `verticalScroll` |
| `…ListOverlayScaffold.kt:282-322` | one section title + one card per axis, each card a `Column` of option rows |
| `…ListOverlayScaffold.kt:149-161` | the sort popup — the same shape, the same 240dp `Surface`, the same unbound `Column` |
| `…ListOverlayScaffold.kt:800` | the multi-action `DropdownMenu` — the third popup in the file |
| `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:132-164` | `trackFilterAxes()` — `dateRange` 7 options, `pinned` 3, `position` 3 |
| `…ListFilter.kt:167-193` | `markerFilterAxes()` — `icon`, `pinned`, `origin`, 3 each |
| `docs/ui-component-guidelines.md:294-323` | §2.10, the canonical popup spec — tokens and hierarchy only, nothing on height or overflow |

Both list drawers render the same `FilterControl`, so the defect reaches markers too; only the track axis set is long enough to expose it on a phone.

## 3. Why the track one clips

From the spec's own constants, not from a device. The track popup stacks **13 option rows** across three axes: each row is a 15sp text line plus 2dp of vertical padding on each side, so roughly 24dp; three cards add 8dp top and bottom each, three section titles about 29dp, the body's 12dp padding twice, and the 4dp gaps. That is roughly **490dp of content**.

A landscape phone offers about 390dp of screen height, and the popup hangs from a header row already inset from the top, so nearer 320dp is free. About six rows — the whole `position` axis — fall past the edge, and with neither a bound nor a scroll they are simply gone.

The markers filter carries 9 rows and the sort popup fewer, which is why the report names the track one alone.

## 4. The options

- **A — bound and scroll (the ask).** `verticalScroll(rememberScrollState())` plus a height cap on the popup body, so the scroll engages only when the content exceeds the cap and a short popup still wraps its own height. Smallest change, keeps the popup's present shape in both orientations, and lands once in the shared component.
- **B — fit instead of scroll.** In landscape, lay the axes in two columns or collapse each axis to its selected value. Fits ~490dp with no scroll and no hidden option, but diverges from the portrait shape, needs a breakpoint and a second rendering path, and rewrites the spec in §2.10.
- **C — take M3's `DropdownMenu`.** It replaces the hand-built popup with the platform's own container, whose measuring, insets and width floor are M3's rather than §2.10's card hierarchy, and it is what the multi-action menu in the same file already uses.

## 5. What is settled, and what is not

Settled by the request: the content scrolls rather than clips — option A over B and C. Still open, for the discussion:

- **Q1 — where the cap comes from.** A fixed dp is fragile across densities and orientations; deriving it from the window height is robust but needs the anchor's own offset to be honest about how much space is left below the header. Whether the cap is a fraction of the window or a new `maro.properties` key is part of this.
- **Q2 — how far the scope reaches.** The defect lives in `FilterControl` alone, but the sort popup at `:149` and the multi-action menu at `:800` sit in the same file with the same shape; a bound on the shared popup body would cover all three, and the sort popup's custom-field card can grow too.
- **Q3 — the scroll affordance.** A dropdown that scrolls silently hides options behind a second gesture with no cue. Whether that needs a visible hint, and whether the popup should instead re-anchor when it runs low on space, is not decided.

## 6. Where the rule would live

§2.10 in `docs/ui-component-guidelines.md` is already the canonical popup spec and the one home for its tokens, and `docs/ui-lists-guidelines.md:150` points at it rather than restating it. A rule of the shape "a popup wraps its own height and scrolls once that exceeds the space it can occupy — it never clips; the bound is the space available, not a fixed dp" belongs there, mirroring §2.11's existing "fails soft by scrolling rather than clipping" for the settings tab strip (`docs/ui-component-guidelines.md:327-329`) and the settings panel's own wrap-and-scroll behaviour (`:535-537`).

Drift found while reading it: `docs/ui-lists-guidelines.md:106-123` still lists a `THIS_YEAR` option in the track `dateRange`, a `geometry` axis under the markers, and a `label: String` field — all three contradict `ListFilter.kt`, where `THIS_YEAR` and the geometry axis were removed and the field is `labelResId: Int`. The document is a sibling of the one this plan would edit, so the tables need the correction whenever this lands.

## Outcome

Shipped 2026-09-22 on `feature/filter-scroll`. Both list popups in `ListOverlayScaffold.kt` — `SortControl` and `FilterControl` — now bound their body with `Modifier.heightIn(max = …)` and scroll it with `verticalScroll(rememberScrollState())`, so a popup wraps its own height while it fits and scrolls once it does not. The bound is one pure helper, `popupMaxHeightDp(screenHeightDp)`: the window's height less a 96dp reserve for the chrome the popup hangs below, floored at 120dp for a degenerate window, pinned by `ListPopupHeightTest`. The sort popup took the same bound because the guideline rule is general and the two popups share the file; the multi-action `DropdownMenu` was left alone, being M3's own container rather than one of these two popups.

Deviations: **Q1** took the window-height derivation over a new properties key; **Q2** was decided by the agent — both popups rather than the filter alone, the choice invisible except for a sort popup long enough to overflow; **Q3** was answered by omission, no scroll affordance added, the request being the scroll itself.

The rule landed as the **Overflow** paragraph of `docs/ui-component-guidelines.md` §2.10, and the drift §6 named was corrected in the same pass — `docs/ui-lists-guidelines.md` now carries the real track and marker axes, the removal of the `geometry` cascade, and `labelResId: Int` / `dependsOnValues` in the spec block.

`apk-build.bat` SUCCESS with no new warnings; `ListPopupHeightTest` 2/2 green. The device pass — the track filter and the sort popup in landscape, each scrolled to its last row — stays owed.
