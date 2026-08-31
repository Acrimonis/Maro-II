# BoatTrace — Marker ↔ Track Link (simplified single reference)

**Date:** 2026-08-31
**Phase:** 2 of 2 — single-reference link + UI ownership (Phase 1: auto-marker cleanup hardening)
**Status:** revised after Ask-mode review (6 amendments folded in).

## Premise corrections (from review)

- **UserMarker is JSON, not protobuf** — [`UserMarker.kt`](app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt:31) uses `@Serializable` without `@ProtoNumber`; persisted as a JSON array by [`UserMarkerRepository.kt`](app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt:35) with `ignoreUnknownKeys = true`. Adding `trackId: String? = null` is safe in both directions — **no JSON migration needed**.
- **BoatMarker is protobuf** — removing [`@ProtoNumber(8) autoMarkerId`](app/src/main/java/ykws/android/maro/data/track/BoatMarker.kt:61) is backward-compatible (unknown fields are skipped on decode). **Never reuse `@ProtoNumber(8)`** for a different type.

## Model

- Add `UserMarker.trackId: String?` — canonical, one-direction. Set **at creation** by the recorder-owned `AutoMarkerManager` (Phase 1).
- Remove the **persisted** `BoatMarker.autoMarkerId` + the two MapScreen 🕐 rendering loops + `ACTION_SET_*` intents + [`setBoatMarkerAutoMarkerId()`](app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:254).
- Keep the **in-memory** marker-id channel internal to the recorder (`session.autoMarkerId`). Since Phase 1 makes the recorder own createTemp/confirm/delete, the id no longer needs to leave the recorder; `IdleCaptureResult.autoMarkerId` and `IdlePeriodCompleted.autoMarkerId` are dropped only after the Phase 1 UI collector is gone. This preserves confirm/delete across Activity recreation.

## Backfill migration (critical)

Before removing `BoatMarker.autoMarkerId`, run a **one-time migration**: for each `Track`, map `boatMarkers[].autoMarkerId` → `UserMarker.trackId = track.id` on the matching marker. Gate behind a version flag (e.g., `MARKER_LINK_SCHEMA_VERSION`). Without this, every pre-existing confirmed auto-marker is silently orphaned (no badge, no delete-cascade, no merge-reassignment).

## Delete-track cascade ("delete track deletes its markers")

Two independent stores (tracks `.bin` protobuf vs `user_markers.json`) — no shared transaction. Rules:

- **Order: delete markers first, then the track** (markers are derived data; reverse order reintroduces ghost pins).
- **Single op**: `loadAll → filter(trackId == id && origin == IDLE_AUTO) → saveAll` (one `saveAll`, not N × `delete`).
- **Filter `origin == IDLE_AUTO`** — user markers are never swept.
- **Exclude the active recording track** (`currentTrackId`) from cascade sweeps.
- **Cover all three delete sites**: [`TrackViewModel.deleteTrack`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:284), `mergeTracks(!keepOriginals)`, and the recorder discard path. Wire the cascade into `TrackRepository.delete` (inject `UserMarkerRepository`) or a coordinating layer owning both.

## Merge ordering

- Reassign `trackId → merged.id` **before** deleting originals (otherwise the cascade deletes the very markers being reassigned).
- `keepOriginals = true`: still reassign; source tracks retained.
- Sequence: `save(merged) → reassign markers → delete(originals)`.

## Renderer unification

`MarkerOverlay` already renders every `IDLE_AUTO` marker (pinned=true, icon 🕐). Remove the duplicate MapScreen loops. Four behavior changes must be decided and documented:

1. **Layer-visibility gate** — today 🕐 pins render whenever tracks are visible, independent of the marker layer; after unification the marker layer hides them. Decide.
2. **Filtered marker source** — `MarkerOverlay` receives the filtered+sorted list; auto-markers become subject to marker-list filtering. Decide.
3. **Z-position** — markers now render above all track polylines (today interleaved per track). Decide/accept.
4. **🕐 opacity** — `MarkerOverlay` applies `boatMarkerIdleOpacityPct`; accept.

Also pin a deterministic overlay insertion order between the track `LaunchedEffect` and `MarkerOverlay`'s `DisposableEffect` (markers must stay above tracks).

## UI

1. Marker list card ([`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:290)) — "🛤️ {trackTitle}" badge when `trackId != null`; tap opens the track.
2. Marker detail card — "Belongs to track: {title}" row.
3. Track detail — "Markers on this track" group (filter by `trackId`).
4. Optional filter: "From a track" vs "Standalone".

## Edge cases

- Dangling `trackId` (track deleted, marker survived) — render no badge; never crash on title lookup.
- Imported GPX tracks carry `boatMarkers` without matching `UserMarker`s — no badge, accepted.
- Cascade must never touch the live temp marker of the currently-recorded track.

## Verification

- Build + deploy.
- E2E: backfill — pre-existing markers gain badges after upgrade.
- E2E: confirm auto-marker → badge appears; tap navigates.
- E2E: delete track → its markers deleted; no ghost pins.
- E2E: rename track → badge title updates (resolved at render).
- E2E: merge (both keepOriginals values) → markers point to merged id, originals not leaked.
- E2E: delete current recording → live temp marker survives.
