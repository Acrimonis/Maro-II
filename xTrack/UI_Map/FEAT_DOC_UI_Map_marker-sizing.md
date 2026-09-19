<!-- scope: feature -->

# Marker Sizing — Dynamic Zoom & Distance

The center-position marker (boat 🚤 on water, blue dot 🔵 on land) changes size in real time from two
factors: the zoom level and the distance to the coastline.

---

## 1. Zoom Level — "How close am I looking?"

Think of zoom like a camera lens. The shipped range is **11 to 20**, and the boat answers it
exponentially. The figures below are what the current exponent draws offshore, where nothing shrinks the
sprite; the land dot is a quarter of each one.

| Zoom | What you see on screen | Boat size |
|------|------------------------|-----------|
| **11** | City scale (Cannes → Antibes) | 29 dp — a small icon |
| **14** | Neighborhood scale (a few km across) | 60 dp |
| **16** | Street level (hundreds of meters) | 97 dp |
| **18** | Dock level (a single marina) | 158 dp |
| **20** | Deepest level | 256 dp — a big sprite over fine ground |

Ground coverage doubles on every +1 zoom, so an exponent of 1.0 would double the sprite with it; the
configured 0.35 adds 27.5 % per level instead.

---

## 2. Distance to Coast — "Am I about to run aground?"

Far out at sea the marker can be big. As the coastline approaches it shrinks, so it never sits on land.

| Distance from shore | Boat size multiplier | Feels like… |
|---------------------|---------------------|-------------|
| **0 m** (on the coastline) | 0.3× | "I'm right at the edge — careful!" |
| **500 m** | 0.475× | "Approaching the bay…" |
| **1 000 m** | 0.65× | "A few minutes from shore…" |
| **2 000 m and beyond** | 1.0× (full size) | "Open water — full throttle!" |

---

## Formulas

The two effects combine: **`finalSize = zoomSize × distanceMultiplier`**

```
zoomSize   = baseDp × 2^(exponent × (zoom − 12))
multiplier = 0.3 + 0.7 × clamp(distance / 2000, 0, 1)
```

---

## Tunables — one home each

| What | Where it lives |
|------|----------------|
| The zoom exponent — sprite, crosshair and cap arrow alike | `map.marker.size.zoomExponent` in `maro.properties`, read as `AppConfig.mapMarkerSizeZoomExponent`; range 0.0–1.0 |
| The two base sizes at the reference zoom | `map.marker.size.boatBaseDp` (36.8) and `map.marker.size.dotBaseDp` (9.2) in `maro.properties`; the dot is kept at 0.25 × the boat |
| The reference zoom | `REF_ZOOM` (12.0) in [`ui/map/MapOverlays.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt) |
| Coast shrink floor and ramp | `DIST_SHRINK_MIN_MULT` (0.3) and `DIST_SHRINK_RAMP_M` (2 000 m), same file |
| The arrow's own length clamps | `CAP_DP_PER_KNOT`, `CAP_MIN_DP`, `CAP_MAX_DP`, same file |
| The map's zoom range | `MAP_MIN_ZOOM` / `MAP_MAX_ZOOM` in [`ui/map/CoastlineMapView.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt) |

---

## Data Flow

```
MapListener.onZoom()                    MapListener.onScroll() / onZoom()
  │                                        │
  ├─ onZoomChanged(zoomLevelDouble)        ├─ onCenterChanged(lat, lon)
  │    │                                   │    │
  │    ▼                                   │    ▼
  │  NavigationViewModel                   │  NavigationViewModel
  │  .updateZoomLevel(zoom)                │  .updateMapCenter(lat, lon)
  │    │                                   │    │
  │    ▼                                   │    ├─ _mapCenter ← LatLng
  │  _zoomLevel ← zoom                     │    ├─ _isWater ← repo.isOnWater()
  │                                        │    └─ _distanceToShore ← repo.distanceToCoast()
  │                                        │
  └────┼───────────────────────────────────┘
       │
       ▼
  MapScreen.collectAsState()
       │
       ├─ zoomLevel: Double
       └─ distanceToShore: Double?
              │
              ▼
       CenterMarkerOverlay(zoomLevel, distanceToShore)
              │
              ├─ zoomSize    = baseDp × 2^(AppConfig.mapMarkerSizeZoomExponent × (zoom − 12))
              ├─ distMultiplier = 0.3 + 0.7 × clamp(distanceToShore / 2000, 0, 1)
              └─ finalSizeDp = zoomSize × distMultiplier
```
