---
name: ui-small-potatoes
status: active
created: 2026-07-06 13:43
modified: 2026-07-06 13:56
active_subfeature: translation
---

# Feature: ui-small-potatoes

**Description:**
General UI polish, refinements, and small fixes across the app. Catch-all for
cross-cutting UI improvements that don't warrant a dedicated feature.

## Subfeatures

### translation  [x]

Do a pass through the codebase: identify all hardcoded user-facing strings,
extract them to `strings.xml`, and translate into corresponding locales (en + fr).
Both locales must be complete — English as baseline, French as translation.

#### Implemented
- 84 EN string resources added to `values/strings.xml` (menu_, track_, marker_, filter_, action_, cd_, settings_, snackbar_, color_picker_ groups)
- 84 FR translations added to `values-fr/strings.xml`
- 14 source files updated: `MenuDrawerOverlay`, `MarkerDrawer`, `IconPickerDialog`, `MarkerManagementOverlay`, `ListOverlayScaffold`, `MapScreen` (incl. Settings Navigation/System tabs), `WizardTopBar`, `DrawerScaffold`, `TypeSelectStep`, `TrackHistoryOverlay`, `DashboardPanel`
- BUILD SUCCESSFUL (×2)

#### Plan
- `xTrack/ui-small-potatoes/FEAT_PLN_ui-small-potatoes_translation-survey.md`

#### Key Files
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/java/ykws/android/maro/MainActivity.kt` (rememberLocalizedContext)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` (languageCode)

## Docs
- `docs/SETUP.md`
