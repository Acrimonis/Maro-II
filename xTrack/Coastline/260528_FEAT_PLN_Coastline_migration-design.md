<!-- scope: feature -->
# Coastline System — Migration Design (Final Scope)

## ⚡ Current Task: Shoreline Tracing Only

This task delivers **only the coastline itself** — polylines drawn on a map, with water/shore side detectable. The 300m buffer zone, point-in-zone test, and closest-distance query are **future requirements** kept in mind for data model compatibility.

---

## 1. Functional Requirements (Now)

| # | Requirement | How |
|---|-------------|-----|
| 1 | Fetch coastline from OSM | Overpass API → `CoastlineGenerator` |
| 2 | Assemble OSM segments into continuous polylines | Endpoint matching via Haversine |
| 3 | Filter islands ≤ 6 NM from main coast | `minDistanceBetweenPolylines()` |
| 4 | Clip to Nice–Fréjus zone (6.70°E – 7.31°E) | Simple longitude filter |
| 5 | Simplify with Douglas-Peucker at ε = 3m | Recursive DP algorithm |
| 6 | Determine which side is water vs shore | Cross-product (per nearest segment) |
| 7 | Display coastline on map | OSMdroid `Polyline` in Compose `AndroidView` |
| 8 | Store metadata (source, point count, etc.) | `CoastlineMetadata` data class |

## 2. Future Requirements (kept in mind)

| # | Requirement | Impact on data model |
|---|-------------|---------------------|
| A | 300m sea-side buffer zone (distance from **closest land point**, not parallel offset) | Coastline must be oriented (water-on-right) to know offset direction |
| B | Point-in-zone test (GPS → is it in the 300m zone?) | Coastline points & orientation must be accessible |
| C | Closest distance from any GPS point to coastline | Same data — just point-to-segment math |

---

## 3. Data Model

```kotlin
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

data class CoastlineSegment(
    val id: String,
    val points: List<LatLng>   // oriented: water on RIGHT
)

data class CoastlineMetadata(
    val source: String,
    val pointCount: Int,
    val meanSpacingM: Double,
    val epsilonM: Double?
)

sealed interface CoastlineState {
    data object Loading : CoastlineState
    data class Ready(
        val polylines: List<CoastlineSegment>,
        val metadata: CoastlineMetadata
    ) : CoastlineState
    data class Error(val message: String) : CoastlineState
}
```

Note: No `BufferPolygon` type yet — that comes with the future zone task.

---

## 4. Architecture (Scoped to Shoreline Only)

```
com.ykws.android.maro/
│
├── data/
│   ├── coastline/
│   │   ├── CoastlineRepository.kt    ← Single source of truth, StateFlow
│   │   └── CoastlineGenerator.kt     ← OSM fetch + pipeline (coroutines)
│   │
│   └── model/
│       ├── LatLng.kt
│       ├── CoastlineSegment.kt
│       ├── CoastlineMetadata.kt
│       └── CoastlineState.kt
│
├── spatial/
│   └── SpatialOperations.kt          ← Pure geometry (ported from legacy)
│
├── ui/
│   ├── map/
│   │   ├── MapScreen.kt             ← Compose + OSMdroid
│   │   └── CoastlineViewModel.kt     ← StateFlow → Compose
│   │
│   └── theme/
│       └── ...
│
└── MainActivity.kt
```

---

## 5. Migration Sequence (Shoreline Only)

| Step | Deliverable | Files | Description |
|------|-------------|-------|-------------|
| **1** | Domain model | `LatLng.kt`, `CoastlineSegment.kt`, `CoastlineMetadata.kt`, `CoastlineState.kt` | Pure data classes |
| **2** | Spatial algorithms | `SpatialOperations.kt` | Haversine, point-to-segment, Douglas-Peucker, cross-product orientation, segment assembly |
| **3** | OSM fetch + pipeline | `CoastlineGenerator.kt` | Port from legacy: OkHttp + coroutines for Overpass, assembly, island filter, clip, simplify |
| **4** | Repository | `CoastlineRepository.kt` | Wrap generator, expose `StateFlow<CoastlineState>` |
| **5** | Dependencies | `libs.versions.toml`, `app/build.gradle.kts` | Add OkHttp, kotlinx-serialization, ViewModel, osmdroid |
| **6** | ViewModel | `CoastlineViewModel.kt` | Collect from repository |
| **7** | Map UI | `MapScreen.kt` | Compose + OSMdroid `AndroidView` rendering coastline polylines |

---

## 6. Water/Shore Side Detection

Already implemented in legacy [`CoastlineChecker.isOnWater()`](C:/Users/nbadino/Documents/Perso/CoDe/Maro/app/src/main/java/io/witwave/maro/CoastlineChecker.kt:56):

```
For the nearest coastline segment A→B:
  1. Project (lat, lon) to local Cartesian plane
  2. Cross product z = (Bx - Ax)*(Py - Ay) - (By - Ay)*(Px - Ax)
  3. z < 0  → point is to the RIGHT → WATER
  4. z >= 0 → point is to the LEFT  → LAND
```

Works for mainland (west→east, right=south=sea) and islands (CCW winding → right=outside=sea) alike.

---

## 7. Dependencies to Add

| Library | Version | Purpose |
|---------|---------|---------|
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP client for Overpass API |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.6.3 | JSON parsing |
| `org.osmdroid:osmdroid-android` | 6.1.18 | Map rendering |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.0 | ViewModel in Compose |

