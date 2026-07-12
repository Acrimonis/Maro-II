# BoatTrace — Merge Tracks & Resume Track — Discussion Plan

**Created:** 2026-07-12 13:47 UTC
**Status:** discussion
**Active Subfeature:** merge-tracks

---

## Use Case

> On a trip, the recording got interrupted. For example: outbound leg recorded as Track A, then forced to start a new Track B for the return leg. The user wants to reconcile these into a single logical trip.

Two approaches are under evaluation:

| | **Merge Tracks** | **Resume Existing Track** |
|---|---|---|
| **When** | After both tracks are finalized | Before the second leg starts |
| **Result** | New track (new UUID), originals optionally kept | Same track (same UUID), modified in-place |
| **Mental model** | "Combine these two recordings" | "Continue where I left off" |

---

## Feature A: Merge Tracks — Implementation Plan

### Concept
The user selects 2+ finalized tracks from the history list and merges them into a single new track with a new UUID. The original tracks are optionally kept. A GAP marker is inserted between each segment so the polyline shows visible breaks.

### Design Decisions (Resolved)

| # | Decision | Rationale |
|---|---|---|
| M1 | **New `TrackMerger` utility class** | Pure function: `merge(tracks, name) → Track`. Keeps merge logic separate from persistence and UI. |
| M2 | **Stats: sum per-track values, don't recompute from scratch** | `distanceNm`, `navigatingDurationSec`, `idleDurationSec` are already correct per track. `averageSpeedMps` = weighted average of track averages. `fastestSpeedMps` = max. No O(n) point scan needed. |
| M3 | **`timeOffsetMs` rebasing: `point.timeOffsetMs + (track.startTimeMs - mergedStartTimeMs)`** | Each track's offsets are relative to its own start. Must rebase to the earliest track's start. Enforce monotonicity. |
| M4 | **GAP marker always inserted between segments** | Two separate recordings always have a temporal gap. One `PointType.GAP` per seam — no threshold check needed (merge is always a gap). |
| M5 | **No simplification during merge** | Each track was already simplified at its own finalize. Double-simplifying would lose detail. |
| M6 | **Naming: prompt dialog with auto-default** | 2 tracks: `"[nameA] + [nameB]"`. 3+ tracks: `"[firstName] ... [lastName]"`. User can edit. |
| M7 | **Originals kept by default** | "Keep original tracks" checkbox, default checked. Safer default — user can delete manually if desired. |
| M8 | **Color: first (earliest) track's color** | Simple, predictable. No prompt needed. |
| M9 | **Pinned: merged track pinned only if ALL sources pinned** | Conservative — one unpinned source means the merged result isn't universally pinned. |
| M10 | **Multi-select: "Select" button → checkbox mode → "Merge (n)" bottom bar** | Standard Material pattern. Checkbox on each card. Bottom bar appears with count, disabled until n≥2. |

### Algorithm — `TrackMerger.merge()`

```
Input:  tracks: List<Track> (pre-sorted by startTimeMs asc), mergedName: String
Output: Track

Precondition: tracks.size >= 2, all have endTimeMs != null, all have trackPoints.isNotEmpty()

1. Compute merged metadata:
   mergedStartMs = tracks.first().startTimeMs
   mergedEndMs = tracks.last().endTimeMs!!

2. Concatenate trackPoints with rebasing:
   mergedPoints = mutableListOf()
   lastOffsetMs = -1L

   for each (track, index) in tracks:
     if index > 0:
       // Insert GAP marker at last real point's position
       gap = TrackPoint(
         lat = mergedPoints.last().lat,
         lon = mergedPoints.last().lon,
         type = PointType.GAP,
         timeOffsetMs = lastOffsetMs + 1,
         timeOffsetSec = ((lastOffsetMs + 1) / 1000).toInt()
       )
       mergedPoints.add(gap)
       lastOffsetMs = gap.timeOffsetMs

     // Rebase this track's points to merged start
     trackDeltaMs = track.startTimeMs - mergedStartMs
     for point in track.trackPoints:
       rebasedMs = point.timeOffsetMs + trackDeltaMs
       if rebasedMs <= lastOffsetMs:
         rebasedMs = lastOffsetMs + 1  // monotonicity enforcement
       mergedPoints.add(point.copy(
         timeOffsetMs = rebasedMs,
         timeOffsetSec = (rebasedMs / 1000).toInt()
       ))
       lastOffsetMs = rebasedMs

3. Concatenate boatMarkers with renumbered sequenceIndex:
   // BoatMarker.startTimeMs/endTimeMs are absolute epoch ms — no rebasing needed.
   // boatLat/boatLon are absolute coordinates — no rebasing needed.
   mergedMarkers = mutableListOf()
   seqIdx = 0
   for track in tracks:
     for marker in track.boatMarkers:
       mergedMarkers.add(marker.copy(sequenceIndex = seqIdx++))

4. Compute stats from per-track values:
   totalPoints = sum of track.trackPoints.size for each track
   avgSpeedMps = if totalPoints > 0:
     sum(track.averageSpeedMps * track.trackPoints.size) / totalPoints
   else: 0f

5. Assemble merged track:
   Track(
     id = UUID.randomUUID().toString(),
     name = mergedName,
     startTimeMs = mergedStartMs,
     endTimeMs = mergedEndMs,
     trackPoints = mergedPoints,
     boatMarkers = mergedMarkers,
     distanceNm = sum of track.distanceNm,
     fastestSpeedMps = max of track.fastestSpeedMps,
     averageSpeedMps = avgSpeedMps,
     navigatingDurationSec = sum of track.navigatingDurationSec,
     idleDurationSec = sum of track.idleDurationSec,
     trackColorArgb = tracks.first().trackColorArgb,
     pinned = tracks.all { it.pinned },
     visibleOnMap = true,
     updatedAtEpochMs = System.currentTimeMillis()
   )
```

