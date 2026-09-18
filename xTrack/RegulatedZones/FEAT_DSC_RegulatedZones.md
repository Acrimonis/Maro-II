---
name: RegulatedZones
status: active
created: 2026-06-11 18:00
modified: 2026-09-16 17:10
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
- Phase 3 (icon assignment) and Phase 4 (warning strip UI) are built — see `## Implemented`; the tag column's family alignment and its trigger rule landed 2026-09-16

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

- **zones-transparency (2026-09-18, `feature/zones-transparency-settings`)** — `drawRegulatedZones()` no longer bakes its own alphas: the polygon fill and outline alpha now come from the user's transparency pair (`regulatedZoneFillTransparencyPct` default 80, `regulatedZoneBoundaryTransparencyPct` default 20, set by the Regulated Zones Appearance row in Settings), derived through the shared `transparencyPctToAlpha`, and `regulatedZoneColor()` returns the per-type hue alone — the `RegulationZoneColor` pair is gone. The icon stack is untouched: it paints opaque category colours. → `xTrack/Ui_Settings/260918_FEAT_PLN_Ui_Settings_regulated-zones-transparency.md`
- **display-layer** — `RegulatedZonesRepository` asset loader + 8-type colour palette + `drawRegulatedZones()` + visibility toggle + layer button
- **toggle-control-merge** — 4-state cycle button (`ZoneLayerButton` / `ZoneLayerState`) + settings toggle; `maro.properties` defaults
- **preparation-for-icons-layout** — GPS icon moved top-left beside EarthWater; icon transparency properties
- **multi-source-normalization** — `RegulationClassification` + enhanced speed extraction (CATREA/RESTRN/INFORM/TXTDSC) + IGN Carto Nature (Natura 2000) + 3-way dedup
- **tag-stack-trigger (2026-09-16, `feature/tracking-more`)** — the bottom-left tags stopped riding the layer's visibility: their set is derived from the Zone categories settings, they are tested against the marker rather than the boat, and the band sign gets its own band result at that point (`markerInZone300`), so the dashboard keeps the boat while the stack answers what the user is looking at. `MapContent`'s `boatPosition` parameter is gone. → `xTrack/RegulatedZones/260916_FEAT_PLN_RegulatedZones_tag-stack-trigger.md`

## Rules
- Personal-use app — regulatory data fetched offline, not redistributed
- Bake before build: `bake-regulated-zones` as an `apk-bake.bat` target
- The 300 m band (Zone300) is owned by Coastline/Zone300 — this feature references but never duplicates it
- The warning strip sits bottom-left, is fed by the zones that contain the boat, dedupes by display category and speed, and suppresses the regulated speed tags while the 300 m band is in force (promoted from the retired icon-warnings plan)
- Two points, on purpose: the stack answers for the marker and the dashboard for the boat. The tags are fed `mapCenter` and their own band result (`markerInZone300`) — never the pipeline's `inZone300`, and never gated by the layer's visibility, which gates the polygons only
- The 300 m sign is unconditional: it ignores the Zone categories toggles by design, where every other tag follows them

## Key Files
- `app/src/main/java/ykws/android/maro/data/regulation/` — model, clients, aggregator, serializer, repository
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — overlay rendering + toggles
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt` — `regulatedZoneColor()` + `drawRegulatedZones()`, whose alphas now come from the settings pair

## Docs
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_category-icon-mapping.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_data-lookup-plan.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_filter-design.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_hexagon-fix-plan.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_multi-source-normalization.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_reqs-formalized.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_toggle-merge-design.md`
- `xTrack/RegulatedZones/260612_FEAT_PLN_RegulatedZones_vessel-filter-design.md`
- `xTrack/RegulatedZones/260916_FEAT_PLN_RegulatedZones_tag-stack-trigger.md`
- `xTrack/Navigation/260916_FEAT_PLN_Navigation_dashboard-position-source.md` — companion plan, Step 2

## Walk

**Level 1 — Date:** 2026-09-16 · **Source:** the two companion plans' open points, tag stack first — the tag-stack trigger plan and the dashboard position-source plan · **Active:** 9 · **Closed:** 2026-09-16

1. Band tag point — **closed:** the tag keeps the marker, the dashboard keeps the boat
2. Auto-reveal narrowing — **closed:** the narrowing keeps the marker
3. Step 1 — **closed:** seven ordered steps in §5.7 of the tag-stack plan
4. Step 2 — **closed:** six ordered steps in §4.7 of the dashboard plan
5. Unit test — **closed:** `DashboardPositionTest`, three cases, scoped run
6. Build both — **closed:** `apk-build.bat`, overlay still layer-gated
7. Device pass — **closed:** the checks listed in both plans
8. Registration — **closed:** both plans and their rules into both feature files
9. R8 — **closed:** the boat's writes stand down while the map is moved, §4.8 of the dashboard plan

