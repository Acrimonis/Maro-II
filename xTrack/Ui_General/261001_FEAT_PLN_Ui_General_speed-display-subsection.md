# Plan — Speed Display subsection inside Track Speed and Direction

> Feature: Ui_General · Branch: `feature/ui-shuffle` · Status: implemented · 2026-10-01
> Follow-up on `261001_FEAT_PLN_Ui_General_tracks-card-tweaks.md`.

## Goal

Move the Display Tracks chips into the *Track Speed and Direction* sub-card as their own titled subsection, headed **Speed Display**, following the `SubSectionHeader` pattern already used inside that card.

## Current state

- Settings Tracks card [`MapScreenSettingsOverlay.kt:224`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:224): the *Track Speed and Direction* expander's `NestedCard` ([`466`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:466)) opens with the *Arrow density* `SubSectionHeader`; the Display Tracks block ([`528-558`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:528)) sits below the expander, above Import/Export, as a caption (`menu_tracks_rendering`) + `MultiSelectRow`.

## Change 1 — relocate and retitle

- Remove the Display Tracks block from [`528-558`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:528); the `SectionDivider()` before Import/Export stays.
- Insert at the top of the direction `NestedCard` ([`466`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:466)), above *Arrow density*:
  - `SubSectionHeader(title = stringResource(R.string.settings_tracks_speed_display_label))` — the new "Speed Display" heading;
  - `Spacer(Modifier.height(8.dp))` then the existing `MultiSelectRow` over Arrows / Speed Colors, still reading `settings.trackArrows` / `settings.trackColours` and writing through `onUpdateSettings`;
  - a `SectionDivider()` separating the subsection from *Arrow density*.
- The `DisplayTrackAxis` enum and `MultiSelectRow` import stay; the old caption `Text` (`menu_tracks_rendering`) is dropped with the block.

## Change 2 — strings

- Add `settings_tracks_speed_display_label` = **Speed Display** (EN) / **Affichage de la vitesse** (FR) in both locale files.
- Remove the now-dead `menu_tracks_rendering` ("Display Tracks with:" / "Afficher les traces avec :") from both locale files.

## Files touched

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`

## Decisions / risks

- **"Speed Display"** is title-only (no description), matching the *Arrow density* `SubSectionHeader` shape in the same card.
- **French** "Affichage de la vitesse" mirrors the app's existing *vitesse* terminology.
- The chip labels **Arrows** / **Speed Colors** are unchanged; only the container moves and gains a heading.
