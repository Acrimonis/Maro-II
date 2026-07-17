# Spike Rejection v2 — Course-Aware + Acceleration Gate + GPS Recovery

**Feature:** BoatTrace | **Subfeature:** track now demo | **Date:** 2026-06-22

## ELI16: Why the current rule is wrong

The current rule is a one-number bouncer: "implied speed > 50 knots? REJECTED."

It has three blind spots:
1. **Doesn't know direction.** A GPS ghost bouncing off a cliff to port gets the same treatment as your boat accelerating along its heading.
2. **Doesn't know context.** Boat at sea (max 32 kn hull speed) vs car on highway (120 km/h) — same 50 kn cap for both.
3. **Doesn't know inertia.** A Tesla launching 0→60 in 3 seconds and a trawler accelerating 5→8 kn over 10 seconds get the same check. Boats don't teleport.

## ELI16: The new algorithm

Four gates, applied only in GPS mode. Demo mode skips everything.

```
New GPS fix
    │
    ├─ Gate 0: GPS just recovered? (hasLock false→true)
    │     YES → ACCEPT (it's GPS coming back, not a spike)
    │
    ├─ Gate 1: Context — at sea or on land?
    │     Self-tunes from GPS speed history
    │     Sea → cap = 32 kn, direction check ON, accel = 10 kn/s
    │     Land → cap = 120 kn, direction check OFF, accel = 30 kn/s
    │
    ├─ Gate 2: Direction (SEA ONLY)
    │     Compute bearing from last valid point → new fix
    │     Compare against course from last 2-3 accepted points
    │     Aligned (±30°) → cap × 1.5 (= 48 kn)
    │     Sideways (>30°) → cap × 0.5 (= 16 kn)
    │     No course history yet → × 1.0
    │     ON LAND: this gate is skipped (cars turn 90° at intersections)
    │
    ├─ Gate 3: Acceleration
    │     |currentSpeed − lastSpeed| / timeDelta
    │     Sea: > 10 kn/s → REJECT
    │     Land: > 30 kn/s → REJECT
    │
    └─ ACCEPT → update trackers
```

## Land/sea auto-detection

Works like a thermostat — no coastline data needed.

```
If 5 consecutive fixes rejected AND GPS speed > 32 kn:
    → switch to LAND mode (cap 120 kn, direction OFF, accel 30 kn/s)

If 10 consecutive fixes accepted AND GPS speed ≤ 32 kn:
    → switch back to SEA mode (cap 32 kn, direction ON, accel 10 kn/s)
```

Edge cases:
- **Walking (3-5 km/h):** Stays in sea mode. Speed well under 32 kn cap — no rejections.
- **Car on highway:** 5 rejections → flips to land mode. Returns to sea when you park at the marina.

## GPS recovery gate (Gate 0)

During GPS loss, dead reckoning extrapolates position. When real GPS returns, the first fix may jump from the dead-reckoned position — looking like a spike.

`GpsFix.hasLock` tracks this: `false` during dead reckoning, `true` when real GPS is back. When `hasLock` transitions `false→true`, skip all spike checks for that fix. It's GPS recovery, not a glitch.

Needs one new field: `private var lastHadLock: Boolean = true`.

## Combined decision table

| Context | Direction | Cap | Accel gate | Example |
|---------|-----------|-----|------------|---------|
| SEA | Aligned (±30°) | 48 kn | 10 kn/s | Boat accelerating along course |
| SEA | Sideways (>30°) | 16 kn | 10 kn/s | GPS multipath off a cliff |
| SEA | No course yet | 32 kn | 10 kn/s | First 2 fixes of recording |
| LAND | (any) | 120 kn | 30 kn/s | Car at intersection, walking |
| GPS recovery | (skipped) | ∞ | ∞ | Dead reckoning → real GPS |

## What's NOT in scope

- **HDOP / accuracy:** `GpsFix` doesn't carry accuracy data. Could be added later.
- **Multi-fix consensus:** If 3 consecutive fixes agree on a new position, trust them. Nice-to-have.
- **Kalman filter:** Overkill.

## New fields in TrackRecorder

```kotlin
// Course tracking (sea only)
private var lastValidCourseDeg: Double? = null
private val courseHistory = ArrayDeque<Pair<Double, Double>>(3)

// Speed tracking
private var lastValidSpeedKn: Double = 0.0

// Land/sea auto-detection
private var consecutiveRejections: Int = 0
private var seaConfidenceCounter: Int = 0
private var isOnLand: Boolean = false

// GPS recovery detection
private var lastHadLock: Boolean = true
```

Reset in `beginRecording()`: all set to defaults. `lastHadLock` reset to `true`.

## Pseudocode

