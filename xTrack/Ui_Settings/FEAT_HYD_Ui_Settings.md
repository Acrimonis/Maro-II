# Context Hydration — Ui_Settings — 2026-09-19

**Last Bake:** 2026-09-19 11:03 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, no work began without an explicit order, the device was never touched, and the one relayed state claim that turned out wrong (the previous hydration's tree paragraph, which git contradicted) was reported as a contradiction rather than quietly resolved.

## State

Branch **`feature/extra-settings`**, created from `origin/develop` at `28d419f` by the ordered overwrite; the stroke-width and shoreline-colour change is complete in the working tree and uncommitted. `apk-build.bat` SUCCESS with no new warnings, and the scoped `ui.map` + `config` run sits at 216 tests with only the six pre-existing `maro.properties`-versus-`AppConfig` drift reds, the two new classes being green.

- Six values are each a property and a setting, seeded from `AppConfig` and clamped once where the setting is read: `map.coastline.widthPx`, `map.coastline.transparencyPct`, `map.coastline.mainland.color`, `map.coastline.island.color`, `map.zone300.boundary.widthPx`, `map.regulatedZone.outline.widthPx`.
- Eleven colour keys moved to `maro.properties` with their names unchanged; the isobath and regulated-zone families stayed palette keys, being keyed by the data's own taxonomy.
- Open: the device pass over the three widths at both extremes, the shoreline transparency at both ends, and the two shoreline colours on an island and on the mainland.
- Logged, not fixed: the px-to-dp conversion of the whole paint code, parked as its own change with `MarkerHalo`'s ring-versus-padding composition, the isobath base-plus-bonus arithmetic and the dash patterns named as the traps a partial conversion hits; and the theme doc's rows that still restate values already in `maro.properties`.

## Target Files

- `app/src/main/assets/maro.properties` — the eleven moved keys plus the four new ones
- `app/src/main/assets/colors.properties` — the swept keys gone, palette roles and the two taxonomy families left
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the moved keys' readers, their KDoc repointed to `maro.properties`, the width parse clamps removed
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the six fields, their seeds from `AppConfig` and their single clamps
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt` — the three draw functions' parameters, with `transparencyPctToAlpha()` on the coastline
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` / `OverlayTracker.kt` — the six parameters, seeds, effect keys and guards
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the two width rows and the coastline Appearance expander
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the new labels, and `settings_value_px` present in both locales
- `app/src/test/java/ykws/android/maro/data/settings/CoastlineAppearanceSettingsTest.kt` — the seeds, the clamps and the round-trip
- `app/src/test/java/ykws/android/maro/config/CoastlineAppearancePropertiesTest.kt` — the six keys against the code's defaults, and the retired width pair

## Next Step

The device pass over the three widths, the shoreline transparency and the two shoreline colours; the plan of record is `xTrack/Ui_Settings/260919_FEAT_PLN_Ui_Settings_stroke-widths-and-shoreline-colours.md`.
