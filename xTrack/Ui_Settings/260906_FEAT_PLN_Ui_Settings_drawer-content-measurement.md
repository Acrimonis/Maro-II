<!-- scope: feature -->

# Drawer Content Measurement — fix marker drawer fit (no scroll)

> **Update (2026-09-06):** the height-formula fix and the width-parity probe fix did not fully resolve the marker
> dashboard resize (still too short). The user wants a **render-then-size** approach: build the layout, render it,
> measure the REAL rendered content, then adjust the panel size — normalized for BOTH the marker and track drawers.
> See "Render-then-size approach" below.

## Context

The marker detail drawer (portrait) and track detail drawer (portrait) both grow to fit their content using a
`MeasureHeight` probe + a fixed height formula `maxOf(portraitDashboardHeight, 60dp + 6dp + contentHeight +
footerHeight + 4dp)` ([`OverlayLayer.kt:357`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:357) marker,
[`OverlayLayer.kt:489`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:489) track).

**Problem:** the marker detail drawer (portrait) scrolls when its content (boat-relative line + marker card +
belongs-to-track row) exceeds the `portraitDashboardHeight` floor.

## Root cause (Ask review)

- The `MeasureHeight` probe at [`MarkerDrawer.kt:235`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:235)
  **already measures the full content stack** (boat line + card + belongs-to-track) — there is NO missing
  measurement.
- The **real bug** is the fragile fixed-constant height formula `60dp + 6dp + content + footer + 4dp`: it reserves
  only ~4dp of slack for the scroll body, which is consumed by dp→px rounding and text reflow when content exceeds
  the `portraitDashboardHeight` floor. The boat line is the **trigger** (pushes content past the floor), not the
  cause.
- The track drawer stays under the floor, so it never hits the bug.
- A shared measurement control is **unnecessary** — both drawers already measure their own full content, and their
  content genuinely differs (marker: conditional boat line + card; track: card only).

## Requirement (user-confirmed)

Make the marker detail drawer (portrait) fit its content with **no scrolling**, resized correctly — matching the
track drawer.

## Fix (simpler than the original plan)

1. **Correct the height formula** so it reserves the exact content padding + trailing spacer (derive header/padding
   from the same tokens, or fold `contentPadding` into the probe) instead of hand-tuned `6dp`/`4dp` magic numbers.
2. **Make the marker drawer non-scrolling when content fits** — set `scrollable = false` when content fits, or use
   the existing `suppressOverscrollWhenFits` on the `DrawerScaffold` ([`MarkerDrawer.kt:222`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:222)).
3. **Track drawer**: no behavior change (it already works); optionally apply the same corrected formula for
   consistency.

## Option B — content sizes the panel, no scroll (user-confirmed 2026-09-06)

The separate `MeasureHeight` probe keeps under-measuring, and the user wants a **clean adjustment with no scroll**.
Option B: restructure so the drawer content **directly sizes the panel** to its natural height — no scroll host
forcing a fixed height, no separate probe.

**Design:**
1. For the marker and track detail drawers (portrait), render the content (position line + card for markers; card
   for tracks) at its **natural wrapped height** — not inside a `weight(1f)` scroll host.
2. The panel height = content natural height + header + footer, animated via `animateDpAsState`, respecting the
   `portraitDashboardHeight` floor.
3. **No scroll** — the panel fits its content exactly. (Trade-off: content taller than the screen would clip; the
   marker/track cards are bounded in practice.)

**Scope:** apply to the marker and track detail drawers. Keep the shared `DrawerScaffold` intact where possible;
if a drawer-specific non-scrolling variant is needed, add it without regressing the other drawers (menu, Where-Am-I).

**Normalization:** unify the marker and track height derivation (header token + content natural height + conditional
footer + unified buffer), removing the divergent magic constants.

## Additional fix (user-confirmed 2026-09-06)

