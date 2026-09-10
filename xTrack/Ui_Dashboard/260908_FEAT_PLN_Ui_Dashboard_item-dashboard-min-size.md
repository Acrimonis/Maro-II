---
feature: Ui_Dashboard
topic: Item dashboard min-size invariant (marker/track selection) — portrait floor + landscape full-coverage
created: 2026-09-08 16:00 UTC
modified: 2026-09-08 17:10 UTC
status: approved-plan
decision: Option B — DrawerScaffold wrap-mode min-height param + orientation-gated wrapContent; marker Viewing portrait floor + landscape full coverage
---

# Item Dashboard Min-Size Invariant — Marker/Track Selection

## Context

When a marker or a track is selected, an "item dashboard" (detail drawer) slides in to replace
the original nav dashboard slot:

- **Portrait** → bottom part of the screen (same region as the portrait dashboard).
- **Landscape ("paysage")** → left part of the screen (same region as the landscape dashboard).

User reports:
1. Rendering and auto-scaling are fine.
2. The invariant **"the item dashboard can never be smaller than the original dashboard"** is not
   respected in **portrait** (bottom of screen).
3. In **landscape**, the item dashboard must **always occupy the whole original dashboard's place**
   (the full left column) — not a partial/shrunken panel.

## Canonical Rule (source of truth)

`docs/ui-drawer-guidelines.md` §3, "Portrait Drawer Height Floor":

> 🔴 A bottom-anchored drawer is never smaller than the original dashboard. Its height is
> `maxOf(portraitDashboardHeight, <content height>)` — the dashboard height is a floor, so the
> drawer either matches the dashboard or grows taller to fit its content.

Reference file: [`docs/ui-drawer-guidelines.md:104`](../../docs/ui-drawer-guidelines.md)

## Current Behaviour (verified in code)

### Layout constants — [`MapScreen.kt:1809`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)
- `portraitDashboardHeight = maxWidth * 3 / 5`
- `landscapeDashboardWidth = maxHeight * 100 / 100` (the full left column)

`DashboardPanel` is **always rendered** (Layer 0) and never gated by drawer state — see
[`MapScreen.kt:2071`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) and the
Layer 0 / Layer 1 architecture in `docs/ui-drawer-guidelines.md` §1. So if an item drawer is
smaller than the dashboard, the dashboard remains visible around/behind it — the exact defect.

### The offending surface: MarkerDrawer `Viewing` (both orientations)

Marker `Viewing` renders through [`ViewingContent`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:120),
which calls `DrawerScaffold` with **`wrapContent = true` hardcoded — NOT gated on `isLandscape`**
([`MarkerDrawer.kt:169`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt)):

1. **Portrait regression:** wrap mode collapses the panel to content's natural height
   ([`DrawerScaffold.kt:179`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt))
   with **no floor**. A short marker → panel smaller than `portraitDashboardHeight` → original
   dashboard still visible behind/above.
2. **Landscape regression:** the landscape slot is full-height
   ([`OverlayLayer.kt:361`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)
   `align(CenterStart).width(landscapeDashboardWidth).fillMaxHeight()`), but the hardcoded
   wrap-content mode collapses the visible panel to content height and bottom-aligns it inside
   that full-height invisible Box — so the **top of the left column does not get covered** by the
   drawer background. Marker `Viewing` landscape therefore violates "occupies the whole original
   dashboard's place". (This contradicts the 2026-09-06 wrap-content plan, which stated landscape
   marker/track slots must stay full-height fixed — the implementation failed to branch on
   `isLandscape`.)

