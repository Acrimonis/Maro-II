---
name: Route
status: active
created: 2026-08-16 10:44
modified: 2026-08-16 11:46
active_subfeature: none
---

# Feature: Route

**Description:**
Set a destination point on the map and have the app compute and trace the fastest water-constrained route from the boat's current GPS position. The route always stays on water and minimises time spent inside speed-limit zones: it goes around a zone when that is faster, takes the shortest crossing when the destination lies inside the 300 m coastal band, and the shortest exit when starting inside it.

## Subfeatures

### navigation-mesh  [ ]

**Focus:** Prebake an adaptive navigation mesh (constrained Delaunay triangulation) for the corridor and serialize it as a bundled protobuf `.bin`; the app only loads and searches it. All builder code lives in `src/test` (prebake) so it can use `testImplementation` geometry libraries.

#### Todos
- [ ] Build planar overlay of speed zones + 300 m contour into disjoint cost regions (min of applicable limits per region)
- [ ] Generate the 300 m band at bake time from the analytic predicate isWater && distanceToCoast ≤ 300 (not from SpeedZone data)
- [ ] Derive a node-count budget from the ≤ 500 ms target before triangulating
- [ ] Build constrained Delaunay triangulation of coastline + cost-region boundaries (JTS for overlay/cleaning/buffer + poly2tri for triangulation; prebake-only testImplementation deps)
- [ ] Integrate edge cost = length ÷ min(cruiseSpeed, zoneLimit) across crossed regions
- [ ] Ensure narrow passages and harbour entrances stay connected in the mesh
- [ ] Serialize mesh to protobuf `.bin`; output to data/app-assets/route/<region>.bin
- [ ] Consume the same regulated-zones `.bin` the app draws (no re-fetching SHOM)

#### Rules
- App is a pure consumer: all mesh construction at build time (JUnit gated by -Dmaro.prebake), on-device search only
- Region = BuildConfig.REGION_LON_WEST/EAST (6.70–7.55°E) + REGION_ID (nice-menton); N/S = coast + 6 NM seaward (matches depth)
- Reserve a forbidden/infinite-cost sentinel for future prohibited zones
- Bake-time geometry is file-based: RouteGeometry's prebake implementation reads coastline/regulated-zone `.bin` files directly via serializers (runnable in a JVM test)

#### Mesh schema (protobuf)
- `RoutePoint(lat, lon)`
- `RouteNode(id, pointIdx, adjacency)`
- `RouteEdge(from, to, lengthM, crossedRegions)` — stores length, not time
- `RouteMesh(regionId, bbox, nodes, edges)`
- `time = lengthM ÷ min(cruiseSpeed, zoneLimit)` is recomputed at search time, so the 3–40 kn cruise slider never requires re-baking

### route-search  [ ]

**Focus:** On-device fastest-path search over the prebaked mesh.

#### Todos
- [ ] A* / Dijkstra over mesh edges with time-based edge cost
- [ ] effectiveSpeed = min(cruiseSpeed, zoneLimit); cruise speed 20 kn default, user-configurable
- [ ] Boundary crossings cost at min(limitA, limitB); units KNOTS_TO_MPS (own copy in RouteConfig)
- [ ] Snap start (GPS fix, or map centre in demo mode) and destination to nearest reachable water node
- [ ] Graceful no-route result; reject/clamp positions outside the corridor
- [ ] Cancellable search on Dispatchers.Default; return a ready polyline to the main thread
- [ ] Confirm the ≤ 500 ms worst-case target on-device (JVM assertion already runs in the prebake test)

#### Rules
- Kotlin coroutines/Flow only
- Mesh load = stream-deserialize on Dispatchers.Default (matches depth/coastline precedent; not memory-mapped)

### destination-ui  [ ]

**Focus:** Destination input, route preview, and overlay.

