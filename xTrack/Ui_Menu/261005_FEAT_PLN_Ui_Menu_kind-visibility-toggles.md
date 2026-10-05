# Menu — per-kind visibility toggles in the Routes and Tracks headers

**Status:** in design · nothing implemented
**Feature:** Ui_Menu (the two drawer headers and their callbacks); the gate split also touches UI_Map ([`MapTrackOverlayEffects`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt)), the map shell ([`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)) and the settings store ([`SettingsManager`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt)).
**Branch:** `feature/menu-kind-visibility`, **stacked on `feature/list-tracks-routes`** (S0) because `origin/develop` does not yet carry the routes-list separation (commit `695053b`) this plan builds on; cut with `--no-track` from that branch rather than from develop. When the list branch reaches `develop`, this branch rebases onto the merged base.
**Revision:** 2026-10-05 — created from the user's request. The per-kind split is the user's own call (D1).

## Request

- Add a **visibility toggle** to the menu drawer's **ROUTES** header and its **TRACKS** header, sitting **on the left of the existing Link / Filter / Reset icons**.
- Each toggle shows or hides **its own kind** on the map in one tap — a quick functional switch reached from the menu rather than a settings trip.
- The two kinds share one render layer, so the toggle reads as a layer switch while staying per kind (the user's word).
- The toggle is a **map-render switch alone**: the count beside each row and the rows themselves are not moved by it (the user's word, 2026-10-05).

## Current state

### The two drawer headers

- The drawer is [`MenuDrawerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:80). The **ROUTES** header is built at [`MenuDrawerOverlay.kt:213`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:213) and the **TRACKS** header at [`MenuDrawerOverlay.kt:263`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:263); each holds its Link · Filter · Reset icons **inside** the `routeFilterAxes` / `trackFilterAxes` non-empty gate, so the whole trailing slot disappears when no axis is configured.
- [`SectionHeader`](app/src/main/java/ykws/android/maro/ui/components/SectionHeader.kt:25) is a title taking the remaining width beside one `trailing` slot; the **order of the trailing children is the call site's**, so a first child renders leftmost.
- The MARKERS header already shows the "outside the gate" idiom: [`MarkerCreateAction`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:447) stands before the axes gate so creation never disappears with the filters (D6 of the settings plans).

### The one visibility gate today

- `tracksVisible` is the **master gate for both kinds**: [`storedTrackSelection()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:534) returns an all-empty selection when it is false, before any per-kind work, so a hidden tracks layer hides routes too.
- The setting lives at [`SettingsManager.kt:286`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:286), loaded at [`SettingsManager.kt:716`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:716) and saved at [`SettingsManager.kt:916`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:916).
- The one toggle today is the map's **layer fan** child 1 ([`MapScreen.kt:4854`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4854)), wired to [`toggleTracksVisibility()`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1293); the fan badge and `activeStates` read `tracksVisible` at [`MapScreen.kt:4826`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4826) and [`MapScreen.kt:4845`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4845).
- The gate is read again at the **inspect candidates** ([`MapScreen.kt:1342`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1342)), the **legend gate** ([`bandedStrokeOnMap()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:854), `tracksVisible && …`), the **open-from-list force-visible** ([`MapScreen.kt:1799`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1799)) and the **list preview key** ([`TrackHistoryOverlay.kt:169`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:169)).
- The rebuild keys carry `tracksVisible` alone ([`MapTrackOverlayEffects.kt:77`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:77)).

### The plumbing

- Read-only drawer state rides [`MenuOverlayData`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:55), built by [`buildMenuOverlayData()`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:18) and consumed by the menu slot at [`OverlayLayer.kt:409`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:409); callbacks are individual `OverlayLayer` parameters, never fields of the bundle.
- The two filter toggles already travel this path (`onToggleRouteLink`, `onToggleTrackLink`, [`MenuDrawerOverlay.kt:123`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:123)), so the visibility pair copies a proven route.

### Icons

- The header idiom pairs an on/off glyph ([`Link`](app/src/main/java/ykws/android/maro/ui/icons/Link.kt:1) / [`LinkOff`](app/src/main/java/ykws/android/maro/ui/icons/LinkOff.kt:1)); an eye already exists ([`Visibility.kt`](app/src/main/java/ykws/android/maro/ui/icons/Visibility.kt:13)) but there is **no `VisibilityOff`** — adding it follows [`material-icons-standalone-guide.md`](docs/material-icons-standalone-guide.md:1) and needs no dependency.

