# Context Hydration — MapDisplay

**Last Bake:** 2026-06-08 12:32
**Feature Status:** active (3/4 subfeatures done)
**Active Subfeature:** zone proximity auto-reveal (done ✅)

## State Summary
The 300 m band proximity auto-reveal was fully reworked this session for functional GPS use and shipped (build + unit suite green; debug APK assembles). Old single-shot / fixed-400 m heuristic removed (`ZONE_AUTO_REVEAL_M` gone). Logic lives in a pure, unit-tested `zone300Decision()` driven by the 150 ms shore pipeline. **Reveal** while a manually-hidden band is OUTSIDE (dist > 0) and closing, within 200 m of the band edge OR 20 s away at SOG (hybrid). **Auto-hide** on any of: stopped & not closing (≤ 1 kn), compliant inside (≤ 5 kn), exited seaward, retreated past margin; `armed` persists through an auto-hide (re-approach re-reveals), manual toggle disarms. **Demo** uses pan-derived speed (paused = 0 kn inside / unknown outside); GPS uses real SOG; logic shared (no gpsMode branch). Thresholds (200 m / 20 s) tunable in `zone.properties` + Settings → Avancé; reg (5 kn) / stop (1 kn) in `ZoneConfig`.

## Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — shore pipeline + `zone300Decision()` + flags
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Settings → Avancé sliders
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` / `data/settings/SettingsManager.kt` / `assets/zone.properties` — thresholds
- `app/src/test/java/ykws/android/maro/ui/map/Zone300DecisionTest.kt` — unit tests

## Next Steps
- Subfeature "depth color" still pending: align DepthCard color with DepthColorRamp palette.
- Optional (flagged, non-blocking): deadband/cooldown for the anchored-within-margin GPS-jitter flap; regulatory speed as a 3rd Advanced slider; persist `armed` across restarts; on-device GPS validation.
