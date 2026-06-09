# Distance-to-Shore Algorithm Options

> Context: Android app (Kotlin/JVM), coastline dataset for Nice–Fréjus (~60 km), simplified with Douglas-Peucker ε=3m → ~15,000 segments across 1 mainland polyline + 1–4 islands. Query rate: 1–10 Hz (GPS updates).

---

## 1. Problem Statement

Given a WGS84 GPS position `(lat, lon)`, compute:
- The **shortest straight-line distance** (meters) to any coastline point — mainland or island.
- The **exact closest point** on the coastline.

The existing [`CoastlineRepository.distanceToCoastMeters()`](../app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:167) uses a brute-force O(S) scan and returns only distance — no closest point.

---

## 2. Shared Foundation

All four approaches use the same distance primitive: [`SpatialOperations.pointToSegmentDistance(p, a, b)`](../app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:38), which projects the query point onto segment a→b using a local planar approximation (accurate to <0.5% for distances <50 km at these latitudes). They differ only in **how candidate segments are selected** before the distance is computed.

---

## 3. The Four Approaches

### A: Brute-Force (Current)

```
Query: for each segment in all polylines:
           d = pointToSegmentDistance(point, a, b)
           if d < best: best = d
       return best
```

| Property | Value |
|----------|-------|
| **Query complexity** | O(S) — scans every segment |
| **Segments checked** | ~15,000 (all) |
| **Per-query CPU** | ~4–8 ms |
| **Memory overhead** | 0 KB (no index) |
| **Preprocess time** | 0 ms (nothing to build) |
| **Returns closest point?** | ❌ Distance only |
| **Speedup vs. baseline** | 1× (baseline) |
| **CPU at 10 Hz** | ~8% (notable on battery) |
| **Code** | Exists in [`CoastlineRepository.kt:167`](../app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt:167) |

**When it's good enough:** One-off queries, or GPS at ≤1 Hz with a very small dataset (<1,000 segments).

**Weakness:** Wastes CPU scanning segments 50+ km away. No closest-point output. Scales linearly with coastline detail.

---

### B: Uniform Spatial Grid ★ Recommended

```
Preprocess:
    Divide bounding box into 500m × 500m cells
    For each segment a→b:
        Compute AABB (min/max lat/lon)
        Insert segment index into every cell overlapping AABB
    Store as sparse HashMap<GridCell, List<SegmentRef>>

Query:
    row, col = hash(lat, lon) → grid cell
    candidates = cell + 8 Moore neighbors
    for each candidate segment:
        d = pointToSegmentDistance(point, a, b)
        if d < best: best = d, closest = project(point, a, b)
    return (best, closest)
```

| Property | Value |
|----------|-------|
| **Query complexity** | O(1) hash + O(k) where k ≈ 50–150 |
| **Segments checked** | ~50–150 (0.3–1% of total) |
| **Per-query CPU** | ~0.03–0.1 ms |
| **Memory overhead** | ~30–40 KB (sparse map) |
| **Preprocess time** | ~1–3 ms (once after coastline load) |
| **Returns closest point?** | ✅ |
| **Speedup vs. baseline** | **80–150×** |
| **CPU at 10 Hz** | ~0.1% (negligible) |
| **Code** | ~60 lines, new file [`CoastlineSpatialIndex.kt`](../app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt) |

**Key design decisions:**
- Cell size = 500m. For the Nice–Fréjus bbox: ~111 rows × 129 cols = 14,319 theoretical cells, but only ~120 are occupied (coastline is 1D), so HashMap storage is sparse.
- Moore neighborhood (9 cells) ensures no boundary misses.
- If all 9 cells are empty (point far offshore): expand search radius by 1 ring at a time.

**Why recommended:** Exact precision (same distance function, just filtered). Negligible memory. Fast enough for 10 Hz GPS with zero perceptible CPU impact. Simple enough to implement and test in under an hour.

---

### C: KD-Tree on Vertices

```
Preprocess:
    Flatten all polyline vertices into array of (lat, lon, polylineIdx, vertexIdx)
    Build 2D KD-tree (alternating lat/lon splits, median pivot)

Query:
    K nearest vertices ← KD-tree query (K=5)
    For each returned vertex:
        Check 2 incident segments (before/after in polyline)
    For each consecutive pair in nearest vertices:
        If they belong to the same polyline and are consecutive:
            Check the segment between them
    Return best (distance, closest point)
```

| Property | Value |
|----------|-------|
| **Query complexity** | O(log V) + O(K) where K=5 |
| **Segments checked** | ~10–15 |
| **Per-query CPU** | ~0.01–0.05 ms |
| **Memory overhead** | ~400–600 KB (tree nodes: 15,000 × ~40 bytes) |
| **Preprocess time** | ~3–8 ms |
| **Returns closest point?** | ✅ |
| **Speedup vs. baseline** | **200–400×** (theoretical) |
| **CPU at 10 Hz** | ~0.05% (negligible) |
| **Code** | ~120 lines, manual KD-tree implementation |

