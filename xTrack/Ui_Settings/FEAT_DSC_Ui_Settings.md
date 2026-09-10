---
name: Ui_Settings
status: active
created: 2026-06-09 15:28
modified: 2026-09-10 10:45
---

**Description:** Settings page UI, settings persistence (SharedPreferences), settings-related widgets, and settings UX enhancements.

> **UI rules:** All settings UI rendering rules (cards, nested surfaces, dividers, headers, sliders, segmented
> selectors, anti-patterns) live in [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) —
> this feature file defers to it and does not duplicate those rules. Colour tokens: [`docs/color-scheme.md`](../../docs/color-scheme.md) §7.

## Implemented

- **C12 OverlayLayer param collapse — Tiers 1a-i + 1a-ii (2026-09-10, `feature/refact-C12`)** — first two slices of the MapScreen step-2 refactor's C12. New same-package `OverlayLayerParams.kt` holds two public `@Immutable` data classes: `OverlayChrome` (7 — the drawer/wizard visibility flags plus `wizardStep` and `drawerState`) and `MenuOverlayData` (12 — GPS mode, menu toggles, first-track/marker ids, track/marker map-referential filters and counts). `OverlayLayer` dropped **89 → 72 params**, removing the read-only params from three separate signature regions while leaving the 4 interleaved callbacks in place, and unpacks both bundles into 19 same-named locals at the top of the body so the body and every child call are unchanged; the single call site in `MapScreen.kt` builds both bundles inline (plain values, never `remember`-ed). Public visibility is required — a public `OverlayLayer` cannot expose an `internal` type. `apk-build.bat` SUCCESS with zero new warnings on both tiers; Ask reviews 8/8 and 8/8, with the call-site expressions confirmed not simplified (`trackMapCount = trackMapVisibleCount`, `markerMapCount = mapMarkersState.size`). The R10 recomposition metric is waived in writing for both tiers (plan R10 entry). Uncommitted at review time. Remaining C12: Tier 1b `SettingsOverlayData` (5) + `TrackInfoOverlayData` (4), Tier 1c `TrackListOverlayData` (3) + `MarkerListOverlayData` (4) plus the 2 dead `rememberLazyListState` defaults, then the citation-drift pass → `xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md` (C12 APPENDIX)

- **finalise-feature (2026-09-07)** — closed all remaining open sections. `render-tweaks` closed as already-implemented (its June 25 proposals — `ui.padding.card.vertical=8dp` + card background standardization to `uiCardBackground` — were delivered by the card-expander-nestedcard-refactor + properties-normalization); `header-normalization` closed as implemented & merged (PR #217, shared `DrawerHeader`); `settings apply on close` closed as out-of-scope/not-needed (settings keep firing immediately). Guideline docs (ui-component-guidelines + ui-drawer-guidelines) verified in sync with code — no drift found.
- **properties-normalization (2026-09-07)** — renamed `ui-tokens.properties` → `ui.properties`, wired into `AppConfig.init()` runtime loading (after maro, before colors so colors win) with typed UI-token accessors (spacing/padding/radius/font/touch/divider/nested-card colours); re-homed 6 misplaced keys across colors/ui/maro (colors→ui `ui.dashboard.dullAlpha`; colors→maro coastline+isobar stroke widths; maro→ui `ui.landscape.panel.widthScale`); replaced hardcoded `.dp`/`.sp` in settings composables (shared widgets + 4 page composables) with the loaded tokens; docs aligned (ui-component-guidelines §3/§2.5, color-scheme §7/§9). Deviations: `overlay.lowDepth.minOpacity` absent (nothing re-homed); `ui.font.toggle.size` aligned 14sp→16sp (code-wins). Ask review: build green, non-blocking follow-ups (inline 14sp slider labels, `BoatSizeSlider`, 8 dead tokens) → `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_properties-normalization.md`
- **drawer-content-measurement (2026-09-06)** — marker detail drawer (portrait) now WRAPS its content at natural height (no scroll): added opt-in `wrapContent` mode to `DrawerScaffold` (visible panel collapses to content; body scrolls only past screen height); marker Viewing slot split from Where-Am-I and converted to wrap-content; deleted the fragile `MeasureHeight` probe + fixed-height formula + `onCardHeightMeasured` plumbing; belongs-to-track color → white; track drawer probe/formula normalized + `suppressOverscrollWhenFits=true` (track kept its probe) → `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_drawer-content-measurement.md`
- **marker-belongs-to-track (2026-09-06)** — marker card bottom row shows owning track name + chevron that opens the owning track's detail drawer, in both the marker list card and the marker detail drawer (shared `MarkerCardContent`; drawer's own "Belongs to track" row deleted to avoid double render) → `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_marker-belongs-to-track.md`
- **card-expander-nestedcard-refactor (2026-09-06)** — introduced structural composables `Card` (20% white, 12dp radius), `Expander` (collapsible disclosure row), `NestedCard` (5% white + border container on expand), `SectionDivider`; migrated all ~12 expander sites + Main cards to the new model; deprecated/removed `SettingsSliderGroup`/`SettingsSliderRow`; Screen section's 3 standalone controls grouped into one Card with 3 sections → `xTrack/Ui_Settings/260906_FEAT_PLN_Ui_Settings_card-expander-nestedcard-refactor.md`
- **guidelines-consolidation (2026-09-06)** — consolidated ui-component/drawer/lists guideline docs: card-surface primitive + popup-styling canonical in component-guidelines, drawer §11 Decision Log deleted, "Migration Guide" + pre-DrawerScaffold skeleton removed, lists visual-token tables deduped → `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_guidelines-consolidation.md`
- **SectionDivider normalization** — renamed `SliderRowDivider` → `SectionDivider`; `uiSettingsDivider` spacing normalized to 6/6/16dp
- **GPS frequency/recenter** — shared `NestedCard` for GPS frequency + recenter controls; always-visible expander
- **tab-navigation-swipe-spacing** — disabled swipe between tabs (userScrollEnabled=false), relocated 24dp horizontal padding per-page so the slide shows a gap while settled layout stays identical → `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_tab-navigation-swipe-spacing.md`
- **transparency-normalization + low-depth redesign (2026-09-06)** — normalized all opacity/transparency settings to the **TRANSPARENCY** paradigm (0 = opaque, 100 = invisible); tracks/marker-halo/zone300 controls relabeled "Transparency" with two-thumb value format "Border X% · Fill Y%" (border = strong/low transparency left thumb, fill = faint/high transparency right thumb); low-depth warning redesigned from a single min-opacity to a **two-depth crash/start model** (solid to crash depth, linear fade to start-warning depth); ui-*guidelines + color-scheme docs aligned → supersedes `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md` (which proposed the rejected OPACITY option)
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
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_header-normalization.md` — header normalization (implemented, PR #217)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_properties-normalization.md` — properties normalization (implemented)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_tab-navigation-swipe-spacing.md` — disable swipe between tabs + per-page slide spacing (implemented)
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md` — opacity/transparency nomenclature normalization (superseded by transparency paradigm)
- `xTrack/Ui_Settings/260625_FEAT_PLN_Ui_Settings_render-tweaks.md` — card rendering tweaks discussion
- `xTrack/Ui_Settings/260609_FEAT_PLN_Ui_Settings_apply-on-close.md` — settings apply-on-close UX design
