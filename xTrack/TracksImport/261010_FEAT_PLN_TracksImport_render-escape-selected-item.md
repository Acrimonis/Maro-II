<!-- scope: feature -->
# The render escape — the open card's item drawn past the filter and past the cap

- **Feature:** TracksImport (the policy files are this feature's; the rule is Ui_General's, the marker
  side UI_Map's — see §9)
- **Date:** 2026-10-10
- **Status:** SHIPPED 2026-10-10 — see §7 for the outcome
- **Type:** user order reversing, in part, the 2026-09-28 tightening chosen from
  `260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md`

## 1. The order

The selected item is drawn **past the map filter and past the render cap while its card stands** — the
selected marker, track or route alike — and the **camera brings it into frame while that card stands**.
The earlier form of this task named no camera move and was denied for it; the frame is part of the order.

## 2. The reversal

**On 2026-09-28** the map was tightened to draw *its filter's set and nothing else*, and three escapes
were deleted: the marker reveal-on-select, the highlighted track's eligibility override in
`TrackSelectionPolicy`, and the pinned carve-out's repeated OR in `storedTrackSelection`.

**The user's word (2026-10-10) re-instates the first two in a narrowed shape** — one item, and only
while its card stands — while the session boost and the pinned carve-out stay filter-bound. This plan is
the record; the 2026-09-21 plan and the Ui_General `## Rules` and 2026-09-28 `## Implemented` note carry
the forward pointer.

## 3. The escape, per kind — exactly one id

- **One item** — the id the open card holds — and **only while that card stands**: closing it, or the
  card's own dismissal, returns the map to the filter's set with nothing else changed.
- **Past the filter and past the cap**, for its kind. It is a rendering escape, not a filter edit:
  nothing is written to the filter, and the filter's own list and badge semantics do not change.
- **Never a set**: no session boost, no pinned carve-out, no "everything I touched" — one id.

**Tracks (unpinned).** `TrackSelectionPolicy.select` builds `eligible` from
`!pinned && (isHighlighted(id) || matchesFilter(...))`, so the highlighted id is again an **eligibility**
term; the ranked list still puts it first, and after `take(cap)` the escape carries it on top of the
capped set when the cap excludes it (a zero cap with a card open still draws the one item). The session
boost keeps **exactly** what it had — the rank term alone, still filter-bound.

**Routes** ride the same id space (`highlightedTrackId` is the open card's id for both halves), so the
escape needs no second term: the route half is the same `TrackSelectionPolicy` call with the route filter
and the route count.

**Pinned items.** A pinned summary is drawn only when its kind's filter holds it, and `TrackSelectionPolicy`
deliberately excludes pinned items from its ranked path — so the same one-id escape is applied to
`storedTrackSelection`'s pinned filter: `pinned && layerVisible && (isHighlighted(id) || matchesFilter(...))`.
Because the ranked path excludes pinned items and the pinned loop excludes unpinned, **whichever loop
draws the selected item draws it once and never twice**. The pinned carve-out (the pin's escape from the
route count) itself stays filter-bound and layer-bound, exactly as it was.

**Markers.** `MarkerSelectionPolicy` had **no selection term at all**; it now reads
`focus.isHighlighted(id) || matchesFilter(filter)`, the shape the track half uses. The selected id is
threaded from the marker card's own source of truth — `MarkersViewModel._selectedMarkerId` — by
`combine(settings, _allMarkers, _selectedMarkerId)`, which re-runs the map projection whenever the
selection changes. There is no cap on this path, so only the filter half needed the escape. No second
source of truth for the selected id was invented.

## 4. The camera — the existing framer, no second path

Selection framing already existed and is untouched; the escape rides it:

- **Markers:** the `MapScreen` marker-framing effect keyed on `markersViewModel.selectedMarkerId` and the
  drawer state, which resolves the marker in `allMarkers` (unfiltered), so a marker the map filter
  excludes still frames; it calls the one `frameMarker` (`markerFocusTarget` + `controller.animateTo`) and
  enforces **once per selection** through `lastFramedMarkerId`.
- **Tracks:** `openSelectedTrack`'s `trackNavigateState` fold for list taps, menu chevrons and walk steps,
  consumed by the `LaunchedEffect(trackNavigateState)` that does the zoom-to-fit.

Both are the very entry points the inspect mode's recentre reads (`frameMarker` for a marker,
`zoomToBoundingBox` for a track), so this hop added **no** camera path. The frame stays a one-shot on the
selection: a card that stands while the user pans does not pull the camera back, and the existing
user-interaction rules (`mapWasInteracted` and the inspect gates) are what carry that.

## 5. Edit set

- `data/model/MapSelectionPolicy.kt` — `TrackSelectionPolicy.select` re-admits `focus.isHighlighted` as an
  eligibility term and carries the selected id past the cap; `MarkerSelectionPolicy.select` gains the
  selection term; both KDoc rewritten to the reversal.
- `ui/map/MapTrackOverlayEffects.kt` — `storedTrackSelection`'s pinned filter gains the one-id
  `focus.isHighlighted` escape; KDoc updated. The two loops' membership rule (ranked excludes pinned,
  pinned excludes unpinned) is untouched, so the item is drawn exactly once.
