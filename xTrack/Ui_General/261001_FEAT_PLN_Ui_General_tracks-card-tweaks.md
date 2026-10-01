# Plan — Tracks card reorder, chip rename, Route action face

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: implemented · 2026-10-01
> Follow-up on `261001_FEAT_PLN_Ui_General_menu-tracks-shuffle.md`.

## Goal

1. Move the **Display Tracks with:** control to sit between the two collapsible expanders and the Import/Export buttons.
2. Rename the chip option **Colours** to **Speed Colors**.
3. Give the menu's **Route** action button the same rendering as the Import/Export buttons.

## Current state

- Settings Tracks card [`MapScreenSettingsOverlay.kt:224`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:224): `CardDescription` → Display Tracks block ([`227-258`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:227), caption + `MultiSelectRow` + `SectionDivider`) → *Tracks Appearance* expander → *Track Speed and Direction* expander → `SectionDivider` → Import/Export pair ([`561-582`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:561)).
- Chip label [`menu_render_colours`](app/src/main/res/values/strings.xml:316) = "Colours" / "Couleurs".
- Route arm button [`MenuDrawerOverlay.kt:388`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:388) = `ConfirmActionRole.PRIMARY`; Import/Export use `SECONDARY`.

## Change 1 — reorder Display Tracks

- Remove the Display Tracks block from above *Tracks Appearance* ([`227-258`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:227)), leaving `CardDescription` adjacent to the first expander again.
- Insert the same block (comment + `Column`, no trailing divider) immediately after the *Track Speed and Direction* expander closes ([`559`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:559)) and before the Import/Export row — the existing `SectionDivider()` ([`561`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:561)) stays as the one separator between Display Tracks and Import/Export.

Result order: CardDescription → Tracks Appearance → Track Speed and Direction → Display Tracks → SectionDivider → Import/Export.

## Change 2 — rename the chip

- [`values/strings.xml:316`](app/src/main/res/values/strings.xml:316): `Colours` → `Speed Colors`.
- [`values-fr/strings.xml:315`](app/src/main/res/values-fr/strings.xml:315): `Couleurs` → `Couleurs de vitesse` (plural, consistent with the existing `settings_speed_colour_label` = "Couleur selon la vitesse").

## Change 3 — Route action face

- [`MenuDrawerOverlay.kt:388`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:388): `role = ConfirmActionRole.PRIMARY` → `ConfirmActionRole.SECONDARY`; the half-width right alignment stays.
- Update the `RouteEndsSection` KDoc sentence ([`353-354`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:353)) and the inline comment ([`378-380`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:378)) that call it "the drawer's primary action" / §5.6's outcome rendering, so the docs no longer claim an accent primary for the Route door.

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`

## Decisions / risks

- **US spelling** "Speed Colors" per the user's wording; the French is proposed as "Couleurs de vitesse".
- **Route keeps its layout** — only the face (role) changes; the drawer then carries no `PRIMARY` action, matching the Import/Export pair's outlined look.