### Surfaces that replace the dashboard slot (`OverlayLayer.kt`) — comparison
| Surface | Orientation | Size | Covers whole dashboard? |
|---|---|---|---|
| MarkerDrawer `Viewing` | portrait | wrap-content, no floor ([`OverlayLayer.kt:381`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)) | ❌ too small when short |
| MarkerDrawer `Viewing` | landscape | wrap-content inside full-height slot ([`MarkerDrawer.kt:169`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt)) | ❌ top gap when short |
| MarkerDrawer `MatchResult` (Where-Am-I) | portrait | `.height(portraitDashboardHeight)` ([`OverlayLayer.kt:404`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)) | ✅ fixed |
| MarkerDrawer `MatchResult` | landscape | full-height non-wrap (shared landscape slot) | ✅ |
| TrackInfoDrawer | portrait | `maxOf(portraitDashboardHeight, 48.dp + card + footer)` ([`OverlayLayer.kt:535`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)) | ✅ floor |
| TrackInfoDrawer | landscape | full-height non-wrap ([`OverlayLayer.kt:428`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)) | ✅ |
| WizardDrawer | portrait / landscape | fixed `portraitDashboardHeight` / full-height | ✅ |

Only **Marker `Viewing`** is the outlier, in **both** orientations, caused by the single hardcoded
`wrapContent = true` at [`MarkerDrawer.kt:169`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt).

## Fix Design — Option B (decided)

### B1. Add a floor parameter to `DrawerScaffold` wrap mode
In [`DrawerScaffold.kt`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:179)
add `wrapContentMinHeight: Dp = 0.dp` (default 0 = unchanged). In the wrap branch, make the visible
wrap `Column` at least this tall so its background spans the floor:

- Wrap `Column` gets `.heightIn(min = wrapContentMinHeight)` so the panel (and its background)
  is `max(contentNaturalHeight, wrapContentMinHeight)` tall.
- When content is taller, behaviour is unchanged (wrap to content; body scrolls only past screen
  height via the existing `heightIn(max = availableBodyHeight)` + `verticalScroll`).
- Default `0.dp` → all current non-marker wrap consumers (none today besides `Viewing`) and future
  callers are unaffected. No regression to non-wrap consumers.

### B2. Gate `wrapContent` on orientation + pass the floor (MarkerDrawer)
Change [`MarkerDrawer.kt:169`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt)
so the marker `Viewing` drawer uses wrap-content **only in portrait** and enforces the portrait
floor there; in landscape it must be **full-height non-wrap** (fill the whole left column, exactly
like TrackInfoDrawer landscape):

- `ViewingContent` (and `MarkerDrawer`) receives a `minPanelHeight: Dp = 0.dp` param.
- `wrapContent = !isLandscape`
  - portrait (`isLandscape=false`): `wrapContent = true`, `wrapContentMinHeight = minPanelHeight`
    (OverlayLayer passes `portraitDashboardHeight`).
  - landscape (`isLandscape=true`): `wrapContent = false` → `DrawerScaffold` non-wrap mode fills
    the full-height slot top-to-bottom with its background (root `Box` keeps `fillMaxSize` +
    background), covering the whole dashboard column.

### B3. Wire the floor from OverlayLayer
[`OverlayLayer.kt`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) portrait
`Viewing` slot call passes `minPanelHeight = portraitDashboardHeight` into `MarkerDrawer`. The
landscape call passes no floor (ignored in non-wrap mode).

### B4. Keep MatchResult / Track / Wizard untouched
They already satisfy the invariant in both orientations (see table). Scope lock: only the
`Viewing` path changes.

### Visual behaviour notes
- Portrait, short marker: panel height = `portraitDashboardHeight` (the floor). Content stacks from
  the top of the panel; the extra space under the footer is the panel's own background — no
  dashboard behind. Optionally bottom-anchor content later; default = top-stacked Column (current
  wrap layout) which already yields a clean banded panel.
- Portrait, tall marker / many prev-next: panel wraps taller than the floor (no forced scroll when
  it fits) — preserves the 2026-09-06 wrap-content goal.
- Landscape: panel always fills the full left column → identical footprint to `DashboardPanel`
  landscape and to TrackInfoDrawer landscape.

## Files Affected
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — add
  `wrapContentMinHeight: Dp = 0.dp`; apply `heightIn(min = …)` to the wrap `Column`.
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — `MarkerDrawer` + `ViewingContent`:
  add `minPanelHeight` param; `wrapContent = !isLandscape`; forward `wrapContentMinHeight`.
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — portrait `Viewing` `MarkerDrawer`
  call: pass `minPanelHeight = portraitDashboardHeight`.