## Target

```text
Menu drawer
  ROUTES header [eye] Link · Filter · Reset   ← the eye stands first and outside the axes gate
  TRACKS header [eye] Link · Filter · Reset

Map render — the eye's only surface
  records+tracks  gated by tracksVisible   (pinned tracks with them)
  routes          gated by routesVisible   (pinned routes with them)

Menu count and lists — untouched by the eye
  each count stays its map-filtered set    each list keeps its rows
  a hidden kind greys nothing in its list and changes no number

Layer fan (unchanged, the user's word)
  child 1 stays the one tracks layer, reading tracksVisible alone
```

## Steps

- **S0 — cut the branch. Done 2026-10-05:** `feature/menu-kind-visibility` was cut with `--no-track` from `feature/list-tracks-routes`, not from `origin/develop`, because develop does not yet carry the routes-list separation this plan builds on; the branch is stacked on that unmerged work and rebases onto develop once the list branch merges.
- **S1 — the routes visibility setting.** Add `routesVisible: Boolean = true` beside [`tracksVisible`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:286), with `KEY_ROUTES_VISIBLE`, its load line beside [`SettingsManager.kt:716`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:716), its save line beside [`SettingsManager.kt:916`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:916) and the default true, mirroring the track key in every field.
- **S2 — the ViewModel toggle.** Add `toggleRoutesVisibility()` beside [`toggleTracksVisibility()`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:1293), same log-tag shape and the same `settingsManager.update { copy(...) }` body.
- **S3 — split the render gate.** In [`storedTrackSelection()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:524) take **`tracksVisible` and `routesVisible`** and remove the all-empty fast path at [`:534`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:534): the recorded half is empty when `!tracksVisible`, the route half when `!routesVisible`, and the **pinned** filter at [`MapTrackOverlayEffects.kt:552`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:552) gains the per-kind gate — a pinned route hides with routes, a pinned track with tracks. Update the one production call at [`MapTrackOverlayEffects.kt:186`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:186) and add `appSettings.routesVisible` to `rebuildKeys` at [`MapTrackOverlayEffects.kt:77`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:77). **The gate belongs to the render pass alone**: the menu counts ([`trackMapVisibleCountOf()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3928) / [`routeMapVisibleCountOf()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3950)) and the list membership are not touched, so the eye never changes the number beside a row.
- **S4 — the legend gate per kind.** [`bandedStrokeOnMap()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:854) gates on `tracksVisible` alone; make the gate per painted id — a route id asks `routesVisible`, a track id asks `tracksVisible` — and thread `routesVisible` through [`legendVisibleForState()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:801). Without this a visible route's ramp legend hides whenever tracks are off.
- **S5 — the inspect candidates per kind.** At [`MapScreen.kt:1342`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1342) the candidate set is gated by `tracksVisible` alone and filtered by `trackMapFilter` for every kind; gate each summary by its own kind's visibility and its own map filter, so a hidden route is never inspectable and a visible route always is.
- **S6 — force the opened kind visible.** At [`MapScreen.kt:1798`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1798) a fresh selection forces `tracksVisible = true`; make it pick the kind so opening a route from the routes list raises `routesVisible` and never touches `tracksVisible`.
- **S7 — the two header toggles.** In [`MenuDrawerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:80) add `trackVisible: Boolean = true`, `routeVisible: Boolean = true`, `onToggleTrackVisible` and `onToggleRouteVisible`; in each of the ROUTES ([`:213`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:213)) and TRACKS ([`:263`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:263)) `SectionHeader`s add the eye `IconButton` as the **first** trailing child — before the Link icon — drawn from the kind's own `Visibility` / `VisibilityOff` with the taken/off alpha the sibling icons use, and placed **outside** the `…FilterAxes.isNotEmpty()` gate so it never disappears with the filters. Add `cd_toggle_routes_map` and `cd_toggle_tracks_map` to `values` and `values-fr`.
- **S8 — the off glyph.** Add [`VisibilityOff.kt`](app/src/main/java/ykws/android/maro/ui/icons/Visibility.kt:1) as a standalone Material Symbols icon under `ui/icons`, package corrected, per [`material-icons-standalone-guide.md`](docs/material-icons-standalone-guide.md:15); no library is added.
- **S9 — the plumbing.** Add `tracksVisible` / `routesVisible` to [`MenuOverlayData`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:55) and to [`buildMenuOverlayData()`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:18); pass them, with the two callbacks, into the menu slot at [`OverlayLayer.kt:409`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:409); wire the callbacks from [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3481) to `viewModel::toggleTracksVisibility` and `viewModel::toggleRoutesVisibility`.
- **S10 — the list preview key.** The list surface takes `tracksVisible` at [`TrackHistoryOverlay.kt:169`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:169); pass the **scope's own** visibility into it at [`OverlayLayer.kt:803`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:803) so the routes list's preview keys off `routesVisible`.
- **S11 — tests.** Cover the settings round-trip for `routesVisible`, the per-kind gating of [`storedTrackSelection()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:524) (tracks off and routes on, and the reverse, with the pinned list split), and the per-kind [`bandedStrokeOnMap()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:854) gate; run the suite to green.
- **S12 — the record.** Add a `## Implemented` entry to [`FEAT_DSC_Ui_Menu.md`](xTrack/Ui_Menu/FEAT_DSC_Ui_Menu.md:23) with this plan's pointer, touch the UI_Map summary if the gate split warrants it, then `#bake`.

