# RegulatedZones — Hydration Snapshot

**Baked:** 2026-06-11 20:59 UTC

**Status:** display-layer subfeature **complete**. Both subfeatures done.

**What was accomplished this session:**
- **Bug found: stale prebaked data** — the `.bin` had only 3 seed zones (2 KB) instead of 75 live SHOM zones (1.9 MB). The prebake had never been successfully run with the updated WFS client.
- **Fix: re-baked** — now 75 SHOM regulation zones from live INSPIRE WFS.
- **Bug found: CRS offset ~5 km north** — `webMercatorToWgs84()` used `EARTH_RADIUS_M = 6,371,000` but EPSG:3857 is defined with WGS84 semi-major axis `6,378,137 m`. The 0.11% error caused ~5 km north shift at 43.5°N.
- **Fix: corrected R value** to `6,378,137` — constant is only used by `ShomRegulationClient.webMercatorToWgs84()`, no impact on other spatial code.
- **Land-clipping fix** — `drawRegulatedZones()` now accepts `waterTest` parameter; zones whose centroid falls on land are skipped.
- **BUILD SUCCESSFUL** — `assembleDebug` passes with zero errors.

**Source files:**
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` — MODIFIED (EARTH_RADIUS_M fix)
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZonesRepository.kt` — NEW (previous session)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — MODIFIED (previous session)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — MODIFIED (waterTest land-clipping, drawRegulatedZones update)
- `data/app-assets/regulated-zones/nice-frejus.bin` — RE-BAKED (75 zones, corrected CRS)

**Next step:** Debug zone-info banner (Issue 3) — show active regulation name/type when boat is inside a zone.