- `docs/ui-drawer-guidelines.md` §3 — update the portrait-height-floor note so the example covers
  the marker `Viewing` portrait drawer, and add a landscape rule stating left-anchored item drawers
  must fill the full dashboard column (wrap-content is portrait-only).

## No-Regression Checklist
- [ ] `DrawerScaffold` default `wrapContentMinHeight = 0.dp` → all existing non-wrap consumers
      (Menu, Settings, MatchResult, Track, Wizard, TrackHistory, MarkerManagement) render identically.
- [ ] Marker `Viewing` portrait: minimal marker → panel ≥ `portraitDashboardHeight`, no dashboard
      behind/above; long marker / many entries → grows taller, content still fits without unwanted
      scroll.
- [ ] Marker `Viewing` landscape: panel covers the **entire** left column top-to-bottom (no gap at
      the top), identical footprint to TrackInfoDrawer landscape and `DashboardPanel`.
- [ ] Marker `MatchResult` portrait & landscape unchanged.
- [ ] TrackInfoDrawer portrait & landscape unchanged.
- [ ] Wizard portrait & landscape unchanged.
- [ ] FROM_BOTTOM slide and TOP edge shadow still align with the (possibly floor-sized) portrait
      panel; FROM_LEFT slide / RIGHT shadow intact in landscape.
- [ ] No `MeasureHeight` probe reintroduced; no new fixed-height formulas.
- [ ] Build via `apk-build.bat` → assembleDebug green.

## Verification
- Portrait: select minimal marker (short name, no boat distance, single marker) → drawer is at
  least `portraitDashboardHeight` tall; `DashboardPanel` not visible behind/around it.
- Portrait: long description / prev-next navigation → drawer grows beyond floor, no unwanted scroll.
- Landscape: select a marker → drawer background spans the whole left column edge-to-edge
  (top reaches the status bar area), matching the track detail drawer.
- Landscape: select a track (regression) → unchanged full-column drawer.

## Branch Note
Branch `feature/ya-ui-twk` created from `origin/develop` (tracking origin/develop). Implement this
plan under it. This file is untracked on that branch.

## Implemented
Option B implemented on `feature/ya-ui-twk` via `#implement` pipeline (portrait floor verified
on-device + fixed; see debug note below):
- [`DrawerScaffold.kt`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt)
  — new `wrapContentMinHeight: Dp = 0.dp` param (default preserves behaviour). The wrap Column
  uses `heightIn(min = wrapContentMinHeight)` **only** (no `wrapContentHeight()`) → panel height is
  `max(content, floor)`.
  **Debug fix (2026-09-08):** an earlier `heightIn(min)` + `wrapContentHeight()` combination let
  content height win over the floor (device logcat showed measured 224.38dp < floor 246.86dp).
  `wrapContentHeight()` was removed so the plain Column honors the `heightIn(min)` floor.
- [`MarkerDrawer.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) —
  `MarkerDrawer`/`ViewingContent` gain `minPanelHeight: Dp`; `wrapContent = !isLandscape`:
  portrait wrap-content floors at `wrapContentMinHeight = minPanelHeight`; landscape uses the
  non-wrap full-height branch so the drawer covers the whole left dashboard column top-to-bottom.
- [`OverlayLayer.kt`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) — portrait
  marker `Viewing` `MarkerDrawer` call passes `minPanelHeight = portraitDashboardHeight`.
  MatchResult portrait (fixed height), landscape Viewing call (non-wrap), Track and Wizard untouched.
- [`docs/ui-drawer-guidelines.md`](../../docs/ui-drawer-guidelines.md) §3 — portrait-height-floor
  note now covers the marker detail drawer; added Landscape Full-Column Coverage rule
  (wrap-content is portrait-only).
- Build: `assembleDebug` SUCCESS. Ask review PASS.
