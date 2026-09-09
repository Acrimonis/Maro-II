<!-- scope: feature -->

# Filters link — decouple List referential from Map referential

## Context

Today there is ONE persisted filter per type (`trackListFilter`, `markerListFilter`) shared by every
surface. Track filter axes: Date Range + Pinned. Marker filter axes: Icon + Pinned + Origin (see
[`ListFilter.kt`](app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:118)). That single filter
drives BOTH the lists and the map overlays, so the map is a mirror of the list: map markers are drawn
from the filtered marker list ([`MarkersViewModel.kt:408`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:408)) and map track history is date/pinned
filtered ([`MapScreen.kt:1224`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1224)). The same
filter UI component is shown in the menu sections
([`MenuDrawerOverlay.kt:222`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:222), `:413`)
and in the list headers ([`ListOverlayScaffold.kt:628`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:628)).

## Goal

Decouple what is filtered in the LIST from what is filtered on the MAP, behind a per-type link toggle.
Two referentials, List and Map; each keeps its own filter state. A link flag decides whether editing one
also edits the other. The same filter component with the same axis options appears in the menu and in the
lists — only the bound referential differs.

## Locked design model (all decisions resolved)

### Referentials and state
- Two referentials per type (Tracks, Markers): **List** and **Map**.
- Per type: `listFilter`, `mapFilter`, `linked` flag — default **linked ON**.
- One shared filter UI component with identical axes in both places:
  - Tracks: Date Range + Pinned.
  - Markers: Icon + Pinned + Origin.
- The **menu** filter control binds to the **Map** referential; the **list header** filter control binds
  to the **List** referential. Same component, different bound state — no duplicated control.

### Link toggle
- Material Symbols `link` (joined chain) / `link_off` (broken chain) as standalone `ImageVector`s
  `Link` and `LinkOff` ([`Link.kt`](app/src/main/java/ykws/android/maro/ui/icons/Link.kt:13),
  [`linkOff.kt`](app/src/main/java/ykws/android/maro/ui/icons/linkOff.kt:13)).
- Toggle shown **in the menu only**, next to each section (Tracks, Markers).
- **Linked (default):** editing/resetting a filter in either place acts on BOTH referentials; counters
  equal while no rendering constraint applies.
- **Unlinked:** editing/resetting acts only on its own referential; counters follow their own referential.
- Unlinking never erases values; values diverge only when one side is edited.
- Re-linking is a PURE FLIP: it only turns the link back ON and never copies either filter to the other.
  If the two referentials had diverged, they keep their values until the next edit while linked, and that
  next edit is written to both (equalizing them). Confirmed rule (Ask review, 2026-09-09).
- The link toggle is reachable in the menu and in each list header (list headers added in Phase 3 of the
  list-icons plan).

### Rendering and counters
- **Map rendering is conditional on the Map filter** for tracks AND markers. Pinned tracks are NOT
  always-on-the-map — they render only if they match the Map filter.
- One shared resolve-map-tracks function answers "which stored tracks to draw given the Map filter" and is
  used by the history, pinned, and highlighted paths so they cannot fall out of sync. The live recording
  line is OUTSIDE that function: it is not a stored track, it is always drawn.
- **Menu counter** = number of items that would be rendered on the map under the Map filter. It ignores
  whether the master layer toggle is on or off (it counts items to be rendered). Divergence from the list
  counter is acceptable when matches exceed the drawing cap, because the list shows every match while the
  map draws up to the cap.
- **List header counter** = number of items in that list under the List filter.
- Live recording track: always drawn, never filterable, excluded from both counters.

### Selection, navigation, reveal
- A detail panel's Prev/Next and its own numbers belong to the world that opened it: List world when
  opened from a list, Map world when opened by tapping the map.
- Map-tap Next/Prev **stops at the edges** (clamps); it does not wrap.
- Reveal-on-select: an item opened from a List that the Map filter would not show is force-drawn on the
  map ONLY while its detail panel is open, then hidden. Map effective set = Map-filter matches ∪ active
  item. The revealed item is never counted anywhere; it never coexists with the menu counter because
  opening the menu closes the dash.
- whereAmI matches override the Map filter (matching uses unfiltered `allMarkers`).

### MarkersViewModel streams
- Two prepared streams over the single unfiltered `allMarkers`: one filtered for the List (drives the
  marker list), one filtered for the Map (drives the map overlay). A detail panel opened from the list
  walks the List stream; opened from the map walks the Map stream.

### Deletion flow (reviewed)
- Marker deletion already advances within the panel's own selection set; keep it, but ensure the selection
  is populated from the correct world when the panel opens.
- Track deletion currently advances over ALL saved tracks; change it to advance within the opening world
  (map world when opened from the map, list world when opened from a list), matching its Next/Prev.
- Undo-tray (soft delete + snackbar undo) and permanent delete paths are unchanged.
- A filter can never change while a dash is open (editing a filter requires the menu or the list, both of
  which close the dash), so no filter-driven ghost auto-close is needed; deletion closes or advances the
  panel as above.

### Persistence / defaults (Option B — no versioning)
- The prefs version/migration mechanism is removed. New keys read with defaults: Map filter = All (empty),
  link = ON. Values the current code understands are honored as-is; values in outdated formats are ignored
  by the tolerant parsers and behave like defaults. Defunct legacy keys are removed idempotently on start.
