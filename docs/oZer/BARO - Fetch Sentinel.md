# BARO — Fetching Sentinel-2 Data

> **Parent**: [BARO - Sentinel-2 SDB guide](plans/BARO - Sentinel-2 SDB guide.md)
> **Status**: Documented — 2026-06-02
> **Purpose**: Technical reference for programmatic Sentinel-2 L2A imagery fetching via Copernicus Data Space APIs. Covers the authentication, search, and download pipeline used to obtain 10m multispectral bands for SDB processing.

---

## 1. Pipeline Overview

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  OIDC Auth   │ ──▶ │  STAC Search │ ──▶ │  OData       │ ──▶ │  SDB Process │
│  Get Token   │     │  Find Images │     │  Download    │     │  Stumpf Algo │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
```

> 💡 **ELI16 — What just happened**: We logged into Europe's satellite data library (Copernicus), searched for cloud-free photos of the Côte d'Azur from summer 2024, found a perfect one (Sep 15, zero clouds), and downloaded three "color channels" — blue, green, and red light — at 10m per pixel. These three channels are the raw ingredients for computing water depth from space.

---

## 2. Authentication

### 2.1 OIDC Token Endpoint

```
POST https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=cdse-public
username={email}
password={password}
```

**Response**: JSON with `access_token` (JWT, expires in ~30 min).

### 2.2 Registration

Free account at: https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/registrations

Email verification required before API access works.

> 💡 **ELI16 — OIDC / JWT**: Think of OIDC (OpenID Connect) like a hotel key card system. You register at the front desk (create account), verify your ID (email confirmation), then get a key card (JWT token) valid for 30 minutes. You swipe it at every door (API call) to prove you're authorized. When it expires, you go back to the front desk for a new one.

### 2.3 Token Usage

All subsequent API calls include the header:
```
Authorization: Bearer {access_token}
```

---

## 3. Image Discovery (STAC API)

### 3.1 STAC Search Endpoint

```
POST https://catalogue.dataspace.copernicus.eu/stac/search
Content-Type: application/json

