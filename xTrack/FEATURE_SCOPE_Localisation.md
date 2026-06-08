---
name: Localisation
status: active
created: 2026-06-08 15:43
modified: 2026-06-08 15:43
active_subfeature: none
subs_total: 5
subs_done: 4
one_liner: Localise the app to English (default) and French with an in-app language selector and instant Compose locale switching (system → French on a fr device, else English).
---

# Feature: Localisation

**Description:** The app shipped with all user-facing text hardcoded as French literals. This feature
extracts every string into Android resources (English as the default `values/`, French in `values-fr/`),
adds a Settings language selector (System / English / Français) persisted via `SettingsManager`, and
applies the choice instantly through a Compose `CompositionLocalProvider` locale override — no Activity
restart, no new dependency. "System" follows the device language only when it is French, otherwise falls
back to a fully-English experience (text + number formatting). Verified on-device (Pixel 7); committed +
pushed to `feature/localisation`.

## Subfeatures

### StringExtraction  [x]
Extract ~70 hardcoded user-facing literals to `stringResource`; English default (`values/strings.xml`)
+ French (`values-fr/strings.xml`). Fixed two stray English "Not at sea" literals in the dashboard.

#### Key Files
- `app/src/main/res/values/strings.xml` — English default set
- `app/src/main/res/values-fr/strings.xml` — French set
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — card text + `depthSourceLabel`

### LocaleApply  [x]
Instant Compose locale override: `CompositionLocalProvider` over `LocalContext` + `LocalConfiguration`,
built from a `ContextWrapper` **around the Activity**. `languageCode` persisted in `SettingsManager`.
"System" resolves to French only on a fr device, else fully English.

#### Rules
- Wrap the Activity in a `ContextWrapper` overriding `getResources()` — never return a detached
  `createConfigurationContext` (it breaks `findActivity()` / `ActivityResultRegistryOwner`, crashing
  `rememberLauncherForActivityResult`).

#### Key Files
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — `rememberLocalizedContext`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `languageCode`

### LanguageSelector  [x]
Settings "Language" section with a 3-segment System / English / Français selector, wired through
`CoastlineViewModel.updateSettings`.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `SettingsLanguageRow`

### BackIconNormalization  [x]
Settings header back control switched from a raw "←" text glyph to
`Icons.AutoMirrored.Filled.ArrowBack`, matching the app's other vector icons.

### GenerationPhaseI18n  [ ]
DEFERRED: generation progress phase text is still French literals in the data/spatial layer. Thread a
`GenerationPhase` enum through `onProgress` (CoastlineGenerator / DepthGenerator / Zone300Builder /
CoastlineRepository) and map it to `stringResource` in the loading overlay. Only shows during first-run
generation.

## Rules
- Default resource set (`values/`) is English; French in `values-fr/`. Android resolves fr devices to
  French, everything else to the English default.
- All user-facing text goes through `stringResource` — no hardcoded UI literals.

## Key Files
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`
- `app/src/main/java/ykws/android/maro/MainActivity.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `DashboardPanel.kt`

## Docs
- — none attached —
