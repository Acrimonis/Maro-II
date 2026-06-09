<!-- scope: feature -->

# Depth Mapping — Data Sources Synthesis

**Recoverable synthesis** of the bathymetry data-source research for **DepthMapping**
(codename BARO). Keep this file current whenever source findings or the data strategy
change (per the feature rule). Supersedes the exploratory notes in
[`oZer/BARO - *.md`](oZer) with live-verified facts.

- **Zone:** Cannes → Menton (French Riviera). Box ≈ lat 43.40–43.80°N, lon 6.65–7.55°E.
  Landmarks: Îles de Lérins (Sainte-Marguerite / Saint-Honorat), Cap d'Antibes, Golfe-Juan,
  Cap Ferrat, Monaco, Menton.
- **Validated:** 2026-06-06, via 5 parallel research agents hitting live endpoints
  (GetCapabilities / DescribeCoverage / GetFeatureInfo / REST / download servers), not just
  marketing pages.
- **Requirements:** (1) **Collision** 0–5 m — shoalest-honest, ±0.5 m, fine grid (safety).
  (2) **Dive site assessment** 5–60 m — fine horizontal resolution (≤~25 m cells) to resolve
  pinnacles / reefs / drop-offs.

---
y
## TL;DR — decided data strategy

1. **Bake everything offline** for this fixed zone. **No on-device GeoTIFF/GML decoder.**
   The EMODnet "WCS won't emit ESRI-ASCII" snag is bypassed entirely: the DTM tile downloads
   directly as ESRI `.asc` / GeoTIFF (no auth, CC-BY) — feed the existing `AsciiGridParser`.
2. **Base bake (now):** **SHOM Litto3D PACA 2015** for 0–10 m collision + **EMODnet DTM 2024
   tile `E5`** for the 10–60 m+ backbone. Litto3D *must* be baked anyway (no runtime point API —
   but the tiles download from a **public, no-account API**, see below), so EMODnet joins the same
   lane. Merge via `DepthMerge` → `DepthValidator` → serializer.
3. **EMODnet ≠ dive detail.** At ~115 m (and **no HRSM, no Côte-d'Azur SDB** for this zone —
   both verified empty live) it cannot resolve pinnacles. It is a coarse *backbone* only.
4. **Dive detail (later, data-availability-driven):** **SHOM survey "lots"** (free, accurate,
   patchy) and/or **DIY Sentinel-2 SDB** (10 m, free — but **Posidonia seagrass degrades it
   exactly here**).
5. **Grid:** **25 m single common grid now** (captures all currently-available real detail;
   ~20 MB over the full zone; perf-safe — one Bitmap `GroundOverlay`). Evolve to
   **two-resolution** (10 m nearshore / 25–50 m offshore) *only when a fine dive source lands*
   — never 10-m-everywhere (~124 MB RAM over the full zone).
6. **Runtime:** keep EMODnet **REST `/depth_sample`** as the live point cross-check / validation
   input (works, no auth).

---

## Source evaluation (free/open, by usefulness)

| Source | Res (this zone) | Depth | Covers zone? | Access → format | Datum / CRS | Licence | Collision | Dive |
|---|---|---|---|---|---|---|---|---|
| **SHOM Litto3D PACA 2015** | **1 m** / 5 m | 0 → ~−10 m (envelope to −40) | fringe only, patchy >−10 m | **public INSPIRE pre-package API** (no account) → `.7z` of `.asc`/`.xyz` | IGN69 / Lambert-93 | Etalab LO v2.0 | ✅ **best** | ⚠ shallow only |
| **EMODnet DTM 2024 (tile E5)** | ~115 m N-S / ~84 m E-W | full | ✅ fully | direct DL → GeoTIFF + **ESRI `.asc`** + CSV; WCS; REST; ERDDAP | LAT (+MSL variant) / EPSG:4326 | CC-BY 4.0 | ⚠ coarse fill | ⚠ coarse backbone |
| **SHOM survey "lots"** | ≤25 m where dense (multibeam) | 0–60 m+ | survey-by-survey | data.shom.fr catalog → XYZ soundings | LAT (zéro hydro) / WGS84 | **CC BY-SA 4.0** | ○ secondary | ✅ **best deep, where it exists** |
| **DIY Sentinel-2 SDB** | **10 m** | ~0–25 m (clear) | ✅ (clear water) | CDSE STAC/OData (free token) + Stumpf + ICESat-2 calib | self-calibrated → LAT | open (Copernicus) | ○ (LiDAR better) | ✅ **fine 5–25 m** ⚠ Posidonia |
| Copernicus Marine Global Coastal SDB | 100 m | shallow | ✅ | Copernicus Marine Store (account) → NetCDF-4 | MSL | free (Mercator) | ✗ coarse | ✗ coarse |
| HOMONIM façade GdL-CA | ~111 m | −2844→+10 | ✅ | diffusion.shom.fr → `.asc`/GRD/BAG | LAT (PBMA) / WGS84 | Etalab LO | ✗ | ✗ (dropped 2026-06-01) |
| GEBCO 2024/2025 | ~450 m | all | ✅ | download.gebco.net → NetCDF/GeoTIFF/ASCII | MSL | public domain | ✗ | ✗ last-resort fill |
| GMRT | ~100 m max (nearshore = GEBCO fill) | — | geo yes, real data deep-margin | GridServer → GeoTIFF/NetCDF/ASCII | — | CC-BY 4.0 | ✗ | ✗ |

