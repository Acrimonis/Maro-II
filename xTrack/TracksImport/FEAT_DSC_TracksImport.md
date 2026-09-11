---
name: TracksImport
status: active
created: 2026-09-11 20:28
modified: 2026-09-11 20:28
---

# Feature: TracksImport

**Description:**
Map track visibility is derived rather than persisted: the `visibleOnMap` flag is dropped from `Track`/`TrackSummary` and replaced by a shared, unit-testable selection policy (`MapSelectionPolicy` — tracks ranked + capped, markers filter-only) driven by `MapRenderFocus` (highlight + session boost). The GAP-split polyline rendering is extracted into `MapTrackSegments`, and an on-demand GPX off-route cleanup harness ships with four test classes. Landed via the Mergitur three-branch integration.

## Sections

## Todos
- [ ] Define follow-up scope for the derived-visibility model (if any)

## Rules
- (none yet)

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt` — shared selection policy (tracks ranked + capped, markers filter-only)
- `app/src/main/java/ykws/android/maro/data/model/MapRenderFocus.kt` — ephemeral highlight + session boost
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackSegments.kt` — GAP split + osmdroid overlay build

## Docs
- `260911_FEAT_PLN_TracksImport_map-render-visibility-refactor.md` — feature plan
