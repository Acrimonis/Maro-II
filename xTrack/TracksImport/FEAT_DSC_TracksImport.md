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
- [ ] Device pass over the reading-2 change — the three position values with a track open and again after recording one, the badge against the drawn set, and the cold-start 0 (plan §8)
- [ ] Decide whether the marker count follows its own layer toggle now that the track count does (plan §5)

## Rules
- (none yet)

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt` — shared selection policy (tracks ranked + capped, markers filter-only)
- `app/src/main/java/ykws/android/maro/data/model/MapRenderFocus.kt` — ephemeral highlight + session boost
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackSegments.kt` — GAP split + osmdroid overlay build

## Docs
- `260911_FEAT_PLN_TracksImport_map-render-visibility-refactor.md` — feature plan (the selection policy, `MapRenderFocus` and D1a)
- `260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md` — the map filter against the render focus: the badge counted the filter alone while the map drew it plus the focus (shipped 2026-09-21)

## Implemented
- **map-filter-vs-render-focus (2026-09-21, `feature/on-water-lannd-filter`)** — the map filter is authoritative again: `TrackSelectionPolicy.select` no longer ORs the session boost past the filter, so only the highlighted track outranks it while the boost keeps the render cap as its one override, the dead `MapRenderFocus.includes` deleted with it, and the menu's track count now reads the painted set the overlay pass publishes rather than re-counting the filter's matches — which is what makes the number and the map one derivation. `apk-build.bat` SUCCESSFUL with the scoped `MapSelectionPolicyTest` + `ui.map.*` + `config.*` run green, the two boost test cases rewritten, the 2026-09-11 plan's §7 and D1a amended beside their clearing rule, and the device pass plus the marker-count question left open → `260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md`
