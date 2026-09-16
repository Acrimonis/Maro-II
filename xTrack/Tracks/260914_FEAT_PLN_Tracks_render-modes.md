<!-- scope: feature -->
# Track render modes — the speed heatmap generalized to every stored track

**Date:** 2026-09-14 · **Status:** in design — every decision below was taken in the same session's walk on `feature/track-speed`, the width rule added as D11, and two review passes folded in; no code written for this change yet
**Request:** the selected-track speed heatmap shipped earlier the same day; generalize it so every stored track renders under one three-way choice — Simple | Dir & Speed | Colours — owned by the menu drawer, with the Settings colour block retitled Default Colors.

## 1. What exists today (measured, not assumed)

- **Two independent axes.** `AppSettings.tracksDirectionVisible` decides whether direction arrows are drawn; `AppSettings.speedHeatmap` decides the selected track's rendering, null meaning follow `track.heatmap.mode` in `maro.properties`. The menu's "Show dir & speed" row writes the first, the drawer header's eye the second, so one visible rendering state has two writers and three sources.
- **The heatmap is a selected-track branch.** `MapTrackOverlayEffects` branches on `summary.id == highlightedTrackId` inside both the history and the pinned loop and calls `selectedTrackRendering(...)`, which has two paths: `HIGHLIGHT` (a 16f dark casing under an 8f gold core — the casing is the selection cue, the gold is the fill) and `SPEED` (the same casing under a banded core from `bandedAppearances`).
- **Every other stored track** takes one `computeTrackPolylineAppearance(...)` over its whole point list and `buildSegmentOverlays(...)`, which is where the recency gradient and both transparency ranges are applied.
- **Stroke widths today:** the live recording line is 10f; the selected track is an 8f core under a 16f casing; the history loop is 8f for the newest and 6f for every other; the pinned loop is 6f throughout. Width is therefore barely a selection cue — the selected track's 8f core is already the newest unselected track's width — and the casing does that work. D11 is the rule this change adopts from these numbers.
- **The banded path is geometry-paired and linear.** `SpeedBand` carries its own `pointIndices`; `bandedAppearances` quantises each point's speed and merges equal adjacent appearances into runs; `buildBandSegmentOverlays` reads only a band's own indices. Banding a track therefore costs one pass over its points, not one pass per band. It bakes `SELECTED_CORE_STROKE_WIDTH` into every band and ignores the fade.
- **The fade never reaches the banded path.** `bandedAppearances` bakes `ramp.coreAlpha` into every band and applies no transparency value. Invisible with one highlighted track, it becomes a regression with twenty banded ones, and it is the cue the parked selection item relies on.
- **The legend keys off the old scope.** `MapScreen` draws `TrackSpeedLegend` only while `heatmapMode == SPEED && highlightedTrackId != null`.
- **Strings.** `menu_show_tracks_direction` ("Show dir & speed") is the menu's only direction toggle; `settings_tracks_direction_label` ("Show tracks direction on map") is referenced by no Kotlin file; `settings_colors_label` ("Colors") heads the Settings colour block.
- **Menu Tracks card order today:** the live block while recording is drawn after the Import and Export pair, not first.

## 2. The decision set (walk, 2026-09-14)