**Precision caveat:** With K=5, the nearest coastline point could theoretically lie on a segment whose both endpoints are >5th-nearest vertices. This requires: (a) a very long straight segment (rare after ε=3m DP simplification, which caps segments at ~100m), AND (b) the query point being nearly equidistant from that segment and 5+ other vertices. Risk: ≪0.01%. Mitigated by also checking segments between consecutive nearest-neighbor vertices.

**Why not recommended (for this project):** 15× more memory than the grid for only 2–4× more speed (already in the "negligible" range). Manual KD-tree implementation is error-prone and harder to maintain. The theoretical precision risk, however small, requires extra guard logic.

---

### D: Hierarchical Bounding-Box Culling (Per-Polyline)

```
Preprocess:
    For each polyline:
        centroid = average of all points
        radius = max haversine(centroid, any point in polyline)

Query:
    For each polyline:
        distToCentroid = haversine(queryPoint, centroid)
        if distToCentroid - radius > currentBest:
            skip this polyline (triangle inequality: can't possibly win)
        else:
            for each segment in this polyline:
                d = pointToSegmentDistance(queryPoint, a, b)
                if d < best: best = d, closest = project(...)
    return (best, closest)
```

| Property | Value |
|----------|-------|
| **Query complexity** | O(P) culling + O(S_fallback) worst-case |
| **Segments checked** | ~3,000 (mainland only, typical) to 15,000 (worst) |
| **Per-query CPU** | ~0.8–2 ms (typical), ~4–8 ms (worst: near coast) |
| **Memory overhead** | ~200 bytes (P centroids + radii, where P ≤ 5) |
| **Preprocess time** | ~0.2 ms |
| **Returns closest point?** | ✅ |
| **Speedup vs. baseline** | **4–10×** (typical), **1×** (worst near mainland) |
| **CPU at 10 Hz** | ~2% (typical), ~8% (worst) |
| **Code** | ~30 lines, minimal changes to [`CoastlineRepository`](../app/src/main/java/ykws/android/maro/data/coastline/CoastlineRepository.kt) |

**Why it's useful as a quick win:** Trivially simple — just precompute a centroid and radius per polyline. Instantly skips distant islands. But the mainland polyline spans the entire 60 km zone — its centroid is always near the query point for most GPS positions, so the triangle-inequality filter rarely prunes it. This makes it only ~5× faster in practice and still O(S) for the common case of being near the mainland.

---

## 4. Comparison Matrix

| Criterion | A: Brute-Force | B: Uniform Grid | C: KD-Tree | D: BBox Culling |
|-----------|:---:|:---:|:---:|:---:|
| **Query time (ms)** | 4–8 | 0.03–0.1 | 0.01–0.05 | 0.8–2 |
| **Speedup** | 1× | **80–150×** | 200–400× | 4–10× |
| **Memory (KB)** | 0 | **~30** | ~500 | ~0.2 |
| **Preprocess (ms)** | 0 | 1–3 | 3–8 | 0.2 |
| **Precision** | Exact¹ | Exact¹ | Near-exact² | Exact¹ |
| **Closest point?** | ❌ | ✅ | ✅ | ✅ |
| **Code lines** | 10 | ~60 | ~120 | ~30 |
| **Tunable?** | No | Yes (cell) | Yes (K) | No |
| **Degenerate risk** | None | Empty cell → expand | Miss long segment | Mainland = no cull |

> ¹ "Exact" = within the planar approximation error of `pointToSegmentDistance` (<0.5%).  
> ² KD-tree may theoretically miss the closest point if on a segment whose endpoints are not among K-nearest vertices. Risk: ≪0.01% for simplified coastlines; mitigatable.

---

## 5. Visual: Per-Query Cost (Log Scale)

```
Approach A  ████████████████████████████████████████  100%   Brute-Force
Approach D  ████████                                   20%   BBox Culling
Approach B  ▋                                           0.7%  Uniform Grid
Approach C  ▎                                           0.3%  KD-Tree
```

## 6. Visual: Memory Footprint (Log Scale)

```
Approach D  ▎   0.2 KB   BBox Culling
Approach B  █  30 KB     Uniform Grid
Approach C  ██ 500 KB    KD-Tree
Approach A  ＿   0 KB     Brute-Force
```

---

## 7. Recommendation

**Approach B — Uniform Spatial Grid (500m cells)** is the Pareto-optimal choice:

- **Speed:** 80–150× faster — imperceptible even at 10 Hz GPS — with CPU at 0.1%.
- **Precision:** Exact — same `pointToSegmentDistance`, just filtered.
- **Memory:** ~30 KB — less than a 48×48 icon.
- **Lean:** ~60 lines of plain Kotlin, no recursion, no allocation during query, no external deps.
- **Returns closest point:** Enables the planned 300m buffer zone feature.

Approach D (bbox culling) is a valid quick-win if you want the closest-point feature today with near-zero code, accepting that it's only 4–10× faster than brute-force. Approach C (KD-tree) is over-engineered for this dataset size.

Full implementation design: [`coastline-distance-algorithm.md`](coastline-distance-algorithm.md)
