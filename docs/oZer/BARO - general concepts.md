# BARO — Bathymetry & Depth Feature for Maro II

> **Feature codename**: BARO (from "bathymétrie")
> **Status**: Requirements & data discovery phase
> **Last updated**: 2026-06-01

---

## 1. Overview

BARO extends Maro II with **real-time depth information** (0–80 m) overlaid on the nautical map. The feature targets three use cases:

| Use Case | Depth Range | Priority | Data Source |
|----------|-------------|----------|-------------|
| **Ground collision warning** | 0–5 m | Critical (safety) | Litto3D PACA |
| **Anchoring site identification** | 2–10 m | High | Litto3D PACA |
| **Scuba diving site identification** | 10–80 m | Medium | HOMONIM NM |

---

## 2. Functional Goals

### 2.1 Depth Under Boat (DUB)
- Real-time numeric display of water depth at the vessel's GPS position
- Update rate: every GPS fix (~1 Hz) or on significant position change (>5 m)
- Display in meters with one decimal place (e.g., "12.4 m")

### 2.2 Depth Map Overlay
- Color-coded depth raster/heatmap rendered on the visible OSMdroid map viewport
- **Precision gradient**: higher resolution near the boat position, coarser further away
- Re-renders on map pan/zoom and GPS position changes

### 2.3 Depth Contours (Isobaths)
- Isobath lines at meaningful depth thresholds:
  - 2 m, 5 m, 10 m, 20 m, 30 m, 50 m, 80 m
- Rendered as polylines on the OSMdroid map (similar to existing coastline rendering)
- Styled distinctly from coastline (dashed, thinner, different color)

### 2.4 Ground Collision Warning
- Configurable depth threshold (default: draft + safety margin, e.g., 2 m)
- Visual + optional audible alert when DUB approaches threshold
- Predictive: uses course-over-ground + speed to estimate time-to-collision

### 2.5 Depth Color Coding
- Color ramp from red (shallow/danger) → yellow (caution) → blue (deep)
- Anchoring zone highlight: 2–10 m in green tint
- Dive site highlight: 10–40 m in distinct palette

---

## 3. Current App Architecture (Relevant Context)

### 3.1 Mapping Engine
- **OSMdroid** (`org.osmdroid`) — OpenStreetMap tile-based Android map library
- Map rendered via `AndroidView` composable wrapping `MapView`
- Overlays: `Polyline` for coastline rendering

### 3.2 Spatial Foundation (Already Built)
| Component | Spring/Java Analog | Role |
|-----------|-------------------|------|
| [`SpatialOperations`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt) | Utility bean (`@Component`) | Haversine distance, point-to-segment distance, projection math |
| [`CoastlineRepository`](app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt) | `@Service` / `@Repository` | Data loading, caching, spatial queries (`isOnWater`, `distanceToCoast`) |
| [`CoastlineViewModel`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) | `@Controller` with `StateFlow` ≈ Reactor `Mono`/`Flux` | UI state bridge, reactive state management |
| `CoastlinePoint` | Entity with pre-computed fields | Carries `xM`, `yM`, `edgeDxM`, `edgeDyM` — pre-projected Cartesian coords |

### 3.3 Key Spatial Patterns (Reusable for BARO)
- **Batch projection**: All points projected to local Cartesian (meters) once upfront at a reference latitude. Queries need only 1 trig call (project GPS → Cartesian), then pure 2D math.
- **Edge vectors**: Pre-computed `(edgeDxM, edgeDyM)` per segment for O(1) distance queries.
- **Data pipeline**: Fetch → Assemble → Filter → Clip → Simplify → Orient → Serialize → Cache
- **Protobuf serialization**: Already integrated (`protobuf-javalite`), used for coastline cache format.

### 3.4 Geographic Bounding Box
- Current coastline zone: `lon ∈ [6.70, 7.31]` (Cannes → Menton, ~50 km of coast)
- BARO data must cover this zone and potentially extend offshore to the 80 m isobath.

---

## 4. Data Sources

### 4.1 Discovered Sources

| Archive | Source | Resolution | Depth Range | Coverage | Role |
|---------|--------|------------|-------------|----------|------|
| `1060_6310.7z` | SHOM Litto3D PACA | 1 m (LIDAR) | 0–10 m | Coastal strip, very nearshore | Ground collision + anchoring |
| ~~`MNT_FACADE_GDL-CA_HOMONIM_NM.7z`~~ | ~~SHOM HOMONIM NM~~ | ~~~100 m~~ | ~~0–80+ m~~ | ❌ **Dropped 2026-06-01** — too coarse (100m) for dive site identification. See [alternative sources](plans/BARO - alternative sources finer than HOMONIM.md). |

> **Note**: HOMONIM files removed from `data/`. We are now researching finer alternatives: Sentinel-2 SDB (10m, 0–25m), SHOM coastal MNT (25–50m), and Navionics SonarChart (1–30m).

### 4.2 Technical Specifications (Confirmed from Metadata)

