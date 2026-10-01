# Plan — move Route to the top of Settings → Navigation

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: implemented · 2026-10-01

## Goal

Reorder the Settings Navigation tab so the **Route** section (free-water pace + slow-water budget) is first, above *Orientation aids*.

## Current state

`NavigationSettings` ([`MapScreenSettingsOverlay.kt:1112`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1112)) lays its sections out as: Orientation aids ([`1123`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1123)) → Re-display on approach → **Route** ([`1359`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1359)) → Automatic map offset.

## Change

- Move the Route section block — the comment, `SectionHeader(route_trip_title)`, its `CardArea` (pace slider + slow-water budget slider) and its trailing section-gap `Spacer` — from [`1359-1399`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1359) to the top of the `Column`, immediately before *Orientation aids* ([`1123`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1123)).
- Re-indent the moved block to the `Column`'s 8-space level so it reads like the sections around it.

Result order: Route → Orientation aids → Re-display on approach → Automatic map offset.

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`

## Decisions / risks

- No strings or settings change; this is a pure section reorder.
- The moved block keeps its pace and slow-water-budget controls exactly as they are.
