# Still-Spike Fix — GPS Drift During Idle

**Created:** 2026-07-14 16:58 UTC
**Updated:** 2026-07-14 17:02 UTC (added trajectory consistency)
**Status:** planned
**Scope:** `TrackRecorder.kt`
**Branch:** `feature/gps-still-spikes`

---

## Problem

During stationary periods, GPS chip drifts positions 200-600m away with `speed=0.00`. The `AdaptiveGpsPolicy` sees displacement → flips `isStopped=false` → spike rejection runs on drift points. The stale timeout (10s) resets and accepts with relaxed checks. Fix C's 150m drift tolerance allows incremental drifts to accumulate because `lastValidPoint` is updated by each accepted drift point, anchoring the next comparison to a bad position.

**Result:** 7+ spike points exported to GPX with `speed=0.00` scattered across a 600m radius.

## Root Cause

Two systems work against each other:
- `AdaptiveGpsPolicy`: displacement → `isStopped=false` ("moving")
- Spike rejection: `isStopped=false` → run gates → stale timeout relaxes → accept drift

And `lastValidPointLat/Lon` gets corrupted by accepted drift points.

## Key Insight

A "moving" boat (`isStopped=false`) reporting `speed=0.00` is a **sensor contradiction**. The GPS speed chip says stationary; the position chip says moved. The speed sensor is the tiebreaker — when speed ≈ 0, the position jump is drift, not movement.

Second insight: drift points have **erratic bearings**. A sliding window of recent bearings exposes the inconsistency — real boats move in consistent directions; drift bounces randomly.

## Existing Sanity Gates (what's already there)

| Gate | Line | Check |
|------|------|-------|
| Stale timeout | 657 | 10s no accepted fix → relaxed caps (1.5×/3×) |
| Gate 0 | 689 | Lock false→true → accept unconditionally |
| Gate 0.5 | 698 | GPS speed > 40kn → reject |
| Fix C | 713 | GPS speed < 2kn + dist > 150m from `lastValidPoint` → reject |
| Gate 1 | 725 | Implied speed > 32kn (sea) / 120kn (land) → reject |
| Gate 2 | 729 | Direction multiplier (aligned ×1.5, sideways ×0.5) |
| Gate 3 | 747 | Acceleration > 10kn/s → reject |
| Same-ms | 762 | Same timestamp + jump > 30m → reject |

Fix C's flaw: compares against `lastValidPoint` which drifts with accepted spikes. Each individual jump under 150m, but cumulative from genuine position is 260m.

## Integrated Fix (4 changes, 1 file)

### 1. `lastGenuineLat/Lon` — anchor to speed-confirmed positions

Only updates when GPS speed ≥ 2kn. All drift distance checks compare against this anchor, not the drifting `lastValidPoint`.

### 2. Contradiction gate — speed=0 + distance from anchor

Before stale timeout: if GPS speed < 2kn AND distance from `lastGenuine` > 150m → reject immediately. No stale timeout relaxation for zero-speed drift.

### 3. Trajectory consistency — sliding bearing window

Maintain ring buffer of last 5 accepted bearings. For each new point:
- Compute bearing from `lastGenuine` to the new point
- If bearing deviates > 90° from median of window AND GPS speed < 5kn → reject
- Only accepted points enter the window (rejects don't corrupt it)
- Falls back to anchor-only check if window has < 3 entries

```
Drift points at Port de La Salis:
  Genuine idle bearings: ~103-157° (med ~130° SE)
  Spike 1 bearing: ~45° NE → 85° off → reject (>90°? no... borderline)
  Spike 2 bearing: ~290° NW → 160° off → reject (>90° ✓)
  Spike 3 bearing: ~140° SE → 10° off → passes bearing, caught by anchor distance
```

### 4. Stale timeout gated on `!stopped`

No GPS reconnection when adaptive policy says stationary. Chip is reporting continuously, just badly.

## Implementation

| # | Change |
|---|--------|
| 1 | Add fields: `lastGenuineLat/Lon`, `bearingWindow: ArrayDeque<Double>(5)` |
| 2 | In `captureAcceptedPoint`: update `lastGenuine` when speed ≥ 2kn; push bearing to window |
| 3 | New gate before stale timeout (line ~655): speed<2kn → check anchor distance + bearing window |
| 4 | Fix C (line ~713): compare against `lastGenuine` instead of `lastValidPoint` |
| 5 | Stale timeout (line ~657): guard with `!stopped` |

**1 file: `TrackRecorder.kt`. ~30 lines added, ~5 lines modified.**

## Verification

1. Build: `gradlew assembleDebug`
2. Idle GPS drift → no spike points in GPX
3. Genuine movement > 2kn → track recorded, bearing window updated
4. GPS recovery while moving (tunnel) → stale timeout still works
