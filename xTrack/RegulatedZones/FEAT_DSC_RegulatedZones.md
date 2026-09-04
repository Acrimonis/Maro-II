---
name: RegulatedZones
status: active
created: 2026-06-11 18:00
modified: 2026-06-13 20:23
---

# Feature: RegulatedZones

**Description:**
French coastal waters (Nice–Fréjus corridor) have numerous regulated zones: speed limitations, anchoring restrictions, access prohibitions, environmental protection areas. Published by SHOM via REST/WFS and by DIRM Méditerranée / data.gouv.fr. Goal: gather, aggregate, and model these zones into a structured, serialized dataset prebaked as a bundled asset and rendered as a map overlay. The app is a pure consumer — all gathering at build time. Key constraint: the boat is <6 m, so vessel-size restrictions must be captured and filterable.

## Sections

### data-lookup

Source discovery (SHOM WFS/INSPIRE), `RegulatedZone` data model + Protobuf, `ShomRegulationClient`, aggregation/dedup with seed fallback, and prebake.

#### Todos
- [ ] Build bake script `tools/bake-regulated-zones.bat` calling the prebake test

#### Rules
- Follow the prebake pattern; best-effort fetch (seeds provide baseline); no redistribution; bbox = Nice–Fréjus corridor

#### Key Files
- `app/src/main/java/ykws/android/maro/data/regulation/{RegulatedZone,ShomRegulationClient,RegulationAggregator,RegulatedZoneSerializer}.kt`
- `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt`

### trouble-shoot-reg-layers

Hexagon-shaped polygon rendering on two zones (invalid/degenerate vertices in the geometry pipeline).

#### Todos
- [ ] Verify fix on device/emulator

#### Rules
- A hexagon indicates osmdroid default fallback rendering; trace SHOM WFS GeoJSON → model → Protobuf → drawRegulatedZones()

#### Key Files
- `ui/map/MapScreen.kt`, `data/regulation/{ShomRegulationClient,RegulatedZone,RegulatedZoneSerializer,RegulatedZonesRepository}.kt`

### reg-zones-filtering

Vessel-size filtering + speed-limit extraction at bake time; icon assignment + warning strip UI.

#### Todos
- [ ] Phase 1 — data extraction & type audit
- [ ] Phase 2 — bake-time filtering (`RegulationFilter` + maro.properties keys)
- [ ] Phase 3 — icon assignment (`RegulatedZoneIconProvider`)
- [ ] Phase 4 — warning strip UI (`RegulatedZoneWarningStrip`)

#### Rules
- Filter runs at bake time; speed zones always apply via `appliesTo()`; default filtered types: ENVIRONMENTAL/FISHING_PROHIBITED/OTHER

### add-zone-text

Zone name/description labels on regulated zone polygons.

#### Todos
- [ ] Design label placement strategy
- [ ] Implement text overlay on polygons
- [ ] Style font/colour/outline
- [ ] Gate behind zoom level
- [ ] Optional settings toggle

### design

Unify speed restrictions (SHOM speed zones + virtual 300m band) into a single Speed Limit engine replacing the 300m-only tile.

#### Todos
- [ ] Validate signed distance logic with real baked data (device/emu)
- [ ] Refine tile flow — always warn of arrival at zone limit (entry or exit)
- [ ] Add speed icon at bottom left when inside speed zone

#### Rules
- 300m band = virtual 5 kn speed zone; spatial index prebaked; color thresholds green/≤limit, orange/≤limit×1.4, red/>limit×1.4; most restrictive limit wins; hysteresis deadband ±5m; graceful degradation to 300m-band-only

#### Key Files
- `data/regulation/{SpeedZone,SpeedZoneBuilder}.kt`, `spatial/SpeedZoneIndex.kt`
- `ui/map/{CoastlineViewModel,DashboardPanel,MapScreen}.kt`

### more-dedebug

Decouple cone/green-line drawing and colour the direction arrow by speed compliance.

#### Todos
- [ ] Decouple cone and green line into independent update cycles
- [ ] Colour direction arrow by speed-vs-limit ratio (green/orange/red)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`

## Implemented

- **display-layer** — `RegulatedZonesRepository` asset loader + 8-type colour palette + `drawRegulatedZones()` + visibility toggle + layer button
- **toggle-control-merge** — 4-state cycle button (`ZoneLayerButton` / `ZoneLayerState`) + settings toggle; `maro.properties` defaults
- **preparation-for-icons-layout** — GPS icon moved top-left beside EarthWater; icon transparency properties
- **multi-source-normalization** — `RegulationClassification` + enhanced speed extraction (CATREA/RESTRN/INFORM/TXTDSC) + IGN Carto Nature (Natura 2000) + 3-way dedup

## Rules
- Personal-use app — regulatory data fetched offline, not redistributed
- Bake before build: `bake-regulated-zones` as an `apk-bake.bat` target
- The 300 m band (Zone300) is owned by Coastline/Zone300 — this feature references but never duplicates it

## Key Files
- `app/src/main/java/ykws/android/maro/data/regulation/` — model, clients, aggregator, serializer, repository
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — overlay rendering + toggles

## Docs
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_category-icon-mapping.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_data-lookup-plan.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_filter-design.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_hexagon-fix-plan.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_icon-warnings-plan.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_multi-source-normalization.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_reqs-formalized.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_toggle-merge-design.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_vessel-filter-design.md`
