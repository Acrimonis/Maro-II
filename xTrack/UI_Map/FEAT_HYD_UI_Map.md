# Context Hydration — UI_Map — 2026-10-17

**Last Bake:** 2026-10-17 12:10 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (`#doc update`, after an ordered layout change on `feature/lyt-ui`), the device was never touched by the agent (the user's own pass confirmed the layout), and every claim about the code rests on a file read.

## State

**Documentation refresh (`#doc update`, 2026-10-17).** The feature's record was rebuilt around one decisions doc: `FEAT_DOC_UI_Map_decisions.md` groups the shipped calls by subject — rendering and layer order, layer visibility, the toggle family, the bottom band and tag column, selection and inspect, map offset and orientation, chrome and the drawer framework — each with its rationale and the plan or source that carries it. `## Implemented` became a pointer index of one-liners, its 2026-10-09 toggle-colour landing added (it shipped without an entry), and the sections lost their local todo lists to the parent `## Todos`; the `toggle-danger-layer` section went as superseded by the layer fan, and `rotate` / `decenter-map` became `## Implemented` entries since both shipped. Decisions were harvested from the plans' own `## Outcome` / `## Decisions` sections (261009, 261007, 261010 — the only three that carry one) and from the Implemented record; no unshipped design was promoted into a decision.

**Bottom-left tag alignment (2026-10-17, `feature/lyt-ui`).** The map's bottom zone padded itself by `WindowInsets.navigationBars` in landscape, so the regulated-zone tag column stood a navigation bar's height above the zoom stack's six-dp gap; it now takes `padding(bottom = TOP_TOGGLE_GUTTER)` in both orientations, the fork and its import gone, committed at `e9303bbe`. The user's device pass read the tag column and the zoom stack bottoming out on **one line**. Both guideline homes carry the rule (`docs/ui-component-guidelines.md` §5.7, `docs/ui-drawer-guidelines.md` §1). Left open: the same box's loading card, error card and exit toast no longer clear the navigation bar in landscape, and the band gutter has two homes — `TOP_TOGGLE_GUTTER` and `cb`'s literal `6.dp`.

**Still live from an earlier session.** The palette's `.floor` 0.25 / `.ms` 666 against `AppConfig`'s 0.33 / 555 keep two `MapPulseDotTest` assertions red as the user ordered, untouched.

**Review.** The `#impl` run that shipped the tag alignment had its Ask hop: coverage held and three findings were carried forward (the banners' lost clearance, the gutter's two homes, two unused `WindowInsets` imports in `MapScreen`). No review artifact is on file for this doc refresh.

**Verification.** `assembleDebug` green on the tag-alignment change; this refresh is documentation only, with every pointer checked against the file it names.

## Target Files
- `xTrack/UI_Map/FEAT_DSC_UI_Map.md` — sections trimmed, `## Implemented` pointer index, consolidated `## Todos`, refreshed `## Docs`
- `xTrack/UI_Map/FEAT_DOC_UI_Map_decisions.md` — the new subject-grouped decisions record
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the bottom zone's one gutter rule (committed `e9303bbe`)
- `docs/ui-component-guidelines.md` §5.7, `docs/ui-drawer-guidelines.md` §1 — the band's placement rule
- `xTrack/GLOBAL_CONTEXT.md` — the UI_Map feature summary and its modified date

## Next Step
The consolidated todo list is the feature's own backlog: the `map refresh` troubleshooting items, the `depth color` alignment, the layer-zone re-bake, the 300 m auto-show and low-depth device passes, the overlay-layer pass, and the four older passes (inspect live-acquire, marker zoom, the dp second-density check and its open Medium).