**Verified-empty / ruled-out for this zone:**
- **EMODnet HRSM** (high-res composites): GetFeatureInfo on `emodnet:hr_bathymetry_area`
  returned 0 at box-centre *and* Monaco — **no high-res EMODnet tile here.**
- **EMODnet / dedicated SDB layer**: SDB sites exist for ES/GR/HR/CY/DK/Sicily — **none for
  Côte d'Azur.** (SDB best-estimate *coastline* covers it, but that's a 0 m vector, not depths.)
- **TCarta 10 m SDB**: commercial only; free tier = a visual basemap, not analytic depths.
- **Navionics SonarChart / C-MAP Reveal**: ToS forbid caching, building a database, or
  extracting depth values; free access is rendered web-tiles only → legally + technically
  unusable as a data source.
- **IHO DCDB Crowdsourced Bathymetry (CSB)**: CC0, but online data is filtered to opted-in
  states; France's territorial waters (the whole box, ≤12 nm) are almost certainly filtered.
  *Verify with one bbox call to the CSB Extract API before fully discounting.*
- **OpenSeaMap**: re-renders GEBCO; sparse soundings; ODbL share-alike. Skip.
- **Ifremer / MALISAR / OCA-Villefranche research DTMs**: real and fine (1–50 m) but
  **deep-margin (>100 m)** — wrong depth band for our 0–60 m needs.
- **ENC S-57/S-63**: encrypted, paid subscription. Not open soundings.
- **SHOM geo-services (WMS/WMTS/WCS)**: **no WCS** returning values; the bathy WMTS
  (Bathyelli) is PNG-only and "soumis à abonnement". No free runtime *point-value* source from
  SHOM — **but** bulk DEM tiles download openly via the **INSPIRE pre-package service** (no
  account; dedicated section below).

---

## The EMODnet "decoder" non-problem (key finding)

The earlier blocker was: *EMODnet WCS `GetCoverage` emits only GeoTIFF / GML / PNG / JPEG /
`text/plain` — **not** ESRI ASCII / NetCDF / CSV* (confirmed verbatim from live
ServiceMetadata). That made `EmodnetWcsClient.fetchCoverage` throw and implied an on-device
GeoTIFF/GML decoder. **It is unnecessary** because the DTM is downloadable as whole-region
**files**:

- Tile for this zone = **`E5`** (bbox lon 3.5–13.375°E, lat 43.125–52.5°N — contains the box).
- Direct, no-auth, CC-BY 4.0 downloads at `https://downloads.emodnet-bathymetry.eu/v12/`:
  - `E5_2024.tif.zip` — 32-bit float GeoTIFF (LAT)
  - `E5_2024.asc.zip` — **ESRI ASCII (LAT)** · `E5_2024.msl.zip` — ESRI ASCII (MSL)
  - `E5_2024.emo.zip` — EMODnet CSV (also `_no_gebco`)
- Bake step: download once → `gdalwarp -te 6.65 43.40 7.55 43.80 -t_srs EPSG:4326` to clip →
  feed the existing tested `AsciiGridParser`. (E5 is large — clip first.)
- If runtime fetch is ever wanted: WCS `format=text/plain` *does* return a numeric depth grid
  (verified: a 48×48 float grid off Cannes), and REST `/depth_sample` works per point.

⇒ **`EmodnetWcsClient` is demoted** (WCS not on the data path); **`EmodnetRestClient` stays**
for runtime point cross-checks / validation.

---

## SHOM Litto3D — public download API (no account; key finding 2026-06-06)