#### Todos
- [ ] Long-press on the map (via MapEventsOverlay, never setOnTouchListener) drops a destination pin
- [ ] Confirmation dialog shows start (auto = boat position), destination, distance + ETA
- [ ] While the dialog is shown, paint the preview route polyline and zoom the map to fit it
- [ ] Route / Cancel buttons; dialog ✕ = dismiss draft (same as Cancel); panel ✕ = clear a confirmed route; empty-map tap clears nothing
- [ ] Render a visible destination marker pin alongside the polyline
- [ ] Active-route panel shows route age; manual Recompute re-runs from the current position
- [ ] Menu-drawer entry to open the destination picker; cruise-speed control in Settings

#### Rules
- One-shot compute for MVP; live re-planning deferred
- Mode-entry + cruise-speed UI integrate into the menu drawer + Settings (accepts extra shared-file edits)
- MapEventsOverlay.longPressHelper yields a GeoPoint directly; pin/polyline/zoom-to-fit are geo-native osmdroid overlays — no manual screen↔geo projection

## Isolation Design

**Principle:** all Route code lives under `ykws.android.maro.route/`. Three deliberate shared-file edits:
1. `MapScreen.kt` — a single `RouteHost(mapView, boatPosition)` call.
2. Menu drawer (`Ui_Menu`/`OverlayLayer`) — one entry to open the destination picker.
3. Settings (`Ui_Settings`/`SettingsOverlay`) — one cruise-speed control section.

**Collision surfaces:**

| Shared file | Risk | Strategy |
|---|---|---|
| `MapScreen.kt` (~5800 lines) | highest | single `RouteHost()` call |
| `OverlayLayer.kt` / menu drawer | mode-entry | one menu item wired to a Route callback |
| `SettingsOverlay` / `AppSettings` | cruise speed | one self-contained settings section reading/writing `RouteSettings` |
| `AppConfig.kt` (~780 lines) | constants | own `RouteConfig` in `route/` |
| `MapOverlayRenderer.kt` | overlay ordering | Route manages its own osmdroid polyline/pin from the `MapView` reference |
| `spatial/` + `data/regulation` | read coupling | read-only `AppSpatialAdapter` implements `RoutePointQueries` + `RouteGeometry` |

**Package layout — main (pure Kotlin, ships in APK):**
```
ykws.android.maro.route/
  RouteConfig.kt
  model/   RoutePoint.kt, RouteMesh.kt, RouteNode.kt, RouteEdge.kt, RouteResult.kt
  data/    RouteMeshRepository.kt, RouteSettings.kt
  search/  RoutePointQueries.kt, RouteGeometry.kt, AppSpatialAdapter.kt, RouteSearch.kt
  ui/      RouteViewModel.kt, RouteHost.kt, RouteOverlay.kt
```

**Package layout — prebake (src/test, never in APK):**
```
app/src/test/java/ykws/android/maro/route/prebake/
  RouteMeshBuilder.kt, RouteMeshPrebakeTest.kt
```

**Decoupling rules:**
1. Route depends only on `data/model/LatLng` and its own `RoutePointQueries`/`RouteGeometry` interfaces — with `AppSpatialAdapter` as the single sanctioned importer of spatial/regulation code.
2. `AppSpatialAdapter` is the only file importing `CoastlineSpatialIndex` / `SpeedZoneIndex` / `RegulatedZonesRepository`; read-only, Context-based, runtime-only.
3. No Route constant, setting, or UI state goes into `AppConfig`, `AppSettings`, or shared ViewModels.
4. All Route runtime state lives in `RouteViewModel` (StateFlow).
5. Tap capture via osmdroid `MapEventsOverlay`, never `setOnTouchListener`.

**Interaction flow (state):**
```
Idle → long-press → Draft (pin + preview route)
Draft → ✕ / Cancel → Idle
Draft → Route → Confirmed
Confirmed → Recompute → Confirmed (from current GPS)
Confirmed → ✕ (panel) / Cancel → Idle
```

