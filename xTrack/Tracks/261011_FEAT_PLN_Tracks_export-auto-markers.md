# Tracks — a trip's GPX carries its auto markers

**Date:** 2026-10-11
**Status:** design — nothing implemented. Second half of the delivery on `feature/mrkr-imexport`, after the marker transfer lands.
**Depends on:** `xTrack/Markers/261011_FEAT_PLN_Markers_marker-import-export.md` — the marker payload codec (base64 blob + JSON mirror, one `Json` home) is that plan's and is reused here verbatim.
**Reviewed:** 2026-10-11 — the findings below are folded in: the `toGpx` signature and its two live call sites, the owner of the marker write, and the auto marker that carries no `trackId`.

## Why

A track export today carries the polyline and the `Track` blob, and nothing else: the trip's own auto markers are left behind, so moving a trip to another device loses the places it stopped. The link already exists and is already maintained — `UserMarker.trackId`, written at creation by [`AutoMarkerManager.createTemp()`](../../app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt:32), re-pointed on a merge by [`TrackViewModel`](../../app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:446), adopted by [`TrackRepository`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:293) and read as a cascade by [`TrackRepository.deleteTrack()`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:133). This plan carries that link into the file.

## Decisions

- **Carriage** — a track's `<extensions>` gains one payload per owned marker: every marker whose `trackId` is this track's id, in the same two forms the marker file uses — the base64 protobuf and the JSON mirror.
- **Readable companions** — one standard `<wpt>` per owned marker as well, so another map application sees the trip's stops rather than an opaque blob.
- **The link is remapped on import** — a bundled marker's `trackId` is rewritten to the imported track's id: kept as it is on an UPDATE (the id is preserved), freshly minted on `IMPORT_NEW` and on a foreign file, which both take a new UUID. This is the same re-point the merge path already performs, so the operation exists and only needs a home.
- **A plain waypoint is born a keeper** — a marker read from a `<wpt>` with no Maro payload arrives `confirmed = true`, `keepable = true`. It must not arrive as-is: [`MapMarkerEffects`](../../app/src/main/java/ykws/android/maro/ui/map/MapMarkerEffects.kt:47) deletes `IDLE_AUTO && !keepable` markers at startup, so an imported stop would vanish on the next launch.
- **Skip-consistency** — a track skipped as a duplicate takes its bundled markers with it; no marker is written pointing at an id that was never persisted. The skip decision is per track, and the markers follow the track's own outcome.
- **The door rule** — the marker importer refuses a file carrying a `<trk>`, so a trip file opened through the marker door writes nothing instead of importing the trip's stops as standalone markers.
- **No new field, no new key** — nothing is added to `Track`, `UserMarker` or `maro.properties`; the carriage lives entirely in the two GPX files and the existing `trackId`.
- **Who writes both stores** — the import must persist the bundled markers through `UserMarkerRepository`, and `TrackViewModel` holds no such repository today. The natural owner is `AutoMarkerManager`, which already wraps that repository and owns the `IDLE_AUTO` lifecycle for the recorder, so the carriage adds an adoption door there rather than a second store dependency inside the view model.
- **The signature follows the call sites** — `toGpx()` is called twice, both in [`TrackSharing.kt`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSharing.kt:57) (`shareTrackGpx` and `shareTracksZip`), so it gains an explicit marker parameter and those two call sites resolve the owned markers; a defaulted parameter would silently ship every existing export without its markers.

## Reversal recorded

The old plan `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-export-import.md` evaluated "include track markers (IDLE_AUTO) — yes, with `trackId`" inside the **marker** file and "export all markers (USER + IDLE_AUTO)". Both are reversed: the auto markers travel **inside their track's file**, and the marker file takes user markers only, so the same marker is not shipped twice under two different lifetimes.

## Files

