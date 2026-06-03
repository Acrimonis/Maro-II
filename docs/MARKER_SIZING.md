<!-- scope: feature -->

# Marker Sizing — Dynamic Zoom & Distance

The center-position marker (boat 🚤 on water, blue dot 🔵 on land) changes size in real time based on two factors: zoom level and distance to coastline.

---

## 1. Zoom Level — "How close am I looking?"

Think of zoom like a camera lens. At **zoom 8** you're looking at the whole French Riviera from space — the boat marker is tiny (~17 dp). At **zoom 18** you're practically standing on the dock — the boat grows huge (~543 dp).

| Zoom | What you see on screen | Boat size |
|------|------------------------|-----------|
| **8** | Whole region (Marseille → Italy) | ≈ 17 dp — a small icon |
| **11** | City scale (Cannes → Antibes) | 48 dp — the "normal" size |
| **14** | Neighborhood scale (a few km across) | ≈ 136 dp — clearly visible |
| **16** | Street level (hundreds of meters) | ≈ 271 dp — big and bold |
| **18** | Dock level (a single marina) | ≈ 543 dp — fills the crosshair area |

The boat follows the same exponential rule the map itself uses (every +1 zoom doubles the ground detail). A **mitigating factor of 0.5×** slows it down.

---

## 2. Distance to Coast — "Am I about to run aground?"

When far out at sea, the marker can be big. As you approach the coastline, it shrinks to avoid visually overlapping land.

| Distance from shore | Boat size multiplier | Feels like… |
|---------------------|---------------------|-------------|
| **0 m** (on the coastline) | 0.5× (half size) | "I'm right at the edge — careful!" |
| **500 m** | 0.625× | "Approaching the bay…" |
| **1000 m** | 0.75× | "A few minutes from shore…" |
| **2000 m+** | 1.0× (full size) | "Open water — full throttle!" |

---

## Formulas

The two effects combine: **`finalSize = zoomSize × distanceMultiplier`**

**Zoom** — exponential, matching map scale:
```
dp = baseDp × 2^(ZOOM_EXPONENT × (zoom − REF_ZOOM))
```

**Distance ramp** — linear shrink near the coast:
```
multiplier = 0.5 + 0.5 × clamp(distance / 2000, 0, 1)
```

---

## Tuning Constants

All defined as `private const val` at the top of [`CenterMarkerOverlay`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:526):

| Constant | Value | What it does |
|----------|-------|-------------|
| `REF_ZOOM` | 11.0 | Zoom where marker is at "normal" base size |
| `BOAT_BASE_DP` | 48.0 | Boat size at zoom 11 (dp) |
| `DOT_BASE_DP` | 16.0 | Land dot size at zoom 11 (dp) |
| `ZOOM_EXPONENT` | 0.5 | How aggressively zoom changes size (1.0 = exactly like map) |
| `DIST_SHRINK_MIN_MULT` | 0.5 | Smallest the marker gets on the coastline |
| `DIST_SHRINK_RAMP_M` | 2000.0 | Distance until marker reaches full size (meters) |

---

## Data Flow

```
MapListener.onZoom()                    MapListener.onScroll() / onZoom()
  │                                        │
  ├─ onZoomChanged(zoomLevelDouble)        ├─ onCenterChanged(lat, lon)
  │    │                                   │    │
  │    ▼                                   │    ▼
  │  CoastlineViewModel                    │  CoastlineViewModel
  │  .updateZoomLevel(zoom)                │  .updateMapCenter(lat, lon)
  │    │                                   │    │
  │    ▼                                   │    ├─ _mapCenter ← LatLng
  │  _zoomLevel ← zoom                     │    ├─ _isWater ← repo.isOnWater()
  │    │                                   │    └─ _distanceToShore ← repo.distanceToCoast()
  │    │                                   │
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
              ├─ sizeByZoom = baseDp × 2^(ZOOM_EXPONENT × (zoom − REF_ZOOM))
              ├─ distMultiplier = ramp(distanceToShore)
              └─ finalSizeDp = sizeByZoom × distMultiplier
```
