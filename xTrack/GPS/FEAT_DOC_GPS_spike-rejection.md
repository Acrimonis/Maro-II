# GPS Spike Rejection — Architecture & Gates

**Updated:** 2026-07-14
**Source:** [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:654)

---

## Overview

The spike rejection system filters GPS fixes before they enter the recorded track. It runs inside `addPoint()` (line 669) in GPS mode only. Demo mode bypasses all gates.

Two orthogonal concerns:
- **Speed spikes** — GPS reports implausibly high speed → rejected by Gates 0.5, 1, 3
- **Position spikes** — GPS drifts while stationary → rejected by Fix C, still-spike gate, bearing sanity

The system auto-detects land vs. sea context (`isOnLand`) and adjusts thresholds accordingly (e.g., 32kn cap at sea, 120kn on land).

---

## Gate Pipeline (execution order)

### Pre-gates (before spike rejection)

| Gate | Line | What | Why |
|------|------|------|-----|
| `isStopped` | 662 | Skip entirely if adaptive policy says stationary | No point capture during idle |
| No speed data | 667 | Skip fixes without `speedMps` | Transient GPS state at recording start |
| Still-spike | 671 | Speed<2kn + dist from `lastGenuine` > 150m → reject | GPS drift while "moving" (see below) |
| Bearing sanity | 682 | Sea only: bearing >90° from median of last 5 → reject | Drift has erratic bearings |

### Spike rejection v2

| Gate | Line | What | Sea cap | Land cap |
|------|------|------|---------|----------|
| **Stale timeout** | 696 | 10s no accepted fix → relaxed caps (48/96kn) | — | — |
| **Gate 0** | 727 | Lock false→true recovery → accept unconditionally | — | — |
| **Gate 0.5** | 737 | GPS-reported speed cap | 40kn | disabled |
| **Fix C** | 752 | GPS speed<2kn + dist >150m → reject (anchored to `lastGenuine`) | 150m | 150m |
| **Gate 1** | 773 | Implied speed cap | 32kn | 120kn |
| **Gate 2** | 777 | Direction multiplier | aligned×1.5 / sideways×0.5 | disabled |
| **Gate 3** | 795 | Acceleration cap | 10kn/s | 30kn/s |
| **Same-ms** | 810 | Same timestamp + jump >30m → reject | — | — |

---

## Still-Spike Fix (2026-07-14)

### Problem

During stationary periods, GPS chip drifts positions 200-600m away with `speed=0.00`. `AdaptiveGpsPolicy` sees displacement → `isStopped=false` → spike rejection runs. Stale timeout (10s) relaxes checks. Fix C's `lastValidPoint` gets corrupted by accepted drift points.

### Solution — Three Integrated Gates

**1. `lastGenuine` anchor** (line 195-200)
- Second reference point that only updates when GPS speed ≥ 2kn
- All drift distance checks compare against `lastGenuine`, not `lastValidPoint`
- Prevents drift points from anchoring each other

**2. Still-spike gate** (line 671-694)
- Runs BEFORE stale timeout
- GPS speed < 2kn + distance from `lastGenuine` > 150m → reject
- Bearing sanity (sea only): new bearing >90° from median of last 5 accepted bearings → reject

**3. Stale timeout idle gate** (line 696)
- `!stopped` guard — no GPS reconnection excuses when adaptive policy says stationary

### How it prevents the Port de La Salis scenario

```
Genuine idle points at dock (speed=0, near lastGenuine):
  → Accepted (within 150m of anchor)

GPS drift spike (speed=0, 280m from dock):
  → Still-spike gate: 280m > 150m → REJECTED
  → lastGenuine unchanged (speed < 2kn, anchor stays at dock)

Next drift (speed=0, 310m from dock):
  → Still-spike gate: compared to lastGenuine (still at dock), 310m > 150m → REJECTED

Boat actually starts moving (speed=15kn):
  → Bypasses still-spike gate (speed ≥ 2kn)
  → Normal gates run → accepted → lastGenuine updated to new position
```

---

## Land Mode Auto-Detection

After 5 consecutive rejections with GPS speed > 32kn, the system switches to land mode (`isOnLand=true`). Thresholds relax: speed cap 120kn, acceleration 30kn/s, direction gate disabled. After 10 consecutive accepted fixes ≤ 32kn, it switches back to sea mode.

---

## Related Docs

- [`260714_FEAT_PLN_GPS_fix-spike.md`](xTrack/GPS/260714_FEAT_PLN_GPS_fix-spike.md) — Fix A-D design (stale timeout, GPS speed gate, stationary drift, dedup)
- [`260714_FEAT_PLN_GPS_still-spike-fix.md`](xTrack/GPS/260714_FEAT_PLN_GPS_still-spike-fix.md) — Still-spike fix plan
- [`260714_FEAT_PLN_GPS_checks.md`](xTrack/GPS/260714_FEAT_PLN_GPS_checks.md) — Spike gate audit & map smoothness
