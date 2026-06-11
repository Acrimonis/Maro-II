---
name: Ui_Settings
status: active
created: 2026-06-09 15:28
modified: 2026-06-10 14:57
active_subfeature: none
---

**Description:** Settings page UI, settings persistence (SharedPreferences), settings-related widgets, and settings UX enhancements.

## Subfeatures

### scroll persistence  [x]

#### Todos
- [x] Hoist the settings scroll state (`ScrollState`) outside the `SettingsOverlay` composable so it survives overlay dismiss/reopen within one session

#### Rules
- Scroll position persistence is session-only (in-memory); on app restart, scroll goes back to top — no SharedPreferences needed
- Must not interfere with existing `rememberScrollState()` behavior for other scrollable areas

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

## Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Docs
- `plans/settings-apply-on-close.md` — settings apply-on-close UX design
- `plans/settings-scroll-persistence.md` — discussion: hoist `ScrollState` to survive overlay dismiss/reopen within a session
- `plans/settings-scroll-persistence-analysis.md` — scroll persistence analysis
- `plans/settings-tab-organization.md` — discussion: organize settings into tabs/sections
