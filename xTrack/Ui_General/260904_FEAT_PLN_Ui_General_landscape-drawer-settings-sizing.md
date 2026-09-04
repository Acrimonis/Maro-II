---
feature: Ui_General
topic: Landscape drawer + settings panel sizing and scroll
created: 2026-09-04 19:39 UTC
status: planned
---

# Plan: Landscape drawer + settings sizing

## Goal

1. Side panel (menu drawer) opens in landscape at the same width as portrait (75% of the short edge).
2. Settings panel opens in landscape at the portrait screen width (the short edge).
3. Both scroll vertically when content overflows, in all orientations.

## Current behaviour

- Landscape dimensions: `portraitDashboardHeight = maxWidth * 3/5`, `landscapeDashboardWidth = maxHeight` (the short edge) — [`MapScreen.kt:1812`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1812).
- Menu drawer: `fillMaxWidth(0.75f)` → 75% of the long edge in landscape — [`OverlayLayer.kt:273`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:273).
- Settings: `fillMaxSize()` → full screen (long edge) in both orientations — [`OverlayLayer.kt:625`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:625).
- Menu drawer content is not vertically scrollable.
- Settings tabs already use `verticalScroll(scrollState)` — [`MapScreen.kt:3415`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3415).

## Changes

- [`OverlayLayer.kt:271`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:271) — menu drawer: landscape width = its portrait width (75% of screen width → `landscapeDashboardWidth * 0.75f`); portrait unchanged (`fillMaxWidth(0.75f)`).
- [`OverlayLayer.kt:625`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:625) — settings: landscape width = its portrait width (100% of screen width → `landscapeDashboardWidth`); portrait unchanged (`fillMaxSize`).
- [`OverlayLayer.kt:187`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:187) + `:200` — add `showSettings` to `showScrim` and `showSettings -> onDismissSettings()` to `scrimDismiss` (outside tap closes Settings; map ignores it).

## Out of scope

- Track history + marker management drawers are also full-screen right drawers; left unchanged unless requested.

## Review notes

- Menu drawer is already scrollable: [`MenuDrawerOverlay.kt:108`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:108) uses `DrawerScaffold(scrollable = true)`, whose body is wrapped in `verticalScroll`. No change needed.
- Settings are already scrollable: all four tabs wrap content in `verticalScroll(scrollState)` — [`MapScreen.kt:3415`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3415), `:4101`, `:4456`, `:4671`. No change needed.
- Only the two width edits remain.

## Scrim / outside-tap behaviour (confirmed)

- A scrim (32% black, full-screen) already covers the menu, track history, marker management, marker viewing, and wizard non-Position steps; tapping it closes the panel — [`OverlayLayer.kt:187`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:187), `:209`.
- Settings has NO scrim (`showSettings` is not in `showScrim`), so outside taps hit the live map and do not close settings.
- Confirmed: add `showSettings` to `showScrim` and `showSettings -> onDismissSettings()` to `scrimDismiss`, so outside taps are ignored by the map and close settings. Keep the wizard Position steps scrim-less (map must stay draggable).

## 1.2× landscape width scale (new)

- New `maro.properties` key: **`ui.landscape.panel.widthScale`** (float, default `1.2`, clamp `0.5–3.0`).
- `AppConfig.uiLandscapePanelWidthScale: Float = 1.2f`, read in `AppConfig.init`.
- [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt): menu width → `landscapeDashboardWidth * 0.75f * uiLandscapePanelWidthScale`; settings → `landscapeDashboardWidth * uiLandscapePanelWidthScale`.

## Verify

- Build `apk-build.bat`; landscape: menu ≈ 75% short edge, settings ≈ short edge; both scroll when content exceeds the screen height.
