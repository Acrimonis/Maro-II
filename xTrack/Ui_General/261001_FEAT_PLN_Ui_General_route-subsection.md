# Plan — Route subsection below Speed Display

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: implemented · 2026-10-01
> Follow-up on `261001_FEAT_PLN_Ui_General_speed-display-subsection.md`.

## Goal

Move the two route rendering gates — *Speed colours on routes* and *Arrows on routes* — out of *Tracks Appearance* and into a new **Route** subsection, placed directly below the *Speed Display* subsection inside *Track Speed and Direction*.

## Current state

- The two gates live in the *Tracks Appearance* expander ([`MapScreenSettingsOverlay.kt:445`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:445)): `ToggleRow(settings_routes_speed_color_label)` → `routeSpeedColor` and `ToggleRow(settings_routes_arrows_label)` → `routeSpeedArrows`, behind a `SectionDivider` and their R37/R38 comment ([`440-444`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:440)).
- The *Track Speed and Direction* `NestedCard` now opens with the *Speed Display* subsection ([`467`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:467)) — heading, chips, then a `SectionDivider` before *Arrow density*.
- `route_trip_title` = "Route" in both locales ([EN `665`](app/src/main/res/values/strings.xml:665) / [FR `666`](app/src/main/res/values-fr/strings.xml:666)).

## Change

- Remove the `SectionDivider`, the R37/R38 comment and the two `ToggleRow`s from *Tracks Appearance* ([`440-454`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:440)).
- In the *Track Speed and Direction* `NestedCard`, insert directly below the *Speed Display* subsection and above *Arrow density*:
  - `SubSectionHeader(title = stringResource(R.string.route_trip_title))` — the **Route** heading;
  - the two `ToggleRow`s (with their R37/R38 comment), writing `routeSpeedColor` / `routeSpeedArrows` through `onUpdateSettings`;
  - a `SectionDivider()` before *Arrow density*.

Result order in the card: Speed Display → Route → Arrow density → Gap range → Speed range.

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`

## Decisions / risks

- **No new string**: the heading reuses `route_trip_title` ("Route"), the same word in both locales.
- **Placement** is inside the *Track Speed and Direction* sub-card, directly below *Speed Display*; the two gates move as-is (labels, state and writers unchanged).
