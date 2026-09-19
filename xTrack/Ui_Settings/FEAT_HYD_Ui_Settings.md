# Context Hydration — Ui_Settings — 2026-09-19

**Last Bake:** 2026-09-19 13:50 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, no work began without an explicit order, the device was never touched, and every claim about the code came from a file read. One gap is declared rather than filled: the pager's `currentPage` versus `settledPage` semantics during `animateScrollToPage` could not be read, Compose Foundation 1.11.1 shipping no sources artifact in the local Gradle cache, so the fourth tab's fault is carried as a reasoned mechanism and never as a proven one.

## State

Branch **`feature/setting-tabs`**, cut from `origin/develop` (`7918c0f`) in this session, carrying one change: the Settings overlay's fourth tab took no tap, both strip and page staying on the third. The pager-to-tab write-back, its `pagerSyncSettled` guard and the bidirectional comment are deleted, so `LaunchedEffect(selectedTab) { pagerState.animateScrollToPage(selectedTab) }` is the only effect and `selectedTab` is the single thing that moves the pager; the page count now derives from `settingsTabLabels` instead of the literal 4.

- Earlier the same day the extra-settings work — the strokes and shoreline colours, the arrow and line appearance, the px→dp pass and the dropped stale test expectations — reached `origin/develop` as the squash of PR #245, proven tree-identical to the local branch that carried it, so this branch starts with all four; their owed device pass is unchanged.
- Build green: `apk-build.bat` → BUILD SUCCESSFUL in 19s, `compileDebugKotlin` executing with no warning at all, so nothing names either touched file. The test suite was not run this session; the last full run stood at 478 tests, 0 failures, 9 skipped.
- The fault's mechanism stays reasoned, never proven, and the fix was chosen so its outcome does not rest on the unread pager semantics.
- Nothing else moved: no `else` branch on the `when (page)`, no scroll-state bundling or reorder, no strip, indicator or cell change, no dependency, no Compose UI test, no git write beyond this bake's own commit.
- **Open, and the user's to call:** the device pass over the four tabs and over the merged extra-settings changes, the second-density emulator check, and the coastline row grid's Medium finding.
- Open, carried: the heading line's colour row reads **Default colour** with nothing to default from.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the derived page count at line 97 and the one surviving effect at 102–104
- `docs/ui-component-guidelines.md` — §2.11's single-source-of-truth row
- `xTrack/Ui_Settings/260919_FEAT_PLN_Ui_Settings_settings-tab-fourth-tap.md` — the plan, its out-of-scope list and its Outcome

## Next Step

The device pass: tap each of the four tabs and confirm the strip and the page both land on the tapped tab, then reopen the overlay on a tab other than the first to confirm the persisted tab still lands and the pager still holds four pages. No baseline exists for a before-and-after comparison, so the pass shows the after state alone.
