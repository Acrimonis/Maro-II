# Context Hydration — Ui_Settings — 2026-09-19

**Last Bake:** 2026-09-19 13:36 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, no work began without an explicit order, the device was never touched, and every claim about the code came from a file read. One earlier report held the cap-arrow repairs outstanding when they were already committed; the source showed otherwise and the claim was corrected.

## State

Branch **`feature/extra-settings`**, now carrying four changes together. The strokes and shoreline colours shipped as `82b2b1d`, rebased onto `origin/develop` and pushed as `217a405`, with a pull request open. The heading line and head arrow appearance — seven settings, the Speed Colour mode, an Appearance expander under each toggle — landed with its four device-review corrections and the colour-row tidy as `256dfc2`. On top sit the px→dp migration of the map's paint lengths, run through the `#implement` pipeline, and the dropped stale test expectations.

- Build green: `:app:assembleDebug` SUCCESSFUL, and the full suite completes at **478 tests, 0 failures, 9 skipped**, where it stood at 481 with three failures at HEAD since 2026-09-12.
- The three stale expectations were dropped on the user's order — two `MarkerFilterMigrationTest` v7 cases asserting a prefs migration the code no longer performs, and one `RegulationAggregatorTest` case asserting a type gate the aggregator no longer has — with the false migration claim removed from the class KDoc and one orphaned import. The aggregator's 50 m same-location collapse is now **unpinned**.
- The px→dp pass renamed three settings keys to `…widthDp` with a `toFloatOrNull` parse, float fields, float prefs keys and a 0.5 dp row grid. **Open, and the user's to call:** the coastline's shipped 3.3333 dp default is not on that grid, so once that row is dragged the 10 px look cannot be returned to.
- The pipeline's independent review found one High and it is repaired: the marker circle's dash was built with the helper's `density = 1f` default, so the `1f` default is deleted and the compiler now forces all fourteen `buildPolyline` call sites to name a density.
- **Open:** the device pass over the strokes, the arrow/line and the dp conversion together, plus the second-density emulator check — the only place the conversion is visible, since on the 3× tuning phone it is invisible by design.
- Wording finding, open: the heading line's colour row reads **Default colour** with nothing to default from.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt` — the `dpToPx` helper, `transparencyPctToAlphaFraction`, the three `…widthDp` paint sites and the isobath group
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt` — the arrow and the line: parameters, the head derivation, the dash ratios, the shaft inset
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`, `MarkerHalo.kt`, `MapMarkerEffects.kt` — the marker strokes, the dashes, the two adds and the single density accessor
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `MapScreenSettingsOverlay.kt` — the conversion call sites, the three width rows on their dp grid, the two Appearance expanders
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the renamed readers, the float fields and the defaults following the file
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the renamed settings, their seeds, clamps and `*_width_dp` prefs keys
- `app/src/main/assets/maro.properties` — the `…widthDp` keys, the swept colours, the reference density stated once
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the labels in both locales, and the retired `settings_value_px` gone from both
- `app/src/test/java/ykws/android/maro/ui/map/MapOverlayRendererTest.kt`, `TrackOutlineTest.kt`, `TrackDirectionOverlayTest.kt`, `app/src/test/java/ykws/android/maro/config/HeatmapRampPropertiesTest.kt` — the dp round trips, the ratios and the settled reds
- `xTrack/UI_Map/260919_FEAT_PLN_UI_Map_px-to-dp-migration.md` — the inventory, the risk table and the review's findings

## Next Step

The device pass over all three changes, and the second-density emulator check that is the only evidence the conversion did anything; the row-grid Medium stays the user's decision.