In the marker card, the belongs-to-track row's track-name `Text` is rendered **blue** (`uiSettingsAccent`,
[`MarkerManagementOverlay.kt:471`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:471)). It should be
**white** (`uiSettingsTextPrimary`) to align with the rest of the card's text rendering. (The chevron stays muted.)
Note: `MarkerCardContent` is shared with the list overlay, so this applies everywhere the card renders (desired).

## Revised approach (user-confirmed 2026-09-06) — refined per Ask review

The height-formula fix alone did not fully resolve the marker dashboard resize. The user wants a **dedicated
content composable** as the single measurement source. Ask review refined the root cause and steps:

**Real root cause (Ask review):** the `MeasureHeight` probe ([`MeasureHeight.kt:28`](app/src/main/java/ykws/android/maro/ui/components/MeasureHeight.kt:28))
measures at the full drawer width (no width inset), while the display body is inset by `contentPadding` (12dp each
side). Text wraps differently at the two widths → the probe under-measures the real display height. The 6dp top
padding is already compensated in the formula ([`OverlayLayer.kt:363`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:363)),
so it is NOT the cause.

**Steps:**
1. **Extract `MarkerDetailContent`** (distance-to-boat line + `MarkerCardContent`) used in BOTH the `DrawerScaffold`
   body and the `MeasureHeight` probe — a clarity refactor (both already use the same `cardContent()` lambda today).
2. **Enforce width parity**: constrain the probe to the SAME inner width as the display body (apply the same
   horizontal inset / max width) so text wraps identically and the measured height matches the display. This is the
   actual fix for the residual resize failure.
3. **Reconcile padding accounting**: if the probe adopts `contentPadding` top 6dp, REMOVE the now-redundant
   `markerContentTopPadding` term from the formula ([`OverlayLayer.kt:363`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:363))
   to avoid double-counting (~6dp too tall).
4. **Keep header/footer/buffer in the formula** (header 60dp, footer, trailing buffer are structurally required;
   derive from tokens where possible).
5. **Fix the belongs-to-track color** to `uiSettingsTextPrimary` (white).
6. Verify no-scroll at rest (the 250ms `animateDpAsState` + `suppressOverscrollWhenFits`).

## Implementation steps (revised)
1. Extract `MarkerDetailContent(boatPosition, marker, ...)` composable (distance line + `MarkerCardContent`).
2. Render it in the `DrawerScaffold` body and the `MeasureHeight` probe with **matching width + padding**.
3. Reconcile the height formula (remove double-counted 6dp if the probe now includes it; keep header/footer/buffer).
4. Change the belongs-to-track track-name color to `uiSettingsTextPrimary`.
5. Verify the marker drawer (portrait) fits its content with no scroll.

## Recommended solution (Ask review, 2026-09-06) — normalize TRACK to the corrected MARKER pattern

The Ask review of Option B concluded that a literal "content sizes the panel, no probe" is NOT cleanly feasible
under the external-height architecture (the panel `.height()` is imposed on the `DrawerSlot`, and the body lives in a
`weight(1f)` scroll host that cannot report natural height). The faithful realization of Option B is to **keep the
probe but make it exact**, and to **normalize the track drawer to the already-corrected marker drawer pattern**.

**Current state audit (verified against code):**
- **Marker drawer — ALREADY CORRECT.** Probe [`MarkerDrawer.kt:193`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:193)
  applies `padding(start=12, top=6, end=12)` matching the display body `contentPadding`; formula
  [`OverlayLayer.kt:362`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:362) = `60dp header + cardHeight +
  footer + 8dp buffer` (no separate 6dp — already removed). Belongs-to-track color
  [`MarkerManagementOverlay.kt:471`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:471) is already
  `uiSettingsTextPrimary` (white).
- **Track drawer — INCONSISTENT (the residual bug).** Probe [`OverlayLayer.kt:565`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:565)
  applies only `padding(horizontal = 12.dp)` — **missing the top-6 inset** the display body has
  (`contentPadding = PaddingValues(start=12, top=6, end=12)`). Formula [`OverlayLayer.kt:499`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:499)
  = `60dp + 6dp + cardHeight + footer + 4dp` — adds a hard-coded `6.dp` top term. The probe under-measures (no top
  inset) while the formula over-reserves (adds 6dp) → the two cancel imperfectly and the drawer can still scroll.

