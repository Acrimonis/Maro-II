# GPS Trace Gaps: Spike Rejection Lock-In & Recovery Failure

**Subfeature:** troubleshoot-gps-turns
**Feature:** GPS
**Date:** 2026-06-22

## Symptom

In GPS mode, when the GPS loses fix OR when spike rejection excludes points on sharp turns, the boat trace fails to resume — large gaps appear in the recorded track.

## Root Cause Analysis

### Issue 1: Spike Rejection Lock-In on Sharp Turns (Gate 2)

In [`TrackRecorder.addPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:305), Gates 1-3 compare each new fix against the **last accepted** point's state:

- `lastValidPointLat/Lon` (line 422-424) — updated only in `captureAcceptedPoint()`
- `lastValidCourseDeg` (line 448-450) — updated only via `updateCourseHistory()`, called from `captureAcceptedPoint()`

When the boat makes a sharp turn:

1. Pre-turn: `lastValidCourseDeg` = old direction, `lastValidPoint` = pre-turn position
2. First post-turn fix: `angularDistance(bearingToFix, lastValidCourseDeg)` > 30°
3. Gate 2 applies `COURSE_SIDEWAYS_MULTIPLIER` (0.5) → effective cap = 32 × 0.5 = **16 kn**
4. Implied speed from pre-turn position to post-turn position may exceed 16 kn → **rejected**
5. `lastValidPoint` and `lastValidCourseDeg` **remain stale** (pre-turn values)
6. Next fix: same stale reference, same rejection → **lock-in**: every subsequent fix in the new direction fails

**Constants:**
```
COURSE_ALIGNED_DEG = 30.0      // ±30° considered "aligned"
COURSE_ALIGNED_MULTIPLIER = 1.5 // cap ×1.5 when aligned (48 kn at sea)
COURSE_SIDEWAYS_MULTIPLIER = 0.5 // cap ×0.5 when sideways (16 kn at sea)
BOAT_MAX_SPEED_KN = 32.0
```

A boat doing 6 kn over 5 seconds travels ~15 m. The implied-speed calculation uses Haversine distance from `lastValidPoint` (pre-turn) to new fix. If the turn happened 2 fixes ago and the distance is 30 m over 5s → implied ~11.7 kn. But after 3+ rejected fixes, the accumulated gap grows and implied speed exceeds 16 kn → permanent rejection.

### Issue 2: GPS Recovery Without Gate 0 Trigger

`Gate 0` (line 327-333) captures the first fix after GPS lock recovery unconditionally:

```kotlin
if (!lastHadLock && fix.hasLock) {  // false→true transition
    lastHadLock = true
    captureAcceptedPoint(fix)
    return
}
lastHadLock = fix.hasLock
```

This works IF a no-lock fix (`hasLock=false`) is emitted before GPS recovers. But `GpsLocationSource` only emits no-lock on:
- `GnssStatus.Callback.onSatelliteStatusChanged` — satellite count drops < 4
- `GnssStatus.Callback.onStopped`
- `LocationListener.onStatusChanged(TEMPORARILY_UNAVAILABLE/OUT_OF_SERVICE)`
- `LocationListener.onProviderDisabled`

If GPS drops and recovers **without triggering any of these callbacks** (brief dropout, fast re-acquisition), `lastHadLock` stays `true`. The recovery fix goes through Gates 1-3 where the large implied distance from the last valid point triggers rejection.

Similarly, if the Android LocationManager simply stops delivering fixes for a period (e.g., power-saving throttling) without any status/availability callback, no `emitNoLock` fires and `lastHadLock` remains stale.

## Proposed Fix Design

### Fix A: Timeout-Based Spike Rejection Reset

When no accepted point has been recorded for `STALE_FIX_TIMEOUT_MS` (e.g., 10 seconds), accept the next fix unconditionally (like Gate 0):

- Track `lastAcceptedTimeMs` in `captureAcceptedPoint()`
- In `addPoint()`: if `now - lastAcceptedTimeMs > STALE_FIX_TIMEOUT_MS`, skip all gates and accept
- This handles BOTH the sharp-turn lock-in AND the GPS recovery-without-Gate-0 scenarios

### Fix B: Direction-Change Detection with Grace Period

When Gate 2 rejects a fix due to sideways bearing, track that a direction change was detected. After N consecutive rejections (e.g., 3), accept the next fix as a new course anchor:

- Add `directionChangeRejections` counter
- On Gate 2 rejection, increment; on any other gate rejection or acceptance, reset
- When `directionChangeRejections >= 3`, accept the fix and reset course history from the new position

### Recommendation

**Implement Fix A** (timeout-based reset) as it's simpler, covers both scenarios, and has no false-positive risk (a genuine spike wouldn't persist for 10s). Fix B could supplement it for more responsive turn handling if needed.

## Implementation Steps

1. Add `lastAcceptedTimeMs` field to `TrackRecorder`, initialized in `beginRecording()`
2. Set `lastAcceptedTimeMs = System.currentTimeMillis()` in `captureAcceptedPoint()`
3. In `addPoint()`, before Gates 0-3, check timeout: `if (now - lastAcceptedTimeMs > STALE_FIX_TIMEOUT_MS) { captureAcceptedPoint(fix); return }`
4. Set `STALE_FIX_TIMEOUT_MS = 10_000L` (10 seconds)
5. Reset `lastHadLock` and spike rejection state in the timeout path
6. Build + on-device test with sharp turns and GPS shadow zones (tunnels, under bridges)
