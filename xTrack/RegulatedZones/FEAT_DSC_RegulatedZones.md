---
name: RegulatedZones
status: active
created: 2026-06-11 18:00
modified: 2026-06-13 20:23
active_subfeature: design
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
other grey. Polygon holes (island interiors) are supported. Zoom-gated below zoom 10.

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

### toggle-control-merge  [x]

**Focus:** Merge the 300m zone and regulated zones map toggle buttons into a single 4-state cycle button, plus add a regulated zones toggle in General/Display/Layers settings.

Replaces the two separate layer toggle buttons (``LayerButton`` for Zone300, ``RegulatedZonesLayerButton`` for regulated zones) with one button cycling through: **None → 300m Zone → Both → Reg Zones**. The button icon uses two concentric circles — inner circle for 300m state, outer circle for regulated zones state. The settings page gets a ``regulatedZonesVisible`` toggle in the General/Display/Layers section alongside the existing zone300 toggle.

#### Todos
- [x] **Create ``maro.properties``** — new config file at repo root with ``layer.zone300.default``, ``layer.regulatedZones.default``, ``layer.lowDepthWarning.default``, ``layer.coastline.default`` keys
- [x] **Wire maro.properties to BuildConfig** — in ``app/build.gradle.kts``, read properties and expose as ``BuildConfig`` fields
- [x] **Update AppSettings defaults** — in ``SettingsManager.kt``, read BuildConfig values as defaults instead of hardcoded booleans
- [x] **Build merged 4-state button** — ``ZoneLayerButton`` composable replacing ``LayerButton`` + ``RegulatedZonesLayerButton``; concentric-circle icon; derives state from two booleans
- [x] **Add ``ZoneLayerState`` enum** — ``NONE``, ``ZONE300``, ``BOTH``, ``REGULATED`` with ``next()`` and ``fromBooleans()`` factory
- [x] **Wire single onCycleZoneLayers callback** — replaces ``onToggleZone300`` + ``onToggleRegulatedZones`` in the right-edge control stack
- [x] **Add settings toggle** — ``regulatedZonesVisible`` checkbox in General/Display/Layers settings section (follow existing ``zone300`` toggle pattern)

#### Rules
- Preserve existing independent settings booleans — the 4-state cycle sets both, but individual settings toggles still override
- Derive 4-state from two booleans each render (not stored separately) to stay synced with Settings
- Z-order remains: isobaths → regulated zones → 300m zone → coastline, regardless of state
- Default state: None (both layers off) — configurable via maro.properties
- Cycle order: None → 300m → Both → Reg
- Danger layer button stays separate (not merged)
- Icon uses two concentric circles: inner = 300m state, outer = regulated zones state

#### Key Files
- ``app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`` — MODIFIED. Replace two toggle buttons with one merged button
- ``app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`` — MODIFIED. Add ``regulatedZonesVisible`` settings UI entry
- ``app/src/main/java/ykws/android/maro/ui/settings/SettingsScreen.kt`` — MODIFIED. Add regulated zones toggle in layers section

### preparation-for-icons-layout  [x]

**Focus:** Move GPS icon from bottom-left to top-left, next to the Earth/Water icon, to free up bottom-left space for future icon layout changes.

Relocates the [GpsStatusIcon] composable from [Alignment.BottomStart] to a Row alongside [EarthWaterIcon] at [Alignment.TopStart], preserving the same 6.dp padding and icon sizes.

#### Todos
- [x] **Move GPS icon** — change GpsStatusIcon placement from BottomStart to TopStart, group with EarthWaterIcon in a horizontal Row
- [x] **Update doc comment** — fix GpsStatusIcon KDoc "placed top-left below EarthWaterIcon" → "placed top-left to the left of EarthWaterIcon"
- [x] **Normalize icon colors** — GPS HEALTHY/IDLE now use same green/blue as EarthWaterIcon and theme
- [x] **Add icon transparency properties** — `icon.back.active.transparency` and `icon.back.inactive.transparency` in maro.properties, wired through BuildConfig → ZoneConfig

#### Key Files
- ``app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`` — MODIFIED. GPS icon layout repositioned

### trouble-shoot-reg-layers  [ ]

