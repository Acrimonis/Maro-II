# BARO — Step 01: Data Sources Analysis

> **Parent**: [BARO - general concepts.md](plans/BARO - general concepts.md)
> **Status**: Analysis complete — 2026-06-01
> **Purpose**: Exhaustive technical comparison of the two identified SHOM datasets to inform the data ingestion pipeline design.

---

## 1. Source Overview

| Parameter | Litto3D PACA | HOMONIM NM |
|-----------|-------------|------------|
| **Full name** | LITTO3D® PACA 2015 PREPAQUET 1060_6310 | MNT Bathymétrique de façade du Golfe du Lion - Côte d'Azur (Projet Homonim) NM |
| **Producer** | SHOM + IGN (joint) | SHOM |
| **Archive file** | [`1060_6310.7z`](data/1060_6310.7z) | [`MNT_FACADE_GDL-CA_HOMONIM_NM.7z`](data/MNT_FACADE_GDL-CA_HOMONIM_NM.7z) |
| **Metadata file** | [`BATHYMETRIE_LITTO3D_PACA_2015_PREPAQUET_1060_6310.xml`](data/BATHYMETRIE_LITTO3D_PACA_2015_PREPAQUET_1060_6310.xml) | [`MNT_MED100m_GDL_CA_HOMONIM_WGS84_NM_ZNEG.xml`](data/MNT_MED100m_GDL_CA_HOMONIM_WGS84_NM_ZNEG.xml) |
| **Spec document** | [`DC_Litto3D.pdf`](data/DC_Litto3D.pdf) + [`DL_Litto3D.pdf`](data/DL_Litto3D.pdf) + [`Specifications-techniques-Litto3D_v1_0-Doc_v1_5.pdf`](data/Specifications-techniques-Litto3D_v1_0-Doc_v1_5.pdf) | [`Descriptif_Contenu_MNT_facade_2015.pdf`](data/Descriptif_Contenu_MNT_facade_2015.pdf) |
| **License** | Licence Ouverte v1.0 (Etalab) | Licence Ouverte v1.0 (Etalab), attribution: *"SHOM, 2015. MNT Bathymétrique de façade du Golfe du Lion - Côte d'Azur (Projet Homonim)."* |
| **Navigation restriction** | ⚠️ "Non adapté à la navigation" | ⚠️ "Ne pas utiliser pour la navigation" |

> 💡 **ELI16 — Who are SHOM & IGN?** SHOM is the French Navy's map-making service for the sea (like the UK's Admiralty). IGN is the French national geographic institute that makes land maps. They teamed up to create Litto3D — a seamless 3D model of the coastline where land meets sea. Both datasets are free to use (Etalab open license) but explicitly say "do not use for navigation" — meaning they're good enough for our recreational boating/diving app but not certified for ship navigation.

---

## 2. Spatial Parameters

### 2.1 Coordinate Reference Systems

| Parameter | Litto3D PACA | HOMONIM NM |
|-----------|-------------|------------|
| **Horizontal CRS** | RGF93 / Lambert-93 (EPSG:2154) | WGS 84 (EPSG:4326) |
| **Vertical datum** | IGN 1969 — altitude normale (EPSG:5720) | Niveau Moyen des Mers (Mean Sea Level) |
| **Depth sign convention** | Positive = above datum (altitude) | Negative = below MSL (depth) — Z negative down |
| **Vertical unit** | Meters | Meters |

> 💡 **ELI16 — CRS / EPSG / Projection**: Think of a CRS (Coordinate Reference System) as a way to turn the curved Earth into flat numbers on a grid. EPSG codes are just standard IDs for these systems — like ISBN numbers for map projections.
>
> - **WGS84 (EPSG:4326)**: "GPS coordinates" — latitude and longitude in degrees. Your phone's GPS gives you this. It's what our app already uses.
> - **Lambert-93 (EPSG:2154)**: A flat grid in meters, used for French national maps. The X and Y coordinates are distances in meters from a reference point. This is what Litto3D uses because it's better for precise measurements on land.
>
> **Why this matters**: Litto3D says "this point is at X=1,060,500 meters, Y=6,310,300 meters in Lambert-93". Our app needs "lat=43.77°, lon=7.50° in WGS84". We need to convert (mathematical formula) before using Litto3D data. HOMONIM is already in WGS84 — it works directly.

