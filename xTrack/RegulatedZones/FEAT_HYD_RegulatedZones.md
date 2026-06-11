# RegulatedZones — Hydration Snapshot

**Baked:** 2026-06-11 20:35 UTC

**Status:** display-layer subfeature **complete**. Both subfeatures done.

**What was accomplished this session:**
- **`RegulatedZonesRepository`** — new asset loader that reads prebaked `RegulatedZoneSet` from `assets/regulated-zones/<region>.bin` via `context.assets.open()`. Best-effort: null if asset missing (graceful degradation).
- **`drawRegulatedZones()`** — osmdroid `Polygon`-based renderer: one translucent filled polygon per zone with coloured outline. Supports polygon holes (island interiors). Zoom-gated at level 10.
- **Per-type colour palette** — 8 `RegulatedZoneType` values mapped to distinct colours at ~19% fill opacity (matching zone300 style): speed (blue), anchoring (amber), access (red), environmental (green), mooring (teal), fishing (yellow), navigation (purple), other (grey).
- **`regulatedZonesVisible` setting** — added to `AppSettings` with `SharedPreferences` persistence (default `true`).
- **`RegulatedZonesLayerButton`** — 64dp circle button (ring+dot icon) in the right-edge layer control stack, between the danger (low-depth) and 300 m band toggles.
- **Data flow wiring** — loaded via `produceState` in `MapScreen`, threaded through `MapContent` → `CoastlineMapView`. Overlay placed between isobaths and the 300 m band in the z-order stack.
- **BUILD SUCCESSFUL** — `assembleDebug` passes with zero errors.

**Source files:**
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZonesRepository.kt` — NEW
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — MODIFIED (regulatedZonesVisible)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — MODIFIED (drawRegulatedZones, RegulatedZonesLayerButton, wiring)

**Next step:** No remaining subfeatures. Possibly vessel-filter interactive demo, or legend panel, or move to next feature.