Litto3D PACA 2015 is **open data** (Etalab Licence Ouverte v2.0) and — contrary to the earlier
"account + cart only" note — is served by the **SHOM INSPIRE pre-package download service**, a
**public, no-authentication** API (INSPIRE pre-defined-dataset download services are open by
directive; the diffusion.shom.fr cart is just one UI over it). There is *no per-point value API*
(the WMTS is rendered-only), but the **bulk DEM tiles download freely**:

- **List groups:** `GET https://services.data.shom.fr/INSPIRE/telechargement/prepackageGroup?request=GetCapabilities`
  → XML `<PrepackageGroup><Name>…` — PACA Litto3D = **`LITTO3D_PACA_2015_PACK_DL`**.
- **List tiles:** `GET …/prepackageGroup/LITTO3D_PACA_2015_PACK_DL` → JSON
  `{prepackageResources:[{prepackageName:"XXXX_YYYY", …}]}` — **259 prépaquets** named by
  Lambert-93 km. The ~**38** intersecting our box (km X 995–1050, Y 6255–6305) total **~2.8 GB**.
- **List files:** `GET …/prepackage/<XXXX_YYYY>` → `{downloadFiles:[{fileName,fileSize,fileMd5}]}`
  (one `.7z` per prépaquet, **MD5 provided**).
- **Download:** `GET …/prepackage/<XXXX_YYYY>/file/<fileName>` → the `.7z`
  (`application/x-7z-compressed`, **HTTP range/resume** supported). Verified live 2026-06-06
  (HTTP 206 + 7z magic bytes + MD5 match).

