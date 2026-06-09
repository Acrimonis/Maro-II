---
name: AppBakFlow
status: done
created: 2026-06-08 16:43
modified: 2026-06-08 16:43
active_subfeature: none
---

# Feature: AppBakFlow

**Description:**
App-lifecycle UX for the Maro-II app: intercept the system back action to guard
against accidental exits, and let the user keep the device screen awake while the
app is running. Branch `feature/app-bak-flow` (off `develop`).

## Subfeatures

### BackToExitConfirm  [x]

Intercept the in-app back action and require confirmation before exiting. On the
first back press, show a transient 2-second "Press back again to exit" toast; exit
(`finishAffinity`) only if back is pressed again within that window. The settings-
overlay back is unchanged (it just closes settings).

#### Todos
- [x] Intercept the back action while in the app (Compose `BackHandler`, enabled when no overlay).
- [x] On first back press, show a 2-second "Press back again to exit" toast.
- [x] Exit only when back is pressed again within the 2-second window; otherwise cancel.

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — exit-confirm `BackHandler`, `Context.findActivity()` helper, and the toast (rendered in `MapContent`'s bottom-overlay slot: bottom-centre, padded `end = 76dp` to clear the right-edge control stack; colour `0xFF16213E` = dashboard tile `cardBg`).
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `exit_press_back_again`.

### KeepScreenOn  [x]

Add a user setting "Keep the phone on when app is running" that prevents the
screen from sleeping while the app is in the foreground.

#### Todos
- [x] Add a "Keep phone on" toggle to Settings (bottom of the Display section).
- [x] Keep the screen awake while the app runs when the setting is enabled.
- [x] Persist the setting alongside the existing app settings.

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `keepScreenOn` field/key/load/persist (SharedPreferences, mirrors `languageCode`).
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `SettingsToggleRow` + `DisposableEffect` driving `LocalView.keepScreenOn`.
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `settings_keep_screen_on_label` / `_desc`.

## Todos

## Rules

## Key Files

## Docs
