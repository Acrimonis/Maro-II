# BoatTrace — Unified Idle Reconciliation, BoatMarker Close, GPX Timezone Fixes

**Created:** 2026-08-15 13:10 UTC
**Status:** planned (2× reviewed, refined)
**Branch:** feature/tracks-mgt
**Evidence:** `🤿Le Village Englouti + Feu Artifice .gpx`

---

## Evidence summary (from the GPX)

| Metric | Stored | Actual (point timeline) |
|--------|--------|-------------------------|
| Total span | 4h35m | 4h35m |
| Idle | 51min (3082s) | ~3h36m (gaps >45s) |
| Navigating | 3h44m | ~1h |

The 2h34m dive stop (16:45 → 19:19 UTC) contributed **zero** idle because no GPS samples were delivered while the screen was off / app backgrounded.

---

## Core concept — a compound idle predicate

Idle is neither purely spatial nor purely speed — it is **both**, as two conditions that must hold together:

```
IDLE_MAX_SPEED_MPS = 0.5   // ≈ 1 knot — implied speed ceiling for "parked"
IDLE_MAX_DRIFT_M   = 500.0 // net displacement ceiling for "stayed in the area"

isIdle(d, Δt) = Δt > 0 && (d / Δt < IDLE_MAX_SPEED_MPS) && (d < IDLE_MAX_DRIFT_M)
```

### Why both conditions

| Gap | d/Δt | d | Verdict | Why |
|-----|------|---|---------|-----|
| 100 m in 1 min | 1.67 m/s | 100 m | **moving** | speed ceiling catches the quick hop |
| 100 m in 30 min | 0.055 m/s | 100 m | **idle** | slow AND within the area |
| 3 km in 2 h | 0.42 m/s | 3000 m | **moving** | drift cap catches slow sustained drift |
| dive stop, 2h34m | 0.003 m/s | ~30 m | **idle** | slow AND within the area |

The speed condition handles the user's "100 m in 1 min vs 30 min" distinction; the drift cap prevents a multi-hour gap's near-zero implied speed from swallowing real travel (3 km over 2 h averages only 0.42 m/s).

**Note:** these are dedicated constants — `GpsLocationSource.MIN_SPEED_MPS` is a heading-trust threshold, and `AdaptiveGpsPolicy` uses a 15 m / 45 s displacement rule; neither is the reconciled-idle classifier. Use the two new constants above.

### One predicate, two call sites

| Call site | Silent interval | `isIdle` → | `!isIdle` → |
|-----------|-----------------|-----------|-------------|
| **On save** (within one track) | gap between consecutive raw points (GAP seams handled per M1) | idle += `dt` | navigating (single transit) |
| **Merge** (interrupted tracks) | gap between `track[N].endTimeMs` and `track[N+1].startTimeMs` | idle += `Δt` (whole gap) | `moving = min(Δt, d/v_ref)`, idle += `Δt − moving` |

`v_ref` = weighted average `averageSpeedMps` of the two merged tracks.

---

## Fix 1 — Within-track idle reconciliation on save

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt)

**Root cause:** idle accumulates only on `moving→idle` / `idle→moving` sample transitions. Long sample-free gaps are invisible and silently land in `navigatingDurationSec`.

**Fix:** at `finalizeTrack()`, derive idle from the **raw** point timeline via the compound predicate, then `maxOf(live, timeline)`.

### Helper (run on RAW points — before simplification)

```kotlin
private fun computeTimelineIdleSec(points: List<TrackPoint>): Long {
    var idle = 0L
    for (i in 1 until points.size) {
        if (points[i].type == PointType.GAP || points[i - 1].type == PointType.GAP) continue
        val dtSec = (points[i].timeOffsetMs - points[i - 1].timeOffsetMs) / 1000.0
        if (dtSec <= 0) continue
        val dM = SpatialOperations.haversine(
            LatLng(points[i - 1].lat, points[i - 1].lon),
            LatLng(points[i].lat, points[i].lon)
        )
        if (dM / dtSec < IDLE_MAX_SPEED_MPS && dM < IDLE_MAX_DRIFT_M) {
            idle += dtSec.toLong()
        }
    }
    return idle
}
```

### Resume seam — always mark, never blind-subtract (M1)

`resume()` currently relies on `detectAndInsertGap()` which inserts a GAP only when `dt > 120s` or `dist > 200m`. Instead:

1. **Always** insert a GAP marker on the first post-resume point (a resume is always a genuine discontinuity) — force the seam, so `computeTimelineIdleSec` skips it deterministically.
2. **Drop** the blind `timelineIdleSec − resumeGapDurationSec` subtraction. Keep `resumeGapDurationSec` only in the `navigatingDurationSec` formula, as today.
3. Known nuance (accepted, documented): if the boat is stationary immediately after resume, the post-resume idle before the first new point is grouped with the seam and not counted. Minor; the live accumulator still captures it if samples were arriving.

### finalizeTrack() integration (B1 fixed: sweep is applied)

```kotlin
val finalizeTimeMs = System.currentTimeMillis()

val reconciledIdleSec = maxOf(idleDurationSec, computeTimelineIdleSec(track.trackPoints))
    .coerceAtMost(totalElapsedSec)

// Sweep-close open IDLE markers (see Fix 3).
val finalMarkers = trackAfterClose.boatMarkers.map { bm ->
    if (bm.trigger == BoatMarkerTrigger.IDLE && bm.endTimeMs == null) {
        bm.copy(endTimeMs = finalizeTimeMs)
    } else bm
}

val finalized = trackAfterClose.copy(
    trackPoints = simplifiedPoints,
    boatMarkers = finalMarkers,          // ← B1: apply the sweep
    endTimeMs = finalizeTimeMs,
    pausedDurationSec = 0,
    idleDurationSec = reconciledIdleSec,
    averageSpeedMps = avgMps,
    distanceNm = cumulativeDistanceNm,
    navigatingDurationSec = (totalElapsedSec - reconciledIdleSec - resumeGapDurationSec)
        .coerceAtLeast(0),
    updatedAtEpochMs = finalizeTimeMs,
    visibleOnMap = true
)
```