## Decisions

- **D1 — independent per-kind visibility.** Taken with the user on 2026-10-05: `tracksVisible` narrows to recorded tracks (pinned tracks with them) and a new `routesVisible` gates routes (pinned routes with them), so each header hides only its own kind.
- **D2 — the map's layer fan is left alone.** Taken with the user: its child 1 stays the one tracks layer and keeps reading `tracksVisible`; the fan is therefore not a routes door and its badge does not mirror `routesVisible`. The two drawer headers are where routes visibility is switched.
- **D3 — the toggle stands outside the axes gate.** The eye is the header's first trailing child and sits outside the `…FilterAxes.isNotEmpty()` gate, so a kind with no filter axis still gets its visibility switch (the [`MarkerCreateAction`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:447) precedent).
- **D4 — the glyph pair is `Visibility` / `VisibilityOff`.** The eye reads on/off directly and mirrors the existing [`Link`](app/src/main/java/ykws/android/maro/ui/icons/Link.kt:1) / [`LinkOff`](app/src/main/java/ykws/android/maro/ui/icons/LinkOff.kt:1) idiom; the alternative — one eye dimmed by alpha like the Reset icon — avoids the new asset but reads less plainly as a hide action.
- **D5 — the eye covers the whole kind.** The render toggle shows or hides **every** item of its kind on the map — pinned items and items beyond the render cap included — so nothing of that kind survives the switch; the per-kind choice is made inside [`storedTrackSelection()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:524), the one home for "per kind".
- **D6 — the count follows the filter alone, never the eye.** The user's word, 2026-10-05: the list count is impacted by the filter, and by visibility not at all. The menu counts are built from [`trackMapVisibleCountOf()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3928) / [`routeMapVisibleCountOf()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3950) over the map filter and read no visibility flag, and the lists keep their rows, so the toggle is added to no count builder and no list flow.

## Risks

- The gate is read at five sites (render effect, legend, inspect candidates, open-from-list force, list preview); missing one leaves a hidden kind still inspectable, or a visible kind's legend greyed.
- An install already holding `tracksVisible = false` starts with `routesVisible` defaulting true, so its routes become visible where they were hidden before the split — the intended per-kind reading, but a visible change on first run.
- [`bandedStrokeOnMap()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:854) and [`legendVisibleForState()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:801) gain a parameter, which touches their unit tests and every caller.
- The pinned list is drawn as one set, so the per-kind gate must live inside [`storedTrackSelection()`](app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:524) rather than in the drawing loop, or a hidden route would survive through the pin.
- Placing the eye inside the axes gate would make it vanish whenever a kind carries no filter axis; D3 pins it outside.
- `MenuOverlayData` is `@Immutable` with `val` fields only; the two booleans are read-only state and the callbacks stay `OverlayLayer` parameters, as the existing toggles do.
- Adding the flag to a count builder or a list flow would make the number beside a row fall when its kind is hidden; D6 keeps both out of the toggle's path.

## Verification

- `apk-build.bat` with no new warning on the touched files, then the scoped `ui.map` + `data.settings` unit run and the whole suite.
- Test coverage owed: the `routesVisible` round-trip, the per-kind gating with the pinned split, and the per-kind legend gate.
- Device pass owed: the eye standing first in both headers and hiding/showing only its own kind, the pinned items following their kind, inspect not reaching a hidden kind, **both counts and both lists unchanged while a kind is hidden**, and the map's layer fan still switching the tracks layer alone.
