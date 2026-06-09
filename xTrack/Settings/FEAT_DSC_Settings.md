---
name: Settings
status: active
created: 2026-06-09 15:28
modified: 2026-06-09 19:42
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

## Todos

## Rules

## Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Docs
- `plans/settings-scroll-persistence.md` — discussion: hoist `ScrollState` to survive overlay dismiss/reopen within a session