{
  "collections": ["sentinel-2-l2a"],
  "bbox": [5.9, 43.0, 7.5, 43.8],
  "datetime": "2024-06-01T00:00:00Z/2024-09-30T23:59:59Z",
  "limit": 10,
  "query": {
    "eo:cloud_cover": { "lte": 5 }
  }
}
```

> 💡 **ELI16 — STAC**: SpatioTemporal Asset Catalog. It's like a library search engine for satellite images. You ask: "Show me Sentinel-2 photos of this rectangle (Côte d'Azur) from summer 2024 with less than 5% clouds." It returns a JSON catalog of matching images with their IDs, dates, cloud coverage, and download URLs.

### 3.2 Search Parameters

| Parameter | Value | Meaning |
|-----------|-------|---------|
| `collections` | `sentinel-2-l2a` | Atmosphere-corrected Sentinel-2 |
| `bbox` | `[5.9, 43.0, 7.5, 43.8]` | Menton→Toulon bounding box |
| `datetime` | `2024-06-01/2024-09-30` | Summer months (best water clarity) |
| `eo:cloud_cover` | `≤ 5%` | Near-cloudless images only |

### 3.3 Image Selection Criteria

| Criterion | Ideal | Why |
|-----------|-------|-----|
| Cloud cover | 0% | Clouds block the seafloor signal |
| Water coverage | >90% | More sea pixels = more bathymetry |
| Sun elevation | >40° | Higher sun = less glint on water |
| Season | Jun–Sep | Warm water = clearer (less algae) |
| Sea state | Calm | Waves/whitecaps confuse the algorithm |
| Processing level | L2A | Already atmosphere-corrected |

---

## 4. Image Download (OData API)

### 4.1 Download URL Pattern

```
https://download.dataspace.copernicus.eu/odata/v1/Products({uuid})/Nodes(...)/$value
```

### 4.2 Band Asset Paths

For a given product UUID and tile ID, the 10m resolution bands are at:

```
/Nodes({safe_name})/Nodes(GRANULE)/Nodes({granule_name})/Nodes(IMG_DATA)/Nodes(R10m)/Nodes({tile}_{date}_B{NN}_10m.jp2)/$value
```

**Example** (full URL for Blue band):
```
https://download.dataspace.copernicus.eu/odata/v1/Products(643f8fec-...)/Nodes(S2B_MSIL2A_20240915T102559_...SAFE)/Nodes(GRANULE)/Nodes(L2A_T32TLP_...)/Nodes(IMG_DATA)/Nodes(R10m)/Nodes(T32TLP_20240915T102559_B02_10m.jp2)/$value
```

### 4.3 Required Bands for SDB

| Band | Wavelength | Resolution | Purpose |
|------|-----------|------------|---------|
| **B02** | 493 nm (Blue) | 10 m | Penetrates deepest — primary depth signal |
| **B03** | 560 nm (Green) | 10 m | Ratio with blue eliminates bottom-type bias |
| **B04** | 665 nm (Red) | 10 m | Absorbed quickly — for shallow water (<5m) and land masking |

### 4.4 File Format

- **JPEG2000** (`.jp2`) — wavelet-compressed, 16-bit unsigned integer
- Reflectance values scaled by 0.0001 (divide by 10,000 to get actual reflectance 0–1)
- Projected in tile-specific UTM zone (EPSG:32632 for T32TLP)
- ~110 MB per band for a full 110×110 km Sentinel-2 tile

> 💡 **ELI16 — JPEG2000**: Think of JPEG2000 like a super-JPEG designed for scientific data. Unlike regular JPEG which loses detail, JPEG2000 preserves the exact brightness values satellites measure. Each pixel stores how much light bounced back from that 10m×10m patch of Earth — a number like "0.0452" meaning 4.52% of sunlight was reflected. Ocean water typically reflects 2–8% in blue/green.

---

## 5. Downloaded Dataset (Sep 15, 2024)

### 5.1 Image Metadata

| Field | Value |
|-------|-------|
| **Product ID** | `S2B_MSIL2A_20240915T102559_N0511_R108_T32TLP_20240915T131207` |
| **UUID** | `643f8fec-2ac2-4418-9aec-933d39de7548` |
| **Satellite** | Sentinel-2B |
| **Date** | 2024-09-15 |
| **Time** | 10:25:59 UTC (~12:25 local) |
| **Tile** | T32TLP (MGRS grid) |
| **Cloud cover** | 0.0% |
| **Water coverage** | 98.35% |
| **Sun elevation** | 46.9° |
| **Sun azimuth** | 163.4° (SSE — sun from the south) |
| **Processing baseline** | N0511 (latest) |

### 5.2 Tile Coverage

```
Geometry: Polygon
  NW: 43.35°N,  6.50°E  (near Cannes)
  NE: 44.25°N,  7.88°E  (near Imperia, Italy)
  SE: 43.24°N,  7.79°E  (offshore)
  SW: 42.34°N,  6.57°E  (offshore)
