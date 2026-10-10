---
name: TracksImport
status: active
created: 2026-09-11 20:28
modified: 2026-10-10 07:19
---

# Feature: TracksImport

**Description:**
Map track visibility is derived rather than persisted: the `visibleOnMap` flag is dropped from `Track`/`TrackSummary` and replaced by a shared, unit-testable selection policy (`MapSelectionPolicy` — tracks ranked + capped, markers filter-only plus the open card's one id) driven by `MapRenderFocus` (highlight + session boost). The GAP-split polyline rendering is extracted into `MapTrackSegments`, and an on-demand GPX off-route cleanup harness ships with four test classes. Landed via the Mergitur three-branch integration.

## Sections

## Todos
- [ ] Define follow-up scope for the derived-visibility model (if any)
- [ ] Device pass over the reading-2 change — the three position values with a track open and again after recording one, the badge against the drawn set, and the cold-start 0 (plan §8)
- [ ] Device pass over the render escape — a filter-excluded selected marker and track drawn while their card stands, a pinned selected item drawn once, the camera framing it once per selection, and the item gone on close (plan §7)
- [ ] Decide whether the marker count follows its own layer toggle now that the track count does (plan §5)

## Rules
- (none yet)

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt` — shared selection policy (tracks ranked + capped, markers filter-only, each carrying the open card's one-id render escape)
- `app/src/main/java/ykws/android/maro/data/model/MapRenderFocus.kt` — ephemeral highlight + session boost
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackSegments.kt` — GAP split + osmdroid overlay build

## Docs
- `260911_FEAT_PLN_TracksImport_map-render-visibility-refactor.md` — feature plan (the selection policy, `MapRenderFocus` and D1a)
- `260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md` — the map filter against the render focus: the badge counted the filter alone while the map drew it plus the focus (shipped 2026-09-21)
- `261010_FEAT_PLN_TracksImport_render-escape-selected-item.md` — the open card's one-item render escape past the filter and past the cap (2026-10-10); reverses in part the 2026-09-28 tightening

## Implemented
- **render-escape-selected-item (2026-10-10)** — the open card's item is drawn **past the map filter and past the render cap** again, one id and only while its card stands (reversing 2026-09-28 in part): `TrackSelectionPolicy.select` re-admits `focus.isHighlighted(id)` as an eligibility term (`!pinned && (isHighlighted || matchesFilter)`) and carries that one id past `take(cap)`, the session boost keeping its filter-bound rank term alone; routes ride the same `highlightedTrackId`, so no second term; `storedTrackSelection`'s pinned filter gains the same `focus.isHighlighted` escape so a pinned selected item is drawn — the pinned carve-out itself staying filter- and layer-bound — and since the ranked path excludes pinned items and the pinned loop excludes unpinned, whichever loop draws the selection draws it **once and never twice**; `MarkerSelectionPolicy.select` gains the selection term it never had (`isHighlighted || matchesFilter`), fed from `MarkersViewModel`'s own `_selectedMarkerId` through `combine(settings, _allMarkers, _selectedMarkerId)`. The camera rides the existing selection framers — the marker effect on `selectedMarkerId` (unfiltered `allMarkers` → `frameMarker`) and `openSelectedTrack`'s `trackNavigateState` zoom-to-fit, the same entry points the inspect recentre reads — a one-shot per selection, no new framer. `apk-build.bat` SUCCESSFUL and the scoped `MapSelectionPolicyTest` + `TrackRouteRoleTest` + `ui.map.*` + `config.*` run green bar two pre-existing `MapPulseDotTest` reds from the working tree's in-flight pulse-dot retune; no dependency, no git write, no device touch. Ask hop: no blocker, one Medium (the pinned-loop guardrail read) and two Low (the marker escape across a card-entered edit; the escape is filter/cap-scoped, not layer-scoped) recorded, folded nowhere → `261010_FEAT_PLN_TracksImport_render-escape-selected-item.md`. *Reverses in part 2026-09-28:* the highlighted override and the pinned loop's one-id escape are back in the narrowed shape the user ordered
- **map-filter-vs-render-focus (2026-09-21, `feature/on-water-lannd-filter`)** — the map filter is authoritative again: `TrackSelectionPolicy.select` no longer ORs the session boost past the filter, so only the highlighted track outranks it while the boost keeps the render cap as its one override, the dead `MapRenderFocus.includes` deleted with it, and the menu's track count now reads the painted set the overlay pass publishes rather than re-counting the filter's matches — which is what makes the number and the map one derivation. `apk-build.bat` SUCCESSFUL with the scoped `MapSelectionPolicyTest` + `ui.map.*` + `config.*` run green, the two boost test cases rewritten, the 2026-09-11 plan's §7 and D1a amended beside their clearing rule, and the device pass plus the marker-count question left open → `260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md`. *Superseded in part 2026-09-28:* the highlighted track no longer outranks the filter either — the drawn set is the filter's alone, the highlight a rank term — and the pinned carve-out went with it *Reversed in part 2026-10-10:* the highlight override is back, narrowed to the open card's one id