**Item 1** — resolved 2026-09-16: the 300 m band is split by surface — as a dashboard value it follows the GPS position, as a tag it follows the marker — so the sign joins its neighbours in answering what the user is looking at while `inZone300` keeps feeding the cards and the auto-show engine.

**Item 2** — resolved 2026-09-16 on the user's ground: "if I am moving the map, it makes sense to reveal where the zone is if I am looking for them; when the map is GPS-centred the marker is the real zone value". The narrowing therefore keeps the map centre, and its two cases are both right: while following, the marker is the boat, so the reveal reads the same value the dashboard uses; the divergence exists only while the user is deliberately looking elsewhere, which is when a reveal is wanted. Recorded in §4.6 of `xTrack/Navigation/260916_FEAT_PLN_Navigation_dashboard-position-source.md`, with no second selection point added.

**Item 3** — the tag stack fix, written out as seven ordered steps in §5.7 of `xTrack/RegulatedZones/260916_FEAT_PLN_RegulatedZones_tag-stack-trigger.md`: a `markerInZone300` flow in the ViewModel for the band's own answer at the marker, the tag set derived from the Zone categories settings, the marker point fed to the strip and the info text, and the `boatPosition` parameter and its duplicated expression deleted. The composables stay untouched, and it closes on the build plus the device checks — layer off inside the Cap d'Antibes speed zone, and the category toggle. Closed 2026-09-16 — the seven steps are carried by §5.7 and the todo list, not by this cursor.

**Item 4** — the dashboard position selection, written out as six ordered steps in §4.7 of `xTrack/Navigation/260916_FEAT_PLN_Navigation_dashboard-position-source.md`: a pure `dashboardPositionFor(marker, boat, gpsMode)` predicate, the `dashboardPosition` flow built from it, and its use as the shore pipeline's source in place of `_mapCenter`. The predicate is the seam the test in item 5 needs, and the pipeline is the only place that changes — the depth layer keeps its own centre and the narrowing keeps the marker, so only the dashboard moves onto the boat. Closed 2026-09-16 — its six steps are carried by §4.7 and the todo list.

**Item 5** — one test class, `DashboardPositionTest` beside the other map tests (`app/src/test/java/ykws/android/maro/ui/map/`), over the top-level `internal` predicate so no ViewModel, no Compose and no device are involved. Three cases: GPS mode with a fix → the boat; GPS mode before the first fix → the marker; demo mode with a fix available → the marker, which is the case that proves the mode wins over mere availability. It closes on a scoped run (`--tests "*DashboardPositionTest*"`) rather than the full suite, which is known-red for two unrelated classes, and it is the only test this pair of plans adds. Closed 2026-09-16: the spec is settled, and writing it is carried by §4.7 step 5 and the todo list rather than by this cursor.

**Item 9** — resolved 2026-09-16 as a bug, not a wart: the boat's writes into the map centre are not gated on suppression, so a fix arriving during a drag, a wizard freeze or an open drawer overwrites the point the user placed even though the camera stays put. The rule now recorded: while the map has been moved and has not been recentred — by the button or by the resume timer — nothing may grab the centre back. One gate, one home, plus a one-line restore in `recenterNow()`; §4.8 of `xTrack/Navigation/260916_FEAT_PLN_Navigation_dashboard-position-source.md`. Two further defects fall out of it: the wizard's corridor preview and the view saved at next launch were both being moved by the fix.

Closing summary — nine items, four decisions and five carried steps: the band split by surface (tag keeps the marker, dashboard keeps the boat), the auto-reveal keeping the marker, the boat's writes standing down while the map is moved, and the two fixes written out as ordered steps (§5.7 of the tag-stack plan, §4.7 and §4.8 of the dashboard plan) with the test, build, device pass and registration carried by the todo list. Nothing was dropped.

**Level 2 — Date:** 2026-09-16 · **Parent:** 1 · **Active:** 1 · **Closed:** 2026-09-16

1. Band tag gets its own band test at the marker — chosen
2. Band tag follows `inZone300` and moves with the dashboard — dropped

Resolution: the tag is fed its own band value computed at the marker point and produced where the strip is fed, so the composable stays free of position logic; it costs one extra band query per tag frame, at a point the stack already has.

**Level 2 — Date:** 2026-09-16 · **Parent:** 2 · **Active:** 2 · **Closed:** 2026-09-16

1. The narrowing moves onto the boat, same selection as the dashboard — dropped
2. The narrowing keeps the marker — chosen

Resolution: the reveal keeps the map centre; while following that is the boat anyway, and while panning the user is looking for zones, so the reveal follows the eye rather than the hull.