**Loading + serialization:**
- Mesh load: stream-deserialize on `Dispatchers.Default` (depth/coastline precedent; not memory-mapped).
- Serialization: protobuf-javalite; output `data/app-assets/route/<region>.bin` (region id from BuildConfig).
- Prebake: JUnit gated by `-Dmaro.prebake=true`; `tools/bake-route.bat`; JTS + poly2tri as `testImplementation` (prebake-only, never in the APK).

## Todos
- [ ] Models + protobuf mesh schema + RoutePointQueries/RouteGeometry interfaces
- [ ] RouteMeshBuilder (src/test, JTS + poly2tri: planar overlay + CDT + edge costing) + node-count budget
- [ ] RouteMeshPrebakeTest (JVM A* benchmark assertion before serializing) + tools/bake-route.bat
- [ ] RouteMeshRepository (stream-deserialize on Dispatchers.Default)
- [ ] RouteSearch (A* over mesh, time-based cost) + on-device benchmark confirmation (≤ 500 ms)
- [ ] AppSpatialAdapter (Context-based runtime implementation of the two interfaces)
- [ ] RouteSettings (own prefs, cruise speed 3–40 kn slider, default 20)
- [ ] RouteViewModel (StateFlow: draft destination, preview route, confirmed route)
- [ ] RouteHost + RouteOverlay (long-press, confirmation dialog, paint + zoom-to-fit, pin, clear, route age + Recompute)
- [ ] Wire MapScreen hook + menu drawer entry + Settings cruise-speed section

## Rules
- Out of scope MVP: depth/draft avoidance, prohibited zones (ACCESS_PROHIBITED etc.), live re-planning
- Code isolation: all Route code lives under `ykws.android.maro.route/`; shared-file edits limited to MapScreen.kt (RouteHost hook), the menu drawer entry, and the Settings cruise-speed section

## Key Files
- `app/src/main/java/ykws/android/maro/route/RouteConfig.kt` — NEW. Route-owned constants (cruise speed default, KNOTS_TO_MPS); region bounds read from BuildConfig
- `app/src/main/java/ykws/android/maro/route/model/` — NEW. RoutePoint, RouteMesh, RouteNode, RouteEdge, RouteResult
- `app/src/main/java/ykws/android/maro/route/search/RoutePointQueries.kt` — NEW. Runtime interface: isWater, speedLimitKn, distanceToCoastM
- `app/src/main/java/ykws/android/maro/route/search/RouteGeometry.kt` — NEW. Bake interface: coastline segments, zone rings, band rings
- `app/src/main/java/ykws/android/maro/route/search/AppSpatialAdapter.kt` — NEW. Context-based read-only adapter over CoastlineSpatialIndex/SpeedZoneIndex/RegulatedZonesRepository
- `app/src/main/java/ykws/android/maro/route/search/RouteSearch.kt` — NEW. On-device A* over the mesh
- `app/src/main/java/ykws/android/maro/route/data/RouteMeshRepository.kt` — NEW. Stream-deserialize mesh `.bin` on Dispatchers.Default
- `app/src/main/java/ykws/android/maro/route/data/RouteSettings.kt` — NEW. Own SharedPreferences, cruise speed 3–40 kn slider, default 20
- `app/src/main/java/ykws/android/maro/route/ui/RouteViewModel.kt` — NEW. StateFlow: destination draft, preview route, confirmed route
- `app/src/main/java/ykws/android/maro/route/ui/RouteHost.kt` — NEW. Hook composed in MapScreen; long-press capture, polyline/pin add-remove, zoom-to-fit
- `app/src/main/java/ykws/android/maro/route/ui/RouteOverlay.kt` — NEW. Destination pin + polyline + confirmation dialog + age/Recompute
- `app/src/test/java/ykws/android/maro/route/prebake/RouteMeshBuilder.kt` — NEW. Prebake: planar overlay + CDT + edge costing (JTS + poly2tri)
- `app/src/test/java/ykws/android/maro/route/prebake/RouteMeshPrebakeTest.kt` — NEW. Prebake test gated by `-Dmaro.prebake=true` (includes JVM A* benchmark assertion)
- `tools/bake-route.bat` — NEW. Bake script
