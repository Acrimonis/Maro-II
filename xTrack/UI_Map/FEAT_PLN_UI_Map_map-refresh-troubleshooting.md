# map refresh — Layer Rendering, Refresh & Caching Troubleshooting Plan

> **Subfeature of:** UI_Map  
> **Created:** 2026-07-04  
> **Status:** reviewed — hypotheses validated by Ask mode  
> **Review date:** 2026-07-04  

---

## Architecture Overview

```
FanLayer buttons (MapScreen)
  └─ onChildClick → onToggleXxx()
       └─ settingsManager.update { copy(flag=!flag) }
            └─ _settings.value = updated          // synchronous StateFlow emission
            └─ prefs.edit()...apply()              // async disk write (trailing)
                 └─ StateFlow<AppSettings> emits
                      └─ MapScreen recomposes
                           └─ visibility gating: if (flag) data else null
                                └─ CoastlineMapView.update receives data or null
                                     └─ OverlayTracker dirty-check (reference equality !==)
                                          └─ mapView.overlays.removeAll + drawXxx + invalidate()
```

Marker rendering is a separate path:

```
MarkersViewModel.toggleMarkerLayer()
  └─ settingsManager.update { copy(markerLayerState=...) }
       └─ StateFlow emits → MapScreen recomposes
            └─ if (markerLayerVisible) → MarkerOverlay composable enters composition
                 └─ DisposableEffect(markers, unconfirmedMarker, mv, matchResult, selectedMarkerId)
                      └─ removeAllMarkerOverlays() → rebuild all overlays → mv.invalidate()
```

---

## Validated Hypotheses

### ~~Issue 1-A: SettingsManager persistence race~~ — STRUCK

**Claim:** SharedPreferences `apply()` is async, could race with StateFlow emission.

**Evidence:** [`SettingsManager.kt:410-417`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:410):
```kotlin
fun update(transform: (AppSettings) -> AppSettings) {
    val current = _settings.value
    val updated = transform(current)
    if (updated == current) return
    _settings.value = updated          // ← StateFlow emits HERE, synchronously
    prefs.edit()...apply()              // ← disk write AFTER emission
}
```
StateFlow emission happens BEFORE the async `apply()`. Compose `collectAsState()` sees the new value on the next frame. **No race condition.**

### ~~Issue 3-D: Cross-effect overlay race~~ — STRUCK

**Claim:** `MarkerOverlay.DisposableEffect` and track `LaunchedEffect` both mutate `mv.overlays` and could race.

**Evidence:** 
- MarkerOverlay removes only `marker_*` prefixed overlays
- Track overlay removes only `track_hist_*` and `track_pin_*` prefixed overlays
- `CoastlineMapView.update` removes only its tracked lists
- All effects run on the Compose main thread sequentially

**No cross-contamination possible.** Each effect operates on disjoint overlay subsets.

---

## Refined Root Causes

### Issue 1: Fan Layer Toggle Inconsistency (REFINED)

**Refined Hypothesis #1-B: Null-in-null-out when toggling ON before `produceState` bitmap is ready**

The visibility gating at [`MapScreen.kt:1616-1629`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1616):
```kotlin
val visibleDepthBitmap = if (appSettings.depthLayerVisible) depthBitmap else null
```

`depthBitmap` comes from `produceState` (line 460). If the bitmap hasn't been built yet when the user toggles ON:
1. `depthBitmap` is `null` (produceState initial value)
2. `visibleDepthBitmap` = `null` (because `null` even though `depthLayerVisible` is `true`)
3. `CoastlineMapView.update` receives `depthBitmap = null`
4. OverlayTracker: `null !== tracker.lastDepthBitmap` — but if `lastDepthBitmap` is also `null` from initial state, no change detected
5. When `produceState` later builds the bitmap, `depthBitmap` changes from `null` to the actual bitmap
6. BUT: `depthBitmap !== tracker.lastDepthBitmap` → bitmap !== null → true → redraws ✓

**Wait — this should self-correct.** When produceState completes, the new bitmap reference triggers an update. Unless...

**Actual edge case:** The `produceState` uses `RasterCache` to load cached bitmaps. If the cache hit returns the same Bitmap instance that was already in the tracker (from a previous session), `!==` sees no change.

But this requires the user to toggle OFF, have the bitmap cached, toggle ON, and get the same cached instance. The OFF→ON cycle sends `null` then `bitmap`, which should always be detected as different.

**Most likely real cause for Issue #1:** We haven't found a definitive code bug. The issue may be:
- A Compose recomposition skipping the `AndroidView.update` call in certain edge cases
- The `anyFanOpen` state interfering with the control visibility (the fan button fades to α=0, user thinks they tapped but the tap was on an invisible button)
- Rapid double-tap where the second tap's state change is de-duplicated by `if (updated == current) return` in SettingsManager

**Recommended approach:** Add debug logging first to determine if this is a state problem (toggle fires but map doesn't update) or a UI problem (toggle doesn't fire because button is invisible/unresponsive).

---

### Issue 2: Marker Rendering Effects Not Updating (REFINED)

