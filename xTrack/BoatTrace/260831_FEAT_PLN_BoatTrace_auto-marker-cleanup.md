# BoatTrace — Auto-Marker Cleanup Hardening Plan

**Date:** 2026-08-31
**Phase:** 1 of 2 — cleanup hardening (Phase 2: marker-track single-reference link + UI)
**Context:** `#focus track` → auto-marker (🕐 IDLE_AUTO) lifecycle cleanup. Reviewed by Ask mode — approved with five amendments (folded in below).

## Problem summary

Temp auto-markers are created `confirmed=false, keepable=false` and must be confirmed (kept) or deleted when their idle period ends. The current flow runs this decision in the `MapScreen` composable, which dies when the Activity is backgrounded — while the foreground service keeps recording. Gaps:

1. **Merged markers are non-keepable** — [`mergeAutoMarkers()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:840) creates the merged marker `confirmed=false, keepable=false` → deleted on next launch.
2. **UI-owned finalize is fragile** — confirm/delete happens in the composable event collector; an idle that starts foregrounded and ends backgrounded leaves an orphan temp marker.
3. **Swallowed finalize exception** — no retry path.
4. **Ghost 🕐 pin** — deleting a kept auto-marker never clears `BoatMarker.autoMarkerId`, so [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1274) keeps drawing its pin.
5. **Final open idle at finalize** — kept by design (decision confirmed).

## Approach

### 1. AutoMarkerManager (data layer)
New `AutoMarkerManager` owning `createTemp`, `confirm`, `delete` for IDLE_AUTO markers, backed by [`UserMarkerRepository`](app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt:31) (Context constructor — same pattern as the track repo). `createTemp` must preserve the dedup parity of [`addTempAutoMarker()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:720): reuse an existing unconfirmed temp marker within `dedupRadiusM`, skip if an already-confirmed marker exists there, otherwise create.

### 2. Serialize repository writes
`add`/`update`/`delete` are unlocked load→save cycles ([`UserMarkerRepository.kt`](app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt:74)). With two writers (UI VM + service) a lost-update race corrupts `user_markers.json`. Add a `Mutex` (or shared repo instance) and a service→UI change channel so [`MarkersViewModel._allMarkers`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:135) stays fresh after service-side writes.

### 3. Recorder wiring (service-owned lifecycle)
The recorder calls `AutoMarkerManager` directly:
- `IdlePeriodStarted` → `createTemp(...)`.
- `IdlePeriodCompleted` → confirm (duration ≥ 120 s, or finalize) or delete (too short).
- **Ordering:** confirm the marker **before** persisting `BoatMarker.autoMarkerId`, so a confirmed marker never has a dangling null reference.

### 4. Durable finalize fallback
On confirm/delete failure in `finalizeTrack`, run the fallback `delete` inside the existing `runBlocking(Dispatchers.IO)` ([`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1119)) so Stop remains durable.

### 5. Ghost pin: render-time existence check
Expose an **unfiltered all-marker id set** (only the filtered `markers` list is currently public). In both track-overlay loops (history [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1273) and pinned), skip a BoatMarker whose `autoMarkerId` is not in that set. No marker → no pin.

### 6. Dead code removal
Remove: `ACTION_SET_ACTIVE_SESSION_AUTO_MARKER_ID` / `ACTION_SET_BOAT_MARKER_AUTO_MARKER_ID` ([`TrackRecordingService.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt:462)), [`setActiveSessionAutoMarkerId()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:249) / [`setBoatMarkerAutoMarkerId()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:254), and the `IdleCaptureResult.autoMarkerId` field.

### 7. Startup cleanup = crash recovery only
Keep the existing `LaunchedEffect(Unit)` cleanup, scoped to `origin == IDLE_AUTO && !keepable` — only for process-death mid-idle, not a recurring sweep.

### 8. Merged markers: keepable + confirmed
In [`mergeAutoMarkers()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:846), set `confirmed = true, keepable = true` on the merged marker.

### 9. Final open idle policy (confirmed)
Keep current behavior — the final stop of a finished track is kept even if under 120 s.

## Verification

- Build + deploy to device.
- E2E: short idle (< 120 s) → marker deleted, no pin.
- E2E: long idle (≥ 120 s) → marker kept (white).
- E2E: idle starts foreground, ends backgrounded → confirmed/deleted correctly (no orphan).
- E2E: kill process mid-idle → temp marker removed at next launch.
- E2E: merge two auto-markers → merged marker survives restart.
- E2E: delete a kept auto-marker → track pin disappears.
