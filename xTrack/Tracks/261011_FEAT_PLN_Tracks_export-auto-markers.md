# Tracks — a trip's GPX carries its auto markers

**Date:** 2026-10-11
**Status:** design — nothing implemented. Second half of the delivery on `feature/mrkr-imexport`, after the marker transfer lands.
**Depends on:** `xTrack/Markers/261011_FEAT_PLN_Markers_marker-import-export.md` — the marker payload codec (base64 blob + JSON mirror, one `Json` home) is that plan's and is reused here verbatim.

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

## Reversal recorded

The old plan `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-export-import.md` evaluated "include track markers (IDLE_AUTO) — yes, with `trackId`" inside the **marker** file and "export all markers (USER + IDLE_AUTO)". Both are reversed: the auto markers travel **inside their track's file**, and the marker file takes user markers only, so the same marker is not shipped twice under two different lifetimes.

## Files

| File | Change |
|---|---|
| `data/track/GpxExporter.kt` | `Track.toGpx()` gains the owned markers' payloads and their `<wpt>` companions |
| `data/track/GpxImporter.kt` | Reads the bundled payloads, remaps `trackId`, applies the skip-consistency and the keeper rule |
| `data/track/TrackViewModel.kt` | Persists the bundled markers beside the track through `UserMarkerRepository`, so one import writes both stores |
| `data/markers/MarkerJson.kt` | Read, unchanged — the codec home the marker plan creates |

## Tests

- A track exported with two owned markers carries both payloads and both waypoints, and an import restores them with `trackId` equal to the imported track's id.
- On `IMPORT_NEW` the track takes a fresh id and every bundled marker follows it — no marker keeps the old id.
- A duplicated track skipped on import writes no marker.
- A foreign trip file (no Maro payload) yields markers that are `confirmed` and `keepable`, and they survive the startup cleanup predicate.
- The trip file is refused by the marker importer.

## Verification

The track and marker suites green with `gradlew :app:assembleDebug` and `apk-build.bat`; the device pass — export a recorded trip, wipe the stores, re-import, and see the trip's stops return attached to it — stays owed to the user.

## Open points

- **Finding a track's markers** — there is no inverse index, so the export side filters the marker store by `trackId`; the export path already holds the summaries, so one `loadAll()` and a filter is enough and no index is introduced. Pinned at implementation.
- **A user marker that also carries a `trackId`** — it travels in both files; identity by id makes the second arrival a skip or an in-place replace, which is accepted rather than special-cased.
