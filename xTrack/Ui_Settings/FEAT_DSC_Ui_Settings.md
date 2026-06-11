---
name: Ui_Settings
status: active
created: 2026-06-09 15:28
modified: 2026-06-11 17:00
active_subfeature: none
---

**Description:** Settings page UI, settings persistence (SharedPreferences), settings-related widgets, and settings UX enhancements.

## Subfeatures

### reorder-settings  [x]

#### Todos
- [x] Rename tab "Display" → "General" in `settingsTabLabels`
- [x] Add "Layers" sub-section under DISPLAY in General tab
- [x] Move Heading line + Cap arrow — keep in General tab, remove from Navigation tab
- [x] Move EMODnet shallow filter → System tab, promoted to `SectionHeader`
- [x] Move Position source / GPS mode → System tab, below Language
- [x] Reorder System tab: Language → Position source (+ GPS-conditional freq/recenter) → Screen (+ FPS) → EMODnet → Regenerate layers
- [x] Rename System "Power saving" section header to "Screen" — added `settings_section_screen` string resource (en/fr)
- [x] Move Recenter delay → System, under POSITION SOURCE, GPS-conditional (`if (settings.gpsMode)`)
- [x] Move GPS acquisition frequency → System, under POSITION SOURCE, GPS-conditional
- [x] Move Map rendering FPS → System, in SCREEN section below Keep screen on
- [x] Strip Recenter/GPS freq/FPS from NavigationSettings — keep only Idle saving + Z300 alert
- [x] Update `HorizontalPager` dispatch
- [x] Verify nothing breaks — BUILD SUCCESSFUL

#### Rules
- All existing settings widgets remain unchanged — only tab placement changes
- Per-tab scroll states (`displayScrollState`, `navigationScrollState`, `systemScrollState`) must be re-checked to ensure they correctly map to the new content

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`


### scroll persistence  [x]

#### Todos
- [x] Hoist the settings scroll state (`ScrollState`) outside the `SettingsOverlay` composable so it survives overlay dismiss/reopen within one session

#### Rules
- Scroll position persistence is session-only (in-memory); on app restart, scroll goes back to top — no SharedPreferences needed
- Must not interfere with existing `rememberScrollState()` behavior for other scrollable areas

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

### fix-status-persistance  [x]

#### Todos
- [x] Hoist `selectedTab` to `MapScreen` composable level with `rememberSaveable` so it survives overlay dismissal/recomposition
- [x] Fix bidirectional pager–tab sync race condition: initial `pagerState.currentPage = 0` was overwriting the restored `selectedTab` before `animateScrollToPage` could run
- [x] Ensure per-tab scroll position survives overlay toggle (already at `MapScreen` level — verified correct)

#### Rules
- `selectedTab` must be hoisted to `MapScreen` composable level with `rememberSaveable` so it survives overlay dismissal/recomposition
- Scroll states (`displayScrollState`, `navigationScrollState`, `systemScrollState`) are already at `MapScreen` level — verify they correctly persist across overlay toggle

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

### settings apply on close  [ ]

#### Todos
- [ ] Defer side effects of settings changes until the settings overlay is dismissed — batch-apply on close instead of firing on each toggle/slider change

#### Rules
- Individual settings widgets (toggles, sliders) should update local UI state immediately for responsiveness, but side effects (regeneration, GPS restart, etc.) must fire only on dismiss
- Regenerate rasters button is excluded — it already triggers explicitly, not on close
- Exceptions: `gpsMode` toggle may need immediate effect (GPS start/stop is safety-critical); `languageCode` change may need immediate effect for proper string rendering in the settings page itself

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`

### tab organization  [x]

#### Todos
- [x] Replace single scrolling settings page with Material 3 TabRow + HorizontalPager (3 tabs: Display, Navigation, System)
- [x] Extract DisplaySettings composable (coastline, Z300, low-depth warning, heading line, cap arrow, EMODnet cutoff)
- [x] Extract NavigationSettings composable (GPS mode, recenter delay, GPS frequency, FPS, idle/adaptive, Z300 alert)
- [x] Extract SystemSettings composable (language, keep screen on, regenerate)
- [x] Move Z300 auto-alert from System tab to Navigation tab under "300M ZONE ALERT" section header
- [x] Hoist 3 per-tab ScrollStates to MapScreen level for session scroll persistence
- [x] Replace TabRow default indicator (purple) with custom blue (0xFF1565C0) drawBehind indicator
- [x] Right-align Regenerate button in System tab

#### Rules
- Tab selection persisted via rememberSaveable — survives overlay dismiss/reopen + config change
- Per-tab scroll states are session-only (resets on app restart)
- SettingsManager/SharedPreferences persistence is untouched

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Todos

## Rules
- **Collapsible grouped-card pattern**: When a setting toggle has conditional sub-settings that should only appear when the toggle is ON, wrap both the toggle and the sub-settings in a single `Column` with `.clip(RoundedCornerShape(12.dp)).background(0x1AFFFFFF)` card background. Use a thin `0.5.dp` white divider (10% alpha) between the toggle and the expander. The sub-settings go inside a `SettingsExpander` with the `if (condition)` gate on the outer card. The expander label style should match the toggle label style (`White/16.sp/Medium`). Key files: `SystemSettings` in `MapScreen.kt` (POSITION SOURCE section).

## Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Docs
- `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_apply-on-close.md` — settings apply-on-close UX design
- `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_scroll-persistence.md` — discussion: hoist `ScrollState` to survive overlay dismiss/reopen within a session
- `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_scroll-persistence-analysis.md` — scroll persistence analysis
- `xTrack/Ui_Settings/FEAT_PLN_Ui_Settings_tab-organization.md` — discussion: organize settings into tabs/sections