**Steps (Code mode):**
1. **Track probe parity** — [`OverlayLayer.kt:567`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:567): change
   `Box(Modifier.padding(horizontal = 12.dp))` → `Box(Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp))` so the
   probe measures at the SAME inner width AND includes the top inset as the display body.
2. **Track formula normalization** — [`OverlayLayer.kt:499`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:499):
   remove the now-redundant hard-coded `6.dp` (the probe now includes the top inset), and align the trailing buffer to
   the marker's `8.dp` for consistency:
   `maxOf(portraitDashboardHeight, 60.dp + cardHeight + footerHeight + 8.dp)`.
3. **No behavior change to marker drawer** — it already follows the correct pattern.
4. **No wrap-content DrawerScaffold variant** — keep `scrollable = true` + `suppressOverscrollWhenFits` (P1/P2 of review).
5. Verify both drawers (portrait) fit their content with no scroll.

## Files Affected
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` (extract `MarkerDetailContent`, width-parity probe) — DONE
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` (track probe top-inset parity + track formula normalization)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt` (belongs-to-track color → white) — DONE

## Verification
- Build via `apk-build.bat`.
- Manual: marker detail drawer (portrait) fits its content (boat line + card + belongs-to-track) with no scroll;
  track detail drawer (portrait) fits its card with no scroll; belongs-to-track track name renders white.

## Wrap-content panel architecture (2026-09-06) — the robust fix, supersedes the probe approach

**User-confirmed symptom (2026-09-06):** the marker detail drawer (portrait) is STILL too short and the content
scrolls (swipeable up/down inside the panel). This persists after every probe/parity/formula fix.

**Root cause of the persistent failure:** the probe approach is fundamentally fragile. The panel height is derived
from a hidden `MeasureHeight` probe + a fixed formula `60dp + content + footer + 8dp`. The scroll-host body gets only
`content + 8dp` while the content (with its 6dp top inset) needs `content + 6dp` → **only ~2dp of slack**. Any
dp→px rounding, text reflow, or font-metric difference between the probe's unbounded-height measurement and the real
bounded display consumes that 2dp, so the content scrolls. The marker card's `IntrinsicSize.Min` + multi-line text
makes it especially sensitive. No amount of probe tuning can make a 2dp margin robust.

**The fix — eliminate the probe and the formula entirely.** Let the drawer panel genuinely WRAP its content at natural
height. The panel auto-sizes to header + content + footer with no hidden measurement and no magic constants.

**Design:**
1. **Add a wrap-content mode to `DrawerScaffold`** ([`DrawerScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt)):
   a new boolean param (e.g. `wrapContent: Boolean = false`). When true, the body is NOT inside a `weight(1f)` scroll
   host. Instead the whole `Column` wraps content at natural height, and the body content is only scrollable if it
   exceeds the available screen height (use `heightIn(max = ...)` + `verticalScroll` so very tall content still
   scrolls rather than clipping). Header + footer remain fixed.
2. **Marker detail drawer (portrait, Viewing state)** — [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt):
   use the wrap-content `DrawerScaffold`; DELETE the `MeasureHeight` probe and the `onCardHeightMeasured` plumbing.
3. **Track detail drawer (portrait)** — DEFERRED (already works, stays under the floor). Keep its current probe/formula
   as-is for now; revisit only if it shows the same symptom.
4. **OverlayLayer marker portrait slot — SPLIT BY STATE** (user ruling: no change to Where-Am-I):
   - `Viewing` state → wrap-content `DrawerSlot` (no fixed `.height`, no `animateDpAsState`, no probe). The slot sizes
     to the content's natural height; the FROM_BOTTOM slide (`slideInVertically { it }`) adapts automatically.
   - `MatchResult` (Where-Am-I) state → UNCHANGED full-height scroll slot (its own `DrawerSlot` with the existing
     fixed height / scroll host).
   - Landscape marker slot stays full-height fixed (`.width(landscapeDashboardWidth).fillMaxHeight()`, FROM_LEFT).