### Implementation Steps

#### Step 1: `TrackMerger` — new utility class

**New file:** `app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt`

Pure Kotlin, no Android dependencies. Single public method `merge(tracks, mergedName)`. No coroutines needed — all data is in memory.

#### Step 2: `TrackViewModel.mergeTracks()` — orchestration

**File:** [`app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt)

```kotlin
fun mergeTracks(trackIds: List<String>, mergedName: String, keepOriginals: Boolean) {
    viewModelScope.launch {
        // Load all tracks, sort by startTimeMs
        val tracks = trackIds.mapNotNull { repository.load(it) }
            .filter { it.endTimeMs != null && it.trackPoints.isNotEmpty() }
            .sortedBy { it.startTimeMs }
        if (tracks.size < 2) return@launch

        // Merge
        val merger = TrackMerger()
        val merged = merger.merge(tracks, mergedName)

        // Save merged track
        repository.save(merged)

        // Optionally delete originals
        if (!keepOriginals) {
            trackIds.forEach { repository.delete(it) }
        }

        // Refresh UI
        refreshSummaries()
        _events.emit(TrackEvent.TracksMerged(merged.id, merged.name))
    }
}
```

#### Step 3: `TrackHistoryOverlay` — add Merge action to existing multi-select

**File:** [`app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt)

**Note:** [`ListOverlayScaffold`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:451) already has a complete multi-select framework (long-press to enter, checkboxes, select-all header, animated action bar). `TrackHistoryOverlay` already uses it with `trackMultiActions` containing Delete, Export, and Pin actions. We just add one more:

```kotlin
MultiActionSpec(
    id = "merge",
    label = "Merge",
    icon = Icons.Filled.MergeType,  // or CallMerge
    enabled = { ids -> ids.size >= 2 },
    action = { ids ->
        // Trigger merge flow — fires name dialog via onAction callback
        onAction(ListAction.MergeTracks(ids))
    }
)
```

**Naming dialog:** `MultiActionSpec` doesn't support custom dialogs (only `confirmMessage`). Handle the name dialog in `TrackHistoryOverlay`'s `onAction` handler when `ListAction.MergeTracks` is received:

```kotlin
is ListAction.MergeTracks -> {
    // Show AlertDialog with:
    // - Pre-filled TextField: auto-generated name (M6)
    // - Checkbox "Keep original tracks" (default checked, M7)
    // - Cancel / Merge buttons
    // On confirm: viewModel.mergeTracks(action.ids, name, keepOriginals)
    // Show Snackbar "Merged into [name]"
}
```

All other multi-select behavior (checkboxes, header, select-all, back handler, auto-exit after action) is handled by the scaffold — zero new code for those.

#### Step 4: `ListAction.MergeTracks` + `TrackEvent.TracksMerged` — new types

**File:** [`app/src/main/java/ykws/android/maro/data/model/ListAction.kt`](app/src/main/java/ykws/android/maro/data/model/ListAction.kt)

```kotlin
/** Merge selected tracks into a single new track. */
data class MergeTracks(val ids: Set<String>) : ListAction()
```

**File:** [`app/src/main/java/ykws/android/maro/data/track/TrackEvent.kt`](app/src/main/java/ykws/android/maro/data/track/TrackEvent.kt)