**Focus:** Debug and fix hexagon-shaped polygon rendering on regulated zone map overlays — two zones (one north of Îles de Lérins, one on top of Cap d'Antibes) render as default hexagons instead of their actual GeoJSON polygon shapes, indicating invalid/degenerate vertices in the geometry pipeline.

#### Todos
- [x] Pinpoint root cause of hexagon rendering (seed zones + bad dedup: 25m centroid-distance + type-equality gate)
- [x] Document findings with concrete examples from the two affected zones
- [x] Implement fix for the geometry pipeline (overlap dedup, RDP simplification, Protobuf serializer)
- [ ] Verify fix on device/emulator (manual — Phase C + D)

#### Rules
- A hexagon shape in osmdroid Polygon indicates default fallback rendering — the Polygon's outline/points may be empty, null, or contain invalid coordinates
- Trace the full data flow: SHOM WFS GeoJSON → ShomRegulationClient → RegulatedZone model → Protobuf serialization → deserialization → drawRegulatedZones() Polygon construction

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `drawRegulatedZones()` polygon rendering
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` — GeoJSON polygon coordinate extraction
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` — Data model with polygon geometry
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZoneSerializer.kt` — Protobuf serialization of geometry
- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZonesRepository.kt` — Asset loading and deserialization

### reg-zones-filtering  [ ]

**Focus:** Vessel size filtering and speed limit extraction for regulated zones — ensuring zones with `vesselSizeRestriction` (min/max length) and speed limits parsed from description text are correctly applied at runtime based on the user's vessel configuration.

#### Todos
- [ ] **Phase 1 — Data Extraction & Type Audit:** Run prebake, dump per-zone details, decide keep/filter list for zone types
- [ ] **Phase 2 — Bake-Time Filtering:** Create `RegulationFilter.kt` with vessel-size + type gates; add `maro.properties` keys; wire into prebake test
- [ ] **Phase 3 — Icon Assignment:** Create `RegulatedZoneIconProvider.kt` generating emoji-on-circle Bitmap icons per zone type
- [ ] **Phase 4 — Warning Strip UI:** Add `RegulatedZoneWarningStrip` composable at `Alignment.BottomStart` — horizontal Row of emoji-on-circle icons per active zone type, tied to `regulatedZonesVisible` setting

#### Rules
- Filter runs at bake time (computer), not runtime — produces smaller `.bin` asset
- Speed limit zones always apply per existing `appliesTo()` logic
- Default filtered types: `ENVIRONMENTAL`, `FISHING_PROHIBITED`, `OTHER` (configurable via maro.properties)
- Use emoji-on-canvas for v1 icons (same pattern as GpsStatusIcon's "📡")
- Store centroids in Protobuf model with nullable defaults so old assets deserialize safely

#### Key Files
- **NEW** — `app/src/main/java/ykws/android/maro/data/regulation/RegulationFilter.kt`
- **NEW** — `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneIconProvider.kt`
- **MODIFIED** — `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` (add centroid fields)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt` (compute centroids)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (icon rendering + overlay cleanup)
- **MODIFIED** — `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt` (insert filter step)
- **MODIFIED** — `maro.properties` + `app/build.gradle.kts` (new config keys)

### add-zone-text  [ ]

**Focus:** Add zone name/description text labels on regulated zone map polygons so the user can identify which zone is which (e.g., "Cannes bay speed limit", "Anchoring prohibited") without guessing from polygon colour alone.

#### Todos
- [ ] Design label placement strategy (centroid? leader line? avoid overlap?)
- [ ] Implement text overlay on regulated zone polygons
- [ ] Style: font size, colour, outline for readability on varied backgrounds
- [ ] Gate behind zoom level (show only above threshold zoom)
- [ ] Optional toggle in settings

#### Key Files
- **MODIFIED** — `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (`drawRegulatedZones` text labels)

### multi-source-normalization  [x]

**Focus:** Extend data gathering to normalize regulated zone data from multiple sources — SHOM auth `REGNAV_BDD_WFS:resare` layer with CATREA/RESTRN/INFORM/TXTDSC parsing, and a new INPN WFS client for Marine Protected Areas. Enhance speed extraction via INFORM string tokenization and TXTDSC legal reference cross-referencing.

#### Todos
- [x] **Phase A1 — Data model extension:** Add `RegulationClassification` sealed class, `SpeedSource` enum, 3 new nullable fields (`classification`, `speedSource`, `legalDecreeRef`) with ProtoNumber 11–13
- [x] **Phase A2 — Enhanced speed extraction:** Add `parseSpeedFromInform()` with expanded keyword matching (`nœuds`, `noeud`, `nds`, `nd`, `kts`, `vitesse`); add `TXTDSC_SPEED_MAP` lookup table (`FR_PREMAR_MED_134_2021`→10 kn, `FR_PREMAR_MED_2012_064`→5 kn)
- [x] **Phase A3 — CATREA + RESTRN parsing:** Add `parseCatrea()` (12→NAVIGATION_RESTRICTION, 27→SPEED_LIMIT); add `parseRestrnAuth()` (1/2→ANCHORING_PROHIBITED, 7/8→ACCESS_PROHIBITED, 10→OTHER)
- [x] **Phase A4 — Auth endpoint support:** Add `DEFAULT_BASE_URL`, `CANDIDATE_TYPENAMES` including `REGNAV_BDD_WFS:resare`, CATREA/RESTRN parsing in `ShomRegulationClient`
- [x] **Phase B1 — Create `InpnRegulationClient`:** WFS GetFeature client for INPN endpoint (BunkerWeb WAF blocks this machine)
- [x] **Phase B2 — INPN GeoJSON parsing:** Map `mpa_type`→`zoneType` (Natura 2000→ENVIRONMENTAL, Biotope→ACCESS_PROHIBITED, Réserve→NAVIGATION_RESTRICTION)
- [x] **Phase B3 — Create `IgnCartoNatureClient`** (alternative to INPN): IGN API Carto Nature client for Natura 2000 data — proven working (16 zones)
- [x] **Phase C1 — Extend aggregator:** Accept `inpnZones` parameter, 3-way dedup with authority rules (SHOM > INPN > SEED), 50 m Haversine threshold, unknown-source handling
- [x] **Phase C2 — Expand bbox:** Update to `6.7,43.4,7.6,43.8` (Menton to Fréjus)
- [x] **Phase C3 — Wire client into prebake:** IGN Nature client fetch + aggregate, per-source summary

#### Rules
- Follow the prebake pattern: all gathering at build time, app is pure consumer
- Best-effort fetch for all sources — unreachable WFS degrades gracefully to empty
- SHOM auth endpoint is best-effort; if it fails (no API key configured), fall back to INSPIRE only
- New fields are nullable with `null` defaults — backward-compatible with existing `.bin` assets
- Use OkHttpClient (already wired) — no new HTTP dependencies

#### Key Files
- **MODIFIED** — `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` (sealed class, enum, 3 new fields)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` (CATREA/RESTRN, INFORM, TXTDSC, REGNAV layer)
- **NEW** — `app/src/main/java/ykws/android/maro/data/regulation/IgnCartoNatureClient.kt` (IGN API Carto Nature — Natura 2000, working)
- **NEW** — `app/src/main/java/ykws/android/maro/data/regulation/InpnRegulationClient.kt` (INPN WFS, WAF-blocked from this machine)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/data/regulation/RegulationAggregator.kt` (3-way merge, 50m Haversine threshold)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneIconProvider.kt` (8 display categories)
- **MODIFIED** — `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt` (IGN wiring, expanded bbox)
- **NEW** — `plans/regulated-zones-multi-source-normalization.md` (this plan)

### design  [ ]

**Focus:** Unify speed restrictions from regulated zones with the 300m zone mechanism — a single Speed Limit engine replacing the current 300m-only dashboard tile with a unified speed limit card showing the most restrictive speed under the boat.

**Design approach:** Create a spatial index over speed zone polygon edges (baked into `.bin` asset, zero runtime build). Merge the 300m geometric band as a virtual 5-kn speed zone. Query nearest zone boundary + most restrictive speed at each GPS tick. Generalize the existing `zone300Decision()` auto-show to any speed zone. Color-code dashboard by speed-vs-limit ratio (green ≤ limit, orange ≤ limit×1.4, red > limit×1.4).

#### Todos
- [x] **Create `SpeedZone` runtime model** — `SpeedZone` + `SpeedZoneQuery` in [`SpeedZone.kt`](../../../../app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt)
- [x] **Create `SpeedZoneBuilder`** — filters `RegulatedZoneSet` to `speedLimitKn != null`, builds `List<SpeedZone>`
- [x] **Create `SpeedZoneIndex`** — grid spatial index over polygon edges (~1-2ms runtime build) in [`SpeedZoneIndex.kt`](../../../../app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt); fixed containment to check ALL zones (not grid-proximate only)
- [x] **Extend `CoastlineViewModel` shore pipeline** — add speed zone queries, merge 300m+SHOM speed limit, 5 new StateFlows (`speedLimitKn`, `activeSpeedZone`, `speedZoneQuery`, `distanceToSpeedZone`, `approachingSpeedZone`)
- [x] **Replace `Zone300Card` with `SpeedLimitCard`** — unified dashboard tile with zone name, limit, distance, compliance color; approaching state shows distance as main value + direction arrow
- [x] **Generalize `zone300Decision()` to any speed zone** — created `zoneAutoShowDecision()` with `ZoneAutoShowConfig` parameterizing 300m-band vs speed-zone behavior; replaced both call sites in `onEach` pipeline. Added speed zone auto-show toggles to Navigation settings UI.
- [x] **Add speed zone auto-show toggles** — `speedZonesVisible`, `speedZoneAutoShowGps`, `speedZoneAutoShowDemo` in AppSettings + SettingsManager
- [x] **Update `SpeedCard` color-coding** — relative to active speed zone limit (limit × 1.4 thresholds)
- [x] **Unit tests** — `SpeedZoneIndexTest` (9 tests), `SpeedZoneBuilderTest` (11 tests), `Zone300DecisionTest` updated (33 tests covering both 300m band + speed zone behaviors)
- [x] **Zone-ahead cone visualization** — flashy yellow cone (±15°) on map showing heading-ahead search area; always visible when heading is known
- [x] **Zone-ahead green line** — thick solid green line from boat to detected zone intersection; always visible
- [x] **Demo mode heading** — always 0° (north), boat always faces top of map; speed still extrapolated from pan velocity
- [x] **Heading-ahead always active** — removed old `hasHeading` gate; runs on every pipeline tick in both GPS and demo modes
- [x] **Prebake fixes** — zone names fixed (SHOM literal "null" filtered, fallback to "Zone X kn"); fresh prebake with current schema
- [x] **Exhaustive containment** — `SpeedZoneIndex.query()` checks ALL zones for point-in-polygon, not just grid-proximate ones (fixes large zone detection)
- [ ] **Validate distance to speed zone** — verify signed distance logic (inside → negative, outside → positive) works correctly with real baked data (requires device/emu)
- [ ] **Refine tile flow** — always warn of arrival at zone limit (entry or exit), not just when already inside
- [ ] **Add speed icon at bottom left** — show speed icon in the dashboard when inside 300m zone / speed zone

#### Rules
- The 300m geometric band is a virtual speed zone (5 kn) — same query path as SHOM zones
- Spatial index is prebaked — zero build cost on device, invalidated on rebake
- Color thresholds: green ≤ zone limit, orange ≤ limit×1.4, red > limit×1.4
- Overlapping zones: display the most restrictive (lowest) speed limit
- Hysteresis deadband (±5m configurable) prevents GPS jitter at zone boundaries
- Graceful degradation: no baked asset → 300m-band-only fallback

#### Key Files
- **NEW** — `app/src/main/java/ykws/android/maro/data/regulation/SpeedZone.kt` (runtime model + query result)
- **NEW** — `app/src/main/java/ykws/android/maro/spatial/SpeedZoneIndex.kt` (grid spatial index)
- **NEW** — `app/src/main/java/ykws/android/maro/data/regulation/SpeedZoneBuilder.kt` (unifies 300m band + SHOM speed zones)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` (shore pipeline extension)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` (SpeedLimitCard replacing Zone300Card)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (wire new StateFlows)
- **MODIFIED** — `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` (auto-show toggles)
- **PLAN** — `plans/speed-zones-design.md` (this design)

#### Docs
- [`plans/speed-zones-design.md`](../plans/speed-zones-design.md) — Full design plan
- [`plans/speed-zones-heading-distance-discussion.md`](../plans/speed-zones-heading-distance-discussion.md) — Gap analysis: heading-aware distance not implemented, auto-show not wired
- [`plans/speed-zones-side-zone-display-design.md`](../plans/speed-zones-side-zone-display-design.md) — Side-zone display: show left/right/behind direction when nothing ahead

### more-dedebug  [ ]

**Focus:** Debug and decouple visualization elements — separate cone and green line drawing, arrow color reflecting speed excess.

#### Todos
- [ ] **Decouple cone and green line drawing** — separate the cone (yellow wedge) and green line (dashed path) into independent LaunchedEffect/update cycles in MapScreen so changes to one don't force a combined redraw of the other
- [ ] **Color direction arrow by speed compliance** — change arrow color (green/orange/red) based on speed-vs-limit ratio, matching the dashboard tile color scheme

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — drawZoneAheadCone, drawZoneAheadLine, LaunchedEffect for heading-ahead

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

#### Docs
- [`plans/regulated-zones-icon-warnings-plan.md`](../plans/regulated-zones-icon-warnings-plan.md) — Implementation plan for regulated zone icon warnings: data extraction, bake-time filtering, icon assignment, map overlay rendering

## Docs
- [`plans/regulated-zones-vessel-filter-design.md`](../plans/regulated-zones-vessel-filter-design.md) — Vessel size restriction design discussion
- [`plans/regulated-zones-readme.md`](../plans/regulated-zones-readme.md) — Feature README: architecture overview, per-step test coverage
- [`plans/regulated-zones-toggle-merge-design.md`](../plans/regulated-zones-toggle-merge-design.md) — Toggle button merge design
- [`plans/regulated-zones-category-icon-mapping.md`](../plans/regulated-zones-category-icon-mapping.md) — Functional category-to-icon chain
- [`plans/regulated-zones-reqs-formalized.md`](../plans/regulated-zones-reqs-formalized.md) — Formalized requirements
- [`plans/regulated-zones-multi-source-normalization.md`](../plans/regulated-zones-multi-source-normalization.md) — Implementation plan
- [`xTrack/RegulatedZones/FEAT_PLN_RegulatedZones_data-format-redesign.md`](FEAT_PLN_RegulatedZones_data-format-redesign.md) — Data format redesign proposal (no backward compat)
