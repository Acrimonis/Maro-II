# Hydration — Localisation

**Last Bake:** 2026-06-08 15:43

## State
App fully localised (English default + French); built, installed, and on-device verified (Pixel 7);
committed + pushed to `feature/localisation` (commits c7b6da0, 089e8aa). ~70 user-facing strings moved
to `stringResource` — English is the default `values/strings.xml`, French in `values-fr/strings.xml`.
Language is applied instantly via a Compose `CompositionLocalProvider` over `LocalContext` /
`LocalConfiguration`, built from a `ContextWrapper` around the Activity (an earlier detached
`createConfigurationContext` crashed the GPS permission launcher — "No ActivityResultRegistryOwner").
`SettingsManager.languageCode` ("system" | "en" | "fr") persists the choice; Settings has a
System / English / Français selector. "System" resolves to French only on a fr device, else fully
English (text + number formatting). Settings back button normalised to
`Icons.AutoMirrored.Filled.ArrowBack`. All 5 subfeatures except `GenerationPhaseI18n` are done.

## Next
- Open subfeature `GenerationPhaseI18n`: thread a `GenerationPhase` enum through `onProgress`
  (CoastlineGenerator / DepthGenerator / Zone300Builder / CoastlineRepository) → map to `stringResource`
  in the loading overlay. First-run generation progress text is the only remaining French.
- Optional: open a PR for `feature/localisation`.

## Key files
`MainActivity.kt` (rememberLocalizedContext), `SettingsManager.kt` (languageCode), `MapScreen.kt`
(SettingsLanguageRow + back icon), `DashboardPanel.kt`, `res/values/strings.xml`,
`res/values-fr/strings.xml`.
