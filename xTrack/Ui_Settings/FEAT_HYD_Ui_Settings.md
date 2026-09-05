# Ui_Settings — Hydration (2026-09-05 13:42)

## State
Settings page UI, persistence (SharedPreferences), settings widgets, and UX. Latest work (tab-navigation-swipe-spacing, implemented 2026-09-05): the 4-tab `SettingsOverlay` (`HorizontalPager` + manual `Row` tab bar with `drawBehind` indicator) previously allowed unwanted horizontal swipe between tabs, and mid-slide the outgoing/incoming pages sat flush with no gap because the 24dp horizontal padding lived only on the outer `Column`. Fix: added `userScrollEnabled = false` to the pager (blocks finger drags, keeps programmatic `animateScrollToPage` so tab clicks still slide); dropped the horizontal inset from the outer `Column`; added `padding(horizontal = 24.dp)` to the header `Row`, tab-bar `Row` (padding modifier placed BEFORE `drawBehind` so the indicator stays aligned), and footer `Text`; and wrapped the pager `when(page)` content in a single padded `Box` so each page carries its own 24dp inset — producing a visible gap between adjacent controls during the slide while the settled layout stays pixel-identical. The 4 page composables were left untouched.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `SettingsOverlay`: outer Column padding, header/tab-bar/footer padding, `HorizontalPager` (`userScrollEnabled = false`), padded `Box` wrapper around pager `when(page)` content

## Next Step
Open `render-tweaks` todo (card rendering in settings overlays per ui-component-guidelines.md) and the deferred `settings apply on close` section (batch-apply side effects on dismiss). BUILD SUCCESSFUL.
