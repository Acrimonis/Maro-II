# Ui_Settings — the fourth tab's tap is lost

**Date:** 2026-09-19 · **Branch:** `feature/setting-tabs` · **Base:** `origin/develop` (`7918c0f`)

## 1. Request

The Settings overlay's fourth tab (System) does not take the tap: both the strip and the page content stay on the third tab (Position), and the end state is stable rather than a flicker. The other three tabs land normally.

## 2. What the code carries today

Read from the working tree, not taken from the report:

- `MapScreenSettingsOverlay.kt:97` — `rememberPagerState(pageCount = { 4 })`, the tab count as a literal.
- `MapScreenSettingsOverlay.kt:99–112` — a bidirectional sync: `LaunchedEffect(selectedTab)` animates the pager and then raises `pagerSyncSettled`, and `LaunchedEffect(pagerState.currentPage)` writes `currentPage` back into `selectedTab` once that flag is set.
- `MapScreenSettingsOverlay.kt:146` — the cells' `onClick` is the only other writer of `selectedTab`.
- `MapScreen.kt:730` — `var selectedTab by rememberSaveable { mutableIntStateOf(0) }`; `MapScreen.kt:2516` feeds it as `onTabChange = { selectedTab = it }`.
- `MapScreen.kt:3513` — `internal val settingsTabLabels` holds the four `@StringRes` labels; the strip already iterates it, while the pager counts to its own literal.

## 3. The mechanism — reasoned, never proven

The tap sets `selectedTab = 3` and starts one animation across two intermediate pages. The second effect is keyed on `currentPage`, which during an animated scroll is the page the pager is **passing through** rather than the page it has settled on, so it writes a lower index back; that write re-keys the first effect and retargets the animation one page short of the tap.

Compose Foundation 1.11.1 ships no sources artifact in the local Gradle cache, so the `currentPage` versus `settledPage` semantics during `animateScrollToPage` cannot be read there and the paragraph above is not established. The fix is chosen so its outcome does not depend on those semantics: it removes the only writer that can disagree with the tap.

## 4. The change

1. `MapScreenSettingsOverlay.kt` — the page count derives from `settingsTabLabels.size` in place of the literal `4`, so the strip, the pager and the `when (page)` cannot disagree about how many tabs exist.
2. `MapScreenSettingsOverlay.kt` — the pager-to-tab write-back, the `pagerSyncSettled` flag and the bidirectional comment are deleted, leaving `LaunchedEffect(selectedTab) { pagerState.animateScrollToPage(selectedTab) }` as the one effect, with a short comment naming why the direction is absent and pointing at `docs/ui-component-guidelines.md` §2.11.
3. `docs/ui-component-guidelines.md` §2.11 — one table row states the invariant.

## 5. Out of scope

No `else` branch on the `when (page)`; the four hoisted `ScrollState` parameters are neither bundled nor reordered; the strip's cells, `edgePadding` and indicator are untouched; no dependency is added; no Compose UI test is added (`app/src` carries `main/` and `test/` only, with no Compose harness); no git write is made.

## 6. Verification

- `apk-build.bat` → BUILD SUCCESSFUL, with no warning naming either touched file.
- The sync region read back → exactly one effect remains, and no `pagerSyncSettled` reference survives.
- Device pass, owed to the user: tap each of the four tabs and confirm the strip and the page both land on the tapped tab, then reopen the overlay on a tab other than the first to confirm the persisted tab still lands and the pager still holds four pages. The pass shows the after state alone — no baseline exists for a before-and-after comparison.

## 7. Outcome

Shipped on `feature/setting-tabs`. The page count derives from `settingsTabLabels.size`; the pager-to-tab write-back, its `pagerSyncSettled` flag and the bidirectional comment are deleted, leaving one `LaunchedEffect(selectedTab)` effect that animates to the tab; and `docs/ui-component-guidelines.md` §2.11 carries the invariant as a table row. `apk-build.bat` → **BUILD SUCCESSFUL** in 19s, `compileDebugKotlin` executing with no warning naming either touched file, and the sync region read back shows exactly one effect with zero surviving references to the flag or to the literal page count. The device pass stays owed to the user, and the mechanism in §3 remains reasoned, never proven.