```kotlin
fun addPoint(fix: GpsFix) {
    val track = currentTrack ?: return
    
    // ── Stillness gate (unchanged) ──
    policy.onFix(...)
    val moving = !policy.isStill()
    _uiState.update { it.copy(isMoving = moving) }
    if (!moving && gpsMode) return
    
    // ── Demo mode: skip all spike checks ──
    if (!gpsMode) { capturePoint(fix); return }
    
    // ── Gate 0: GPS recovery ──
    if (!lastHadLock && fix.hasLock) {
        lastHadLock = true
        capturePoint(fix)
        return
    }
    lastHadLock = fix.hasLock
    
    // ── Need at least one valid point for gates 1-3 ──
    if (lastValidPointLat == null) { capturePoint(fix); return }
    
    val distM = haversine(lastValidPoint, fix.position)
    val timeDeltaSec = (fix.timestampEpochMs - lastValidPointTimeMs) / 1000.0
    if (timeDeltaSec <= 0) { capturePoint(fix); return }
    val impliedSpeedKn = (distM / timeDeltaSec) * 1.94384
    
    // ── Gate 1: Context speed cap ──
    val baseCap = if (isOnLand) 120.0 else 32.0
    
    // ── Gate 2: Direction (sea only) ──
    val effectiveCap = if (!isOnLand && lastValidCourseDeg != null) {
        val bearingToFix = initialBearing(lastValidPoint, fix.position)
        val delta = angularDistance(bearingToFix, lastValidCourseDeg!!)
        when {
            delta <= 30.0 -> baseCap * 1.5
            else -> baseCap * 0.5
        }
    } else {
        baseCap  // land mode or no course history → neutral
    }
    
    if (impliedSpeedKn > effectiveCap) {
        logRejection("speed cap", impliedSpeedKn, effectiveCap)
        consecutiveRejections++
        checkLandDetection(fix)
        return
    }
    
    // ── Gate 3: Acceleration ──
    val currentSpeedKn = fix.speedMps?.times(1.94384) ?: impliedSpeedKn
    val accelKnPerSec = abs(currentSpeedKn - lastValidSpeedKn) / timeDeltaSec
    val accelLimit = if (isOnLand) 30.0 else 10.0
    
    if (accelKnPerSec > accelLimit) {
        logRejection("acceleration", accelKnPerSec, accelLimit)
        consecutiveRejections++
        return
    }
    
    // ── Accepted ──
    consecutiveRejections = 0
    capturePoint(fix)
    updateCourseHistory(fix.position)
    lastValidSpeedKn = currentSpeedKn
}

fun checkLandDetection(fix: GpsFix) {
    if (consecutiveRejections >= 5) {
        val gpsSpeedKn = fix.speedMps?.times(1.94384) ?: 0.0
        if (gpsSpeedKn > 32.0) {
            isOnLand = true
            consecutiveRejections = 0
            lastValidCourseDeg = null  // reset course — direction check now off
        }
    }
    if (isOnLand && consecutiveRejections == 0) {
        val gpsSpeedKn = fix.speedMps?.times(1.94384) ?: 0.0
        if (gpsSpeedKn <= 32.0) {
            seaConfidenceCounter++
            if (seaConfidenceCounter >= 10) {
                isOnLand = false
                seaConfidenceCounter = 0
            }
        } else {
            seaConfidenceCounter = 0
        }
    }
}

fun updateCourseHistory(pos: LatLng) {
    if (isOnLand) return  // don't track course on land
    courseHistory.addLast(pos.lat to pos.lon)
    if (courseHistory.size > 3) courseHistory.removeFirst()
    if (courseHistory.size >= 2) {
        val first = courseHistory.first()
        val last = courseHistory.last()
        lastValidCourseDeg = initialBearing(
            LatLng(first.first, first.second),
            LatLng(last.first, last.second)
        )
    }
}

fun angularDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}
```

## Constants

```kotlin
private const val BOAT_MAX_SPEED_KN = 32.0
private const val LAND_MAX_SPEED_KN = 120.0
private const val MAX_ACCEL_KN_PER_SEC_SEA = 10.0
private const val MAX_ACCEL_KN_PER_SEC_LAND = 30.0
private const val COURSE_ALIGNED_DEG = 30.0
private const val COURSE_ALIGNED_MULTIPLIER = 1.5
private const val COURSE_SIDEWAYS_MULTIPLIER = 0.5
private const val LAND_DETECTION_REJECTIONS = 5
private const val SEA_RECOVERY_CONSECUTIVE = 10
```

## Cross-feature impact review

| Feature | Impact |
|---------|--------|
| **Navigation** (cap arrow, direction line) | None — reads `navigationState`, not track data |
| **DepthSafety** | None |
| **RegulatedZones** | None |
| **Ui_Dashboard** | Slightly fewer points = more accurate stats. Correct. |
| **ZoneTile** | None |
| **Performance** | ~microseconds per fix at 1 Hz |
| **GPS background service** | None |
| **gps-line-acquisition** | Independent layer |
| **drift-on-idle** | Independent layer |
| **Dead reckoning (GPS loss)** | Gate 0 handles recovery. Dead-reckoned fixes pass direction check (they ARE the course). |
| **Demo mode** | All gates bypassed. |

## Files changed

| File | Change |
|------|--------|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | Replace lines 283-299 (spike rejection) with v2 algorithm; add 7 new fields; add `angularDistance()` helper |
| No other files | Self-contained |