- **D1 — the mode owns strokes and arrows.** Simple is the default colours with no arrows; Dir & Speed keeps those colours and adds the arrows; Colours bands the strokes and colours each arrow by the band it sits on. `tracksDirectionVisible` retires with the mode taking its place. Cost accepted: a heat map always draws its arrows, so the ramp alone is not expressible.
- **D2 — scope.** The mode governs both stored-track loops, history and pinned; the live recording line keeps its appearance, so the ramp is built once per stored track inside the loop that exists rather than on the append path. Cost accepted: pinned tracks lose the amber gradient, colour being the speed reading.
- **D3 — the store.** One non-null `TrackRenderMode` field in `AppSettings` beside its key constant, default `SIMPLE`, persisted. Neither `speed_heatmap` nor `track_direction_visible` is read any more, so an install that held either returns to Simple — accepted, not migrated. `track.heatmap.mode` goes too, leaving one default (the code constant) and one owner of the mode (the store); the eye's scoped value, if §4 ever persists it, is a second field in that store and never a second writer of the mode.
- **D4 — Settings surface.** The Layers tab's Tracks card is unchanged in structure; only the inner Colours heading becomes **Default Colors**, its description naming those colours as the rendering used outside the Colours mode. The expander keeps its "Track rendering" label, since it also carries the track count and both transparency ranges, which govern all three modes and are not colours.
- **D5 — menu surface.** The "Show dir & speed" row is replaced in its own slot by a **Tracks rendering** caption and the three-way switch, and no other row moves: the card's order is settled as the live card, then the track list row with its count and chevron, then the switch, then the Import and Export pair — which moves the live block to the card's head from its present place after the import pair. The order is settled for portrait; landscape follows it because the drawer is one composable, and that is inference rather than measurement, so §6 checks it.
- **D6 — arrow controls.** The Track Speed and Direction expander ships untouched — label, density choice, gap range and speed range. Cost accepted: those controls do nothing while Simple is the mode, and the menu switch is the only thing that says why.
- **D7 — legend.** Drawn whenever the mode is Colours, selection no longer a condition, keeping its position and its readout of the ramp.
- **D8 — the fade multiplies the bands.** A band's alpha is the track's own fade value — newest to oldest for history, the pinned range for pinned — times `ramp.coreAlpha`, so transparency means the same thing in all three modes and stays the cue the parked selection item relies on. The fade is not re-derived: it is the same index-over-total reading `computeTrackPolylineAppearance` takes today between the two transparency ranges, reused by the banded path. Cost accepted: two alphas multiply, so the heaviest fades desaturate the bands and the oldest tracks read their speed least sharply.
- **D9 — strings.** The switch reads Simple | Dir & Speed | Colours, in French Simple | Dir & Vitesse | Couleurs, under a caption reading Tracks rendering and Rendu des traces. The Settings heading becomes Default Colors and Couleurs par défaut. Two strings are deleted from both locales: `menu_show_tracks_direction` and the unreferenced `settings_tracks_direction_label`. The ramp keeps its own heat-map name everywhere the code and the properties refer to it — the option label is a label, not a rename of the mechanism.
- **D10 — the drawer eye stays, scoped.** The eye keeps its place in the drawer header and its business is the selected track alone: it flips that one track's rendering and never moves the mode every other track renders by, so the menu switch remains the mode's only writer.
- **D11 — the banded stroke keeps today's widths.** 8f for the newest history track, 6f for every other and for pinned, with the selected track's 8f core under its 16f casing unchanged, so a mode switch does not restyle the map's density. Mechanically the width becomes a parameter of `bandedAppearances` rather than the `SELECTED_CORE_STROKE_WIDTH` constant it bakes today.
- **Noted costs, decided knowingly.** Colours mode changes three things at once — banded lines, band-coloured arrows and an always-present legend — so a user who wants the ramp alone still gets arrows and a strip, which is the combination D1's accepted cost gives up. And the Settings expander keeps its "Track rendering" label while the mode now lives in the menu, so a reader looking for the rendering choice under that label finds colour rows instead.

## 3. Contract detail

### 3a. Mode semantics

- `TrackRenderMode` replaces `TrackHeatmapMode` with three values — `SIMPLE`, `DIR_SPEED`, `HEATMAP` — the last carrying the visible label **Colours**; the internal and properties vocabulary stays heat-map.
- Strokes: Simple and Dir & Speed use `computeTrackPolylineAppearance(...)` unchanged, including the recency gradient, both transparency ranges and the widths D11 fixes — that decision is the single home of the numbers; Colours uses the banded path for every stored track, with the same fade applied per D8 and the same widths.
- Arrows: drawn for Dir & Speed and Colours, never for Simple; spacing, density and both ranges are exactly today's controls, and the arrow colour in Colours is the local band colour through the existing resolver.
- Selection: the selected track keeps the 16f dark casing and the z-lift in every mode; gold remains the fill in Simple and Dir & Speed. Width is not part of the cue, the selected track's 8f core already being the newest unselected track's width (D11). How the selected and pinned tracks are told apart in Colours is **not settled here** — see §4.

### 3b. Rendering path and its keys

- The mode is decided once per rebuild and dispatches to the two self-contained path builders that `selectedTrackRendering` already separates, extended so the banded path takes the track's own fade and the track's own width.
- Every band of a track keeps that track's single overlay title — `track_hist_<id>` or `track_pin_<id>` — never a per-band suffix, because teardown matches titles by prefix and the selected track's z-lift matches its title exactly. Recorded before for the one selected track, the rule now holds for every stored track.
- The rebuild key list becomes mode-aware: values the current mode does not read must not trigger a rebuild, so a default-colour edit cannot rebuild a heat-mapped map. The transparency and count values stay in the list, since D8 makes them live in every mode.