> **⚠️ CRS Mismatch**: Litto3D is in Lambert-93 (projected, meters) while HOMONIM is WGS84 (geographic, degrees). The app operates in WGS84 lat/lon. Litto3D tiles must be reprojected from Lambert-93 → WGS84 during the preprocessing/ingestion pipeline.

> 💡 **ELI16 — Vertical Datum (IGN69 vs MSL)**: "Sea level" isn't the same everywhere — gravity varies, currents pile up water, and different countries define sea level differently.
>
> - **IGN69**: France's official height system — "0 meters" is tied to a physical tide gauge in Marseille harbor.
> - **MSL (Mean Sea Level)**: The global average of sea level measurements.
>
> In the Mediterranean near Côte d'Azur, IGN69 sits about **30–50 cm higher** than MSL. So a depth of "2.0 m" in Litto3D (relative to IGN69) is actually "1.5–1.7 m" relative to MSL — a big deal when your keel needs 2 meters of water! We must apply a correction.

> **⚠️ Datum Mismatch**: IGN69 (orthometric height, relative to geoid at Marseille) ≠ Mean Sea Level. In the Mediterranean near Côte d'Azur, IGN69 is approximately 0.3–0.5 m above MSL. For collision warning (0–5 m), this offset is **significant** and must be corrected.

### 2.2 Geographic Coverage

| Parameter | Litto3D PACA (tile 1060_6310) | HOMONIM NM |
|-----------|-------------------------------|------------|
| **West lon** | 7.4714° | 2.9° |
| **East lon** | 7.5369° | 7.9° |
| **South lat** | 43.7497° | 41.7° |
| **North lat** | 43.7972° | 44.4° |
| **Tile size (approx)** | ~5.3 km × 5.3 km | ~500 km × ~300 km |
| **App coastline zone** | 6.70°–7.31° | ✅ Fully contained |

> 💡 **ELI16 — Tile Naming (1060_6310)**: Litto3D is delivered as puzzle pieces called "dalles" (tiles). Each tile is exactly 1 km × 1 km. The name `1060_6310` means:
> - `1060` = X coordinate 1,060,000 meters in Lambert-93 (the western edge of the tile)
> - `6310` = Y coordinate 6,310,000 meters (the northern edge)
>
> So this tile covers the area from (1,060,000 , 6,310,000) to (1,061,000 , 6,309,000) in Lambert-93 — which happens to be the coastline around Antibes and Golfe-Juan. Our app's Cantons→Menton zone would need tiles like `1048_6298`, `1049_6298`, `1050_6298`, etc. — roughly 50+ of these 1km² squares.

