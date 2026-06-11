# RegulatedZones — Hydration Snapshot

**Baked:** 2026-06-11 21:08 UTC

**Status:** display-layer subfeature **complete**. New subfeature `filtering-zone-data` created.

**What was accomplished this session:**
- **Overlay reorder** — regulated zones now drawn above the 300 m band (between zone300 and coastline in z-order).
- **Point-in-polygon detection** — `pointInPolygon()` ray-casting algorithm added; boat position (GPS fix or demo center) tested against all zone polygons on each update.
- **Regulation info banner** — `RegulationInfoBanner` composable at the bottom of the map, dark semi-transparent background, colour-coded per zone type, showing zone name + type label + speed limit + vessel size restriction + description.
- **`filtering-zone-data` subfeature created** — placeholder for vessel-size filtering, per-type visibility toggles, and zone tap interaction.
- **BUILD SUCCESSFUL** — `assembleDebug` passes with zero errors.

**Source files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — MODIFIED (overlay reorder, pointInPolygon, RegulationInfoBanner, activeRegulations wiring)

**Next step:** `filtering-zone-data` — apply vessel-size filter, per-type toggles, zone interaction.