- `ui/map/MarkersViewModel.kt` — the map-referential stream combines `_selectedMarkerId` and writes
  `markerMapFocus.highlight(selectedId)` before selecting; KDoc updated.
- `data/model/MapRenderFocus.kt` — KDoc updated to the narrowed escape.

## 6. Tests

`MapSelectionPolicyTest` — the two 2026-09-28 boost cases stay as they are; the escape gets its own:

- `selectedTrack_excludedByTheFilter_isStillDrawn` (replaces the 2026-09-28 claim that it is not);
- `selectedTrack_survivesTheCap_evenAtZero` (a zero cap draws nothing with no card, the one item with one);
- `unselectedFilterExcludedTrack_isNotDrawn` (the escape is one id, never a set);
- `selectedMarker_excludedByTheFilter_isStillDrawn` (the marker half's one case).

Unchanged and still asserted: the boost dropped by the filter but still defeating the cap, the reset
case, the pinned exclusion, the live date exemption, `markers_filterOnly_noCap`, the resume twin ordering.
`TrackRouteRoleTest`'s `storedTrackSelection` cases all pass a `MapRenderFocus()` with no highlight, so
the new pinned term is inert for them.

## 7. Outcome — shipped 2026-10-10

One `#implement` pass, Code → Ask → Architect. The escape is one id in three shapes: the highlighted id
re-admitted to `TrackSelectionPolicy`'s eligibility and carried past the cap, the same id re-admitted to
`storedTrackSelection`'s pinned filter (drawn by the pinned loop alone — the ranked path excludes pinned
items, so never twice), and the marker half's new selection term fed from `MarkersViewModel`'s own
`selectedMarkerId`. The camera needed no new path: the escape rides the marker-framing effect (unfiltered
`allMarkers`) and the `openSelectedTrack` zoom-to-fit — the same framers the inspect recentre reads — each
a one-shot per selection. `apk-build.bat` BUILD SUCCESSFUL and the scoped
`MapSelectionPolicyTest` + `TrackRouteRoleTest` + `ui.map.*` + `config.*` run green apart from **two
pre-existing reds in `MapPulseDotTest`**, which are the working tree's in-flight pulse-dot retune
(`colors.properties` now ships `ui.map.pulse.dot.floor=0.25`/`ms=666` against the test's `0.33`/`555` and
`AppConfig`'s unchanged defaults) and are untouched by this change; no dependency, no git write, no
device touch.

## 8. Non-goals

- The rank term, the cap value, the pinned loop's own filter rule for every pinned item, the list filters,
  the sort and the badge all stay as they are.
- The session boost is not widened: it stays a rank term, filter-bound.
- No filter edit and no second framer. The device pass stays owed to the user.

## 9. Ownership

Filed under **TracksImport** because the two policy files the behaviour lives in are this feature's. The
rule that names the behaviour lives in `xTrack/Ui_General/FEAT_DSC_Ui_General.md` `## Rules` (Ui_General's
own), and the marker half's wiring is UI_Map's (`MarkersViewModel`); each carries its own pointer to this
plan rather than restating it.
