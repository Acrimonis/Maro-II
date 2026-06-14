# Hydration: RegulatedZones

**Baked:** 2026-06-13 20:23 UTC
**Status:** active
**Active subfeature:** design (15/18 complete), more-dedebug (0/2, newly created)

## State

SpeedZones implementation complete: SpeedZone model, SpeedZoneIndex grid spatial index with exhaustive containment, SpeedLimitCard replacing Zone300Card with 6 render states, heading-ahead cone + green line overlays, generalized zoneAutoShowDecision() with ZoneAutoShowConfig. Performance fixes: pipeline throttled from 150ms→333ms (~3 Hz) to resolve drag stutter; uniform border rule applied (removed pulsing animation, all tiles get solid border matching cardColor).

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — SHORE_SAMPLE_INTERVAL_MS=333L, heading-ahead pipeline
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — SpeedLimitCard, uniform border rule, SpeedCard color-coding
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — cone+line overlay, LaunchedEffect
- `app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt` — grid spatial index
- `app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt` — runtime model
- `app/src/main/java/ykws/android/maro/data/regulation/SpeedZoneBuilder.kt` — builder + zone name filter
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — auto-show toggles

## Next Step

On-device validation of speed zone distance logic. Then address more-dedebug subfeature: decouple cone/green line drawing, color arrow by speed compliance.
