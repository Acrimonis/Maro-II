# Plan — Move menu Tracks rendering + Import/Export into Settings

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: implemented · 2026-10-01
> Follows the menu→Settings shuffle already shipped in `261001_FEAT_PLN_Ui_General_menu-navigation-shuffle.md`.

## Goal

Two moves from the right-side menu's Tracks card into the Settings **Tracks** card:

1. Move the **Display Tracks with:** render-axes control into Settings, as a sub-section above *Tracks Appearance*.
2. Move the **Import / Export** pair into Settings, as a sub-section below *Track Speed and Direction*.

## Destination tab note

The Settings Tracks card — the one holding the *Tracks Appearance* expander ([`settings_track_settings_label`](app/src/main/res/values/strings.xml:53)) and the *Track Speed and Direction* expander ([`settings_tracks_direction_settings_label`](app/src/main/res/values/strings.xml:389)) — lives in the **Layers** tab of [`MapScreenSettingsOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:211). The Navigation tab holds Orientation aids, Re-display on approach, Route and Map offset, with no Tracks section, so both moves target the Layers-tab Tracks card named by those two anchors.

## Current state

- Menu Tracks card [`MenuDrawerOverlay.kt:218`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:218): Live stats (conditional), Track List row, then the **Display Tracks with:** block ([`273-310`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:273)) — a `menu_tracks_rendering` caption plus a `MultiSelectRow` over the two `TrackAxis` chips — and the **Import / Export** pair ([`312-338`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:312)).
- The two chips read/write `trackArrows` / `trackColours`, carried to the menu through `MenuOverlayData` ([`OverlayLayerParams.kt:53`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:53)) and `buildMenuOverlayData` ([`MapOverlayData.kt:25`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:25)); their writers are `applyTrackArrowsChange` / `applyTrackColoursChange` in [`MapScreen.kt:3540`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3540).
- Settings Tracks card [`MapScreenSettingsOverlay.kt:211`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:211): `CardDescription`, the *Tracks Appearance* expander ([`217`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:217)), then the *Track Speed and Direction* expander ([`450`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:450)).
- The map repaints the axes itself: the track overlay's rebuild keys already include `trackArrows` and `trackColours` ([`MapTrackOverlayEffects.kt:73`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:73)), so a Settings write needs no `mapView?.invalidate()`.

## Change 1 — move Display Tracks into Settings

- Remove from [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt):
  - the block at [`273-310`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:273);
  - parameters `trackArrows`, `trackColours`, `onTrackArrowsChange`, `onTrackColoursChange` ([`79-86`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:79));
  - the now-unused `TrackAxis` enum ([`55`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:55)) and the `MultiSelectRow` import.
- Remove from [`OverlayLayerParams.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:53): `MenuOverlayData.trackArrows`, `trackColours`.
- Remove from [`MapOverlayData.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:25): the two builder lines.
- Remove from [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt): the `onTrackArrowsChange` / `onTrackColoursChange` parameters, the `trackArrows` / `trackColours` reads ([`195-196`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:195)) and the four menu args ([`399-401`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:399)).
- Remove from [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt): the `onTrackArrowsChange` / `onTrackColoursChange` call-site args, and the now-dead `applyTrackArrowsChange` / `applyTrackColoursChange` helpers ([`3540`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3540)).
- Add in [`MapScreenSettingsOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:215), directly after the Tracks `CardDescription` and above the *Tracks Appearance* expander:
  - a sub-section reproducing the menu block — the `menu_tracks_rendering` caption plus a `MultiSelectRow` over Arrows/Colours, reading `settings.trackArrows` / `settings.trackColours` and writing through `onUpdateSettings { it.copy(trackArrows = on) }` / `it.copy(trackColours = on) }`;
  - a `SectionDivider()` after it.
  - Requires importing `MultiSelectRow`.

## Change 2 — move Import / Export into Settings

- Remove from [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt): the pair at [`312-338`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:312) and parameters `onImportTracks`, `onExportAllTracks`.
- Remove from [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt): the `onImportTracks` / `onExportAllTracks` menu args (the inline `onTrackAction` wrappers).
- Thread the callbacks into Settings:
  - `SettingsOverlay` and `LayersSettings` gain `onImportTracks: () -> Unit` and `onExportAllTracks: () -> Unit` parameters.
  - In [`OverlayLayer.kt:854`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:854), pass them wired as the menu did, with the settings door closed first: `onImportTracks = { onDismissSettings(); onTrackAction(ListAction.ImportTracks) }` and `onExportAllTracks = { onDismissSettings(); onTrackAction(ListAction.BatchExportGpx(trackSummaries.map { it.id }.toSet())) }`.
- Add in [`MapScreenSettingsOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:516), below the *Track Speed and Direction* expander and before the card's closing spacer:
  - a `SectionDivider()`, then the same two-`ConfirmActionButton` row the menu drew (Export / Import, `ConfirmActionRole.SECONDARY`), reusing `action_export` / `action_import`.
  - Requires importing `ConfirmAction`, `ConfirmActionButton`, `ConfirmActionRole`.

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`

## Decisions / risks

- **No new strings**: the moves reuse `menu_tracks_rendering`, `menu_render_arrows`, `menu_render_colours`, `action_export`, `action_import`, all already present in both locales.
- **No map invalidate in Settings**: the track overlay rebuilds from `trackArrows` / `trackColours` in its own keys, so the Settings writers are plain `onUpdateSettings` calls.
- **Menu leftovers kept**: Live stats and the Track List row stay in the menu; only the render axes and the Import/Export pair move.
- **Import/Export closes the Settings drawer** before acting, mirroring the menu's dismiss-then-act order.
