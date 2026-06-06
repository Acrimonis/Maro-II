# BARO — Sentinel-2 Satellite-Derived Bathymetry (SDB) Guide

> **Parent**: [BARO - general concepts.md](plans/BARO - general concepts.md)
> **Status**: Research & access guide — 2026-06-02
> **Purpose**: How to obtain 10m-resolution bathymetry (0–25m) for the Côte d'Azur from free Sentinel-2 satellite imagery.

---

## 1. What is SDB?

> 💡 **ELI16 — How a satellite measures depth**: Sentinel-2 is a pair of European Space Agency satellites that photograph the entire Earth every 5 days. They capture light in 13 color bands — from deep blue to infrared. When sunlight hits the seafloor and bounces back to the satellite, different wavelengths get absorbed by the water column at different rates:
>
> - **Blue light (490 nm)**: Penetrates deepest — can see bottom at 20–25m in clear water
> - **Green light (560 nm)**: Penetrates moderately — good to 10–15m
> - **Red light (665 nm)**: Absorbed quickly — only sees bottom in <5m
>
> An SDB algorithm takes the ratio of blue to green reflectance and mathematically solves for depth. It's the same physics that makes shallow water look turquoise and deep water look dark blue — but quantified.

| Parameter | Value |
|-----------|-------|
| **Satellite** | Sentinel-2A & 2B (ESA/Copernicus) |
| **Resolution** | 10 m (visible + NIR bands) |
| **Revisit time** | 5 days (both satellites combined) |
| **Max depth** | 15–25 m in Mediterranean (water clarity dependent) |
| **Accuracy** | 1–2 m RMS in ideal conditions |
| **Coverage** | Global coastal, free (Copernicus Open Access) |
| **Format** | GeoTIFF (imagery), NetCDF/GeoTIFF (SDB product) |

---

## 2. Three Paths to SDB Data

### Path A: Pre-computed SDB from EMODnet (easiest)

EMODnet has an experimental SDB layer derived from Sentinel-2.

