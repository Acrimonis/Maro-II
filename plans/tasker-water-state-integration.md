# Tasker ↔ Maro II: Boat Water State Integration Plan

**Feature branch:** `feature/intent`  
**Status:** Architecture approved, ready for implementation

---

## Architecture Overview

```
GPS Fix → NavigationViewModel._gpsPosition
                ↓
         repository.isOnWater(lat, lon)
                ↓
         NavigationViewModel._boatIsWater (StateFlow)
                ↓
         MapScreen → TrackRecordingService (ACTION_UPDATE + EXTRA_ON_WATER)
                ↓
         TrackRecordingService.lastKnownOnWater
                ├──→ Push: sendBroadcast(WATER_STATE_CHANGED) on toggle
                └──→ Query: BroadcastReceiver responds to QUERY_WATER_STATE
                              ↳ sendBroadcast(WATER_STATE_RESULT)
```

### Why TrackRecordingService is the state holder

| Concern | Resolution |
|---------|------------|
| Lifecycle | Service outlives Activity — query always works even when app is backgrounded |
| Existing pattern | Already receives ACTION_UPDATE intents from MapScreen with recording stats |
| No new singletons | Reuses the existing persistent foreground service |
| Push + Query | Same state source for both mechanisms, no drift |

---

## Implementation Steps

### 1. NavigationViewModel — add `_boatIsWater` StateFlow

**File:** [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt)

Add new StateFlow (near line 189, alongside `_isWater`):

```kotlin
/** True when the boat's GPS position is on water (not map center). */
private val _boatIsWater = MutableStateFlow(false)
val boatIsWater: StateFlow<Boolean> = _boatIsWater.asStateFlow()
```

Compute it at two points where `_gpsPosition` is set:

**A) GPS fix arrival** (line ~656–659):
```kotlin
.onEach { fix ->
    val now = SystemClock.elapsedRealtime()
    _gpsPosition.value = fix.position
    _boatIsWater.value = repository.isOnWater(fix.position.latitude, fix.position.longitude)
    updateMapCenter(fix.position.latitude, fix.position.longitude)
    // ... rest unchanged
```

**B) Dead reckoning update** (line ~839):
```kotlin
_gpsPosition.value = estimatedPos
_boatIsWater.value = repository.isOnWater(estimatedPos.latitude, estimatedPos.longitude)
updateMapCenter(estimatedPos.latitude, estimatedPos.longitude)
```

**C) GPS-off clear** (line ~786–788):
```kotlin
if (!on) {
    _gpsPosition.value = null
    _boatIsWater.value = false   // reset to land
    // ... rest unchanged
```

---

### 2. TrackRecordingService — store water state + push broadcast

**File:** [`TrackRecordingService.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt)

**New fields:**
```kotlin
private var lastKnownOnWater: Boolean = false
private var waterQueryReceiver: BroadcastReceiver? = null
```

**New intent extras (companion object):**
```kotlin
const val EXTRA_ON_WATER = "on_water"

// Push broadcast
const val ACTION_WATER_STATE_CHANGED = "ykws.android.maro.action.WATER_STATE_CHANGED"

// Query: Tasker sends this, Maro responds with WATER_STATE_RESULT
const val ACTION_QUERY_WATER_STATE = "ykws.android.maro.action.QUERY_WATER_STATE"
const val ACTION_WATER_STATE_RESULT = "ykws.android.maro.action.WATER_STATE_RESULT"
```

**onStartCommand** — extract water state from incoming intents:
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Check for water state update (may come alongside notification update or standalone)
    if (intent != null && intent.hasExtra(EXTRA_ON_WATER)) {
        val newOnWater = intent.getBooleanExtra(EXTRA_ON_WATER, false)
        if (newOnWater != lastKnownOnWater) {
            lastKnownOnWater = newOnWater
            // Push broadcast to Tasker
            sendBroadcast(Intent(ACTION_WATER_STATE_CHANGED).apply {
                putExtra(EXTRA_ON_WATER, newOnWater)
                // Include position for Tasker variables
                intent.getDoubleExtra("latitude", Double.NaN).let { if (!it.isNaN()) putExtra("latitude", it) }
                intent.getDoubleExtra("longitude", Double.NaN).let { if (!it.isNaN()) putExtra("longitude", it) }
            })
        }
    }
    // ... existing notification logic unchanged
```

**onCreate** — register query receiver:
```kotlin
override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    
    waterQueryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_QUERY_WATER_STATE) {
                val result = Intent(ACTION_WATER_STATE_RESULT).apply {
                    putExtra(EXTRA_ON_WATER, lastKnownOnWater)
                }
                sendBroadcast(result)
            }
        }
    }
    registerReceiver(waterQueryReceiver, IntentFilter(ACTION_QUERY_WATER_STATE))
}
```

**onDestroy** — cleanup:
```kotlin
override fun onDestroy() {
    waterQueryReceiver?.let { unregisterReceiver(it) }
    super.onDestroy()
}
```

---

### 3. MapScreen — send water state to service

**File:** [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)

**CRITICAL:** Do NOT add `boatIsWater` as a key to the existing recording LaunchedEffect — that would restart the 5s notification loop on every water toggle. Use two separate effects:

**A) New standalone LaunchedEffect for water state pushes** (add after line ~760):

