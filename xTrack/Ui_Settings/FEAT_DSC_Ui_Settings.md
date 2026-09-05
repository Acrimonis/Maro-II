---
name: Ui_Settings
status: active
created: 2026-06-09 15:28
modified: 2026-09-05 10:42
---

**Description:** Settings page UI, settings persistence (SharedPreferences), settings-related widgets, and settings UX enhancements.

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

- **opacity-normalization (2026-09-05)** — standardized all opacity/transparency settings on OPACITY (higher = more visible); tracks converted transparency→opacity with v8 migration; marker halo + zone300 relabeled to "Opacity"/"Fill·Border"; guidelines updated → `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md`
- **settings-reorganization** — 4 tabs (Layers/Navigation/Position/System); 6 layer toggles + zone-shapes toggles removed; 8 expander flags → rememberSaveable; localized; dead-code sweep
- **approach-redisplay** — re-display on approach (2 mode switches + 3 type switches + 2 sliders); per-zone proximity render; prefs migration v5→6
- **reorder-settings** — Display→General rename; POSITION SOURCE / GPS freq / Recenter / FPS → System; Navigation tab slimmed
- **scroll persistence** — settings `ScrollState` hoisted outside `SettingsOverlay` (session-only)
- **fix-status-persistance** — `selectedTab` hoisted to MapScreen with `rememberSaveable`; pager–tab sync race fixed
- **tab organization** — Material 3 TabRow + HorizontalPager (3 tabs); per-tab scroll states; custom blue indicator
- **track-drawer-settings-btn** — Settings gear in Track Drawer header (64dp), drawer padding trimmed, redundant map Settings button removed

## Rules
- **Collapsible grouped-card pattern**: conditional sub-settings wrapped with the toggle in a single `Column` (12dp radius, `0x1AFFFFFF` bg), thin 0.5dp white divider (10% alpha), `SettingsExpander` inside `if (condition)`; expander label matches toggle style (`White/16.sp/Medium`).

## Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Docs
- `xTrack/Ui_Settings/260905_FEAT_PLN_Ui_Settings_opacity-normalization.md` — opacity/transparency nomenclature normalization (implemented)
- `xTrack/Ui_Settings/260625_FEAT_PLN_Ui_Settings_render-tweaks.md` — card rendering tweaks discussion
- `xTrack/Ui_Settings/260609_FEAT_PLN_Ui_Settings_apply-on-close.md` — settings apply-on-close UX design