### 3c. Store and migration

- `AppSettings` gains `trackRenderMode: TrackRenderMode` (non-null, default `SIMPLE`) with its key constant, written by the menu switch and read by the map.
- Reads and writes of `track_direction_visible` and `speed_heatmap` are removed; `track.heatmap.mode` is removed from `maro.properties` and its parsing from `AppConfig`. An install that held the old values starts on Simple.
- The eye's own state is separate from the mode (D10) and is an open point in §4; until that point is settled the eye is session-only and never persisted, so a relaunch lands on the stored mode.

### 3d. Surfaces

- **Menu** — a section caption "Tracks rendering" over a three-option `SegmentedRow` (the component the density row already uses), in the retired row's slot, card order per D5.
- **Settings** — the heading rename only, one string pair; the arrow expander untouched per D6.
- **Legend** — the condition reduces to the mode per D7 and nothing else about it changes: it is a Compose overlay above the map canvas rather than an osmdroid overlay, so its panel treatment and its layering stand as they are, and no legend file appears in the work package.

### 3e. Strings

| Purpose | Key | EN | FR |
|---|---|---|---|
| Section caption | new | Tracks rendering | Rendu des traces |
| Option 1 | new | Simple | Simple |
| Option 2 | new | Dir & Speed | Dir & Vitesse |
| Option 3 | new | Colours | Couleurs |
| Settings heading | `settings_colors_label` | Default Colors | Couleurs par défaut |
| Retired | `menu_show_tracks_direction` | deleted | deleted |
| Retired | `settings_tracks_direction_label` | deleted | deleted |

## 4. Open points

- **Selection and pinned cues in Colours mode (walk item 3, parked).** With colour spent on speed and the amber gradient given up, the interim rule is the differing transparency levels as they are set today; the width half is settled by D11, so what remains is colour alone. Four readings stand recorded in the feature file's walk — casing only, gold surviving selection, a casing token per cue, or no map cue.
- **The banding cost (walk item 10, parked).** Nothing bounds it beyond the not-pinned count slider and the unconditional pinned render. Measured: the path stays linear in the points of the rendered tracks, since a band builds only its own geometry, while object and polyline counts rise by a constant factor with the number of bands. Three readings stand recorded — trim the keys only, trim and cache, or trim and measure on device — and the device run that would settle it is the same one that verifies this change.
- **The eye's own value (carried from walk item 12).** D10 scopes the eye to the selected track and D3 removed the legacy key it used to write, so two answers are owed: where that value lives (a second field beside the mode, session-only state, or a derived default) and whether it persists; and what a flip returns to — from Simple it turns the ramp on, from Colours it turns it off, and from Dir & Speed the intent is ambiguous, the track already carrying arrows. Until both are settled the eye ships session-only, never persisted.
- **`track.heatmap.*` keeps its prefix** even though the mode now covers three renderings; renaming it would touch the baked file, `AppConfig` and the ramp tests for no behaviour change.

## 5. Work package

- `config/HeatmapRamp.kt` — the enum's home: `TrackHeatmapMode` becomes `TrackRenderMode` with `SIMPLE`, `DIR_SPEED`, `HEATMAP`, and its consumers are `OverlayLayerParams`, `OverlayLayer`, `MapScreen`, `MapTrackOverlayEffects` and the tests that name it.
- `data/settings/SettingsManager.kt` — the `TrackRenderMode` field, its key, the removal of the two legacy reads and writes.
- `config/AppConfig.kt` — drop the `track.heatmap.mode` parse; the ramp, the tick table and the scale minimum stay.
- `app/src/main/assets/maro.properties` — remove `track.heatmap.mode` and its comment block.
- `ui/map/MapTrackOverlayEffects.kt` — the mode decides the path for every stored track; the fade and the width reach the bands; the key list becomes mode-aware.
- `ui/map/TrackSpeedHeatmap.kt` — the fade and width parameters on the banded path, and the constant names that still say *selected*.
- `ui/map/MenuDrawerOverlay.kt` — the caption and switch, the retired row, the live block moved to the card's head.
- `ui/map/OverlayLayer.kt`, `ui/map/OverlayLayerParams.kt` — two inputs in place of the arrow flag: the mode with its setter, and the eye's scoped override as a parameter of its own, since D10 lets it change the selected track without touching the mode.
- `ui/map/MapScreen.kt` — the legend condition; the eye's wiring follows §4's decision.
- `ui/map/MapScreenSettingsOverlay.kt` — the Default Colors heading.
- `ui/map/TrackHistoryOverlay.kt` — named to be explicit rather than as a work item: `computeTrackPolylineAppearance` is shared with the list's accent bars, and the mode is a map concern, so the cards keep their present colours.
- `res/values/strings.xml`, `res/values-fr/strings.xml` — the additions and the two deletions.
- Tests: the mode-to-path decision, the fade multiplication, the width carried per band, and the string set.

