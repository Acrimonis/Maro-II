# Landscape menu drawer — content overflow

**Feature:** Ui_General
**Status:** discussion
**Created:** 2026-08-16 07:50 UTC
**Branch:** feature/GPS-ui

## Problem

In landscape, the right-side menu drawer (hamburger panel) is not fully visible or
interactable: the bottom sections are cut off and unreachable.

## Root cause

The menu drawer is rendered as a full-height right-edge panel with a **non-scrollable**
body:

- Slot sizing in [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:256):
  `align(TopEnd).fillMaxWidth(0.75f).fillMaxHeight()` — no orientation branch, unlike
  Wizard / MarkerDrawer which branch on `isLandscape`.
- Content in [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:100)
  uses `DrawerScaffold(scrollable = false)`.

In landscape the viewport height is small (≈ 360–430 dp), but the body is tall:

- Idle: ≈ 470 dp (spacers + POSITION SOURCE card + TRACKS card + MARKERS card).
- Recording: ≈ 610 dp — the live-stats block
  ([`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:282))
  adds 7 stat rows.

With `scrollable = false` the body is a plain `Column` inside a fixed-height host
([`DrawerScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:168)),
so the overflow is clipped and the bottom rows cannot be reached.

Other right-side drawers (TrackHistory, MarkerManagement, Settings) are unaffected —
they scroll via `ListOverlayScaffold`/`LazyColumn`.

## Options

### A′ — Scroll only when it doesn't fit (`scrollable = true`, always) — recommended

Set `DrawerScaffold(scrollable = true)` in `MenuDrawerOverlay` — no orientation flag.

Compose's `verticalScroll` yields a non-zero scroll range only when the content is
taller than the viewport, so this is the requested "scroll only if necessary" behavior
for free:

- Portrait (content fits today): scroll range = 0 → nothing scrolls, no scrollbar,
  rendering identical to current.
- Landscape (content overflows): body scrolls, fixed header stays pinned.
- Future menu entries: scrolling appears automatically in either orientation the
  moment content no longer fits.

Safety check: the body must not use vertical `weight`/`fillMaxHeight`. The menu body
uses only horizontal `weight(1f)` in section-header rows — safe.

Nuance: with a zero scroll range, dragging the panel can still show Android's default
overscroll glow/stretch. Standard and subtle; suppressible if undesired.

### B — Landscape two-column layout

In landscape, lay the sections out side-by-side (e.g. POSITION SOURCE + TRACKS in one
column, MARKERS in the other) to reduce total height.

- Reduces height, but more invasive: new conditional layout branch, duplicated or
  extracted section composables.
- Live stats (7 rows) still tall — a single column may still overflow, so it would
  likely need to be paired with scroll anyway.
- Risk to visual quality on a ~360 dp-tall canvas.

### C — Hybrid: scroll + landscape compacting

Combine A′ with landscape-only tightening (smaller spacers, 2-column stat grid).

- Most work and most token divergence; only worth it if A′ alone feels suboptimal.

## Recommendation

**Option A′** — a single-line change (`scrollable = false` → `true` in
`MenuDrawerOverlay`). Portrait is unchanged because a zero scroll range has no visual
or interaction effect; landscape and any future menu growth are covered automatically.
B/C remain follow-ups only.

## Portrait constraint

Portrait content already fits → zero scroll range → identical layout and interaction.
No orientation branch required.

## Decision

**A′ + suppress overscroll when content fits** (chosen 2026-08-16).

## Implementation

1. [`DrawerScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:132)
   — add `suppressOverscrollWhenFits: Boolean = false` parameter (opt-in; default keeps
   `MarkerDrawer` / `TrackInfoDrawer` behavior unchanged). In the `scrollable` branch,
   hoist `rememberScrollState()`, track `canScroll = derivedStateOf { maxValue > 0 }`,
   and pass `overscrollEffect = null` while `suppressOverscrollWhenFits && !canScroll`.
2. [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:104)
   — set `scrollable = true` and `suppressOverscrollWhenFits = true`.
3. Build (`apk-build.bat`) to verify.
