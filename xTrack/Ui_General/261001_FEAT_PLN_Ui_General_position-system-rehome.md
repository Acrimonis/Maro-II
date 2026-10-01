# Plan — drop Settings GPS-mode row and re-home the Navigation group

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: implemented · 2026-10-01

## Goal

1. Remove the GPS-mode toggle from Settings → Position.
2. Move the Navigation group (its GPS Tuning section) into Settings → System, above *Screen*.

## Current state

- PositionSettings [`MapScreenSettingsOverlay.kt:1444`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1444) holds the *Position source* section — a `SectionHeader(settings_section_position)` ("Navigation"), then a card with the GPS-mode `ToggleRow` ([`1462-1470`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1462)) and the *GPS tuning* `Expander` ([`1473`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1473)) — followed by *Stop detection*.
- SystemSettings [`1592`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1592) holds Language → Route algorithm → Screen → Regenerate Layers.
- `onGpsModeChange` threads MapScreen → OverlayLayer → SettingsOverlay → PositionSettings, and is used only by that one Settings row (the map's top-left GPS icon uses MapScreen's own local).

## Change 1 — remove the GPS-mode row

- In PositionSettings, delete the `ToggleRow` ([`1462-1470`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1462)) and its separating `Spacer(groupedRowGap)` ([`1472`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1472)), leaving the card to open with the GPS tuning expander.
- Drop `onGpsModeChange` and `onDismiss` from the `PositionSettings` signature ([`1447-1448`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1447)).
- Update the section comment ([`1457`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1457)) — it no longer carries the Demo/GPS position source.

## Change 2 — move the Navigation group to System, above Screen

- Move the whole group — the comment, `SectionHeader(settings_section_position)`, its header `Spacer`, the `CardArea` (now just the GPS tuning expander) and its trailing section-gap `Spacer` — from PositionSettings into SystemSettings, inserted between *Route algorithm* ([`1636`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1636)) and *Screen* ([`1640`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1640)).
- Both tabs use the same 8-space column indentation, so the block moves without re-indenting.

## Change 3 — plumbing cleanup

`onGpsModeChange` loses its only Settings consumer, so the chain shrinks:

- SettingsOverlay: drop the `onGpsModeChange` parameter ([`97`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:97)) and simplify the page-2 call ([`184`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:184)).
- OverlayLayer: drop the `onGpsModeChange` parameter ([`120`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:120)) and its SettingsOverlay arg ([`830`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:830)).
- MapScreen: drop the `onGpsModeChange = onGpsModeChange` arg into `OverlayLayer` ([`3120`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3120)); the local `onGpsModeChange` stays for the map's top-left GPS icon.

## Change 4 — strings

- Remove the now-dead `settings_gps_mode_desc` from both locale files; `settings_gps_mode_label` stays (it labels the map-offset GPS toggle).

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`

## Decisions / risks

- GPS mode remains reachable through the map's top-left GPS icon; only the Settings row goes.
- The moved group keeps its "Navigation" title (`settings_section_position`, shared with the menu's Navigation card).
- The Position tab then holds only *Stop detection*; the System tab gains the Navigation/GPS-tuning group above *Screen*.
