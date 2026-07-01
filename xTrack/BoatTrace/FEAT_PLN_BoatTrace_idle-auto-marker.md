# Idle Auto-Marker — Design

**Created:** 2026-07-01 14:37 UTC
**Updated:** 2026-07-01 15:47 UTC
**Branch:** feature/track+markers
**Status:** finalised
**Depends on:** `FEAT_PLN_BoatTrace_boat-markers.md`

## Goal

Automatically create a 🕐 Pin marker at the boat's idle position — temporary during idle, permanent if idle lasted long enough. Provides a visual record of where the boat stopped.

---

## 1. Marker Spec

```kotlin
UserMarker(
    geometry = MarkerGeometry.Pin(position = LatLng(entryLat, entryLon)),
    proximityOverrideM = null,    // matches like normal Pin — no distance limit
    pinned = true,               // always rendered on map
    icon = "\uD83D\uDD50",       // 🕐 U+1F550 — now in ICON_SET
    confirmed = false,           // caution color during temporary phase
    keepable = false,            // set to true when idle duration is sufficient
    origin = MarkerOrigin.IDLE_AUTO,
)
```

### New fields on UserMarker

```kotlin
enum class MarkerOrigin { USER, IDLE_AUTO }

// Added to UserMarker, defaults preserve backward compatibility:
val origin: MarkerOrigin = MarkerOrigin.USER
val keepable: Boolean = true          // user-created markers are keepable by default
```

### Lifecycle

```
idle entry → create marker (confirmed=false, keepable=false, caution color, name: "yyyy-MM-dd @ HH:mm -> ...")
idle exit  → if duration >= autoMarkerMinDurationSec:
               update confirmed=true, keepable=true, name: "yyyy-MM-dd @ HH:mm -> HH:mm (X min)"
             else:
               delete marker
```

If track finalized during idle (`endTimeMs=0`):
```
  → update confirmed=true, keepable=true, name: "yyyy-MM-dd @ HH:mm -> ?"
```

### Startup cleanup

Delete all markers where `keepable == false`. Catches:
- Orphaned auto-markers from app crash during idle
- Auto-markers where idle was too short (should have been deleted but app crashed)

User-created markers always have `keepable = true`, never affected.

### Name format

| Scenario | Format | Example |
|----------|--------|---------|
| Placeholder (during idle) | `yyyy-MM-dd @ HH:mm -> ...` | `2026-07-01 @ 14:15 -> ...` |
| Normal idle exit | `yyyy-MM-dd @ HH:mm -> HH:mm (X min)` | `2026-07-01 @ 14:15 -> 14:35 (20 min)` |
| Track finalized during idle | `yyyy-MM-dd @ HH:mm -> ?` | `2026-07-01 @ 14:15 -> ?` |

---

## 2. Architecture

### TrackRecorder emits events

| Event | When | Carries |
|-------|------|---------|
| `IdlePeriodStarted` | Idle entry | `entryLat`, `entryLon`, `startTimeMs` |
| `IdlePeriodCompleted` | Idle exit or track finalized | `entryLat`, `entryLon`, `startTimeMs`, `endTimeMs`, `durationSec`, `autoMarkerId` |

### MapScreen handles events

```kotlin
// On IdlePeriodStarted:
val markerId = markersViewModel.addTempAutoMarker(
    lat = event.entryLat,
    lon = event.entryLon,
    startTimeMs = event.startTimeMs
)
// Creates: confirmed=false, keepable=false, name="yyyy-MM-dd @ HH:mm -> ...", icon=🕐

// Store ID in TrackRecorder's session for IdlePeriodCompleted:
trackViewModel.setActiveSessionAutoMarkerId(markerId)

// On IdlePeriodCompleted:
try {
    if (event.durationSec >= autoMarkerMinDurationSec || event.endTimeMs == 0L) {
        markersViewModel.confirmAutoMarker(
            id = event.autoMarkerId ?: return,
            name = buildName(event)
        )
        // Sets confirmed=true, keepable=true, updates name
    } else {
        markersViewModel.deleteMarker(event.autoMarkerId ?: return)
    }
} catch (e: Exception) {
    Log.w(TAG, "autoMarker finalize failed", e)
}
```

### MarkersViewModel new methods

```kotlin
/** Create temporary 🕐 pin with confirmed=false, keepable=false. Returns marker ID. */
fun addTempAutoMarker(lat: Double, lon: Double, startTimeMs: Long): String

/** Set confirmed=true, keepable=true + update name. */
fun confirmAutoMarker(id: String, name: String)

/** Delete marker by ID. */
fun deleteMarker(id: String)  // existing
```

---

## 3. Config

```
# maro.properties
track.boatMarker.autoMarkerMinDurationSec=120   # min idle before 🕐 pin becomes permanent
```

| Key | Default | Purpose |
|-----|---------|---------|
| `track.boatMarker.idleThresholdSec` | 60 | Snapshot markers + auto-open drawer |
| `track.boatMarker.autoMarkerMinDurationSec` | 120 | Minimum idle before 🕐 pin persists |

---

## 4. Edge Cases

| Scenario | Handling |
|----------|----------|
| Idle < minDurationSec | Marker created (temporary), deleted on exit |
| Idle ≥ minDurationSec | Marker confirmed (permanent), keepable=true |
| Track finalized during idle | Marker confirmed with name `-> ?`, keepable=true |
| App crash during idle | `keepable=false` markers cleaned up on next startup |
| User manually creates 🕐 marker | `keepable=true`, `origin=USER` — never cleaned up |
| Multiple idle periods | Each creates independent temporary → permanent marker |

---

## 5. ICON_SET Expansion

Now 16 icons, 4×4 grid:

| | | | |
|---|---|---|---|
| ⚓ Anchor | 🤿 Diver | ⚠️ Warning | 📍 Pin |
| 🐟 Fish | ⛵ Sailboat | 🏊 Swimmer | 🎣 Fishing |
| ⭐ Star | 💀 Danger | 🏝️ Island | 🗺️ Map |
| 🐬 Dolphin | 🐚 Shell | 🏖️ Beach | 🕐 Clock |

[`IconPickerDialog`](app/src/main/java/ykws/android/maro/ui/map/IconPickerDialog.kt): `for (row in 0..2)` → `0..3`.

---

## 6. Implemented

2026-07-01 — merged into boat-markers implementation:

- `MarkerOrigin.IDLE_AUTO` + `keepable` on `UserMarker`
- `addTempAutoMarker(lat, lon, startTimeMs)` — title=date, description=timing placeholder, `confirmed=false`, `keepable=false`
- `confirmAutoMarker(id, name, desc)` — `confirmed=true`, `keepable=true`, updates description with end time
- `IdlePeriodStarted` → temp 🕐 pin created, ID stored in `IdleSessionContext`
- `IdlePeriodCompleted` → confirm (if ≥ minDurationSec) or delete marker
- Startup cleanup of `keepable=false` markers (crash recovery)
- ICON_SET: 🐬🐚🏖️🕐 added, 4×4 grid
- Config: `track.boatMarker.autoMarker.minDurationSec` (120s default), `track.boatMarker.autoMarker.opacity` (50%)

## 7. Future Considerations

- Notification integration: show idle marker count in recording notification
- Filter/group auto-markers by track in marker list
