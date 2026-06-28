# Spike Rejection: Stale-Timeout Fix

## Problem

4 km GPS spike at point [091] passed through `TrackRecorder` spike rejection because:

1. **Stale-fix timeout** (`STALE_FIX_TIMEOUT_MS = 10s`, line 40) fired after 14s gap, accepting [091] unconditionally at line 331-336.
2. **Same-ms bug** at line 351 (`timeDeltaSec > 0.0` excludes zero) let [092] skip all spike gates when emitted in the same millisecond as [091].

## Fix: Two targeted changes in `TrackRecorder.kt`

### Change 1 — Replace unconditional timeout acceptance with relaxed cap (lines 330-336)

**Current:**
```kotlin
if (lastAcceptedTimeMs > 0L && System.currentTimeMillis() - lastAcceptedTimeMs > STALE_FIX_TIMEOUT_MS) {
    Log.w(TAG, "Spike reset: ${(System.currentTimeMillis() - lastAcceptedTimeMs) / 1000}s since last accepted sample — accepting unconditionally")
    lastHadLock = sample.hasLock
    captureAcceptedPoint(sample)
    return
}
```

**Replace with:**
```kotlin
if (lastAcceptedTimeMs > 0L && System.currentTimeMillis() - lastAcceptedTimeMs > STALE_FIX_TIMEOUT_MS) {
    Log.w(TAG, "Spike reset: ${(System.currentTimeMillis() - lastAcceptedTimeMs) / 1000}s since last accepted sample")
    lastHadLock = sample.hasLock
    // Relaxed check: reject only physically impossible jumps (>6× boat max speed ≈ 192 kn).
    // This catches 4 km GPS spikes while allowing legitimate position recovery after outage.
    if (lastValidPointLat != null && lastValidPointLon != null && lastValidPointTimeMs > 0L) {
        val refPos = LatLng(lastValidPointLat!!, lastValidPointLon!!)
        val distM = SpatialOperations.haversine(refPos, sample.position)
        val dtSec = (sample.timestampEpochMs - lastValidPointTimeMs) / 1000.0
        if (dtSec > 0.0) {
            val impliedKn = (distM / dtSec) * 1.94384
            if (impliedKn > BOAT_MAX_SPEED_KN * 6.0) {
                Log.w(TAG, "Spike reset REJECTED: implied=${"%.1f".format(impliedKn)}kn dist=${"%.0f".format(distM)}m dt=${"%.1f".format(dtSec)}s")
                return
            }
        }
    }
    captureAcceptedPoint(sample)
    return
}
```

**Why 6× multiplier:** `BOAT_MAX_SPEED_KN = 32`, so cap = 192 knots. No recreational vessel, car, or civilian vehicle reaches 192 kn (356 km/h). The 4 km spike at 14s = 560 kn → caught. A 100 m GPS recovery jump at 14s = 14 kn → passes. No false rejections possible.

### Change 2 — Handle same-ms fixes (line 351 + block restructure)

**Current (lines 351-389):**
```kotlin
                if (timeDeltaSec > 0.0) {
                    val impliedSpeedKn = (distM / timeDeltaSec) * 1.94384
                    // Gate 1 ...
                    // Gate 2: Direction ...
                    // Gate 3: Acceleration ...
                    consecutiveRejections = 0
                }
```

**Replace with:**
```kotlin
                if (timeDeltaSec > 0.0) {
                    val impliedSpeedKn = (distM / timeDeltaSec) * 1.94384
                    // Gate 1: Context speed cap
                    val baseCap = if (isOnLand) LAND_MAX_SPEED_KN else BOAT_MAX_SPEED_KN
                    // Gate 2: Direction (sea only)
                    val effectiveCap = if (!isOnLand && lastValidCourseDeg != null) {
                        val bearingToSample = SpatialOperations.initialBearing(lastValidPos, sample.position)
                        val delta = angularDistance(bearingToSample, lastValidCourseDeg!!)
                        when {
                            delta <= COURSE_ALIGNED_DEG -> baseCap * COURSE_ALIGNED_MULTIPLIER
                            else -> baseCap * COURSE_SIDEWAYS_MULTIPLIER
                        }
                    } else {
                        baseCap
                    }
                    if (impliedSpeedKn > effectiveCap) {
                        logRejection("speed cap", impliedSpeedKn, effectiveCap)
                        consecutiveRejections++
                        checkLandDetection(sample)
                        return
                    }
                    // Gate 3: Acceleration
                    val currentSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: impliedSpeedKn
                    val accelKnPerSec = abs(currentSpeedKn - lastValidSpeedKn) / timeDeltaSec
                    val accelLimit = if (isOnLand) MAX_ACCEL_KN_PER_SEC_LAND else MAX_ACCEL_KN_PER_SEC_SEA
                    if (accelKnPerSec > accelLimit) {
                        logRejection("acceleration", accelKnPerSec, accelLimit)
                        consecutiveRejections++
                        return
                    }
                    consecutiveRejections = 0
                } else {
                    // Same-ms or out-of-order: max 30 m position change without time progression.
                    // GPS precision floor for a stationary receiver — beyond this is a spike.
                    if (distM > 30.0) {
                        logRejection("same-ms jump", distM, 30.0)
                        consecutiveRejections++
                        return
                    }
                }
```

**Why 30 m:** GPS CEP (circular error probable) is ~5-10 m for consumer devices. 30 m is 3× the worst-case error — conservative enough to never reject legitimate same-second fixes, but tight enough to catch coordinate teleports.

## Verification against the spike scenario

| Step | Before fix | After fix |
|------|-----------|-----------|
| [090] (stale recovery) | Accepted unconditionally | Accepted (dist from [089] = ~17 m over 18 min = 0.03 kn < 192 kn) ✅ |
| [091] (4 km spike) | Accepted unconditionally 🔴 | **Rejected** (4027 m / 14s = 560 kn > 192 kn) ✅ |
| [092] (recovery) | Skipped spike check (same-ms) | Compared against [090]: ~2 m / 1s = 4 kn — accepted ✅ |

## Side effects: none

- **Change 1** only affects the timeout recovery path (rare — requires >10s gap). Normal sampling never hits this.
- **Change 2** adds an else branch for `timeDeltaSec ≤ 0` — catches same-ms or out-of-order GPS timestamps. Legitimate same-second GPS fixes are typically <5 m apart; 30 m threshold gives 6× safety margin.
- **No new constants** — uses existing `BOAT_MAX_SPEED_KN` with multiplier, and a hardcoded 30 m that's documented inline.
- **No performance impact** — the extra haversine in Change 1 runs only on timeout recovery (rare). Change 2 replaces a skipped block with a trivial distance comparison (cheap).
