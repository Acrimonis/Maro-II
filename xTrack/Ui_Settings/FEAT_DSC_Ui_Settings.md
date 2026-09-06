---
name: Ui_Settings
status: active
created: 2026-06-09 15:28
modified: 2026-09-06 11:25
---

**Description:** Settings page UI, settings persistence (SharedPreferences), settings-related widgets, and settings UX enhancements.

> **UI rules:** All settings UI rendering rules (cards, nested surfaces, dividers, headers, sliders, segmented
> selectors, anti-patterns) live in [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) —
> this feature file defers to it and does not duplicate those rules. Colour tokens: [`docs/color-scheme.md`](../../docs/color-scheme.md) §7.

## Sections

### render-tweaks

#### Todos
- [ ] Tweak card rendering in settings overlays per ui-component-guidelines.md

#### Rules
- Follow canonical patterns in `docs/ui-component-guidelines.md` — grouped cards (§2.3), nested surfaces (§2.4), divider spacing (§2.6)
- Card background: `uiCardBackground`, 12dp radius
- Nested card surface: `ui.nested.card.bg` / `ui.nested.card.border`
- No anti-patterns (§4)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

### header-normalization

#### Todos
- [ ] Migrate the Settings header to the shared `DrawerHeader` composable (32dp back, 17sp title) — plan: `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_header-normalization.md`

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (SettingsOverlay header)
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` (DrawerHeader)

### properties-normalization

#### Todos
- [ ] Rename `ui-tokens.properties` → `ui.properties`, wire into AppConfig loading, re-home misplaced keys (colors/ui/maro separation) — plan: `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_properties-normalization.md`

#### Key Files
- `app/src/main/assets/ui-tokens.properties` / `colors.properties` / `maro.properties`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`

### settings apply on close

#### Todos
- [ ] Defer side effects of settings changes until the settings overlay is dismissed — batch-apply on close instead of firing on each toggle/slider change

#### Rules
- Individual settings widgets update local UI state immediately, but side effects (regeneration, GPS restart, etc.) fire only on dismiss
- Regenerate rasters button excluded — it already triggers explicitly, not on close
- Exceptions: `gpsMode` toggle may need immediate effect (GPS start/stop is safety-critical); `languageCode` may need immediate effect for string rendering

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`

## Implemented

- **drawer-content-measurement (2026-09-06)** — marker detail drawer (portrait) now WRAPS its content at natural height (no scroll): added opt-in `wrapContent` mode to `DrawerScaffold` (visible panel collapses to content; body scrolls only past screen height); marker Viewing slot split from Where-Am-I and converted to wrap-content; deleted the fragile `MeasureHeight` probe + fixed-height formula + `onCardHeightMeasured` plumbing; belongs-to-track color → white; track drawer probe/formula normalized + `suppressOverscrollWhenFits=true` (track kept its probe) → `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_drawer-content-measurement.md`
- **marker-belongs-to-track (2026-09-06)** — marker card bottom row shows owning track name + chevron that opens the owning track's detail drawer, in both the marker list card and the marker detail drawer (shared `MarkerCardContent`; drawer's own "Belongs to track" row deleted to avoid double render) → `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_marker-belongs-to-track.md`
- **card-expander-nestedcard-refactor (2026-09-06)** — introduced structural composables `Card` (20% white, 12dp radius), `Expander` (collapsible disclosure row), `NestedCard` (5% white + border container on expand), `SectionDivider`; migrated all ~12 expander sites + Main cards to the new model; deprecated/removed `SettingsSliderGroup`/`SettingsSliderRow`; Screen section's 3 standalone controls grouped into one Card with 3 sections → `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_card-expander-nestedcard-refactor.md`
- **guidelines-consolidation (2026-09-06)** — consolidated ui-component/drawer/lists guideline docs: card-surface primitive + popup-styling canonical in component-guidelines, drawer §11 Decision Log deleted, "Migration Guide" + pre-DrawerScaffold skeleton removed, lists visual-token tables deduped → `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_guidelines-consolidation.md`
- **SectionDivider normalization** — renamed `SliderRowDivider` → `SectionDivider`; `uiSettingsDivider` spacing normalized to 6/6/16dp
- **GPS frequency/recenter** — shared `NestedCard` for GPS frequency + recenter controls; always-visible expander
- **tab-navigation-swipe-spacing** — disabled swipe between tabs (userScrollEnabled=false), relocated 24dp horizontal padding per-page so the slide shows a gap while settled layout stays identical → `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_tab-navigation-swipe-spacing.md`
- **opacity-normalization (2026-09-05)** — standardized all opacity/transparency settings on OPACITY (higher = more visible); tracks converted transparency→opacity with v8 migration; marker halo + zone300 relabeled to "Opacity"/"Fill·Border"; guidelines updated → `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md`
- **settings-reorganization** — 4 tabs (Layers/Navigation/Position/System); 6 layer toggles + zone-shapes toggles removed; expander open state moved to `SettingsViewModel.expanderStates` map; localized; dead-code sweep
- **approach-redisplay** — re-display on approach (2 mode switches + 3 type switches + 2 sliders); per-zone proximity render; prefs migration v5→6
- **reorder-settings** — Display→General rename; POSITION SOURCE / GPS freq / Recenter / FPS → System; Navigation tab slimmed
- **scroll persistence** — settings `ScrollState` hoisted outside `SettingsOverlay` (session-only)
- **fix-status-persistance** — `selectedTab` hoisted to MapScreen with `rememberSaveable`; pager–tab sync race fixed
- **tab organization** — Material 3 TabRow + HorizontalPager (3 tabs); per-tab scroll states; custom blue indicator
- **track-drawer-settings-btn** — Settings gear in Track Drawer header (64dp), drawer padding trimmed, redundant map Settings button removed

## Rules
- **Defer to [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md)** — the canonical source for all settings UI patterns (grouped cards §2.3, nested surfaces §2.4, dividers §2.6, headers §2.9, anti-patterns §4). No UI rules are duplicated here.

## Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Docs
- `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_drawer-content-measurement.md` — marker/track drawer content-fit normalization (implemented)
- `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_card-expander-nestedcard-refactor.md` — Card/Expander/NestedCard structural refactor (implemented)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_guidelines-consolidation.md` — component/drawer/lists guideline consolidation (implemented)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_header-normalization.md` — header normalization (pending)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_properties-normalization.md` — properties normalization (pending)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_tab-navigation-swipe-spacing.md` — disable swipe between tabs + per-page slide spacing (implemented)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md` — opacity/transparency nomenclature normalization (implemented)
- `xTrack/Ui_Settings/260625_FEAT_PLN_Ui_Settings_render-tweaks.md` — card rendering tweaks discussion
- `xTrack/Ui_Settings/260609_FEAT_PLN_Ui_Settings_apply-on-close.md` — settings apply-on-close UX design
