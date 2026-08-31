# BoatTrace — Phase 4: Marker export/import + normalized import modes

**Date:** 2026-08-31
**Phase:** 4 — export/import of markers and their track links, with three import modes

## Current state (gap)

- Track export ([`GpxExporter.kt`](app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt:25)) emits standard `<trkpt>` elements **and** a `<maro:data>` blob (full `Track` protobuf). No `UserMarker`s, no `trackId`.
- Track import ([`GpxImporter.kt`](app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt:26)) reads the blob first (lossless), else falls back to `<trkpt>` (lossy); dedupes by **name** only.
- No marker export exists.

## Normalized import behavior — three modes (tracks + markers)

**Principle: identity = `id`; mode decides what happens on a match.**

1. **Skip existing** (default) — id-based dedup; idempotent re-import.
2. **Update existing** — replace the matching entity. Match by `id` (Maro blob) else by `name` (foreign/edited). **Keep the existing `id`** so `UserMarker.trackId` links survive.
3. **Import as new** — always fresh id + name suffix.

### Update-mode semantics (the "trim points in XML" workflow)

- **Points** — prefer the standard `<trkpt>` elements when present (the human-editable part); fall back to the blob's points.
- **Metadata** (name, comment, color, pinned, `boatMarkers`) — from the blob when present; defaults when foreign.
- **Derived stats** (distance, duration, idle) — recomputed from the final points.
- **Id** — the existing track's id is kept.

This applies identically to markers: update = overwrite the marker with the same id (name/icon/geometry edited externally), keeping `trackId`.

## Evaluation

### 1. Global marker export — recommended
Export all `UserMarker`s (USER + IDLE_AUTO): GPX `<wpt>` per marker (interop) + a Maro `<maro:data>` blob with the full JSON list (lossless, incl. `trackId`).

### 2. Include track markers (IDLE_AUTO) — yes
Include with `trackId`; dangling on import → no badge. Maro track exports preserve `Track.id`, so track+markers exported together resolve cleanly.

### 3. Include the track in marker export — no (reference only)
Keep `trackId` as a reference; do not bundle full tracks (duplication + double-import risk).

## Decisions (resolved)

- **Single `.gpx` import** — offer Skip / Update / New via a dialog (Skip default).
- **Batch `.zip` import** — always "Skip existing" (idempotent), no per-file dialog.
- **Entry points** — share/export button in `MarkerManagementOverlay` + import action in the menu.
- **Format** — GPX `<wpt>` + Maro blob (recommend both).

## Verification

- E2E: export all markers → wipe → import → markers + badges restored.
- E2E: export a track, trim `<trkpt>` in the XML, import with "Update" → same id, trimmed points, recomputed stats, links intact.
- E2E: re-import same file twice with "Skip" → no duplicates.
- E2E: import markers whose tracks are absent → no badge, no crash.
