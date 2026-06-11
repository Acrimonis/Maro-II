# Settings — Hydration Snapshot

**Baked:** 2026-06-10 14:57 UTC

## State

Parent feature active; no subfeature focused.

## Completed

- **tab organization [x]** — Settings page split into 3 Material 3 tabs (Display, Navigation, System) with HorizontalPager. Replaced TabRow (purple indicator) with custom Row + drawBehind blue indicator. Z300 alert moved from System to Navigation tab. Regenerate button right-aligned. 3 hoisted ScrollStates for per-tab scroll persistence.

## Next Steps

- `settings apply on close [ ]` — defer side effects until overlay dismiss

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
