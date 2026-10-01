# Plan — move Stop detection under Route in Navigation

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: implemented · 2026-10-01

## Goal

Move the **Stop detection** section from the Settings Position tab into the Navigation tab, directly below the **Route** group.

## Current state

- PositionSettings ([`MapScreenSettingsOverlay.kt:1443`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1443)) now holds only the Stop detection section ([`1455-1509`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1455)).
- NavigationSettings has Route at the top, ending with its section-gap `Spacer` ([`1162`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1162)) before *Orientation aids* ([`1164`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1164)).

## Change

- Remove the Stop detection section (comment + `SectionHeader(settings_idle_section_label)` + header `Spacer` + `CardArea` + trailing section-gap `Spacer`) from PositionSettings.
- Insert it in NavigationSettings between Route's section-gap `Spacer` and *Orientation aids* — the block carries its own trailing gap, so no re-indent or extra spacing is needed.

Result order in Navigation: Route → Stop detection → Orientation aids → Re-display on approach → Automatic map offset.

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`

## Decisions / risks

- **The Position tab becomes empty** after this move — it keeps its tab label but renders no sections. Flagged as a consequence; nothing further is changed here.
- No strings or settings change; the section moves as-is.