- First run after upgrade: the List keeps its saved filter; the new Map filter is All until edited.

## Implementation steps

1. Add the chain toggle icons. `Link` (`Link.kt`) and `LinkOff` (`linkOff.kt`) already exist under
   `ui/icons/` with the correct package — verify the glyphs render as an unbroken and a broken chain.
2. AppSettings: add `trackMapFilter`, `markerMapFilter`, `trackFilterLinked`, `markerFilterLinked`; a
   link-aware write helper (linked → write both, unlinked → write only the edited referential); read the new
   keys with defaults (Map filter = All, linked = ON). No versioning: remove the prefs version/migration
   block and constants; new keys simply default when absent.
3. Bind the menu filter controls to the Map referential and the list-header controls to the List
   referential using the same shared component; add the link/link_off toggle in the menu (Tracks +
   Markers sections).
4. Add the single resolve-map-tracks function and point the history/pinned/highlighted track rendering at
   the Map filter through it; leave the live recording line always drawn.
5. MarkersViewModel: expose `listMarkers` (List filter) and `mapMarkers` (Map filter) streams over
   `allMarkers`; bind the marker list to the List stream and the map overlay to the Map stream.
6. Wire detail-panel navigation per opening world; map-tap Next/Prev clamps at edges; populate marker
   drawer selection from the correct world at open; unify track delete-advance to the opening world.
7. Implement reveal-on-select: map draws Map-filter matches plus the active dash item while its panel is
   open.
8. Per-referential counters: menu = map items to be rendered (independent of master toggle, cap allowed to
   diverge); list header = list content; live excluded.
9. Keep deletion undo/permanent-delete flows; verify auto-close only reacts to the opening world's filter
   defensively.
10. Build via `apk-build.bat` and run the verification scenarios.

## Files Affected (indicative)

- `app/src/main/java/ykws/android/maro/ui/icons/Link.kt`, `linkOff.kt` (standalone icons, done)
- `app/src/main/java/ykws/android/maro/data/settings/AppSettings` (+ link-aware write helper)
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` (Map-referential binding; link toggle)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (resolve-map-tracks; counters; reveal; track
  delete-advance; map-tap nav clamp)
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` (list/map streams; selection per world;
  drawer nav clamp for map source)
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`, `TrackHistoryOverlay.kt`,
  `MarkerManagementOverlay.kt`, `ListOverlayScaffold.kt` (List-referential binding; list counter)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` / track drawer nav (Prev/Next universe)
- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt` (unchanged; consumers pick the referential)

## Ask-review notes (locked for implementation)

1. **Menu counter vs per-item visibility:** exclude tracks hidden individually (`visibleOnMap=false`) from
   the menu count even when they match the Map filter; the master layer toggle does NOT zero the count.
   Marker menu counter likewise ignores the marker layer master toggle.
2. **Split filter handlers per referential:** the single `onTrackFilterChange` / `onMarkerFilterChange`
   handlers must become separate list vs map handlers. Unlinked map edits must NOT call
   `trackViewModel.refreshSummaries(...)` (list rebuild) or the marker list stream; unlinked list edits
   must not touch the map stream/redraw.
3. **MarkersViewModel:** keep `allMarkers` as the single refresh target; derive `listMarkers` and
   `mapMarkers` from ONE settings observer so reload/delete/create handlers never re-apply a filter by
   hand (currently ~10 sites bake `markerListFilter`).
4. **Map tap source:** add a `DrawerSource.MAP` that clamps at edges; map taps at `MapScreen.kt:1968` /
   `:2116` must use it instead of the default `WHERE_AM_I` wrap.
5. **Reveal survives redraws:** the active item (selected marker id / highlighted track id) is part of the
   map draw set and must be a render key, so reveal-on-select is not dropped by the next map redraw.
6. **No migration (Option B):** the prefs versioning block and version constants are removed from
   SettingsManager; new map-filter keys default to All and link defaults ON; defunct legacy keys are
   cleaned up idempotently in the SettingsManager init.

## Verification

- Linked default: editing filter/reset in menu or a list updates both; counters match (cap divergence
  accepted); upgrade shows no change.
- Unlinked: menu edits change only the map; list edits change only that list; counters follow each
  referential; unlinking never resets values.
- Pinned track renders on the map only when it matches the Map filter.
- Opening a list item the Map filter excludes shows it on the map only while its panel is open; it is not
  counted; opening the menu closes the panel.
- Map-tap Next/Prev stops at the edges; list-opened Next/Prev clamps at list edges.
- Deleting a viewed track advances within the opening world; deleting a viewed marker advances within its
  selection; undo restores; permanent delete removes.
- whereAmI reveals markers regardless of the Map filter.

## Out of scope

- Sorting (sort remains list-side only).
- Regulated-zone / layer toggles and other visibility controls beyond the marker/track filters.
- The boat-state HUD DashboardPanel (independent).

## ELIJP

Two services that today read one shared config row are split into two rows with a link flag: flag on =
writing one writes both; flag off = each reads only its own. The same dropdown is reused everywhere but
bound to its own world. Counts show what each world really displays. Picking something from a list that
the map would hide shows that one item only while you look at it, then hides it again.

## Branch note

Branch `feature/filters-link` created from `origin/develop`. This plan doc is present as an untracked file
on that branch. Implementation begins only with explicit go-ahead (e.g. `#implement`).
