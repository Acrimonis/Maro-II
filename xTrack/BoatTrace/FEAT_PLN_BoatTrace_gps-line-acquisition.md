# GPS Line Acquisition — Outlier & Ordering Fixes

## Symptoms
1. **Zigzag backtracking** visible on the active track polyline — points rendered spatially out of chronological order
2. **Sporadic outliers** tens of meters off true position, roughly once per minute

## Root Causes

### Cause 1 — Dual-listener ordering race (zigzag)
[`GpsLocationSource.locationUpdates()`](app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt:59) registers two `LocationListener`s into one `callbackFlow`:
- `listener` on `GPS_PROVIDER` (line 171)
- `passiveListener` on `PASSIVE_PROVIDER` (line 174)

Both call `trySend()` on the same channel. When a passive fix arrives before the GPS provider fix for the same epoch, they enter the channel in reverse chronological order. The passive dedup at lines 160-164 filters duplicates within 1m/2s but does not enforce timestamp ordering.

### Cause 2 — No outlier filter (spikes)
[`TrackRecorder.addPoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:251) has no spike rejection. [`AdaptiveGpsPolicy.onFix()`](app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt:37) only detects stillness — any GPS fix with displacement ≥ thresholdM passes the `!isStill()` gate and gets recorded. A multipath spike 30m off true position sails through.

## Fixes

### Fix 1 — Remove passive listener
Remove `PASSIVE_PROVIDER` registration and `passiveListener` from `GpsLocationSource`. Single `GPS_PROVIDER` listener guarantees strict FIFO ordering. The passive provider's marginal benefit (free fixes when other apps request GPS) does not justify the ordering bugs it introduces — when Maro is actively navigating, the GPS provider is already delivering fixes at the requested cadence.

**File:** `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`

Changes:
- Remove `passiveListener` (lines 155-168)
- Remove `lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, ...)` (lines 174-176)
- Remove `lm.removeUpdates(passiveListener)` from `awaitClose` (line 180)
- Remove `lastGpsProviderPos`, `lastGpsProviderMs` fields (lines 65-66) — only used by passive dedup
- Remove `haversineApprox()` companion method (lines 195-205) — only used by passive dedup

### Fix 2 — Implied-speed spike gate
In `addPoint()`, after the `!isStill()` check and before appending the point, compute the implied speed from the last *valid* recorded point to the current fix. If it exceeds a physical maximum for recreational boats, discard the fix as a spike.

**File:** `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`

Changes:
- Add constant: `MAX_REALISTIC_SPEED_KN = 50.0` (well above any recreational powerboat)
- Add fields: `lastValidPointLat`, `lastValidPointLon`, `lastValidPointTimeMs`
- In `addPoint()`, after the `!moving` gate but before point construction:
  ```kotlin
  // Outlier rejection: implied speed vs last valid point
  if (lastValidPointLat != null && lastValidPointLon != null) {
      val distM = TrackGeofenceChecker.distanceM(
          lastValidPointLat!!, lastValidPointLon!!,
          fix.position.latitude, fix.position.longitude
      )
      val timeDeltaSec = (fix.timestampEpochMs - lastValidPointTimeMs) / 1000.0
      if (timeDeltaSec > 0) {
          val impliedSpeedKn = (distM / timeDeltaSec) * 1.94384
          if (impliedSpeedKn > MAX_REALISTIC_SPEED_KN) {
              Log.w(TAG, "Spike rejected: dist=${"%.1f".format(distM)}m dt=${"%.1f".format(timeDeltaSec)}s implied=${"%.1f".format(impliedSpeedKn)}kn > ${MAX_REALISTIC_SPEED_KN}kn")
              return // discard outlier
          }
      }
  }
  ```
- After successfully recording a point, update `lastValidPoint*` from the current fix
- In `beginRecording()`, reset `lastValidPoint*` to null

## Implementation Order
1. Remove passive listener from `GpsLocationSource` (fix 1)
2. Add implied-speed spike gate to `TrackRecorder.addPoint()` (fix 2)
3. Build: `assembleDebug`

## Rules
- `MAX_REALISTIC_SPEED_KN = 50.0` — hard cap; any fix implying faster travel is discarded
- Spike rejection uses `fix.timestampEpochMs` (GPS epoch), not `System.currentTimeMillis()` wall clock
- The `AdaptiveGpsPolicy` still receives every fix (including spikes) for stillness detection — only point *recording* is gated
- `lastValidPoint*` tracks the last *recorded* point, separate from `lastPointLat/Lon` (which are used for cumulative Haversine distance)
- No smoothing or median filtering — keep it simple, address the two specific artifacts

## Key Files
- `app/src/main/java/ykws/android/maro/data/location/GpsLocationSource.kt`
- `app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`
