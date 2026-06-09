# Settings — Hydration Snapshot

**Baked:** 2026-06-09 19:42 UTC

## State

Parent feature active; no subfeature focused.

## Completed

- **scroll persistence [x]** — Settings scroll position now survives overlay dismiss/reopen within one session. Uses explicit save (snapshotFlow → MutableState at MapScreen level) + layout-aware restore (wait for maxValue > 0 via snapshotFlow.first() before scrollTo). File: `MapScreen.kt:1175-1197`.

## Next Steps

- `settings apply on close [ ]` — defer side effects until overlay dismiss

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
