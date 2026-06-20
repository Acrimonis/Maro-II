# Plan: Tracking States & Triggers Simplification

## Functional Requirements

### States (pure 2-state)

```
OFF ⇄ ON
```

| State | Meaning | Icon |
|---|---|---|
| **OFF** | No tracking. GPS positions ignored. | Dimmed blue 🚤 |
| **ON** | Tracking active. GPS positions processed. | Active |
| ├ ON + **!isStill()** | Moving → **record** GPS points | Green 🚤 |
| └ ON + **isStill()** | Stationary → **skip** GPS points | Blue 🚤 |

`isStill()` is determined by `AdaptiveGpsPolicy`: true when boat stays within `stopDetectionDistanceM` (default 15m) of anchor for `stopDetectionTimeSec` (default 45s).

### Triggers

| From | To | Trigger | Details |
|---|---|---|---|
| OFF | ON | Geofence exit | Boat leaves Port Salis geofence → auto-start tracking |
| OFF | ON | Manual | User taps "Start Tracking" in drawer |
| ON | OFF | Geofence entrance | Boat returns to Port Salis geofence → auto-stop + save |
| ON | OFF | Manual | User taps "Stop Tracking" in drawer |

### Within ON state

- `isStill() == false` → GPS points saved to current track
- `isStill() == true` → no points saved (recording continues, just no data)

---

## Current → Required Delta

### State machine

```
Current:  IDLE → RECORDING ⇄ PAUSED → FINALIZING → IDLE
Required: OFF ⇄ ON
```

### What changes

| Aspect | Current | Required |
|---|---|---|
| **States** | IDLE, RECORDING, PAUSED, FINALIZING | **OFF, ON** |
| **PAUSED** | Manual pause with resume | **Removed entirely** |
| **FINALIZING** | Separate blink-state before IDLE | **Eliminated** — save runs async, state goes ON→OFF directly |
| **Auto-OFF on geofence entry** | ❌ Missing | ✅ Add: `insideGeofence && state==ON → finalizeTrack()` |
| **Auto-start on geofence exit** | Requires moving (ACTIVE for 10s) | ✅ Start on any exit (moving or drifting). `isStill()` gates point capture |
| **Point capture gate** | `if (!policy.isStill()) return` in addPoint() | Same — **already works** |
| **Manual start/stop** | Via drawer | Same — **minor cleanup** |
| **Pause-related fields** | `pausedStartTimeMs`, `totalPausedDurationMs` | **Removed** |
| **Elapsed timer** | Subtracts paused duration | **Simplified** — just elapsed from start |

---

## Files to Modify

### 1. `TrackRecorder.kt`

**Enum:**
```kotlin
enum class TrackRecorderState { OFF, ON }
```

**Remove:**
- `pauseRecording()` method
- `resumeRecording()` method
- `pausedStartTimeMs` field
- `totalPausedDurationMs` field
- `Paused` case in `transitionTo()` / `_uiState` updates
- PAUSED case in `processFix()` when

**Modify `processFix()`:**
```
OFF state:
  - manual start → beginRecording(), transition to ON
  - !insideGeofence && geofenceEnabled → feed policy, if ACTIVE for 10s → ON
  - !geofenceEnabled → feed policy, if ACTIVE for 10s → ON

ON state:
  - addPoint(fix)  [gates capture by isStill()]
  - if geofenceEnabled && insideGeofence → 15s debounce, then finalizeTrack(), transition to OFF
  - outside geofence → reset debounce
```

**Simplify `finalizeTrack()`:**
- No FINALIZING state
- `transitionTo(OFF)` instead of `transitionTo(FINALIZING)` then `transitionTo(IDLE)`
- `pausedDurationSec = 0` (field preserved in data class but always 0)
- `navigatingDurationSec = totalElapsedSec` (no pause subtraction)

