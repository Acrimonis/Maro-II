# Hydration — MapDisplay

**Baked:** 2026-06-07

**Status:** 2/3 subfeatures complete. `layer refresh` and `zonetile` fully delivered. `depth color` has its core work done (DepthCard background aligned with DepthColorRamp palette, depth→ARGB mapped) but subfeature not yet checked off.

**Completed this session:**
- Implemented Zone300Card distance+speed color rules in `DashboardPanel.kt`
- Extracted 3 gradient tunables to `zone.properties` (gradientText=600m, gradientColor=300m, gradientTransp=33%)
- Created `ZoneConfig.kt` as runtime loader with graceful fallback defaults
- Wired `ZoneConfig.init(this)` in `MainActivity.kt`

**Key files involved:**
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — Zone300Card speed-aware color `when` block + gradient lerp
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — properties loader
- `app/src/main/assets/zone.properties` — gradient tunables

**Next:** Confirm `depth color` subfeature status — the two todos are done but the checkbox is still `[ ]`.
