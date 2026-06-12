---
name: RegulatedZones
status: active
created: 2026-06-11 18:00
modified: 2026-06-12 06:55
active_subfeature: none
---

# Feature: RegulatedZones

**Description:**
French coastal waters — particularly the Nice–Fréjus marine corridor — have numerous regulated zones: speed limitations (e.g. 10 kn off Cap d'Antibes, 5 kn in the 300 m band), anchoring restrictions, access prohibitions (e.g. between the Îles de Lérins, size/engine restrictions), and environmental protection areas. These are published by SHOM (Service Hydrographique et Océanographique de la Marine) via their REST/WFS APIs, and also by DIRM Méditerranée and data.gouv.fr.

Goal: gather, aggregate, and model these spatial regulatory zones into a structured, serialized dataset that can be prebaked as a bundled asset and rendered as a map overlay layer. The app is a pure consumer — all data gathering runs at build time on the computer (prebake pattern), never on-device.

**Key design constraint:** The user's boat is < 6 m. The data model and client must capture **vessel size restrictions** (min/max length) published in SHOM's WFS properties so zones that don't apply to small boats can be filtered at runtime.

## Subfeatures

### data-lookup  [x]

**Focus:** Data source discovery, WFS/REST fetching, aggregation, deduplication, and serialization into a bundled `.bin` asset.

**Design approach:** Follow the established prebake pipeline pattern (Gather → Process → Serialize). The data model must be generic enough to handle multiple regulation types (speed zones, anchoring, access, environmental) with typed attributes and polygon geometries.

#### Todos
- [x] **Source discovery & capability check.** Identify SHOM WFS endpoint and layer names for maritime regulation zones in the Mediterranean:
  - Candidate: SHOM `data.shom.fr` WFS — `https://services.data.shom.fr/wfs/reglementation` (or similar) + GetCapabilities to enumerate `typeName`s
  - Candidate: SHOM INSPIRE WFS — `https://services.data.shom.fr/inspire/wfs`
  - Backup: DIRM Méditerranée arrêtés data; data.gouv.fr; OSM `boundary=maritime` relation tagging
  - Confirm: `outputFormat=application/json` support (preferred), or fall back to GML/XML parsing
- [x] **Define regulated zone data model.** Create `RegulatedZone` data class:
  - Geometry: `List<List<LatLng>>` (polygon outer rings, zero or more holes)
  - Attributes: `zoneType` enum (`SPEED_LIMIT`, `ANCHORING_PROHIBITED`, `ACCESS_PROHIBITED`, `ENVIRONMENTAL`, `MOORING`, `FISHING_PROHIBITED`, `NAVIGATION_RESTRICTION`, `OTHER`)
  - `speedLimitKn: Double?` (null if not speed-related)
  - `name: String`, `source: String` (e.g. "SHOM", "DIRM", "OSM"), `sourceRef: String` (official ID/arrêté ref)
  - `validityPeriod: String?` (saisonnalité, if any)
  - `description: String`
  - `vesselSizeRestriction: VesselSizeRestriction?` (min/max vessel length, null = applies to all)
- [x] **Add `@ProtoNumber` annotations** to data model for Protobuf serialization
- [x] **Create `RegulatedZoneSerializer`** — `object` using `ProtoBuf { encodeDefaults = false }`
- [x] **Add `VesselSizeRestriction` data class** — `minLengthM: Double?`, `maxLengthM: Double?`, @ProtoNumber(1-2)
- [x] **Add vessel size field to `RegulatedZone`** — `@ProtoNumber(9) val vesselSizeRestriction: VesselSizeRestriction? = null`
- [x] **Update SHOM WFS client** — parse `longueur_hors_tout_mini` / `longueur_hors_tout_maxi` (or `longueur_mini` / `longueur_maxi`) from GeoJSON properties and populate `vesselSizeRestriction`
- [x] **Build SHOM WFS client** — `ShomRegulationClient.kt` following the pattern from the planned `ShomAtonClient` (OkHttp, WFS GetFeature request with bbox filter, parse GeoJSON or GML response):
  - bbox = Nice–Fréjus corridor (6.73°E–7.31°E, 43.35°N–43.73°N)
  - Best-effort, swallow errors (dataset degrades gracefully to empty)
  - Add a hard-coded seed fallback: the known Cap d'Antibes 10 kn zone, Lérins restrictions, and the 300 m band (already covered by Zone300 feature, but enumerated here for consistency)
- [x] **Build aggregation/normalization** — merge zones from multiple sources (SHOM + seeds + future): deduplicate by proximity (zones within X m = same feature), reconcile conflicting attributes
- [x] **Define serialization schema** — Protobuf or kotlinx.serialization for `RegulatedZoneSet`:
  - `zones: List<RegulatedZone>`
  - `metadata: {regionId, source, fetchTimestampMs, zoneCount}`
- [x] **Build prebake test** — `RegulatedZonePrebakeTest.kt` gated by `-Dmaro.prebake=true`:
  - Fetch from live sources → aggregate → serialize to `data/app-assets/regulated-zones/<region>.bin`
  - Produce a console summary: zones found by type, count per source
- [ ] **Build bake script** — `tools/bake-regulated-zones.bat` calling the prebake test
- [x] **Integrate into `apk-bake.bat`** — add "regulated-zones" as a selectable target in the bake menu

#### Rules
- Follow the prebake pattern: all gathering at build time, app is pure consumer
- Best-effort fetch: if SHOM WFS is unreachable, the seeds provide baseline coverage
- No redistribution of SHOM data — personal-use app
- bbox is the Nice–Fréjus corridor; reject anything outside
- Use OkHttpClient (already wired in the project) — no new HTTP dependencies

#### Key Files
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` — NEW. Data model (data classes, enums, `RegulatedZoneSet`)
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` — NEW. WFS GetFeature client for SHOM regulation layers
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt` — NEW. Merge + deduplicate zones from multiple sources
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZoneSerializer.kt` — NEW. Serialization to/from `.bin`
- `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt` — NEW. Prebake test gated by `-Dmaro.prebake=true`
- `tools/bake-regulated-zones.bat` — NEW. Bake script
- `data/app-assets/regulated-zones/` — NEW. Output directory (gitignored)

### display-layer  [x]

**Focus:** Rendering regulated zones as a map overlay layer with per-type colour/legend.

Renders each [RegulatedZone] as a translucent filled osmdroid Polygon with a coloured
outline. Each [RegulatedZoneType] gets a distinct colour — speed limits blue, anchoring
amber, access red, environmental green, mooring teal, fishing yellow, navigation purple,
other grey. Polygon holes (island interiors) are supported. A visibility toggle button
(ring-with-dot icon) sits in the right-edge layer controls, between the danger and 300 m
band toggles. Zoom-gated below zoom 10.

The prebaked asset is loaded via [RegulatedZonesRepository] from `assets/regulated-zones/`
using a `produceState` in `MapScreen`, following the same pattern as `depthBitmap`. If no
asset is found (never baked), the overlay is simply absent — graceful degradation.

#### Todos
- [x] **Create `RegulatedZonesRepository`** — load prebaked `RegulatedZoneSet` from APK assets
- [x] **Define per-type colour palette** — `regulatedZoneColor()` for all 8 `RegulatedZoneType` values
- [x] **Implement `drawRegulatedZones()`** — osmdroid Polygon per zone with fill + stroke, holes support, zoom gate at 10
- [x] **Add `regulatedZonesVisible` setting** — `AppSettings` field + `SettingsManager` persistence key
- [x] **Add layer toggle button** — `RegulatedZonesLayerButton` in right-edge control stack (ring + dot icon)
- [x] **Wire data flow** — `produceState` loading in `MapScreen`, pass through `MapContent` → `CoastlineMapView`
- [x] **Overlay stack placement** — drawn between isobaths and the 300 m band (above contours, below coastline)

#### Rules
- Follow the existing osmdroid overlay pattern (Polygon fill + Polyline outline, same as Zone300)
- Graceful degradation: no baked asset → no overlay (null-safe in drawRegulatedZones)
- Zoom-gate at 10 to avoid sub-pixel rendering
- Layer button matches the existing 64dp circle style with theme-blue tint

#### Key Files
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZonesRepository.kt` — NEW. Asset loader
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — MODIFIED. Added `drawRegulatedZones()`, `RegulatedZonesLayerButton`, `regulatedZonesVisible` wiring
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — MODIFIED. Added `regulatedZonesVisible` field + persistence

## Todos
- [x] Data source discovery (SHOM WFS endpoint & layer names)
- [x] Data model definition (`RegulatedZone` data class + enum + set + `VesselSizeRestriction`)
- [x] SHOM WFS client implementation
- [x] Aggregation + dedup + seed fallback
- [x] Serialization schema (Protobuf + `RegulatedZoneSerializer`)
- [x] Prebake test + bake script
- [x] Integration into apk-bake.bat
- [x] Live SHOM INSPIRE WFS integration test (Cap d'Antibes, 72 zones)
- [x] Regulated zones map overlay renderer (`drawRegulatedZones()`)
- [x] Per-type colour palette (8 zone types)
- [x] Regulated zones visibility toggle + layer button
- [x] Prebaked asset loading via `RegulatedZonesRepository`
- [x] Vessel size filtering (`appliesTo()` with description text heuristic)
- [x] Speed limit extraction from description text
- [x] Public INSPIRE endpoint (no auth required)
- [x] `test-regulated-zones.bat` runner with HTML report
- [x] ELI16 practical summary in README

## Rules
- Personal-use app — regulatory data fetched offline, not redistributed
- Bake before build: `bake-regulated-zones` must exist as an `apk-bake.bat` target alongside coastline/depth
- The 300 m band (Zone300) is already covered by the Coastline/Zone300 feature — the regulated-zones feature may reference it but must not duplicate it

## Key Files
- (See subfeature key files above)

## Docs
- [`plans/regulated-zones-vessel-filter-design.md`](../plans/regulated-zones-vessel-filter-design.md) — Vessel size restriction design discussion: data model, SHOM parsing, runtime filtering, updated ProtoNumber assignment, and run instructions
- [`plans/regulated-zones-readme.md`](../plans/regulated-zones-readme.md) — Feature README: architecture overview, per-step test coverage, how to demonstrate each step, quick reference