5. **Keep the `portraitDashboardHeight` floor** only where a minimum panel height is genuinely desired; for the marker
   Viewing drawer the content is the source of truth (no floor needed, or a small floor via `heightIn(min = ...)`).
6. **Scope:** marker detail (Viewing) portrait drawer ONLY. Do NOT change Where-Am-I, track, menu, wizard, settings,
   or track-history drawers.

**Trade-off:** content taller than the screen would scroll (handled by step 1's `heightIn(max)` + scroll) rather than
clip. The marker card is bounded in practice, so this is acceptable and matches the user's "no scroll when it fits"
requirement.

## Files Affected (wrap-content)
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — add `wrapContent` mode
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — use wrap-content; remove probe + height callback
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — split marker portrait slot by state (Viewing wraps, MatchResult unchanged); remove probe/formula/animated-height for the Viewing slot only
- `app/src/main/java/ykws/android/maro/ui/components/MeasureHeight.kt` — KEEP (track drawer still uses it; delete only when track is also converted)

## Verification (wrap-content)
- Build via `apk-build.bat`.
- Manual: marker detail drawer (portrait, Viewing) fits its content exactly with NO scroll; Where-Am-I (MatchResult)
  unchanged; track drawer unchanged; no regression to the other drawers.

## Ask review findings (2026-09-06) — two must-fix gaps + sizing contract

The wrap-content approach is **feasible and the right direction**. Review confirmed: removing the fixed `.height()`
from the slot is safe for the FROM_BOTTOM slide (`slideInVertically { it }` slides by content's own height) and the
TOP shadow (shadow Box fills the wrap-sized content bounds). Regression risk to other drawers is LOW because the new
mode is opt-in (`wrapContent: Boolean = false` default). Clean win: deletes `MeasureHeight.kt`, the
`onCardHeightMeasured` plumbing, `cardHeight`/`markerCardHeight` state, both `animateDpAsState` blocks, and the
magic-constant formulas.

**Gap 1 — Where-Am-I shares the marker portrait slot (RESOLVED by user 2026-09-06).** User ruling: "No change on
where I am. focus on display dash of marker." → **Split the marker portrait slot by state**: the `Viewing` (marker
detail) state becomes wrap-content (no scroll); `MatchResult` (Where-Am-I) keeps its existing full-height scroll
behavior UNCHANGED. Implement as two separate `DrawerSlot`s (only one state active at a time), each with its own
`visible` condition and modifier.

**Gap 2 — Sizing contract (MUST SPECIFY).** The current root is `Box(fillMaxSize)` → `Column(fillMaxSize)` with a
`weight(1f)` body. For wrap mode:
  - Keep the root `Box` as `fillMaxSize()` (it IS the screen / bounded parent).
  - Make the inner `Column` `wrapContentHeight().align(TopCenter)` (or BottomCenter for bottom-anchored).
  - Drop the `weight(1f)` body host entirely. Give the body `heightIn(max = availableHeight)` + `verticalScroll` so
    overflow still scrolls instead of clipping. Capture `availableHeight` via `BoxWithConstraints` at the top of the
    scaffold (maxHeight − header − footer), since a wrap Column cannot reference screen height directly.
  - `bottomAnchoredContent` becomes meaningless in wrap mode (no weight host) — ignore/remove it there.

**Must-keep:** landscape marker/track slots stay full-height fixed (`.width(landscapeDashboardWidth).fillMaxHeight()`,
FROM_LEFT) — only the PORTRAIT branches convert to wrap-content. The implementation must branch on `isLandscape`.

**Cleanup:** delete now-unused `MeasureHeight.kt` after both probes are removed.