**Refined Hypothesis #2-A: Missing `markerZonesVisible` and `markerLayerState` from DisposableEffect keys**

[`MarkerOverlay.kt:134`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:134):
```kotlin
DisposableEffect(markers, unconfirmedMarker, mv, matchResult, selectedMarkerId)
```

These are the ONLY keys that trigger a full marker overlay rebuild. But the rendering logic also depends on:
- `markerZonesVisible` (line 171): controls whether zone shapes (circles, corridors) render
- `markerLayerState` (line 111): controls SHOW_ALL vs HIDDEN (but this is gated at the call site in MapScreen)

When `markerZonesVisible` changes (e.g., user toggles "Show marker zones" in settings), the `markers` list identity hasn't changed, so the `DisposableEffect` does NOT restart. The old overlays with the old zone visibility remain on the map.

**Fix:** Add `markerZonesVisible` to the DisposableEffect keys.

Additionally, when marker properties (`colorIndex`, `icon`, `pinned`) change:
1. `MarkersViewModel` calls `repo.update(marker)` then `repo.loadAll()` which always produces a NEW list (via `sortedWith()`)
2. The `markers` list reference changes → `DisposableEffect` restarts → overlays rebuilt
3. **This path works correctly.** The issue is specifically with `markerZonesVisible`.

---

### Issue 3: Markers Disappear Until App Restart (CONFIRMED)

**Confirmed Hypothesis #3-C: Silent `emptyList()` on deserialization exception**

[`UserMarkerRepository.kt:40-50`](app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt:40):
```kotlin
suspend fun loadAll(): List<UserMarker> = withContext(Dispatchers.IO) {
    if (!markersFile.exists()) return@withContext emptyList()
    try {
        val text = markersFile.readText()
        if (text.isBlank()) return@withContext emptyList()
        json.decodeFromString<List<UserMarker>>(text)
    } catch (e: Exception) {
        // Corrupt file — return empty; backup is written on next save.
        emptyList()
    }
}
```

**Scenarios that trigger silent data loss:**
1. **kotlinx.serialization exception:** If any `UserMarker` field fails to deserialize (e.g., a sealed class discriminator mismatch after a code change), the entire list is lost. `ignoreUnknownKeys = true` only handles unknown JSON keys — it does NOT handle unknown enum values or sealed class type mismatches.
2. **File encoding issue:** If the file contains invalid UTF-8, `readText()` throws.
3. **Concurrent access:** If `saveAll()` writes while `loadAll()` reads, the read could see a partial file (though write-to-tmp + rename mitigates this on most filesystems).

**The silent catch is the critical flaw:** The user has no indication their markers were lost. On the next save, the empty list overwrites the (possibly recoverable) file.

---

## Debug Session Plan

Before implementing fixes, deploy a diagnostic build with strategic logging to confirm which hypotheses manifest in the field.

### Logging Instrumentation

Add the following `Log.d("MaroMapRefresh", ...)` calls:

| # | File | Line | What to log |
|---|------|------|-------------|
| 1 | [`NavigationViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt) | 1026 | `"toggleDepthLayer → $newValue"` on every toggle |
| 2 | [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | 1628 | `"visibleDepthBitmap = ${depthBitmap?.hashCode()}"` on every recompose |
| 3 | [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | 2184 | `"update depth: new=${depthBitmap?.hashCode()} last=${tracker.lastDepthBitmap?.hashCode()} dirty=$dirty"` |
| 4 | [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) | 246 | `"toggleMarkerLayer: $current → $next"` |
| 5 | [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt) | 134 | `"DisposableEffect restart: markers=${markers.size} zonesVisible=$markerZonesVisible"` |
| 6 | [`UserMarkerRepository.kt`](app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt) | 46 | `"loadAll FAILED: ${e.message}"` with full stack trace |
| 7 | [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | 1328 | `"mapView=${mapView?.hashCode()}"` when MarkerOverlay is called |

### Reproduction Steps

**For Issue #1 (fan toggle):**
1. Cold-start the app (kill process, clear from recents)
2. Immediately open the layer fan and toggle depth layer OFF → ON → OFF rapidly
3. Observe: does the depth color map disappear/reappear correctly?
4. Check logcat: did `toggleDepthLayer` fire each time? Did `update` see a dirty change?

**For Issue #2 (marker effects):**
1. Create a marker with zone shapes visible
2. Open Settings → Markers → toggle "Show marker zones" OFF
3. Observe: do the zone shapes (circle outlines) disappear from the map?
4. Check logcat: did `DisposableEffect` restart with the new `markerZonesVisible`?

**For Issue #3 (markers disappear):**
1. Create several markers
2. Force-stop the app
3. Manually corrupt `user_markers.json` (add a `}` at the end, or change a field name)
4. Restart the app
5. Observe: do markers appear? Check logcat for the exception.

### Decision Gate

After collecting logs from one or more reproduction attempts:
- If Issue #1 logs show `toggleDepthLayer` firing but `update` sees no change → investigate the Compose recomposition path
- If Issue #1 logs show `toggleDepthLayer` NOT firing → investigate `FanLayout.onChildClick` or button visibility
- If Issue #2 logs show `DisposableEffect` NOT restarting when `markerZonesVisible` changes → confirmed, apply Fix #2
- If Issue #3 logs show a deserialization exception → confirmed, apply Fix #3

If the logs are inconclusive (no issues reproduced), the problems may be environmental (device-specific, memory pressure, configuration change during toggle) and require a different diagnostic approach.

---

## Implementation Plan

### Fix #1: Add Logging + Fan Button Diagnostic (P0)

**Files:** `NavigationViewModel.kt`, `MapScreen.kt`, `FanLayout.kt`

**Actions:**
1. Add `Log.d("MaroMapRefresh", ...)` at all 7 instrumentation points listed above
2. In `FanLayout.kt:167-169`, fix the confusing `isActive` parameter:
   ```kotlin
   // BEFORE (confusing):
   onChildClick?.invoke(i, !config.isOpen)
   // AFTER (correct):
   onChildClick?.invoke(i, activeStates.getOrElse(i) { config.toggleChildren })
   ```
   This passes the actual toggle state instead of the fan open state. MapScreen ignores this parameter today, but it should be correct for future use and debugging.
3. Build debug APK with `assembleDebug`
4. Reproduce on-device with `adb logcat -s MaroMapRefresh`

### Fix #2: Add `markerZonesVisible` to DisposableEffect Keys (P1)

**File:** [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt)

**Change:**
```kotlin
// BEFORE (line 134):
DisposableEffect(markers, unconfirmedMarker, mv, matchResult, selectedMarkerId)

