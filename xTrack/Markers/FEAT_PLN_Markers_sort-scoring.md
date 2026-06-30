# Markers — Composite Sort Scoring

> **Feature:** Markers | **Subfeature:** sort-scoring
> **Created:** 2026-06-30 | **Status:** Plan — discussion

## Formula

```
score = typeWeight × (zoneSize + distance × distanceWeight)

Lower score = displayed first.
Children always display before parents (depth-first, leaves-first).
```

## Parameters (maro.properties)

```properties
# ── Marker match ordering ─────────────────────────────────────────────
# Composite score = typeWeight × (zoneSize + distance × distanceWeight)
# Lower score = displayed first (Kotlin sortedBy ascending).
# Children always before parents (depth-first, leaves-first), regardless of scores.
#
# typeWeight.Pin (default 0.15)
#   Pin = single point of interest — most specific marker type.
#   Lower = pin always wins. At 0.15: pin at 1500m ≈ circle at dead center.
#   0.0 = pin always first. 1.0 = pin treated same as circle.
#
# typeWeight.Circle (default 1.0)
#   Baseline — neutral multiplier.
#
# typeWeight.Corridor (default 2.0)
#   Corridor = least specific zone type.
#   Higher = corridor must be closer to win. At 2.0: corridor needs ~2× less
#   (zoneSize + distance×W) than a circle with the same numbers to beat it.
#
# distanceWeight (default 3.0)
#   How much distance matters vs zone size in the (zoneSize + distance × W) term.
#   At 1.0: equal weight. A 200m zone at 200m ≈ 400m zone at 0m.
#   At 3.0: distance ×3. A 200m zone at 67m ≈ 400m zone at 0m.
#   At 10.0: distance dominates completely.
#   Higher = being closer to center matters more than zone being small.

marker.sort.typeWeight.Pin=0.15
marker.sort.typeWeight.Circle=1.0
marker.sort.typeWeight.Corridor=2.0
marker.sort.distanceWeight=3.0
```

## How each parameter changes ordering

### typeWeight.Pin
Controls the crossover distance where a far-away pin loses to a zone at dead center.

| Pin weight | Pin at X meters ≈ Circle 200m at center | Effect |
|------------|----------------------------------------|--------|
| 0.05 | 4000m | Pin wins almost always |
| **0.15** | **~1333m** | **Current default** |
| 0.5 | 400m | Pin only wins when fairly close |
| 1.0 | 200m | Pin = Circle (no type advantage) |

### typeWeight.Corridor
Controls how much closer a corridor must be to beat a same-size circle.

| Corridor weight | Corridor 200m at 10m vs Circle 200m at Xm | Effect |
|-----------------|------------------------------------------|--------|
| 1.5 | Circle at ~153m | Corridor slightly penalized |
| **2.0** | **Circle at ~77m** | **Current default** |
| 3.0 | Circle at ~23m | Corridor heavily penalized |

### distanceWeight
Controls whether zone size or distance dominates within the same type.

| W | UC1: Circle 200m/190m vs Circle 500m/5m | Behavior |
|---|----------------------------------------|----------|
| 0 | 200(200) → 500(500) | Size-only (current) |
| 2 | 500(510) → 200(580) | Distance starts to matter |
| **3** | **500(515) → 200(770)** | **Current default** |
| 5 | 500(525) → 200(1150) | Distance dominates |

---

## ELI16 — Real-world scenarios

### Scenario A — "Phare de la Fourmigue"
You're boating near Cap d'Antibes. You have:
- Pin: "Phare de la Fourmigue" at 80m (ProximityMatch)
- Circle: "Plateau du Milieu" 200m radius, you're at the center (ZoneMatch)
- Corridor: "Chenal des Îles" 300m width, you're at the center (ZoneMatch)

Scores:
- Pin: 0.15 × 80 = **12**
- Circle: 1.0 × (200 + 0) = **200**
- Corridor: 2.0 × (300 + 0) = **600**

