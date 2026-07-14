# Fix Plan — Spike Rejection Failures (Bad-spikes.gpx)

**Date:** 2026-07-12
**Status:** analysis complete, fixes designed
**Related:** [`FEAT_DSC_GPS.md`](FEAT_DSC_GPS.md) → `### fix-spike`

---

## 1. Root Cause Analysis

The spike rejection system in [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) has three independent failure modes, all visible in [`Bad-spikes.gpx`](Bad-spikes.gpx) (207 points, 3 stops).

### Architecture of current spike rejection

```
addPoint(sample)
  ├─ DEDUP: same position within 500ms? → return      // Fix D (hoisted — first gate)
  ├─ if isStopped → return                            // line 595
  ├─ if speedMps == null → return                      // line 599
  ├─ STALE TIMEOUT: >10s since last accepted?          // line 604
  │    ├─ Fix C: gpsSpeed < 2kn && dist > 150m → REJECT
  │    ├─ Relaxed: reject only if implied > 192 kn
  │    ├─ captureAcceptedPoint() → updates references
  │    ├─ lastValidCourseDeg = null; courseHistory.clear()
  │    └─ return (bypasses Gates 0-3)
  ├─ Gate 0: GPS recovery (lock false→true)            // line 626
  ├─ Gate 0.5: GPS-reported speed > 40 kn → REJECT     // Fix B
  ├─ Gate 1: Implied speed cap (32 kn sea / 120 land)  // line 642
  │    ├─ Fix C: gpsSpeed < 2kn && dist > 150m → REJECT
  │    └─ Course-aligned ×1.5, sideways ×0.5
  ├─ Gate 3: Acceleration (10 kn/s sea / 30 land)      // line 664
  └─ captureAcceptedPoint() → updates references       // line 693
```

### Failure 1: Stale Timeout Bypasses All Gates (→ 500-700m spatial spikes)

**Trigger:** When GPS fixes are >10s apart, the stale timeout at line 604 fires for **every** fix. The relaxed check only rejects implied speed > 192 kn (6× `BOAT_MAX_SPEED_KN`).

**Trace through [88]→[97] (the "stopped @ 19:35 for 7min" cluster):**

| Point | Time | Δt from prev | Stale? | Implied kn | 192 kn gate | Result |
|-------|------|-------------|--------|------------|-------------|--------|
| [88] | 19:43:31 | ~8 min | YES | calculated | passes | ACCEPTED → ref updated to [88] |
| [89] | 19:45:38 | 127s | YES | calculated | passes | ACCEPTED → ref updated to [89] |
| [90] | 19:45:57 | 19s | YES | **56.6** | < 192 | ACCEPTED → ref = spike! |
| [91] | 19:46:18 | 21s | YES | **66.1** | < 192 | ACCEPTED → ref = spike! |
| [92] | 19:46:20 | 2s | NO | ~33 | < cap | normal gates, may pass |
| [93] | 19:46:25 | 5s | NO | ~22 | < cap | passes |
| [94] | 19:46:44 | 19s | YES | ~5 | < 192 | ACCEPTED |
| [95] | 19:46:58 | 14s | YES | ~10.6 | < 192 | ACCEPTED |
| [96] | 19:47:49 | 51s | YES | **27.1** | < 192 | ACCEPTED |
| [97] | 19:48:03 | 14s | YES | **66.1** | < 192 | ACCEPTED |

**Root cause:** 192 kn is a "physically impossible teleport" filter, not a "spatial spike" filter. When GPS drift produces 500-700m jumps with implied speeds of 27-66 knots, every one passes the stale timeout's relaxed check. And because `captureAcceptedPoint` updates the reference position, each spike becomes the new baseline — poisoning all subsequent comparisons.

### Failure 2: No GPS-Reported Speed Validation (→ 44-45 knot spikes)

**Trigger:** Gate 1 only checks **implied** speed (`distance / time`), never the GPS-reported `sample.speedMps`.

**Trace through [97]→[98]→[99]→[100]:**

| Point | GPS Speed | Position vs prev | Implied kn | Gate 1 result |
|-------|-----------|-----------------|------------|---------------|
| [97] | 0.00 kn | — | — | Accepted via stale timeout |
| [98] | **44.55 kn** | SAME position | 0.0 | PASSES (0 < any cap) |
| [99] | **44.85 kn** | near same | ~small | PASSES |
| [100] | **44.85 kn** | 222m in 35s | ~12.4 | PASSES |

[97] was accepted via stale timeout with `lastValidSpeedKn = 0`. [98] arrives with `distance = 0` (same position) → `impliedSpeedKn = 0` → Gate 1 passes. Gate 3 (acceleration) would compute `abs(44.55 - 0) / 0.006s = 7425 kn/s` and reject — **but** [98] was likely accepted via stale timeout too (since [97] may have been skipped by `isStopped`, leaving `lastAcceptedTimeMs` stale from [96] at 19:47:49 → gap of 14s > 10s).