## 6. Verification

- Build green (`apk-build.bat`) and the scoped unit tests green, including the new ones above.
- The mode at Simple renders exactly what the app renders today — same colours, same widths, no arrows — which is the regression gate for the two rewritten loops.
- Device run with the count slider at its maximum and at least one pinned track, in both orientations: three modes switching from the menu, arrows appearing only in the last two, the fade reading newest to oldest, the legend present whenever the ramp is, and no stall while dragging a transparency slider — which is also the run that answers §4's cost question.
- Widths on the map: the newest history track heavier than the rest, the selected track's casing intact, and no global thickening against today's rendering.
- The French pair as rendered in the drawer, and both a fresh install and an install holding the legacy keys landing on Simple.
- Regression check on the paths D2 excludes: the live recording line's appearance, and the drawer header's eye still reaching only the selected track.

## 7. Future enhancements (out of scope)

- **A coloured casing per track.** Every stored track takes a 1–2 dp rim in its default colour — pinned amber for pinned, the recency colour for the rest — drawn under the stroke and dashed across GAP seams, so a track's identity survives the moment colour stops carrying it. Measured shape: one extra polyline per track segment rather than per band, since the rim's colour is uniform; the costs are one further copy of each track's geometry and roughly a hundred extra draw calls at twenty-five stored tracks, against today's ~100 polylines in Simple mode and 400–1000 in Colours mode. Objection: it spends a second full copy of every track's points for a rim that at low zoom mostly reads as slightly thicker lines. Recorded rather than dropped because it is also a candidate answer to §4's parked cue item.

## Outcome

**Shipped 2026-09-14 on `feature/track-speed`** through the `#implement` pipeline (Code → Ask → Architect), commit `910565b` local, `apk-build.bat` SUCCESS, 48 scoped tests green.

- **Built:** `TrackRenderMode` in `config/HeatmapRamp.kt`; one persisted `AppSettings.trackRenderMode` with `KEY_TRACK_RENDER_MODE`, and the two legacy keys plus `track.heatmap.mode` dropped; `trackRenderPlan()` beside a three-path `storedTrackRendering` in `MapTrackOverlayEffects`, with the fade and the width as banded-path parameters and a mode-aware rebuild key list; the menu's caption and switch with the live block moved to the card's head; the legend keyed on the mode; the Settings heading and its description renamed in both locales; the drawer eye kept as a session-only selected-track override; two new test classes beside the extended `TrackSpeedHeatmapTest`.
- **Deviations, all recorded:** `SegmentedRow` moved from `private` to `internal` so the menu reuses the density row's component rather than a copy; the eye's flip takes §4's interim reading and persists nothing; the dark casing chevron stays the selected track's alone in Colours mode instead of being extended to every banded track; and `settings_colors_desc` changed beside the heading, since D4 has the description name those colours as the rendering used outside Colours.
- **Ask-hop findings, open:** two mediums sharing one root — the eye's `false` maps to SIMPLE, so the override also rewrites the arrow decision (in Dir & Speed a second tap strips the selected track's arrows while every other track keeps theirs, and in Simple one tap gives that one track arrows no other track has), and the rebuild key list adds the arrow keys only when the mode is not Simple, which leaves those chevrons stale in exactly that state. Four lows: `HISTORY_NEWEST_STROKE_WIDTH` aliases `SELECTED_CORE_STROKE_WIDTH`, so a selection-named constant governs every newest history track; the mode is threaded twice into `OverlayLayerParams` as `trackRenderMode` and `renderMode`; the new caption is a raw muted `Text` against the card's shared stencils; and three pre-existing `HeatmapRampPropertiesTest` reds pin ramp values and tick rows the shipped `maro.properties` no longer holds.
- **Still open from §4:** the Colours-mode cue, whose width half D11 settled; the banding cost; the eye's own value and its flip semantics; and the `track.heatmap.*` prefix.
