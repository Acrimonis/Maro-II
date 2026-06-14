# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 23:49 UTC+2
**State:** Seeds removed, boat size filter, category toggles, collapsible settings, red dot for speed info.

## Session Summary

Implemented Round 2 of regulated zones: boat size slider (3–25m, default 6m), 9 per-category visibility toggles with persisted collapsible state, Regulation info toggle (off by default), red dot 🔴 for speed info text, coastline moved to bottom of Layers section, `appliesTo()` "between X and Y" fix, and removed seed zones.

## Current State

- **SHOM INSPIRE:** 110 zones fetched
- **IGN Natura 2000:** 16 zones fetched, 12 survived dedup
- **SEED:** 0 zones (removed)
- **Total after aggregate:** 121 zones
- **APK:** Built and deployed to device

## Target Files Modified

- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` — `appliesTo()` range fix
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationSeeds.kt` — emptied
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — +11 fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — filter pipeline, settings UI, red dot
- `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt` — seeds removed

## Next Steps

- `trouble-shoot-reg-layers` subfeature
- `reg-zones-filtering` subfeature
- `add-zone-text` subfeature
- Translation of settings labels (en/fr)
