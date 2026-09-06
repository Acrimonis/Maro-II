<!-- scope: feature -->

# Settings Tab Navigation — Disable Swipe + Fix Slide Spacing

> Reviewed by Ask mode (2026-09-05). Approved with amendments — see "Implementation (refined)".

## Context

`SettingsOverlay` ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3270)) renders 4 tabs
(Layers / Navigation / Position / System) via a `HorizontalPager` + a manual `Row` tab bar with a `drawBehind`
indicator. Tab selection is synced bidirectionally with the pager.

Two reported issues:

1. **Swipe navigation unwanted** — the `HorizontalPager` inherently accepts horizontal drags, so users can swipe
   between tabs. Requirement: navigate only by clicking the tab labels.
2. **Slide animation looks wrong** — during a pager slide, the outgoing and incoming pages sit flush side-by-side
   with **no gap** between their controls, whereas the settled single-page view has comfortable padding.

## Root Cause

The 24dp horizontal padding lives on the **outer `Column`** ([`MapScreen.kt:3310`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3310)),
which wraps the header, tab bar, pager, and footer. The pager's 4 page composables (`LayersSettings`,
`NavigationSettings`, `PositionSettings`, `SystemSettings`) have **no horizontal padding of their own** and are laid
out flush against each other inside the pager. Mid-slide, page A's rightmost control and page B's leftmost control
therefore touch at the pager center. In the settled single-page view the outer 24dp inset hides this, so the defect
only appears during the transition.

## Decision (Option C — keep pager, block swipe, fix spacing)

Keep the `HorizontalPager` and its slide animation. Two changes:

### 1. Disable swipe
Add `userScrollEnabled = false` to the `HorizontalPager`
([`MapScreen.kt:3387`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3387)). This blocks finger drags while
leaving programmatic `animateScrollToPage` intact, so tab clicks still animate the slide.

### 2. Give adjacent pages breathing room during the slide
Relocate the horizontal padding from the outer `Column` into each page so pages gap during the slide while the
settled view stays pixel-identical.

**Net effect:** settled view inset stays 24dp (unchanged); during a slide the two pages are each inset 24dp from
their own edge, producing a visible gap between adjacent controls instead of glued-together content.

## Implementation (refined — per Ask review)

To minimise edit surface and risk, do **not** edit all 4 page composables. Instead:

1. **Disable swipe** — add `userScrollEnabled = false` to the `HorizontalPager`
   ([`MapScreen.kt:3387`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3387)). Programmatic
   `animateScrollToPage` (tab clicks) still works.
2. **Outer `Column`** ([`MapScreen.kt:3306`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3306)): change
   `padding(horizontal = 24.dp, vertical = 3.dp)` → `padding(vertical = 3.dp)` only (drop the horizontal inset so
   the pager spans full width).
3. **Header `Row`** ([`:3313`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3313)), **tab-bar `Row`**
   ([`:3350`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3350)), and **footer `Text`**
   ([`:3402`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3402)): add `padding(horizontal = 24.dp)` so
   they stay aligned with page content.
   - **Tab-bar pitfall:** the 24dp padding modifier MUST precede the `drawBehind` modifier in the chain so
     `size.width` reflects the reduced content width and the indicator stays aligned under the 4 equal `weight(1f)`
     tabs. Padding after `drawBehind` would misalign the indicator.
   - Footer is a short centered `Text`; symmetric padding keeps it centered (low risk).
4. **Pager content** — wrap the `when(page)` block inside the pager lambda
   ([`:3393`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3393)) in a single padded container:
   `Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) { when (page) { ... } }`. This supplies the per-page
   24dp inset and the slide gap with one edit point; the inner per-page `verticalScroll` Columns are unaffected.

**Manual check:** confirm vertical scrollbar position (scroll container now spans full width; content padded) is
acceptable — if settings use a visible scrollbar it shifts ~24dp toward the screen edge.

## Why not the alternatives

- **Drop the pager entirely / direct `when(selectedTab)`** — cleanest for swipe removal, but loses the slide
  animation the user wants to keep.
- **Add per-page padding on top of the existing 24dp** — simpler but widens the settled inset (24dp + extra),
  changing the "normal rendering" the user wants preserved.
- **`scrollToPage` (instant, no slide)** — removes the artifact but also removes the animation; keeps pager
  machinery for no benefit.

## Files Affected

- [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) —
  `SettingsOverlay` only: outer `Column` padding, header/tab-bar/footer padding, `HorizontalPager`
  (`userScrollEnabled = false`), and a single padded `Box` wrapper around the pager `when(page)` content.
  The 4 page composables are untouched.

## Verification

- Build via `apk-build.bat`.
- Manual: open Settings → confirm swiping left/right does NOT change tabs; clicking each tab slides with a visible
  gap between the outgoing/incoming controls; settled layout inset unchanged.