### Design decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Reconcile on **raw** points | Captures mid-leg stops that simplification would erase; also keeps original `timeOffsetMs` untouched. |
| D2 | Always mark resume seam with a GAP; no blind subtraction | Deterministic seam skip. `resumeGapDurationSec` stays only in the navigating formula — never double-subtracted. |
| D3 | Compound predicate (speed AND drift cap) | Speed distinguishes 100 m / 1 min vs 30 min; drift cap stops multi-hour gaps swallowing travel. |
| D4 | `maxOf(live, timeline)` | Live wins when larger; timeline upgrades when samples were missing. |
| D5 | Clamp `idle ≤ totalElapsed`, `navigating ≥ 0` | Guards against over-count. |

---

## Fix 2 — Merge gap uses the same compound predicate

**File:** [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt)

**Current behavior (shipped):** always decomposes the inter-track gap via `moving = min(gapSec, d/pairAvgMps)`. It never takes a "same area" shortcut, and a speed-only shortcut would swallow slow sustained drift.

**Fix:** guard the decomposition with the same compound predicate:

```kotlin
for (i in 0 until tracks.size - 1) {
    val a = tracks[i]
    val b = tracks[i + 1]
    val gapMs = b.startTimeMs - a.endTimeMs!!
    if (gapMs <= 0) continue

    val gapSec = gapMs / 1000.0
    val dM = SpatialOperations.haversine(
        LatLng(a.trackPoints.last().lat, a.trackPoints.last().lon),
        LatLng(b.trackPoints.first().lat, b.trackPoints.first().lon)
    )

    if (dM / gapSec < IDLE_MAX_SPEED_MPS && dM < IDLE_MAX_DRIFT_M) {
        gapIdleAccum += gapSec          // stayed in the area — whole gap idle
    } else {
        val pairAvgMps = weightedAvgSpeed(a, b)
        val estMovingSec = if (pairAvgMps > 0) minOf(dM / pairAvgMps, gapSec) else 0.0
        gapMovingAccum += estMovingSec
        gapIdleAccum += gapSec - estMovingSec
    }
}
```

Then `navigatingDurationSec = Σ(navigating) + gapMovingAccum.toLong()` and `idleDurationSec = Σ(idle) + gapIdleAccum.toLong()` as today.

### Design decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D6 | Same compound predicate as Fix 1 | One definition of "idle" across single tracks and merged tracks. |
| D7 | Moved branch keeps `d/v_ref` decomposition | Recovers "traveled, then waited at destination" for genuinely different areas. |

---

## Fix 3 — Close open IDLE BoatMarkers at finalize

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt)

**Root cause:** `closeOpenBoatMarker()` is gated on `session.boatMarkerIndex`, and the finalize flush only runs when `wasStopped && idleStartMs > 0`. An IDLE marker can be left with `endTimeMs == null`.

**Fix:** the defensive sweep shown in Fix 1 (IDLE-only, reusing `finalizeTimeMs`), applied via `boatMarkers = finalMarkers` in the `copy(...)`.

After the sweep, re-run `recomputeDescription()` so the newly closed durations appear in the track description text (e.g. "… for 2h 34min").

### Design decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D8 | Restrict sweep to `BoatMarkerTrigger.IDLE` | MANUAL markers are instantaneous events with no `endTimeMs` by design. |
| D9 | Single `finalizeTimeMs` reused for sweep + copy | `trackAfterClose.endTimeMs` is null at the sweep point. |
| D10 | Sweep is idempotent | Only mutates `endTimeMs == null`; the active marker is already closed before the sweep. |
| D11 | Refresh description after sweep | Closed durations must be reflected in the `comment`. |

---

## Fix 4 — GPX export timezone

**File:** [`GpxExporter.kt`](app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt:9)

**Root cause:** `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)` treats `Z` as a literal and uses device-local timezone.

```kotlin
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
```

- `toGpx()` is the only export writer — this formatter is the sole time formatter in the export path.
- `formatStopLine`/`formatTimestamp` render local display times and stay local.
- **Importer:** [`GpxImporter.parseIsoTime()`](app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt:241) already rewrites `Z`→`+0000` and parses the explicit offset correctly — **no change needed there.** (Earlier claim that foreign import was shifted was wrong.)

---

## Files Touched

| File | Change |
|------|--------|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | `computeTimelineIdleSec()` compound predicate; always-mark resume GAP; `boatMarkers = finalMarkers` + `finalizeTimeMs` + `recomputeDescription()` after sweep |
| [`TrackMerger.kt`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt) | Compound-predicate same-area shortcut before `d/v_ref` decomposition |
| [`GpxExporter.kt`](app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt) | UTC timezone on `isoFormat` |

## Verification

- **Unit test:** `computeTimelineIdleSec` — 100 m / 1 min → not idle; 100 m / 30 min → idle; 3 km / 2 h → not idle; GAP seam skipped.
- **Unit test:** merge — 30 m apart over 2 h → whole gap idle; 5 km over 2 h → travel + idle decomposition.
- **Unit test:** finalize with open IDLE marker → closed + comment refreshed; open MANUAL marker → untouched.
- **Regression:** re-export `🤿Le Village Englouti` → first `<time>` 16:17:26Z; idle ≈ 3h36m; navigating ≈ 1h.