// AFTER:
DisposableEffect(markers, unconfirmedMarker, mv, matchResult, selectedMarkerId, markerZonesVisible)
```

This ensures that toggling "Show marker zones" in Settings triggers a full overlay rebuild.

### Fix #3: Resilient Marker Loading with Fallback Cache (P0)

**File:** [`UserMarkerRepository.kt`](app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt)

**Changes:**

1. Add an in-memory fallback cache:
   ```kotlin
   private var lastKnownGood: List<UserMarker>? = null
   ```

2. On successful load, update the cache:
   ```kotlin
   suspend fun loadAll(): List<UserMarker> = withContext(Dispatchers.IO) {
       if (!markersFile.exists()) return@withContext emptyList()
       try {
           val text = markersFile.readText()
           if (text.isBlank()) return@withContext emptyList()
           val result = json.decodeFromString<List<UserMarker>>(text)
           lastKnownGood = result    // ← cache on success
           result
       } catch (e: Exception) {
           Log.e(TAG, "Failed to load markers, using last-known-good cache", e)
           lastKnownGood ?: emptyList()   // ← fallback to cache, or empty if never loaded
       }
   }
   ```

3. Also write a backup before overwriting:
   ```kotlin
   suspend fun saveAll(markers: List<UserMarker>) = withContext(Dispatchers.IO) {
       // Backup existing file before overwriting
       if (markersFile.exists()) {
           markersFile.copyTo(File(markersDir, MARKERS_BACKUP_NAME), overwrite = true)
       }
       val tmp = File(markersDir, MARKERS_TMP_NAME)
       tmp.writeText(json.encodeToString(ListSerializer(UserMarker.serializer()), markers))
       tmp.renameTo(markersFile)
       lastKnownGood = markers   // ← update cache after successful save
   }
   ```

4. Add constants:
   ```kotlin
   companion object {
       // ... existing ...
       private const val MARKERS_BACKUP_NAME = "user_markers.json.bak"
       private const val TAG = "UserMarkerRepo"
   }
   ```

### Fix #4: Build, Verify, Iterate

1. `apk-build.bat` to build the debug APK
2. Reproduce all three issues on-device using the steps above
3. Verify fixes via logcat confirmation
4. If any issue persists after fixes, return to the debug session plan with additional logging

---

## Key Files

| File | Role |
|------|------|
| [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](MapScreen.kt) | Fan buttons, CoastlineMapView, overlay composition, MarkerOverlay call site |
| [`app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt`](NavigationViewModel.kt) | Layer toggle methods |
| [`app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`](MarkersViewModel.kt) | Marker CRUD, layer toggle |
| [`app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`](MarkerOverlay.kt) | DisposableEffect, overlay management |
| [`app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt`](OverlayTracker.kt) | Reference-based dirty checking |
| [`app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`](FanLayout.kt) | onChildClick parameter |
| [`app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt`](SettingsManager.kt) | StateFlow + SharedPreferences persistence |
| [`app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`](UserMarkerRepository.kt) | JSON persistence, silent emptyList() on error |

---

## Implementation Order

| Priority | Issue | Action | Confidence |
|----------|-------|--------|------------|
| P0 | #3 Markers disappear | Fix UserMarkerRepository with fallback cache + backup | **High** |
| P0 | #1 Fan toggle | Add diagnostic logging + fix FanLayout parameter | **Medium** (needs field confirmation) |
| P1 | #2 Marker effects | Add `markerZonesVisible` to DisposableEffect keys | **High** |
| P2 | All | Build + on-device verification | — |