```kotlin
/** Tracks were successfully merged. */
data class TracksMerged(val mergedId: String, val mergedName: String) : TrackEvent()
```

### Edge Cases

| Scenario | Handling |
|---|---|
| Single track selected | `enabled` predicate: `ids.size >= 2` → button dimmed |
| Track with zero points in selection | Filtered out in `mergeTracks()` — if fewer than 2 remain, abort |
| Merging tracks with different colors | Uses first (earliest) track's color (M8) |
| Some pinned, some not | Merged track is pinned only if ALL are pinned (M9) |
| Merge, then undo | Originals kept by default — delete merged track and retry |
| Merge, then resume merged track | Works — merged track is a normal finalized track |
| 10+ tracks merged | Fully supported. Performance: O(totalPoints) for copy loop |

### Files Touched

| File | Change |
|---|---|
| **New:** `TrackMerger.kt` | Pure merge logic |
| `TrackViewModel.kt` | New `mergeTracks()` method |
| `TrackHistoryOverlay.kt` | Add `MultiActionSpec("merge")` to `trackMultiActions` + name dialog in `onAction` |
| `ListAction.kt` | New `MergeTracks(ids)` action |
| `TrackEvent.kt` | New `TracksMerged` event |
| `TrackRepository.kt` | No changes needed |
---

## Feature B: Resume Existing Track — Implementation Plan

### Concept
The user picks a finalized track from history and resumes recording on it. The track transitions from "finalized" back to "recording", new points append to the existing polyline, and stats update incrementally.

### Current State
[`TrackRecorder.resume()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:274) already exists — but only for **crash recovery** (orphaned `_checkpoint.bin` files). It:
- Restores state from a checkpointed `Track`
- Inserts a GAP marker on first new point
- Deletes the checkpoint file after confirming resume
- Emits `Resumed(points)` event to restore the polyline

The gap: it cannot resume a **finalized** track (`.bin` file, `endTimeMs` set, no checkpoint).

### Design Decisions (Resolved)

| # | Decision | Rationale |
|---|---|---|
| D1 | **API: `fromCheckpoint: Boolean = true` flag on `resume()`** | Only 3 lines differ (endTimeMs + checkpointFileDeleted + resumeGapDurationSec). Flag keeps the code path unified. |
| D2 | **Clear `endTimeMs` to null on resume** | `currentTrack = track.copy(endTimeMs = null)`. A track being recorded doesn't have an end time. Re-set on finalize. |
| D3 | **`checkpointFileDeleted = true` when not from checkpoint** | Avoids the no-op `deleteCheckpoint()` call. Set based on `fromCheckpoint` flag. |
| D4 | **Stats carry-forward (incremental)** | `speedSumMps = avgSpeed * pointCount`, `cumulativeDistanceNm = track.distanceNm`. Already in current `resume()`. |
| D5 | **BoatMarkers carry forward** | `idleDurationSec = track.idleDurationSec`. Existing markers preserved. New idle periods append during recording. |
| D6 | **Title: skip auto-rename on re-finalize if user customized** | If current name doesn't match `yyyy-MM-dd HH:mm` pattern (auto-generated), skip `computeFinalTitle()`. Respects user edits. |
| D7 | **`visibleOnMap` forced `true` on resume** | User explicitly chose to resume — they want to see it. Force `true`, keep `true` after finalize. |
| D8 | **UI: ▶ "Resume" icon on track card** | Placed between pin and share icons. Only visible when track is finalized (`endTimeMs != null`) and no recording active. |
| D9 | **Concurrent guard: prevent if already recording** | Existing `state != OFF → return` handles this + UI hides the button. |
| D10 | **`resumeGapDurationSec`: exclude inter-session gap from navigating duration** | When resuming a finalized track, the wall-clock gap between original `endTimeMs` and resume time would inflate `navigatingDurationSec` (bug: a 2h track resumed 2 days later would report 50h of navigating). Store gap at resume, subtract at finalize. Also fixes `elapsedSeconds` display. |
| D11 | **`resumeTrack()` mirrors `resumeOrphanedCheckpoint()` setup exactly** | Same 9-step sequence: stopRecorder → buildRecorder → collectors → activeRecorder → resume → events → refreshSummaries. Only difference: `fromCheckpoint = false`. |

### Implementation Steps

#### Step 1: `TrackRecorder.resume()` — accept `fromCheckpoint` flag + gap tracking

**File:** [`app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:274)

New field:
```kotlin
private var resumeGapDurationSec: Long = 0L
```

