# Context Hydration — UI_Map — 2026-10-10

**Last Bake:** 2026-10-10 07:43 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-09 17:02 UTC) the session ran on the user's own words alone: the mark's geometry moved into the palette one key at a time — size, floor, period, ring width, then the corner inset as a ratio — with the user's own edits to `colors.properties` taken as the source of truth, and the selection escape's marker half landed in this feature's files. Every landing ran on an `#implement`; no dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: the toggle-colour work sits in `50452a44` on `feature/route-swap-direction`, and today's work is in the working tree under this session's own `#commit`.

## State

**What shipped.** The mark's whole geometry is settings now — `ui.map.pulse.dot.size` 12, `.floor` 0.25, `.ms` 666, `.ring.width` 1 and `.inset.ratio` 0.25 — with the corner inset **derived** as that share of the disc (3 dp at the 12 dp disc) rather than a second number to keep in step, and `AppConfig` following the palette for each. The ring holds full strength outside the beat, so only the body fades; the row's squares paint the mark from the square's own corner, and the marks outside the row keep their own placement by being the same component. The palette block's own comment was rewritten in the same pass, retiring its stale 60 % floor. **The selection escape's marker half landed here**: `MarkerSelectionPolicy` gained the selection term it never had, fed from `MarkersViewModel`'s own selected marker id, so a marker the map filter excludes is drawn while its card stands — the decision itself is filed under TracksImport.

**Known red, on the user's word.** The palette reads `.floor` 0.25 and `.ms` 666 while `AppConfig`'s defaults and `MapPulseDotTest` still read 0.33 and 555, so two `MapPulseDotTest` assertions are red. The user ordered the pulse left exactly as it is, so the code and the test were **not** brought onto the palette: the red is expected, not a regression, and the pass that next touches the mark is the one to settle it.

**Status.** `apk-build.bat` green; the faces, the shipped palette and the mark's own keys pinned by `MapToggleFaceTest` (17), `MapTogglePaletteTest` (5) and `TopToggleControlTest` (4), and no open walk — the feature file holds no `## Walk` section.

**What is owed.** The device pass over the five faces, the dot's colours, the mark's ring, its 25 % floor and its ratio inset, and the three surface weights as settled; then the inspect live-acquire, marker-zoom and dp-pass device checks, unchanged.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapPulseDot.kt` — the mark's four geometry keys and the derived ratio inset, each read from `AppConfig`
- `app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt`, `MapToggleFace.kt` — the base under every face and the row's one pure resolution home
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — the selected marker id the marker policy now reads
- `app/src/main/java/ykws/android/maro/data/model/MapSelectionPolicy.kt` — the marker and track selection policies; the escape is filed under TracksImport
- `app/src/main/assets/colors.properties`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the palette's keys and the code defaults that follow them
- `app/src/test/java/ykws/android/maro/ui/map/MapToggleFaceTest.kt`, `MapTogglePaletteTest.kt`, `MapPulseDotTest.kt`, `TopToggleControlTest.kt` — the face, palette and mark-key pins
- `xTrack/UI_Map/261009_FEAT_PLN_UI_Map_toggle-colour-normalization.md` — the normalization's one home; `xTrack/TracksImport/261010_FEAT_PLN_TracksImport_render-escape-selected-item.md` — the escape's

## Next Step

The device pass over the toggle squares — the five faces, the dot's colours, the mark's ring, its 25 % floor and its ratio inset, and the three surface weights as settled.