Each `.7z` holds 1 km sub-tiles with **MNT1m/** + MNT5m/ ESRI ASCII (Lambert-93 / IGN69,
`nodata −99999`), a point cloud, masks, and the Etalab PDF. Windows **`tar` (bsdtar) reads `.7z`**
→ no 7-Zip needed. The fetch is fully scripted in
[`tools/fetch_litto3d_paca.ps1`](../tools/fetch_litto3d_paca.ps1) (curl + tar, MD5-checked,
idempotent, resumable) → extracts the 1 m `.asc` into `tools/litto3d_tiles/` for
`bake_litto3d.bat`. ⇒ **Litto3D is no longer a manual blocker.**

---

## Merge & conflict-resolution design (per-cell arbitration)

The grid stores per-cell `(depth, source, confidence)` — so merging N sources = resample each
onto the common grid, then arbitrate per cell. Resolve in this order:

0. **Datum-align to LAT first.** Most apparent "contradictions" are just vertical-datum
   offsets (IGN69 ≈ +0.40 m, MSL ≈ +0.15 m, LAT = 0). `DepthValidator.convertDatum` models
   these and flags a near-constant residual as a datum mismatch. Align before declaring conflict.
1. **Band rule (coded in `DepthMerge`):** collision 0–5 m → **shoalest-wins** among *measured*
   sources (`mergeShallowShoalest`); dive 5–60 m → **finest/highest-confidence wins**
   (`mergeDeep`).
2. **Genuine disagreement** (trusted sources differ by > `k·√(σ₁²+σ₂²)`): don't silently pick —
   **down-weight that cell's confidence** (UI badge → "uncertain") and resolve conservatively
   (collision → shoalest; dive → measured over inferred).
3. **SDB is systematically biased, not noisy.** Posidonia → reads *too deep*. So
   **cross-calibrate SDB to the overlapping measured grid** (offset + scale), and where it
   still diverges over dark bottom → **seagrass-flag and mask it**. SDB = clear-water gap-filler,
   never an authority over a measured depth.
4. **Safety asymmetry:** in 0–5 m a lower-trust source may only make a cell *shallower*, never
   deeper.

**Trust cascade (per cell, after datum-align):** Litto3D (≤10 m, measured 1 m) ▸ SHOM survey
lots (measured multibeam) ▸ Sentinel-2 SDB (inferred 10 m, clear/non-seagrass, ≤~25 m) ▸
EMODnet (~115 m composite) ▸ GEBCO (~450 m). But **confidence decides, not raw rank**:
`confidence = seed × validationResidualFactor × agreementFactor`. The code currently seeds
confidence a-priori only — **the main loop to wire up is validator → confidence**
(down-weight sources that fail their tier in `DepthValidator`).

- **Collision band → there IS a global winner:** measured-shoalest, always.
- **Dive band → no global winner:** it's whatever is best-surveyed at that spot →
  data-availability-driven (the reason for per-cell provenance + a residual-measuring harness).
- When downsampling a fine source into a 25 m collision cell, aggregate by **min (shoalest)**,
  not mean, or a 1 m rock gets averaged away.

---

## Grid resolution decision

**25 m single common grid now** → **two-resolution later** (not 10-m-everywhere).

- At 25 m we already over-sample EMODnet (~115 m) 4.6× and preserve Litto3D's shoalest per
  cell ⇒ 25 m captures all real detail that currently exists; finer now just interpolates air.
- 25 m over the full zone ≈ ~20 MB; render is one Bitmap `GroundOverlay` (blit cost is
  resolution-independent) ⇒ perf-safe.
- 10-m-everywhere over the full zone ≈ ~124 MB RAM — too heavy. When a fine dive source (SDB /
  dense lots) lands, the correct move is **two-resolution** (10 m in the 0–25 m nearshore band,
  25–50 m offshore), matching how the data actually exists. Not a one-way door — `DepthSerializer`
  takes any dims; changing res = re-bake.
- **TODO:** widen `WATER_BBOX` (`DepthConstants`) from the current Cannes/Lérins box to the full
  Cannes→Menton coast (lon 6.70–7.31).

---

## Build-order priority (≠ merge priority)

1. **Litto3D + EMODnet E5** — in hand, certain, reuse `AsciiGridParser`. (Both requirements get
   *some* data immediately: collision well-covered, deep band coarse.)
2. **SHOM survey lots** — free + accurate for the dive band, but must locate dense multibeam lots
   per site (interactive catalog) and write a point-cloud → grid gridder; CC BY-SA share-alike.
3. **DIY Sentinel-2 SDB** — most effort (ACOLITE + ICESat-2 calibration) + Posidonia risk; do
   last, only where it demonstrably adds value over the measured sources.

---

## Key endpoints / URLs

- EMODnet DL: `https://downloads.emodnet-bathymetry.eu/v12/E5_2024.{tif,asc,msl,emo}.zip`
- EMODnet WCS caps: `https://ows.emodnet-bathymetry.eu/wcs?SERVICE=WCS&REQUEST=GetCapabilities&VERSION=2.0.1`
  (coverage `emodnet__mean`; formats GeoTIFF/GML/PNG/JPEG/`text/plain`)
- EMODnet REST: `https://rest.emodnet-bathymetry.eu/depth_sample?geom=POINT(7.05 43.52)`
- EMODnet terms (CC-BY 4.0): `https://emodnet.ec.europa.eu/en/terms-use-emodnet-online-services-data-and-data-products`
- **SHOM Litto3D PACA 2015 — public INSPIRE pre-package download (no account):**
  · groups `https://services.data.shom.fr/INSPIRE/telechargement/prepackageGroup?request=GetCapabilities`
  · tiles `https://services.data.shom.fr/INSPIRE/telechargement/prepackageGroup/LITTO3D_PACA_2015_PACK_DL`
  · files `…/prepackageGroup/LITTO3D_PACA_2015_PACK_DL/prepackage/<XXXX_YYYY>`
  · download `…/prepackage/<XXXX_YYYY>/file/<XXXX_YYYY>.7z` · scripted `tools/fetch_litto3d_paca.ps1`
  · (optional cart UI) `https://diffusion.shom.fr/donnees/altimetrie-littorale/litto3d-paca-2015.html`
  · spec `https://services.data.shom.fr/static/specifications/DC_Litto3D.pdf`
- SHOM survey lot metadata example (EM710, CC BY-SA, LAT): `https://services.data.shom.fr/geonetwork/srv/api/records/LOTS_BATHY_S201802500-002`
- Copernicus Data Space (Sentinel-2): `https://documentation.dataspace.copernicus.eu/APIs.html`
- Copernicus Marine Global Coastal SDB: `https://data.marine.copernicus.eu/product/BATHYMETRY_GLO_PHY_COASTAL_L4_MY_016_001/description`
- ACOLITE (atmos correction): `https://odnature.naturalsciences.be/remsem/software-and-data/acolite/`
- GEBCO 2024: `https://www.gebco.net/data-products-gridded-bathymetry-data/gebco2024-grid`

---

## Flagged uncertainties (verify before relying)

- Exact byte sizes of E5 download files (not exposed in metadata; expect tens–hundreds of MB
  zipped) — clip immediately after download.
- Litto3D's true offshore depth reached *per tile* around Monaco/Menton — a coverage/content
  question only; **access is solved** via the public pre-package API above. Which/how-dense SHOM
  survey lots cover Lérins / Antibes / Cap Ferrat still needs the data.shom.fr catalog.
- France's IHO CSB opt-in status — one bbox call to the DCDB Extract API confirms empty/not.
- Whether SHOM's bathy WMTS subscription is free-with-account or paid (GetCapabilities only
  says "soumis à abonnement"). Moot unless a rendered SHOM backdrop is wanted.
