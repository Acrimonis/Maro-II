# BoatTrace — Merge: Inter-Track Gap as Idle + Moving

**Created:** 2026-08-08 15:50 UTC
**Status:** planned
**Branch:** feature/merge-track-idling
**Depends on:** `260714_FEAT_PLN_BoatTrace_merge-tracks.md` (Merge Tracks — already implemented)

---

## Problem

[`TrackMerger.merge()`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt:97-98) sums per-track `idleDurationSec` and `navigatingDurationSec` but ignores the time gap **between** tracks:

```
merged.idleDurationSec       = Σ(source.idleDurationSec)
merged.navigatingDurationSec = Σ(source.navigatingDurationSec)
```

The inter-track gap (e.g., 2 hours between `track[N].endTimeMs` and `track[N+1].startTimeMs`) is invisible — counted as neither idle nor navigating. The invariant `totalElapsedSec = navigating + idle` does not hold for merged tracks.

## Proposal

For each adjacent pair `(track[N], track[N+1])`, estimate how much of the gap was travel (moving) vs stationary (idle):

```
gapSec        = (track[N+1].startTimeMs - track[N].endTimeMs) / 1000
gapDistanceM  = distance(track[N].lastPoint, track[N+1].firstPoint)
pairAvgMps    = (v1*p1 + v2*p2) / (p1+p2)   // weighted by point count
estMovingSec  = min(gapDistanceM / pairAvgMps, gapSec)
gapIdleSec    = gapSec - estMovingSec
```

Then fold into the merged stats:

```
merged.idleDurationSec       = Σ(source.idleDurationSec) + Σ(gapIdleSec)
merged.navigatingDurationSec = Σ(source.navigatingDurationSec) + Σ(estMovingSec)
```

The invariant now holds: `totalElapsedSec = navigating + idle`.

## Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| G1 | **Weighted average speed by point count** | `pairAvgMps = (v1*p1 + v2*p2)/(p1+p2)`. A 2-hour track with 2000 points dominates a 1-minute track with 5 points. |
| G2 | **Clamp `estMovingSec` to `gapSec`** | If gapDistanceM is anomalously large (e.g., tracks recorded in different locations), `estMovingSec` cannot exceed the actual elapsed gap. Floor at 0 implicit via `gapSec - estMovingSec`. |
| G3 | **Negative/zero gap → skip pair** | If tracks overlap in time (shouldn't happen due to `sortedBy startTimeMs`, but defensive), `gapSec <= 0` → add 0 to both accumulators. |
| G4 | **Distance uses last/first real points** | `track.trackPoints.last()` and `track.trackPoints.first()` are always real positions (GAP markers are only inserted *during* merge, not present in source tracks). |
| G5 | **`distanceNm` unchanged** | Distance is point-to-point within polyline geometry. Straight-line gap distance isn't part of the track path — no points exist for it. |

## Algorithm — updated `TrackMerger.merge()` step 4

Replace the current stats assembly (lines 93-98) with:

```
4. Compute stats from per-track values + inter-track gaps:

   // 4a. Per-track aggregates (unchanged)
   totalPoints = sum of track.trackPoints.size for each track
   avgSpeedMps = if totalPoints > 0:
     sum(track.averageSpeedMps * track.trackPoints.size) / totalPoints
   else: 0f

   // 4b. Inter-track gap idle/moving estimation  ← NEW
   // Accumulate as Double to avoid per-pair .toLong() truncation; round at end.
   var gapIdleAccum = 0.0
   var gapMovingAccum = 0.0

   for i in 0 until tracks.size - 1:
     val a = tracks[i]
     val b = tracks[i + 1]
     val gapMs = b.startTimeMs - a.endTimeMs!!
     if (gapMs <= 0) continue  // overlapping or zero gap

     val gapSec = gapMs / 1000.0
     val lastPt = a.trackPoints.last()
     val firstPt = b.trackPoints.first()
     val gapDistM = SpatialOperations.haversine(lastPt.lat, lastPt.lon, firstPt.lat, firstPt.lon)

     // Weighted average speed of the pair (Float → Double for math)
     val pA = a.trackPoints.size.toDouble()
     val pB = b.trackPoints.size.toDouble()
     val pairAvgMps = if (pA + pB > 0.0) {
         (a.averageSpeedMps.toDouble() * pA + b.averageSpeedMps.toDouble() * pB) / (pA + pB)
     } else avgSpeedMps.toDouble()  // fallback to overall merged avg (shouldn't happen)

     val estMovingSec = if (pairAvgMps > 0.0) {
         min(gapDistM / pairAvgMps, gapSec)
     } else 0.0

     gapMovingAccum += estMovingSec
     gapIdleAccum += (gapSec - estMovingSec)

   val gapMovingSec = gapMovingAccum.toLong()
   val gapIdleSec = gapIdleAccum.toLong()
```

Step 5 assemble with updated values:

```
5. Assemble merged track:
   Track(
     ...
     navigatingDurationSec = tracks.sumOf { it.navigatingDurationSec } + gapMovingSec,
     idleDurationSec = tracks.sumOf { it.idleDurationSec } + gapIdleSec,
     ...
   )
```

## Distance: reuse `SpatialOperations.haversine()`

The codebase already has a battle-tested Haversine in [`SpatialOperations.haversine()`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt). **Reuse it** — do not add a private copy.

Add import: `import ykws.android.maro.spatial.SpatialOperations`

This adds a dependency on the `spatial` package, but `TrackMerger` is already in the `data` layer which depends on `spatial` for other types.

## Edge Cases

| Scenario | Handling |
|----------|----------|
| `gapMs <= 0` (overlapping tracks) | `continue` — skip pair entirely |
| `gapDistM ≈ 0` (same location) | `estMovingSec ≈ 0`, entire gap → idle. Correct. |
| `pairAvgMps == 0` (both tracks stationary) | `estMovingSec = 0`, entire gap → idle |
| `estMovingSec > gapSec` (distance implies faster travel than gap allows) | Clamped: `min(dist/avg, gapSec)`. No negative idle. |
| Single-point tracks | `lastPoint == firstPoint` → distance 0 → all idle. Reasonable. |
| Merging 10+ tracks | O(n) loop over pairs, O(1) per pair. Negligible. |

## Files Touched

| File | Change |
|------|--------|
| [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt) | Replace step 4 stats assembly with gap-aware version; add `import ykws.android.maro.spatial.SpatialOperations` |

Only one file — the change is entirely internal to `TrackMerger.merge()`. No new private Haversine — reuses the existing `SpatialOperations.haversine()`.

## Verification

- **Unit test:** `TrackMergerTest` — merge two tracks with known gap distance/time, assert `idleDurationSec` and `navigatingDurationSec` include gap estimates.
- **Invariant:** `merged.navigatingDurationSec + merged.idleDurationSec == (merged.endTimeMs - merged.startTimeMs) / 1000` (within 1s rounding tolerance).
