# 260827 — Track finalize durability & representative end time

- **Feature:** BoatTrace
- **Status:** implemented — A, B, C, D, E + atomic-move fix; build passing
- **Branch:** feature/persist-track-tweak
- **Date:** 2026-08-27

## Symptom

🤿Le Rascoui — Baptême petits Bussinger dashboard row:

- `17:38 → 22:21`, `2740 pts`, Total `4h 42m 41s`
- GPX export (creator = Maro II) last point = `18:35:52Z` = `20:35:52` local
- `endTimeMs` = `22:21:17` local → ~1h45m phantom duration

## Root cause

The track was finalized by the **orphan-checkpoint recovery path**, not the live Stop path.

- Normal stop: [`finalizeTrack()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1059) sets
  [`endTimeMs = System.currentTimeMillis()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1120).
- Recovery: [`finalizeOrphanedCheckpoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:113) sets
  [`endTimeMs = now`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:117) at recovery time.

Sequence: Stop pressed at 20:35 → `finalizeTrack()` built the final track in memory and launched an **async** coroutine
that runs [`deleteCheckpoint` then `save`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1140).
Process death in that window lost the finalized write; only the periodic checkpoint survived
([`startCheckpointJob`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1027) → `saveCheckpoint`).
On next launch the recovery dialog saved the checkpoint, stamping `endTimeMs = now` (22:21).

## Fix A — reorder finalize persistence (approved)

File: [`TrackRecorder.kt:1140`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1140)

- Save the finalized track **before** deleting the checkpoint.
- Make `save` atomic (write temp file + rename) so a crash cannot leave a partial/corrupt `.bin`.
- Invariant: a crash leaves at least one complete copy — finalized track OR checkpoint, never neither.

## Fix B — recovery end time from data (approved)

File: [`TrackRepository.kt:114`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:114)

- Derive end from the last real (non-GAP) track point:
  `dataEndMs = track.startTimeMs + lastNonGapPoint.timeOffsetMs` (fallback `now` if no points).
- `navigatingDurationSec = ((dataEndMs - track.startTimeMs)/1000 - track.idleDurationSec).coerceAtLeast(0)`.
- Keep `updatedAtEpochMs = now` — that genuinely is the recovery/finalize instant.

## Fix C — data-driven end/Total at the model layer (approved — Option 1)

The list rows are `TrackSummary` objects. Correct the value once at the model layer so every consumer
(row label, Total stat, sort-by-total, drawer summary) is consistent.

- Add persisted `lastPointTimeMs` (proto) to [`Track`](app/src/main/java/ykws/android/maro/data/track/Track.kt:30)
  and [`TrackSummary`](app/src/main/java/ykws/android/maro/data/track/Track.kt:51).
- Populate in **both** finalize paths from the last real (non-GAP) point:
  `lastPointTimeMs = startTimeMs + lastNonGapPoint.timeOffsetMs`.
- Switch consumers to `lastPointTimeMs`:
  - [`TrackHistoryOverlay.kt:495`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:495) end label,
    [`TrackHistoryOverlay.kt:655`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:655) `totalSec`
  - [`TrackViewModel.kt:240`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:240) `totalTimeSec` sort key
  - [`TrackRepository.rebuildIndex`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:139) → set `TrackSummary.lastPointTimeMs`
- `endTimeMs` keeps its "finalize time" meaning → no regression in `TrackMerger` / resume-gap.

## Fix D — transactional Stop (approved)

Make the Stop tap durable before the process can die. With A+B+C correctness is guaranteed even on crash;
D removes the recovery-dialog UX gap by ensuring the finalized track is on disk at Stop time.

- In [`finalizeTrack()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1059), perform the
  finalize write synchronously on `Dispatchers.IO` (bounded; protobuf payload is small) before `transitionTo(OFF)`:
  `runBlocking(Dispatchers.IO) { repository.save(finalized); repository.deleteCheckpoint(id) }`.
- Order: save finalized track first, then delete checkpoint (see Fix A) — atomic write (temp + rename).
- Alternative if blocking is unacceptable: keep async but hold the service with `goAsync()` and a deferred
  `stopSelf(startId)` until the write coroutine completes.

## Fix E — backfill existing tracks (one-time migration)

New proto field `lastPointTimeMs` defaults to 0 on already-saved `.bin` files and in the cached `index.bin`.
[`listTracks()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:52) reads the cached index and only
rebuilds when missing/corrupt — so existing tracks need a one-time backfill:

- **One-shot gating (no recurring per-launch cost):** persist a track-schema version in SharedPreferences.
  Run the backfill only when `storedVersion < CURRENT`, then bump `storedVersion = CURRENT`. Subsequent launches
  skip the migration entirely — a no-op.
- During the single migration pass: decode each `.bin`, compute
  `lastPointTimeMs = startTimeMs + lastNonGapPoint.timeOffsetMs`, re-save, then `rebuildIndex()`.
  Runs on `Dispatchers.IO`; cost is O(tracks × points) once, then zero.
- GPX import already derives end from points ([`GpxImporter.kt:211`](app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt:211)),
  so imported tracks are correct at import time. Existing tracks do NOT need re-import — their points are already on disk.

## Review — caveats, uncovered cases, regressions

**A — reorder + atomic save**
- [`save()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:32) is non-atomic ([`writeBytes`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:35)).
  Must switch to temp-file + rename, else a crash mid-write leaves a corrupt `.bin` that `load`/`rebuildIndex` then `delete()`s → data loss.
