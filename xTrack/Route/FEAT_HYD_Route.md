# Context Hydration — Route — 2026-09-29

**Last Bake:** 2026-09-29 13:12 UTC — written by `#bake`

**Directive trace:** this session ran the covered action classes on the user's own word — the `#new` that cut the branch, the `#impl` and `#commit` of the marker pass, then the route pass: the discuss gate's "plan, report, discuss", two `#impl` runs, four prose orders on the fan (its order and the loss's mark, the parent's mark, the loss's colour, the parent's colour), and the look at `color-scheme.md` that followed — and none was taken without it. No dependency was added, no machine-shaped data file was opened, the device was never touched and nothing was deployed, so every claim about the code came from a file read or a command's own output. Two gaps are named rather than hidden: a hop changed the fan's parent mark and another edited an epic entry beyond their instructions, the first was undone on the rule that an action taken without the user's word is void, and both were reported.

## State

**The map's route surface changed shape.** The control stack's `cm` square — the Add Zone door markers no longer need — became a **route fan**: a `ControlId.ROUTE_FAN` anchor that **opens the arc and arms nothing**, with five momentary children that close the fan on a press. The arming moved into the arc, so the mode is entered from the map by a child rather than by the square itself.

**The arc's order is the screen's, not the list's** — the distinction that cost two passes. It reads, top to bottom, `bolt` (`Route (auto)`) · `route` (`Route`) · `save` (`Save to track`) · `logout` (`Save to track and exit`) · `cancel` (`Discard, unasked`), so the **list runs bottom-to-top** (`FanDirection.LEFT` fixes `baseAngleDeg` at 270°, putting index 0 at the arc's bottom, 198°, and index 4 at its top, 342°). The first pass wrote the list in reading order and drew the arc mirrored; §3 of the plan carries the mapping so it is never re-derived.

**The gating is one derivation, and each cell reads the flag its own door reads**: the two arming children on `!routeArmed`, `Save to track` where a line stands unwritten, `Save to track and exit` on `FOLLOWING` and unwritten, `Discard` on `routeArmed`. An Ask finding was folded before the pointer landed — the arming cells and `Discard` had keyed on `RouteState.phase`, which disagrees with `routeArmed` after process death and on the arming frame, drawing live cells that did nothing and a dead `Discard`.

**Two capabilities the framework owed, both defaulted so nothing else moved**: `MapControlButton(enabled)` pins Material's disabled container and content back to its own tokens, and `FanLayout(enabledStates)` suppresses a child's press. The fan passes no `activeStates`, so no glyph inherits the toggle's 0.25 face; a disabled child wears the fan's own inactive alpha, which is the convention the user kept.

**The auto-pick takes the settled line, through the panel's own seam.** `routeAutoPickReady()` fires on the first `Choosing` carrying a plan and selects **index 0**, which `routeCandidateLines` defines as the settled answer with the engine's offers behind it — so `selectRoute()` is used unchanged, nothing waits for an offer, and the flag clears on the selection, on an end and on a new arming.

**No glyph in that fan carries a hue.** The user's rule, in two passes: *all white all the time*. The Discard child lost `AppConfig.semanticDanger`, the parent glyph lost `routeLineColor` / `routeNavigateColor`, and the anchor's three faces are told apart by the `MapPulseDot` alone. The mode's read at large is untouched — `RouteToggleButton` still draws R51's green while acquiring and blue while following — so the map's colour is the mode's own square, and `docs/color-scheme.md` §2 now states the rule without its parent exception.

**The rest of the day, on the same branch:** the markers' create action moved to the head of both markers headers (committed `12b8338`, device pass owed), and the master book took the fan's requirements — **R49 · R50 · R51 · R59 · R60 · R73 amended** and **R75–R80 added**.

**Not validated:** nothing of this session has been seen on the device — not the arc's order, its fade against the layer fan, the 0.25 face across five glyphs, the close on press, the anchor's faces and dot, the two waypoint marks in one arc, the auto-pick's hand-over, nor the markers' two new header actions.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `ControlId.ROUTE_FAN`, the children list in bottom-to-top order, `routeFanEnabled`, `routeFanActions`, the auto-pick's arming, the Add Zone square's three removal sites
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — the five child icons, the parent's plain mark, `AddLocationAltIcon` deleted, `MapPulseDot` untouched
- `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt` · `MapControlButton.kt` · `MapControls.kt` — `enabledStates`, `enabled`, the new `ControlId` entry
- `app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt` — `routeAutoPickReady()` beside `routeCandidateLines`
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the auto-pick flag and its one-shot
- `app/src/main/java/ykws/android/maro/ui/icons/` — `AddRoad.kt`, `Bolt.kt`, `Route.kt`, `Save.kt`, `Logout.kt` and `Cancel.kt` added, `AddRoad.kt` and `DeleteForever.kt` deleted with their callers
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — the fan's two new keys in both locales
- `app/src/test/java/ykws/android/maro/ui/map/RouteAutoPickTest.kt` (new, 4 cases)
- `docs/color-scheme.md` §2 · `docs/ui-lists-guidelines.md` — the round map button's no-hue rule, and the markers list's header order
- `xTrack/Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md` — R49, R50, R51, R59, R60, R73 amended, R75–R80 added
- `xTrack/Route/260929_FEAT_PLN_Route_map-fan-add-route.md` — this pass's plan of record, reviewed to **revise** with every finding folded, its Outcome written
- `xTrack/Route/FEAT_DSC_Route.md` — both new `## Implemented` entries, the fan rule, the Docs row, the date
- `xTrack/Markers/260929_FEAT_PLN_Markers_add-marker-trigger.md` · `xTrack/Markers/FEAT_DSC_Markers.md` — the morning's marker pass

## Next Step

The Route walk at [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md) is **open** — its first level's cursor still reads item 3, which is closed, so the field is stale and the first open item is **13** (the trigger's read of the end pair, moved out of its coroutine launch) — and an open walk blocks the bake's fold and any `#archive` until it closes. Behind it the walk's own order stands: item 14's never-re-reviewed folded fixes, Phase 5's marker weights, Change 4's fine band, the progressive-draw findings, the F1/F2 wording pair, the two long-line record rows, and the device passes — the fairing's, the drawer's, the acquisition's, the markers' two header actions and now the arc's own readings.