> **⚠️ Critical Finding**: The Litto3D tile `1060_6310` covers ONLY ~28 km² around **Golfe-Juan / Cap d'Antibes** (coordinates 1060_6310 = Lambert-93 km grid: X=1,060,000 / Y=6,310,000). The full PACA Litto3D dataset covers the entire PACA coastline, but we only have **one tile**. To cover the app's coastline zone (Cannes → Menton, ~50 km), we would need **dozens of tiles** (roughly 50+). Each adjacent tile must be downloaded from [diffusion.shom.fr](http://diffusion.shom.fr) or [data.shom.fr](http://data.shom.fr).

> **✅ HOMONIM**: Covers the entire Golfe du Lion → Côte d'Azur facade in a single dataset.

---

## 3. Resolution & Accuracy

### 3.1 Grid Resolution

| Parameter | Litto3D PACA | HOMONIM NM |
|-----------|-------------|------------|
| **Grid spacing** | 1 m (MNT 1m) / 5 m (MNT 5m) | 0.001° (~111 m N-S, ~100 m E-W at 43.5°N) |
| **Grid dimensions (per tile)** | 1000 × 1000 nodes (1 km² tile) | 5001 rows × 2701 cols (full facade) |
| **Point density (semis)** | ≥1 pt/m² (topo), ≥0.04 pt/m² (bathy, 1 pt per 25 m²) | N/A (interpolated grid) |
| **Depth range** | 0 to ~10 m (seaward limit = CB10 isobath) | +10 m to −2844 m |

> 💡 **ELI16 — What "1m resolution" actually means**: Imagine taking a photo of the seabed with a camera that captures one pixel every square meter. Litto3D's 1m grid means you know the depth for every 1m×1m cell — about the size of a bathroom tile. If there's a rock the size of a car on the seabed, Litto3D will see it.
>
> HOMONIM's 100m grid means each "pixel" covers an area the size of a football pitch. It can't see individual rocks or small reefs — it's a smoothed-out, averaged view. Fine for understanding general depth zones, useless for avoiding specific underwater obstacles.
>
> **The ".001° ≈ 100m" rule**: At our latitude (~43.5°N), 0.001° of latitude = ~111 meters (fixed). 0.001° of longitude = ~111 × cos(43.5°) ≈ 81 meters. HOMONIM's 0.001° cells are ~111m tall × ~81m wide.

### 3.2 Accuracy

| Parameter | Litto3D PACA | HOMONIM NM |
|-----------|-------------|------------|
| **Vertical (topo LiDAR)** | EMQ 0.20 m (controlled) | N/A |
| **Vertical (bathy LiDAR)** | 0.50 m @ 95% confidence | N/A |
| **Vertical (SMF)** | 0.40 m @ 95% confidence | N/A |
| **Planimetric (topo LiDAR)** | EMQ 0.50–0.60 m | N/A |
| **Planimetric (bathy LiDAR)** | 2.80 m @ 95% confidence | N/A |
| **Overall vertical accuracy** | Sub-meter in nearshore | < 1% of grid spacing (~1 m) or S-44 order |

> 💡 **ELI16 — Accuracy Numbers Decoded**:
> - **EMQ (Erreur Moyenne Quadratique)** = RMS (Root Mean Square) in English. Think of it as the "typical error." If EMQ = 0.20m, then about 68% of measurements are within ±0.20m of the true depth. It's the standard deviation of errors.
> - **"@ 95% confidence"**: Means "we're 95% sure the real depth is within ±X meters of what we measured." More conservative than EMQ. 0.50m @ 95% = if the data says 5.0m, real depth is between 4.5m and 5.5m (19 times out of 20).
> - **Planimetric**: Horizontal position accuracy — how precisely the X,Y location is known. 2.80m planimetric error means the depth you're reading might actually be from a spot 2.8m away.
>
> **Practical take**: In shallow water (<5m), Litto3D tells you depth within ~0.5m. Your GPS also has ~5–15m error. Combined, if Litto3D says 2.5m and your draft is 2.0m, you should still be cautious — the combined uncertainty could put you at 2.0−0.5 = 1.5m in worst case.

### 3.3 Suitability by Depth Range

| Depth Range | Litto3D | HOMONIM | Recommendation |
|-------------|---------|---------|----------------|
| 0–2 m (ground collision) | ✅ Excellent (1m grid, 0.5m accuracy) | ❌ Too coarse | **Litto3D only** |
| 2–5 m (shallow caution) | ✅ Excellent | ❌ Marginal | **Litto3D only** |
| 5–10 m (anchoring) | ✅ Good (at acquisition limit) | ⚠️ Borderline (~100m cells) | **Litto3D preferred** |
| 10–30 m (recreational diving) | ❌ Out of coverage | ✅ Adequate | **HOMONIM** |
| 30–80 m (technical diving) | ❌ Out of coverage | ✅ Adequate | **HOMONIM** |

---

## 4. Data Format & Structure

### 4.1 Litto3D

**Available formats** (from `DL_Litto3D.pdf`):
| Format | Type | Use |
|--------|------|-----|
| **GRID ASCII (.asc)** | Text grid, header + row-major values | MNT 1m/5m — best for direct parsing |
| **GeoTIFF 32 bits (.tif)** | Binary georeferenced raster | Ready-to-use but needs TIFF library |
| **BIL 32 bits (.bil)** | Binary interleaved raster | Raw binary, needs .hdr companion |
| **XYZ text (.xyz)** | Space-separated `X Y Z` point cloud | Semis de points — direct parsing |
| **LAS 1.2 (.las)** | Binary LiDAR point cloud | Not in standard delivery |

> 💡 **ELI16 — File Format Cheat Sheet**:
> - **GRID ASCII (.asc)**: Just a text file you can open in Notepad. Starts with 6 header lines (number of columns, rows, coordinates, cell size), then rows of numbers. Each number = depth at that grid cell. Easy to parse with any programming language.
> - **NetCDF (.grd)**: A binary format that stores multi-dimensional arrays efficiently. Think of it like a `.zip` for scientific data — compact but needs a library to read.
> - **GeoTIFF (.tif)**: Like a regular .tif image but with GPS coordinates baked in. Each pixel's color value = depth. Needs a GeoTIFF-aware library.
> - **XYZ (.xyz)**: The simplest format — just a list of "X Y Z" lines. No structure, no header. Like a CSV with spaces instead of commas.
> - **GLZ (.glz)**: "lon lat depth" — a sparse point cloud. Only stores points where there's actual data, not a full grid.

**Tile naming**: `LITTO3D_FXX_XXXX_YYYY_MNT_1M_AAAAMMJJ_LAMB93_IGN69.asc`
- `XXXX`/`YYYY` = NW corner in Lambert-93 kilometers
- Example: `1060_6310` = X=1,060,000 m, Y=6,310,000 m

**NODATA value**: `-99999`

> 💡 **ELI16 — NODATA**: When a grid cell has no measurement (e.g., land, or beyond the survey area), it's filled with a magic number — `-99999` for Litto3D. When reading the grid, any `-99999` means "no data here, skip this cell." Always check for this before using a depth value. A depth of `-99999` meters would be… problematic.

**Quality layers** (accompany each MNT tile):
- **SOURCE** (GeoTIFF 8-bit): Identifies the acquisition instrument per node (LiDAR topo, LiDAR bathy, SMF, interpolated, etc.)
- **DISTANCE** (GeoTIFF 8-bit): Distance in meters from nearest measured point (0 = directly measured, 255 = no data)

> 💡 **ELI16 — Quality Layers**: Litto3D doesn't just give you depths — it also tells you *how* each depth was measured and *how reliable* it is.
> - **SOURCE**: Was this cell measured by a laser from an airplane (LiDAR), or by a boat sonar (SMF), or was it computer-guessed (interpolated)? A depth from a direct laser measurement is more trustworthy than one that was estimated from far-away neighbors.
> - **DISTANCE**: How far is this grid cell from the nearest actual measurement? Distance = 0 means "we measured right here." Distance = 50 means "the nearest real measurement was 50 meters away, so we guessed." Useful for showing a "reliability fade" on the map.

### 4.2 HOMONIM NM

**Available formats**:
| Format | Type | Use |
|--------|------|-----|
| **NetCDF/GRD (.grd)** | Binary GMT format, 32-bit float | Direct grid access |
| **BAG (.bag)** | HDF5-based binary | Rich metadata, needs HDF5 library |
| **ESRI ASCII (.asc)** | Text grid with header | Easiest to parse |
| **GLZ (.glz)** | Space-separated `lon lat depth` | Sparse point cloud, no header |

**ESRI ASCII header** (expected):
```
ncols 2701
nrows 5001
xllcorner 2.9     (or xllcenter)
yllcorner 41.7
cellsize 0.001
nodata_value NaN  (or -99999)
```

**Depth convention**: Z negative = below MSL. In the XML: `minimumValue = -2844`, `maximumValue = 10`.

---

## 5. Data Origins & Lineage

### 5.1 Litto3D Acquisition (PACA 2015)
- **Period**: 30 Sep–2 Oct 2007, 5–7 Oct 2010, 30 Apr 2012–12 Jul 2013
- **Sensors**:
  - SHOALS 3000 (bathy LiDAR)
  - HawkEye IIb (bathy LiDAR)
  - LADS MkIII (bathy LiDAR)
  - RIEGL VQ-820-G (mixed topo-bathy LiDAR, for 0–3 m shallows)
  - ALTM31000AE-IGN (topo LiDAR)
- **Fusion**: SHOM delivers bathy data → IGN merges with topo data at 10m inland from HistoLitt coastline → joint validation

> 💡 **ELI16 — How LiDAR Bathymetry Works**: An airplane flies over the coast shooting two laser beams at the water:
> 1. A **green laser** (532nm wavelength) that penetrates water and bounces off the seabed
> 2. An **infrared laser** (1064nm) that bounces off the water surface
>
> The depth = (time infrared returned − time green returned) × speed of light in water ÷ 2.
>
> This only works in clear water up to ~70m deep. In murky water or beyond 10m, the green laser gets absorbed. That's why Litto3D stops at ~10m — past that, they use **SMF (Sondeur Multifaisceaux)** — a boat with a sonar array that maps the seabed with sound waves (like a very fancy fish finder).
>
> **Why multiple sensors?** Each sensor type has a sweet spot: LiDAR is fast and cheap for shallow clear water, SMF works in deep or murky water but is slow and expensive (needs a boat), mixed topo-bathy LiDAR bridges the gap at the shoreline where traditional methods struggle.

### 5.2 HOMONIM Lineage
- **Primary sources**: BDBS (SHOM's bathymetric database), Litto3D®, EMODnet, GEBCO, NGDC
- **Processing pipeline**:
  1. Collect & evaluate multi-source, multi-epoch data
  2. Homogenize vertical and horizontal references
  3. Interpolation adapted to heterogeneous point density
  4. Quality assessment and documentation
- **Bias policy**: No systematic bias — "most probable" bathymetry (unlike navigation charts which err shallow)

> 💡 **ELI16 — EMODnet & GEBCO**: HOMONIM is a composite — it blends data from multiple sources:
> - **BDBS**: SHOM's own database of French naval surveys (most reliable)
> - **Litto3D**: High-res coastal data (best nearshore detail)
> - **EMODnet**: A European Union project that collected bathymetry from all member states
> - **GEBCO**: A global ocean floor map (lowest resolution, fills gaps where no survey exists)
>
> The key design choice: HOMONIM aims for the "most probable" depth (unbiased), unlike navigation charts which deliberately show depths *shallower* than reality for safety. This is better for scientific modeling but means we can't trust it for collision avoidance.

---

## 6. Data Volume Estimates

### 6.1 Litto3D — Per Tile (1 km²)

| Format | 1m MNT | Semis de points |
|--------|--------|-----------------|
| Uncompressed | ~7 MB (ASCII) / 4 MB (GeoTIFF) | ~210 MB (XYZ text) |
| Compressed (7z) | ~1–2 MB | ~30 MB |

### 6.2 HOMONIM — Full Facade

| Format | Estimated size |
|--------|---------------|
| ESRI ASCII (.asc) | ~55 MB (5001 × 2701 values) |
| NetCDF (.grd) | ~55 MB (32-bit float) |
| GLZ (.glz) | Variable (depends on land/sea ratio) |

### 6.3 APK Budget Impact
- **HOMONIM full facade**: ~55 MB uncompressed → ~10–15 MB compressed in APK. Acceptable.
- **Litto3D full PACA coastline**: ~50–100 tiles × ~2 MB each (compressed ASC) = **100–200 MB**. Too large for APK.
  - **Mitigation**: On-demand tile download from SHOM servers, or pre-package only the tiles covering the app zone after reprojection.

> 💡 **ELI16 — Why APK Size Matters**: Google Play limits APK size to 200 MB (with extensions up to 2 GB). Users on slow connections or limited data plans won't download a 200 MB app. Our current app is tiny — adding 100+ MB of depth data would be the dominant cost.
>
> **The HOMONIM advantage**: At ~15 MB compressed, it's like adding a few high-res photos to the app. Entirely reasonable to bundle directly.
>
> **The Litto3D problem**: 100–200 MB for full coverage forces us to choose: bundle selected tiles, download on-demand, or stream from a server. For an offline-first boating app, streaming is impractical (no cell signal offshore). The likely solution: pre-process Litto3D tiles into a compact Protobuf binary format (same pattern as coastline cache), keep only the tiles for our zone, and accept the APK size increase.

---

## 7. Key Technical Decisions Required

| # | Decision | Options | Impact |
|---|----------|---------|--------|
| D1 | **Litto3D tile coverage**: Download all PACA tiles vs. single representative tile | Full coverage vs. prototype | Determines collision warning viability across the full zone |
| D2 | **Reprojection strategy**: Pre-process offline vs. on-device at first launch | Offline (build-time script) vs. runtime | Runtime reprojection cost vs. APK complexity |
| D3 | **Vertical datum harmonization**: Apply static offset vs. ignore the difference | IGN69 → MSL offset (~0.3–0.5 m) | Critical for <2 m collision threshold |
| D4 | **Storage format for app**: Convert to Protobuf binary grid (like coastline) vs. keep as ASCII/GeoTIFF | Protobuf (aligned with existing architecture) vs. raw | Consistency with coastline cache pattern |
| D5 | **HOMONIM downsampling**: Use native 0.001° (~100 m) or downsample for mobile | Native vs. 0.002° (~200 m) | Memory/rendering trade-off |
| D6 | **Data packaging**: Embed in APK vs. download-on-first-launch vs. served from remote WMS | APK embedded vs. OTA | APK size vs. offline capability |

---

## 8. Summary Matrix

| Criterion | Litto3D PACA | HOMONIM NM | Winner |
|-----------|-------------|------------|--------|
| Resolution | ⭐⭐⭐⭐⭐ (1 m) | ⭐⭐ (100 m) | Litto3D |
| Coverage breadth | ⭐ (one 5km tile) | ⭐⭐⭐⭐⭐ (entire facade) | HOMONIM |
| Depth range | 0–10 m | 0–2800 m | HOMONIM |
| Accuracy @ <10 m | ⭐⭐⭐⭐⭐ (0.5 m) | ⭐⭐ (~1 m, but at 100 m grid) | Litto3D |
| CRS compatibility | ⭐⭐ (Lambert 93) | ⭐⭐⭐⭐⭐ (WGS84) | HOMONIM |
| App-ready format | Needs reprojection + tile assembly | Near-ready (just parsing) | HOMONIM |
| Ground collision fit | ⭐⭐⭐⭐⭐ Perfect | ⭐ Useless (too coarse) | Litto3D |
| Dive site fit | ❌ Doesn't reach dive depths | ⭐⭐⭐⭐ Good | HOMONIM |
| Data volume | Large if full coverage | Moderate | HOMONIM |

---

## 9. Recommended Data Strategy

```
┌─────────────────────────────────────────────────────┐
│                  DEPTH RESOLUTION                    │
│                                                     │
│  0m ─┬── Litto3D (1m grid, 0.5m accuracy) ── 10m   │
│      │   ├─ Ground collision (0-5m)                 │
│      │   └─ Anchoring sites (2-10m)                 │
│      │                                              │
│  10m ─┼── HOMONIM (100m grid, ~1m accuracy) ────►  │
│      │   ├─ Dive sites (10-80m)                     │
│      │   ├─ Overall depth context                   │
│      │   └─ Isobath generation                      │
│      │                                              │
│ 80m  ─┴── (beyond functional requirements)          │
└─────────────────────────────────────────────────────┘
```

**Two-tier depth engine**:
1. **Nearshore layer (Litto3D)**: High-res 1m grid for 0–10 m. Covers collision + anchoring. Requires tile expansion beyond the single tile we have.
2. **Offshore layer (HOMONIM)**: Coarser 100m grid for full facade. Covers dive sites + regional context + contour generation.

> 💡 **ELI16 — Why Two Layers?**: It's like Google Maps — you have high-res satellite imagery when zoomed in, but lower-res imagery for the big picture. Litto3D is our "zoom level 18" for the dangerous shallow zone where precision matters. HOMONIM is our "zoom level 8" for the big picture offshore context. We query Litto3D first (if available for that position), and fall back to HOMONIM. This is the same pattern games use for terrain — high-detail near the player, lower detail at distance.

**Priority order for implementation**:
1. First: Ingest HOMONIM → get depth map + contours rendering (simpler, single file, WGS84-native)
2. Second: Ingest Litto3D tiles → enable high-precision collision warning + anchoring overlay
3. Third: Merge both sources with priority logic (Litto3D wins where data exists, HOMONIM fills gaps)
