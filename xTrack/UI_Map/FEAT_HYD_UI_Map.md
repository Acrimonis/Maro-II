# Context Hydration — UI_Map — 2026-10-07

**Last Bake:** 2026-10-07 21:15 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session's edits all ran on the user's own device-read orders — the inspect flow, then the dash family — and its build and scoped test runs are named below; no claim below rests on anything but a file read or a command this session.

## State

Inspect mode's live acquire shipped and was then driven through a sequence of device readings, each fixed in a
build of its own: the nearest item now opens its dashboard at once with no lift and no dwell, a change of
nearest swaps the panel, the anchor losing its target closes it while the mode stays armed, the quiet's expiry
recentres on the acquired item and freezes the ladder the walk steps, a drag after that recentre resets the
card, the ladder and the captured frame, and the armed map-marker tap went with `MarkerOverlay`'s proximity
receiver. The same session then normalised the selected-item slot's whole family — the marker card, the track
card and the route panel — onto one header padding, one scrollable body, one body-only dissolve, square
corners in both orientations, and one walk-row frame: 8 dp above, 8 dp between, 8 dp below, all carried in the
row's own `padding(horizontal = 12.dp, vertical = 8.dp)`, wearing the route footer's own tier-1
`ConfirmActionButton` pair rather than hand-rolled pills. That swap also gained a distance margin
(`map.inspect.switchMarginPct`), the selected path a gold casing on its own `path.line.casing.selected` colour
and `.width` keys, and `DrawerScaffold` an `initialHeight` seed — the outgoing card's measured height — so an
incoming card is pre-sized at the size already on screen and settles once.

The closed walk of 2026-09-17 was folded: its eight points are resolved into
`260917_FEAT_PLN_UI_Map_inspect-mode.md`, which the feature's `## Implemented` already points at. The
feature's four older open todos stand unchanged.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt` — the sweep, the quiet clock, the recentre and the pure rules
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the inspect state machine and the `DashboardBandState` seed
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`, `OverlayLayer.kt`, `RouteConfirmPanel.kt` — the three panels of the selected-item slot
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — the wrap frame's `initialHeight` seed and its pre-measured frame
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties` — the dwell, switch-margin and selection-casing keys
- `docs/ui-drawer-guidelines.md` — §6's dash-panel corners, dash-footer buttons, walk-row spacing and swap pre-size
- `xTrack/UI_Map/261007_FEAT_PLN_UI_Map_inspect-live-acquire.md` — the plan of record, its §8 carrying every reading's fix

## Next Step

The device pass the feature's `## Todos` names, on the user's own timing: the live acquire and its exits, and
the dash family's swap settling without a jump.
