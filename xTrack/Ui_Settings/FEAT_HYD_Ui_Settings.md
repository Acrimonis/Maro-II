# Context Hydration — Ui_Settings — 2026-09-18

**Last Bake:** 2026-09-18 19:52 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped since the last bake — one unread claim was made and corrected within the session, a shell tool declared absent before it was checked, and no machine-shaped data file was opened, no device was touched and the implementation waited for the explicit order.

## State

Branch **`feature/zones-transparency-settings`**, created from `origin/develop` at the end of the session; the working tree is dirty with the regulated-zone transparency work, uncommitted. `apk-build.bat` SUCCESS with no new warnings; the scoped `ui.map` + `config` run reports 189 tests with only the five pre-existing `maro.properties`-versus-`AppConfig` reds, and `MapOverlayRendererTest` contributes five green ones.

- The Regulated zones card of the Layers tab now leads with a **Regulated Zones Appearance** expander holding one two-thumb Transparency row (outline left / fill right, 5% snap, commit on release), its defaults 20/80 mirroring the 300 m band so both controls read the same pair.
- Persisted as `regulatedZoneFillTransparencyPct` / `regulatedZoneBoundaryTransparencyPct`; `drawRegulatedZones()` derives both polygon alphas through the shared `transparencyPctToAlpha()` — which `drawZone300()` now uses too — so the baked `0x30000000` fill, the literal `alpha = 200` and the per-type `RegulationZoneColor` pair are all gone. The icon stack keeps its opaque category colours.
- The subject-free `settings_transparency_border_fill_label` / `settings_transparency_border_fill_value_fmt` replaced the 300 m and marker-halo duplicates; the tracks row's own `settings_transparency_label` is a different sentence and was left untouched.
- Open: the device pass (0%, both extremes, the reordered expanders, the untouched-install look); the two state files resolved out of the branch-move conflict are still unmerged in the index; `stash@{0}` holds the pre-move WIP until it is dropped.
- Logged, not fixed: `map.zone300.fill` is now readerless, and the `#CC` alphas on three `regulatedZone.type.*` entries never reach the outline because `Paint.alpha` replaces them.

## Target Files

- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the persisted pair and its prefs keys
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt` — `transparencyPctToAlpha()`, `regulatedZoneColor()`, `drawRegulatedZones()`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` / `OverlayTracker.kt` — the parameter chain, its `last*` seeds and the effect guard
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the settings → map call site
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the Appearance expander
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the shared heading and value format
- `app/src/test/java/ykws/android/maro/ui/map/MapOverlayRendererTest.kt` — the derivation's first coverage

## Next Step

Add the two conflict-resolved state files to the index, then run the device pass over the new row; the plan of record is `xTrack/Ui_Settings/260918_FEAT_PLN_Ui_Settings_regulated-zones-transparency.md`.
