# Markers — marker export/import (GPX transfer)

**Date:** 2026-10-11
**Status:** shipped 2026-10-11. Branch `feature/mrkr-imexport`, cut from `origin/develop` by the user's `#new`; the transfer is built in the working tree, its result banner naming its kind and the four share-sheet titles moved into both locales in the same pass.
**Supersedes:** the marker half of `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-export-import.md` — the format, the three import modes and the identity rule are carried forward here, its export-all evaluation is reversed.
**Reviewed:** 2026-10-11 — the findings below are folded in: the symmetric doors, the flags a foreign waypoint is born with, the blank-name rule, the name-identity collision rule, the track-less auto marker, the mirror's token form and the share space's growth.

## Decisions (locked with the user, 2026-10-11)

- **Format** — GPX 1.1, one file per marker: a `<wpt>` at the marker's centre plus two payloads in `<extensions>` — `<maro:markerData>` (base64 protobuf of the whole `UserMarker`, the lossless path) and `<maro:markerJson>` (CDATA, the same object in pretty JSON, mirroring every field name-for-name so a human or a foreign tool reads all of them).
- **Batch** — a ZIP of per-marker `.gpx` files, shared through the same `ACTION_SEND` chooser the track batch uses.
- **The exported set** — the markers backup takes every user marker and every marker that owns no track: `origin == USER || trackId == null`. An auto marker that carries a `trackId` rides that track's own file — see `xTrack/Tracks/261011_FEAT_PLN_Tracks_export-auto-markers.md` — while one that carries none would otherwise be stranded, which is exactly the 🕐 marker the UI's own creation path builds; the ruling of 2026-10-11 sends that one here. This **reverses** the old plan's "export all `UserMarker`s (USER + IDLE_AUTO)". A user marker that also carries a `trackId` therefore appears in both files, which identity by id makes harmless; were the two files meant to be exactly disjoint, the rule would narrow to `trackId == null` alone, at the cost of a track-linked marker the user made leaving the backup.
- **Doors** — a share action on the marker card, an export spec in the list's multi-select bar, and a Markers transfer card in Settings carrying Export and Import. The old plan's "import action in the menu" door is **not** taken.
- **Single-file import** — a match on an existing marker asks Duplicate / Override / Cancel; a ZIP import never asks and always skips (idempotent re-import).
- **Identity** — the blob's `id`, else the mirror's `id`, else the `<wpt>`'s `name`; a foreign waypoint is read as a Pin.
- **`trackId`** — kept verbatim, never rewritten. A dangling id is expected on another device and already harmless: the card reads `marker.trackId?.let(trackTitleLookup)` and shows nothing when the track is absent.
- **Geometry as a foreign consumer sees it** — the `<wpt>` carries the centre alone, so a Circle's radius and a Corridor's second end exist only in the payloads; one waypoint per marker is the accepted limitation of the readable form.
- **A plain waypoint is born a keeper** — a marker read from a `<wpt>` with no Maro payload arrives `confirmed = true`, `keepable = true`, `origin = USER`; it must never land as an auto marker, because the startup cleanup at [`MapMarkerEffects`](../../app/src/main/java/ykws/android/maro/ui/map/MapMarkerEffects.kt:47) deletes `IDLE_AUTO && !keepable` markers.
- **A blank name** — a marker with an empty name exports no `<name>` element, and an import whose payloads carry neither an id nor a name is refused as invalid rather than guessed at, since the name is the last link of the identity chain.
- **Name identity is first-wins** — a name match resolves to the first marker carrying that name, mirroring the track path's `summaries.associate { it.name to it.id }`; two markers may share a name, so only the id path is a true identity.
- **The doors are symmetric** — a trip file opened at the marker door writes nothing, and a marker file opened at the track door writes nothing either: [`parseStandardGpxData`](../../app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt:258) reads only `<trk>`, the `<name>` inside it and `<trkpt>`, so a `<wpt>`-only file parses to nothing and the track import reports zero imported and zero ignored. The two payloads cannot be confused either, since `<maro:data>` is not a substring of `<maro:markerData>`.

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

`gradlew :app:testDebugUnitTest` and `gradlew :app:assembleDebug` green with the marker suites, then `apk-build.bat`; the device pass — export a marker, open the file in a text editor to read the mirror, then re-import — stays owed to the user.