```kotlin
// ── Water state push to TrackRecordingService ─────────────────────────
// Sends a one-shot intent every time boatIsWater toggles, so the service
// can fire the WATER_STATE_CHANGED broadcast to Tasker immediately.
val boatIsWater by viewModel.boatIsWater.collectAsState()
LaunchedEffect(boatIsWater) {
    val intent = Intent(context, TrackRecordingService::class.java).apply {
        action = TrackRecordingService.ACTION_UPDATE
        putExtra(TrackRecordingService.EXTRA_ON_WATER, boatIsWater)
        putExtra(TrackRecordingService.EXTRA_IS_DEMO, !appSettings.gpsMode)
    }
    context.startService(intent)
}
```

**B) Existing recording LaunchedEffect** (line ~738) — add `EXTRA_ON_WATER` to both the recording loop and the "Ready" fallback, reading the current `boatIsWater` value. Do NOT add `boatIsWater` as a key:

```kotlin
LaunchedEffect(trackRecorderState, appSettings.gpsMode) {
    val state = trackRecorderState
    val isDemo = !appSettings.gpsMode
    val intent = Intent(context, TrackRecordingService::class.java).apply {
        action = TrackRecordingService.ACTION_UPDATE
        putExtra(TrackRecordingService.EXTRA_IS_DEMO, isDemo)
    }
    if (state.state == TrackRecorderState.ON) {
        while (true) {
            intent.putExtra(TrackRecordingService.EXTRA_RECORDING, true)
            intent.putExtra(TrackRecordingService.EXTRA_SPEED_KN, state.currentSpeedKn)
            intent.putExtra(TrackRecordingService.EXTRA_ELAPSED_SEC, state.elapsedSeconds)
            intent.putExtra(TrackRecordingService.EXTRA_DISTANCE_NM, state.distanceNm)
            intent.putExtra(TrackRecordingService.EXTRA_ON_WATER, boatIsWater)  // ← ADD
            context.startService(intent)
            delay(5_000L)
        }
    } else {
        intent.putExtra(TrackRecordingService.EXTRA_RECORDING, false)
        intent.putExtra(TrackRecordingService.EXTRA_ON_WATER, boatIsWater)      // ← ADD
        context.startService(intent)
    }
}
```

**C) Add imports** to TrackRecordingService (needed for BroadcastReceiver):
```kotlin
import android.content.BroadcastReceiver
import android.content.IntentFilter
```

---

### 4. Update notification text

**File:** [`TrackRecordingService.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt)

Modify `buildReadyNotification` and `buildRecordingNotification` to include water state:

```kotlin
private fun buildReadyNotification(isDemo: Boolean, isOnWater: Boolean): Notification {
    val waterLabel = if (isOnWater) "On Water" else "On Land"
    val text = if (isDemo) "Ready (Demo) • $waterLabel" else "Ready • $waterLabel"
    // ... rest unchanged
}
```

This gives Tasker users a third way to detect water state (Notification event → text contains "On Water").

---

### 5. No AndroidManifest changes needed

The query receiver is dynamically registered in `TrackRecordingService.onCreate()`. The push broadcast and query response are sent via `sendBroadcast()` — no manifest registration required since we're sending, not receiving via manifest.

The service is already declared in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml:42-49).

---

## Tasker Configuration (User Side)

### Profile 1: Water State Toggle (Push)

```
Profile → Event → System → Intent Received
  Action: ykws.android.maro.action.WATER_STATE_CHANGED
```

Task variables:
- `%on_water` — `true` or `false`
- `%latitude`, `%longitude` — GPS position (if included)

### Profile 2: Poll Water State (Query)

```
Task → Misc → Send Intent
  Action: ykws.android.maro.action.QUERY_WATER_STATE
  Target: Broadcast Receiver
```

Then catch the result with another Intent Received profile on `ykws.android.maro.action.WATER_STATE_RESULT`.

---

## File Change Summary

| File | Change |
|------|--------|
| [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) | Add `_boatIsWater` StateFlow; compute at GPS fix + dead reckoning + GPS-off |
| [`TrackRecordingService.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt) | Store water state; push broadcast on toggle; query receiver; update notification text |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Send `EXTRA_ON_WATER` in ACTION_UPDATE intents |

No new files. No manifest changes.

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Coastline not loaded yet | `isOnWater()` returns false → `lastKnownOnWater` stays false → no spurious push |
| GPS fix lost (gap → dead reckoning) | Dead reckoning also computes `isOnWater()` — state stays accurate |
| GPS mode turned off | `_boatIsWater` reset to false → push if was true |
| App backgrounded | Service stays alive → query still works; push still fires |
| Tasker sends query while Maro not running | No receiver registered → broadcast ignored (no crash) |
| Rapid land→water→land toggles (coastal noise) | `distinctUntilChanged` via the service's `lastKnownOnWater` comparison prevents spam |