| Parameter | Litto3D PACA | HOMONIM NM |
|-----------|-------------|------------|
| **Horizontal CRS** | RGF93 / Lambert-93 (EPSG:2154) | WGS 84 (EPSG:4326) |
| **Vertical datum** | IGN 1969 (EPSG:5720) — altitude normale | Niveau Moyen des Mers (Mean Sea Level) |
| **Depth convention** | Positive = altitude (above IGN69) | Negative = depth (below MSL) |
| **Grid resolution** | 1 m (MNT 1m) | 0.001° (~100 m at 43.5°N) |
| **Vertical accuracy** | 0.50 m @ 95% (bathy LiDAR) | < 1% of grid spacing (~1 m) |
| **Formats available** | GRID ASCII (.asc), GeoTIFF (.tif), BIL, XYZ, LAS | ESRI ASCII (.asc), NetCDF (.grd), BAG (.bag), GLZ (.glz) |
| **NODATA value** | `-99999` | `NaN` or `-99999` |
| **Tile size** | 1000 m × 1000 m (1 km² tiles) | Single file, 5001 × 2701 nodes |
| **Depth range** | 0 to ~10 m (seaward limit: CB10 isobath) | +10 m to −2844 m |

- Both datasets from **SHOM** under Licence Ouverte v1.0 (Etalab)
- Both carry "Ne pas utiliser pour la navigation" restriction
- Litto3D spec documents: DC_Litto3D.pdf, DL_Litto3D.pdf, Specifications-techniques-Litto3D_v1_0-Doc_v1_5.pdf
- HOMONIM spec: Descriptif_Contenu_MNT_facade_2015.pdf

### 4.3 Critical Data Issues

| Issue | Detail | Severity |
|-------|--------|----------|
| **CRS mismatch** | Litto3D = Lambert-93 projected (meters), HOMONIM = WGS84 geographic (degrees). Litto3D must be reprojected to WGS84 for the app. | High |
| **Vertical datum offset** | IGN69 vs MSL differ by ~0.3–0.5 m in this region. Significant for < 2 m collision threshold. | High |
| **Litto3D tile coverage gap** | The tile `1060_6310` covers only ~28 km² around Antibes/Golfe-Juan. To cover the full app coastline zone (Cannes→Menton), dozens more tiles are needed from SHOM. | Critical |
| **Depth sign convention** | Litto3D: positive = altitude; HOMONIM: negative = depth. Must normalize to a single convention. | Medium |

See [BARO - Step 01 - Data sources discussion](plans/BARO - Step 01 - Data sources discussion.md) for exhaustive analysis.

---

## 5. Architectural Approach (Preliminary)

### 5.1 Data Layer
```
BathymetryRepository
├── Litto3DSource      (0–10 m, high-res, nearshore)
├── HomonimSource      (0–80 m, coarser, offshore)
└── DepthInterpolator  (merges sources, handles priority)
```

- **BathymetryRepository** ≈ a Spring `@Service` combining two data sources
- Offline-first: all data pre-packaged and processed at build time or on first launch
- Grid-based spatial index for O(1) depth lookup at a GPS position

### 5.2 ViewModel Layer
```
BathymetryViewModel
├── depthUnderBoat: StateFlow<DepthReading>
├── depthMap: StateFlow<DepthGrid>
├── contours: StateFlow<List<IsobathLine>>
├── warningState: StateFlow<CollisionWarning>
└── colorScheme: StateFlow<DepthColorMap>
```

### 5.3 UI Layer (Compose + OSMdroid)
- **Depth indicator**: Composable overlay (bottom bar or floating widget) showing DUB
- **Depth raster**: OSMdroid `GroundOverlay` or tiled heatmap
- **Contours**: OSMdroid `Polyline` overlays (same pattern as coastline)
- **Warning**: Compose `AlertDialog` or persistent banner

---

## 6. Discussion & Decision Log

| Date | Topic | Decision / Outcome |
|------|-------|--------------------|
| 2026-06-01 | Feature kickoff | BARO feature scope defined; data discovery initiated |
| 2026-06-01 | HOMONIM dropped | 100m resolution too coarse for dive site identification. Files removed from data/. |
| 2026-06-01 | SHOM Levés bathymétriques abandoned | All post-2005 surveys in our zone are transit campaigns (Brest→Indian Ocean) with ~100m-spaced single-beam track lines. Not viable for diving. Data files purged. |
| 2026-06-02 | EMODnet-style grid discovered | `data/Mean depth in color/` contains a ~100m gridded bathymetry covering Saint-Tropez→Menton (lat 43.28–43.75°, lon 6.61–7.45°) with rich per-cell metadata (value_count, stdev, interpolation_flag). ~358K cells, JSON format. |

---

## 7. Related Documents

- [BARO - Step 01 - Data sources discussion](plans/BARO - Step 01 - Data sources discussion.md) *(pending)*
- [BARO - gathering of data from public publications](plans/BARO - gathering of data from public publications.md) *(pending)*
- [Phase 1 Complete Summary](plans/phase1-complete-summary.md) — existing coastline pipeline reference
- [Coastline migration design](plans/coastline-migration-design.md) — Protobuf cache pattern
- [Distance to shore](plans/distance-to-shore.md) — spatial query patterns

---

## 8. Open Questions

1. **Vertical datum alignment**: Litto3D (likely IGN69 orthometric) vs HOMONIM (likely LAT). Must normalize to a single chart datum.
2. **Data format**: Need to extract `.7z` archives to confirm exact format (GeoTIFF, XYZ, DTED, NetCDF?).
3. **Grid resolution trade-off**: How to balance APK size vs. precision? Tiling? On-device downsampling?
4. **Tidal correction**: Real-time tide adjustment for depth readings? Or static datum?
5. **Rendering performance**: OSMdroid `GroundOverlay` vs custom tiled rendering for depth heatmap.
