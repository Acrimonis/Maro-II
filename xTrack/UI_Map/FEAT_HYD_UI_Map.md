# Context Hydration — UI_Map — 2026-10-10

**Last Bake:** 2026-10-10 08:05 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (the fan-master plan, its implementation, then this bake), the device was never touched (the build and the scoped suite are named below), and every claim about the code rests on a file read.

**Directive trace, develop-side bake (2026-10-10 07:43 UTC):** Since the last bake (2026-10-09 17:02 UTC) the session ran on the user's own words alone: the mark's geometry moved into the palette one key at a time — size, floor, period, ring width, then the corner inset as a ratio — with the user's own edits to `colors.properties` taken as the source of truth, and the selection escape's marker half landed in this feature's files. Every landing ran on an `#implement`; no dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: the toggle-colour work sits in `50452a44` on `feature/route-swap-direction`, and today's work is in the working tree under this session's own `#commit`.

## State

**Layer fan master gate (2026-10-10 08:05 UTC).** The layer fan became the **master display gate** for the routes-and-tracks family, on `feature/rte-n-trck-list`. `AppSettings.routeTracksVisible` (key `route_tracks_visible`, default true) is what fan child 1 now writes — it no longer writes `tracksVisible`, which stays the TRACKS header eye's own — and the render reads master ∧ kind-eye through the new pure `kindLayerOn`, applied at the render pass (with `routeTracksVisible` in its rebuild keys), at the inspect candidates and at the list preview.

The open dashboard's own item is the gates' **one exception** (the user's rule): `storedTrackSelection` takes the open item's id and answers it alone when its half is gated off, and the marker overlay composes for its open card's marker while the layer is hidden. The filter is never escaped, so the map's own 2026-09-28 rule stands. The two header eyes are drawn dimmed yet stay **tappable**, so a tap still records the kind's choice for the master's return. This supersedes the list-split plan's D2 — *the map's layer fan is left alone… the fan is therefore not a routes door* — and the settings doc's sentence that repeated it was rewritten.

**Toggle geometry and the selection escape (2026-10-10 07:43 UTC).** The mark's whole geometry is settings now — `ui.map.pulse.dot.size` 12, `.floor` 0.25, `.ms` 666, `.ring.width` 1 and `.inset.ratio` 0.25 — with the corner inset **derived** as that share of the disc (3 dp at the 12 dp disc) rather than a second number to keep in step, and `AppConfig` following the palette for each. The ring holds full strength outside the beat, so only the body fades; the row's squares paint the mark from the square's own corner, and the marks outside the row keep their own placement by being the same component. The palette block's own comment was rewritten in the same pass, retiring its stale 60 % floor. **The selection escape's marker half landed here**: `MarkerSelectionPolicy` gained the selection term it never had, fed from `MarkersViewModel`'s own selected marker id, so a marker the map filter excludes is drawn while its card stands — the decision itself is filed under TracksImport.

**Known red, on the user's word (develop-side bake).** The palette reads `.floor` 0.25 and `.ms` 666 while `AppConfig`'s defaults and `MapPulseDotTest` still read 0.33 and 555, so two `MapPulseDotTest` assertions are red. The user ordered the pulse left exactly as it is, so the code and the test were **not** brought onto the palette: the red is expected, not a regression, and the pass that next touches the mark is the one to settle it.

**Status.** `apk-build.bat` BUILD SUCCESSFUL with `app-debug.apk` produced, and `:app:testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` green at 41 suites / 419 tests / 0 failures on this branch; the develop-side bake reports the two `MapPulseDotTest` assertions above as expected reds. The faces, the shipped palette and the mark's own keys are pinned by `MapToggleFaceTest` (17), `MapTogglePaletteTest` (5) and `TopToggleControlTest` (4), and no open walk — the feature file holds no `## Walk` section.

**What is owed.** The device pass, owed to the user: fan child 1 hiding and showing both kinds, each header eye dimmed yet still writable, a list-opened route drawn alone with the family off, and a hidden marker layer drawing its open card's marker alone; then the device pass over the five faces, the dot's colours, the mark's ring, its 25 % floor and its ratio inset, and the three surface weights as settled; then the inspect live-acquire, marker-zoom and dp-pass device checks, unchanged. The feature's older owed passes stand beside it.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the pure `kindLayerOn(masterVisible, kindVisible)` and the `selectedId` escape on `storedTrackSelection`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — fan child 1 on the master, the badge and active states, the inspect-candidate gate, the marker overlay's open-card exception, and the removed force-the-kind-on block
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — `toggleRouteTracksVisibility()`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the master flag, its key, its load and its save
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — `masterVisible` and the dimmed-but-live eye
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`, `OverlayLayerParams.kt`, `MapOverlayData.kt` — the master carried through `MenuOverlayData` and the list preview's gate
- `app/src/main/java/ykws/android/maro/ui/map/MapPulseDot.kt` — the mark's four geometry keys and the derived ratio inset, each read from `AppConfig`
- `app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt`, `MapToggleFace.kt` — the base under every face and the row's one pure resolution home
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — the selected marker id the marker policy now reads
- `app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt` — the marker and track selection policies; the escape is filed under TracksImport
- `app/src/main/assets/colors.properties`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the palette's keys and the code defaults that follow them
- `app/src/test/java/ykws/android/maro/ui/map/TrackRouteRoleTest.kt` — the master-and-eye rule and the open item's cases
- `app/src/test/java/ykws/android/maro/ui/map/MapToggleFaceTest.kt`, `MapTogglePaletteTest.kt`, `MapPulseDotTest.kt`, `TopToggleControlTest.kt` — the face, palette and mark-key pins
- `xTrack/UI_Map/261010_FEAT_PLN_UI_Map_route-tracks-master-gate.md` — the plan of record, superseding the split plan's D2
- `xTrack/UI_Map/261009_FEAT_PLN_UI_Map_toggle-colour-normalization.md` — the normalization's one home; `xTrack/TracksImport/261010_FEAT_PLN_TracksImport_render-escape-selected-item.md` — the escape's

## Next Step

The device pass, owed to the user: fan child 1 hiding and showing both kinds, each header eye dimmed yet still writable, a list-opened route drawn alone with the family off, and a hidden marker layer drawing its open card's marker alone. The feature's older owed passes stand beside it.
