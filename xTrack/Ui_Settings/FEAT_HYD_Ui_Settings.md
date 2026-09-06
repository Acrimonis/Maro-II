# Ui_Settings — Hydration (2026-09-06 09:59)

## State
Settings page UI, persistence (SharedPreferences), settings widgets, and UX. Latest work (2026-09-06, settings-misc branch): a **Card/Expander/NestedCard structural refactor** normalized the settings surface model. New composables: `Card` (20% white `uiCardBackground`, 12dp radius — the section surface), `Expander` (collapsible disclosure row, no box of its own), `NestedCard` (5% white `0x0DFFFFFF` + `0x40FFFFFF` border container revealed on expand, holds any controls), and `SectionDivider` (renamed from `SliderRowDivider`, spacing normalized to 6/6/16dp). All ~12 expander sites plus Main cards migrated to the model; `SettingsSliderGroup`/`SettingsSliderRow` deprecated and removed; the Screen section's 3 standalone controls grouped into one Card with 3 sections. GPS frequency/recenter now share a `NestedCard` with an always-visible expander. Concurrently, the component/drawer/lists guideline docs were consolidated (card-surface primitive + popup-styling canonical in component-guidelines, drawer §11 Decision Log deleted, lists deduped). Header-normalization and properties-normalization plans remain pending (not implemented).

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `Card`/`Expander`/`NestedCard`/`SectionDivider` composables + migrated expander/Main-card sites
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt`
- `app/src/main/assets/colors.properties` — `uiSettingsDivider` spacing tokens
- `docs/ui-component-guidelines.md`, `docs/ui-drawer-guidelines.md`, `docs/ui-lists-guidelines.md`, `docs/color-scheme.md`

## Next Step
Open the pending `header-normalization` and `properties-normalization` plans (both 260905, not yet implemented), then the `render-tweaks` todo and deferred `settings apply on close` section. BUILD SUCCESSFUL.
