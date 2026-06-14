# Hydration: Dashboard — baked 2026-06-14 16:50 UTC

## State
Dashboard tile status fixes completed. Speed tile confirmed OK. Distance tile: on-land subdued background, negative exit/entry values, SHOM zone priority over 300m band, `nextZoneAhead` removed from `DistanceCard` (uses only `currentZone.beyondType`), `isNearEntry` threshold-gated by `autoRevealDistanceM`/`autoRevealTimeS`. Zone tile thresholds aligned via shared `autoRevealDistanceM` (100m) / `autoRevealTimeS` (10s).

### Changes
- [`DashboardPanel.kt`](app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt): DistanceCard — on-land subdued bg, negative exit/entry values, removed `nextZoneAhead` branch (shows `currentZone.beyondType` instead), `isNearEntry` re-gated with threshold
- [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt): `currentZone` priority: SHOM zone first > 300m band fallback. `zonesAroundBoat()` steps 1 & 2 now respect `excludeZoneName`. `_zoneSituation` update guarded by `shore.distanceMeters != null`
- [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt): Prefs migration v2 — clears stale `zoneAutoRevealDistanceM`/`zoneAutoRevealTimeS` keys
- [`ZoneConfig.kt`](app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt): Defaults changed from 200m/20s → 100m/10s
- [`zone.properties`](app/src/main/assets/zone.properties): Updated docs

### Target Files
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — DistanceCard + SpeedLimitCard
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — zoneSituation pipeline + zonesAroundBoat
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — prefs migration
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — defaults

### Remaining Dashboard Todos
- readability subfeature (padding/weight/string updates)