🔗 **Links**:
- [EMODnet SDB Portal](https://emodnet.ec.europa.eu/en/satellite-derived-bathymetry)
- [EMODnet SDB Map Viewer](https://portal.emodnet-bathymetry.eu/?F=SDBS2CSatelliteDerivedBathymetry)

**Steps:**
1. Open the EMODnet map viewer
2. Enable the "Satellite Derived Bathymetry" layer
3. Navigate to Côte d'Azur (lat ~43.5°, lon ~7°)
4. Check if SDB coverage exists for our zone
5. Download via the portal or WCS (Web Coverage Service)

⚠️ **Caveat**: SDB coverage is not global — it's produced region by region. The Mediterranean may or may not be covered yet.

### Path B: Copernicus Marine Service (pre-computed, European focus)

The Copernicus Marine Service may provide SDB as part of their coastal products.

🔗 **Links**:
- [Copernicus Marine Data Store](https://data.marine.copernicus.eu/products)
- Search for: "satellite derived bathymetry" or "SDB"

**Steps:**
1. Register for a free Copernicus Marine account
2. Search the catalog for "bathymetry" products
3. Filter by region: Mediterranean Sea
4. Look for "OCEANCOLOUR_MED_BGC_L4" or similar coastal products

### Path C: DIY — Compute SDB from raw Sentinel-2 imagery (most flexible)

If pre-computed SDB doesn't cover our zone, we can generate it ourselves using open-source tools.

🔗 **Links**:
- [Copernicus Data Space (Sentinel-2 Browser)](https://dataspace.copernicus.eu/browser/)
- [Sentinel Hub EO Browser](https://apps.sentinel-hub.com/eo-browser/) — easier search interface
- [AROSICS](https://github.com/GFZ/arosics) — image co-registration
- [SDB Tool (GitHub)](https://github.com/oxfordmmm/sdb_tool) — open-source SDB processor

**Steps:**

#### C1. Find cloud-free Sentinel-2 imagery
1. Go to [Sentinel Hub EO Browser](https://apps.sentinel-hub.com/eo-browser/)
2. Search for "Nice, France" or "Cannes, France"
3. Set date range: summer months (June–September) for best water clarity + low cloud
4. Filter: Sentinel-2 L2A (atmospherically corrected)
5. Max cloud coverage: <5%
6. Find images with calm sea state (no whitecaps — they confuse the algorithm)

#### C2. What to look for in a good image
- ☀️ Low sun glint (avoid images with bright specular reflections on water)
- 🌊 Calm sea (no waves/whitecaps — they reflect light differently)
- ☁️ <5% cloud cover
- 🌿 Low turbidity (after heavy rain, river plumes make water murky)
- 📅 Summer months (higher sun angle, clearer water)

#### C3. Download the imagery
1. Select the image in EO Browser
2. Download bands: B2 (Blue, 490nm), B3 (Green, 560nm), B4 (Red, 665nm)
3. Format: GeoTIFF, 10m resolution
4. Also download: SCL (Scene Classification) band to mask clouds/land

#### C4. Compute SDB
Using Python and open-source tools:

```python
# Conceptual SDB pipeline (not production code)
# Algorithm: Stumpf et al. (2003) ratio method

import rasterio
import numpy as np

# Load bands
blue = rasterio.open('B2.tif').read(1)
green = rasterio.open('B3.tif').read(1)

# Ratio transform
ratio = np.log(n_bands * blue) / np.log(n_bands * green)

# Depth = m0 * ratio - m1
# m0, m1 calibrated from known depths (e.g., Litto3D or chart data)
depth = m0 * ratio - m1

# Save as GeoTIFF
```

> 💡 **ELI16 — The Stumpf ratio method**: Instead of trying to measure absolute brightness (which varies with sun angle, haze, etc.), this method uses the *ratio* of blue to green light. The ratio is stable regardless of bottom type (sand vs. seagrass reflects differently, but the ratio of blue:green changes predictably with depth). It's like judging depth by the *color balance* rather than brightness — turquoise = shallow, deep blue = deep, regardless of whether the bottom is sandy or rocky.

#### C5. Calibration
The SDB algorithm needs ground-truth depth data to calibrate `m0` and `m1`:
- **Option A**: Use Litto3D (1m grid, 0–10m zone) as calibration reference
- **Option B**: Use the 93m "Mean depth" grid for calibration
- **Option C**: Use charted depths from SHOM ENC

#### C6. Validation
- Compare SDB output against Litto3D in the 0–10m overlap zone
- Expected accuracy: 1–2m RMS in clear water
- Flag areas where SDB depth > 25m as "unreliable" (beyond light penetration)

---

## 3. Recommended Path (Prioritized)

| Priority | Path | Effort | Likelihood | Output |
|----------|------|--------|------------|--------|
| **1st** | **Path A** — EMODnet pre-computed SDB | 15 min | Medium (depends on regional coverage) | 10m GeoTIFF |
| 2nd | Path B — Copernicus Marine SDB | 30 min | Medium | 10m NetCDF/GeoTIFF |
| 3rd | Path C — DIY from Sentinel-2 | 4–8 hours | High (we control it) | 10m custom GeoTIFF |

---

## 4. Walkthrough: Path A (EMODnet SDB)

### Step 1: Check coverage
1. Open: https://portal.emodnet-bathymetry.eu/
2. In the layer panel (left), find "Satellite Derived Bathymetry"
3. Toggle it ON
4. Navigate the map to: lat 43.5°, lon 7.0° (Côte d'Azur)
5. Zoom in to ~1:100,000 scale
6. **Observe**: Is there SDB coverage (colored pixels) or is it blank?

### Step 2: If coverage exists
1. Use the "Download" tool in the map viewer
2. Draw a rectangle covering our zone: lon 5.9–7.5°, lat 43.0–43.8°
3. Select format: GeoTIFF or NetCDF
4. Download

### Step 3: If coverage does NOT exist → Path C
Proceed to Path C (DIY from Sentinel-2 imagery).

---

## 5. Walkthrough: Path C (DIY, if needed)

### Step 1: Find cloud-free summer imagery
1. Go to: https://apps.sentinel-hub.com/eo-browser/
2. Create free account
3. Search: "Nice, France"
4. Set:
   - Data source: Sentinel-2 L2A
   - Date: June–September 2024 or 2025
   - Max cloud: 5%
5. Browse results — look for images where the sea appears calm and clear
6. Note the image date and tile ID

### Step 2: Download bands
1. In EO Browser, use "Download" → "Analytical" option
2. Select bands: B02 (blue), B03 (green), B04 (red), SCL
3. Download as GeoTIFF (EPSG:32632 or EPSG:4326)
4. File size: ~100 MB per band for a full Sentinel-2 tile

### Step 3: Process locally
We'll write a Kotlin/Java or Python preprocessing script that:
1. Reads the Sentinel-2 bands
2. Masks clouds and land (using SCL band)
3. Masks water deeper than ~25m (using the 93m grid as mask)
4. Applies the Stumpf ratio algorithm
5. Calibrates with Litto3D ground truth
6. Outputs a GeoTIFF or our Protobuf binary format

### Step 4: Package for the app
- Process SDB into our existing Protobuf binary grid format (same pattern as coastline cache)
- Embed the 0–25m SDB layer alongside the 93m regional grid
- The app queries SDB first; falls back to 93m grid for deeper zones

---

## 6. What We Need From You

| # | Action | Link |
|---|--------|------|
| 1 | Check EMODnet SDB coverage for Côte d'Azur | [EMODnet Map Viewer](https://portal.emodnet-bathymetry.eu/?F=SDBS2CSatelliteDerivedBathymetry) |
| 2 | If no coverage: find a cloud-free summer Sentinel-2 image | [EO Browser](https://apps.sentinel-hub.com/eo-browser/) |
| 3 | Report back: coverage (yes/no), image ID, or any issues |

> 💡 **ELI16 — Why we want SDB so badly**: The 93m grid is like a topographic map of the Alps — you see the big mountains and valleys. SDB at 10m is like a hiking trail map — you see individual boulders, ledges, and features that matter for diving. At 10m, a 20m-wide rocky pinnacle occupies 4 pixels (2×2) — visible! At 93m, it occupies 0.04 pixels — invisible. This is the difference between "there's a canyon somewhere around here" and "there's a pinnacle right here at 43.5123°, 7.0456°."