Modified `resume()`:
```kotlin
fun resume(track: Track, sampleFlow: Flow<TrackSample>, fromCheckpoint: Boolean = true) {
    if (state != TrackRecorderState.OFF) {
        Log.w(TAG, "resume: ignored — state=$state (not OFF)")
        return
    }

    val now = System.currentTimeMillis()
    val points = track.trackPoints
    val lastPoint = points.lastOrNull()

    // Compute inter-session gap BEFORE clearing endTimeMs (D10)
    resumeGapDurationSec = if (!fromCheckpoint && track.endTimeMs != null) {
        (now - track.endTimeMs) / 1000
    } else 0L

    // If resuming a finalized track, clear endTimeMs (it's being recorded again)
    val resumedTrack = if (!fromCheckpoint && track.endTimeMs != null) {
        track.copy(endTimeMs = null)
    } else {
        track
    }

    // Restore track state
    currentTrack = resumedTrack
    recordingStartTimeMs = resumedTrack.startTimeMs
    // ... (rest of existing code unchanged) ...

    isResuming = lastPoint != null
    checkpointFileDeleted = !fromCheckpoint  // ← skip delete for finalized tracks

    // elapsedSeconds excludes the inter-session gap (D10)
    val displayElapsed = (now - resumedTrack.startTimeMs - resumeGapDurationSec) / 1000
    _uiState.update {
        TrackRecorderUiState(
            state = TrackRecorderState.ON,
            currentTrackId = resumedTrack.id,
            currentTrackName = resumedTrack.name,
            currentTrackComment = resumedTrack.comment,
            isMoving = false,
            pointCount = points.size,
            distanceNm = resumedTrack.distanceNm,
            avgSpeedKn = resumedTrack.averageSpeedMps * 1.94384f,
            maxSpeedKn = resumedTrack.fastestSpeedMps * 1.94384f,
            elapsedSeconds = displayElapsed  // ← gap-aware
        )
    }
    // ... (rest unchanged: scope, collectors, events, checkpoint job, polling) ...
}
```

Changes: 3 additions — `resumeGapDurationSec` computation, `resumedTrack` copy, gap-aware `elapsedSeconds`.

#### Step 2: `TrackRecorder.finalizeTrack()` — gap-aware navigating + title guard

**File:** [`app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:850)

Two changes at the `trackAfterClose.copy(...)` block:

```kotlin
val finalized = trackAfterClose.copy(
    trackPoints = simplifiedPoints,
    endTimeMs = System.currentTimeMillis(),
    pausedDurationSec = 0,
    idleDurationSec = idleDurationSec,
    averageSpeedMps = avgMps,
    distanceNm = cumulativeDistanceNm,
    navigatingDurationSec = totalElapsedSec - idleDurationSec - resumeGapDurationSec,  // ← D10
    updatedAtEpochMs = System.currentTimeMillis(),
    visibleOnMap = true  // ← D7: force visible after resume
)
```

Title guard (D6):
```kotlin
// Only auto-rename if the current name matches the auto-generated pattern
val isAutoName = trackAfterClose.name.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"))
val finalName = if (isAutoName) computeFinalTitle(finalized) else null
val finalizedWithTitle = if (finalName != null) finalized.copy(name = finalName) else finalized
```

#### Step 3: `TrackViewModel.resumeTrack()` — new method

**File:** [`app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt)

Mirrors [`resumeOrphanedCheckpoint()`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:319) with identical 9-step setup (D11):

```kotlin
fun resumeTrack(trackId: String) {
    // Guard: cannot resume while already recording
    if (_uiState.value.state == TrackRecorderState.ON) {
        Log.w(TAG, "resumeTrack: already recording")
        return
    }
    viewModelScope.launch {
        // 1. Load and validate the track
        val track = repository.load(trackId) ?: return@launch
        if (track.endTimeMs == null) {
            Log.w(TAG, "resumeTrack: track $trackId is not finalized")
            return@launch
        }
        // Force visible (D7)
        if (!track.visibleOnMap) {
            repository.save(track.copy(visibleOnMap = true))
        }

        // 2. Stop any existing recorder
        val sampleFlow = cachedSampleFlow ?: return@launch
        stopRecorder()

        // 3. Build new recorder (same params as resumeOrphanedCheckpoint)
        val rec = buildRecorder()

        // 4. Set active recorder
        recorder = rec

        // 5. Wire event forwarding
        eventsForwardingJob?.cancel()
        eventsForwardingJob = viewModelScope.launch {
            rec.events.collect { _events.emit(it) }
        }

        // 6. Wire UI state
        viewModelScope.launch {
            rec.uiState.collect { state -> _uiState.value = state }
        }

        // 7. Register with foreground service
        TrackRecordingService.activeRecorder = rec

        // 8. Resume with fromCheckpoint=false ← THE ONLY DIFFERENCE
        rec.resume(track, sampleFlow, fromCheckpoint = false)

        // 9. Emit Resumed + refresh summaries
        if (track.trackPoints.isNotEmpty()) {
            _events.tryEmit(TrackEvent.Resumed(track.trackPoints))
        }
        viewModelScope.launch {
            delay(500)
            refreshSummaries()
        }
    }
}
```