## Open points

- **Export all's own set** — every marker, or the listed (filtered) set: pinned at implementation against the source the track transfer card itself reads, so the two cards mean the same thing by "all".
- **The banner slot** — `MapScreenChrome.trackOpStatus` would carry marker text; the wiring pass settles whether it is renamed to a neutral name or the marker side takes a second slot.
- **Settled — the track-less auto marker travels in the backup** (2026-10-11): the set is `origin == USER || trackId == null`, so the marker [`addTempAutoMarker`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:1136) builds without a `trackId`, against the recorder's own [`AutoMarkerManager.createTemp`](../../app/src/main/java/ykws/android/maro/data/markers/AutoMarkerManager.kt:32) which stamps one, is stranded no longer. No `keepable` clause is needed and no creation path changes; the first reading of the ruling, that no track-less auto marker should exist at all, would instead harden `addTempAutoMarker` with the recording's id and is recorded in the trip plan as a named finding rather than done here.
- **The mirror's token form** — the geometry block carries kotlinx's polymorphic discriminator and serial names, and their exact form is not pinned anywhere in this repo. Rather than assert a shape, the plan pins it with a round-trip test over a marker the repository itself encoded, so the file's readable copy is defined by the writer rather than by a description of it.
- **The share space accumulates** — each transfer leaves its `.gpx` or `.zip` behind in the marker share folder, exactly as the track path already leaves its own behind. A cleanup rule would belong to both and is deliberately out of this plan.
- **Deliberately not emitted** — no `<type>` and no `<sym>` on the waypoint, so the readable surface stays the name and the description; the geometry lives in the payloads alone.

## Implemented

**Shipped 2026-10-11 on `feature/mrkr-imexport`.** Every decided rule above is in the code, and each carries a test except the three shape changes named below.

- **The codec** — [`MarkerPayload.kt`](../../app/src/main/java/ykws/android/maro/data/markers/MarkerPayload.kt:1): the base64 protobuf blob, the pretty JSON mirror over the repository's own `Json`, `writeCdata` / `readCdata`, and the `elementOf` / `markersIn` readers a trip file shares. Built on the JVM's Base64 because `minSdk` is 26 and the unit-test classpath carries no Android API — without that, none of this could be tested off-device.
- **The writer and the reader** — [`MarkerGpxExporter.kt`](../../app/src/main/java/ykws/android/maro/data/markers/MarkerGpxExporter.kt:1) and [`MarkerGpxImporter.kt`](../../app/src/main/java/ykws/android/maro/data/markers/MarkerGpxImporter.kt:1), with the importer's own attribute-and-children waypoint read in place of the platform parser, for the same reason.
- **The doors** — the card's share action, the list's export spec in `markerMultiActions`, the Settings transfer card inside the markers card of the layers tab, and `MarkerImportConflictHost` for the single-file match.
- **The refresh** — every write goes through `UserMarkerRepository.add` / `update`, so its mutex and its `markerChanges` channel are the whole refresh mechanism, and the layer is raised when something landed.
- **Shape change one** — the busy banner kept the name `trackOpStatus`, its KDoc restated to say it carries either transfer's progress; the rename is mechanical and unmade.
- **Shape change two** — Export all takes `markersViewModel.allMarkers`, every marker held, because that is what a backup means; the list's own door exports the selection instead.
- **Shape change three** — a trip file's markers are filtered to `keepable` at the track door, so the trip plan's "keep a plain waypoint" rule is enforced by never importing an unkeepable one, while the marker door still reads a plain waypoint as a keeper Pin.

**Tests:** [`MarkerGpxTransferTest`](../../app/src/test/java/ykws/android/maro/data/markers/MarkerGpxTransferTest.kt:1) 15/15 — the round trip, the mirror alone with the blob stripped, id and name identity, import-as-new and override, a foreign waypoint born a keeper, a nameless waypoint refused, a trip file refused, an unowned auto marker carried, a dangling `trackId` kept, the ZIP's skip and traversal guard, a `]]>` in the description, a blank name emitting no `<name>`, and the peek. `:app:testDebugUnitTest` and `:app:assembleDebug` BUILD SUCCESSFUL with `apk-build.bat`; the device pass is owed and is the user's.
