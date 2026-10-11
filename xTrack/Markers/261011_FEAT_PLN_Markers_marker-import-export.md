# Markers — marker export/import (GPX transfer)

**Date:** 2026-10-11
**Status:** design — nothing implemented. Branch `feature/mrkr-imexport`, cut from `origin/develop` by the user's `#new`.
**Supersedes:** the marker half of `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-export-import.md` — the format, the three import modes and the identity rule are carried forward here, its export-all evaluation is reversed.

## Decisions (locked with the user, 2026-10-11)

- **Format** — GPX 1.1, one file per marker: a `<wpt>` at the marker's centre plus two payloads in `<extensions>` — `<maro:markerData>` (base64 protobuf of the whole `UserMarker`, the lossless path) and `<maro:markerJson>` (CDATA, the same object in pretty JSON, mirroring every field name-for-name so a human or a foreign tool reads all of them).
- **Batch** — a ZIP of per-marker `.gpx` files, shared through the same `ACTION_SEND` chooser the track batch uses.
- **The exported set** — `origin == USER` only. An auto (`IDLE_AUTO`) marker exists for its trip and rides that track's own file — see `xTrack/Tracks/261011_FEAT_PLN_Tracks_export-auto-markers.md`. This **reverses** the old plan's "export all `UserMarker`s (USER + IDLE_AUTO)".
- **Doors** — a share action on the marker card, an export spec in the list's multi-select bar, and a Markers transfer card in Settings carrying Export and Import. The old plan's "import action in the menu" door is **not** taken.
- **Single-file import** — a match on an existing marker asks Duplicate / Override / Cancel; a ZIP import never asks and always skips (idempotent re-import).
- **Identity** — the blob's `id`, else the mirror's `id`, else the `<wpt>`'s `name`; a foreign waypoint is read as a Pin.
- **`trackId`** — kept verbatim, never rewritten. A dangling id is expected on another device and already harmless: the card reads `marker.trackId?.let(trackTitleLookup)` and shows nothing when the track is absent.
- **Geometry as a foreign consumer sees it** — the `<wpt>` carries the centre alone, so a Circle's radius and a Corridor's second end exist only in the payloads; one waypoint per marker is the accepted limitation of the readable form.

## Format

```
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Maro II"
     xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xmlns:maro="https://maro.ykws.android/marker/1"
     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <wpt lat="<centerPoint.latitude>" lon="<centerPoint.longitude>">
    <name>escaped marker name</name>
    <desc>escaped description</desc>                     <!-- omitted when blank -->
    <extensions>
      <maro:markerData>BASE64 PROTOBUF</maro:markerData>
      <maro:markerJson><![CDATA[{ ...every field... }]]></maro:markerJson>
    </extensions>
  </wpt>
</gpx>
```

**The JSON mirror is the repository's own encoding**, read through one shared home so the file's two copies cannot drift: the exporter, the importer and `UserMarkerRepository` all read one `MarkerJson` carrying the repository's `Json` config. Fields mirrored name-for-name: `id`, `name`, `geometry` (sealed — its `type` discriminator plus `position`, or `center` + `radiusM`, or `p1` + `p2` + `widthM`), `description`, `proximityOverrideM`, `confirmed`, `colorIndex`, `icon`, `createdAtEpochMs`, `origin`, `trackId`, `keepable`, `pinned`, `updatedAtEpochMs`, `routingCost`, `routeOrigin`, `routeDestination`, `routingOnly`.

**Namespace** — a marker namespace of its own, `https://maro.ykws.android/marker/1`, never the track string, so a marker file and a trip file can be told apart by their own element names.

**CDATA guard** — the JSON is emitted inside CDATA, and a `]]>` occurring inside free text (a description, a name) is split so it cannot close the section early.

## Behaviour mirrored from the track transfer

- The busy banner while a transfer runs, and a result toast with imported and ignored counts plus a failure face.
- `ImportMode` (Skip / Update / New) — the enum moves to `data/model/ImportMode.kt` so the marker layer does not reach into `data/track` for a three-value policy.
- The ZIP path keeps the path-traversal guard (`..`, a leading `/`) and skips entries that are not `.gpx`.
- Per-base-name counter inside a ZIP, since two markers may share a name; file base name `yyyy_MM_dd_HH_mm-<sanitized name>` built from `createdAtEpochMs` through the shared `sanitizeFileName`.

## Files

| File | Change |
|---|---|
| `data/markers/MarkerJson.kt` (new) | The one `Json` home and the payload pair — base64 blob + pretty JSON — read by the marker exporter, the marker importer and the track exporter |
| `data/model/ImportMode.kt` (new) | `ImportMode` moved out of `GpxImporter.kt`, its track references updated |
| `data/markers/MarkerGpxExporter.kt` (new) | `fun UserMarker.toMarkerGpx()`, reusing `escapeXml` |
| `data/markers/MarkerGpxImporter.kt` (new) | Single file and ZIP dispatch, the traversal guard, identity by blob → mirror → name, a foreign `wpt` read as a Pin |
| `ui/map/MarkerSharing.kt` (new) | `shareMarkerGpx`, `shareMarkersZip`, file naming |
| `ui/map/MarkersViewModel.kt` | `importMarkers`, `peekImportMatch`; every save goes through `UserMarkerRepository.add` / `update` so the write mutex and the `markerChanges` channel refresh the list and the map |
| `data/model/ListAction.kt` | `ExportMarkerGpx`, `BatchExportMarkerGpx`, `ImportMarkers` |
| `ui/map/MarkerManagementOverlay.kt` · `MarkerCardContent` | The card share action and the export spec in `markerMultiActions` |
| `ui/map/MapScreen.kt` | The file-picker launcher, the pending-conflict state, a `MarkerImportConflictHost` sheet beside `MapImportConflictHost`, the result banner, the action arms |
| `ui/map/MapScreenSettingsOverlay.kt` · `OverlayLayer.kt` | The Markers transfer card and its wiring |
| `res/xml/provider_paths.xml` | A second `files-path` entry for the marker share folder |
| `res/values/strings.xml` · `values-fr/strings.xml` | Busy banner, conflict sheet, result plurals, settings description — both locales |
| `docs/maro-code.md` · the two feature files | The code map and the feature records |

## Tests

- Export then import returns an identical marker (blob path), geometry included.
- With the blob stripped, the mirror alone rebuilds the marker.
- Identity by blob id, by mirror id, by name.
- A foreign `<wpt>` imports as a Pin.
- A ZIP skips an existing id and imports the rest; a ZIP entry named `../x.gpx` is refused.
- A dangling `trackId` survives the round trip unchanged.
- A file carrying a `<trk>` is refused by the marker importer.

## Verification

`gradlew :app:assembleDebug` and `apk-build.bat` green, the marker suites green; the device pass — export a marker, open the file in a text editor to read the mirror, then re-import — stays owed to the user.

## Open points

- **Export all's own set** — every marker, or the listed (filtered) set: pinned at implementation against the source the track transfer card itself reads, so the two cards mean the same thing by "all".
- **The banner slot** — `MapScreenChrome.trackOpStatus` would carry marker text; the wiring pass settles whether it is renamed to a neutral name or the marker side takes a second slot.