Note: No `buildRecorder()` helper exists — the constructor is duplicated inline in [`startRecorder()`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:122), [`resumeOrphanedCheckpoint()`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:329), and [`initRecorder()`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:184). Either duplicate inline (consistent with existing pattern) or extract a shared helper.

#### Step 4: `TrackHistoryOverlay` — Resume button

**File:** [`app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt)

Add a ▶ play/resume icon button on each finalized track card. Placement between pin and share:

```
Row: [pin icon] [▶ resume icon] [share icon]
```

Visibility: `track.endTimeMs != null && uiState.state == OFF`

On tap: `viewModel.resumeTrack(track.id)`, then close the history overlay.

### Edge Cases

| Scenario | Handling |
|---|---|
| Resume while recording | **Cannot happen** — the ▶ button is only visible when `uiState.state == OFF`. The guard in `resume()` is defensive only. |
| Resume, then stop without moving | `finalizeTrack()` runs — no new points, stats unchanged, `endTimeMs` updated to now |
| Resume same track multiple times across sessions | Example: resume Track A → record leg 2 → finalize. Next week, resume Track A again → record leg 3 → finalize. Each resume inserts a GAP marker at the seam (gap detection fires on the first new point each time). This is correct — the GAP markers visibly show where recording was paused between legs. |
| Track file corrupted | `repository.load()` returns null → log and return, no crash |
| Pinned track resumed | Stays pinned — `pinned` flag untouched by resume/finalize |
| Hidden track resumed | Force `visibleOnMap = true` before passing to recorder |

### Files Touched

| File | Change |
|---|---|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | `resume()`: add `fromCheckpoint` param, clear `endTimeMs`, set `checkpointFileDeleted`. `finalizeTrack()`: pattern-guard title recompute, force `visibleOnMap` |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) | New `resumeTrack(trackId)` method |
| [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) | ▶ Resume icon on track cards |
| [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) | No changes |

---

## Comparison

```
                    MERGE                     RESUME
                    ─────                     ──────
Track A ──┐                    Track A (finalized)
          ├─→ NEW Track C               │
Track B ──┘                    resume() ↓
                               Track A (recording)
                                      │
                               finalize() ↓
                               Track A (updated)
```

| Axis | Merge | Resume |
|---|---|---|
| **Data integrity** | Safer — originals preserved, new track atomic | Riskier — modifies existing track in-place |
| **Stats** | Sum per-track stats (no recompute) | Incremental (gap-aware carry-forward) |
| **Undo** | Delete merged track, originals still exist | No undo (in-place mutation) |
| **UX friction** | Multi-select + confirm dialog | Single tap ▶ |
| **Implementation** | New `TrackMerger` + multi-select UI | Extend `resume()` + one button |
| **timeOffsetMs** | Rebasing required for segments 2+ | No rebasing (same startTimeMs) |
| **BoatMarkers** | Concatenate + renumber sequenceIndex | Carry forward unchanged |
| **Use case** | "I already have two finalized recordings" | "I want to continue this track" |

### Verdict

Both features are valid and not mutually exclusive. For the stated use case ("interrupted recording"):

- **Resume** is the natural fit — one continuous trip with a gap. Smaller implementation lift (extends existing infrastructure).
- **Merge** is the reconciliation tool — when the user already committed to separate recordings and later decides they should be one.

**Recommended order:** Resume first, Merge second. Both fully designed and ready for implementation.

---

## Summary: Files Touched (Both Features)

| File | Resume | Merge |
|---|---|---|
| [`TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) | `resume(fromCheckpoint)`, `finalizeTrack(gap+navigating+title+visible)` | — |
| [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) | `resumeTrack(trackId)` | `mergeTracks(ids, name, keepOriginals)` |
| [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) | ▶ button per card | Multi-select mode + "Merge (n)" bar + name dialog |
| **New:** `TrackMerger.kt` | — | Pure merge algorithm |
| [`TrackEvent.kt`](app/src/main/java/ykws/android/maro/data/track/TrackEvent.kt) | — | `TracksMerged` event |
| [`TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) | No changes | No changes |
