# Context Hydration — AppBakFlow — 2026-06-08

**Active Subfeature:** none

## State
Both subfeatures implemented and compiling (`assembleDebug` green). **BackToExitConfirm:**
a `BackHandler(enabled = !showSettings)` in `MapScreen` shows a 2-second "Press back again
to exit" toast on first back; a second back within 2 s calls `findActivity()?.finishAffinity()`.
The toast is a Material `Surface` (rounded 28dp, 8dp shadow, white 16sp text) rendered inside
`MapContent`'s bottom-overlay slot — bottom-centre, padded `end = 76dp` to clear the right-edge
zoom/control stack — colour `0xFF16213E` (sampled from the dashboard tile background / `cardBg`).
**KeepScreenOn:** `keepScreenOn` persisted in `SettingsManager` (SharedPreferences, mirrors
`languageCode`); a "Keep phone on" `SettingsToggleRow` at the bottom of the Display settings
section; a `DisposableEffect` driving `LocalView.keepScreenOn`. New strings added in EN + FR.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — back handler, toast, keep-screen-on effect, settings row
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `keepScreenOn` pref
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — new strings

## Next Step
On-device verification (NOT yet deployed — user controls deploy): 2-second back-to-exit timing
+ toast appearance vs. dashboard tiles, and keep-screen-on holding the screen awake + persisting
across restart. Then open a PR from `feature/app-bak-flow`.