- `save()` also calls `updateIndex()` → full `rebuildIndex()` scan of every `.bin` ([`TrackRepository.kt:168`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:168)).
  O(tracks × points) on every save — making finalize synchronous (D) amplifies this. Consider an incremental index update.
- Residual race after reorder: crash between `save` and `deleteCheckpoint` leaves both a finalized `.bin` and a stale checkpoint.
  Next launch would offer recovery for an already-finalized track → duplicate/confusion. Guard: skip checkpoint when a finalized `.bin` with the same id exists.

**B — recovery end time from data**
- Empty / only-GAP point list → fallback `now` (unchanged behaviour). Acceptable; document it.
- Must skip trailing GAP points when deriving the last real point.
- `idleDurationSec` in the checkpoint does not include the recovery window; the new data-based `navigatingDurationSec` correctly excludes it.

**C — `lastPointTimeMs`**
- The two synthetic `TrackSummary` builders in [`OverlayLayer.kt:358`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:358) and [`OverlayLayer.kt:438`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:438) must also populate the new field, or the track-info drawer shows 0/stale.
- [`TrackMerger.merge()`](app/src/main/java/ykws/android/maro/data/track/TrackMerger.kt:135) must set `lastPointTimeMs` on the merged `Track`.
- Sort key fallback: live tracks have `lastPointTimeMs = 0`; [`totalTimeSec`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:240) must fall back to `endTimeMs ?: now`.
- Live rows: keep `endTimeMs == null` semantics so live tracks render "ongoing", not a bogus last-point end.
- Protobuf: adding a numbered field is backward/forward compatible (unknown → ignored, missing → default). Safe.

**D — transactional Stop**
- `runBlocking(Dispatchers.IO)` on the main thread risks ANR for large tracks, and `save()` triggers a full index rebuild.
  Prefer `goAsync()` + deferred `stopSelf(startId)`, or make finalize a suspend fn awaited in a service-scope coroutine.
- `finalizeTrack()` runs from both the Stop intent (main thread) and the sample coroutine (geofence auto-stop) — chosen approach must be safe in both contexts.

**E — backfill migration**
- Must touch only finalized `.bin` (`endTimeMs != null`); never `_checkpoint.bin`.
- Idempotent: skip when `lastPointTimeMs != 0`; safe to rerun (e.g. after app-data clear resets the version flag).
- First-launch cost O(tracks × points) on `Dispatchers.IO`; avoid blocking UI.
- Semantic note: all finalized tracks with an idle tail change "Total" from stop-to-start to recorded-span. That is the intended "representative" behaviour, but it is a deliberate, global change — not limited to recovered tracks.

**Cross-cutting**
- GPX export is already data-driven ([`GpxExporter.kt:54`](app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt:54)); no change. `<maro:data>` re-import now round-trips the corrected values.
- Open IDLE `BoatMarker` sweep still stamps `endTimeMs = finalizeTimeMs` ([`TrackRecorder.kt:1113`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1113)); if D moves to data-end, cap the sweep at last point for consistency (minor).
- `resumeGapDurationSec` still uses `endTimeMs` (finalize time); an idle tail counts as "gap" on resume. Related but out of scope (Option 1 leaves `endTimeMs` semantics unchanged).

## Key files

- [`app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:1059)
- [`app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:113)
- [`app/src/main/java/ykws/android/maro/data/track/Track.kt`](app/src/main/java/ykws/android/maro/data/track/Track.kt:30)
- [`app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:495)
- [`app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:240)

## Implemented

- **A** — [`TrackRepository.save()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) writes atomically (temp + `Files.move` `ATOMIC_MOVE`, `REPLACE_EXISTING`); [`finalizeTrack()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt) saves before deleting the checkpoint.
- **B** — [`finalizeOrphanedCheckpoint()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) derives `endTimeMs` + `navigatingDurationSec` from the last non-GAP point.
- **C** — `lastPointTimeMs` proto field on `Track`/`TrackSummary` ([`Track.kt`](app/src/main/java/ykws/android/maro/data/track/Track.kt)); populated in finalize, recovery, import, merge, and both `OverlayLayer` summary builders; consumed by end/Total in [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) and the `totalTimeSec` sort in [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt).
- **D** — transactional Stop: `finalizeTrack()` persists synchronously on `Dispatchers.IO` via `runBlocking`.
- **E** — one-time, version-gated [`backfillLastPointTimeMs()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) triggered from `TrackViewModel.runTrackSchemaMigration()`.
- **Guard** — [`recoverOrphanedCheckpoints()`](app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt) deletes a checkpoint whose finalized `.bin` already exists.

Build: `gradlew assembleDebug` — **BUILD SUCCESSFUL**.
