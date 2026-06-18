# Plan: Adaptive isStill() — Stop Detection Redesign

## Motivation

Replace the current `AdaptiveGpsPolicy` implementation (which uses `wakeSpeedMps`, `lastPos` jump detection, and anchor drift logic) with a **pure position-only algorithm** that answers one question:

> If the boat didn't move more than `adaptiveDistance` in the last `adaptiveTime`, `isStill()` returns true.

The GPS dormant interval becomes a **percentage of adaptiveTime** instead of a fixed value. Old settings (`adaptiveWindowSec`, `adaptiveDistanceM`, `adaptiveIdleIntervalSec`) are **completely removed** — no migration path, clean cutover.

---

## Algorithm

```
State:
    anchorPos: LatLng? = null    // position when stillness check started
    anchorTime: Long = 0         // when anchor was set
    result: ACTIVE               // cached for isStill()

onFix(pos, now):
    if anchorPos == null:
        anchorPos = pos
        anchorTime = now
        result = ACTIVE
        return ACTIVE

    if haversine(anchorPos, pos) >= adaptiveDistance:
        anchorPos = pos          // re-anchor
        anchorTime = now
        result = ACTIVE
        return ACTIVE

    if now - anchorTime >= adaptiveTime:
        result = IDLE
        return IDLE
    else:
        result = ACTIVE
        return ACTIVE

isStill() = result == IDLE
```

Three paths — no speed threshold, no lastPos jump detection, no separate drift logic.

---

## Settings Structure

### New AppSettings fields (replaces three old fields with four new ones)

| Setting | Type | Range | Default | Notes |
|---|---|---|---|---|
| `stopDetectionEnabled` | boolean | on/off | true | Master toggle |
| `stopDetectionTimeSec` | int | 15–60 | 30 | Replaces `adaptiveWindowSec` |
| `stopDetectionDistanceM` | int | 10–30 | 20 | Replaces `adaptiveDistanceM` |
| `stopDetectionDelayGps` | boolean | on/off | true | Whether to apply GPS dormancy when still |

**Removed with no migration:**
- `adaptiveWindowSec` — **deleted**
- `adaptiveDistanceM` — **deleted**
- `adaptiveIdleIntervalSec` — **deleted** (replaced by percentage-based dormant interval)

### maro.properties constant

```properties
# Stop detection: GPS dormant interval as % of adaptive time.
# GPS polls at (stopDetectionTimeSec * this / 100) seconds when isStill().
# Must be < 100%. Range 10-90, recommended 80.
stopDetection.gpsDormantPct=80
```

### GPS dormant interval computation

```
gpsDormantIntervalMs = stopDetectionTimeSec * 1000 * gpsDormantPct / 100
```

When `stopDetectionDelayGps == false` → use `gpsActiveIntervalSec` even when IDLE (no dormancy).

---

## Settings UI Replacement

The section is replaced **in-place** — same location in the Advanced tab, same card structure, but content changes.

### Current (MapScreen.kt:2843–2937)

```
SectionHeader: "Idle saving"                          ← strings.xml: settings_idle_section_label
  ┌─ Card Column (RoundedCornerShape 12dp) ───────────┐
  │ Row: "Idle duration" label + value "6s"            │  ← adaptiveIdleIntervalSec
  │ Slider: 4 f..15 f, steps=10                       │
  ├─ Divider ─────────────────────────────────────────┤
  │ SettingsExpander: "Stop detection tuning"          │  ← settings_advanced_stop_label
  │   SliderRowContent: "Adaptive time" 15-60s steps=8│  ← adaptiveWindowSec
  │   SliderRowDivider                                 │
  │   SliderRowContent: "Adaptive distance" 10-30m     │  ← adaptiveDistanceM
  └────────────────────────────────────────────────────┘
```

### Replacement (same card, same expander, new content)

```
SectionHeader: "Stop detection"                       ← new string
  ┌─ Card Column (RoundedCornerShape 12dp) ───────────┐
  │ SettingsToggleRow: "Enable stop detection" [ON]   │  ← stopDetectionEnabled
  │   Description: "Detect when boat is stationary"   │
  ├─ Divider ─────────────────────────────────────────┤
  │ SettingsToggleRow: "Delay GPS when still"    [ON] │  ← stopDetectionDelayGps
  │   Description: "Space out GPS fixes to save       │
  │                 battery when stopped"              │
  ├─ Divider ─────────────────────────────────────────┤
  │ SettingsExpander: "Detection thresholds"           │  ← new label
  │   SliderRowContent: "Adaptive time" 15-60s steps=8│  ← stopDetectionTimeSec
  │   SliderRowDivider                                 │
  │   SliderRowContent: "Adaptive distance" 10-30m     │  ← stopDetectionDistanceM
  └────────────────────────────────────────────────────┘
```

