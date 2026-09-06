# Ui_Settings — Hydration (2026-09-06 21:13)

## State
Settings page UI, persistence (SharedPreferences), settings widgets, and UX. Latest work (2026-09-06): the **global opacity→transparency normalization + low-depth 2-depth warning redesign** was implemented (commit a9c1889 on feature/settings-misc). All opacity/transparency settings now use **TRANSPARENCY** semantics (0 = opaque, 100 = invisible): tracks, marker halo, and 300 m band controls are relabeled "Transparency" with two-thumb value format "Border X% · Fill Y%" (border = strong/low transparency = left thumb; fill = faint/high transparency = right thumb). The low-depth warning was redesigned from a single min-opacity to a **two-depth crash/start model** — solid from the surface down to the crash depth, then a linear alpha fade to the start-warning depth (crash/start sliders, min/max 0 m / 5 m shown). The earlier opacity-normalization plan (which proposed the rejected OPACITY option) is SUPERSEDED. ui-component-guidelines, color-scheme, and ui-lists guidelines docs were aligned to the transparency paradigm. Prior session work (Card/Expander/NestedCard refactor, guideline consolidation, marker "Belongs to track") remains implemented. Header-normalization and properties-normalization plans remain pending (not implemented).

## Target Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — transparency fields (`*TransparencyPct`, `trackingTransparency*`), low-depth crash/start depth fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — transparency sliders (tracks, marker halo, zone300), low-depth crash/start slider
- `app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt` — two-depth alpha ramp (crash → start)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerHalo.kt` / `MapOverlayRenderer.kt` — transparency % → alpha
- `docs/ui-component-guidelines.md`, `docs/color-scheme.md`, `docs/ui-lists-guidelines.md` — transparency convention + low-depth model

## Next Step
Open the pending `header-normalization` and `properties-normalization` plans (both 260905, not yet implemented), then the `render-tweaks` todo and deferred `settings apply on close` section. BUILD SUCCESSFUL.
