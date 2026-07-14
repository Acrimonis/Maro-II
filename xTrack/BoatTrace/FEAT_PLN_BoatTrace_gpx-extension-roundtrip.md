# BoatTrace — GPX Extension Round-Trip Export/Import

> **Feature:** BoatTrace | **Subfeature:** more-stuff
> **Branch:** feature/track-save-finalization
> **Status:** discussion

## Requirements

1. **Standards-interoperable:** Export must remain valid GPX 1.1, readable by QGIS / Google Earth / OsmAnd
2. **Full MaroII round-trip:** All track metadata + BoatMarkers must survive export→import losslessly

## Current State

[`GpxExporter.kt`](../../app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt) exports only standard GPX 1.1 elements:
- `<name>`, `<cmt>`, `<trkseg>` with `<trkpt>` (lat, lon, speed, course, time)
- **Discarded:** pinned status, track color, idle/navigating durations, distance, all `boatMarkers` with `MarkerSnapshot` data
- **No import path exists**

## Approach: GPX `<extensions>` with Protobuf Blob

GPX 1.1 defines `<extensions>` for arbitrary XML namespaces. Standard tools ignore unknown extensions.

**Export format:**
```xml
<gpx version="1.1" creator="Maro II"
     xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:maro="https://maro.ykws.android/track/1">
  <trk>
    <name>Port Salis → Cap d'Antibes</name>
    <trkseg>
      <trkpt lat="43.55" lon="7.02">...</trkpt>
    </trkseg>
    <extensions>
      <maro:data>[base64-encoded protobuf of full Track]</maro:data>
    </extensions>
  </trk>
</gpx>
```

The `<maro:data>` element contains a **base64-encoded protobuf blob** of the full [`Track`](app/src/main/java/ykws/android/maro/data/track/Track.kt) — all 17 fields including `boatMarkers`.

**Why a blob rather than individual XML elements:**
- Protobuf schema is already the source of truth — no XML schema drift
- Adding/removing Track fields → blob auto-updates with protobuf evolution
- `List<BoatMarker>` with nested `MarkerSnapshot` is deeply nested — XML encoding would be verbose and error-prone
- Blob is opaque to other tools, trivial for MaroII decode

**Import logic:**
1. Parse GPX XML
2. If `<maro:data>` extension present → base64-decode → `ProtoBuf.decodeFromByteArray<Track>(...)` → **lossless round-trip**
3. If absent (foreign GPX) → parse standard `<trkpt>` elements → new Track with defaults (unpinned, amber, no markers, generated ID) — **lossy but functional**

**File size:** Typical 500-pt track with 5 BoatMarkers: ~15 KB protobuf → ~20 KB base64. GPX body ~50 KB. Total ~70 KB. Negligible.

## Tradeoffs

| | Extension Blob | Sidecar .maro | Dual Export Buttons |
|---|---|---|---|
| Single file | ✅ | ❌ | ✅ |
| Standards-compatible | ✅ | N/A | ✅ |
| Data fidelity | ✅ | ✅ | ✅ |
| Import simplicity | ✅ | ✅ | ✅ |
| User confusion | Low | High | Medium |
| GPX file overhead | ~1–5 KB base64 | 0 | 0 |

## Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 2 | **Name-based anti-collision** on import | If a track with the exact same name already exists, propose a suffixed name (e.g. `"Port Salis → Cap d'Antibes (2)"`). Not ID-based — names are what the user sees. |
| 3 | **No standards-only toggle** | The MaroII blob is always included. No user-facing option to omit it. |

## Implementation Scope

### Export

**Single track:** [`GpxExporter.kt`](app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt)
- Add `xmlns:maro` namespace declaration
- After `</trkseg>`, append `<extensions><maro:data>[base64]</maro:data></extensions>`
- Base64-encode: `Track → ProtoBuf.encodeToByteArray → Base64.encodeToString`

**Multi-select export (new):**
- User selects multiple tracks in [`TrackHistoryOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) via multi-select mode
- Export produces a **ZIP archive** containing one `.gpx` file per track (each with the MaroII blob)
- ZIP filename: `maro-tracks-YYYY-MM-DD-HHmm.zip`
- Individual GPX filenames inside archive: `[track name].gpx` (sanitized for filesystem)

### Import (New: `GpxImporter.kt`)

**Dispatch:**
- If input is a single `.gpx` file → import one track
- If input is a `.zip` archive → extract all `.gpx` files, import each

**Per-track import logic:**
1. Parse GPX XML via `XmlPullParser`
2. Extract `<maro:data>` if present → base64-decode → `ProtoBuf.decodeFromByteArray<Track>(...)` → **lossless round-trip**
3. If absent (foreign GPX) → parse standard `<trkpt>` elements → new Track with defaults (unpinned, amber, no markers, generated UUID) — **lossy but functional**
4. **Anti-collision check:** if a track with the same `name` already exists in the repository, append `" (2)"`, `" (3)"`, etc. until unique
5. Save via `TrackRepository.saveTrack()`

### UI Integration

**Export (multi-select):**
- [`TrackHistoryOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) gains multi-select mode (checkbox per track card)
- Action bar with "Export selected (N)" button → produces ZIP → Android share sheet

**Import:**
- Import button as an action in the track list header bar (e.g. download/import icon)
- Triggers Android file picker (`.gpx` + `.zip` MIME filter)
- Progress indicator during import (especially for multi-track archives)
- Result summary: "Imported 3 tracks" or "Import failed: …"
