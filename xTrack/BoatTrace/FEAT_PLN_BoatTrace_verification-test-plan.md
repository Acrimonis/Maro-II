<!-- scope: feature -->
# BoatTrace — E2E Verification Test Plan

> **Feature:** BoatTrace
> **Status:** Verification phase
> **Date:** 2026-06-16
> **Build:** `app-debug.apk` (19.3 MB)

---

## Test Scenarios

### 1. Enable Tracking
| Property | Value |
|----------|-------|
| **Precondition** | App installed, fresh launch |
| **Action** | Open Settings > General > Track Recording, toggle ON |
| **Expected** | 👣 TrackStatusIcon appears in top-left icon row, dimmed/grey (IDLE state) |
| **Result** | |

### 2. Auto-start when leaving Port Salis
| Property | Value |
|----------|-------|
| **Precondition** | GPS mode ON, boat within Port Salis (43.55°N, 7.00°E), tracking enabled |
| **Action** | Sail out of the geofence (500m radius from center) |
| **Expected** | TrackStatusIcon turns green with pulsing dot (RECORDING state). Track drawer shows live stats: elapsed time, point count, distance, speed |
| **Result** | |

### 3. Real-time map trace
| Property | Value |
|----------|-------|
| **Precondition** | Recording active |
| **Action** | Sail around; observe map |
| **Expected** | Colored polyline appears on the map following the boat's path |
| **Result** | |

### 4. Pause/Resume
| Property | Value |
|----------|-------|
| **Precondition** | Recording active, boat moving > 2.5 kn |
| **Action 1** | Stop sailing (speed drops below 2.5 kn) |
| **Expected 1** | Icon turns amber (PAUSED state) |
| **Action 2** | Sail again (speed > 2.5 kn) |
| **Expected 2** | Icon returns to green, recording resumes |
| **Result** | |

### 5. Manual Start/Stop
| Property | Value |
|----------|-------|
| **Precondition** | Tracking enabled, IDLE state |
| **Action 1** | Open TrackDrawer, tap "Start Recording" |
| **Expected 1** | Recording starts (bypasses geofence) |
| **Action 2** | Tap "Stop Recording" |
| **Expected 2** | State returns to IDLE |
| **Result** | |

### 6. Track History
| Property | Value |
|----------|-------|
| **Precondition** | At least one track recorded and stopped |
| **Action** | Open TrackDrawer > "Track List" |
| **Expected** | The track appears with auto-generated name ("yyyy-MM-dd HH:mm"), max speed, distance |
| **Result** | |

### 7. Edit Track Name
| Property | Value |
|----------|-------|
| **Precondition** | Track in history list |
| **Action** | Tap track name in history |
| **Expected** | Inline TextField appears. Type new name, tap Done or Save |
| **Postcondition** | Name persists after app restart |
| **Result** | |

### 8. Visibility Toggle
| Property | Value |
|----------|-------|
| **Precondition** | Track in history list, polyline visible on map |
| **Action 1** | Tap 👁️ button on the track |
| **Expected 1** | Track polyline disappears from map |
| **Action 2** | Tap 👁️ again |
| **Expected 2** | Polyline reappears on map |
| **Result** | |

### 9. Delete Track
| Property | Value |
|----------|-------|
| **Precondition** | Track in history list |
| **Action 1** | Tap Delete on a track |
| **Expected 1** | Confirmation dialog appears |
| **Action 2** | Confirm deletion |
| **Expected 2** | Track is removed from list and file system |
| **Result** | |

### 10. GPX Share
| Property | Value |
|----------|-------|
| **Precondition** | Track in history list |
| **Action** | Tap Share button on a track |
| **Expected** | Android share sheet opens with the GPX file. Share to a file manager or email |
| **Postcondition** | The `.gpx` file contains valid XML |
| **Result** | |

### 11. Demo Mode
| Property | Value |
|----------|-------|
| **Precondition** | Switch to Demo mode (GPS toggle OFF), tracking enabled |
| **Action** | Pan the map at speed > 2.5 kn |
| **Expected** | Recording starts (geofence bypassed in demo mode) |
| **Result** | |

### 12. Process Death Recovery
| Property | Value |
|----------|-------|
| **Precondition** | Recording active with several points |
| **Action 1** | Force-kill the app (swipe from recents) |
| **Action 2** | Reopen the app |
| **Expected** | The partial track is recovered and appears in history |
| **Result** | |

---

## Environment

| Item | Value |
|------|-------|
| **Device** | |
| **Android Version** | |
| **Build** | `app-debug.apk` (2026-06-16) |

## Notes

- All tests should be run with the debug APK built from the BoatTrace feature branch.
- Logcat filtering: `adb logcat -s TrackRecorder,TrackRepository,BoatTraceViewModel`
- GPX validation: `adb pull /sdcard/Android/data/ykws.android.maro/files/tracks/<track-name>.gpx` then verify with any XML validator.
