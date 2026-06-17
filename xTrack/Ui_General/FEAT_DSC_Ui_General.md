---
name: Ui_General
status: active
created: 2026-06-08 16:43
modified: 2026-06-17 10:28
active_subfeature: none
---

# Feature: Ui_General

**Description:**
App-lifecycle UX for the Maro-II app: intercept the system back action to guard
against accidental exits, and let the user keep the device screen awake while the
app is running. Extended with page-layout concerns: edge-to-edge rendering, status
bar immersion, and WindowInsets management.

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

### page layout  [x]

Edge-to-edge rendering with `enableEdgeToEdge()`, immersive status bar (dark background, light icons), and proper WindowInsets consumption in MapScreen to fix portrait dashboard bottom clipping.

#### Todos
- [x] Add `enableEdgeToEdge()` in MainActivity.onCreate() before setContent
- [x] Add `WindowInsetsController` status bar appearance (light icons on dark background)
- [x] Add `WindowInsets.systemBars` padding in MapScreen root Box
- [x] Verify build — BUILD SUCCESSFUL

#### Rules
- `enableEdgeToEdge()` must be called before `setContent()` in Activity.onCreate()
- WindowInsets handling must not break existing landscape layout
- Status bar icons must be light (white) on the dark background

#### Key Files
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — add `enableEdgeToEdge()`
- `app/src/main/res/values/themes.xml` — theme update
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — WindowInsets padding
- `gradle/libs.versions.toml` — check activity-ktx version

### immersive ui rework  [x]

Extend `enableEdgeToEdge()` to the nav bar: remove blanket `windowInsetsPadding(WindowInsets.systemBars)` from the root Box and apply targeted insets only to the overlay controls. The map fills the full screen behind both system bars.

#### Todos
- [x] Remove `windowInsetsPadding(WindowInsets.systemBars)` from the root `Box` in `MapScreen` — map draws full-screen behind both status bar and nav bar
- [x] Add `windowInsetsPadding(WindowInsets.statusBars)` + left padding to top-left icons Row (GPS + EarthWater) — removed redundant 6dp top padding
- [x] Add `windowInsetsPadding(WindowInsets.systemBars)` + horizontal padding to right-edge control stack Column — removed redundant top/bottom
- [x] Add `windowInsetsPadding(WindowInsets.navigationBars)` to bottom overlay areas (loading overlay, exit toast, zone info row)
- [x] Build & run — BUILD SUCCESSFUL, debounced on device (controls visible, map fills full screen)
- [-] DashboardPanel nav bar handling: decision — dashboard background (#16213E) matches window background, extends behind nav bar seamlessly; no extra padding needed
- [-] `portraitDashboardHeight` formula unchanged — dashboard extends behind nav bar without adjustment

#### Rules
- The map surface must fill the entire screen (behind both system bars)
- Overlay controls (GPS/EarthWater icons, Settings button, zoom, dashboard) must remain visible and not overlap with system bars
- `WindowInsets` consumption order: `windowInsetsPadding` first, then manual `padding`, so insets are consumed before extra spacing

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — root Box modifier, top-left icons Row, right-edge Column, DashboardPanel modifiers, bottom overlays
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — no change needed (already calls `enableEdgeToEdge()`)

### ButtonColors  [x]

Harmonise map control buttons (zoom +/- , settings gear, GPS/EarthWater toggles, dashboard tile accent strips) onto the dark dashboard theme using the centralized `colors.properties` palette. Replace hardcoded `0xFF...` literals with named colour tokens.

#### Todos
- [ ]

#### Rules

#### Key Files

### SVSpacing  [x]

Adjust vertical spacing between buttons in the right-edge control stack for improved touch targeting and visual balance.

#### Todos

#### Rules

#### Key Files

### map-print-layout  [ ]

#### Todos

#### Rules

#### Key Files

## Todos

## Rules

## Key Files

## Docs
- `plans/portrait-bottom-space-statusbar-discussion.md` — analysis of portrait bottom space and status bar immersion
- `plans/btn-color-harmonization.md` — button color harmonization: match map control buttons to dashboard dark theme
- `docs/color-scheme.md` — canonical reference for all colour tokens in the app
- `plans/color-props-migration-plan.md` — migration plan: all colours → colors.properties
- `plans/button-colors-discussion.md` — discussion: UI round button color identification and exploration
- `plans/right-edge-controls-gap-asymmetry-analysis.md` — root cause analysis of asymmetric gap between right-edge controls and map edges after immersive rework
- `plans/map-overlay-layout-rationalization.md` — complete layout refactor: 2-column Row structure, symmetric 6dp margins, orientation-aware insets
- `plans/map-overlay-layout-inventory.md` — current overlay inventory and planned evolution audit