| File | Change |
|---|---|
| `data/track/GpxExporter.kt` | `Track.toGpx()` gains the owned markers' payloads and their `<wpt>` companions |
| `data/track/GpxImporter.kt` | Reads the bundled payloads, remaps `trackId`, applies the skip-consistency and the keeper rule |
| `ui/map/TrackSharing.kt` | The two `toGpx()` call sites — each resolves the track's owned markers before the file is written |
| `data/markers/AutoMarkerManager.kt` | Gains the adoption door that writes a track's bundled markers through the repository it already holds |
| `data/track/TrackViewModel.kt` | Hands the bundled markers to that door on the import path, so one import writes both stores |
| `data/markers/MarkerJson.kt` | Read, unchanged — the codec home the marker plan creates |

## Tests

- A track exported with two owned markers carries both payloads and both waypoints, and an import restores them with `trackId` equal to the imported track's id.
- On `IMPORT_NEW` the track takes a fresh id and every bundled marker follows it — no marker keeps the old id.
- A duplicated track skipped on import writes no marker.
- A foreign trip file (no Maro payload) yields markers that are `confirmed` and `keepable`, and they survive the startup cleanup predicate.
- The trip file is refused by the marker importer.

## Verification

The track and marker suites green with `gradlew :app:testDebugUnitTest` and `gradlew :app:assembleDebug`, then `apk-build.bat`; the device pass — export a recorded trip, wipe the stores, re-import, and see the trip's stops return attached to it — stays owed to the user.

## Open points

- **Finding a track's markers** — there is no inverse index, so the export side filters the marker store by `trackId`; the export path already holds the summaries, so one `loadAll()` and a filter is enough and no index is introduced. Pinned at implementation.
- **A user marker that also carries a `trackId`** — it travels in both files; identity by id makes the second arrival a skip or an in-place replace, which is accepted rather than special-cased.
- **The track-less auto marker is settled the other way** — a confirmed 🕐 auto marker built by `MarkersViewModel`'s own [`addTempAutoMarker`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:1136) carries no `trackId`, so no track's file can claim it; the marker transfer's set was widened on 2026-10-11 to `origin == USER || trackId == null`, and that marker is exactly what the new clause is for. The carriage is unchanged by it.
- **A named finding, not work** — that same creation path could stamp the recording's own id, so an auto marker is never born unowned; the recorder's [`AutoMarkerManager.createTemp`](../../app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt:32) already does, and the UI's copy does not. Nothing here depends on it, since the backup now covers the unowned case.

## Implemented

**Shipped 2026-10-11 on `feature/mrkr-imexport`, after the marker transfer.**

- **The carriage** — [`Track.toGpx(markers)`](../../app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt:26) writes every owned marker's two payloads inside the track's `<extensions>` through the marker plan's own codec, and one `wpt` per marker ahead of the `<trk>` for other applications. The signature takes its markers explicitly and all three call sites resolve them — `TrackSharing`'s single share, its batch zip and `MapScreen`'s inline share — through a new `TrackViewModel.ownedMarkers(trackId)`.
- **The remap** — [`GpxImporter`](../../app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt:26) reads the bundled payloads and re-points each marker at the id the track actually landed on: kept on an update, minted on an import-as-new, and the same id on a skip-existing import that is not a duplicate.
- **The skip-consistency** — a skipped duplicate carries no markers at all, so nothing is written pointing at a track that was never persisted.
- **The keeper rule changed shape** — rather than reading plain `<wpt>`s and forcing them keepable, the track door reads payloads only and filters them to `keepable`, so a transient idle marker is never imported; the plain-waypoint keeper rule stays where it belongs, at the marker door. This is the one plan decision implemented differently, and it is the stricter reading.
- **The owner changed** — the markers land through `TrackViewModel`'s own `markerRepository`, which the view model already held, rather than a new `AutoMarkerManager` door: one fewer dependency, the same mutex and the same change channel that refresh the marker list and the map.

**Tests:** [`TrackMarkerCarriageTest`](../../app/src/test/java/ykws/android/maro/data/track/TrackMarkerCarriageTest.kt:1) 6/6 — a trip carrying its own markers and keeping their link, the readable waypoint ahead of the `trk`, the remap on an import-as-new, a skipped duplicate carrying none, a transient marker not carried, and a marker file importing nothing here. The full suite and `assembleDebug` are BUILD SUCCESSFUL with `apk-build.bat`; **the device pass is owed** — export a recorded trip, wipe the stores, re-import, and see its stops return attached to it.