```

Covers the entire Côte d'Azur from Cannes to beyond Menton, extending offshore ~50 km.

### 5.3 Downloaded Bands

| File | Size | Pixels |
|------|------|--------|
| `T32TLP_20240915T102559_B02_10m.jp2` | 113 MB | 10,980 × 10,980 |
| `T32TLP_20240915T102559_B03_10m.jp2` | 115 MB | 10,980 × 10,980 |
| `T32TLP_20240915T102559_B04_10m.jp2` | 112 MB | 10,980 × 10,980 |

> 💡 **ELI16 — 10,980 × 10,980 pixels**: Each Sentinel-2 tile covers ~110×110 km. At 10m resolution, that's 10,980 pixels per side. Each pixel represents a 10m×10m patch of Earth — about the size of a small apartment. For our SDB, we only care about the water pixels (98.35% = ~118 million water pixels). That's our raw material for computing depth.

---

## 6. Future: In-App Fetch Button

### 6.1 Concept

Add a "Fetch Satellite Depth" button to the map UI that:
1. Determines the current map viewport bounding box
2. Queries STAC for the best available cloud-free Sentinel-2 image
3. Downloads B02/B03/B04 bands
4. Runs SDB processing on-device
5. Overlays the resulting 10m depth layer on the map

### 6.2 Implementation Considerations

| Aspect | Approach |
|--------|----------|
| **Auth** | Embed Copernicus OIDC credentials (or use app-level token). Token refresh handled by `BathymetryRepository`. |
| **STAC query** | OkHttp POST to STAC endpoint. Parse JSON with kotlinx.serialization. |
| **Band download** | OkHttp with progress callback. Download to app cache dir. |
| **SDB processing** | Offload to coroutine on `Dispatchers.Default`. Process tiles in chunks to avoid OOM. |
| **Caching** | Cache processed SDB tiles in Protobuf format (same pattern as coastline cache). Re-fetch only when new imagery available (every 5 days). |
| **Zone boundaries** | Initially hardcoded to predefined Côte d'Azur bounds. Future: configurable or dynamic based on viewport. |
| **UI feedback** | Progress bar: "Searching satellite imagery..." → "Downloading bands (113 MB)..." → "Computing depth..." → "Done!" |
| **Offline** | Processed SDB cached locally. No network needed after first fetch. |

> 💡 **ELI16 — Why a button?**: Currently we manually download Sentinel-2 data on a PC, process it offline, and embed it in the APK. The "Fetch" button would let users do this on their phone — tap a button, wait a few minutes, and get a fresh 10m depth map computed from the latest cloud-free satellite pass. The trade-off: 340 MB download + CPU processing time vs. pre-baked data in the APK.

### 6.3 Architecture (Spring/Quarkus Analogy)

| Android Component | Spring/Java Analog |
|-------------------|-------------------|
| `SentinelRepository` | `@Service` — handles STAC queries + downloads |
| `SdbProcessor` | `@Service` — Stumpf algorithm, runs on `Dispatchers.Default` |
| `BathymetryViewModel.fetchSdb()` | `@Controller` endpoint — triggers the pipeline |
| `SdbState` (sealed class) | State machine: `Idle → Searching → Downloading(progress) → Processing → Ready(grid)` |

### 6.4 Data Flow

```
User taps "Fetch SDB"
  → ViewModel.fetchSdb()
    → SentinelRepository.searchStac(bbox)
      → POST /stac/search → List<StacItem>
    → Pick best image (lowest cloud%, highest water%)
    → SentinelRepository.downloadBand(uuid, "B02")
    → SentinelRepository.downloadBand(uuid, "B03")
    → SentinelRepository.downloadBand(uuid, "B04")
    → SdbProcessor.compute(b02, b03, b04, calibrationPoints)
      → Stumpf ratio: depth = m0 * ln(blue)/ln(green) - m1
      → Calibrate m0, m1 using Litto3D ground truth
      → Output: DepthGrid (Protobuf)
    → BathymetryRepository.cacheSdb(grid)
  → UI updates with new depth layer
```

---

## 7. API Reference Summary

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `identity.dataspace.copernicus.eu/.../token` | POST | None | Get OIDC JWT |
| `catalogue.dataspace.copernicus.eu/stac/search` | POST | None | Find Sentinel-2 images |
| `download.dataspace.copernicus.eu/odata/v1/Products({uuid})/.../$value` | GET | Bearer | Download band assets |

---

## 8. Notes & Gotchas

- **Token expiry**: JWT expires after ~30 min. Must refresh before each download session. For the in-app button, store credentials securely (EncryptedSharedPreferences) and auto-refresh the token before each fetch — user never sees auth after initial login.
- **Refresh token**: The OIDC `/token` response also includes a `refresh_token`. Use it to get new access tokens without re-entering credentials: `POST /token` with `grant_type=refresh_token&refresh_token={rt}`. Refresh tokens are long-lived (hours/days). This is the correct flow for a mobile app — cache the refresh token, not the password.
- **API key check**: Copernicus Data Space does NOT offer static API keys (as of 2026). Only OIDC JWT. For automated fetching (CI/CD or server-side), use a dedicated service account with refresh token grant. For the Android app, initial login → store refresh token in EncryptedSharedPreferences → silent refresh on each fetch session.
- **Rate limiting**: Copernicus Data Space has quota limits. Free tier: ~10 GB/month per user.
- **Tile coordinate systems**: Sentinel-2 tiles use UTM projection (EPSG:3263x). Must reproject to WGS84 for the app.
- **No-data value**: 0 in JPEG2000 = no data (outside tile bounds or masked).
- **Reflectance scaling**: Pixel values × 0.0001 = actual reflectance. Values >1.0 indicate saturation (sun glint).
- **Adjacent tiles**: Our zone spans two tiles (T32TLP + T32TLN). For full Toulon→Menton coverage, download both.
