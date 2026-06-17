<!-- scope: feature -->
# BARO — Alternative Bathymetry Sources (finer than HOMONIM 100m)

> **Parent**: [BARO - Step 01 - Data sources discussion](plans/BARO - Step 01 - Data sources discussion.md)
> **Context**: HOMONIM NM at 0.001° (~100m) is too coarse for scuba diving site identification (10–80m range). We need resolution in the 10–30m range to identify reef structures, drop-offs, wrecks, and pinnacles.
> **Status**: Research phase — 2026-06-01

---

## Problem Statement

> 💡 **ELI16 — Why 100m is useless for diving**: A typical dive site is a 15m-wide rocky pinnacle rising from 40m to 18m. On a 100m grid, that pinnacle occupies at most 1/40th of a single cell. The cell's depth will be averaged to ~39m — the pinnacle is literally invisible. You need cells no larger than ~25m to have a chance of detecting interesting dive structures (at least 2–3 cells across a feature to resolve it). That means we need 4× to 10× finer than HOMONIM.

---

## 🔗 Quick-Link Index

| Source | Resolution | Main Portal | Map/Download |
|--------|-----------|-------------|--------------|
| EMODnet DTM | ~115m | [emodnet.ec.europa.eu](https://emodnet.ec.europa.eu/en/bathymetry) | [Map Viewer](https://portal.emodnet-bathymetry.eu/) |
| SHOM data portal | varies | [data.shom.fr](https://data.shom.fr/) | [diffusion.shom.fr](https://diffusion.shom.fr/) |
| Copernicus SDB | 10m | [marine.copernicus.eu](https://marine.copernicus.eu/) | [EMODnet SDB map](https://emodnet.ec.europa.eu/en/satellite-derived-bathymetry) |
| GMRT | 25–400m | [gmrt.org](https://www.gmrt.org/) | [GMRT MapTool](https://www.gmrt.org/GMRTMapTool/) |
| Navionics SonarChart | 1–30m | [navionics.com](https://www.navionics.com/) | [Web Chart Viewer](https://webapp.navionics.com/) |
| Ifremer / Campagnes | varies | [campagnes.flotteoceanographique.fr](https://campagnes.flotteoceanographique.fr/) | [Seanoe data portal](https://www.seanoe.org/) |
| GEBCO | 450m | [gebco.net](https://www.gebco.net/) | [GEBCO Grid](https://www.gebco.net/data_and_products/gridded_bathymetry_data/) |

---

## 1. Candidate Sources

### 1.1 EMODnet Bathymetry DTM (Latest Version)

| Parameter | Detail |
|-----------|--------|
| **Provider** | European Marine Observation and Data Network (EU) |
| **Latest resolution** | 1/16 arc-minute ≈ **~115m** (standard). Some regional tiles at higher resolution where national surveys exist. |
| **Coverage** | All European seas, including full Mediterranean |
| **Vertical datum** | MSL or LAT (varies by region) |
| **Format** | NetCDF, GeoTIFF, ESRI ASCII |
| **License** | Free, open access |
| **Update** | Continuous, new releases every ~2 years |

🔗 **Links**:
- [EMODnet Bathymetry Portal](https://emodnet.ec.europa.eu/en/bathymetry) — main download portal, product descriptions & metadata
- [EMODnet Map Viewer](https://portal.emodnet-bathymetry.eu/) — interactive map showing resolution per tile before downloading
- [EMODnet DTM Download](https://emodnet.ec.europa.eu/en/bathymetry-download) — check latest 2024 release, filter by Mediterranean tiles

**Assessment**: EMODnet's standard grid is essentially the same resolution as HOMONIM. However, EMODnet has been working on higher-resolution composite DTMs for specific regions. The 2024 release may include improved Mediterranean resolution. **Worth checking the latest EMODnet portal for Mediterranean-specific high-res tiles.**

> 💡 **ELI16 — EMODnet vs HOMONIM**: They're similar resolution grids built from the same underlying survey data. HOMONIM is SHOM's own product; EMODnet is the EU-wide version. The advantage of checking EMODnet: they sometimes release experimental higher-resolution DTMs for specific regions that haven't been back-ported to SHOM's portal yet.

**Verdict**: ⭐⭐ (likely similar to HOMONIM, but check latest release)

---

### 1.2 SHOM Coastal MNT Products (Beyond HOMONIM)

SHOM distributes several bathymetry products. Beyond HOMONIM, there may be:

| Product (to verify) | Expected Resolution | Depth Range | Notes |
|---------------------|---------------------|-------------|-------|
| **MNT Bathymétrique Côtier** | 0.00025° (~25m) or 0.0005° (~50m) | 0–200m | Specialized coastal DTMs for specific regions |
| **MNT Topo-Bathymétrique** | 1–5m (nearshore), coarser offshore | 0–30m | Continuation of Litto3D concept, extending deeper |
| **BDBS Sondes (individual soundings)** | Point data, variable density | All depths | Raw survey data that could be interpolated into a custom grid |
| **Cartes Marines (ENC S-57/S-100)** | Variable (chart scale dependent) | Charted depths | Electronic navigational charts contain individual depth soundings |

🔗 **Links**:
- [data.shom.fr](https://data.shom.fr/) — main geospatial data portal, browse all products
- [diffusion.shom.fr](https://diffusion.shom.fr/) — product download portal, search by region/theme
- [SHOM Bathymetry Products Catalog](https://diffusion.shom.fr/produits/bathymetrie.html) — list of all MNT products
- [SHOM ENC/S-57](https://diffusion.shom.fr/produits/cartes-marines.html) — electronic navigational charts (may have dense soundings)

> 💡 **ELI16 — ENC Charts as Depth Source**: Electronic Navigational Charts (used by ships) contain thousands of individual depth measurements. In well-surveyed areas like the Côte d'Azur, charted soundings can be dense enough to interpolate a much finer grid than HOMONIM. The catch: ENCs are not always free (SHOM charges for full ENC sets), and they deliberately err shallow (showing minimum depths, not "most probable" depths).

**Verdict**: ⭐⭐⭐⭐ (highest potential, needs investigation on data.shom.fr)

---

### 1.3 Satellite-Derived Bathymetry (SDB) — Sentinel-2

| Parameter | Detail |
|-----------|--------|
| **Provider** | Copernicus / ESA (free), various research groups |
| **Method** | Optical satellite imagery → depth estimation via light attenuation in water column |
| **Resolution** | **10 m** (Sentinel-2 visible bands) |
| **Max depth** | 15–25 m in clear Mediterranean water; up to 30m in exceptional conditions |
| **Accuracy** | 1–2 m RMS in ideal conditions (clear water, known bottom type) |
| **Coverage** | Global coastal, updated every 5 days (Sentinel-2 revisit) |
| **Format** | GeoTIFF, NetCDF |
| **License** | Free (Copernicus Open Access) |

🔗 **Links**:
- [EMODnet Satellite-Derived Bathymetry](https://emodnet.ec.europa.eu/en/satellite-derived-bathymetry) — EMODnet's SDB layer, interactive map
- [Copernicus Marine Service](https://marine.copernicus.eu/) — EU marine data portal, search "bathymetry" or "SDB"
- [Copernicus Data Space (Sentinel-2)](https://dataspace.copernicus.eu/) — raw Sentinel-2 L2A imagery if we want to compute SDB ourselves
- [TCarta Marine SDB](https://www.tcarta.com/products/marine-satellite-derived-bathymetry) — commercial SDB (10m, global), free samples may be available
- [Allen Coral Atlas](https://allencoralatlas.org/) — SDB-based mapping (tropical only, not Med, but good reference for technique)

> 💡 **ELI16 — How Satellites Measure Depth**: Water absorbs different colors of light at different rates. Blue penetrates deepest, red disappears first. A satellite like Sentinel-2 takes photos in multiple color bands. By analyzing the ratio of blue to green light reflected from the seabed, and knowing how clear the water is, algorithms can estimate depth. It's the same principle as looking down from a plane and judging depth by color — but done mathematically.
>
> **The Mediterranean sweet spot**: The Med has some of the clearest water in Europe, making SDB unusually effective here. In the Calanques or around Porquerolles, SDB can see 20–25m deep. The 10m pixel size is excellent for identifying reef structures.

**Key SDB products**:
| Product | Provider | Resolution | Coverage | Cost |
|---------|----------|------------|----------|------|
| **Copernicus SDB** | EMODnet/ Copernicus Marine | 10m | European coastal waters (experimental) | Free |
| **Allen Coral Atlas** | Arizona State Univ. | 10m | Coral reefs worldwide (tropical only, not Med) | Free |
| **TCarta Marine SDB** | TCarta (commercial) | 2–10m | Custom on-demand | Paid |
| **Digital Earth Africa/ Pacific** | Various | 10m | Regional (not Med) | Free |

**Assessment**: SDB is the most promising free high-res source for the 0–25m range. Perfect for dive sites that are within photic zone. The 10m resolution is ideal. **Limitation**: doesn't reach the full 80m spec — only 0–25m. Beyond 25m we still need acoustic data.

**Verdict**: ⭐⭐⭐⭐⭐ (0–25m), ❌ (25–80m)

---

### 1.4 GMRT — Global Multi-Resolution Topography

| Parameter | Detail |
|-----------|--------|
| **Provider** | Lamont-Doherty Earth Observatory (Columbia University) |
| **Resolution** | Variable — ~100m in well-surveyed areas, ~400m elsewhere |
| **Coverage** | Global oceans |
| **Format** | NetCDF, GeoTIFF |
| **License** | Free for non-commercial use (CC BY-NC-SA) |

🔗 **Links**:
- [GMRT Homepage](https://www.gmrt.org/) — project overview and data access
- [GMRT MapTool](https://www.gmrt.org/GMRTMapTool/) — interactive web map, zoom to Côte d'Azur to see actual resolution
- [GMRT Data Download](https://www.gmrt.org/GMRTMapTool/np/) — data request form

**Assessment**: GMRT aggregates multibeam sonar surveys from research vessels. In the Mediterranean, some areas have dense ship-track coverage (especially near oceanographic institutes like Villefranche-sur-Mer). Resolution varies dramatically — a lucky area might have 25m data, but most of the Med is at ~200–400m. **Not a reliable upgrade over HOMONIM.**

**Verdict**: ⭐⭐ (hit-or-miss, check specific region via MapTool)

---

### 1.5 French Research Data — Ifremer / CNRS / OCA

| Source | Detail |
|--------|--------|
| **Ifremer** | French oceanographic institute. Holds multibeam surveys from research vessels (Pourquoi Pas?, L'Atalante). Some data publicly released. |
| **Observatoire Océanologique de Villefranche (CNRS/SU)** | Long-term research station on Côte d'Azur. May have detailed local bathymetry from repeated surveys. |
| **Campagnes Océanographiques Françaises** | French oceanographic campaigns archive. |

🔗 **Links**:
- [Campagnes Océanographiques Françaises](https://campagnes.flotteoceanographique.fr/) — search by region "Méditerranée", find campaigns with multibeam bathymetry
- [Seanoe (Ifremer data portal)](https://www.seanoe.org/) — marine data repository, search "bathymétrie Méditerranée"
- [SISMER (Ifremer data center)](https://data.ifremer.fr/) — Ifremer's scientific data catalog
- [Observatoire Océanologique de Villefranche](https://www.obs-vlfr.fr/) — CNRS/SU research station, local data

> 💡 **ELI16 — Research Vessel Data**: French research ships like the *Pourquoi Pas?* regularly map the Mediterranean seafloor with high-end multibeam sonars capable of 5–10m resolution. Much of this data eventually makes its way into EMODnet or GMRT, but with a delay. Direct access to campaign data (if publicly released) could yield much higher resolution than the composite grids.

**Verdict**: ⭐⭐⭐ (potential gold mine, but requires digging through research archives)

---

### 1.6 Commercial / Partially-Free Sources

| Source | Resolution | Coverage | Cost | Notes |
|--------|------------|----------|------|-------|
| **C-MAP Reveal** | 5–50m (from Navico/Lowrance) | Global coastal | Paid ($150–300) | High-res bathymetry from crowdsourced sonar logs + surveys |
| **Navionics SonarChart** | 1–30m (crowdsourced) | Global coastal | Freemium ($25/yr) | Boaters upload sonar logs → community-built HD bathymetry |
| **Graticule/TCarta** | 2–10m SDB | Custom | Paid | Commercial SDB, but free samples exist |

🔗 **Links**:
- [Navionics Web Chart Viewer](https://webapp.navionics.com/) — interactive map, see SonarChart resolution for Côte d'Azur immediately
- [Navionics SonarChart Info](https://www.navionics.com/usa/charts/features/sonarchart/) — description of the crowdsourcing technology
- [Navionics/Garmin Developer API](https://developer.garmin.com/marine/) — API access for embedding in 3rd-party apps
- [C-MAP Reveal](https://www.c-map.com/products/reveal/) — commercial high-res bathymetry
- [TCarta Marine](https://www.tcarta.com/) — commercial SDB, request a sample for Côte d'Azur

> 💡 **ELI16 — Crowdsourced Bathymetry (Navionics)**: Garmin's Navionics has a feature where recreational boaters with fish finders/sonars automatically upload their depth readings. Millions of boats = billions of data points. In popular boating areas like the Côte d'Azur, the resulting depth maps can be extraordinarily detailed (sub-10m resolution). The data is available via Navionics Boating app or SonarChart API. This is essentially "Google Maps traffic data" but for depths — and it's built from volunteer boaters' data. For a recreational boating app, this is arguably the most practical high-res source.

**Verdict**: ⭐⭐⭐⭐⭐ (Navionics SonarChart — perfect fit, but not fully free)

---

## 2. Resolution Comparison

```
Source                        Resolution    Depth Range    Free?    Coverage
─────────────────────────────────────────────────────────────────────────────
Litto3D PACA (MNT 1m)        1 m           0–10m          ✅       One tile only
Sentinel-2 SDB               10 m          0–25m          ✅       Full coast
Navionics SonarChart         1–30m         0–500m         ❌($$)   Full coast
SHOM MNT Côtier (if exists)  25–50m        0–200m         ✅?      Regional
EMODnet (latest)             115m          0–5000m        ✅       Full Europe
HOMONIM NM                   100m          0–2800m        ✅       Golfe du Lion→CA
GEBCO 2024                   450m          All            ✅       Global
─────────────────────────────────────────────────────────────────────────────
        ← USABLE FOR DIVING (≤30m cell size) →   ← TOO COARSE →
```

---

## 3. Recommended Investigation Path

### Short-term (immediately actionable):

1. **Check data.shom.fr** for:
   - "MNT bathymétrique côtier" products at sub-100m resolution
   - Individual ENC tiles (S-57/S-100 format) with depth soundings for the Côte d'Azur
   - Any newer facade MNT with improved resolution
   - 🔗 [https://data.shom.fr/](https://data.shom.fr/)
   - 🔗 [https://diffusion.shom.fr/produits/bathymetrie.html](https://diffusion.shom.fr/produits/bathymetrie.html)

2. **Check EMODnet latest release** (2024 DTM):
   - Look for Mediterranean-specific high-resolution tiles
   - Some regions (Adriatic, Aegean) have 1/32 arc-minute (~55m) experimental grids
   - 🔗 [https://portal.emodnet-bathymetry.eu/](https://portal.emodnet-bathymetry.eu/)

3. **Investigate Sentinel-2 SDB availability**:
   - EMODnet Bathymetry portal has an SDB layer (experimental)
   - Copernicus Marine Service may have Mediterranean SDB products
   - Alternatively: generate our own SDB from Sentinel-2 L2A imagery using open-source tools (AROSICS + SDB algorithm)
   - 🔗 [https://emodnet.ec.europa.eu/en/satellite-derived-bathymetry](https://emodnet.ec.europa.eu/en/satellite-derived-bathymetry)
   - 🔗 [https://dataspace.copernicus.eu/](https://dataspace.copernicus.eu/)

### Medium-term (if short-term yields nothing):

4. **Explore Navionics SonarChart**:
   - Check Navionics Boating API terms for embedding in 3rd-party apps
   - Evaluate cost vs. value for diving depth layer
   - Alternative: Navionics Web API for tile-based depth overlay
   - 🔗 [https://webapp.navionics.com/](https://webapp.navionics.com/) (immediately visible)
   - 🔗 [https://developer.garmin.com/marine/](https://developer.garmin.com/marine/)

5. **Contact research institutes**:
   - OCA Villefranche-sur-Mer for local high-res surveys
   - Ifremer for publicly released campaign data
   - 🔗 [https://www.obs-vlfr.fr/](https://www.obs-vlfr.fr/)
   - 🔗 [https://campagnes.flotteoceanographique.fr/](https://campagnes.flotteoceanographique.fr/)

---

## 4. Ideal Data Mix (Revised)

```
┌──────────────────────────────────────────────────────────┐
│                    DEPTH RESOLUTION                       │
│                                                          │
│  0m ─┬── Litto3D (1m) ────────────── 10m                │
│      │   └─ Ground collision + anchoring                 │
│      │                                                   │
│ 10m ─┼── Sentinel-2 SDB (10m) ──────── 25m              │
│      │   └─ Recreational diving + nearshore features     │
│      │                                                   │
│ 25m ─┼── SHOM Côtier / Navionics (~25m) ── 80m          │
│      │   └─ Deeper diving + offshore features            │
│      │                                                   │
│ 80m ─┴── HOMONIM (100m, regional context fallback)       │
└──────────────────────────────────────────────────────────┘
```

> 💡 **ELI16 — Three-tier depth stack**: Like how Google Earth uses satellite photos for cities, aerial photos for suburbs, and low-res imagery for oceans — we'd use the best available data for each depth zone. Litto3D for the ultra-shallow danger zone, SDB for the coastal dive zone where water is clear enough, and a mid-res acoustic grid (Navionics or SHOM Côtier) for deeper dive sites. HOMONIM stays as the "big picture" background.

---

## 5. Next Steps (Your Decision)

| # | Action | Effort | Impact | Link |
|---|--------|--------|--------|------|
| A1 | Browse data.shom.fr for higher-res coastal MNT products | 30 min | Potentially solves 25–80m range | [data.shom.fr](https://data.shom.fr/) |
| A2 | Check EMODnet 2024 DTM resolution for Med tiles | 15 min | May offer modest improvement | [portal.emodnet-bathymetry.eu](https://portal.emodnet-bathymetry.eu/) |
| A3 | Investigate Copernicus/EMODnet SDB availability for Côte d'Azur | 1 hr | Solves 0–25m with 10m resolution | [emodnet SDB](https://emodnet.ec.europa.eu/en/satellite-derived-bathymetry) |
| A4 | Evaluate Navionics SonarChart API licensing and cost | 1 hr | Best data, but may cost money | [webapp.navionics.com](https://webapp.navionics.com/) |
| A5 | Search Ifremer/OCA research data portals | 1 hr | Unlikely to find full coverage | [seanoe.org](https://www.seanoe.org/) |

**My recommendation**: Start with A3 (SDB) + A1 (SHOM portal) in parallel — these are the two most likely to yield usable results for free. SDB covers the 0–25m dive range beautifully; a SHOM coastal MNT at 25–50m would cover the deeper range.