**Simplify `addPoint()` time offset:**
```kotlin
// Before:
val timeOffsetSec = ((now - recordingStartTimeMs - totalPausedDurationMs) / 1000).toInt()
// After:
val timeOffsetSec = ((now - recordingStartTimeMs) / 1000).toInt()
```

### 2. `TrackEvent.kt`
- Remove `Paused`, `Resumed` event types
- Keep: `Started`, `Stopped`, `PointCaptured`

### 3. `colors.properties`
Add new tracking status section:
```properties
# ── Tracking status icon states ────────────────────────
# Tracking ON + moving (recording points) — green
status.tracking.healthy=#CC4CAF50
# Tracking ON + idle (stationary, not recording) — blue
status.tracking.idle=#FF1565C0
# Tracking OFF (not tracking) — white
status.tracking.off=#FFFFFFFF
# Dot colour when recording/moving — red (pulses top-right)
status.tracking.dot.recording=#FFF44336
# Dot colour when idle/stationary — blue (pulses top-right)
status.tracking.dot.idle=#FF1565C0
# Tracking icon background alpha when in active state (0.0-1.0)
status.tracking.alpha.active=0.75
# Tracking icon background alpha when dimmed (0.0-1.0)
status.tracking.alpha.dimmed=0.50
```

### 4. `AppConfig.kt`
Add new fields following the `status.gps.*` pattern:
- `statusTrackingHealthy: Int` — default `0xCC4CAF50`
- `statusTrackingIdle: Int` — default `0xFF1565C0`
- `statusTrackingOff: Int` — default `0xFFFFFFFF`
- `statusTrackingDotRecording: Int` — default `0xFFF44336`
- `statusTrackingDotIdle: Int` — default `0xFF1565C0`
- `statusTrackingAlphaActive: Float` — default `0.75f`
- `statusTrackingAlphaDimmed: Float` — default `0.50f`
Parse from `colors.properties` keys in the `load()` method.

### 5. `TrackStatusIcon.kt`
3 visual states with pulsing dot:

```kotlin
when (recorderState.state) {
    TrackRecorderState.OFF -> {
        // Not tracking — white bg, dimmed (like GPS DEMO)
        baseColor = Color(AppConfig.statusTrackingOff)
        bgAlpha = AppConfig.statusTrackingAlphaDimmed
        contentAlpha = 0.50f
        showDot = false
    }
    TrackRecorderState.ON -> {
        // Same base color scheme as GPS: healthy (green) when moving, idle (blue) when still
        baseColor = if (recorderState.isMoving)
            Color(AppConfig.statusTrackingHealthy)
        else
            Color(AppConfig.statusTrackingIdle)
        bgAlpha = AppConfig.statusTrackingAlphaActive
        contentAlpha = 1f
        showDot = true
        dotColor = if (recorderState.isMoving)
            Color(AppConfig.statusTrackingDotRecording)  // red
        else
            Color(AppConfig.statusTrackingDotIdle)       // blue
    }
}
```

The dot:
- 8dp diameter circle, positioned ~4dp from top-right corner of 44dp box
- Pulsing animation: `animateFloatAsState` target 0.3f→1.0f, infinite repeat, RepeatMode.Reverse
- Drawn with `Canvas` or `Box` overlay on top of the base background

### 6. `TrackDrawerOverlay.kt`

### 4. `TrackDrawerOverlay.kt`

```kotlin
// Before:
val isActive = recorderState.state == TrackRecorderState.RECORDING ||
               recorderState.state == TrackRecorderState.PAUSED

// After:
val isActive = recorderState.state == TrackRecorderState.ON
```

State label in stats card:
```kotlin
if (recorderState.isMoving) "\u25CF Recording" else "\u25CF Idle"
```

### 5. `Tack.kt` (if TrackEvent imported)
- Remove Paused/Resumed references if any

### 6. xTrack
- Update `tracking-status-n-triggers` subfeature checklist
- Update hydration

---

## Migration

No settings migration. Old saved tracks unaffected — `pausedDurationSec` field in protobuf will just be 0 for new tracks.