Display: `"Phare de la Fourmigue, Plateau du Milieu, Chenal des Îles"`

The lighthouse pin wins because it's a precise point of interest 80m away. The 200m circle comes next — you're inside a specific diving plateau. The corridor comes last — it's the widest, least specific zone.

### Scenario B — "Deep inside a big zone"
You're at the exact center of a 1000m corridor "Îles de Lérins". Nearby:
- Pin: "Bouée Cardinale" at 300m (ProximityMatch)
- Circle: "Anse de la Tradelière" 100m radius, you're at 95m from center (near edge, ZoneMatch)

Scores:
- Pin: 0.15 × 300 = **45**
- Circle: 1.0 × (100 + 95×3) = **385**
- Corridor: 2.0 × (1000 + 0) = **2000**

Display: `"Bouée Cardinale, Anse de la Tradelière, Îles de Lérins"`

Even deep inside a 1000m corridor, the corridor penalty (2.0) and its size (1000m) push it to last. The pin at 300m still wins — it's a precise point. The 100m circle at the edge barely beats the corridor (385 < 2000).

### Scenario C — "Tweaking distanceWeight"
Same markers as Scenario B, but you feel the corridor should rank higher because you're dead center. Lower distanceWeight to 1.0:

Scores at W=1:
- Pin: 0.15 × 300 = **45**
- Circle: 1.0 × (100 + 95) = **195**
- Corridor: 2.0 × (1000 + 0) = **2000**

Still last — the corridor penalty (2.0) dominates. At W=1, the pin and circle just get slightly closer scores (195 vs 385).

To make the corridor competitive, you'd need to lower typeWeight.Corridor (e.g., from 2.0 to 1.2):
- Corridor at 1.2: 1.2 × 1000 = **1200** — still behind Circle (195), but closer.

### Scenario D — "Two circles, which is first?"
- Circle A: 500m radius, you're at 5m from center (deep inside)
- Circle B: 200m radius, you're at 190m from center (near edge)

Current W=3:
- A: 1.0 × (500 + 5×3) = **515**
- B: 1.0 × (200 + 190×3) = **770**

Display: A first, then B. Even though B is smaller (200m vs 500m), being 5m from center of A feels more "inside" than being at the edge of B. W=3 makes this call.

At W=0 (size-only, old behavior): B(200) → A(500). The 200m circle wins because it's smaller, even though you're barely inside it.

### Scenario E — "Same type, same zone, different distances"
- Circle A: 200m radius, at 10m from center
- Circle B: 200m radius, at 150m from center

All W values give same result: A before B. Distance always breaks ties in the same direction.

---

## Implementation

### files changed
| File | Change |
|------|--------|
| `maro.properties` | 4 new keys |
| [`AppConfig.kt`](app/src/main/java/ykws/android/maro/config/AppConfig.kt) | 4 new properties |
| [`MarkerMatcher.kt`](app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt) | Replace `sizeOf()` with `sortScore()`, read from AppConfig |

### sortScore() implementation
```kotlin
private fun sortScore(match: WhereAmIMatch): Double {
    val typeWeight = when (markerOf(match).geometry) {
        is MarkerGeometry.Pin -> AppConfig.markerSortTypeWeightPin
        is MarkerGeometry.Circle -> AppConfig.markerSortTypeWeightCircle
        is MarkerGeometry.Corridor -> AppConfig.markerSortTypeWeightCorridor
    }
    val (zoneSize, distance) = when (match) {
        is WhereAmIMatch.ZoneMatch -> match.zoneSizeM to match.distanceToCenterM
        is WhereAmIMatch.ProximityMatch -> 0.0 to match.seaDistanceM
    }
    return typeWeight * (zoneSize + distance * AppConfig.markerSortDistanceWeight)
}
```

Replace `sortedBy { sizeOf(it) }` with `sortedBy { sortScore(it) }` in `depthFirstLeavesFirst()`.
