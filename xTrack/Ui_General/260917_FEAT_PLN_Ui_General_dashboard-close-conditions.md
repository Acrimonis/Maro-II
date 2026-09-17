---
feature: Ui_General
topic: Selected-item dashboard close conditions
created: 2026-09-17 13:50 UTC
status: shipped
---

# Plan: Selected-item dashboard — close conditions

Reviewed in Ask mode 2026-09-17; the findings are folded in — the panel-over-drawer ladder, the
one-selected-item guard inside both openers, the marker callbacks in change 5 and the
`ListAction.EditItem` wizard entry in change 3.

Re-assesses `xTrack/UI_Map/260904_FEAT_PLN_UI_Map_marker-filter-map-and-dashboard-close.md` change 2 and
its follow-up `260904_FEAT_PLN_Ui_General_scrim-strengths-and-dashboard-close.md`.

## Goal

One rule set for the selected-item dashboard — the marker detail drawer and the track detail drawer —
replacing the accumulated per-call-site autocloses. A control may close it for exactly two reasons;
everything else leaves it open.

## The two rules

**R1 — the action wants the dashboard's own slot → close.**
Two members only: the marker/track **wizard** (it slides in place of the dashboard, same size, same
position) and the **other selected-item dashboard** (one selected item at a time). Both replace the
dashboard; nothing else does.

**R2 — the action rewrites the scope of the walk → close.**
The Prev/Next walk reads a world: for a marker, the referential of the surface it was opened from
(`DrawerSource.LIST` → the marker list referential, `DrawerSource.MAP` → the marker map referential);
for a track, the track list referential, plus the map referential whenever the link is on. A filter,
sort or reset that rewrites that world closes the dashboard; a change to the other referential is
display-only and leaves it open.

## Panels that keep the selection

The menu, the settings page, the track history and marker management lists are panels **over** the map,
not occupants of the dashboard slot, so the selection survives them and returns when they close. That
survival is what keeps the render chips (Arrows / Colours), the display settings and the list filters
reachable while an item is selected — and it is what makes R2 a live rule rather than a dead guard,
since the list filter can now be edited with a selection still open behind the panel.

Strongest objection: in portrait the menu drawer covers the same bottom region as the selected-item
drawer, so the surviving selection is invisible until the panel closes, and the ladder must render the
panel **above** the drawer or the dashboard would float over the panel's scrim. Device check required.

## Explicitly kept

- **Display-only actions**: the layer fan and its six children, the GPS/demo toggle, zoom, recentre, the
  screen lock, the drawer's own eye and Speed toggles, map gestures.
- **The item's own modals**: resume-confirm sheet, confirm dialogs, icon picker — they belong to the
  surface that opened them, so a modal over the dashboard is not stacking.
- **User-driven closes**: Back, the drawer's own close control, delete-advance (owned by
  `delete-advance-next`, not by this rule).

## Decisions (confirmed 2026-09-17)

- The menu, the settings page and both lists are confirmed as panels over the map: they leave the
  selection open, and it returns when they close.
- Any action that hits neither rule is confirmed as a keep — no third reason may close the dashboard.
- The autoclose set is closed at three members: the wizard, the other selected-item dashboard, and a
  scope change of the walk. The fan needs no change, being already outside it.

## Action table

| Action | Rule | Today | Target |
|---|---|---|---|
| Menu (hamburger) open | keep | closes both | keep the selection |
| Settings, track history, marker management (from the menu) | keep | closes via the menu open | keep the selection |
| Marker / track wizard — Add Zone, create-first, drawer Edit | R1 | closes | keep closing |
| The other selected-item dashboard opens | R1 | marker→track jump closes it; a map marker tap does not | close the other one |
| Layer fan anchor + 6 layer children | keep | already does not close | keep |
| Arrows / Colours chips, display settings | keep | unreachable while selected | reachable, selection survives |
| Marker list filter while opened from the list | R2 | closes only when the item leaves the filter | close on any change |
| Marker list sort, opened from the list | R2 | stays open | close |
| Marker map filter over a map-opened marker | R2 | stays open by design | close |
| Track list filter / sort / reset | R2 | no close, index silently stale | close |
| Track map filter while linked | R2 | no close | close |
| Eye / Speed toggle, GPS toggle, zoom, recentre, lock, map gestures | keep | stays open | keep |
| Back, drawer close, delete-advance | user | closes / advances | unchanged |

## Code changes

1. [`MapScreen.kt:1265`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1265) — drop
   `closeSelectedItemDashboards()` from `onOpenTrackDrawer`: the menu is a panel over the map, so the
   selection survives it. This is the change that answers the re-assessment.
   [`OverlayLayer.kt:273`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:273) then hides the
   marker and track detail slots while `chrome` reports the menu, settings or either list open, so the
   panel wins the region instead of being over-floated by the survivor: the ladder declares the scrim and
   the menu at [`265`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:265) and
   [`324`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:324) *before* the detail slots, so
   without the gate the selection would draw over the panel's scrim. State is untouched, so the dashboard
   returns when the panel closes — chosen over re-ordering the ladder, which would move the wizard too.
