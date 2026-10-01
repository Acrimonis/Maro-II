# Plan — Right-side menu Navigation cleanup + Show zones re-home

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: in design · 2026-10-01
> Rebased onto `origin/develop` `db0a01f` before planning finished; citations below match the merged code.
> Scope touches the right-side menu drawer (Ui_General/Ui_Menu), the Settings Layers tab (Ui_Settings), and the Navigation auto-show gate (Navigation).

## Goal

Three moves on the right-side menu, one re-home into Settings:

1. Remove the **GPS mode** toggle from the menu's Navigation card.
2. Remove the **Auto-show zones** master switch from the same card, and delete its backing field — the per-mode Settings toggles become the single source.
3. Move the **Show zones** toggle out of the menu's Markers card into Settings → Layers → Markers, placed above *Markers Appearance*.

## Current state

- The menu's first card is the Navigation section — [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:152) under `settings_section_position` = "Navigation".
- GPS mode row: [`MenuDrawerOverlay.kt:158`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:158) — `ToggleRow(settings_gps_mode_label)`, checked from `gpsMode`, tinted `gpsToggleColor`.
- Auto-show master row: [`MenuDrawerOverlay.kt:196`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:196) — `ToggleRow(settings_autoshow_master_label)`, guarded by `autoShowMasterVisible`, writing `autoShowMasterOverride`.
- Show zones row: [`MenuDrawerOverlay.kt:454`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:454) — `ToggleRow(menu_show_zones)` from `markerZonesVisible`.
- Menu data is built by [`buildMenuOverlayData`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:19) (hoisted from `MapScreen` by the mapscreen-health merge) — it still carries `gpsMode`, `autoShowMasterVisible`, `autoShowMasterOverride`, `gpsToggleColor`, `markerZonesVisible` ([`27-31`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:27)).
- Master gate: [`NavigationViewModel.kt:790`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:790) — `(if (gpsMode) approachAutoShowGps else approachAutoShowDemo) && autoShowMasterOverride`.
- Settings Navigation tab already holds the two per-mode enables: [`MapScreenSettingsOverlay.kt:1208`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1208) — `settings_redisplay_enable_gps` / `settings_redisplay_enable_demo`.
- Settings Layers tab Markers card: [`MapScreenSettingsOverlay.kt:523`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:523) — `CardDescription`, then the *Markers Appearance* expander (`settings_marker_rendering_label`), then *Auto markers*.

## Change 1 — remove GPS mode toggle from the menu

- [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt):
  - Drop the `ToggleRow` at [`158-164`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:158).
  - Drop parameters `gpsMode`, `onGpsModeChange`, `gpsToggleColor` from the signature ([`74-79`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:74)).
  - Restructure the route block's leading divider: the `SectionDivider()` before `RouteSummaryBlock` ([`173`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:173)) moves inside the `if (routeSummaryVisible)` block, after the summary, so no divider leads the card and none doubles.
- [`OverlayLayerParams.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:53): drop `gpsMode`, `gpsToggleColor` from `MenuOverlayData`.
- [`MapOverlayData.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:19): drop `gpsMode = appSettings.gpsMode` ([`27`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:27)), `gpsToggleColor = gpsToggleColor` ([`30`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:30)), and the `gpsToggleColor` parameter ([`21`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:21)).
- [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt): drop the `gpsMode`/`gpsToggleColor` reads ([`197`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:197), [`200`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:200)) and the two args into `MenuDrawerOverlay` ([`373-374`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:373), [`378`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:378)). `onGpsModeChange` stays on `OverlayLayer` — the Settings Position tab still reads it ([`857`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:857)).
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt): drop the `gpsToggleColor` arg into `buildMenuOverlayData` ([`3129`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3129)); delete the now-dead `gpsToggleColor` local ([`1029`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1029)) — its only consumer was the menu row. `onGpsModeChange` at [`3135`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3135) stays (top-left GPS icon + Settings Position tab).

## Change 2 — remove Auto-show zones master switch

- [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt): drop the whole block at [`196-207`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:196) and parameters `autoShowMasterVisible`, `autoShowMasterOverride`, `onAutoShowMasterChange` ([`76-78`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:76)).
- [`OverlayLayerParams.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:55): drop `autoShowMasterVisible`, `autoShowMasterOverride`.
- [`MapOverlayData.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:28): drop `autoShowMasterVisible = …`, `autoShowMasterOverride = …`.
- [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt): drop `onAutoShowMasterChange` from the signature ([`121`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:121)), the reads ([`198-199`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:198)), and the menu args ([`375-377`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:375)).
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt): drop the `onAutoShowMasterChange` arg ([`3136`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3136)).
- [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt): remove the field `autoShowMasterOverride` ([`68`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:68)), its pref read ([`524`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:524)), its write ([`778`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:778)) and the `KEY_AUTO_SHOW_MASTER_OVERRIDE` constant ([`933`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:933)).
- [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:790): the gate becomes `val globalEnabled = if (cfg.gpsMode) cfg.approachAutoShowGps else cfg.approachAutoShowDemo`.
- `res/values/strings.xml` + `res/values-fr/strings.xml`: delete the now-dead `settings_autoshow_master_label` ([EN `39`](app/src/main/res/values/strings.xml:39) / [FR `38`](app/src/main/res/values-fr/strings.xml:38)).

## Change 3 — move Show zones into Settings → Layers → Markers

- [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt): drop the `ToggleRow` at [`454-460`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:454) and parameters `markerZonesVisible`, `onToggleMarkerZones` ([`86-87`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:86)).
- [`OverlayLayerParams.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:58): drop `markerZonesVisible`.
- [`MapOverlayData.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:31): drop `markerZonesVisible = appSettings.markerZonesVisible`.
- [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt): drop `onToggleMarkerZones` from the signature ([`122`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:122)), the read ([`201`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:201)), and the menu args ([`412-413`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:412)).
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt): drop the `onToggleMarkerZones` arg ([`3137`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3137)); delete the now-dead `toggleMarkerZones` helper ([`3549`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3549)).
- [`MapScreenSettingsOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:523): in the Markers `CardArea`, insert directly after `CardDescription` ([`527`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:527)) and before the *Markers Appearance* `Expander` ([`528`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:528)):

  - `ToggleRow(label = stringResource(R.string.menu_show_zones), checked = settings.markerZonesVisible, onCheckedChange = { on -> onUpdateSettings { it.copy(markerZonesVisible = on) } })`
  - then a `SectionDivider()` separating it from *Markers Appearance*.

  The row moves as-is — same label, same persisted `markerZonesVisible` preference — so no string or key changes. No `mapView?.invalidate()` is needed here: `MarkerOverlay`'s draw effect is already keyed on `markerZonesVisible` ([`MarkerOverlay.kt:167`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:167)) and redraws on the setting change.

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`

## Decisions / risks

- **Master-switch removal is behavioural**: with `autoShowMasterOverride` deleted, zone reveal is governed by the per-mode enables alone — exactly the intent stated, but users who had the master off while a per-mode enable was on will now see reveal re-enabled. Accepted per the task.
- **Show zones placement**: inserted as a standalone row above *Markers Appearance* rather than a titled sub-section, matching "move it as it is"; no new string is introduced.
- **`onGpsModeChange` stays** on `OverlayLayer` and in `MapScreen` — the Settings Position tab and the top-left GPS icon still use it; only the menu's use is removed.