**Root cause:** The GPS-reported speed field is never validated against any cap. A GPS chip error reporting 44 kn while position stays fixed sails through Gate 1 because `impliedSpeedKn = 0`.

### Failure 3: Duplicate Emissions (→ 30+ sub-ms dupes, 11-point burst)

**Two distinct duplicate mechanisms:**

**3a. Sub-millisecond duplicates (1-8ms apart):** Same position/speed/course emitted twice within the same event-loop tick. These are code-level double-emissions from the `SharedFlow`/`combine` pipeline in [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) or [`CoastlineViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt). The `combine` flow may emit the same `TrackSample` when one of its source flows updates without changing the combined value, or the `SharedFlow` replay buffer may re-emit.

**3b. GPS chipset bursts (30-60ms apart):** The GPS chip re-emits the same fix multiple times. The massive burst at [159]→[170] shows 11 consecutive identical points (2.73 kn) over 657ms. Each emission is a distinct Android `Location` object from `LocationManager`, but with identical coordinates.

**3c. Mixed duplicate [194]→[195]:** Same position (43.58424, 7.09884) but different speed (14.18→11.49 kn) and different course (195°→118°) within 5ms. This is a genuine GPS re-fix — the chip computed a new speed/course for the same position. Not a bug, but should still be deduplicated.

---

## 2. Fix Plans

### Fix A: Tighten Stale-Timeout Speed Cap + Don't Poison References

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), `addPoint()` lines 604-624

**Problem:** `BOAT_MAX_SPEED_KN * 6.0` (192 kn) is too permissive. The stale timeout's `captureAcceptedPoint()` call updates `lastValidPointLat/Lon` and `lastValidCourseDeg`, poisoning references with spike positions.

**Changes:**

1. Replace the relaxed check (line 610-621) with a two-tier filter:
   - **Tier 1 (low speed):** If `sample.speedMps < LOW_SPEED_THRESHOLD_MPS` (~1 m/s = 2 kn), use a tight cap: `BOAT_MAX_SPEED_KN * 1.5` (48 kn). This catches stationary GPS drift while the boat isn't moving.
   - **Tier 2 (normal speed):** If GPS reports meaningful speed, use a moderate cap: `BOAT_MAX_SPEED_KN * 3.0` (96 kn). This allows legitimate position recovery after signal loss at speed.

2. When accepting via stale timeout, update `lastValidPointLat/Lon/TimeMs` but **do not update `lastValidCourseDeg`**. Instead, clear `courseHistory` and set `lastValidCourseDeg = null` — forcing course recalculation from the next 2-3 accepted points.

```kotlin
// New constants
private const val LOW_SPEED_MPS = 1.0          // ~2 kn — below this, treat as stationary
private const val STALE_CAP_LOW_SPEED = 1.5    // × BOAT_MAX_SPEED_KN = 48 kn
private const val STALE_CAP_NORMAL = 3.0       // × BOAT_MAX_SPEED_KN = 96 kn

// In stale timeout block (line 610-621):
if (lastValidPointLat != null && lastValidPointLon != null && lastValidPointTimeMs > 0L) {
    val refPos = LatLng(lastValidPointLat!!, lastValidPointLon!!)
    val distM = SpatialOperations.haversine(refPos, sample.position)
    val dtSec = (sample.timestampEpochMs - lastValidPointTimeMs) / 1000.0
    if (dtSec > 0.0) {
        val impliedKn = (distM / dtSec) * 1.94384
        val isLowSpeed = (sample.speedMps ?: 0.0) < LOW_SPEED_MPS
        val staleCap = BOAT_MAX_SPEED_KN * if (isLowSpeed) STALE_CAP_LOW_SPEED else STALE_CAP_NORMAL
        if (impliedKn > staleCap) {
            Log.w(TAG, "Spike reset REJECTED (${if (isLowSpeed) "low-speed" else "normal"}): implied=${"%.1f".format(impliedKn)}kn cap=${"%.1f".format(staleCap)}kn dist=${"%.0f".format(distM)}m")
            return
        }
    }
}
captureAcceptedPoint(sample)
// Clear course history — don't trust the spike position for direction
lastValidCourseDeg = null
courseHistory.clear()
return
```

**Impact on Bad-spikes.gpx:**
- [89]→[90]: speed=0 (< 2kn), implied=56.6 → cap=48 → **REJECTED** ✓
- [90]→[91]: speed=0, implied=66.1 → cap=48 → **REJECTED** ✓
- [95]→[96]: speed=0, implied=27.1 → cap=48 → **PASSES** (this is marginal — 717m in 51s at 0 speed)
- [96]→[97]: speed=0, implied=66.1 → cap=48 → **REJECTED** ✓

The 717m spike at [95]→[96] (27 kn implied) would still pass the 48 kn cap. Covered by Fix C below.

### Fix B: Add GPS-Reported Speed Gate

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), `addPoint()` before Gate 1 (after line 637)

**Problem:** Gate 1 only checks implied speed (distance/time), never the GPS-reported `sample.speedMps`. When position doesn't change (distance=0), implied speed is 0 and any GPS speed passes.

**Changes:** Add a simple GPS speed cap check before the existing Gate 1:

```kotlin
// New: Gate 0.5 — GPS-reported speed cap (sea mode only)
if (!isOnLand) {
    val gpsSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: 0.0
    val gpsSpeedCap = BOAT_MAX_SPEED_KN * 1.25  // 40 kn
    if (gpsSpeedKn > gpsSpeedCap) {
        logRejection("gps speed", gpsSpeedKn, gpsSpeedCap)
        consecutiveRejections++
        checkLandDetection(sample)  // after 5 rejections at >32kn → isOnLand=true → gate disabled
        return
    }
}
```

**Impact on Bad-spikes.gpx:**
- [98]: GPS speed=44.55 kn < 48 → **PASSES** (but this is close)
- If `BOAT_MAX_SPEED_KN * 1.25` (40 kn): 44.55 > 40 → **REJECTED** ✓

**`!isOnLand` guard rationale:** In sea mode, cap GPS speed at 40 kn. After 5 consecutive rejections at >32 kn, `checkLandDetection` flips `isOnLand = true`, disabling this gate. Car-mode points then flow through Gate 1 with the 120 kn land cap. Recovery: 10 consecutive points at ≤32 kn flips back to sea mode and the 40 kn gate reactivates.

**GPS speed cap fixed at 40 kn (×1.25).** No boat in this region exceeds 40 kn; the `checkLandDetection` fallback handles the car-testing edge case automatically.

### Fix C: Absolute Distance Cap When Stationary

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), `addPoint()` inside Gate 1 block (after line 638)

**Problem:** When GPS-reported speed is very low (< 2 kn) but implied speed is moderate (20-30 kn), the gates pass because 20-30 kn < 32-48 kn caps. These are GPS drift positions that happen to be far enough to produce concerning implied speeds but not far enough to trigger caps.

**Changes:** Add an absolute distance check when GPS-reported speed indicates the boat is nearly stationary:

```kotlin
// Inside the Gate 1 block, after computing distM and impliedSpeedKn:
val gpsSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: 0.0
if (gpsSpeedKn < 2.0 && distM > MAX_STATIONARY_DRIFT_M) {
    logRejection("stationary drift", distM, MAX_STATIONARY_DRIFT_M.toDouble())
    consecutiveRejections++
    checkLandDetection(sample)  // keep land-detection counter in sync
    return
}
```

```kotlin
// New constant
private const val MAX_STATIONARY_DRIFT_M = 150  // max plausible GPS drift while stationary
```

**Impact on Bad-spikes.gpx:**
- [95]→[96]: speed=0, dist=717m, 717 > 150 → **REJECTED** ✓
- All other zero-speed spikes > 150m → **REJECTED** ✓

This catches the case Fix A's 48 kn cap misses (717m/51s = 27 kn < 48 kn).

### Fix D: Deduplicate Consecutive Identical Points

**File:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), `addPoint()` at the top of the method (before line 544 — before the `isStopped` check and stale timeout), NOT inside `captureAcceptedPoint`

**Placement rationale:** Hoisting Fix D above the stale timeout prevents duplicate bursts from entering the spike rejection machinery entirely — each duplicate would otherwise trigger stale-timeout log spam and redundant gate evaluations before being dedup-rejected in `captureAcceptedPoint`.

**Problem:** No deduplication exists. Code-level double-emissions and GPS chipset bursts produce duplicate points.

**Changes:** Add a simple same-position dedup check:

```kotlin
// New fields
private var lastAcceptedLat: Double? = null
private var lastAcceptedLon: Double? = null
private var lastAcceptedTimeWallMs: Long = 0L

// New constant
private const val DEDUP_WINDOW_MS = 500L  // skip identical positions within this window
```

In `addPoint()`, at the top of the method (before `isStopped` check, line 544):

```kotlin
// Dedup: skip if same position as last accepted point within DEDUP_WINDOW_MS
val now = System.currentTimeMillis()
if (lastAcceptedLat != null && lastAcceptedLon != null &&
    sample.position.latitude == lastAcceptedLat &&
    sample.position.longitude == lastAcceptedLon &&
    now - lastAcceptedTimeWallMs < DEDUP_WINDOW_MS) {
    Log.v(TAG, "Dedup: skipped identical position within ${now - lastAcceptedTimeWallMs}ms")
    return
}
lastAcceptedLat = sample.position.latitude
lastAcceptedLon = sample.position.longitude
lastAcceptedTimeWallMs = now
```

**Initialization in `beginRecording()` (after line 466):**
```kotlin
lastAcceptedLat = null
lastAcceptedLon = null
lastAcceptedTimeWallMs = 0L
```

**Initialization in `resume()` (after line 302):**
```kotlin
lastAcceptedLat = null
lastAcceptedLon = null
lastAcceptedTimeWallMs = 0L
```

**Impact on Bad-spikes.gpx:**
- All 30+ sub-ms duplicates → **SKIPPED** ✓
- 11-point burst [159]→[170] → 10 points **SKIPPED** ✓
- Mixed duplicate [194]→[195] (same position, different speed/course 14.18→11.49kn): **SKIPPED** — position matches, so dedup fires. Acceptable: a genuine GPS re-fix at the same coordinates within 5ms contributes negligible tracking value.

### Fix E (Optional): Investigate SharedFlow Double-Emission Source

**File:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), `combine` flow that feeds `TrackSample` to `TrackRecorder`

**Problem:** Sub-ms duplicates suggest the same `TrackSample` is emitted twice. Fix D handles the symptom, but the root cause should be fixed at the source.

**Investigation steps:**
1. Add logging in the `combine` lambda that creates `TrackSample` — log a counter or hash.
2. Check if the `SharedFlow` replay buffer is re-emitting (if `replay > 0`).
3. Check if multiple collectors are attached to the same flow.
4. Verify that the `combine` doesn't re-trigger when one source flow emits the same value.

This is lower priority — Fix D provides defense in depth regardless of source.

---

## 3. Implementation Order

| Priority | Fix | File | Lines | Risk |
|----------|-----|------|-------|------|
| **P0** | Fix A: Tighten stale timeout | TrackRecorder.kt | ~15 | Low — narrows an existing gate |
| **P0** | Fix C: Stationary distance cap | TrackRecorder.kt | ~5 | Low — additive check |
| **P1** | Fix B: GPS speed gate | TrackRecorder.kt | ~5 | Low — additive check |
| **P1** | Fix D: Dedup identical points | TrackRecorder.kt | ~10 | Low — additive check |
| **P2** | Fix E: Source investigation | MapScreen.kt | ~5 | Medium — may touch flow wiring |

All fixes are in [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt), `addPoint()` and `captureAcceptedPoint()` methods. No new files, no new dependencies.

---

## 4. Ask Review Refinements (2026-07-12)

Review by Ask mode confirmed technical soundness. Five refinements applied:

| # | Refinement | Applied |
|---|-----------|---------|
| 1 | Fix C rejection path now calls `checkLandDetection(sample)` — keeps land-detection counter in sync with `consecutiveRejections` | ✓ |
| 2 | Fix D clarified: [194]→[195] is SKIPPED (position match), not PASSES — previous analysis was wrong about the code outcome | ✓ |
| 3 | Fix D hoisted to top of `addPoint()` (before `isStopped` and stale timeout) — duplicates short-circuit before entering spike rejection machinery, avoiding stale-timeout log spam during bursts | ✓ |
| 4 | Fix D fields explicitly initialized in `beginRecording()` and `resume()` — prevents stale dedup state across recording sessions | ✓ |
| 5 | GPS speed cap decision point documented: 40 kn recommended, 48 kn safe fallback. `checkLandDetection` provides auto-escalation if 5 consecutive rejections occur at >32 kn | ✓ |

**Confirmed safe interactions:**
- Gate 0 correctly bypasses Fix B (GPS speed after lock recovery is unreliable)
- Fix C rejects don't update `lastValidSpeedKn` — no poisoning of Gate 3 acceleration baseline
- `lastValidCourseDeg = null` + `courseHistory.clear()` on stale timeout accept is safe — neutral 32 kn cap applies for next 1-2 points, which is actually more restrictive than the course-aligned ×1.5 path

---

## 5. Verification

After implementation:
1. Build: `apk-build.bat`
2. Replay [`Bad-spikes.gpx`](Bad-spikes.gpx) through the recording pipeline (or instrument a unit test)
3. Verify:
   - Zero-speed spatial spikes [89]→[97] are rejected
   - 44-45 kn speed spikes [98]→[100] are rejected
   - Duplicate points are deduplicated (point count < 207)
   - Legitimate movement (e.g., [8]→[22], [156]→[206]) still records correctly