2. One selected item at a time — the guard lives inside the two openers, not at one call site:
   [`openTrackDetail`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1604) closes the marker
   drawer, and [`openMarkerDetail`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1650) plus
   [`onMarkerTap`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1249) close the track drawer.
   That covers the menu chevrons
   ([`OverlayLayer.kt:356`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:356)) and the list
   rows ([`MapScreen.kt:1746`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1746),
   [`1841`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1841)), while the belongs-to-track jump
   already closes its own surface at
   [`OverlayLayer.kt:404`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:404),
   [`426`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:426),
   [`450`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:450) and
   [`737`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:737).
3. Wizard closes — four entries, each closing the *other* dashboard:
   [`MapScreen.kt:1246`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1246),
   [`1844`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1844),
   [`1924`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1924) and
   [`MarkerDrawer.kt:237`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:237). The marker half
   needs no close at any of them, because the wizard replaces the Viewing content inside the same
   `MarkerDrawerState`; the track half is why the guard exists.
4. [`MapScreen.kt:1761`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1761),
   [`1766`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1766),
   [`1773`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1773),
   [`1785`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1785),
   [`1794`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1794) — the five track referential
   callbacks close the open track dashboard through one named helper (R2), the two map-referential ones
   only when `trackFilterLinked` moves the list world with them.
5. [`MarkersViewModel.kt:436`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:436) —
   `applyFilterSort` keys its guard on `drawerSource` (list world for `LIST`, map world for `MAP`),
   replacing the comment that declares map-opened views exempt, plus a map-referential entry point called
   from [`MapScreen.kt:1901`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1901) and
   [`1909`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1909). Membership alone cannot fire on
   a sort, so the five marker callbacks —
   [`1879`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1879),
   [`1883`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1883),
   [`1891`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1891),
   [`1901`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1901) and
   [`1909`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1909) — call the close explicitly
   beside the re-keyed guard, mirroring change 4 on the track side.
6. Two named helpers carry the rules — `closeSelectedItemDashboards()` for R1 and one scope entry point
   for R2 — each close site naming its rule in a KDoc line, so a new control is classified rather than
   patched.
7. Docs (applied 2026-09-17): [`FEAT_DSC_Ui_General.md`](xTrack/Ui_General/FEAT_DSC_Ui_General.md:42)
   carries the corrected fan line and the two rules under `## Rules`, both older plans carry a note
   pointing here, and the routing row for this topic landed in
   [`GLOBAL_CONTEXT.md:34`](xTrack/GLOBAL_CONTEXT.md:34) — a cold open no longer lands on
   [`Ui_Dashboard`](xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md:1).

## Review findings (Ask hop, 2026-09-17) — all four closed

Shipped, reviewed, then fixed in a second Code hop: `apk-build.bat` SUCCESS and the scoped run steady at
134 tests. Each finding is kept as it was found, with the fix that closed it.

- F1 — closed: the crosshair Where-Am-I entry and the idle auto-open now run `closeTrackDrawer()` first
  ([`MapScreen.kt:1299`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1299) and
  [`888`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:888)), and the sweep found no third
  `MatchResult` opener — `whereAmI()` holds its only two setters and both callers are these.
- F2 — closed: the guard stays on the live [MarkerOverlay callback](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1526)
  and both dead parameters went, `onMarkerTap` and `onViewTrackList`, neither deletion rippling past
  `MapScreen.kt` since both belong to the private `MapContent` composable.
- F3 — closed: the Back handler and the delete-advance fallback both call
  [`closeTrackDrawer()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:516), which now owns the
  close; as a consequence the fallback restores the pre-navigation camera when `mapWasInteracted` is
  false, where it previously skipped the restore.
- F4 — closed: the drawer Edit closes the track half only at
  [`MapScreen.kt:1734`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1734), the other three
  wizard entries keeping the full entry point; a side effect to know is that the marker being edited keeps
  its selected rendering, `_selectedMarkerId` no longer being cleared on the way into the wizard.
- Cleared: no wizard can be cancelled — `closeMarkerDashboard()` returns unless the drawer is Viewing or
  MatchResult — and the synchronous membership read in `applyScopeGuard()` cannot disagree with the map,
  `MarkerSelectionPolicy.select` being filter-only for markers.

## Open points

- A future entry point to a list owned by a drawer itself (none today) must declare its rule before it
  ships.
- Two `GLOBAL_CONTEXT.md` summary strings are stale on the menu half only (the UI_Map row and the
  Ui_General row): a `#bake` rewrites them with the shipped wording, the fan half staying true, and the
  bake is also what refreshes the feature summary and the hydration — the implemented pointer landed with
  the pipeline.

## The fan report

Not reproducible in code: [`onToggleFan`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1337)
holds no close call, and the 260904 plan's change 2 removed the one that existed. The two stripe icons
sitting in the same right-hand column are the likely source of the report — the menu button
([`HamburgerIcon`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2582), which closes) directly
above the layer-fan anchor ([`ThreeStripeLayerIcon`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2627),
which does not). Device check: tap each of the two with a track open.

## Verify

- Unit: the marker guard is pure and testable — `scopeClosed(source, inListWorld, inMapWorld)`.
- Device, with a dashboard open: menu → the panel covers the region, the selection is hidden and returns
  when the panel closes; fan anchor and each layer child → survives; `+` → the wizard replaces it; map tap
  on a marker with a track open → the track closes; a menu chevron opening the other kind → the first
  closes; list filter or sort change → closes; eye and Speed toggles → survives.
- Build: `apk-build.bat`.