---

## Files to Modify

### 1. `app/src/main/assets/maro.properties`
- Add `stopDetection.gpsDormantPct=80`

### 2. `app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt`
- Remove `wakeSpeedMps` parameter from `onFix()`
- Remove `lastPos` field and jump detection logic
- Remove `wakeSpeedMps` from doc/KDoc
- Simplify to pure anchor + time-window algorithm
- New signature: `onFix(nowMs: Long, pos: LatLng, windowMs: Long, thresholdM: Double): AcquisitionMode`

### 3. `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- Remove fields: `adaptiveWindowSec`, `adaptiveDistanceM`, `adaptiveIdleIntervalSec`
- Add fields: `stopDetectionEnabled: Boolean` (default true), `stopDetectionTimeSec: Int` (default 30), `stopDetectionDistanceM: Int` (default 20), `stopDetectionDelayGps: Boolean` (default true)
- Remove SharedPreferences keys: `KEY_ADAPTIVE_WINDOW_S`, `KEY_ADAPTIVE_DISTANCE_M`, `KEY_ADAPTIVE_IDLE_S`
- Add SharedPreferences keys for new fields
- No migration logic — old keys are simply not read anymore

### 4. `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (lines 2843–2937)
- Replace entire "Idle saving" section header and its content with new "Stop detection" section
- Remove `adaptiveIdleIntervalSec` slider (inline, outside expander)
- Replace SettingsExpander content: `adaptiveWindowSec` → `stopDetectionTimeSec`, `adaptiveDistanceM` → `stopDetectionDistanceM`
- Add `stopDetectionEnabled` toggle and `stopDetectionDelayGps` toggle

### 5. `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- Line 609: Remove `adaptiveIdleIntervalSec` from `distinctUntilChangedBy` — replace with `stopDetectionTimeSec`, `stopDetectionDistanceM`, `stopDetectionDelayGps`
- Line 614: Replace `s.adaptiveIdleIntervalSec * 1_000L` with dormant interval computation: `if (s.stopDetectionDelayGps) s.stopDetectionTimeSec * 1000L * gpsDormantPct / 100 else s.gpsActiveIntervalSec * 1000L`
- Line 668-671: Update `adaptivePolicy.onFix()` call — remove `fix.speedMps` argument, use `s.stopDetectionTimeSec`, `s.stopDetectionDistanceM`

### 6. `app/src/main/res/values/strings.xml` + `values-fr/strings.xml`
- Add new strings for Stop Detection section (section header, enable toggle, delay toggle)
- Remove or replace old strings: `settings_idle_section_label`, `settings_idle_section_desc`, `settings_idle_interval_label`, `settings_idle_interval_desc`, `settings_advanced_stop_label`, `settings_window_label`, `settings_window_desc`, `settings_adaptive_dist_label`, `settings_adaptive_dist_desc`
- Keep `settings_section_advanced` if used elsewhere

---

## Behavior Changes

| Scenario | Before | After |
|---|---|---|
| Boat stationary < 30s | `adaptiveIdleIntervalSec` (6s) GPS polling | `stopDetectionTimeSec * gpsDormantPct / 100` GPS polling (e.g. 24s if enabled) |
| Boat stationary ≥ 30s | Same idle interval | Policy returns IDLE, GPS poll interval goes dormant |
| Boat starts moving | Speed > 0.8 m/s wakes immediately | Next GPS fix shows displacement ≥ threshold → ACTIVE |
| `stopDetectionEnabled = false` | N/A | Policy always returns ACTIVE |
| `stopDetectionDelayGps = false` | N/A | GPS uses `gpsActiveIntervalSec` even when IDLE |

---

## Key Files Reference

- `app/src/main/java/ykws/android/maro/data/location/AdaptiveGpsPolicy.kt`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt`
- `app/src/main/assets/maro.properties`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
